import { describe, expect, it } from 'vitest';
import { lockedBy, normalizePlanModules } from './PlansTab';

describe('normalizePlanModules', () => {
  it('adds OPD when APPOINTMENTS is selected', () => {
    expect(normalizePlanModules(['APPOINTMENTS'])).toEqual(['APPOINTMENTS', 'OPD']);
  });

  it('keeps OPD independent when appointments are not selected', () => {
    expect(normalizePlanModules(['OPD', 'BILLING'])).toEqual(['OPD', 'BILLING']);
  });

  it('adds BILLING when IPD is selected, because every admission is billed', () => {
    expect(normalizePlanModules(['OPD', 'IPD'])).toEqual(['OPD', 'IPD', 'BILLING']);
  });

  it('lets an OPD-only plan leave billing out', () => {
    expect(normalizePlanModules(['OPD'])).toEqual(['OPD']);
  });
});

describe('lockedBy', () => {
  it('locks Billing on while IPD is selected, and OPD while Appointments is', () => {
    expect(lockedBy('BILLING', ['OPD', 'IPD', 'BILLING'])).toBe('IPD');
    expect(lockedBy('OPD', ['APPOINTMENTS', 'OPD'])).toBe('APPOINTMENTS');
  });

  it('leaves a module free when nothing selected depends on it', () => {
    expect(lockedBy('BILLING', ['OPD', 'BILLING'])).toBeNull();
    expect(lockedBy('IPD', ['OPD', 'IPD', 'BILLING'])).toBeNull();
  });
});
