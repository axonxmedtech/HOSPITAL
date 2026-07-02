import apiService from './apiService';

const biomedicalService = {
  registerEquipment: (data) => apiService.post('/hospital/biomedical/equipment', data),
  getEquipment: () => apiService.get('/hospital/biomedical/equipment'),

  openBreakdownTicket: (data) => apiService.post('/hospital/biomedical/breakdown', data),
  getTickets: () => apiService.get('/hospital/biomedical/breakdown'),

  recordCalibration: (data) => apiService.post('/hospital/biomedical/calibration', data),
  getCalibrations: () => apiService.get('/hospital/biomedical/calibration'),

  closeTicket: (ticketId, confirmResolution) =>
    apiService.post('/hospital/biomedical/ticket/close', { ticketId, confirmResolution }),
};

export default biomedicalService;
