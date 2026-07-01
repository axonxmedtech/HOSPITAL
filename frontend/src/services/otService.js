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
};

export default otService;
