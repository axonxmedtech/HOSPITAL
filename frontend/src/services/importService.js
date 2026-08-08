import apiClient from './apiService';

/**
 * Legacy patient data import and hospital data export.
 *
 * The import API is deliberately stateless: the spreadsheet is re-sent for preview and again for
 * commit rather than being stored between steps. That means the patient file — which is full PHI —
 * never lands on the server's filesystem, so there is no temp file to leak or forget to delete.
 * The cost is that the caller must hold on to the File object across the wizard, which is why every
 * call below takes it.
 */
const importService = {
  /** Target fields a column can be mapped to, with their labels. */
  fields: async (entityType = 'PATIENT') => {
    const res = await apiClient.get('/hospital/imports/fields', { params: { entityType } });
    return res.data?.data ?? [];
  },

  /**
   * Parses the file and returns its headers, a suggested mapping, and sample rows.
   * Writes nothing.
   */
  upload: async (file, entityType = 'PATIENT') => {
    const form = new FormData();
    form.append('file', file);
    form.append('entityType', entityType);
    const res = await apiClient.post('/hospital/imports/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
      maxContentLength: Infinity,
      maxBodyLength: Infinity,
    });
    return res.data?.data;
  },

  /**
   * Dry run. Reports exactly what a commit would do and writes nothing at all — this is what
   * makes it safe to point an admin at a 8,000-row file they have never seen before.
   */
  preview: async (file, mapping) => {
    const form = new FormData();
    form.append('file', file);
    Object.entries(mapping).forEach(([header, field]) => {
      if (field) form.append(header, field);
    });
    const res = await apiClient.post('/hospital/imports/preview', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
      maxContentLength: Infinity,
      maxBodyLength: Infinity,
    });
    return res.data?.data;
  },

  /** Applies the file for real. Returns the batch, including its counts and public id. */
  commit: async (file, mapping) => {
    const form = new FormData();
    form.append('file', file);
    Object.entries(mapping).forEach(([header, field]) => {
      if (field) form.append(header, field);
    });
    const res = await apiClient.post('/hospital/imports/commit', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000,
      maxContentLength: Infinity,
      maxBodyLength: Infinity,
    });
    return res.data?.data;
  },

  /** Past imports, newest first. */
  list: async () => {
    const res = await apiClient.get('/hospital/imports');
    return res.data?.data ?? [];
  },

  /** Reverses a batch. Refused by the server if the patients have clinical activity. */
  undo: async (publicId) => {
    const res = await apiClient.post(`/hospital/imports/${publicId}/undo`);
    return res.data?.data;
  },

  /**
   * Downloads the failed rows as a CSV shaped for re-upload: original headers, only the rows that
   * failed, plus an _error column. Correcting just those rows leaves everything that already
   * imported untouched.
   */
  downloadErrors: async (publicId, headers) => {
    const res = await apiClient.get(`/hospital/imports/${publicId}/errors.csv`, {
      params: { headers },
      paramsSerializer: { indexes: null },
      responseType: 'blob',
      timeout: 120000,
      maxContentLength: Infinity,
    });
    return res.data;
  },

  /** Downloads every patient record for this hospital as an .xlsx workbook. */
  exportPatients: async () => {
    const res = await apiClient.get('/hospital/exports/patients.xlsx', {
      responseType: 'blob',
      timeout: 600000,
      maxContentLength: Infinity,
    });
    return res;
  },
};

/** Triggers a browser download for a blob response. */
export const saveBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export default importService;
