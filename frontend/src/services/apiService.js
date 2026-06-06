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

// Base URL for backend API
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// Create axios instance
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000, // 30 seconds timeout
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

// Response interceptor to handle authentication errors
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Don't redirect on login endpoints - let the login page handle errors
    const isLoginEndpoint = error.config?.url?.includes('/login');

    // If 401 Unauthorized or 403 Forbidden (but not on login pages)
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      if (!isLoginEndpoint) {
        console.warn('Authentication error:', error.response.status, 'Redirecting to login');
        sessionStorage.removeItem('token');
        sessionStorage.removeItem('user');
        window.location.href = '/login';
      } else {
        console.warn('Login failed:', error.response.status, error.response.data);
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
