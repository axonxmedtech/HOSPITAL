import { useSyncExternalStore } from 'react';
import apiClient from '../services/apiService';

/**
 * The signed-in user's help progress: guide steps ticked, tours finished or skipped, and whether
 * the welcome tour was offered. Saved on this device at once, and to the server shortly after so
 * it follows the user to any computer. If the server cannot be reached, help keeps working from
 * this device's copy.
 *
 * Shape: { guides: { [guideId]: [stepId…] }, tours: { [tourId]: 'done' | 'skipped' }, welcome: 'shown' }
 */

const EMPTY = { guides: {}, tours: {} };
let state = EMPTY;
let ownerKey = null;
let loadedFor = null; // the user whose server copy has been fetched (or failed to)
const listeners = new Set();
let saveTimer = null;

const emit = () => listeners.forEach((l) => l());

const keyFor = (user) =>
  user ? `hms.help.guides.${user.hospitalId ?? 'p'}.${user.userId ?? user.email ?? 'u'}` : null;

/** Phase 2 stored only guide ticks under this key; it is read as the guides part. */
function readLocal(key) {
  try {
    const raw = JSON.parse(window.localStorage.getItem(key) || '{}') || {};
    if (raw.guides || raw.tours) return normalise(raw);
    return normalise({ guides: raw });
  } catch {
    return EMPTY;
  }
}

function writeLocal(key, value) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* private window: kept for this visit only */
  }
}

function normalise(v) {
  const guides = {};
  for (const [g, steps] of Object.entries(v?.guides || {})) {
    if (Array.isArray(steps)) guides[g] = steps.filter((s) => typeof s === 'string');
  }
  const tours = {};
  for (const [t, s] of Object.entries(v?.tours || {}))
    if (s === 'done' || s === 'skipped') tours[t] = s;
  return { guides, tours, ...(v?.welcome ? { welcome: v.welcome } : {}) };
}

/** Two copies combined: a step ticked on either is ticked; a finished tour stays finished. */
export function merge(a, b) {
  const x = normalise(a);
  const y = normalise(b);
  const guides = { ...x.guides };
  for (const [g, steps] of Object.entries(y.guides)) {
    guides[g] = [...new Set([...(guides[g] || []), ...steps])];
  }
  const tours = { ...x.tours };
  for (const [t, s] of Object.entries(y.tours)) if (tours[t] !== 'done') tours[t] = s;
  const welcome = x.welcome || y.welcome;
  return { guides, tours, ...(welcome ? { welcome } : {}) };
}

function scheduleSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    apiClient.put('/help/progress', state).catch(() => {
      /* offline or refused: this device's copy is kept and sent with the next change */
    });
  }, 800);
}

function set(next) {
  state = normalise(next);
  if (ownerKey) writeLocal(ownerKey, state);
  emit();
  scheduleSave();
}

export const progressStore = {
  get: () => state,
  subscribe: (l) => {
    listeners.add(l);
    return () => listeners.delete(l);
  },
  /** Loads this user's progress: this device's copy at once, then merged with the server's. */
  load: async (user) => {
    const key = keyFor(user);
    if (!key || (key === ownerKey && loadedFor === key)) return;
    if (key !== ownerKey) {
      ownerKey = key;
      state = readLocal(key);
      emit();
    }
    try {
      const res = await apiClient.get('/help/progress');
      if (ownerKey !== key) return; // signed out or switched user meanwhile
      loadedFor = key;
      const combined = merge(res?.data, state);
      const changed = JSON.stringify(combined) !== JSON.stringify(normalise(res?.data));
      state = combined;
      writeLocal(key, state);
      emit();
      if (changed) scheduleSave();
    } catch {
      /* the server copy is optional */
      if (ownerKey === key) {
        loadedFor = key;
        emit();
      }
    }
  },
  /** True once this user's server copy was fetched (or could not be), so offers are not repeated. */
  isLoaded: () => loadedFor !== null && loadedFor === ownerKey,
  toggleStep: (guideId, stepId) => {
    const cur = state.guides[guideId] || [];
    set({
      ...state,
      guides: {
        ...state.guides,
        [guideId]: cur.includes(stepId) ? cur.filter((s) => s !== stepId) : [...cur, stepId],
      },
    });
  },
  markTour: (tourId, outcome) => set({ ...state, tours: { ...state.tours, [tourId]: outcome } }),
  markWelcome: () => set({ ...state, welcome: 'shown' }),
  /** Forgets the signed-out user's progress (their copies stay on the server and this device). */
  clear: () => {
    clearTimeout(saveTimer);
    state = EMPTY;
    ownerKey = null;
    loadedFor = null;
    emit();
  },
};

export function useHelpProgress() {
  return useSyncExternalStore(progressStore.subscribe, progressStore.get, progressStore.get);
}
