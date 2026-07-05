import apiClient from '../apiService';

const branchesApi = {
    getAll: async () => {
        const response = await apiClient.get('/hospital/pharmacy-branches');
        return response.data;
    },
    create: async (data) => {
        const response = await apiClient.post('/hospital/pharmacy-branches', data);
        return response.data;
    },
    update: async (id, data) => {
        const response = await apiClient.put(`/hospital/pharmacy-branches/${id}`, data);
        return response.data;
    },
    resetPassword: async (id, password) => {
        const response = await apiClient.post(`/hospital/pharmacy-branches/${id}/reset-password`, { password });
        return response.data;
    },
    delete: async (id) => {
        const response = await apiClient.delete(`/hospital/pharmacy-branches/${id}`);
        return response.data;
    },
};

export default branchesApi;
