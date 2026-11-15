import { describe, it, expect } from 'vitest';
import { getEffectiveRole, buildSidebarConfig, getRoleNavigateUrl } from './sidebarNavigation';

describe('sidebarNavigation utility', () => {
  describe('getEffectiveRole', () => {
    it('returns role directly for normal users', () => {
      expect(getEffectiveRole({ role: 'DOCTOR' })).toBe('DOCTOR');
      expect(getEffectiveRole({ role: 'RECEPTIONIST' })).toBe('RECEPTIONIST');
      expect(getEffectiveRole({ role: 'NURSE' })).toBe('NURSE');
    });

    it('returns DOCTOR for single doctor admin when activeDashboard is not admin', () => {
      const user = { role: 'HOSPITAL_ADMIN', isSingleDoctor: true };
      expect(getEffectiveRole(user, 'doctor')).toBe('DOCTOR');
      expect(getEffectiveRole(user, null)).toBe('DOCTOR');
      expect(getEffectiveRole(user, 'admin')).toBe('HOSPITAL_ADMIN');
    });

    it('returns PHARMACIST for single pharmacist admin when activeDashboard is not admin', () => {
      const user = {
        role: 'HOSPITAL_ADMIN',
        modules: ['SINGLE_PHARMACIST_ADMIN'],
      };
      expect(getEffectiveRole(user, 'pharmacy')).toBe('PHARMACIST');
      expect(getEffectiveRole(user, null)).toBe('PHARMACIST');
      expect(getEffectiveRole(user, 'admin')).toBe('HOSPITAL_ADMIN');
    });
  });

  describe('buildSidebarConfig for HOSPITAL_ADMIN', () => {
    it('provides grouped sidebar with Patient Management containing ipd for HOSPITAL and CLINIC', () => {
      const user = {
        role: 'HOSPITAL_ADMIN',
        hospitalType: 'HOSPITAL',
        hospitalName: 'Apex Hospital',
        modules: ['OPD', 'IPD', 'ICU', 'BILLING', 'OT', 'PATHOLOGY'],
      };
      const expandedGroups = new Set(['group-patient-management']);
      const config = buildSidebarConfig({
        user,
        activeDashboard: 'admin',
        expandedGroups,
        activeTab: 'ipd',
      });

      expect(config.usesGroupedSidebar).toBe(true);
      expect(config.title).toBe('HMS Portal');
      expect(config.footerTitle).toBe('Hospital');
      expect(config.footerData).toBe('Apex Hospital');

      // Overview should be top-level
      expect(config.tabs[0].id).toBe('overview');

      // Patient Management group should contain ipd
      const pmGroup = config.tabs.find((t) => t.id === 'group-patient-management');
      expect(pmGroup).toBeDefined();
      expect(pmGroup.isExpanded).toBe(true);
      const ipdSubItem = pmGroup.subItems.find((s) => s.id === 'ipd');
      expect(ipdSubItem).toBeDefined();
      expect(ipdSubItem.label).toBe('IPD');
    });

    it('uses Clinic as footerTitle when hospitalType is CLINIC', () => {
      const user = {
        role: 'HOSPITAL_ADMIN',
        hospitalType: 'CLINIC',
        hospitalName: 'Care Clinic',
        modules: ['OPD', 'IPD'],
      };
      const config = buildSidebarConfig({ user });
      expect(config.footerTitle).toBe('Clinic');
    });
  });

  describe('buildSidebarConfig for DOCTOR', () => {
    it('provides flat sidebar with all doctor tabs matching DoctorDashboard', () => {
      const user = {
        role: 'DOCTOR',
        hospitalType: 'HOSPITAL',
        hospitalName: 'Apex Hospital',
        modules: [
          'OPD',
          'IPD',
          'APPOINTMENTS',
          'BILLING',
          'OT',
          'ICU',
          'MEDICAL_INVENTORY',
          'HOSPITAL_INVENTORY',
        ],
        receptionMode: 'SOLO',
        billingHandler: 'DOCTOR',
      };

      const config = buildSidebarConfig({ user });
      expect(config.usesGroupedSidebar).toBe(false);
      const tabIds = config.tabs.map((t) => t.id);

      expect(tabIds).toContain('overview');
      expect(tabIds).toContain('patients');
      expect(tabIds).toContain('appointments');
      expect(tabIds).toContain('opd');
      expect(tabIds).toContain('follow-ups');
      expect(tabIds).toContain('ipd');
      expect(tabIds).toContain('billing');
      expect(tabIds).toContain('ot');
      expect(tabIds).toContain('icu-dashboard');
      expect(tabIds).toContain('icu-beds');
      expect(tabIds).toContain('inventory');
      expect(tabIds).toContain('hospital-inventory');
      expect(tabIds).not.toContain('queue'); // queue was the erroneous old tab
    });
  });

  describe('buildSidebarConfig for RECEPTIONIST', () => {
    it('provides flat sidebar with all receptionist tabs matching ReceptionistDashboard', () => {
      const user = {
        role: 'RECEPTIONIST',
        hospitalType: 'HOSPITAL',
        hospitalName: 'Apex Hospital',
        modules: [
          'OPD',
          'IPD',
          'APPOINTMENTS',
          'BILLING',
          'OT',
          'ICU',
          'MEDICAL_INVENTORY',
          'HOSPITAL_INVENTORY',
        ],
        billingHandler: 'RECEPTIONIST',
      };

      const config = buildSidebarConfig({ user });
      expect(config.usesGroupedSidebar).toBe(false);
      const tabIds = config.tabs.map((t) => t.id);

      expect(tabIds).toContain('overview');
      expect(tabIds).toContain('patients');
      expect(tabIds).toContain('appointments');
      expect(tabIds).toContain('opd');
      expect(tabIds).toContain('follow-ups');
      expect(tabIds).toContain('ipd');
      expect(tabIds).toContain('icu');
      expect(tabIds).toContain('billing');
      expect(tabIds).toContain('inventory');
      expect(tabIds).toContain('hospital-inventory');
      expect(tabIds).toContain('ot');
      expect(tabIds).toContain('icu-dashboard');
      expect(tabIds).toContain('icu-beds');
    });
  });

  describe('buildSidebarConfig for NURSE and NURSE_INCHARGE', () => {
    it('provides nurse tabs', () => {
      const user = {
        role: 'NURSE',
        modules: ['NURSING', 'ICU'],
      };
      const config = buildSidebarConfig({ user });
      expect(config.title).toBe('Nurse Portal');
      const tabIds = config.tabs.map((t) => t.id);
      expect(tabIds).toEqual([
        'dashboard',
        'my-patients',
        'my-tasks',
        'my-shifts',
        'my-attendance',
        'forms',
        'icu-beds',
      ]);
    });

    it('provides nurse incharge tabs', () => {
      const user = {
        role: 'NURSE_INCHARGE',
        modules: ['NURSING'],
      };
      const config = buildSidebarConfig({ user });
      expect(config.title).toBe('Nurse Incharge');
      const tabIds = config.tabs.map((t) => t.id);
      expect(tabIds).toContain('my-ward-patients');
      expect(tabIds).toContain('unassigned-patients');
    });
  });

  describe('getRoleNavigateUrl', () => {
    it('navigates to doctor dashboard for doctor', () => {
      expect(getRoleNavigateUrl('overview', { role: 'DOCTOR' })).toBe(
        '/hospital/doctor?tab=overview'
      );
      expect(getRoleNavigateUrl('patients', { role: 'DOCTOR' })).toBe(
        '/hospital/doctor?tab=patients'
      );
    });

    it('navigates to receptionist dashboard for receptionist', () => {
      expect(getRoleNavigateUrl('opd', { role: 'RECEPTIONIST' })).toBe(
        '/hospital/receptionist?tab=opd'
      );
    });

    it('navigates to hospital admin dashboard for admin', () => {
      expect(getRoleNavigateUrl('wards', { role: 'HOSPITAL_ADMIN' })).toBe(
        '/hospital/admin?tab=wards'
      );
    });

    it('navigates to pharmacy admin for pharmacy tenant admin', () => {
      const user = { role: 'HOSPITAL_ADMIN', hospitalType: 'PHARMACY' };
      expect(getRoleNavigateUrl('inventory', user)).toBe('/pharmacy/admin?tab=inventory');
    });

    it('navigates to nurse dashboard for nurse', () => {
      expect(getRoleNavigateUrl('my-patients', { role: 'NURSE' })).toBe(
        '/hospital/nurse?tab=my-patients'
      );
    });
  });
});
