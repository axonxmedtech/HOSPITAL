import apiClient from '../apiService';

const API_URL = '/api/pharmacy/purchases';

const purchaseApi = {
    create: async (data) => {
        const response = await apiClient.post(API_URL, data);
        return response.data;
    },
    getAll: async (page = 0, size = 10) => {
        const response = await apiClient.get(`${API_URL}?page=${page}&size=${size}`);
        return response.data;
    },
    getById: async (id) => {
        const response = await apiClient.get(`${API_URL}/${id}`);
        return response.data;
    },
    postInvoice: async (id) => {
        const response = await apiClient.post(`${API_URL}/${id}/post`);
        return response.data;
    }
};

export default purchaseApi;
