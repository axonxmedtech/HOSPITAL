import apiService from './apiService';

const nurseService = {
  // Admin nurse management
  getNurses: (search = '', page = 0, size = 10) =>
    apiService.get('/hospital/nurses', { params: { search, page, size } }),
  createNurse: (data) => apiService.post('/hospital/nurses', data),
  updateNurse: (id, data) => apiService.put(`/hospital/nurses/${id}`, data),
  deleteNurse: (id) => apiService.delete(`/hospital/nurses/${id}`),
  resetPassword: (id, newPassword) =>
    apiService.post(`/hospital/nurses/${id}/reset-password`, { newPassword }),
  assignWard: (id, wardId) =>
    apiService.post(`/hospital/nurses/${id}/assign-ward`, { wardId }),
  removeWard: (id, wardId) =>
    apiService.delete(`/hospital/nurses/${id}/assign-ward/${wardId}`),

  // Nurse dashboard
  getMyPatients: () => apiService.get('/hospital/nurse/dashboard/patients'),
  getMyTasks: () => apiService.get('/hospital/nurse/dashboard/my-tasks'),

  // Assessment
  createAssessment: (admissionId, data) =>
    apiService.post(`/api/ipd/${admissionId}/assessment`, data),
  getAssessment: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/assessment`),

  // Vitals
  recordVitals: (admissionId, data) =>
    apiService.post(`/api/ipd/${admissionId}/vitals`, data),
  getVitals: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/vitals`),

  // Orders (doctor creates, nurse reads)
  getOrders: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/orders`),
  createOrder: (admissionId, data) =>
    apiService.post(`/api/ipd/${admissionId}/orders`, data),
  updateOrder: (admissionId, publicId, data) =>
    apiService.put(`/api/ipd/${admissionId}/orders/${publicId}`, data),
  cancelOrder: (admissionId, publicId) =>
    apiService.delete(`/api/ipd/${admissionId}/orders/${publicId}`),

  // Tasks
  getTasks: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/tasks`),
  getPendingTasks: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/tasks/pending`),
  executeTask: (admissionId, taskId, payload) =>
    apiService.put(`/api/ipd/${admissionId}/tasks/${taskId}/execute`, payload),
};

export default nurseService;
