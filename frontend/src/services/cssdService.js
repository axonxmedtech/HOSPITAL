import apiService from './apiService';

const cssdService = {
  registerTray: (data) => apiService.post('/hospital/cssd/trays', data),
  getTrays: () => apiService.get('/hospital/cssd/trays'),

  returnTray: (data) => apiService.post('/hospital/cssd/return', data),

  startCycle: (data) => apiService.post('/hospital/cssd/cycle/start', data),
  verifyCycle: (id, data) => apiService.post(`/hospital/cssd/cycle/verify/${id}`, data),
  getCycles: () => apiService.get('/hospital/cssd/cycles'),

  issueTray: (data) => apiService.post('/hospital/cssd/issue', data),
  getIssues: () => apiService.get('/hospital/cssd/issues'),
};

export default cssdService;
