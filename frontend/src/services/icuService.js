import apiClient from './apiService';

/**
 * ICU Phase 2 — read-only capacity views.
 *
 * There is deliberately no write call here. Bed status and admissions keep their existing
 * owners (WardService / hospitalService); ICU only reads what those already record, so the
 * board can never become a second place a bed is marked occupied.
 */
const IcuService = {
  /** Totals, per-unit counts and every bed row, as one snapshot. */
  getBoard: () => apiClient.get('/hospital/icu/board').then((r) => r.data),

  /** Totals and per-unit counts without the bed grid — the dashboard's lighter refresh. */
  getUnits: () => apiClient.get('/hospital/icu/board/units').then((r) => r.data),

  /** ICU-4: every ICU stay for an admission, newest first. Empty when the patient has none. */
  getStaysForAdmission: (ipdId) =>
    apiClient.get(`/hospital/icu/admissions/${ipdId}/stays`).then((r) => r.data),

  /** ICU-5: fluid I/O entries for an admission, newest first, including superseded ones. */
  getIoEntries: (ipdId) =>
    apiClient.get(`/hospital/nurse/io/admission/${ipdId}`).then((r) => r.data),

  /** ICU-5: intake/output totals and net. Computed server-side from the entries, never stored. */
  getIoBalance: (ipdId) =>
    apiClient.get(`/hospital/nurse/io/admission/${ipdId}/balance`).then((r) => r.data),

  recordIoEntry: (payload) => apiClient.post('/hospital/nurse/io', payload).then((r) => r.data),

  /** Append-only: writes a new entry superseding the original, which stays readable. */
  correctIoEntry: (publicId, payload) =>
    apiClient.post(`/hospital/nurse/io/${publicId}/correction`, payload).then((r) => r.data),

  /**
   * ICU-6: every infusion for an admission, running and stopped, newest first.
   *
   * Infusions are drug delivery. They are deliberately NOT part of the I/O balance (D-1) —
   * nothing here reaches `getIoBalance`, and nothing there reaches this.
   */
  getInfusions: (ipdId) =>
    apiClient.get(`/hospital/nurse/infusions/admission/${ipdId}`).then((r) => r.data),

  /** The full rate history of one infusion, newest first, including superseded rows. */
  getInfusionRates: (publicId) =>
    apiClient.get(`/hospital/nurse/infusions/${publicId}/rates`).then((r) => r.data),

  startInfusion: (payload) =>
    apiClient.post('/hospital/nurse/infusions', payload).then((r) => r.data),

  /** Titrating APPENDS a rate; the previous one stays in the history. */
  titrateInfusion: (publicId, payload) =>
    apiClient.post(`/hospital/nurse/infusions/${publicId}/rate`, payload).then((r) => r.data),

  stopInfusion: (publicId, payload) =>
    apiClient.post(`/hospital/nurse/infusions/${publicId}/stop`, payload).then((r) => r.data),

  /** Append-only: the mistaken rate stays readable, struck through. */
  correctInfusionRate: (ratePublicId, payload) =>
    apiClient
      .post(`/hospital/nurse/infusions/rate/${ratePublicId}/correction`, payload)
      .then((r) => r.data),

  /** The rate-unit catalogue. Rates are stored as entered and never converted. */
  getInfusionRateUnits: () =>
    apiClient.get('/hospital/nurse/infusions/rate-units').then((r) => r.data),

  /** Ward unit-type catalogue for the ward form's classification selector. */
  getUnitTypes: () => apiClient.get('/hospital/icu/unit-types').then((r) => r.data),
};

export default IcuService;
