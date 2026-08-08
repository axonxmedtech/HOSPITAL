import apiClient from './apiService';

/**
 * ICU stays for an admission.
 *
 * Read-only by design. Moving a patient into or out of ICU is an ordinary bed change and goes
 * through the existing IPD endpoint — a second transfer path would be a parallel implementation of
 * the thing that keeps the whole stay on one bill.
 */
const icuService = {
  /** Every ICU stint on this admission, oldest first. Empty when the patient never went to ICU. */
  stays: async (admissionId) =>
    (await apiClient.get(`/hospital/icu/stays/${admissionId}`)).data?.data ?? [],
};

export default icuService;
