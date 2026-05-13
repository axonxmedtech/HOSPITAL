import apiClient from '../apiService';

const medicinesApi = {
    getAll: async (search = '', page = 0, size = 10) => {
        const query = `?search=${encodeURIComponent(search)}&page=${page}&size=${size}`;
        const response = await apiClient.get(`/api/pharmacy/medicines${query}`);
        return response.data;
    },
    getById: async (id) => {
        const response = await apiClient.get(`/api/pharmacy/medicines/${id}`);
        return response.data;
    },
    create: async (data) => {
        const response = await apiClient.post('/api/pharmacy/medicines', data);
        return response.data;
    },
    update: async (id, data) => {
        const response = await apiClient.put(`/api/pharmacy/medicines/${id}`, data);
        return response.data;
    },
    // High speed optimized search
    search: async (query, page = 0, size = 10) => {
        const q = `?q=${encodeURIComponent(query)}&page=${page}&size=${size}`;
        const response = await apiClient.get(`/api/pharmacy/search/medicines${q}`);
        return response.data;
    },
    // Dropdown autocomplete
    autocomplete: async (query) => {
        const response = await apiClient.get(`/api/pharmacy/autocomplete/medicines?q=${encodeURIComponent(query)}`);
        return response.data;
    }
};

export default medicinesApi;
