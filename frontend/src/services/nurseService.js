import apiClient from './apiService';

/**
 * nurseService - nurse-facing API calls (Phase 1 Nurse module).
 * Mirrors the hospitalService axios-wrapper style; interceptors (auth token,
 * 401 handling) are shared via apiClient.
 */
const nurseService = {
    // --- Shift (availability) ---

    /** Whether the current nurse is on shift. */
    getShiftStatus: async () => {
        const response = await apiClient.get('/hospital/nurse/shift/status');
        return response.data; // { onShift: boolean }
    },

    /** Start shift → nurse becomes available. */
    startShift: async () => {
        const response = await apiClient.post('/hospital/nurse/shift/start');
        return response.data;
    },

    /** End shift → nurse becomes unavailable. */
    endShift: async () => {
        const response = await apiClient.post('/hospital/nurse/shift/end');
        return response.data;
    },

    /** Dashboard aggregates: assigned patient count + recent patients. */
    getDashboard: async () => {
        const response = await apiClient.get('/hospital/nurse/dashboard');
        return response.data;
    },

    /** Active IPD admissions assigned to the logged-in nurse. */
    getMyPatients: async () => {
        const response = await apiClient.get('/hospital/nurse/my-patients');
        return response.data;
    },

    /** Composite read-only bedside view for one admission (403 if not assigned). */
    getPatientDetail: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/patients/${admissionId}`);
        return response.data;
    },

    // --- Admission form (complete the IPD admission) ---

    /** Get the admission form (pre-filled draft if not saved yet). */
    getAdmissionForm: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/admission-form/admission/${admissionId}`);
        return response.data;
    },

    /** Save the admission form. */
    saveAdmissionForm: async (admissionId, form) => {
        const response = await apiClient.post(`/hospital/nurse/admission-form/admission/${admissionId}`, form);
        return response.data;
    },

    /** Mark the patient admitted after the signed form is collected. */
    confirmAdmission: async (admissionId) => {
        const response = await apiClient.post(`/hospital/nurse/admission-form/admission/${admissionId}/confirm`);
        return response.data;
    },

    // --- Initial assessment (nurse-captured clinical fields) ---

    getInitialAssessment: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/initial-assessment/admission/${admissionId}`);
        return response.data;
    },

    saveInitialAssessment: async (admissionId, body) => {
        const response = await apiClient.post(`/hospital/nurse/initial-assessment/admission/${admissionId}`, body);
        return response.data;
    },

    // --- Vulnerability assessment ---

    getVulnerabilityAssessment: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/vulnerability-assessment/admission/${admissionId}`);
        return response.data;
    },

    saveVulnerabilityAssessment: async (admissionId, body) => {
        const response = await apiClient.post(`/hospital/nurse/vulnerability-assessment/admission/${admissionId}`, body);
        return response.data;
    },

    // --- Sugar chart ---

    getSugarEntries: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/sugar-chart/admission/${admissionId}`);
        return response.data;
    },
    createSugarEntry: async (payload) => {
        const response = await apiClient.post('/hospital/nurse/sugar-chart', payload);
        return response.data;
    },
    updateSugarEntry: async (publicId, payload) => {
        const response = await apiClient.put(`/hospital/nurse/sugar-chart/${publicId}`, payload);
        return response.data;
    },
    deleteSugarEntry: async (publicId) => {
        const response = await apiClient.delete(`/hospital/nurse/sugar-chart/${publicId}`);
        return response.data;
    },

    // --- Vitals (M4) ---

    /** Record a new vitals reading for an admission. */
    createVitals: async (payload) => {
        const response = await apiClient.post('/hospital/nurse/vitals', payload);
        return response.data;
    },

    /** Vitals timeline for an admission (newest first). */
    getVitals: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/vitals/admission/${admissionId}`);
        return response.data;
    },

    /** Edit a vitals reading (author only, within the edit window). */
    updateVitals: async (publicId, payload) => {
        const response = await apiClient.put(`/hospital/nurse/vitals/${publicId}`, payload);
        return response.data;
    },

    // --- Nursing Notes (M5) ---

    /** Add a nursing note for an admission. */
    createNote: async (payload) => {
        const response = await apiClient.post('/hospital/nurse/notes', payload);
        return response.data;
    },

    /** Notes timeline for an admission (newest first). */
    getNotes: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/notes/admission/${admissionId}`);
        return response.data;
    },

    /** Edit a note (author only, within the edit window). */
    updateNote: async (publicId, payload) => {
        const response = await apiClient.put(`/hospital/nurse/notes/${publicId}`, payload);
        return response.data;
    },

    /** Soft-delete a note (author only, within the edit window). */
    deleteNote: async (publicId) => {
        const response = await apiClient.delete(`/hospital/nurse/notes/${publicId}`);
        return response.data;
    },

    // --- Medication Administration (M6) ---

    /** Active prescriptions for an admission. */
    getMedicationPrescriptions: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/medication/admission/${admissionId}/prescriptions`);
        return response.data;
    },

    /** Record administration of a prescribed dose. */
    recordMedication: async (payload) => {
        const response = await apiClient.post('/hospital/nurse/medication', payload);
        return response.data;
    },

    /** Medication administration history for an admission. */
    getMedicationHistory: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/medication/admission/${admissionId}`);
        return response.data;
    },

    /** Full medication chart: all orders (active/stopped/completed) + reminders. */
    getMedicationChart: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nurse/medication/admission/${admissionId}/chart`);
        return response.data;
    },

    // --- Manual Tasks (M7) ---

    /** Get assigned tasks for the logged-in nurse. */
    getMyTasks: async () => {
        const response = await apiClient.get('/hospital/nurse-tasks/my');
        return response.data;
    },

    /** Update status of a nurse task. */
    updateTaskStatus: async (publicId, payload) => {
        const response = await apiClient.put(`/hospital/nurse-tasks/${publicId}/status`, payload);
        return response.data;
    },
};

export default nurseService;
