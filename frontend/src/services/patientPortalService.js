import apiService from './apiService';

const patientPortalService = {
  requestOtp: (hospitalId, mobile, uhid) =>
    apiService.post('/hospital/portal/otp/request', { hospitalId, mobile, uhid }),
  verifyOtp: (hospitalId, mobile, otp) =>
    apiService.post('/hospital/portal/otp/verify', { hospitalId, mobile, otp }),

  getDashboard: () => apiService.get('/hospital/portal/dashboard'),
  getAppointments: () => apiService.get('/hospital/portal/appointments'),
  getReports: () => apiService.get('/hospital/portal/reports'),
  getPrescriptions: () => apiService.get('/hospital/portal/prescriptions'),
  getBilling: () => apiService.get('/hospital/portal/billing'),
};

export default patientPortalService;
