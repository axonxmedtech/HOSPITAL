import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import apiClient from './apiService';
import { commitPatientImport, previewPatientImport } from './patientImportService';
const originalAdapter = apiClient.defaults.adapter;
const request = () => ({
  file: new File(['Name\nSynthetic'], 'a.csv'),
  mapping: { Name: 'name' },
  excludedColumns: [],
});
beforeEach(() => {
  sessionStorage.setItem('token', 'test-token');
  sessionStorage.setItem(
    'user',
    JSON.stringify({ role: 'HOSPITAL_ADMIN', hospitalType: 'CLINIC' })
  );
});
afterEach(() => {
  apiClient.defaults.adapter = originalAdapter;
  sessionStorage.clear();
});
it('uses the existing clinic rewrite and JWT interceptor', async () => {
  const adapter = vi.fn(async (config) => ({
    data: { data: { counts: {} } },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }));
  apiClient.defaults.adapter = adapter;
  await previewPatientImport(request());
  expect(adapter.mock.calls[0][0].url).toBe('/clinic/patients/import/preview');
  expect(adapter.mock.calls[0][0].headers.Authorization).toBe('Bearer test-token');
});
it('uses existing 401 session clearing', async () => {
  apiClient.defaults.adapter = async (config) => {
    throw { config, response: { status: 401 } };
  };
  await expect(previewPatientImport(request())).rejects.toMatchObject({
    response: { status: 401 },
  });
  expect(sessionStorage.getItem('token')).toBeNull();
  expect(sessionStorage.getItem('user')).toBeNull();
});
it('does not retry commit on network or server failure', async () => {
  for (const response of [undefined, { status: 503 }]) {
    const adapter = vi.fn(async (config) => {
      throw { config, response };
    });
    apiClient.defaults.adapter = adapter;
    await expect(commitPatientImport(request())).rejects.toBeDefined();
    expect(adapter).toHaveBeenCalledTimes(1);
  }
});
