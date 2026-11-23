import apiClient from '../apiService';

const API_URL = '/pharmacy/purchases';

const purchaseApi = {
  create: async (data) => {
    const response = await apiClient.post(API_URL, data);
    return response.data;
  },
  /** Edit a DRAFT purchase. */
  update: async (id, data) => {
    const response = await apiClient.put(`${API_URL}/${id}`, data);
    return response.data;
  },
  getAll: async (page = 0, size = 10) => {
    const p = Number.isNaN(parseInt(page, 10)) ? 0 : page;
    const s = Number.isNaN(parseInt(size, 10)) ? 10 : size;
    const response = await apiClient.get(`${API_URL}?page=${p}&size=${s}`);
    return response.data;
  },
  getById: async (id) => {
    const response = await apiClient.get(`${API_URL}/${id}`);
    return response.data;
  },
  postInvoice: async (id) => {
    const response = await apiClient.post(`${API_URL}/${id}/post`);
    return response.data;
  },
  /** Discards a draft, or reverses a posted purchase while all its stock is still on the shelf. */
  cancel: async (id, reason) => {
    const response = await apiClient.post(`${API_URL}/${id}/cancel`, { reason });
    return response.data;
  },
  /** { amount, method, reference, paidOn } — a payment made to the supplier. */
  recordPayment: async (id, payment) => {
    const response = await apiClient.post(`${API_URL}/${id}/payments`, payment);
    return response.data;
  },
  getPayments: async (id) => {
    const response = await apiClient.get(`${API_URL}/${id}/payments`);
    return response.data;
  },
};

export default purchaseApi;
