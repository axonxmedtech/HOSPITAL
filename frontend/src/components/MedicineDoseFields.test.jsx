import { describe, expect, it } from 'vitest';
import {
  doseProblem,
  formatDuration,
  parseDose,
  parseDuration,
  parseStrength,
  strengthFromName,
} from './MedicineDoseFields';

describe('medicine strength, dose and duration', () => {
  it('requires a strength, a dose and a duration', () => {
    expect(doseProblem({ strength: '', dosage: '1 tablet', duration: '3 Days' })).toMatch(
      /strength/
    );
    expect(doseProblem({ strength: '500 mg', dosage: '', duration: '3 Days' })).toMatch(/dose/);
    expect(doseProblem({ strength: '500 mg', dosage: '1/2 tablet', duration: '' })).toMatch(/days/);
    expect(
      doseProblem({ strength: '500 mg', dosage: '1/2 tablet', duration: '3 Days' })
    ).toBeNull();
  });

  it('understands fractional doses and other units', () => {
    expect(parseDose('1/2 tablet')).toEqual({ amount: '1/2', unit: 'tablet' });
    expect(parseDose('1 1/2 tablets')).toEqual({ amount: '1 1/2', unit: 'tablet' });
    expect(parseDose('5 ml')).toEqual({ amount: '5', unit: 'ml' });
    expect(parseDose('1 tab')).toEqual({ amount: '1', unit: 'tablet' });
  });

  it('keeps strengths in the right unit, not always mg', () => {
    expect(parseStrength('250 mg/5 ml')).toEqual({ value: '250', unit: 'mg/5 ml' });
    expect(parseStrength('1 %')).toEqual({ value: '1', unit: '%' });
    expect(strengthFromName('Amoxicillin 250')).toBe('250 mg');
    expect(strengthFromName('Pantop 40mg')).toBe('40 mg');
    expect(strengthFromName('Betadine')).toBe('');
  });

  it('writes durations people read', () => {
    expect(formatDuration('1', 'Days')).toBe('1 Day');
    expect(formatDuration('3', 'Days')).toBe('3 Days');
    expect(parseDuration('2 Weeks')).toEqual({ value: '2', unit: 'Weeks' });
  });
});
