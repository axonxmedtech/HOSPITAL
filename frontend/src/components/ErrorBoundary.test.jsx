import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { isChunkLoadError } from '../utils/chunkLoadError';
import ErrorBoundary from './ErrorBoundary';

const Boom = ({ error }) => {
  throw error;
};

describe('ErrorBoundary', () => {
  let reload;
  let store;

  beforeEach(() => {
    // React logs caught errors to console.error; keep test output clean.
    vi.spyOn(console, 'error').mockImplementation(() => {});
    reload = vi.fn();
    vi.stubGlobal('location', { ...window.location, reload });
    store = {};
    vi.stubGlobal('sessionStorage', {
      getItem: vi.fn((k) => store[k] ?? null),
      setItem: vi.fn((k, v) => {
        store[k] = v;
      }),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders children when nothing throws', () => {
    render(
      <ErrorBoundary>
        <p>dashboard</p>
      </ErrorBoundary>
    );
    expect(screen.getByText('dashboard')).toBeInTheDocument();
  });

  it('shows a recovery screen instead of a blank page on a render error', () => {
    render(
      <ErrorBoundary>
        <Boom error={new TypeError("Cannot read properties of undefined (reading 'name')")} />
      </ErrorBoundary>
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload page' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to dashboard' })).toHaveAttribute('href', '/');
    // The technical message is never shown to the user.
    expect(screen.queryByText(/Cannot read properties/)).not.toBeInTheDocument();
    expect(reload).not.toHaveBeenCalled();
  });

  it('never logs component props (they can hold patient data)', () => {
    render(
      <ErrorBoundary>
        <Boom error={new Error('render failed')} patient={{ name: 'PHI-NAME' }} />
      </ErrorBoundary>
    );
    const logged = console.error.mock.calls.flat().map(String).join(' ');
    expect(logged).not.toContain('PHI-NAME');
  });

  it('reloads once when a lazy page chunk is missing after a deploy', () => {
    const stale = new TypeError(
      'Failed to fetch dynamically imported module: https://app/assets/DoctorDashboard-abc.js'
    );
    render(
      <ErrorBoundary>
        <Boom error={stale} />
      </ErrorBoundary>
    );
    expect(reload).toHaveBeenCalledTimes(1);
    expect(screen.getByText('A new version is available')).toBeInTheDocument();
  });

  it('does not reload in a loop if the chunk is still missing right after reloading', () => {
    store['hms:chunk-reload-at'] = String(Date.now());
    render(
      <ErrorBoundary>
        <Boom error={new TypeError('Failed to fetch dynamically imported module: x')} />
      </ErrorBoundary>
    );
    expect(reload).not.toHaveBeenCalled();
    expect(screen.getByText('A new version is available')).toBeInTheDocument();
  });

  it('clears the error when resetKey (the route) changes', () => {
    const { rerender } = render(
      <ErrorBoundary resetKey="/hospital/doctor">
        <Boom error={new Error('x')} />
      </ErrorBoundary>
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    rerender(
      <ErrorBoundary resetKey="/hospital/admin">
        <p>admin page</p>
      </ErrorBoundary>
    );
    expect(screen.getByText('admin page')).toBeInTheDocument();
  });
});

describe('isChunkLoadError', () => {
  it.each([
    'Failed to fetch dynamically imported module: /assets/x.js',
    'Importing a module script failed.',
    'error loading dynamically imported module',
    'Loading chunk 42 failed.',
  ])('detects "%s"', (message) => {
    expect(isChunkLoadError(new Error(message))).toBe(true);
  });

  it('ignores ordinary errors', () => {
    expect(isChunkLoadError(new TypeError('x is undefined'))).toBe(false);
    expect(isChunkLoadError(undefined)).toBe(false);
  });
});
