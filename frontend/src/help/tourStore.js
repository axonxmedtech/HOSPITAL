import { helpStore } from './helpStore';
import { progressStore } from './progressStore';

/** Which tour is running and on which step; TourRunner draws it. */
let state = { tour: null, index: 0 };
const listeners = new Set();
const set = (patch) => {
  state = { ...state, ...patch };
  listeners.forEach((l) => l());
};

export const tourStore = {
  get: () => state,
  subscribe: (l) => {
    listeners.add(l);
    return () => listeners.delete(l);
  },
  start: (tour) => {
    if (!tour?.steps?.length) return;
    helpStore.closePanel();
    helpStore.closePalette();
    set({ tour, index: 0 });
  },
  next: () => {
    const { tour, index } = state;
    if (!tour) return;
    if (index >= tour.steps.length - 1) tourStore.end('done');
    else set({ index: index + 1 });
  },
  back: () => state.tour && state.index > 0 && set({ index: state.index - 1 }),
  end: (outcome = 'skipped') => {
    const { tour } = state;
    if (tour) progressStore.markTour(tour.id, outcome);
    set({ tour: null, index: 0 });
  },
};

const visible = (el) => {
  if (!el) return false;
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden';
};

const outsideHelp = (el) => !el.closest('[aria-label="Help and support"], [data-tour-card]');

/** The element a step points at, or null. Exported for tests. */
export function findTarget(target) {
  if (!target) return null;
  if (typeof target === 'string') {
    return (
      [...document.querySelectorAll(target)].find((el) => visible(el) && outsideHelp(el)) || null
    );
  }
  const want = target.text.trim().toLowerCase();
  const label = (el) =>
    (el.getAttribute('aria-label') || el.textContent || '')
      .replace(/\s+/g, ' ')
      .trim()
      .toLowerCase();
  return (
    [...document.querySelectorAll('button, a, [role="button"]')].find(
      (el) => label(el) === want && visible(el) && outsideHelp(el)
    ) || null
  );
}
