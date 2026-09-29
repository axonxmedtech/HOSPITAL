import apiClient from './apiService';
import WardService from './wardService';

const icuWardService = {
  getIcuWards: () => apiClient.get('/hospital/icu/wards').then((r) => r.data),
  getIcuWard: (publicId) => apiClient.get(`/hospital/icu/wards/${publicId}`).then((r) => r.data),
  createIcuWard: (payload) => apiClient.post('/hospital/icu/wards', payload).then((r) => r.data),
  updateIcuWard: (publicId, payload) =>
    apiClient.put(`/hospital/icu/wards/${publicId}`, payload).then((r) => r.data),
  deleteIcuWard: (publicId) =>
    apiClient.delete(`/hospital/icu/wards/${publicId}`).then((r) => r.data),
  setIncharge: (publicId, nurseProfileId) =>
    apiClient
      .post(`/hospital/icu/wards/${publicId}/incharge`, { nurseProfileId })
      .then((r) => r.data),
  getBeds: (wardId) => WardService.getBeds(wardId),
};

export default icuWardService;
