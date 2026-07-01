import apiService from './apiService';

const otService = {
  getBookings: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings`),

  scheduleBooking: (admissionId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings`, payload),

  getChecklist: (admissionId, bookingId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/checklist`),

  updateStatus: (admissionId, bookingId, status) =>
    apiService.put(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/status`, null, { params: { status } }),

  signChecklist: (admissionId, bookingId, payload) =>
    apiService.put(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/checklist`, payload),

  getOperationRecord: (admissionId, bookingId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/operation-record`),

  saveOperationRecord: (admissionId, bookingId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/operation-record`, payload),

  updateOperationRecord: (admissionId, bookingId, payload) =>
    apiService.put(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/operation-record`, payload),

  finalizeOperationRecord: (admissionId, bookingId) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/operation-record/finalize`),

  getAnaesthesiaRecord: (admissionId, bookingId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/anaesthesia-record`),

  startAnaesthesiaRecord: (admissionId, bookingId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/anaesthesia-record`, payload),

  updateAnaesthesiaRecord: (admissionId, bookingId, payload) =>
    apiService.put(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/anaesthesia-record`, payload),

  completeAnaesthesiaRecord: (admissionId, bookingId) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/anaesthesia-record/complete`),

  getPacuRecord: (admissionId, bookingId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/pacu-record`),

  startPacuRecord: (admissionId, bookingId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/pacu-record`, payload),

  updatePacuRecord: (admissionId, bookingId, payload) =>
    apiService.put(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/pacu-record`, payload),

  transferPacuRecord: (admissionId, bookingId) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/pacu-record/transfer`),

  getClinicalHandover: (admissionId, bookingId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/handover`),

  initiateHandover: (admissionId, bookingId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/handover`, payload),

  updateHandover: (admissionId, bookingId, payload) =>
    apiService.put(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/handover`, payload),

  acceptHandover: (admissionId, bookingId) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/handover/accept`),

  getPostopOrders: (admissionId, bookingId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/postop-orders`),

  savePostopOrders: (admissionId, bookingId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/postop-orders`, payload),

  signPostopOrders: (admissionId, bookingId) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/postop-orders/sign`),

  getInstrumentCount: (admissionId, bookingId) =>
    apiService.get(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/instrument-count`),

  saveInstrumentCount: (admissionId, bookingId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/instrument-count`, payload),

  resolveInstrumentCount: (admissionId, bookingId, payload) =>
    apiService.post(`/api/ipd/${admissionId}/ot/bookings/${bookingId}/instrument-count/resolve`, payload),
};

export default otService;
