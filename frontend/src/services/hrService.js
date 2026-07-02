import apiService from './apiService';

const hrService = {
  onboardEmployee: (data) => apiService.post('/hospital/hr/employee', data),
  getEmployees: () => apiService.get('/hospital/hr/employee'),
  exitEmployee: (id) => apiService.post(`/hospital/hr/employee/${id}/exit`),

  createRosterSlot: (data) => apiService.post('/hospital/hr/roster', data),
  getRoster: () => apiService.get('/hospital/hr/roster'),

  requestLeave: (data) => apiService.post('/hospital/hr/leave', data),
  getLeaveRequests: () => apiService.get('/hospital/hr/leave'),
  approveLeave: (id, status) => apiService.post(`/hospital/hr/leave/approve/${id}`, { status }),

  processPayroll: (data) => apiService.post('/hospital/hr/payroll/process', data),
  getPayroll: () => apiService.get('/hospital/hr/payroll'),
};

export default hrService;
