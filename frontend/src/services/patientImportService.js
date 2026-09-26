import apiClient from './apiService';
import authService from './authService';

export const canImportPatients = (user) =>
  user?.role === 'HOSPITAL_ADMIN' &&
  ['HOSPITAL', 'CLINIC'].includes(user.hospitalType || 'HOSPITAL');

const assertAccess = () => {
  if (!canImportPatients(authService.getCurrentUser())) {
    throw new Error('Patient import requires a hospital or clinic administrator.');
  }
};

const upload = async (action, { file, mapping, excludedColumns, sheetName }) => {
  assertAccess();
  const form = new FormData();
  form.append('file', file);
  form.append('mapping', JSON.stringify(mapping));
  form.append('excludedColumns', JSON.stringify(excludedColumns));
  if (sheetName) form.append('sheetName', sheetName);
  const response = await apiClient.post(`/hospital/patients/import/${action}`, form, {
    // Let the browser supply the multipart boundary and length. Imports are synchronous;
    // the shared client's 30-second timeout is inappropriate for this operation.
    headers: { 'Content-Type': undefined },
    timeout: 0,
  });
  return response.data.data;
};

export const previewPatientImport = (request) => upload('preview', request);
export const commitPatientImport = (request) => upload('commit', request);
export const getPatientImportStatus = async (publicId) => {
  assertAccess();
  const response = await apiClient.get(`/hospital/patients/import/${encodeURIComponent(publicId)}`);
  return response.data.data;
};

// Never render arbitrary HTTP bodies or exception messages: they can contain patient data.
export const importErrorMessage = (error, committing = false) => {
  const status = error.response?.status;
  const messages = {
    400: 'The file or column choices could not be accepted. Check the headers, required name mapping and file contents, then preview again.',
    401: 'Your session has expired. Please sign in again.',
    403: 'You do not have permission to import patients for this hospital or clinic.',
    404: 'This import could not be found for your hospital or clinic.',
    409: 'This file already has an import, or another import is starting. Check its status before trying again.',
    411: 'The upload length could not be determined. Select the local file again and retry the preview.',
    413: 'The upload is too large. Select a CSV or XLSX file no larger than 50 MiB.',
  };
  const message =
    messages[status] ||
    (status >= 500
      ? 'The server could not finish this request.'
      : 'The connection was interrupted or the server could not be reached.');
  return committing
    ? `${message} Some rows may already have been imported. Check the batch status or preview the same original file before considering another import.`
    : message;
};

export const errorBatchId = (error) => {
  const id = error.response?.data?.errors?.batchPublicId;
  return typeof id === 'string' && /^[a-f0-9-]{36}$/i.test(id) ? id : null;
};
