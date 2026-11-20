import { Link, useLocation } from 'react-router-dom';

/** Shown for any URL that matches no route (previously these silently redirected to "/"). */
const NotFound = () => {
  const { pathname } = useLocation();
  return (
    <main className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md bg-white border border-gray-200 p-8 text-center">
        <p className="text-sm font-semibold text-gray-500 mb-1">404</p>
        <h1 className="text-xl font-bold text-gray-900 mb-2">Page not found</h1>
        <p className="text-sm text-gray-600 mb-6 break-words">
          There is no page at <span className="font-mono text-gray-900">{pathname}</span>. Check the
          link, or go back to your dashboard.
        </p>
        <Link
          to="/"
          className="inline-block px-5 py-2.5 bg-gray-900 text-white text-sm font-semibold hover:bg-gray-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-900 focus-visible:ring-offset-2"
        >
          Go to dashboard
        </Link>
      </div>
    </main>
  );
};

export default NotFound;
