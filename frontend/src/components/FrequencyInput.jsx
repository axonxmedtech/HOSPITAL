import React from 'react';

/**
 * FrequencyInput — dose-schedule entry for medicine frequency.
 *
 * Instead of free text, the user enters three dose counts (Morning-Afternoon-Night)
 * that save as the string "1-0-1". An "As Per Required" checkbox (SOS/PRN) saves the
 * string "As Per Required" and disables the boxes.
 *
 * Value contract: a plain string, matching the existing frequency fields, so no
 * backend/API change is needed.
 *   - "1-0-1"           -> boxes 1 / 0 / 1
 *   - "As Per Required" -> checkbox ticked
 *   - "" (empty)        -> nothing entered
 *   - any legacy free text (e.g. "BD") is left untouched until the user edits a box.
 */
export const AS_PER_REQUIRED = 'As Per Required';

/**
 * One-click dose schedules, in the order a prescriber reaches for them. Each `value` is an
 * ordinary Morning-Afternoon-Night string, so a preset is nothing more than a shortcut to
 * something the boxes below can already express — no new format, no new validation.
 *
 * QID (1-1-1-1) is deliberately absent: a fourth dose does not fit the three-slot contract,
 * and widening it would change what the third digit means (Night today, Evening in a
 * four-slot value) for every record already written. That is a clinical-format decision,
 * not a UI shortcut, so it is being raised separately rather than settled here.
 */
export const FREQUENCY_PRESETS = [
  { value: '1-0-0', label: 'OD', title: 'Once daily — morning' },
  { value: '1-0-1', label: 'BD', title: 'Twice daily — morning and night' },
  { value: '1-1-1', label: 'TDS', title: 'Three times daily — morning, afternoon and night' },
  { value: '0-0-1', label: 'HS', title: 'At bedtime — night only' },
];

/**
 * A frequency is valid when it is either "As Per Required" or a Morning-Afternoon-Night
 * pattern (d-d-d) with at least one non-zero dose. Empty and "0-0-0" are invalid — a
 * medicine must be taken at least once a day, or explicitly marked as-per-required.
 */
export const isFrequencyValid = (value) => {
  const s = (value == null ? '' : String(value)).trim();
  if (!s) return false;
  if (s.toLowerCase() === AS_PER_REQUIRED.toLowerCase()) return true;
  const m = /^(\d)-(\d)-(\d)$/.exec(s);
  if (!m) return false;
  return Number(m[1]) + Number(m[2]) + Number(m[3]) > 0;
};

const parse = (v) => {
  const s = (v == null ? '' : String(v)).trim();
  if (!s) return { m: '', a: '', n: '', sos: false };
  if (s.toLowerCase() === AS_PER_REQUIRED.toLowerCase()) return { m: '', a: '', n: '', sos: true };
  const mm = /^(\d)\s*-\s*(\d)\s*-\s*(\d)$/.exec(s);
  if (mm) return { m: mm[1], a: mm[2], n: mm[3], sos: false };
  // Legacy / unrecognised value: show empty boxes but don't clobber it until edited.
  return { m: '', a: '', n: '', sos: false };
};

const compose = (s) => {
  if (s.sos) return AS_PER_REQUIRED;
  if (!s.m && !s.a && !s.n) return '';
  return `${s.m || 0}-${s.a || 0}-${s.n || 0}`;
};

const FrequencyInput = ({ value, onChange, disabled = false, className = '' }) => {
  const s = parse(value);

  const onBox = (key, raw) => {
    const digits = (String(raw).match(/\d/g) || []).join('');
    // Without a browser-level maxLength, a click-and-type edit reaches this controlled input.
    // Keep one dose: replace an existing digit with the newly entered one, while a paste into an
    // empty box deterministically keeps its first digit.
    const digit = digits ? (s[key] && digits.length > 1 ? digits.at(-1) : digits[0]) : '';
    onChange(compose({ ...s, [key]: digit, sos: false }));
  };

  const selectDose = (event) => event.currentTarget.select();

  const toggleSos = (checked) => onChange(checked ? AS_PER_REQUIRED : '');

  const boxCls =
    'w-9 text-center border border-gray-300 rounded-md px-1 py-1.5 text-sm outline-none focus:ring-2 focus:ring-teal-500 disabled:bg-gray-100 disabled:text-gray-400';

  // A preset is "selected" purely by matching the current value, so typing 1-0-1 by hand
  // lights the BD chip too — the chips describe the value, they do not own it.
  const activePreset = s.sos ? '' : compose(s);

  return (
    <div className={`flex flex-wrap items-center gap-x-1.5 gap-y-1 ${className}`}>
      <div
        className="flex flex-wrap items-center gap-1"
        role="group"
        aria-label="Frequency presets"
      >
        {FREQUENCY_PRESETS.map((preset) => (
          <button
            key={preset.value}
            type="button"
            title={preset.title}
            aria-label={`${preset.value} ${preset.label}`}
            aria-pressed={activePreset === preset.value}
            disabled={disabled}
            onClick={() => onChange(preset.value)}
            className={`inline-flex items-center gap-1 px-2 py-1 text-xs font-medium border rounded-full transition disabled:opacity-50 disabled:cursor-not-allowed ${
              activePreset === preset.value
                ? 'bg-teal-600 text-white border-teal-600'
                : 'bg-teal-50 text-teal-700 border-teal-200 hover:bg-teal-100'
            }`}
          >
            <span>{preset.value}</span>
            <span className="opacity-70">{preset.label}</span>
          </button>
        ))}
      </div>
      <div className="flex items-center gap-1">
        <input
          type="text"
          inputMode="numeric"
          title="Morning"
          aria-label="Morning dose"
          placeholder="0"
          value={s.m}
          disabled={disabled || s.sos}
          onFocus={selectDose}
          onClick={selectDose}
          onChange={(e) => onBox('m', e.target.value)}
          className={boxCls}
        />
        <span className="text-gray-400 select-none">-</span>
        <input
          type="text"
          inputMode="numeric"
          title="Afternoon"
          aria-label="Afternoon dose"
          placeholder="0"
          value={s.a}
          disabled={disabled || s.sos}
          onFocus={selectDose}
          onClick={selectDose}
          onChange={(e) => onBox('a', e.target.value)}
          className={boxCls}
        />
        <span className="text-gray-400 select-none">-</span>
        <input
          type="text"
          inputMode="numeric"
          title="Night"
          aria-label="Night dose"
          placeholder="0"
          value={s.n}
          disabled={disabled || s.sos}
          onFocus={selectDose}
          onClick={selectDose}
          onChange={(e) => onBox('n', e.target.value)}
          className={boxCls}
        />
      </div>
      <label className="flex items-center gap-1 text-xs text-gray-600 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={s.sos}
          disabled={disabled}
          onChange={(e) => toggleSos(e.target.checked)}
          className="accent-teal-600"
        />
        As Per Required
      </label>
    </div>
  );
};

export default FrequencyInput;
