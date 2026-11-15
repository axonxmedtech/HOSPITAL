import React, { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.jsx';
import ErrorBoundary from './components/ErrorBoundary';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* Last resort for errors outside the router (e.g. in providers). */}
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </StrictMode>
);
