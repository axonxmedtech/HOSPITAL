import React, { useState, useEffect } from 'react';

/**
 * DobPicker — Day / Month / Year dropdowns for date of birth.
 *
 * Replaces the native <input type="date"> so picking a birth year is instant
 * (a plain <select>, newest year first) instead of paging the native picker.
 *
 * Value contract is unchanged from the old date input: it reads and emits a
 * "YYYY-MM-DD" string (or "" while incomplete), so existing validation and age
 * calculation keep working as-is.
 *
 * The three dropdowns are backed by internal state, not just the parent value:
 * a single "YYYY-MM-DD" string cannot represent a partial choice, so driving the
 * selects straight from it made each field reset until all three were set at once.
 */
const MONTHS = [
  ['01', 'Jan'],
  ['02', 'Feb'],
  ['03', 'Mar'],
  ['04', 'Apr'],
  ['05', 'May'],
  ['06', 'Jun'],
  ['07', 'Jul'],
  ['08', 'Aug'],
  ['09', 'Sep'],
  ['10', 'Oct'],
  ['11', 'Nov'],
  ['12', 'Dec'],
];

const pad2 = (n) => String(n).padStart(2, '0');

const daysInMonth = (year, month) => {
  // month is 1-12; year may be blank -> assume a leap year so 29 stays selectable
  const y = year ? Number(year) : 2000;
  const m = month ? Number(month) : 1;
  return new Date(y, m, 0).getDate();
};

const splitParts = (value) => {
  const [y = '', m = '', d = ''] = (value || '').split('-');
  return { y, m, d };
};

const DobPicker = ({ value, onChange, hasError = false, disabled = false }) => {
  // Internal state so partial selections (e.g. Year picked, Month/Day not yet) persist.
  const [parts, setParts] = useState(() => splitParts(value));

  // Adopt an externally-driven value change (edit mode / form reset) without clobbering
  // an in-progress partial selection while the parent simply holds "" for "incomplete".
  useEffect(() => {
    setParts((prev) => {
      const prevComplete = prev.y && prev.m && prev.d ? `${prev.y}-${prev.m}-${prev.d}` : '';
      if (prevComplete === (value || '')) return prev; // already in sync
      if (!value && !prevComplete && (prev.y || prev.m || prev.d)) return prev; // keep partial
      return splitParts(value);
    });
  }, [value]);

  const currentYear = new Date().getFullYear();
  // Newest first so a birth year is a couple of scrolls away, not 100+.
  const years = [];
  for (let yr = currentYear; yr >= currentYear - 120; yr--) years.push(yr);

  const maxDay = daysInMonth(parts.y, parts.m);
  const days = [];
  for (let i = 1; i <= maxDay; i++) days.push(pad2(i));

  const setPart = (key, val) => {
    const next = { ...parts, [key]: val };
    // Keep the selected day within the (year, month) it now belongs to.
    if (next.d) {
      const dim = daysInMonth(next.y, next.m);
      if (Number(next.d) > dim) next.d = pad2(dim);
    }
    setParts(next);
    // Complete -> emit the date; incomplete -> "" so "required" validation still fires.
    onChange(next.y && next.m && next.d ? `${next.y}-${next.m}-${next.d}` : '');
  };

  const selCls = `input-field cursor-pointer ${hasError ? 'border-error-300 focus:ring-error-500' : ''}`;

  return (
    <div className="grid grid-cols-3 gap-2">
      <select
        aria-label="Day"
        value={parts.d}
        disabled={disabled}
        onChange={(e) => setPart('d', e.target.value)}
        className={selCls}
      >
        <option value="">Day</option>
        {days.map((dd) => (
          <option key={dd} value={dd}>
            {Number(dd)}
          </option>
        ))}
      </select>
      <select
        aria-label="Month"
        value={parts.m}
        disabled={disabled}
        onChange={(e) => setPart('m', e.target.value)}
        className={selCls}
      >
        <option value="">Month</option>
        {MONTHS.map(([mv, ml]) => (
          <option key={mv} value={mv}>
            {ml}
          </option>
        ))}
      </select>
      <select
        aria-label="Year"
        value={parts.y}
        disabled={disabled}
        onChange={(e) => setPart('y', e.target.value)}
        className={selCls}
      >
        <option value="">Year</option>
        {years.map((yr) => (
          <option key={yr} value={String(yr)}>
            {yr}
          </option>
        ))}
      </select>
    </div>
  );
};

export default DobPicker;
