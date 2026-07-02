import apiService from './apiService';

const bloodBankService = {
  registerDonor: (data) => apiService.post('/hospital/blood-bank/donors', data),
  getDonors: () => apiService.get('/hospital/blood-bank/donors'),

  addUnit: (data) => apiService.post('/hospital/blood-bank/units', data),
  getAvailableUnits: (bloodGroup, rhType) =>
    apiService.get('/hospital/blood-bank/units', { params: { bloodGroup, rhType } }),

  requestBlood: (data) => apiService.post('/hospital/blood-bank/requests', data),
  getRequests: () => apiService.get('/hospital/blood-bank/requests'),

  performCrossMatch: (data) => apiService.post('/hospital/blood-bank/cross-match', data),

  issueUnit: (bloodUnitId, patientId) =>
    apiService.post(`/hospital/blood-bank/units/${bloodUnitId}/issue`, null, { params: { patientId } }),

  startTransfusion: (data) => apiService.post('/hospital/blood-bank/transfusions', data),
  completeTransfusion: (recordId, data) =>
    apiService.post(`/hospital/blood-bank/transfusions/${recordId}/complete`, data),
  getPatientTransfusions: (patientId) =>
    apiService.get(`/hospital/blood-bank/transfusions/patient/${patientId}`),
};

export default bloodBankService;
