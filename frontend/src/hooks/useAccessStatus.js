import { useEffect, useState } from 'react';
import apiClient from '../services/apiService';
import authService from '../services/authService';

/**
 * The signed-in hospital's demo / plan state (GET /hospital/access-status), shared by the navbar
 * label, the sidebar label and the banner so the app asks once, not once per component.
 * Refreshed every five minutes, so a demo that ends while someone is working shows up soon.
 */
const REFRESH_MS = 5 * 60 * 1000;
let current = null;
let loadedAt = 0;
let inFlight = null;
const listeners = new Set();

const isTenantUser = () => {
  const user = authService.getCurrentUser();
  return !!user?.hospitalId && user?.role !== 'SUPER_ADMIN';
};

function load(force = false) {
  if (!isTenantUser()) return Promise.resolve(null);
  if (!force && current && Date.now() - loadedAt < REFRESH_MS) return Promise.resolve(current);
  if (inFlight) return inFlight;
  inFlight = apiClient
    .get('/hospital/access-status')
    .then((res) => {
      current = res.data || null;
      loadedAt = Date.now();
      listeners.forEach((l) => l(current));
      return current;
    })
    // The label is a helper: if it can't load, the app simply shows none.
    .catch(() => current)
    .finally(() => {
      inFlight = null;
    });
  return inFlight;
}

/** Re-read now (after a refusal that says the demo has ended, for example). */
export const refreshAccessStatus = () => load(true);

/** For tests: forget what was loaded. */
export const resetAccessStatus = () => {
  current = null;
  loadedAt = 0;
};

export default function useAccessStatus() {
  const [status, setStatus] = useState(current);
  useEffect(() => {
    listeners.add(setStatus);
    load();
    const timer = setInterval(() => load(true), REFRESH_MS);
    return () => {
      listeners.delete(setStatus);
      clearInterval(timer);
    };
  }, []);
  return status;
}
