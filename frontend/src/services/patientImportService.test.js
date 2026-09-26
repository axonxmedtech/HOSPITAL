import { beforeEach, describe, expect, it, vi } from 'vitest';
import apiClient from './apiService';
import authService from './authService';
import {
  canImportPatients,
  commitPatientImport,
  errorBatchId,
  getPatientImportStatus,
  importErrorMessage,
  previewPatientImport,
} from './patientImportService';
vi.mock('./apiService', () => ({ default: { post: vi.fn(), get: vi.fn() } }));
vi.mock('./authService', () => ({ default: { getCurrentUser: vi.fn() } }));
beforeEach(() => {
  vi.clearAllMocks();
  authService.getCurrentUser.mockReturnValue({ role: 'HOSPITAL_ADMIN', hospitalType: 'HOSPITAL' });
});
describe('import HTTP contract', () => {
  it.each([previewPatientImport, commitPatientImport])(
    'sends only the original file and supported choices',
    async (call) => {
      const file = new File(['Name,Extra\nPerson,private'], 'a.csv');
      apiClient.post.mockResolvedValue({ data: { data: { counts: { created: 1 } } } });
      await call({
        file,
        mapping: { Name: 'name' },
        excludedColumns: ['Extra'],
        sheetName: 'Patients',
        hospitalId: 123,
        createdBy: 'ignored',
      });
      const [path, form, config] = apiClient.post.mock.calls[0];
      expect(path).toMatch(/^\/hospital\/patients\/import\/(preview|commit)$/);
      expect([...form.keys()]).toEqual(['file', 'mapping', 'excludedColumns', 'sheetName']);
      expect(form.get('file')).toBe(file);
      expect(JSON.parse(form.get('excludedColumns'))).toEqual(['Extra']);
      expect(config.headers['Content-Type']).toBeUndefined();
      expect(config.timeout).toBe(0);
    }
  );
  it('uses the public batch ID for status', async () => {
    apiClient.get.mockResolvedValue({ data: { data: { status: 'COMPLETED' } } });
    expect(await getPatientImportStatus('batch-id')).toEqual({ status: 'COMPLETED' });
    expect(apiClient.get).toHaveBeenCalledWith('/hospital/patients/import/batch-id');
  });
  it('permits hospital/clinic administrators and denies pharmacy and other roles', async () => {
    for (const hospitalType of ['HOSPITAL', 'CLINIC'])
      expect(canImportPatients({ role: 'HOSPITAL_ADMIN', hospitalType })).toBe(true);
    for (const user of [
      { role: 'DOCTOR', hospitalType: 'HOSPITAL' },
      { role: 'HOSPITAL_ADMIN', hospitalType: 'PHARMACY' },
      null,
    ]) {
      expect(canImportPatients(user)).toBe(false);
      authService.getCurrentUser.mockReturnValue(user);
      await expect(commitPatientImport({})).rejects.toThrow();
    }
    expect(apiClient.post).not.toHaveBeenCalled();
  });
  it.each([400, 401, 403, 404, 409, 411, 413, 500, undefined])(
    'sanitizes status %s errors',
    (status) => {
      const error = {
        response: { status, data: { message: 'SECRET PHI STACK' } },
        message: 'SECRET PHI STACK',
      };
      expect(importErrorMessage(error)).not.toContain('SECRET');
      expect(importErrorMessage(error, true)).toContain('Some rows may already have been imported');
    }
  );
  it('accepts only a public batch identifier from error details', () => {
    expect(
      errorBatchId({ response: { data: { errors: { batchPublicId: 'a'.repeat(36) } } } })
    ).toBe('a'.repeat(36));
    expect(
      errorBatchId({ response: { data: { errors: { batchPublicId: '<script>patient name' } } } })
    ).toBeNull();
  });
});
