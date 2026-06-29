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
};

export default otService;
