import apiService from './apiService';

const adminDashboardService = {
  getExecutiveDashboard: (timeframe) => apiService.get('/hospital/dashboard/executive', { params: { timeframe } }),
  getClinicalDashboard: () => apiService.get('/hospital/dashboard/clinical'),

  createAlert: (data) => apiService.post('/hospital/dashboard/alert', data),
  getAlerts: () => apiService.get('/hospital/dashboard/alert'),
  acknowledgeAlert: (alertId, status, remarks) =>
    apiService.post('/hospital/dashboard/alert/acknowledge', { alertId, status, remarks }),
};

export default adminDashboardService;
