import apiClient from '../apiService';

/** params: optional { from, to } ISO dates (both ends included); none = all time. */
const reportsApi = {
  getDashboardData: async (params = {}) => {
    const response = await apiClient.get('/pharmacy/reports/dashboard', { params });
    return response.data;
  },
  exportLedgerCsv: async (params = {}) => {
    const response = await apiClient.get('/pharmacy/reports/export', {
      params,
      responseType: 'blob',
    });
    return response.data;
  },
};

export default reportsApi;
