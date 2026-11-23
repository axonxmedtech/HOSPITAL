/**
 * Strength, dose and duration of one prescribed medicine, as structured fields instead of free
 * text: "Paracetamol 500 mg — 1/2 tablet — Twice daily — 3 Days".
 *
 *  - Strength (required): a number and a unit. Not every medicine is in mg, so the unit is a
 *    choice (mg, mcg, g, ml, mg/5 ml, IU, %, units).
 *  - Dose: how much per time, fractions included (1/2, 1 1/2), in the form's own unit (tablet,
 *    capsule, ml, drops, puff …).
 *  - Duration (required): a number of days, weeks or months.
 *
 * The stored record keeps three readable strings (strength, dosage, duration) so existing lists,
 * prints and history keep working; these helpers convert between them and the fields.
 */

export const STRENGTH_UNITS = ['mg', 'mcg', 'g', 'ml', 'mg/5 ml', 'IU', '%', 'units'];
export const DOSE_AMOUNTS = ['1/4', '1/2', '3/4', '1', '1 1/2', '2', '2 1/2', '3', '4', '5', '10'];
export const DOSE_UNITS = [
  'tablet',
  'capsule',
  'ml',
  'drops',
  'puff',
  'sachet',
  'application',
  'injection',
  'unit',
];
export const DURATION_UNITS = ['Days', 'Weeks', 'Months'];

const num = (v) => {
  const n = Number(String(v ?? '').trim());
  return Number.isFinite(n) && n > 0;
};

/** Trimmed, with runs of spaces collapsed, so the patterns below need no open-ended `\s*`. */
const tidy = (text) =>
  String(text || '')
    .trim()
    .replace(/\s+/g, ' ');

const LEADING_NUMBER = /^\d+(?:\.\d+)?/;
// Anchored at both ends and applied to one or two whole words, so it runs in linear time.
const STRENGTH_WORD = /^(\d+(?:\.\d+)?)(mg|mcg|g|ml|iu|%)?$/i;
const UNIT_WORD = /^(mg|mcg|g|ml|iu|%)$/i;
const LEADING_AMOUNT = /^(?:\d+ \d+\/\d+|\d+\/\d+|\d+(?:\.\d+)?)/;
const DURATION_TEXT = /^(\d+) ?(days?|d|weeks?|w|months?|m)?$/i;
const SHORT_UNITS = { tab: 'tablet', cap: 'capsule' };
const DURATION_BY_LETTER = { w: 'Weeks', m: 'Months', d: 'Days' };

/** "500 mg" → { value: '500', unit: 'mg' }. */
export function parseStrength(text) {
  const t = tidy(text);
  const m = LEADING_NUMBER.exec(t);
  if (!m) return { value: '', unit: 'mg' };
  const rest = t.slice(m[0].length).trim().toLowerCase();
  const unit = STRENGTH_UNITS.find((u) => u.toLowerCase() === rest);
  return { value: m[0], unit: unit || 'mg' };
}

/** A strength written in the medicine's name ("Amoxicillin 250", "Pantop 40mg") pre-fills the field. */
export function strengthFromName(name) {
  const words = tidy(name).split(' ');
  let last = words.at(-1) || '';
  // "Calpol 500 mg": the unit is its own word, so read it together with the number before it.
  if (UNIT_WORD.test(last) && words.length > 1) last = words.at(-2) + last;
  const m = STRENGTH_WORD.exec(last);
  if (!m) return '';
  const unit = (m[2] || 'mg').toLowerCase();
  return `${m[1]} ${unit === 'iu' ? 'IU' : unit}`;
}

/** "1/2 tablet" → { amount: '1/2', unit: 'tablet' }. Anything else stays as written. */
export function parseDose(text) {
  const t = tidy(text);
  const m = LEADING_AMOUNT.exec(t);
  if (!m) return { amount: '', unit: 'tablet' };
  const unitText = t.slice(m[0].length).trim().toLowerCase().replace(/s$/, '');
  const unit =
    DOSE_UNITS.find((u) => u.replace(/s$/, '') === unitText) || SHORT_UNITS[unitText] || 'tablet';
  return { amount: m[0], unit };
}

/** "3 Days" → { value: '3', unit: 'Days' }. */
export function parseDuration(text) {
  const m = DURATION_TEXT.exec(tidy(text));
  if (!m) return { value: '', unit: 'Days' };
  const letter = (m[2] || 'd').toLowerCase()[0];
  return { value: m[1], unit: DURATION_BY_LETTER[letter] };
}

const plural = (n, word) => (Number(n) === 1 ? word.replace(/s$/, '') : word);

export const formatStrength = (value, unit) => (num(value) ? `${value} ${unit}` : '');
export const formatDose = (amount, unit) => (amount ? `${amount} ${unit}` : '');
export const formatDuration = (value, unit) =>
  num(value) ? `${value} ${plural(value, unit)}` : '';

/** What is missing before this medicine can be added, or null. */
export function doseProblem({ strength, dosage, duration }) {
  if (!parseStrength(strength).value) return 'Enter the strength (for example 500 mg).';
  if (!parseDose(dosage).amount) return 'Choose the dose (for example 1/2 tablet).';
  if (!parseDuration(duration).value) return 'Enter for how many days (or weeks) to take it.';
  return null;
}
