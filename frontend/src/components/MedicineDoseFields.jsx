/** The strength, dose and duration fields of one prescribed medicine. Parsing lives in utils/medicineDose. */
import {
  STRENGTH_UNITS,
  DOSE_AMOUNTS,
  DOSE_UNITS,
  DURATION_UNITS,
  parseStrength,
  parseDose,
  parseDuration,
  formatStrength,
  formatDose,
  formatDuration,
} from '../utils/medicineDose';

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
