import apiService from './apiService';

const emergencyService = {
  getActiveVisits: () =>
    apiService.get('/api/emergency/visits'),

  registerVisit: (payload) =>
    apiService.post('/api/emergency/visits', payload),

  triage: (visitId, payload) =>
    apiService.post(`/api/emergency/visits/${visitId}/triage`, payload),

  assess: (visitId, payload) =>
    apiService.post(`/api/emergency/visits/${visitId}/assess`, payload),

  dispose: (visitId, payload) =>
    apiService.post(`/api/emergency/visits/${visitId}/dispose`, payload),
};

export default emergencyService;
