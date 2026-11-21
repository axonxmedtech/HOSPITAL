/**
 * Standardized date formatting utility functions.
 */

const pad2 = (n) => String(n).padStart(2, '0');

/**
 * "YYYY-MM-DD" for the given moment in the user's local timezone.
 *
 * Use this — never `toISOString().slice(0, 10)` — for "today" defaults and date filters.
 * toISOString() is UTC, so in India (UTC+5:30) it returns *yesterday* from midnight until
 * 5:30 AM, and night-shift purchases, attendance and lists defaulted to the wrong day.
 */
export const localIsoDate = (date = new Date()) =>
  `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;

export const formatDate = (dateString) => {
  if (!dateString) return '-';
  try {
    const d = new Date(dateString);
    if (Number.isNaN(d.getTime())) return '-';
    return d.toLocaleDateString();
  } catch {
    return '-';
  }
};

export const formatDateTime = (dateString) => {
  if (!dateString) return '-';
  try {
    const d = new Date(dateString);
    if (Number.isNaN(d.getTime())) return '-';
    return d.toLocaleString();
  } catch {
    return '-';
  }
};

export const formatTime = (dateString) => {
  if (!dateString) return '-';
  try {
    const d = new Date(dateString);
    if (Number.isNaN(d.getTime())) return '-';
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '-';
  }
};

const parse = (v) => {
  if (!v) return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
};

/** "20 Nov 2026" — the easy-to-read day used on records screens. */
export const formatDay = (v) => {
  const d = parse(v);
  return d
    ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';
};

/** "20 Nov 2026, 9:31 pm" — day and time without seconds, for records screens. */
export const formatWhen = (v) => {
  const d = parse(v);
  if (!d) return '—';
  return `${formatDay(d)}, ${d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })}`;
};

/** "1 day" / "3 days". */
export const formatDays = (n) => {
  if (n == null) return '—';
  return Number(n) === 1 ? '1 day' : `${n} days`;
};
