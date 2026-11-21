import { useEffect, useSyncExternalStore } from 'react';
import apiClient from '../services/apiService';
import authService from '../services/authService';

/**
 * The sidebar tabs the admin has hidden for the signed-in user's role (Settings → Sidebar).
 * Loaded once per sign-in and shared by every dashboard; reloaded when the admin saves.
 * Display only — screens and APIs keep their own permission checks.
 */
export const SIDEBAR_CHANGED = 'hms:sidebar-changed';

let state = { hidden: new Set(), loadedFor: null };
const listeners = new Set();
const emit = () => listeners.forEach((l) => l());

function keyFor(user) {
  return user ? `${user.hospitalId || ''}:${user.role || ''}:${user.email || user.id || ''}` : null;
}

async function load(user) {
  const key = keyFor(user);
  if (!key || user.role === 'SUPER_ADMIN' || user.hospitalType === 'PHARMACY') {
    state = { hidden: new Set(), loadedFor: key };
    emit();
    return;
  }
  try {
    const res = await apiClient.get('/hospital/settings/sidebar/mine');
    state = { hidden: new Set(res.data?.hidden || []), loadedFor: key };
  } catch {
    // Unreachable settings never take tabs away.
    state = { hidden: new Set(), loadedFor: key };
  }
  emit();
}

export function refreshHiddenTabs() {
  return load(authService.getCurrentUser());
}

export default function useHiddenTabs() {
  const snapshot = useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => state
  );
  useEffect(() => {
    const user = authService.getCurrentUser();
    if (state.loadedFor !== keyFor(user)) void load(user);
    const onChange = () => void load(authService.getCurrentUser());
    window.addEventListener(SIDEBAR_CHANGED, onChange);
    return () => window.removeEventListener(SIDEBAR_CHANGED, onChange);
  }, []);
  return snapshot;
}
