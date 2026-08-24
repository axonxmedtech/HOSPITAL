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

  /** Ward unit-type catalogue for the ward form's classification selector. */
  getUnitTypes: () => apiClient.get('/hospital/icu/unit-types').then((r) => r.data),
};

export default IcuService;
