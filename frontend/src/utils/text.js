/**
 * Title-case a string: capitalize the first letter of every word.
 * e.g. "hospital one" -> "Hospital One". Safe for null/undefined.
 */
export const titleCase = (str) => {
  if (!str) return str || '';
  return String(str)
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
};

/**
 * A stored code as words people read: "DISCHARGE_PLANNED" -> "Discharge planned",
 * "ipd-admission" -> "Ipd admission". Leaves ordinary text alone; safe for null/undefined.
 */
export const humanizeCode = (code) => {
  if (code === null || code === undefined || code === '') return '';
  const s = String(code);
  if (!/^[A-Za-z0-9]+([_-][A-Za-z0-9]+)*$/.test(s) || !/[_-]|^[A-Z0-9]+$/.test(s)) return s;
  const words = s
    .replace(/[_-]+/g, ' ')
    .toLowerCase()
    .replace(/\b(ipd|opd|icu|ot|upi|pdf|mar|gst|id)\b/g, (w) => w.toUpperCase());
  return words.charAt(0).toUpperCase() + words.slice(1);
};

export default titleCase;
