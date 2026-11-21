import { describe, it, expect } from 'vitest';
import {
  formatDate,
  formatDateTime,
  formatDay,
  formatDays,
  formatTime,
  formatWhen,
  localIsoDate,
} from './date';

describe('date formatters', () => {
  it('return a dash for empty/invalid input', () => {
    expect(formatDate('')).toBe('-');
    expect(formatDate(null)).toBe('-');
    expect(formatDate('not-a-date')).toBe('-');
    expect(formatDateTime('')).toBe('-');
    expect(formatDateTime('nonsense')).toBe('-');
    expect(formatTime('')).toBe('-');
    expect(formatTime('nonsense')).toBe('-');
  });

  it('format valid ISO input to non-dash strings', () => {
    const iso = '2024-03-15T10:30:00';
    expect(formatDate(iso)).not.toBe('-');
    expect(formatDateTime(iso)).not.toBe('-');
    expect(formatTime(iso)).not.toBe('-');
  });
});

describe('localIsoDate', () => {
  it('uses the local calendar day, not the UTC one', () => {
    // 00:30 local on 17 Nov. In any timezone east of UTC, toISOString() would say the 16th.
    expect(localIsoDate(new Date(2026, 10, 17, 0, 30))).toBe('2026-11-17');
  });

  it('zero-pads month and day', () => {
    expect(localIsoDate(new Date(2026, 0, 5, 12, 0))).toBe('2026-01-05');
  });
});

describe('records date formats', () => {
  it('reads as day month year, without seconds', () => {
    expect(formatDay('2026-11-20T21:31:31')).toBe('20 Nov 2026');
    expect(formatWhen('2026-11-20T21:31:31')).toMatch(/^20 Nov 2026, 9:31\s?pm$/i);
    expect(formatWhen(null)).toBe('—');
    expect(formatDays(1)).toBe('1 day');
    expect(formatDays(3)).toBe('3 days');
  });
});
