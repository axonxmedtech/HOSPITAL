import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../services/apiService', () => ({
  default: {
    get: vi.fn(() => Promise.reject(new Error('offline'))),
    put: vi.fn(() => Promise.resolve({})),
  },
}));
vi.mock('../services/hospitalService', () => ({
  default: { getSetupStatus: vi.fn(() => Promise.resolve(null)) },
}));

import { ALL_ENTRIES, helpRolesFor } from './content';
import { ALL_GUIDES, guidesFor } from './guideData';
import Guides from './Guides';
import { helpStore } from './helpStore';
import { progressStore } from './progressStore';

/** Every screen id of every dashboard, so a typo in help content is caught here. */
const SCREENS = new Set(
  [
    // hospital / clinic / pharmacy admin
    'overview patients appointments opd follow-ups ipd icu beds wards icu-wards ot pathology pharmacy pharmacists inventory hospital-inventory billing fees daily-collection doctors receptionists ot-incharges ot-theatres ot-analytics nurses nurse-assignments nurse-tasks time-slots calendar analytics audit-logs settings plan-usage support quick-notes symptom-presets diagnosis-presets prescription-presets in-clinic-presets pharmacies suppliers pharmacy-billing',
    // pharmacist
    'dashboard billing billing-history prescriptions inventory purchase suppliers manufacturers returns expiry reports audit-logs settings',
    // nurse, nurse in-charge, OT
    'due-now my-patients my-tasks my-shifts my-attendance forms icu-beds my-nurses my-ward-patients unassigned-patients schedule attendance beds coverage calendar board requests day',
    // super admin
    'dashboard medicines audit_logs help_insights hospital:hospitals hospital:plans hospital:tickets hospital:faqs clinic:clinics pharmacy:pharmacies',
  ]
    .join(' ')
    .split(' ')
);

const ALL_MODULES = [
  'OPD',
  'APPOINTMENTS',
  'IPD',
  'ICU',
  'OT',
  'BILLING',
  'NURSING',
  'PHARMACY',
  'MEDICAL_INVENTORY',
  'HOSPITAL_INVENTORY',
];
const ids = (user) => guidesFor(user, helpRolesFor(user)).map((g) => g.id);
const steps = (user, id) =>
  guidesFor(user, helpRolesFor(user))
    .find((g) => g.id === id)
    ?.steps.map((s) => s.id);

describe('help screen links', () => {
  it('every answer and guide step points at a real screen', () => {
    for (const e of ALL_ENTRIES) {
      [...(e.screens || []), ...(e.goTo ? [e.goTo] : [])].forEach((s) =>
        expect(SCREENS.has(s), `${e.id}: ${s}`).toBe(true)
      );
    }
    for (const g of ALL_GUIDES) {
      const seen = new Set();
      for (const s of g.steps) {
        expect(seen.has(s.id), `${g.id}: duplicate step ${s.id}`).toBe(false);
        seen.add(s.id);
        expect(s.title && s.text, `${g.id}/${s.id}`).toBeTruthy();
        if (s.goTo) expect(SCREENS.has(s.goTo), `${g.id}/${s.id}: ${s.goTo}`).toBe(true);
      }
    }
  });
});

describe('guides fit the facility', () => {
  it('a full hospital gets the hospital setup with wards, ICU, nurses and OT', () => {
    const admin = { role: 'HOSPITAL_ADMIN', hospitalType: 'HOSPITAL', modules: ALL_MODULES };
    expect(ids(admin)).toEqual(
      expect.arrayContaining(['setup-hospital', 'tut-opd', 'tut-ipd', 'tut-ot'])
    );
    expect(ids(admin)).not.toContain('setup-clinic');
    expect(steps(admin, 'setup-hospital')).toEqual(
      expect.arrayContaining(['doctors', 'wards', 'icu', 'nurses', 'ot-theatres', 'hospital-stock'])
    );
  });

  it('a clinic never sees ward, ICU or theatre steps', () => {
    const admin = {
      role: 'HOSPITAL_ADMIN',
      hospitalType: 'CLINIC',
      modules: ['OPD', 'BILLING', 'APPOINTMENTS'],
    };
    expect(ids(admin)).toContain('setup-clinic');
    expect(ids(admin)).not.toContain('tut-ipd');
    const s = steps(admin, 'setup-clinic');
    expect(s).toEqual(expect.arrayContaining(['doctors', 'fees', 'reception-mode']));
    ['wards', 'icu', 'nurses', 'ot-theatres'].forEach((x) => expect(s).not.toContain(x));
  });

  it('Solo mode skips adding receptionists', () => {
    const admin = {
      role: 'HOSPITAL_ADMIN',
      hospitalType: 'CLINIC',
      modules: ['OPD'],
      receptionMode: 'SOLO',
    };
    expect(steps(admin, 'setup-clinic')).not.toContain('receptionists');
  });

  it('pharmacies get the steps for their tier', () => {
    const base = { role: 'HOSPITAL_ADMIN', hospitalType: 'PHARMACY' };
    const solo = steps(
      { ...base, modules: ['PHARMACY', 'SINGLE_PHARMACIST_ADMIN'] },
      'setup-pharmacy'
    );
    const single = steps({ ...base, modules: ['PHARMACY', 'SINGLE_PHARMACY'] }, 'setup-pharmacy');
    const multi = steps({ ...base, modules: ['PHARMACY', 'MULTI_PHARMACY'] }, 'setup-pharmacy');
    expect(solo).toContain('solo-switch');
    expect(solo).not.toContain('pharmacists');
    expect(single).toContain('pharmacists');
    expect(multi).toContain('branches');
    expect(multi).not.toContain('pharmacists');
  });

  it('each staff role gets its own first-day guide', () => {
    const m = { hospitalType: 'HOSPITAL', modules: ALL_MODULES };
    expect(ids({ ...m, role: 'RECEPTIONIST' })).toContain('first-reception');
    expect(ids({ ...m, role: 'DOCTOR' })).toContain('first-doctor');
    expect(ids({ ...m, role: 'DOCTOR', receptionMode: 'SOLO' })).toContain('first-reception');
    expect(ids({ ...m, role: 'NURSE' })).toEqual(
      expect.arrayContaining(['first-nurse', 'tut-ipd'])
    );
    expect(ids({ ...m, role: 'NURSE_INCHARGE' })).toContain('first-incharge');
    expect(ids({ ...m, role: 'OT_INCHARGE' })).toEqual(
      expect.arrayContaining(['first-ot', 'tut-ot'])
    );
    expect(ids({ ...m, role: 'PHARMACIST' })).toContain('first-pharmacist');
    expect(ids({ role: 'SUPER_ADMIN' })).toContain('first-super-admin');
  });
});

describe('Guides tab', () => {
  beforeEach(() => {
    progressStore.clear();
    const store = new Map();
    vi.stubGlobal('localStorage', {
      getItem: (k) => (store.has(k) ? store.get(k) : null),
      setItem: (k, v) => store.set(k, String(v)),
      removeItem: (k) => store.delete(k),
    });
  });

  it('opens a guide, ticks a step and remembers it', async () => {
    const user = {
      role: 'NURSE',
      userId: 7,
      hospitalId: 1,
      hospitalType: 'HOSPITAL',
      modules: ALL_MODULES,
    };
    const onTabChange = vi.fn();
    helpStore.registerNav({
      tabs: [{ id: 'my-patients', label: 'My Patients' }],
      activeTab: 'dashboard',
      onTabChange,
    });
    const { unmount } = render(<Guides user={user} onGo={(id) => helpStore.goTo(id)} />);

    await userEvent.click(screen.getByRole('button', { name: /Your first day as a nurse/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Go to My Patients →' }));
    expect(onTabChange).toHaveBeenCalledWith('my-patients');

    await userEvent.click(screen.getByRole('checkbox', { name: 'Mark "Find your patients" done' }));
    expect(screen.getByText('1 of 6')).toBeInTheDocument();
    unmount();

    render(<Guides user={user} onGo={vi.fn()} />);
    const card = screen.getByRole('button', { name: /Your first day as a nurse/ });
    expect(within(card).getByText('1 of 6')).toBeInTheDocument();
    helpStore.clearNav(onTabChange);
  });
});

describe('screenshots', () => {
  it('every screenshot is attached to a real answer or guide step, and exists', async () => {
    const { SHOTS, _attach } = await import('./shots');
    const fs = await import('node:fs');
    const path = await import('node:path');
    const entryIds = new Set(ALL_ENTRIES.map((e) => e.id));
    const stepKeys = new Set(ALL_GUIDES.flatMap((g) => g.steps.map((s) => `${g.id}/${s.id}`)));
    const shotIds = new Set(SHOTS.map((s) => s.id));
    for (const [key, shot] of Object.entries(_attach)) {
      expect(entryIds.has(key) || stepKeys.has(key), `unknown answer/step ${key}`).toBe(true);
      expect(shotIds.has(shot), `unknown shot ${shot}`).toBe(true);
    }
    for (const s of SHOTS) {
      const file = path.resolve(__dirname, '../../public/help/shots', `${s.id}.jpg`);
      expect(
        fs.existsSync(file),
        `missing ${s.id}.jpg — run scripts/help/capture-screenshots.mjs`
      ).toBe(true);
    }
  });
});
