import apiClient from './apiService';

/**
 * Hospital Service - API calls for hospital-related operations
 * 
 * This service handles API calls for:
 * - Patients
 * - Doctors
 * - Appointments
 * - Billing
 * 
 * All requests automatically include JWT token via apiClient interceptor.
 * 
 * @author HMS Team
 * @version Phase-1
 */

const hospitalService = {
    // ========== Patient APIs ==========

    /**
     * Get all patients for the current hospital
     * Supports optional search query
     */
    getPatients: async (search, page = 0, size = 10, view = '') => {
        let query = `?page=${page}&size=${size}`;
        if (search) query += `&search=${search}`;
        if (view) query += `&view=${view}`;
        const response = await apiClient.get(`/hospital/patients${query}`);
        return response.data; // Now returns { content: [...], totalElements: ..., totalPages: ... }
    },

    /**
     * Add a new patient
     */
    addPatient: async (patientData) => {
        const response = await apiClient.post('/hospital/patients', patientData);
        return response.data;
    },

    /**
     * Update existing patient
     */
    updatePatient: async (id, patientData) => {
        const response = await apiClient.put(`/hospital/patients/${id}`, patientData);
        return response.data;
    },

    /**
     * Get patient by ID
     */
    getPatientById: async (id) => {
        const response = await apiClient.get(`/hospital/patients/${id}`);
        return response.data;
    },

    /**
     * Delete patient
     */
    deletePatient: async (id, reason) => {
        const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
        const response = await apiClient.delete(`/hospital/patients/${id}${query}`);
        return response.data;
    },

    /**
     * Update patient status
     */
    updatePatientStatus: async (patientId, status) => {
        const response = await apiClient.put(`/hospital/patients/${patientId}/status?status=${status}`);
        return response.data;
    },

    /**
     * Start consultation for a patient
     */
    startConsultation: async (patientId) => {
        const response = await apiClient.post(`/hospital/patients/${patientId}/start-consultation`);
        return response.data;
    },

    /**
     * Get complete patient consultation details
     * Includes demographics, medical history, and current visit info
     */
    getPatientConsultationDetails: async (patientId) => {
        const response = await apiClient.get(`/hospital/patients/${patientId}/consultation-details`);
        return response.data;
    },

    /**
     * Get latest consultation details (Prescription view)
     */
    getLatestPrescription: async (patientId) => {
        const response = await apiClient.get(`/hospital/patients/${patientId}/latest-prescription`);
        return response.data;
    },

    // ========== Doctor APIs ==========

    /**
     * Get all doctors for the current hospital
     * Supports optional search query
     */
    getDoctors: async (search, page = 0, size = 10) => {
        const query = search ? `?search=${search}&page=${page}&size=${size}` : `?page=${page}&size=${size}`;
        const response = await apiClient.get(`/hospital/doctors${query}`);
        return response.data; // Now returns { content: [...], totalElements: ..., totalPages: ... }
    },

    /**
     * Add a new doctor
     */
    addDoctor: async (doctorData) => {
        const response = await apiClient.post('/hospital/doctors', doctorData);
        return response.data;
    },

    /**
     * Update existing doctor
     */
    updateDoctor: async (id, doctorData) => {
        const response = await apiClient.put(`/hospital/doctors/${id}`, doctorData);
        return response.data;
    },

    /**
     * Get doctor by ID
     */
    getDoctorById: async (id) => {
        const response = await apiClient.get(`/hospital/doctors/${id}`);
        return response.data;
    },

    /**
     * Delete doctor
     */
    deleteDoctor: async (id, reason) => {
        const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
        const response = await apiClient.delete(`/hospital/doctors/${id}${query}`);
        return response.data;
    },

    // ========== Receptionist APIs ==========

    /**
     * Get all receptionists for the current hospital
     */
    getReceptionists: async (search, page = 0, size = 10) => {
        const query = search ? `?search=${search}&page=${page}&size=${size}` : `?page=${page}&size=${size}`;
        const response = await apiClient.get(`/hospital/receptionists${query}`);
        return response.data; // Now returns { content: [...], totalElements: ..., totalPages: ... }
    },

    /**
     * Add a new receptionist
     */
    addReceptionist: async (receptionistData) => {
        const response = await apiClient.post('/hospital/receptionists', receptionistData);
        return response.data;
    },

    /**
     * Delete receptionist
     */
    deleteReceptionist: async (id, reason) => {
        const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
        const response = await apiClient.delete(`/hospital/receptionists/${id}${query}`);
        return response.data;
    },

    // ========== Pharmacist APIs ==========

    /**
     * Get all pharmacists
     */
    getPharmacists: async (search, page = 0, size = 10) => {
        const query = search ? `?search=${search}&page=${page}&size=${size}` : `?page=${page}&size=${size}`;
        const response = await apiClient.get(`/hospital/pharmacists${query}`);
        return response.data;
    },

    /**
     * Add a new pharmacist
     */
    addPharmacist: async (data) => {
        const response = await apiClient.post('/hospital/pharmacists', data);
        return response.data;
    },

    /**
     * Delete pharmacist
     */
    deletePharmacist: async (id, reason) => {
        const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
        const response = await apiClient.delete(`/hospital/pharmacists/${id}${query}`);
        return response.data;
    },

    /**
     * Get pending prescriptions for pharmacy
     */
    getPendingPrescriptions: async () => {
        const response = await apiClient.get('/hospital/pharmacy/prescriptions/pending');
        return response.data;
    },

    /**
     * Get pharmacy inventory
     */
    getInventory: async () => {
        const response = await apiClient.get('/hospital/pharmacy/inventory');
        return response.data;
    },

    /**
     * Dispense medicine for a prescription
     */
    dispenseMedicine: async (prescriptionId) => {
        const response = await apiClient.post(`/hospital/pharmacy/dispense/${prescriptionId}`);
        return response.data;
    },

    /**
     * Submit Consultation (Doctor only)
     */
    submitConsultation: async (data) => {
        const response = await apiClient.post('/hospital/doctors/consultation', data);
        return response.data;
    },

    /**
     * Search medicines
     */
    searchMedicines: async (query) => {
        const response = await apiClient.get(`/hospital/medicines/search?query=${query}`);
        return response.data;
    },

    /**
     * Get Consultation Details (Prescription)
     */
    getConsultationDetails: async (appointmentId) => {
        const response = await apiClient.get(`/hospital/doctors/consultation/${appointmentId}`);
        return response.data;
    },

    /**
     * Download Prescription PDF
     */
    downloadPrescription: async (appointmentId) => {
        return apiClient.get(`/hospital/doctors/prescription/${appointmentId}/pdf`, {
            responseType: 'blob'
        }).then(response => response.data);
    },

    // --- Billing ---
    getBills: async (search = '', page = 0, size = 10, status) => {
        let url = `/hospital/billing?page=${page}&size=${size}`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (status) url += `&status=${encodeURIComponent(status)}`;
        const response = await apiClient.get(url);
        return response.data; // Returns Page object with { content: [...], totalElements: ..., totalPages: ... }
    },

    updateBillStatus: async (id, status, paymentMethod, paymentReference) => {
        let url = `/hospital/billing/${id}/status?status=${status}`;
        if (paymentMethod) url += `&paymentMethod=${encodeURIComponent(paymentMethod)}`;
        if (paymentReference) url += `&paymentReference=${encodeURIComponent(paymentReference)}`;
        const response = await apiClient.put(url);
        return response.data;
    },

    downloadReceipt: async (id) => {
        const response = await apiClient.get(`/hospital/billing/${id}/pdf`, {
            responseType: 'blob'
        });
        return response.data;
    },

    // ========== Appointment APIs ==========

    /**
     * Get all appointments for the current hospital
     */
    getAppointments: async (searchTerm = '', page = 0, size = 10, view) => {
        let url = `/hospital/appointments?page=${page}&size=${size}`;
        if (searchTerm) url += `&search=${encodeURIComponent(searchTerm)}`;
        if (view) url += `&view=${view}`;
        const response = await apiClient.get(url);
        return response.data; // Now returns { content: [...], totalElements: ..., totalPages: ... }
    },

    /**
     * Create a new appointment
     */
    createAppointment: async (appointmentData) => {
        const response = await apiClient.post('/hospital/appointments', appointmentData);
        return response.data;
    },

    // ========== OPD / Case APIs ==========

    /**
     * Create an OPD (case) record. Expects CreateOpdRequest payload.
     */
    createOpd: async (opdData) => {
        const response = await apiClient.post('/hospital/opd', opdData);
        return response.data;
    },

    /**
     * Get queue entries for a doctor (today)
     */
    getDoctorQueue: async (doctorId) => {
        // If doctorId is provided, use explicit endpoint (receptionist view)
        if (doctorId) {
            const response = await apiClient.get(`/hospital/opd/queue/doctor/${doctorId}`);
            return response.data;
        }
        // Otherwise use authenticated doctor's queue
        const response = await apiClient.get(`/hospital/opd/queue/my`);
        return response.data;
    },

    /**
     * Get paginated OPD / cases (Receptionist view)
     */
    getOpds: async (search = '', page = 0, size = 10) => {
        let url = `/hospital/opd?page=${page}&size=${size}`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        const response = await apiClient.get(url);
        return response.data;
    },

    downloadCasePaper: async (opdId) => {
        const response = await apiClient.get(`/hospital/opd/${opdId}/pdf`, { responseType: 'blob' });
        return response.data;
    },

    downloadPrescriptionByOpd: async (opdId) => {
        const response = await apiClient.get(`/hospital/doctors/prescription/opd/${opdId}/pdf`, { responseType: 'blob' });
        return response.data;
    },
    createIpdAdmission: async (payload) => {
        const response = await apiClient.post('/api/ipd/admit', payload);
        return response.data;
    },
    getIpdAdmissions: async (page = 0, size = 10, search = '') => {
        const response = await apiClient.get('/api/ipd', { params: { page, size, search } });
        return response.data;
    },
    getMyIpdAdmissions: async () => {
        const response = await apiClient.get('/api/ipd/my');
        return response.data;
    },
    getAdmittedIpdAdmissions: async () => {
        const response = await apiClient.get('/api/ipd/admissions');
        return response.data; // returns array of DTOs
    },
    getIpdDetails: async (id) => {
        const response = await apiClient.get(`/api/ipd/${id}`);
        return response.data;
    },
    planDischarge: async (id, payload) => {
        const response = await apiClient.post(`/api/ipd/${id}/plan-discharge`, payload);
        return response.data;
    },
    confirmDischarge: async (id) => {
        const response = await apiClient.post(`/api/ipd/${id}/confirm-discharge`);
        return response.data;
    },
    getIpdBill: async (ipdId) => {
        const response = await apiClient.get(`/hospital/billing/ipd/${ipdId}/bill`);
        return response.data;
    },
    payBilling: async (billingId, payload) => {
        const response = await apiClient.post(`/hospital/billing/${billingId}/pay`, payload);
        return response.data;
    },
    addIpdFollowup: async (id, payload) => {
        const response = await apiClient.post(`/api/ipd/${id}/followup`, payload);
        return response.data;
    },
    addIpdPrescription: async (id, payload) => {
        const response = await apiClient.post(`/api/ipd/${id}/prescriptions`, payload);
        return response.data;
    },
    stopPrescription: async (prescriptionId) => {
        const response = await apiClient.put(`/api/ipd/prescriptions/${prescriptionId}/stop`);
        return response.data;
    },

    /**
     * Get hospital-wide queue for today
     */
    getHospitalQueue: async () => {
        const response = await apiClient.get(`/hospital/opd/queue`);
        return response.data;
    },

    /**
     * Get appointments for a specific doctor
     */
    getAppointmentsByDoctor: async (doctorId, view) => {
        const query = view ? `?view=${view}` : '';
        const response = await apiClient.get(`/hospital/appointments/doctor/${doctorId}${query}`);
        return response.data;
    },

    /**
     * Get appointment by ID
     */
    getAppointmentById: async (id) => {
        const response = await apiClient.get(`/hospital/appointments/${id}`);
        return response.data;
    },

    /**
     * Delete appointment
     */
    deleteAppointment: async (id, reason) => {
        const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
        const response = await apiClient.delete(`/hospital/appointments/${id}${query}`);
        return response.data;
    },

    /**
     * Get appointments for a specific patient (History)
     */
    getAppointmentsByPatient: async (patientId) => {
        const response = await apiClient.get(`/hospital/appointments/patient/${patientId}`);
        return response.data;
    },

    /**
     * Get dashboard stats
     */
    getAppointmentStats: async () => {
        const response = await apiClient.get('/hospital/appointments/stats');
        return response.data;
    },

    /**
     * Get appointments for the logged-in doctor
     */
    getMyAppointments: async (view, search, page = 0, size = 10) => {
        let query = `?page=${page}&size=${size}`;
        if (view) query += `&view=${view}`;
        if (search) query += `&search=${encodeURIComponent(search)}`;
        const response = await apiClient.get(`/hospital/appointments/my-appointments${query}`);
        return response.data;
    },

    /**
     * Update appointment status (Legacy)
     * @param {number} id 
     * @param {string} status 
     * @param {string} reason
     */
    updateAppointmentStatus: async (id, status, reason) => {
        const response = await apiClient.put(`/hospital/appointments/${id}/status`, { status, reason });
        return response.data;
    },

    /**
     * Update appointment details (Status & Notes)
     * @param {number} id 
     * @param {string} status 
     * @param {string} notes 
     */
    updateAppointment: async (id, status, notes) => {
        const response = await apiClient.put(`/hospital/appointments/${id}`, { status, notes });
        return response.data;
    },

    // ========== Billing APIs ==========

    /**
     * Get all billing records for the current hospital
     */
    getBillingRecords: async (page = 0, size = 10, status) => {
        let url = `/hospital/billing?page=${page}&size=${size}`;
        if (status) url += `&status=${encodeURIComponent(status)}`;
        const response = await apiClient.get(url);
        return response.data; // Returns Page object
    },

    /**
     * Create a new billing record
     */
    createBilling: async (billingData) => {
        const response = await apiClient.post('/hospital/billing', billingData);
        return response.data;
    },

    /**
     * Get billing record by ID
     */
    getBillingById: async (id) => {
        const response = await apiClient.get(`/hospital/billing/${id}`);
        return response.data;
    },

    // ========== Overview Dashboard APIs ==========

    /**
     * Get dashboard statistics for Hospital Admin Overview
     * Returns total patients, doctors, and today's appointments count
     */
    getGlobalStats: async () => {
        const response = await apiClient.get('/hospital/stats');
        return response.data;
    },

    /**
     * Get today's appointments for Overview dashboard
     */
    getTodaysAppointments: async () => {
        const response = await apiClient.get('/hospital/appointments/today');
        return response.data;
    },

    // ========== Audit Log APIs ==========

    /**
     * Get recent activity for dashboard
     */
    getAuditLogs: async (searchTerm) => {
        const response = await apiClient.get(`/hospital/audit-logs${searchTerm ? `?search=${encodeURIComponent(searchTerm)}` : ''}`);
        return response.data;
    },

    // ========== Hospital Settings / Fees ==========
    getHospitalFees: async () => {
        const response = await apiClient.get('/hospital/settings/fees');
        return response.data;
    },

    updateHospitalFees: async (fees) => {
        const response = await apiClient.put('/hospital/settings/fees', fees);
        return response.data;
    },

    /**
     * Get history for specific entity
     */
    getEntityHistory: async (entityType, entityId) => {
        const response = await apiClient.get(`/hospital/audit-logs/${entityType}/${entityId}`);
        return response.data;
    },
};

export default hospitalService;
