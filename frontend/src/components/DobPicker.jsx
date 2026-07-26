import React from 'react';

/**
 * DobPicker — Day / Month / Year dropdowns for date of birth.
 *
 * Replaces the native <input type="date"> so picking a birth year is instant
 * (a plain <select>, newest year first) instead of paging the native picker.
 *
 * Value contract is unchanged from the old date input: it reads and emits a
 * "YYYY-MM-DD" string (or "" while incomplete), so existing validation and age
 * calculation keep working as-is.
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

const DobPicker = ({ value, onChange, hasError = false, disabled = false }) => {
  const [y = '', m = '', d = ''] = (value || '').split('-');

  const now = new Date();
  const currentYear = now.getFullYear();
  // Newest first so a birth year is a couple of scrolls away, not 100+.
  const years = [];
  for (let yr = currentYear; yr >= currentYear - 120; yr--) years.push(yr);

  const maxDay = daysInMonth(y, m);
  const days = [];
  for (let i = 1; i <= maxDay; i++) days.push(pad2(i));

  const emit = (ny, nm, nd) => {
    // Keep the selected day within the (year, month) it now belongs to.
    let day = nd;
    if (day) {
      const dim = daysInMonth(ny, nm);
      if (Number(day) > dim) day = pad2(dim);
    }
    if (ny && nm && day) onChange(`${ny}-${nm}-${day}`);
    else onChange(''); // incomplete -> empty, so "required" validation still fires
  };

  const selCls = `input-field cursor-pointer ${hasError ? 'border-error-300 focus:ring-error-500' : ''}`;

  return (
    <div className="grid grid-cols-3 gap-2">
      <select
        aria-label="Day"
        value={d}
        disabled={disabled}
        onChange={(e) => emit(y, m, e.target.value)}
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
        value={m}
        disabled={disabled}
        onChange={(e) => emit(y, e.target.value, d)}
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
        value={y}
        disabled={disabled}
        onChange={(e) => emit(e.target.value, m, d)}
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
