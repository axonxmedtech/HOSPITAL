import apiClient from '../apiService';

const salesApi = {
    create: async (data) => {
        const response = await apiClient.post('/api/pharmacy/sales', data);
        return response.data;
    },
    getHistory: async (page = 0, size = 10) => {
        const response = await apiClient.get(`/api/pharmacy/sales?page=${page}&size=${size}`);
        return response.data;
    },
    getDetails: async (id) => {
        const response = await apiClient.get(`/api/pharmacy/sales/${id}`);
        return response.data;
    },
    getStats: async () => {
        const response = await apiClient.get('/api/pharmacy/sales/stats');
        return response.data;
    }
};

export default salesApi;
