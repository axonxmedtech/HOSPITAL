import axios from 'axios';

/**
 * API Service - Axios wrapper for making HTTP requests
 * 
 * This service:
 * - Configures base URL for backend API
 * - Automatically includes JWT token in request headers
 * - Handles authentication errors
 * 
 * @author HMS Team
 * @version Phase-1
 */

// Base URL for backend API — exported so components can import instead of redeclaring (BUG-028)
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// Create axios instance
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,            // 30 second request timeout
  maxContentLength: 5242880, // 5 MB response cap
});

// Request interceptor to add JWT token to headers
apiClient.interceptors.request.use(
  (config) => {
    // Get token from sessionStorage
    const token = sessionStorage.getItem('token');

    // Add token to Authorization header if it exists
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Helper to handle standard response errors (such as unauthorized session redirect)
const handleResponseError = (error) => {
  const isLoginEndpoint = error.config?.url?.includes('/login');

  // Timeout error — surface a clean message instead of network error
  if (error.code === 'ECONNABORTED') {
    return Promise.reject(new Error('Request timed out. Please try again.'));
  }

  // If 401 Unauthorized (token expired/missing), redirect to login
  if (error.response && error.response.status === 401) {
    if (!isLoginEndpoint) {
      try {
        const userStr = sessionStorage.getItem('user');
        const hospitalType = userStr ? JSON.parse(userStr)?.hospitalType : null;
        sessionStorage.removeItem('token');
        sessionStorage.removeItem('user');
        if (hospitalType === 'CLINIC') window.location.href = '/login/clinic';
        else if (hospitalType === 'PHARMACY') window.location.href = '/login/pharmacy';
        else window.location.href = '/login/hospital';
      } catch (_) {
        sessionStorage.removeItem('token');
        sessionStorage.removeItem('user');
        window.location.href = '/login/hospital';
      }
    }
  }

  return Promise.reject(error);
};

// Response interceptor to handle retries and authentication errors
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;
    // Only retry safe GET requests on network errors, timeout errors, or 5xx server errors
    if (!config || !config.method || config.method.toLowerCase() !== 'get') {
      return handleResponseError(error);
    }

    config.__retryCount = config.__retryCount || 0;

    if (config.__retryCount >= 3) {
      return handleResponseError(error);
    }

    const shouldRetry = !error.response || (error.response.status >= 500 && error.response.status <= 599) || error.code === 'ECONNABORTED';

    if (shouldRetry) {
      config.__retryCount += 1;
      const delay = 1000 * config.__retryCount;
      await new Promise((resolve) => setTimeout(resolve, delay));
      return apiClient(config);
    }

    return handleResponseError(error);
  }
);

export default apiClient;
