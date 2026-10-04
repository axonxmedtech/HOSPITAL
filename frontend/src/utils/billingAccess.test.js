import { describe, it, expect } from 'vitest';
import { canManageBilling } from './billingAccess';

const user = (role, extra = {}) => ({
  role,
  modules: ['OPD', 'BILLING'],
  billingHandler: 'RECEPTIONIST',
  receptionMode: 'HAS_RECEPTIONIST',
  ...extra,
});

describe('canManageBilling (mirrors BillingController.validateBillingAccess)', () => {
  it('nobody manages bills without the BILLING module — hospital or clinic', () => {
    for (const role of ['HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST']) {
      expect(canManageBilling(user(role, { modules: ['OPD'], receptionMode: 'SOLO' }))).toBe(false);
    }
  });

  it('an admin always may, including a single-doctor clinic admin', () => {
    expect(canManageBilling(user('HOSPITAL_ADMIN'))).toBe(true);
    expect(
      canManageBilling(user('HOSPITAL_ADMIN', { isSingleDoctor: true, receptionMode: 'SOLO' }))
    ).toBe(true);
  });

  it('a doctor may when billing is the doctor’s, or in solo mode', () => {
    expect(canManageBilling(user('DOCTOR'))).toBe(false);
    expect(canManageBilling(user('DOCTOR', { billingHandler: 'DOCTOR' }))).toBe(true);
    expect(canManageBilling(user('DOCTOR', { billingHandler: 'BOTH' }))).toBe(true);
    expect(canManageBilling(user('DOCTOR', { receptionMode: 'SOLO' }))).toBe(true);
  });

  it('a receptionist may only when billing is reception’s, and never in solo mode', () => {
    expect(canManageBilling(user('RECEPTIONIST'))).toBe(true);
    expect(canManageBilling(user('RECEPTIONIST', { billingHandler: 'BOTH' }))).toBe(true);
    expect(canManageBilling(user('RECEPTIONIST', { billingHandler: 'DOCTOR' }))).toBe(false);
    expect(canManageBilling(user('RECEPTIONIST', { receptionMode: 'SOLO' }))).toBe(false);
  });

  it('other roles and a missing user may not', () => {
    expect(canManageBilling(user('NURSE'))).toBe(false);
    expect(canManageBilling(user('PHARMACIST'))).toBe(false);
    expect(canManageBilling(null)).toBe(false);
  });
});
