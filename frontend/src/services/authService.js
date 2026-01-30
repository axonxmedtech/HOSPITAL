import apiClient from './apiService';

/**
 * Auth Service - Authentication and authorization utilities
 * 
 * This service handles:
 * - Super Admin login
 * - Hospital user login
 * - Token storage and retrieval
 * - User information management
 * - Logout
 * 
 * @author HMS Team
 * @version Phase-1
 */

const authService = {
    /**
     * Super Admin login
     * 
     * @param {string} email - Email address
     * @param {string} password - Password
     * @returns {Promise} Login response with token and user details
     */
    platformLogin: async (email, password) => {
        const response = await apiClient.post('/platform/login', { email, password });

        // Store token and user info in sessionStorage (Tab specific)
        if (response.data.token) {
            sessionStorage.setItem('token', response.data.token);
            sessionStorage.setItem('user', JSON.stringify(response.data));
        }

        return response.data;
    },

    /**
     * Hospital user login (Hospital Admin or Doctor)
     * 
     * @param {string} email - Email address
     * @param {string} password - Password
     * @returns {Promise} Login response with token and user details
     */
    hospitalLogin: async (email, password) => {
        const response = await apiClient.post('/login', { email, password });

        // Store token and user info in sessionStorage (Tab specific)
        if (response.data.token) {
            sessionStorage.setItem('token', response.data.token);
            sessionStorage.setItem('user', JSON.stringify(response.data));
        }

        return response.data;
    },

    /**
     * Logout user
     * Clears token and user info from sessionStorage
     */
    logout: () => {
        sessionStorage.removeItem('token');
        sessionStorage.removeItem('user');
    },

    /**
     * Get current user information from sessionStorage
     * 
     * @returns {Object|null} User object or null if not logged in
     */
    getCurrentUser: () => {
        // DISABLED FOR DEVELOPMENT - Return mock user when no real user is logged in
        const userStr = sessionStorage.getItem('user');
        if (userStr) {
            return JSON.parse(userStr);
        }
        
        // Return mock user for development
        return {
            id: 1,
            name: "Development User",
            email: "dev@example.com",
            role: "HOSPITAL_ADMIN",
            hospitalId: 1,
            hospitalName: "Development Hospital"
        };
    },

    /**
     * Check if user is authenticated
     * 
     * @returns {boolean} True if user has a valid token
     */
    isAuthenticated: () => {
        // DISABLED FOR DEVELOPMENT - Always return true to bypass authentication
        return true;
        // return !!sessionStorage.getItem('token');
    },

    /**
     * Check if current user is Super Admin
     * 
     * @returns {boolean} True if user is Super Admin
     */
    isSuperAdmin: () => {
        const user = authService.getCurrentUser();
        return user && user.role === 'SUPER_ADMIN';
    },

    /**
     * Check if current user is Hospital Admin
     * 
     * @returns {boolean} True if user is Hospital Admin
     */
    isHospitalAdmin: () => {
        const user = authService.getCurrentUser();
        return user && user.role === 'HOSPITAL_ADMIN';
    },

    /**
     * Check if current user is Doctor
     * 
     * @returns {boolean} True if user is Doctor
     */
    isDoctor: () => {
        const user = authService.getCurrentUser();
        return user && user.role === 'DOCTOR';
    },

    /**
     * Check if current user is Receptionist
     * 
     * @returns {boolean} True if user is Receptionist
     */
    isReceptionist: () => {
        const user = authService.getCurrentUser();
        return user && user.role === 'RECEPTIONIST';
    },

    /**
     * Check if current user is Pharmacist
     * 
     * @returns {boolean} True if user is Pharmacist
     */
    isPharmacist: () => {
        const user = authService.getCurrentUser();
        return user && user.role === 'PHARMACIST';
    },

    /**
     * Get fresh user profile from server
     */
    getProfile: async () => {
        const response = await apiClient.get('/auth/me');
        return response.data;
    },

    /**
     * Update local user storage with fresh data
     */
    updateCurrentUser: (newProfile) => {
        const currentUser = authService.getCurrentUser();
        if (!currentUser) return null;

        // Merge new profile, preserving token if missing in new profile
        const updatedUser = { ...currentUser, ...newProfile };
        if (!newProfile.token) {
            updatedUser.token = currentUser.token;
        }
        sessionStorage.setItem('user', JSON.stringify(updatedUser));
        return updatedUser;
    }
};

export default authService;
