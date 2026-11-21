import { useEffect, useState } from 'react';
import { localIsoDate } from '../utils/date';
import DateSelect from './DateSelect';

/**
 * The one filter bar every records list uses: which records (Current / Discharged / All …), a
 * date window with quick picks, and a search box. Filtering happens on the server, so paging
 * and totals stay right however many years of records a hospital has.
 *
 * `range` is { preset, from, to } with ISO dates ('' = open-ended).
 */

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

/** Debounced search text, so typing doesn't fire a request per key. */
export function useDebounced(value, ms = 350) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

const control =
  'h-[38px] px-3 rounded-lg border border-gray-300 bg-white text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-primary-500';

/** True when a custom range ends before it starts. */
export const rangeIsBackwards = (range) =>
  Boolean(range?.from && range?.to && range.to < range.from);

/**
 * Date-window picker: quick picks (Today, last 7 / 30 / 90 days, All time) or two dates.
 * Used by every records list, so dates are chosen the same way everywhere.
 */
export function DateRangeFilter({ range, onChange, label = 'Date range' }) {
  const custom = range?.preset === 'custom';
  return (
    <>
      <select
        aria-label={label}
        title={label}
        value={range?.preset || 'all'}
        onChange={(e) => onChange(rangeFor(e.target.value, range))}
        className={`${control} pr-8`}
      >
        {RANGE_PRESETS.map((p) => (
          <option key={p.id} value={p.id}>
            {p.label}
          </option>
        ))}
      </select>
      {custom && (
        <div className="flex flex-wrap items-center gap-2" aria-label="Custom dates">
          <span className="text-xs font-semibold text-gray-500">From</span>
          <DateSelect filter value={range.from} onChange={(v) => onChange({ ...range, from: v })} />
          <span className="text-xs font-semibold text-gray-500">To</span>
          <DateSelect filter value={range.to} onChange={(v) => onChange({ ...range, to: v })} />
          {rangeIsBackwards(range) && (
            <span role="alert" className="text-xs font-semibold text-red-600">
              The “To” date is before the “From” date.
            </span>
          )}
        </div>
      )}
    </>
  );
}

export default function RecordsFilterBar({
  views,
  view,
  onViewChange,
  range,
  onRangeChange,
  search,
  onSearchChange,
  searchPlaceholder = 'Search name, phone or number…',
  dateLabel,
  children,
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {views?.length > 0 && (
        <div
          className="inline-flex rounded-lg border border-gray-300 bg-white p-0.5"
          role="tablist"
        >
          {views.map((v) => (
            <button
              key={v.id}
              type="button"
              role="tab"
              aria-selected={view === v.id}
              onClick={() => onViewChange(v.id)}
              className={`h-[32px] px-3 rounded-md text-sm font-semibold whitespace-nowrap ${
                view === v.id ? 'bg-sky-700 text-white' : 'text-gray-700 hover:bg-gray-50'
              }`}
            >
              {v.label}
              {typeof v.count === 'number' && (
                <span
                  className={`ml-1.5 text-xs ${view === v.id ? 'text-sky-100' : 'text-gray-400'}`}
                >
                  {v.count}
                </span>
              )}
            </button>
          ))}
        </div>
      )}

      {onRangeChange && (
        <DateRangeFilter range={range} onChange={onRangeChange} label={dateLabel} />
      )}

      {onSearchChange && (
        <input
          type="search"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder={searchPlaceholder}
          aria-label="Search"
          className={`${control} min-w-[14rem] flex-1`}
        />
      )}

      {children}
    </div>
  );
}
