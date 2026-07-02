import api from './apiService';

// ─── Lab Orders ──────────────────────────────────────────────────────────────

/**
 * Doctor places a new lab order.
 * @param {{testName, patientId, ipdAdmissionId?, opdId?, notes?, priority?}} data
 */
export const placeLabOrder = (data) => api.post('/hospital/lab/orders', data);

/**
 * Get lab orders with optional filters.
 * @param {{status?, ipdAdmissionId?, patientId?, page?, size?}} params
 */
export const getLabOrders = (params) => api.get('/hospital/lab/orders', { params });

/**
 * Get a single lab order with its result (if available).
 * @param {string} publicId
 */
export const getLabOrder = (publicId) => api.get(`/hospital/lab/orders/${publicId}`);

/**
 * Lab tech marks sample as collected.
 * Transitions order: ORDERED → SAMPLE_COLLECTED
 * @param {string} publicId
 */
export const collectSample = (publicId) => api.put(`/hospital/lab/orders/${publicId}/collect-sample`);

/**
 * Lab tech enters result.
 * Transitions order: SAMPLE_COLLECTED → COMPLETED
 * @param {string} publicId
 * @param {{parameters: string, resultSummary: string, isAbnormal: boolean, isCritical?: boolean}} data
 */
export const enterLabResult = (publicId, data) => api.post(`/hospital/lab/orders/${publicId}/result`, data);

/**
 * Pathologist verifies (signs off on) a result. Requires the is_pathologist capacity flag.
 * Transitions order: COMPLETED → VERIFIED
 * @param {string} publicId
 */
export const verifyLabResult = (publicId) => api.put(`/hospital/lab/orders/${publicId}/verify`);

/**
 * Releases a verified, now-immutable report to the doctor/patient.
 * Transitions order: VERIFIED → RELEASED
 * @param {string} publicId
 */
export const releaseLabResult = (publicId) => api.put(`/hospital/lab/orders/${publicId}/release`);

/**
 * Doctor cancels a lab order (cannot cancel COMPLETED orders).
 * @param {string} publicId
 */
export const cancelLabOrder = (publicId) => api.put(`/hospital/lab/orders/${publicId}/cancel`);

// ─── Lab Technician Management (Admin) ───────────────────────────────────────

/**
 * Admin creates a new lab technician account.
 * @param {{name, email, password, phone?}} data
 */
export const createLabTechnician = (data) => api.post('/hospital/lab-technicians', data);

/**
 * Admin lists all active lab technicians.
 * @param {{search?, page?, size?}} params
 */
export const getLabTechnicians = (params) => api.get('/hospital/lab-technicians', { params });

/**
 * Admin updates a lab technician's name/phone.
 * @param {string} publicId
 * @param {{name, phone?}} data
 */
export const updateLabTechnician = (publicId, data) => api.put(`/hospital/lab-technicians/${publicId}`, data);

/**
 * Admin deactivates a lab technician.
 * @param {string} publicId
 */
export const deactivateLabTechnician = (publicId) => api.delete(`/hospital/lab-technicians/${publicId}`);
