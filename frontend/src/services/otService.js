import apiClient from './apiService';

const otService = {
    dashboard: async () => (await apiClient.get('/ot/dashboard')).data,
    lookups: async () => (await apiClient.get('/ot/lookups')).data,
    bookings: async (params = {}) => (await apiClient.get('/ot/bookings', { params })).data,
    createBooking: async (payload) => (await apiClient.post('/ot/bookings', payload)).data,
    updateBooking: async (id, payload) => (await apiClient.put(`/ot/bookings/${id}`, payload)).data,
    details: async (id) => (await apiClient.get(`/ot/bookings/${id}`)).data,
    updatePreOp: async (id, payload) => (await apiClient.put(`/ot/bookings/${id}/pre-op-checklist`, payload)).data,
    updateWho: async (id, payload) => (await apiClient.put(`/ot/bookings/${id}/who-checklist`, payload)).data,
    updateStatus: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/status`, payload)).data,
    assignStaff: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/staff`, payload)).data,
    assignEquipment: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/equipment`, payload)).data,
    assignInstrument: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/instruments`, payload)).data,
    addAnesthesia: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/anesthesia`, payload)).data,
    addImplant: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/implants`, payload)).data,
    addRecovery: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/recovery`, payload)).data,
    addConsumable: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/consumables`, payload)).data,
    addCharge: async (id, payload) => (await apiClient.post(`/ot/bookings/${id}/charges`, payload)).data,
    saveNotes: async (id, payload) => (await apiClient.put(`/ot/bookings/${id}/notes`, payload)).data,
    complete: async (id) => (await apiClient.post(`/ot/bookings/${id}/complete`)).data,
    rooms: async () => (await apiClient.get('/ot/rooms')).data,
    saveRoom: async (payload) => (await apiClient.post('/ot/rooms', payload)).data,
    surgeries: async () => (await apiClient.get('/ot/surgeries')).data,
    saveSurgery: async (payload) => (await apiClient.post('/ot/surgeries', payload)).data,
    equipment: async () => (await apiClient.get('/ot/equipment')).data,
    saveEquipment: async (payload) => (await apiClient.post('/ot/equipment', payload)).data,
    instruments: async () => (await apiClient.get('/ot/instrument-sets')).data,
    saveInstrument: async (payload) => (await apiClient.post('/ot/instrument-sets', payload)).data,
    reports: async () => (await apiClient.get('/ot/reports')).data,
};

export default otService;
