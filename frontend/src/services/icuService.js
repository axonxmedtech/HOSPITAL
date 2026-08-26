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

  /** Ward unit-type catalogue for the ward form's classification selector. */
  getUnitTypes: () => apiClient.get('/hospital/icu/unit-types').then((r) => r.data),
};

export default IcuService;
