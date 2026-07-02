import apiService from './apiService';

const trainingService = {
  createMaster: (data) => apiService.post('/hospital/training/masters', data),
  getMasters: () => apiService.get('/hospital/training/masters'),

  createSession: (data) => apiService.post('/hospital/training/sessions', data),
  getSessions: () => apiService.get('/hospital/training/sessions'),
  cancelSession: (id, reason) => apiService.post(`/hospital/training/sessions/${id}/cancel`, { reason }),
  verifySession: (id) => apiService.post(`/hospital/training/sessions/${id}/verify`),

  markAttendance: (data) => apiService.post('/hospital/training/attendance', data),
  getAttendance: () => apiService.get('/hospital/training/attendance'),
  correctAttendance: (id, attendanceStatus, reason) =>
    apiService.put(`/hospital/training/attendance/${id}/correct`, { attendanceStatus, reason }),

  getCertifications: () => apiService.get('/hospital/training/certifications'),
  getEmployeeHistory: (employeeId) => apiService.get(`/hospital/training/employees/${employeeId}/history`),
};

export default trainingService;
