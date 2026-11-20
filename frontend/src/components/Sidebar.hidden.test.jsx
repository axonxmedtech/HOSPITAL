import { describe, expect, it } from 'vitest';
import { tabsForRole } from '../utils/sidebarNavigation';
import { withoutHidden } from './Sidebar';

describe('Settings → Sidebar', () => {
  it('leaves out hidden tabs, and a group whose items are all hidden', () => {
    const tabs = [
      { id: 'overview', label: 'Overview' },
      { id: 'follow-ups', label: 'Follow-ups' },
      { id: 'group-staff', label: 'Staff', subItems: [{ id: 'doctors' }, { id: 'nurses' }] },
      { id: 'group-rooms', label: 'Rooms', subItems: [{ id: 'wards' }] },
    ];
    const out = withoutHidden(tabs, new Set(['follow-ups', 'nurses', 'wards']));
    expect(out.map((t) => t.id)).toEqual(['overview', 'group-staff']);
    expect(out[1].subItems.map((s) => s.id)).toEqual(['doctors']);
    expect(withoutHidden(tabs, new Set())).toBe(tabs);
  });

  it('offers the admin every tab a role can have, incl. front-desk ones', () => {
    const user = { hospitalType: 'HOSPITAL', modules: ['OPD', 'IPD', 'ICU', 'HOSPITAL_INVENTORY'] };
    const doctor = tabsForRole('DOCTOR', user).map((t) => t.id);
    expect(doctor).toEqual(
      expect.arrayContaining(['overview', 'opd', 'follow-ups', 'icu-beds', 'hospital-inventory'])
    );
    expect(tabsForRole('RECEPTIONIST', user).map((t) => t.id)).toContain('beds');
  });
});
