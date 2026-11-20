import { useSyncExternalStore } from 'react';

/**
 * Shared state for the help system: whether the help panel or the command bar is open, what the
 * panel should show, and the current dashboard's navigation (registered by the Sidebar) so help
 * answers and the command bar can take the user straight to a screen.
 */
let state = {
  panelOpen: false,
  panelView: 'assistant', // assistant | topics | shortcuts | setup
  question: null, // a question to answer as soon as the panel opens
  answerId: null, // an answer to show as soon as the panel opens
  paletteOpen: false,
  nav: null, // { tabs, activeTab, onTabChange }
};
const listeners = new Set();
const emit = () => listeners.forEach((l) => l());
const set = (patch) => {
  state = { ...state, ...patch };
  emit();
};

export const helpStore = {
  get: () => state,
  subscribe: (l) => {
    listeners.add(l);
    return () => listeners.delete(l);
  },
  openPanel: (view = 'assistant', extra = {}) =>
    set({
      panelOpen: true,
      panelView: view,
      paletteOpen: false,
      question: null,
      answerId: null,
      ...extra,
    }),
  closePanel: () => set({ panelOpen: false, question: null, answerId: null }),
  togglePanel: () => (state.panelOpen ? helpStore.closePanel() : helpStore.openPanel()),
  openPalette: () => set({ paletteOpen: true }),
  closePalette: () => set({ paletteOpen: false }),
  setView: (panelView) => set({ panelView }),
  /** Called by the Sidebar on every render with the dashboard's own navigation. */
  registerNav: (nav) => {
    const cur = state.nav;
    if (
      cur &&
      cur.tabs === nav.tabs &&
      cur.activeTab === nav.activeTab &&
      cur.onTabChange === nav.onTabChange
    )
      return;
    set({ nav });
  },
  clearNav: (onTabChange) => {
    if (state.nav?.onTabChange === onTabChange) set({ nav: null });
  },
  /** Every screen the current user can open, flattened from grouped sidebars. */
  destinations: () => flattenTabs(state.nav?.tabs || []),
  /** Opens a screen of the current dashboard. Returns false if this dashboard has no such screen. */
  goTo: (tabId) => {
    const nav = state.nav;
    if (!nav?.onTabChange || !flattenTabs(nav.tabs || []).some((t) => t.id === tabId)) return false;
    nav.onTabChange(tabId);
    return true;
  },
};

export function flattenTabs(tabs) {
  const out = [];
  for (const t of tabs || []) {
    if (!t) continue;
    const subs = t.subItems || t.children || t.items;
    if (Array.isArray(subs) && subs.length) {
      for (const s of subs) if (s?.id) out.push({ id: s.id, label: s.label, group: t.label });
    } else if (t.id && !String(t.id).startsWith('group-')) {
      out.push({ id: t.id, label: t.label });
    }
  }
  return out;
}

export function useHelpState() {
  return useSyncExternalStore(helpStore.subscribe, helpStore.get, helpStore.get);
}
