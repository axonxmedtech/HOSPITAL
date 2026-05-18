import apiClient from '../apiService';

const reportsApi = {
    getDashboardData: async () => {
        const response = await apiClient.get('/api/pharmacy/reports/dashboard');
        return response.data;
    },
    exportLedgerCsv: async () => {
        const response = await apiClient.get('/api/pharmacy/reports/export', {
            responseType: 'blob'
        });
        return response.data;
    }
};

export default reportsApi;
