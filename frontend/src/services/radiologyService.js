import api from './apiService';

// ─── Radiology Orders ────────────────────────────────────────────────────────

/**
 * Doctor places a new radiology order.
 * @param {{testName, patientId, ipdAdmissionId?, opdId?, notes?, priority?}} data
 */
export const placeRadiologyOrder = (data) => api.post('/hospital/radiology/orders', data);

/**
 * Get radiology orders with optional filters.
 * @param {{status?, ipdAdmissionId?, patientId?, page?, size?}} params
 */
export const getRadiologyOrders = (params) => api.get('/hospital/radiology/orders', { params });

/**
 * Get a single radiology order with its result (if available).
 * @param {string} publicId
 */
export const getRadiologyOrder = (publicId) => api.get(`/hospital/radiology/orders/${publicId}`);

/**
 * Radiology tech marks study as conducted.
 * Transitions order: ORDERED → STUDY_CONDUCTED
 * @param {string} publicId
 */
export const conductRadiologyStudy = (publicId) => api.put(`/hospital/radiology/orders/${publicId}/conduct-study`);

/**
 * Radiology tech enters result.
 * Transitions order: STUDY_CONDUCTED → COMPLETED
 * @param {string} publicId
 * @param {{findings: string, impression: string, isAbnormal: boolean, resultFileUrl?: string, verifiedByName?: string}} data
 */
export const enterRadiologyResult = (publicId, data) => api.post(`/hospital/radiology/orders/${publicId}/result`, data);

/**
 * Doctor cancels a radiology order (cannot cancel COMPLETED orders).
 * @param {string} publicId
 */
export const cancelRadiologyOrder = (publicId) => api.put(`/hospital/radiology/orders/${publicId}/cancel`);

// ─── Radiology Technician Management (Admin) ───────────────────────────────

/**
 * Admin creates a new radiology technician account.
 * @param {{name, email, password, phone?}} data
 */
export const createRadiologyTechnician = (data) => api.post('/hospital/radiology-technicians', data);

/**
 * Admin lists all active radiology technicians.
 * @param {{search?, page?, size?}} params
 */
export const getRadiologyTechnicians = (params) => api.get('/hospital/radiology-technicians', { params });

/**
 * Admin updates a radiology technician's name/phone.
 * @param {string} publicId
 * @param {{name, phone?}} data
 */
export const updateRadiologyTechnician = (publicId, data) => api.put(`/hospital/radiology-technicians/${publicId}`, data);

/**
 * Admin deactivates a radiology technician.
 * @param {string} publicId
 */
export const deactivateRadiologyTechnician = (publicId) => api.delete(`/hospital/radiology-technicians/${publicId}`);
