import apiClient from './apiService';

/**
 * Patient records attached from outside — lab reports, scans, prescriptions.
 *
 * Files are served from our own server behind authentication, never a public URL, so a download
 * goes through the axios client and its Authorization header rather than a plain link.
 */

// Matches the backend default (`hms.documents.max-file-size=25MB`, see
// UploadedFileValidator). There is no config endpoint to read this from, so — the same
// convention already used for CSV import ("Up to 200 MB" in ImportDataCard.jsx) — it is a single
// constant exported here, so every place that states the limit reads from the same source rather
// than retyping a number that could drift.
export const MAX_FILE_SIZE_MB = 25;
export const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

export const DOCUMENT_TYPES = [
  { value: 'LAB_REPORT', label: 'Lab Report' },
  { value: 'XRAY', label: 'X-Ray' },
  { value: 'SCAN', label: 'Scan' },
  { value: 'PRESCRIPTION', label: 'Prescription' },
  { value: 'DISCHARGE_SUMMARY', label: 'Discharge Summary' },
  { value: 'OTHER', label: 'Other' },
];

export const documentTypeLabel = (type) =>
  DOCUMENT_TYPES.find((t) => t.value === type)?.label || 'Other';

/** Human-readable file size, e.g. "245 KB" or "2.4 MB". */
export const formatFileSize = (bytes) => {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

/**
 * Pulls a usable message out of an axios error.
 *
 * A download request uses `responseType: 'blob'`, so when the server rejects it (403 from the
 * nurse-assignment rule, 400 for a missing file) axios hands back the JSON error body as a Blob
 * rather than parsed data — reading `err.response.data.error` directly would just be `undefined`.
 */
export const extractErrorMessage = async (err, fallback) => {
  const data = err?.response?.data;
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text());
      return parsed?.error || parsed?.message || fallback;
    } catch {
      return fallback;
    }
  }
  return data?.error || err?.message || fallback;
};

const patientDocumentService = {
  list: async (patientPublicId) =>
    (await apiClient.get(`/hospital/patients/${patientPublicId}/documents`)).data?.data ?? [],

  upload: async (patientPublicId, { file, title, documentType, documentDate }) => {
    const form = new FormData();
    form.append('file', file);
    form.append('title', title);
    if (documentType) form.append('documentType', documentType);
    if (documentDate) form.append('documentDate', documentDate);
    const res = await apiClient.post(`/hospital/patients/${patientPublicId}/documents`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // A 25 MB scan over a clinic's connection outlasts the client's 30s default.
      timeout: 120000,
      maxBodyLength: 26 * 1024 * 1024,
      maxContentLength: 26 * 1024 * 1024,
    });
    return res.data?.data;
  },

  /**
   * Returns a Blob. The caller decides whether to open it or save it.
   *
   * `apiService.js` sets a 5 MB response cap globally; without these overrides a 6 MB scan would
   * fail to download with a confusing axios error rather than anything about size.
   */
  download: async (patientPublicId, documentPublicId) =>
    (
      await apiClient.get(
        `/hospital/patients/${patientPublicId}/documents/${documentPublicId}/file`,
        { responseType: 'blob', timeout: 120000, maxContentLength: 26 * 1024 * 1024 }
      )
    ).data,

  remove: async (patientPublicId, documentPublicId) =>
    (await apiClient.delete(`/hospital/patients/${patientPublicId}/documents/${documentPublicId}`))
      .data,
};

export default patientDocumentService;
