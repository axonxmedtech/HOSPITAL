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
    getHospitals: async (page = 0, size = 10, type = '') => {
        const params = { page, size };
        if (type) params.type = type;
        const response = await apiClient.get('/platform/hospitals', { params });
        return response.data;
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
     * Update hospital details (name, admin email, admin name, isSingleDoctor)
     */
    updateHospitalDetails: async (id, name, adminEmail, adminName, reason, isSingleDoctor) => {
        const response = await apiClient.put(`/platform/hospitals/${id}/details`, { name, adminEmail, adminName, reason, isSingleDoctor });
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
     * Permanently delete a hospital and all its data
     */
    deleteHospital: async (id) => {
        const response = await apiClient.delete(`/platform/hospitals/${id}`);
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

    /**
     * Get all FAQs (uses public API since FAQs are global)
     */
    getFaqs: async () => {
        const response = await apiClient.get('/api/public/faqs');
        return response.data;
    },

    /**
     * Add a new FAQ
     */
    addFaq: async (faqData) => {
        const response = await apiClient.post('/platform/faqs', faqData);
        return response.data;
    },

    /**
     * Delete an FAQ by ID
     */
    deleteFaq: async (id) => {
        const response = await apiClient.delete(`/platform/faqs/${id}`);
        return response.data;
    },

    // ─── Plan Management ─────────────────────────────────────────────────────

    getPlans: async (type = '') => {
        const params = type ? { type } : {};
        const response = await apiClient.get('/platform/plans', { params });
        return response.data;
    },

    createPlan: async (planData) => {
        const response = await apiClient.post('/platform/plans', planData);
        return response.data;
    },

    updatePlan: async (publicId, planData) => {
        const response = await apiClient.put(`/platform/plans/${publicId}`, planData);
        return response.data;
    },

    deletePlan: async (publicId) => {
        const response = await apiClient.delete(`/platform/plans/${publicId}`);
        return response.data;
    },

    assignPlan: async (planPublicId, hospitalPublicId, billingPeriod) => {
        const response = await apiClient.post(`/platform/plans/${planPublicId}/assign`, {
            hospitalPublicId,
            billingPeriod,
        });
        return response.data;
    },
};

export default platformService;
