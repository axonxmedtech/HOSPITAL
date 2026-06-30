import apiClient from './apiService';

const cdssService = {
  checkPrescription: (patientId, ipdAdmissionId, medicineName, medicineMasterId) =>
    apiClient.post('/hospital/cdss/check-prescription', {
      patientId, ipdAdmissionId, medicineName, medicineMasterId,
    }).then(r => r.data),

  acknowledge: (patientId, ipdAdmissionId, alerts, overrideReason = '') =>
    apiClient.post('/hospital/cdss/acknowledge', {
      patientId, ipdAdmissionId, alerts, overrideReason,
    }),

  getEws: (ipdAdmissionId) =>
    apiClient.get(`/hospital/cdss/ews/${ipdAdmissionId}`).then(r => r.data),

  getSmartSummary: (ipdAdmissionId) =>
    apiClient.get(`/hospital/cdss/smart-summary/${ipdAdmissionId}`).then(r => r.data),

  seedInteractions: () =>
    apiClient.post('/hospital/cdss/seed-interactions').then(r => r.data),
};

export default cdssService;
