import { useSyncExternalStore } from 'react';

/**
 * Open/closed state of the phone navigation drawer.
 *
 * Every dashboard renders <Navbar> and <Sidebar> as siblings. On phones the sidebar is hidden,
 * so the Navbar's menu button opens it as a drawer instead. This tiny shared store connects the
 * two without a provider or any per-dashboard wiring. Only one dashboard is mounted at a time.
 */
let isOpen = false;
const listeners = new Set();

const setOpen = (value) => {
  if (isOpen === value) return;
  isOpen = value;
  listeners.forEach((listener) => listener());
};

export const mobileNav = {
  open: () => setOpen(true),
  close: () => setOpen(false),
  subscribe: (listener) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  getSnapshot: () => isOpen,
};

/** Tailwind's `md` breakpoint: below it the desktop sidebar is hidden. */
export const DESKTOP_NAV_QUERY = '(min-width: 768px)';

/** True when the viewport is below the md breakpoint (where the sidebar is hidden). */
export const isPhoneViewport = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  !window.matchMedia(DESKTOP_NAV_QUERY).matches;

/** Subscribes a component to the drawer's open state. */
export const useMobileNavOpen = () =>
  useSyncExternalStore(mobileNav.subscribe, mobileNav.getSnapshot, mobileNav.getSnapshot);
