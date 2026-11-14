import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios, { AxiosError, CanceledError } from 'axios';
import apiClient, { MAX_GET_RETRIES, TIMEOUT_MESSAGE } from './apiService';

// Drive the real interceptors through a fake transport: no network, full axios pipeline.
const httpError = (config, status, data = {}) =>
  new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_RESPONSE', config, {}, {
    status,
    data,
    headers: {},
    config,
  });
const networkError = (config) => new AxiosError('Network Error', 'ERR_NETWORK', config, {});
const timeoutError = (config) =>
  new AxiosError('timeout of 30000ms exceeded', 'ECONNABORTED', config, {});
const ok = (config, data = { ok: true }) => ({ status: 200, data, headers: {}, config });

let adapter;
const originalAdapter = apiClient.defaults.adapter;

// Settle a request whose retries wait on (fake) timers, returning the outcome.
const settle = async (promise) => {
  const outcome = promise.then(
    (value) => ({ value }),
    (error) => ({ error })
  );
  await vi.runAllTimersAsync();
  return outcome;
};

beforeEach(() => {
  vi.useFakeTimers();
  adapter = vi.fn();
  apiClient.defaults.adapter = adapter;
  sessionStorage.clear();
  // The 401 path reads only localStorage.lastPortal; give each test a clean, complete stand-in.
  vi.stubGlobal('localStorage', { getItem: vi.fn(() => null) });
  vi.stubGlobal('location', { href: '/hospital/admin' });
});

afterEach(() => {
  apiClient.defaults.adapter = originalAdapter;
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('apiService retry policy', () => {
  it('retries a GET on network failure, then gives up with the original error', async () => {
    adapter.mockImplementation((config) => Promise.reject(networkError(config)));

    const { error } = await settle(apiClient.get('/hospital/patients'));

    expect(adapter).toHaveBeenCalledTimes(1 + MAX_GET_RETRIES);
    expect(error).toBeInstanceOf(AxiosError);
    expect(error.code).toBe('ERR_NETWORK');
  });

  it('recovers when a GET hits a 503 and the retry succeeds', async () => {
    adapter
      .mockImplementationOnce((config) => Promise.reject(httpError(config, 503)))
      .mockImplementationOnce((config) => Promise.resolve(ok(config)));

    const { value } = await settle(apiClient.get('/hospital/patients'));

    expect(adapter).toHaveBeenCalledTimes(2);
    expect(value.data).toEqual({ ok: true });
  });

  it.each([502, 504])('retries a GET on gateway status %i', async (status) => {
    adapter.mockImplementation((config) => Promise.reject(httpError(config, status)));

    await settle(apiClient.get('/hospital/patients'));

    expect(adapter).toHaveBeenCalledTimes(1 + MAX_GET_RETRIES);
  });

  it('does not retry a deterministic 500', async () => {
    adapter.mockImplementation((config) => Promise.reject(httpError(config, 500)));

    const { error } = await settle(apiClient.get('/hospital/patients'));

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(error.response.status).toBe(500);
  });

  it('does not retry a timeout, and keeps it an AxiosError with a readable message', async () => {
    adapter.mockImplementation((config) => Promise.reject(timeoutError(config)));

    const { error } = await settle(apiClient.get('/hospital/patients'));

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(error).toBeInstanceOf(AxiosError);
    expect(error.code).toBe('ECONNABORTED');
    expect(error.message).toBe(TIMEOUT_MESSAGE);
  });

  it('does not retry a request the caller cancelled', async () => {
    // CanceledError(message, config, request) — config must be attached, as axios does.
    adapter.mockImplementation((config) => Promise.reject(new CanceledError(undefined, config, {})));

    const { error } = await settle(apiClient.get('/hospital/patients'));

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(axios.isCancel(error)).toBe(true);
  });

  it('never retries a non-GET request', async () => {
    adapter.mockImplementation((config) => Promise.reject(networkError(config)));

    await settle(apiClient.post('/hospital/patients', {}));

    expect(adapter).toHaveBeenCalledTimes(1);
  });
});

describe('apiService 401 handling', () => {
  it('clears the session and redirects to the tenant login on a 401', async () => {
    sessionStorage.setItem('token', 't');
    sessionStorage.setItem('user', JSON.stringify({ hospitalType: 'CLINIC' }));
    adapter.mockImplementation((config) => Promise.reject(httpError(config, 401)));

    const { error } = await settle(apiClient.get('/hospital/patients'));

    expect(error.response.status).toBe(401);
    expect(sessionStorage.getItem('token')).toBeNull();
    expect(globalThis.location.href).toBe('/login/clinic');
  });

  it('leaves the login form to show a 401 from the login endpoint itself', async () => {
    sessionStorage.setItem('token', 't');
    adapter.mockImplementation((config) => Promise.reject(httpError(config, 401)));

    await settle(apiClient.post('/auth/login', {}));

    expect(sessionStorage.getItem('token')).toBe('t');
    expect(globalThis.location.href).toBe('/hospital/admin');
  });
});
