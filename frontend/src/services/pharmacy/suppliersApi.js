import apiClient from '../apiService';

const suppliersApi = {
    getAll: async (search = '', page = 0, size = 10) => {
        const query = `?search=${encodeURIComponent(search)}&page=${page}&size=${size}`;
        const response = await apiClient.get(`/api/pharmacy/suppliers${query}`);
        return response.data;
    },
    getById: async (id) => {
        const response = await apiClient.get(`/api/pharmacy/suppliers/${id}`);
        return response.data;
    },
    create: async (data) => {
        const response = await apiClient.post('/api/pharmacy/suppliers', data);
        return response.data;
    },
    update: async (id, data) => {
        const response = await apiClient.put(`/api/pharmacy/suppliers/${id}`, data);
        return response.data;
    }
};

export default suppliersApi;
