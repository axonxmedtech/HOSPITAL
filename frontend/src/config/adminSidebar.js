/**
 * The hospital/clinic admin sidebar, in one place.
 *
 * This exists because the structure was previously built twice — once in HospitalAdminDashboard
 * and again, by hand, in IpdDetails. The two drifted: the dashboard grouped its tabs under
 * Patient Management / Rooms / Staff and so on, while the case screen rendered a flat list that
 * had also fallen behind on content (no OT Theatres, no nursing, no presets). Opening a case
 * therefore rearranged the navigation under the user, which is disorienting in a system people
 * use all day without looking.
 *
 * Anything that renders the admin sidebar builds it from here. A tab added in one place is a tab
 * everywhere, and it cannot be added to one copy and forgotten in the other.
 */

/**
 * Every tab an admin can have, before module gating.
 *
 * @param {string} tenantWord "Hospital" or "Clinic" — only affects the inventory label.
 */
export const buildAllAdminTabs = (tenantWord = 'Hospital') => [
  { id: 'overview', label: 'Overview', icon: null, requiredModule: null },
  // Clinical workflow
  { id: 'patients', label: 'Patients', icon: null, requiredModule: 'OPD' },
  { id: 'appointments', label: 'Appointments', icon: null, requiredModule: 'APPOINTMENTS' },
  { id: 'opd', label: 'OPD', icon: null, requiredModule: 'OPD' },
  { id: 'ipd', label: 'IPD', icon: null, requiredModule: 'IPD' },
  { id: 'wards', label: 'Wards & Beds', icon: null, requiredModule: 'IPD' },
  { id: 'ot', label: 'Operation Theatre', icon: null, requiredModule: 'OT' },
  { id: 'pathology', label: 'Pathology', icon: null, requiredModule: 'PATHOLOGY' },
  // Pharmacy & inventory
  { id: 'pharmacy', label: 'Pharmacy', icon: null, requiredModule: 'PHARMACY' },
  { id: 'pharmacists', label: 'Pharmacists', icon: null, requiredModule: 'PHARMACY' },
  { id: 'inventory', label: 'Medicine Inventory', icon: null, requiredModule: 'MEDICAL_INVENTORY' },
  {
    id: 'hospital-inventory',
    label: `${tenantWord} Inventory`,
    icon: null,
    requiredModule: 'HOSPITAL_INVENTORY',
  },
  // Financial
  { id: 'billing', label: 'Billing', icon: null, requiredModule: 'BILLING' },
  { id: 'fees', label: 'Fees', icon: null, requiredModule: 'BILLING' },
  // Staff
  { id: 'doctors', label: 'Doctors', icon: null, requiredModule: 'OPD' },
  { id: 'receptionists', label: 'Receptionists', icon: null, requiredModule: 'OPD' },
  { id: 'ot-incharges', label: 'OT Incharge', icon: null, requiredModule: 'OT' },
  { id: 'ot-theatres', label: 'OT Theatres', icon: null, requiredModule: 'OT' },
  { id: 'ot-analytics', label: 'OT Analytics', icon: null, requiredModule: 'OT' },
  { id: 'nurses', label: 'Nurses', icon: null, requiredModule: 'NURSING' },
  { id: 'nurse-assignments', label: 'Nurse Assignments', icon: null, requiredModule: 'NURSING' },
  { id: 'nurse-tasks', label: 'Nurse Tasks', icon: null, requiredModule: 'NURSING' },
  { id: 'time-slots', label: 'Time Slots', icon: null, requiredModule: 'NURSING' },
  { id: 'calendar', label: 'Calendar', icon: null, requiredModule: 'NURSING' },
  // Admin & meta
  { id: 'analytics', label: 'Reports & Analytics', icon: null, requiredModule: 'REPORTS' },
  { id: 'audit-logs', label: 'Audit Logs', icon: null, requiredModule: null },
  { id: 'settings', label: 'Settings', icon: null, requiredModule: null },
  { id: 'support', label: 'Support', icon: null, requiredModule: null },
  { id: 'quick-notes', label: 'Quick Notes', icon: null, requiredModule: null },
  { id: 'symptom-presets', label: 'Symptom Presets', icon: null, requiredModule: null },
  { id: 'diagnosis-presets', label: 'Diagnosis Presets', icon: null, requiredModule: null },
  { id: 'prescription-presets', label: 'Prescription Presets', icon: null, requiredModule: null },
  // In-Clinic presets are bundles of stock medicines administered in the clinic, so they only
  // make sense while the In-Clinic flow is on (gated below, like Medicine Inventory).
  { id: 'in-clinic-presets', label: 'In-Clinic Presets', icon: null, requiredModule: null },
];

/**
 * How the tabs are grouped in the sidebar.
 *
 * Overview is deliberately absent: it is a single item, so it stays a plain top-level link rather
 * than a one-item dropdown. A group whose tabs are all gated away (a clinic with no IPD, OT or
 * Nursing on its plan) has no subItems and is dropped entirely, so a clinic naturally shows a
 * shorter sidebar without any clinic-specific branching here.
 */
export const SIDEBAR_GROUPS = [
  {
    id: 'group-patient-management',
    label: 'Patient Management',
    tabIds: ['patients', 'appointments', 'opd', 'ipd', 'ot', 'pathology'],
  },
  { id: 'group-rooms', label: 'Rooms', tabIds: ['wards', 'ot-theatres'] },
  {
    id: 'group-staff',
    label: 'Staff',
    tabIds: ['doctors', 'pharmacists', 'receptionists', 'ot-incharges'],
  },
  {
    id: 'group-nursing',
    label: 'Nursing',
    tabIds: ['nurses', 'nurse-assignments', 'nurse-tasks', 'time-slots', 'calendar'],
  },
  { id: 'group-pharmacy', label: 'Pharmacy', tabIds: ['pharmacy'] },
  { id: 'group-inventory', label: 'Inventory', tabIds: ['inventory', 'hospital-inventory'] },
  { id: 'group-finance', label: 'Finance', tabIds: ['billing', 'fees'] },
  { id: 'group-reports', label: 'Reports', tabIds: ['analytics', 'ot-analytics', 'audit-logs'] },
  {
    id: 'group-presets',
    label: 'Presets',
    tabIds: [
      'quick-notes',
      'symptom-presets',
      'diagnosis-presets',
      'prescription-presets',
      'in-clinic-presets',
    ],
  },
  { id: 'group-administration', label: 'Administration', tabIds: ['settings', 'support'] },
];

/** Grouped sidebars are for hospital and clinic admins; pharmacy tenants keep a flat, plan-driven list. */
export const usesGroupedSidebar = (hospitalType) =>
  hospitalType === 'HOSPITAL' || hospitalType === 'CLINIC';

/**
 * Applies module gating and the two settings that hide tabs.
 *
 * @param {object} options
 * @param {string[]} options.modules       modules on the tenant's plan
 * @param {boolean}  options.hasInClinic   In-Clinic medicine flow enabled
 * @param {boolean}  options.otInchargeEnabled OT Incharge staff option enabled
 * @param {string}   options.tenantWord    "Hospital" or "Clinic"
 */
export const buildAdminTabs = ({
  modules = [],
  hasInClinic = true,
  otInchargeEnabled = false,
  tenantWord = 'Hospital',
} = {}) =>
  buildAllAdminTabs(tenantWord).filter((tab) => {
    if (tab.requiredModule && !modules.includes(tab.requiredModule)) return false;
    if (tab.id === 'inventory' && !hasInClinic) return false;
    if (tab.id === 'in-clinic-presets' && !hasInClinic) return false;
    if (tab.id === 'ot-incharges' && !otInchargeEnabled) return false;
    return true;
  });

/**
 * Turns a flat tab list into the grouped structure the Sidebar renders.
 *
 * Purely presentational: the same ids and labels come out, so navigation, module gating and
 * permissions are untouched.
 *
 * @param {Array}  tabs             already gated
 * @param {Set}    expandedGroupIds groups currently open
 */
export const groupSidebarTabs = (tabs, expandedGroupIds = new Set()) => [
  ...tabs.filter((t) => t.id === 'overview'),
  ...SIDEBAR_GROUPS.map((group) => ({
    id: group.id,
    label: group.label,
    subItems: group.tabIds.map((id) => tabs.find((t) => t.id === id)).filter(Boolean),
    // Expansion is driven ONLY by state. It used to also OR in group.tabIds.includes(activeTab),
    // which force-expanded the group owning the open tab — so clicking that group's header could
    // never close it. The active tab's group is instead auto-expanded once, when the tab changes.
    isExpanded: expandedGroupIds.has(group.id),
  })).filter((group) => group.subItems.length > 0),
];

/** The group that owns a tab, so it can be auto-expanded when that tab becomes active. */
export const groupOwning = (tabId) => SIDEBAR_GROUPS.find((g) => g.tabIds.includes(tabId));
