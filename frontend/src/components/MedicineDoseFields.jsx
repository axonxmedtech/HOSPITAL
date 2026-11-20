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

/** "500 mg" → { value: '500', unit: 'mg' }. */
export function parseStrength(text) {
  const m = String(text || '')
    .trim()
    .match(/^(\d+(?:\.\d+)?)\s*(.*)$/);
  if (!m) return { value: '', unit: 'mg' };
  const unit = STRENGTH_UNITS.find((u) => u.toLowerCase() === m[2].trim().toLowerCase());
  return { value: m[1], unit: unit || 'mg' };
}

/** A strength written in the medicine's name ("Amoxicillin 250", "Pantop 40mg") pre-fills the field. */
export function strengthFromName(name) {
  const m = String(name || '').match(/(\d+(?:\.\d+)?)\s*(mg|mcg|g|ml|iu|%)?\s*$/i);
  if (!m) return '';
  const unit = (m[2] || 'mg').toLowerCase();
  return `${m[1]} ${unit === 'iu' ? 'IU' : unit}`;
}

/** "1/2 tablet" → { amount: '1/2', unit: 'tablet' }. Anything else stays as written. */
export function parseDose(text) {
  const t = String(text || '').trim();
  const m = t.match(/^(\d+\s+\d+\/\d+|\d+\/\d+|\d+(?:\.\d+)?)\s*(.*)$/);
  if (!m) return { amount: '', unit: 'tablet' };
  const unitText = m[2].trim().toLowerCase().replace(/s$/, '');
  const unit =
    DOSE_UNITS.find((u) => u.replace(/s$/, '') === unitText) ||
    (unitText === 'tab' ? 'tablet' : unitText === 'cap' ? 'capsule' : 'tablet');
  return { amount: m[1], unit };
}

/** "3 Days" → { value: '3', unit: 'Days' }. */
export function parseDuration(text) {
  const m = String(text || '')
    .trim()
    .match(/^(\d+)\s*(day|days|d|week|weeks|w|month|months|m)?$/i);
  if (!m) return { value: '', unit: 'Days' };
  const u = (m[2] || 'days').toLowerCase()[0];
  return { value: m[1], unit: u === 'w' ? 'Weeks' : u === 'm' ? 'Months' : 'Days' };
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

const field =
  'border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 bg-white';

/** The three rows of fields. `value` holds the strings; `onChange(patch)` updates them. */
export default function MedicineDoseFields({ value, onChange }) {
  const s = parseStrength(value.strength);
  const d = parseDose(value.dosage);
  const r = parseDuration(value.duration);
  return (
    <>
      <fieldset className="flex gap-2 min-w-0">
        <legend className="sr-only">Strength</legend>
        <input
          type="number"
          inputMode="decimal"
          min="0"
          step="any"
          aria-label="Strength"
          placeholder="Strength *"
          value={s.value}
          onChange={(e) =>
            onChange({ strength: formatStrength(e.target.value, s.unit) || e.target.value })
          }
          className={`${field} flex-1 min-w-0`}
        />
        <select
          aria-label="Strength unit"
          value={s.unit}
          onChange={(e) => onChange({ strength: formatStrength(s.value, e.target.value) })}
          className={`${field} w-28`}
        >
          {STRENGTH_UNITS.map((u) => (
            <option key={u}>{u}</option>
          ))}
        </select>
      </fieldset>
      <fieldset className="flex gap-2 min-w-0">
        <legend className="sr-only">Dose</legend>
        <select
          aria-label="Dose"
          value={d.amount}
          onChange={(e) => onChange({ dosage: formatDose(e.target.value, d.unit) })}
          className={`${field} flex-1 min-w-0`}
        >
          <option value="">Dose *</option>
          {[...new Set([...DOSE_AMOUNTS, ...(d.amount ? [d.amount] : [])])].map((a) => (
            <option key={a} value={a}>
              {a}
            </option>
          ))}
        </select>
        <select
          aria-label="Dose unit"
          value={d.unit}
          onChange={(e) => onChange({ dosage: formatDose(d.amount || '1', e.target.value) })}
          className={`${field} w-32`}
        >
          {DOSE_UNITS.map((u) => (
            <option key={u}>{u}</option>
          ))}
        </select>
      </fieldset>
      <fieldset className="flex gap-2 min-w-0">
        <legend className="sr-only">Duration</legend>
        <input
          type="number"
          inputMode="numeric"
          min="1"
          step="1"
          aria-label="Duration"
          placeholder="Duration *"
          value={r.value}
          onChange={(e) => onChange({ duration: formatDuration(e.target.value, r.unit) })}
          className={`${field} flex-1 min-w-0`}
        />
        <select
          aria-label="Duration unit"
          value={r.unit}
          onChange={(e) => onChange({ duration: formatDuration(r.value || '1', e.target.value) })}
          className={`${field} w-28`}
        >
          {DURATION_UNITS.map((u) => (
            <option key={u}>{u}</option>
          ))}
        </select>
      </fieldset>
    </>
  );
}
