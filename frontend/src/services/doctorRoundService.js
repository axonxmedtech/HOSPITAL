import apiService from './apiService';

const doctorRoundService = {
  getRoundsHistory: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/rounds`),

  logRound: (admissionId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/rounds`, payload),

  amendRound: (admissionId, roundId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/rounds/${roundId}/amend`, payload),
};

export default doctorRoundService;
