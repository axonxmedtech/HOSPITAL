import apiService from './apiService';

const housekeepingService = {
  createTask: (data) => apiService.post('/hospital/housekeeping/task', data),
  getTasks: () => apiService.get('/hospital/housekeeping/task'),
  completeTask: (id) => apiService.post(`/hospital/housekeeping/complete/${id}`),
  verifyTask: (id, supervisorSig) => apiService.post(`/hospital/housekeeping/verify/${id}`, { supervisorSig }),

  logWaste: (data) => apiService.post('/hospital/housekeeping/waste', data),
  getWasteLog: () => apiService.get('/hospital/housekeeping/waste'),

  openComplaint: (data) => apiService.post('/hospital/housekeeping/complaint', data),
  getComplaints: () => apiService.get('/hospital/housekeeping/complaint'),
  confirmComplaint: (id, role, resolution) =>
    apiService.post(`/hospital/housekeeping/complaint/${id}/confirm`, { role, resolution }),
};

export default housekeepingService;
