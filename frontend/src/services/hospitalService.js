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

    getPatients: async (search, page = 0, size = 10, date = '', view = '') => {
        let query = `?page=${page}&size=${size}`;
        if (search) query += `&search=${search}`;
        if (view) query += `&view=${view}`;
        if (date) query += `&date=${date}`;
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

    /**
     * Reset doctor password
     */
    resetDoctorPassword: async (id, newPassword) => {
        const response = await apiClient.post(`/hospital/doctors/${id}/reset-password`, { newPassword });
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

    /**
     * Get receptionist by ID
     */
    getReceptionistById: async (id) => {
        const response = await apiClient.get(`/hospital/receptionists/${id}`);
        return response.data;
    },

    /**
     * Update receptionist
     */
    updateReceptionist: async (id, data) => {
        const response = await apiClient.put(`/hospital/receptionists/${id}`, data);
        return response.data;
    },

    /**
     * Reset receptionist password
     */
    resetReceptionistPassword: async (id, newPassword) => {
        const response = await apiClient.post(`/hospital/receptionists/${id}/reset-password`, { newPassword });
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
     * Get pharmacist by ID
     */
    getPharmacistById: async (id) => {
        const response = await apiClient.get(`/hospital/pharmacists/${id}`);
        return response.data;
    },

    /**
     * Update pharmacist
     */
    updatePharmacist: async (id, data) => {
        const response = await apiClient.put(`/hospital/pharmacists/${id}`, data);
        return response.data;
    },

    /**
     * Reset pharmacist password
     */
    resetPharmacistPassword: async (id, newPassword) => {
        const response = await apiClient.post(`/hospital/pharmacists/${id}/reset-password`, { newPassword });
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

    // --- In-Clinic Medicine & Inventory ---
    getCatalogMedicines: async () => {
        const response = await apiClient.get('/hospital/medicines/catalog');
        return response.data;
    },

    addCatalogMedicine: async (data) => {
        const response = await apiClient.post('/hospital/medicines/catalog', data);
        return response.data;
    },

    updateCatalogMedicine: async (id, data) => {
        const response = await apiClient.put(`/hospital/medicines/catalog/${id}`, data);
        return response.data;
    },

    deleteCatalogMedicine: async (id) => {
        const response = await apiClient.delete(`/hospital/medicines/catalog/${id}`);
        return response.data;
    },

    importCatalogCsv: async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await apiClient.post('/hospital/medicines/catalog/import-csv', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return response.data;
    },

    getInventoryMedicines: async () => {
        const response = await apiClient.get('/hospital/medicines/inventory');
        return response.data;
    },

    addInventoryMedicine: async (data) => {
        const response = await apiClient.post('/hospital/medicines/inventory', data);
        return response.data;
    },

    updateInventoryMedicine: async (id, data) => {
        const response = await apiClient.put(`/hospital/medicines/inventory/${id}`, data);
        return response.data;
    },

    deleteInventoryMedicine: async (id) => {
        const response = await apiClient.delete(`/hospital/medicines/inventory/${id}`);
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
            responseType: 'blob',
            timeout: 60000
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
            responseType: 'blob',
            timeout: 60000
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
    getOpds: async (search = '', page = 0, size = 10, date = '', status = '') => {
        let url = `/hospital/opd?page=${page}&size=${size}`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (date) url += `&date=${encodeURIComponent(date)}`;
        if (status) url += `&status=${encodeURIComponent(status)}`;
        const response = await apiClient.get(url);
        return response.data;
    },

    downloadCasePaper: async (opdId) => {
        const response = await apiClient.get(`/hospital/opd/${opdId}/pdf`, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },

    downloadPrescriptionByOpd: async (opdId) => {
        const response = await apiClient.get(`/hospital/doctors/prescription/opd/${opdId}/pdf`, {
            responseType: 'blob',
            timeout: 60000
        });
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
        const response = await apiClient.get(`/api/ipd/${id}`, { timeout: 30000 });
        return response.data;
    },
    getDischargeSummary: async (id) => {
        const response = await apiClient.get(`/api/ipd/${id}/discharge-summary`);
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
    downloadDischargeSummaryPdf: async (id) => {
        const response = await apiClient.get(`/api/ipd/${id}/discharge-summary/pdf`, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },
    getVitalsByAdmission: async (id) => {
        const response = await apiClient.get(`/api/ipd/${id}/vitals`);
        return response.data;
    },
    recordVitals: async (id, payload) => {
        const response = await apiClient.post(`/api/ipd/${id}/vitals`, payload);
        return response.data;
    },
    getIpdBill: async (ipdId) => {
        const response = await apiClient.get(`/hospital/billing/ipd/${ipdId}/bill`);
        return response.data;
    },
    recordAdvance: async (payload) => {
        const response = await apiClient.post(`/hospital/billing/advances`, payload);
        return response.data;
    },
    getAdvanceBalance: async (ipdId) => {
        const response = await apiClient.get(`/hospital/billing/advances/${ipdId}/balance`);
        return response.data;
    },
    requestRefund: async (payload) => {
        const response = await apiClient.post(`/hospital/billing/refunds`, payload);
        return response.data;
    },
    getRefunds: async () => {
        const response = await apiClient.get(`/hospital/billing/refunds`);
        return response.data;
    },
    approveRefund: async (id) => {
        const response = await apiClient.put(`/hospital/billing/refunds/${id}/approve`);
        return response.data;
    },
    rejectRefund: async (id, rejectionReason) => {
        const response = await apiClient.put(`/hospital/billing/refunds/${id}/reject`, { rejectionReason });
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
    administerIpdItems: async (id, items) => {
        const response = await apiClient.post(`/api/ipd/${id}/administer`, { administeredItems: items });
        return response.data;
    },
    administerIpdHospitalItems: async (id, items) => {
        const response = await apiClient.post(`/api/ipd/${id}/administer-hospital-items`, { items });
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
    changeBed: async (ipdId, newBedId) => {
        const response = await apiClient.put(`/api/ipd/${ipdId}/change-bed?newBedId=${newBedId}`);
        return response.data;
    },

    getAvailableBeds: async () => {
        const response = await apiClient.get('/hospital/beds/available');
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
     * Get today's follow-up patients list based on role
     */
    getTodaysFollowUps: async () => {
        const response = await apiClient.get('/hospital/opd/today-followups');
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

    getAnalyticsStats: async () => {
        const response = await apiClient.get('/hospital/stats/analytics');
        return response.data;
    },

    /**
     * Get today's appointments for Overview dashboard
     */
    getTodaysAppointments: async () => {
        const response = await apiClient.get('/hospital/appointments/today');
        return response.data;
    },

    /**
     * Get patient activity for a specific date (OPD, Appointment, IPD)
     * Used by Patient tab's Date toggle view
     */
    getPatientActivityByDate: async (date) => {
        const response = await apiClient.get(`/hospital/stats/patient-activity?date=${date}`);
        return response.data;
    },

    /**
     * Download patient activity PDF report for a specific date
     */
    downloadPatientActivityPdf: async (date) => {
        const response = await apiClient.get(`/hospital/stats/patient-activity/pdf?date=${date}`, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },

    // ========== Audit Log APIs ==========

    getAuditLogs: async (searchTerm, role, limit) => {
        let url = '/hospital/audit-logs';
        const params = [];
        if (searchTerm) params.push(`search=${encodeURIComponent(searchTerm)}`);
        if (role) params.push(`role=${encodeURIComponent(role)}`);
        if (limit) params.push(`limit=${limit}`);
        if (params.length > 0) {
            url += `?${params.join('&')}`;
        }
        const response = await apiClient.get(url);
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

    getCustomFees: async () => {
        const response = await apiClient.get('/hospital/settings/fees/custom');
        return response.data;
    },

    addCustomFee: async (feeData) => {
        const response = await apiClient.post('/hospital/settings/fees/custom', feeData);
        return response.data;
    },

    updateCustomFee: async (id, feeData) => {
        const response = await apiClient.put(`/hospital/settings/fees/custom/${id}`, feeData);
        return response.data;
    },

    deleteCustomFee: async (id) => {
        const response = await apiClient.delete(`/hospital/settings/fees/custom/${id}`);
        return response.data;
    },

    updateBillItems: async (billId, items) => {
        const response = await apiClient.put(`/hospital/billing/${billId}/items`, items);
        return response.data;
    },

    getHospitalOperationsSettings: async () => {
        const response = await apiClient.get('/hospital/settings/operations');
        return response.data;
    },

    updateHospitalOperationsSettings: async (settings) => {
        const response = await apiClient.put('/hospital/settings/operations', settings);
        return response.data;
    },

    /**
     * Get history for specific entity
     */
    getEntityHistory: async (entityType, entityId) => {
        const response = await apiClient.get(`/hospital/audit-logs/${entityType}/${entityId}`);
        return response.data;
    },

    // ========== Support & FAQ APIs ==========
    getPublicFaqs: async () => {
        const response = await apiClient.get('/api/public/faqs');
        return response.data;
    },

    getTickets: async () => {
        const response = await apiClient.get('/hospital/tickets');
        return response.data;
    },

    createTicket: async (ticketData) => {
        const response = await apiClient.post('/hospital/tickets', ticketData);
        return response.data;
    },

    // ========== Hospital Inventory & Patient Bills ==========
    getPatientBills: async (patientId) => {
        const response = await apiClient.get(`/hospital/billing/patient/${patientId}`);
        return response.data;
    },

    searchHospitalInventoryCatalog: async (query) => {
        const response = await apiClient.get(`/hospital/hospital-inventory/search?query=${encodeURIComponent(query)}`);
        return response.data;
    },

    getHospitalInventoryCatalog: async () => {
        const response = await apiClient.get('/hospital/hospital-inventory/catalog');
        return response.data;
    },

    addHospitalInventoryCatalog: async (item) => {
        const response = await apiClient.post('/hospital/hospital-inventory/catalog', item);
        return response.data;
    },

    updateHospitalInventoryCatalog: async (id, item) => {
        const response = await apiClient.put(`/hospital/hospital-inventory/catalog/${id}`, item);
        return response.data;
    },

    deleteHospitalInventoryCatalog: async (id) => {
        const response = await apiClient.delete(`/hospital/hospital-inventory/catalog/${id}`);
        return response.data;
    },

    getHospitalInventory: async () => {
        const response = await apiClient.get('/hospital/hospital-inventory/inventory');
        return response.data;
    },

    addHospitalInventory: async (stock) => {
        const response = await apiClient.post('/hospital/hospital-inventory/inventory', stock);
        return response.data;
    },

    updateHospitalInventory: async (id, stock) => {
        const response = await apiClient.put(`/hospital/hospital-inventory/inventory/${id}`, stock);
        return response.data;
    },

    deleteHospitalInventory: async (id) => {
        const response = await apiClient.delete(`/hospital/hospital-inventory/inventory/${id}`);
        return response.data;
    },

    getHospitalInventoryPurchases: async () => {
        const response = await apiClient.get('/hospital/hospital-inventory/purchases');
        return response.data;
    },

    addHospitalInventoryPurchase: async (purchase) => {
        const response = await apiClient.post('/hospital/hospital-inventory/purchases', purchase);
        return response.data;
    },

    getMedicinePurchases: async () => {
        const response = await apiClient.get('/hospital/medicines/purchases');
        return response.data;
    },

    addMedicinePurchase: async (purchase) => {
        const response = await apiClient.post('/hospital/medicines/purchases', purchase);
        return response.data;
    },

    downloadOpdMedicinesList: async (opdId) => {
        const response = await apiClient.get(`/hospital/patients/opd/${opdId}/medicines/pdf`, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },

    downloadIpdPrescription: async (ipdId) => {
        const response = await apiClient.get(`/hospital/patients/ipd/${ipdId}/prescription/pdf`, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },

    downloadIpdMedicinesList: async (ipdId) => {
        const response = await apiClient.get(`/hospital/patients/ipd/${ipdId}/medicines/pdf`, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },

    downloadPatientsReportPdf: async (date = '') => {
        let url = `/hospital/patients/report/pdf`;
        if (date) url += `?date=${encodeURIComponent(date)}`;
        const response = await apiClient.get(url, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },

    downloadOpdReportPdf: async (date = '', status = '', reportType = '') => {
        let url = `/hospital/opd/report/pdf?`;
        const params = [];
        if (date) params.push(`date=${encodeURIComponent(date)}`);
        if (status) params.push(`status=${encodeURIComponent(status)}`);
        if (reportType) params.push(`reportType=${encodeURIComponent(reportType)}`);
        url += params.join('&');
        const response = await apiClient.get(url, {
            responseType: 'blob',
            timeout: 60000
        });
        return response.data;
    },

    // ========== Medical Records Department (MRD) APIs ==========
    
    getPendingMrdArchives: async () => {
        const response = await apiClient.get('/hospital/mrd/pending');
        return response.data;
    },

    getArchivedMrdRecords: async () => {
        const response = await apiClient.get('/hospital/mrd/archived');
        return response.data;
    },

    archiveMrdRecord: async (payload) => {
        const response = await apiClient.post('/hospital/mrd/archive', payload);
        return response.data;
    },

    // Patient Allergies
    getPatientAllergies: async (patientId) => {
        const response = await apiClient.get(`/hospital/patients/${patientId}/allergies`);
        return response.data;
    },
    addPatientAllergy: async (patientId, payload) => {
        const response = await apiClient.post(`/hospital/patients/${patientId}/allergies`, payload);
        return response.data;
    },
    removePatientAllergy: async (patientId, allergyId) => {
        await apiClient.delete(`/hospital/patients/${patientId}/allergies/${allergyId}`);
    },

    // ========== Nurse Dashboard APIs ==========

    /**
     * Get shift activity summary for the current nurse
     * Returns activities (admissions, discharges, vitals, medications) during a shift window
     * @param {string} shiftStart - ISO datetime string (e.g., "2026-07-01T08:00:00Z")
     * @param {string} shiftEnd - ISO datetime string (e.g., "2026-07-01T16:00:00Z")
     */
    getShiftActivity: async (shiftStart, shiftEnd) => {
        const response = await apiClient.get('/hospital/nurse/dashboard/shift-activity', {
            params: { shiftStart, shiftEnd }
        });
        return response.data;
    },

    // ========== Clinical Assessment APIs (Form 07) ==========

    createClinicalAssessmentDraft: async (payload) => {
        const response = await apiClient.post('/hospital/clinical-assessments', payload);
        return response.data;
    },

    updateClinicalAssessment: async (id, payload) => {
        const response = await apiClient.put(`/hospital/clinical-assessments/${id}`, payload);
        return response.data;
    },

    finalizeClinicalAssessment: async (id, payload) => {
        const response = await apiClient.post(`/hospital/clinical-assessments/${id}/finalize`, payload);
        return response.data;
    },

    amendClinicalAssessment: async (id, payload) => {
        const response = await apiClient.post(`/hospital/clinical-assessments/${id}/amend`, payload);
        return response.data;
    },

    getClinicalAssessmentByAdmission: async (admissionId) => {
        const response = await apiClient.get(`/hospital/clinical-assessments/admission/${admissionId}`);
        return response.data;
    },

    getPatientClinicalHistory: async (patientId) => {
        const response = await apiClient.get(`/hospital/patients/${patientId}/clinical-history`);
        return response.data;
    },

    // ========== Consent APIs (Forms 05 & 01) ==========

    createConsentDraft: async (payload) => {
        const response = await apiClient.post('/hospital/consents', payload);
        return response.data;
    },

    getConsentsByAdmission: async (admissionId) => {
        const response = await apiClient.get(`/hospital/consents/admission/${admissionId}`);
        return response.data;
    },

    getConsentsByPatient: async (patientId) => {
        const response = await apiClient.get(`/hospital/patients/${patientId}/consents`);
        return response.data;
    },

    signConsent: async (id, payload) => {
        const response = await apiClient.post(`/hospital/consents/${id}/sign`, payload);
        return response.data;
    },

    submitConsent: async (id) => {
        const response = await apiClient.post(`/hospital/consents/${id}/submit`);
        return response.data;
    },

    // ========== Risk Assessment APIs (Form 06) ==========

    saveRiskAssessment: async (payload) => {
        const response = await apiClient.post('/hospital/risk-assessments', payload);
        return response.data;
    },

    getRiskAssessmentByAdmission: async (admissionId) => {
        const response = await apiClient.get(`/hospital/risk-assessments/admission/${admissionId}`);
        return response.data;
    },

    reviewRiskAssessment: async (id, payload) => {
        const response = await apiClient.post(`/hospital/risk-assessments/${id}/review`, payload);
        return response.data;
    },

    // ========== Fluid Chart APIs (Form 10) ==========

    recordFluidIntake: async (payload) => {
        const response = await apiClient.post('/hospital/fluid/intake', payload);
        return response.data;
    },

    recordFluidOutput: async (payload) => {
        const response = await apiClient.post('/hospital/fluid/output', payload);
        return response.data;
    },

    getFluidBalance: async (admissionId) => {
        const response = await apiClient.get(`/hospital/fluid/balance/${admissionId}`);
        return response.data;
    },

    getFluidTrends: async (admissionId) => {
        const response = await apiClient.get(`/hospital/fluid/trends/${admissionId}`);
        return response.data;
    },

    // ========== Nursing Progress APIs (Form 08) ==========

    saveNursingProgress: async (payload) => {
        const response = await apiClient.post('/hospital/nursing/progress', payload);
        return response.data;
    },

    submitNursingProgress: async (id) => {
        const response = await apiClient.post(`/hospital/nursing/progress/${id}/submit`);
        return response.data;
    },

    addNursingProcedure: async (progressNoteId, payload) => {
        const response = await apiClient.post(`/hospital/nursing/progress/${progressNoteId}/procedures`, payload);
        return response.data;
    },

    saveShiftHandover: async (payload) => {
        const response = await apiClient.post('/hospital/nursing/handover', payload);
        return response.data;
    },

    getNursingProgressByAdmission: async (admissionId) => {
        const response = await apiClient.get(`/hospital/nursing/progress/admission/${admissionId}`);
        return response.data;
    },

    // ========== Charge Master & Insurance APIs (Form 30) ==========

    getChargeMaster: async () => {
        const response = await apiClient.get('/hospital/charge-master');
        return response.data;
    },

    createChargeMaster: async (data) => {
        const response = await apiClient.post('/hospital/charge-master', data);
        return response.data;
    },

    updateChargeMaster: async (id, data) => {
        const response = await apiClient.put(`/hospital/charge-master/${id}`, data);
        return response.data;
    },

    deleteChargeMaster: async (id) => {
        const response = await apiClient.delete(`/hospital/charge-master/${id}`);
        return response.data;
    },

    postInsurancePreauth: async (payload) => {
        const response = await apiClient.post('/hospital/billing/insurance-preauth', payload);
        return response.data;
    },
};

export default hospitalService;

