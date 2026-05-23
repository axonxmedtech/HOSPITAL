import apiClient from './apiService';

/**
 * Platform Service - API calls for Super Admin operations
 * 
 * This service handles API calls for:
 * - Creating hospitals
 * - Listing hospitals
 * - Activating/deactivating hospitals
 * - Viewing hospital details
 * 
 * All requests automatically include JWT token via apiClient interceptor.
 * Only Super Admin can access these endpoints.
 * 
 * @author HMS Team
 * @version Phase-1
 */

const platformService = {
    /**
     * Get all hospitals
     */
    getHospitals: async (page = 0, size = 10) => {
        const response = await apiClient.get(`/platform/hospitals?page=${page}&size=${size}`);
        return response.data; // Returns { content: [...], totalElements: ..., totalPages: ... }
    },

    /**
     * Create a new hospital with hospital admin
     */
    createHospital: async (hospitalData) => {
        const response = await apiClient.post('/platform/hospitals', hospitalData);
        return response.data;
    },

    /**
     * Get hospital by ID
     */
    getHospitalById: async (id) => {
        const response = await apiClient.get(`/platform/hospitals/${id}`);
        return response.data;
    },

    /**
     * Update hospital status (activate/deactivate)
     */
    updateHospitalStatus: async (id, isActive, reason) => {
        const response = await apiClient.put(`/platform/hospitals/${id}/status`, { isActive, reason });
        return response.data;
    },

    /**
     * Update hospital details (name, admin email, admin name)
     */
    updateHospitalDetails: async (id, name, adminEmail, adminName, reason) => {
        const response = await apiClient.put(`/platform/hospitals/${id}/details`, { name, adminEmail, adminName, reason });
        return response.data;
    },

    updateHospitalPlan: async (id, plan, reason) => {
        const response = await apiClient.put(`/platform/hospitals/${id}/plan`, { plan, reason });
        return response.data;
    },

    /**
     * Update hospital enabled modules
     */
    updateHospitalModules: async (id, modules, reason) => {
        const response = await apiClient.put(`/platform/hospitals/${id}/modules`, { modules, reason });
        return response.data;
    },

    /**
     * Reset Tenant Admin Password — password chosen by Super Admin
     */
    resetTenantPassword: async (id, password, reason = '') => {
        const response = await apiClient.post(`/platform/hospitals/${id}/reset-password`, { password, reason });
        return response.data;
    },

    /**
     * Get audit logs
     */
    getAuditLogs: async () => {
        const response = await apiClient.get('/platform/audit-logs');
        return response.data;
    },

    /**
     * Get hospital statistics for Super Admin Overview dashboard
     * Returns total, active, and inactive hospital counts
     */
    /**
     * Get user statistics for Super Admin Overview dashboard
     */
    getHospitalStats: async () => {
        const response = await apiClient.get('/platform/hospitals/stats');
        return response.data;
    },

    /**
     * Get all users
     */
    getUsers: async (role = '', hospitalId = '', search = '', page = 0, size = 10) => {
        const response = await apiClient.get(`/platform/users`, {
            params: { role, hospitalId, search, page, size }
        });
        return response.data;
    },

    /**
     * Reset User Password
     */
    resetUserPassword: async (id) => {
        const response = await apiClient.post(`/platform/users/${id}/reset-password`);
        return response.data;
    },

    /**
     * Get all support tickets (submitted by hospital admins)
     */
    getTickets: async () => {
        const response = await apiClient.get('/platform/tickets');
        return response.data;
    },

    /**
     * Resolve a ticket by ID
     */
    resolveTicket: async (ticketId) => {
        const response = await apiClient.put(`/platform/tickets/${ticketId}/status`, { status: 'RESOLVED' });
        return response.data;
    },
};

export default platformService;
