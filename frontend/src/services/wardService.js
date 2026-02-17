import apiClient from './apiService';

const WardService = {
  getWards: () => apiClient.get('/api/wards').then(r => r.data),
  createWard: (payload) => apiClient.post('/api/wards', payload).then(r => r.data),
  bulkCreate: (payload) => apiClient.post('/api/wards/bulk', payload).then(r => r.data),
  getBeds: (wardId) => apiClient.get(`/api/wards/${wardId}/beds`).then(r => r.data),
  updateWard: (wardId, payload) => apiClient.put(`/api/wards/${wardId}`, payload).then(r => r.data),
  updateBedStatus: (bedId, status) => apiClient.put(`/api/beds/${bedId}`, { status }).then(r => r.data),
  getAvailableBeds: (wardId) => apiClient.get('/api/beds/available', { params: { ward_id: wardId } }).then(r => r.data),
};

export default WardService;
