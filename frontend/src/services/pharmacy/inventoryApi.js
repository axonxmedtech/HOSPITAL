import apiClient from '../apiService';

const inventoryApi = {
    // Standard list and basic partial-match search
    getInventory: async (query = '', page = 0, size = 10) => {
        const params = `?q=${encodeURIComponent(query)}&page=${page}&size=${size}`;
        const response = await apiClient.get(`/api/pharmacy/inventory${params}`);
        return response.data;
    },
    // Low Stock specialized query
    getLowStock: async (page = 0, size = 10) => {
        const response = await apiClient.get(`/api/pharmacy/inventory/low-stock?page=${page}&size=${size}`);
        return response.data;
    },
    // Soon Expiring analytics query
    getExpiring: async (daysThreshold = 30, page = 0, size = 10) => {
        const response = await apiClient.get(`/api/pharmacy/inventory/expiring?days=${daysThreshold}&page=${page}&size=${size}`);
        return response.data;
    }
};

export default inventoryApi;
