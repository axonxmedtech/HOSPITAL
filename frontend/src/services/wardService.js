import apiClient from './apiService';

const WardService = {
  getWards: () => apiClient.get('/hospital/wards').then(r => r.data),
  createWard: (payload) => apiClient.post('/hospital/wards', payload).then(r => r.data),
  bulkCreate: (payload) => apiClient.post('/hospital/wards/bulk', payload).then(r => r.data),
  getBeds: (wardId) => apiClient.get(`/hospital/wards/${wardId}/beds`).then(r => r.data),
  updateWard: (wardId, payload) => apiClient.put(`/hospital/wards/${wardId}`, payload).then(r => r.data),
  updateBedStatus: (bedId, status) => apiClient.put(`/hospital/beds/${bedId}`, { status }).then(r => r.data),
  getAvailableBeds: (wardId) => apiClient.get('/hospital/beds/available', { params: { ward_id: wardId } }).then(r => r.data),
  deleteWard: (wardId) => apiClient.delete(`/hospital/wards/${wardId}`).then(r => r.data),
};

export default WardService;
