import apiService from './apiService';

const feedbackService = {
  issueToken: (data) => apiService.post('/hospital/feedback/tokens', data),
  getFeedback: () => apiService.get('/hospital/feedback'),
  getComplaints: () => apiService.get('/hospital/feedback/complaints'),
  resolveComplaint: (id, resolution) =>
    apiService.put(`/hospital/feedback/complaints/${id}/resolve`, { resolution }),

  // Public, unauthenticated — token itself resolves patient/hospital server-side.
  submitPublicFeedback: (token, data) =>
    apiService.post(`/api/public/feedback/${token}`, data),
};

export default feedbackService;
