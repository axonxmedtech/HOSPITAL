import { Component } from 'react';
import { isChunkLoadError } from '../utils/chunkLoadError';

const CHUNK_RELOAD_KEY = 'hms:chunk-reload-at';
const CHUNK_RELOAD_WINDOW_MS = 30_000;

const readReloadAt = () => {
  try {
    return Number(sessionStorage.getItem(CHUNK_RELOAD_KEY)) || 0;
  } catch {
    return 0;
  }
};

const writeReloadAt = (value) => {
  try {
    sessionStorage.setItem(CHUNK_RELOAD_KEY, String(value));
  } catch {
    // Storage unavailable (private mode): fall through to the fallback screen.
  }
};

/**
 * Catches render errors anywhere below it and shows a recovery screen instead of a blank page.
 *
 * - A stale-chunk error after a deploy reloads the page once (guarded so it cannot loop).
 * - `resetKey` (the current path) clears the error when the user navigates elsewhere.
 * - Only the error itself is logged: never props or state, which can hold patient data.
 */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error) {
    console.error('[ErrorBoundary] Unhandled render error:', error?.name, error?.message);
    if (isChunkLoadError(error) && Date.now() - readReloadAt() > CHUNK_RELOAD_WINDOW_MS) {
      writeReloadAt(Date.now());
      window.location.reload();
    }
  }

  componentDidUpdate(prevProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    const stale = isChunkLoadError(error);
    return (
      <div role="alert" className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="w-full max-w-md bg-white border border-gray-200 p-8 text-center">
          <h1 className="text-xl font-bold text-gray-900 mb-2">
            {stale ? 'A new version is available' : 'Something went wrong'}
          </h1>
          <p className="text-sm text-gray-600 mb-6">
            {stale
              ? 'The app was updated while this page was open. Reload to continue.'
              : 'This screen hit an unexpected error. Your saved data is safe. Reload the page, or go back to your dashboard.'}
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="px-5 py-2.5 bg-gray-900 text-white text-sm font-semibold hover:bg-gray-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-900 focus-visible:ring-offset-2"
            >
              Reload page
            </button>
            <a
              href="/"
              className="px-5 py-2.5 border border-gray-300 text-gray-900 text-sm font-semibold hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-900 focus-visible:ring-offset-2"
            >
              Go to dashboard
            </a>
          </div>
        </div>
      </div>
    );
  }
}

export default ErrorBoundary;
