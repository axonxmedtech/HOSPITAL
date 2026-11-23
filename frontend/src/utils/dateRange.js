/**
 * Date windows for records lists: quick picks (Today, last 7 / 30 / 90 days, All time) or a
 * custom from–to, as ISO dates ('' = open-ended).
 */
import { localIsoDate } from './date';

export const RANGE_PRESETS = [
  { id: 'today', label: 'Today' },
  { id: '7d', label: 'Last 7 days' },
  { id: '30d', label: 'Last 30 days' },
  { id: '90d', label: 'Last 90 days' },
  { id: 'all', label: 'All time' },
  { id: 'custom', label: 'Custom dates' },
];

const daysAgo = (n) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return localIsoDate(d);
};

/** The from/to dates a preset stands for. */
export function rangeFor(preset, current = {}) {
  const today = localIsoDate();
  switch (preset) {
    case 'today':
      return { preset, from: today, to: today };
    case '7d':
      return { preset, from: daysAgo(6), to: today };
    case '30d':
      return { preset, from: daysAgo(29), to: today };
    case '90d':
      return { preset, from: daysAgo(89), to: today };
    case 'custom':
      return { preset, from: current.from || daysAgo(29), to: current.to || today };
    default:
      return { preset: 'all', from: '', to: '' };
  }
}

/** Query params for the API: only what is set. */
export function rangeParams(range) {
  const out = {};
  if (rangeIsBackwards(range)) return out; // the picker shows the problem; don't send it
  if (range?.from) out.from = range.from;
  if (range?.to) out.to = range.to;
  return out;
}

/** True when a custom range ends before it starts. */
export const rangeIsBackwards = (range) =>
  Boolean(range?.from && range?.to && range.to < range.from);
