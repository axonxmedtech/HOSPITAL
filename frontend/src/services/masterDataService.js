import apiClient from './apiService';

const masterDataService = {
  // Lab Tests
  searchLabTests: (q = '') =>
    apiClient.get(`/hospital/master/lab-tests/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createLabTest: (data) => apiClient.post('/hospital/master/lab-tests', data).then(r => r.data),
  updateLabTest: (id, data) => apiClient.put(`/hospital/master/lab-tests/${id}`, data).then(r => r.data),
  deactivateLabTest: (id) => apiClient.delete(`/hospital/master/lab-tests/${id}`),

  // Radiology Tests
  searchRadiologyTests: (q = '') =>
    apiClient.get(`/hospital/master/radiology-tests/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createRadiologyTest: (data) => apiClient.post('/hospital/master/radiology-tests', data).then(r => r.data),
  updateRadiologyTest: (id, data) => apiClient.put(`/hospital/master/radiology-tests/${id}`, data).then(r => r.data),
  deactivateRadiologyTest: (id) => apiClient.delete(`/hospital/master/radiology-tests/${id}`),

  // Allergies
  searchAllergies: (q = '') =>
    apiClient.get(`/hospital/master/allergies/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createAllergy: (data) => apiClient.post('/hospital/master/allergies', data).then(r => r.data),
  deactivateAllergy: (id) => apiClient.delete(`/hospital/master/allergies/${id}`),

  // Diagnoses
  searchDiagnoses: (q = '') =>
    apiClient.get(`/hospital/master/diagnoses/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createDiagnosis: (data) => apiClient.post('/hospital/master/diagnoses', data).then(r => r.data),
  deactivateDiagnosis: (id) => apiClient.delete(`/hospital/master/diagnoses/${id}`),

  // Procedures
  searchProcedures: (q = '') =>
    apiClient.get(`/hospital/master/procedures/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createProcedure: (data) => apiClient.post('/hospital/master/procedures', data).then(r => r.data),
  updateProcedure: (id, data) => apiClient.put(`/hospital/master/procedures/${id}`, data).then(r => r.data),
  deactivateProcedure: (id) => apiClient.delete(`/hospital/master/procedures/${id}`),

  // Medicines (from pharmacy master)
  searchMedicines: (q = '') =>
    apiClient.get(`/hospital/master/medicines/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),

  // Seed defaults
  seedDefaults: () => apiClient.post('/hospital/master/seed').then(r => r.data),
};

export default masterDataService;
