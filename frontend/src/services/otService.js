import apiClient from './apiService';

/**
 * otService - Operation Theatre API wrapper (OT module, Phase 2).
 * Shares auth/401 interceptors via apiClient.
 */
const otService = {
    // Doctor
    createRequest: async (payload) => (await apiClient.post('/hospital/surgeries', payload)).data,
    getActiveForAdmission: async (admissionId) =>
        (await apiClient.get(`/hospital/surgeries/admission/${admissionId}/active`)).data,
    getMyBoard: async () => (await apiClient.get('/hospital/surgeries/my-board')).data,

    // Reception
    getRequests: async () => (await apiClient.get('/hospital/surgeries/requests')).data,
    getBoard: async () => (await apiClient.get('/hospital/surgeries/board')).data,
    getSurgeons: async () => (await apiClient.get('/hospital/surgeries/surgeons')).data,
    schedule: async (publicId, payload) =>
        (await apiClient.post(`/hospital/surgeries/${publicId}/schedule`, payload)).data,
    start: async (publicId) => (await apiClient.post(`/hospital/surgeries/${publicId}/start`)).data,
    complete: async (publicId) => (await apiClient.post(`/hospital/surgeries/${publicId}/complete`)).data,
    cancel: async (publicId) => (await apiClient.post(`/hospital/surgeries/${publicId}/cancel`)).data,

    // --- OT/NABH surgery forms (nurse fill/save/print) ---
    getSurgeryForm: async (admissionId, formType) =>
        (await apiClient.get(`/hospital/surgery-forms/admission/${admissionId}/${formType}`)).data,
    saveSurgeryForm: async (admissionId, formType, data, performedByNurseId) =>
        (await apiClient.post('/hospital/surgery-forms', {
            ipdAdmissionId: admissionId, formType, data,
            ...(performedByNurseId != null ? { performedByNurseId } : {}),
        })).data,
    getSavedFormTypes: async (admissionId) =>
        (await apiClient.get(`/hospital/surgery-forms/admission/${admissionId}`)).data,
};

export default otService;
