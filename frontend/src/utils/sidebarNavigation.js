import { canManageBilling } from './billingAccess';

export const ADMIN_SIDEBAR_GROUPS = [
  {
    id: 'group-patient-management',
    label: 'Patient Management',
    tabIds: ['patients', 'appointments', 'opd', 'follow-ups', 'ipd', 'icu', 'ot', 'pathology'],
  },
  { id: 'group-rooms', label: 'Rooms', tabIds: ['wards', 'icu-wards'] },
  {
    id: 'group-critical-care',
    label: 'Critical Care',
    tabIds: ['icu-dashboard', 'icu-beds'],
  },
  {
    id: 'group-staff',
    label: 'Staff',
    tabIds: [
      'doctors',
      'pharmacists',
      'receptionists',
      'ot-incharges',
      'ot-theatres',
      'nurses',
      'nurse-tasks',
    ],
  },
  { id: 'group-finance', label: 'Finance', tabIds: ['billing', 'fees'] },
  {
    id: 'group-pharmacy-inventory',
    label: 'Pharmacy & Inventory',
    tabIds: ['pharmacy', 'inventory', 'hospital-inventory'],
  },
  { id: 'group-reports', label: 'Reports', tabIds: ['reports', 'audit-logs'] },
  { id: 'group-communication', label: 'Communication', tabIds: ['support'] },
  { id: 'group-administration', label: 'Administration', tabIds: ['settings'] },
];

export function getEffectiveRole(user, activeDashboardPreference) {
  if (!user) return null;
  const isSingleDoctor = !!user.isSingleDoctor;
  const isSoloPharmacistAdmin = (user.modules || []).includes('SINGLE_PHARMACIST_ADMIN');

  if (user.role === 'HOSPITAL_ADMIN') {
    if (isSingleDoctor && activeDashboardPreference !== 'admin') {
      return 'DOCTOR';
    }
    if (isSoloPharmacistAdmin && activeDashboardPreference !== 'admin') {
      return 'PHARMACIST';
    }
    return 'HOSPITAL_ADMIN';
  }
  return user.role;
}

export function buildSidebarConfig({
  user,
  activeDashboard = typeof sessionStorage !== 'undefined'
    ? sessionStorage.getItem('activeDashboard')
    : null,
  expandedGroups = new Set(),
  activeTab = 'ipd',
}) {
  const effectiveRole = getEffectiveRole(user, activeDashboard);
  const modules = user?.modules || [];
  const tenantWord = user?.hospitalType === 'CLINIC' ? 'Clinic' : 'Hospital';
  const isPharmacyTenant = user?.hospitalType === 'PHARMACY';

  const hasOPD = modules.includes('OPD');
  const hasIPD = modules.includes('IPD');
  const hasAppointments = modules.includes('APPOINTMENTS');
  const hasBilling = canManageBilling(user);
  const hasMedicalInventory = modules.includes('MEDICAL_INVENTORY');
  const hasHospitalInventory = modules.includes('HOSPITAL_INVENTORY');
  const hasOT = modules.includes('OT');
  const hasICU = modules.includes('ICU');
  const canDoctorManageReception = user?.receptionMode === 'SOLO' || user?.receptionMode === 'BOTH';
  const hasInClinic = user?.inClinic !== false;

  let tabs = [];
  let usesGroupedSidebar = false;
  let title = 'HMS Portal';
  let footerTitle = tenantWord;
  let footerData =
    user?.hospitalName || (tenantWord === 'Clinic' ? 'Clinic' : 'HMS Medical Center');

  if (effectiveRole === 'HOSPITAL_ADMIN') {
    const adminRawTabs = [
      { id: 'overview', label: 'Overview', icon: null, requiredModule: 'OPD' },
      { id: 'patients', label: 'Patients', icon: null, requiredModule: 'OPD' },
      { id: 'appointments', label: 'Appointments', icon: null, requiredModule: 'APPOINTMENTS' },
      { id: 'opd', label: 'OPD', icon: null, requiredModule: 'OPD' },
      { id: 'follow-ups', label: 'Follow-ups', icon: null, requiredModule: 'OPD' },
      { id: 'ipd', label: 'IPD', icon: null, requiredModule: 'IPD' },
      { id: 'icu', label: 'ICU', icon: null, requiredModule: 'ICU' },
      { id: 'wards', label: 'Wards & Beds', icon: null, requiredModule: 'IPD' },
      { id: 'icu-wards', label: 'ICU Wards & Beds', icon: null, requiredModule: 'ICU' },
      { id: 'icu-dashboard', label: 'ICU Dashboard', icon: null, requiredModule: 'ICU' },
      { id: 'icu-beds', label: 'ICU Bed Board', icon: null, requiredModule: 'ICU' },
      { id: 'ot', label: 'Operation Theatre', icon: null, requiredModule: 'OT' },
      { id: 'pathology', label: 'Pathology', icon: null, requiredModule: 'PATHOLOGY' },
      { id: 'doctors', label: 'Doctors', icon: null, requiredModule: 'OPD' },
      { id: 'pharmacists', label: 'Pharmacists', icon: null, requiredModule: 'PHARMACY' },
      { id: 'receptionists', label: 'Receptionists', icon: null, requiredModule: 'OPD' },
      { id: 'ot-incharges', label: 'OT Incharge', icon: null, requiredModule: 'OT' },
      { id: 'ot-theatres', label: 'Theatres', icon: null, requiredModule: 'OT' },
      { id: 'nurses', label: 'Nurses', icon: null, requiredModule: 'NURSING' },
      { id: 'nurse-tasks', label: 'Nurse Tasks', icon: null, requiredModule: 'NURSING' },
      { id: 'billing', label: 'Billing', icon: null, requiredModule: 'BILLING' },
      { id: 'fees', label: 'Fees', icon: null, requiredModule: 'OPD' },
      { id: 'pharmacy', label: 'Pharmacy', icon: null, requiredModule: 'PHARMACY' },
      { id: 'inventory', label: 'Medicine Inventory', icon: null, requiredModule: 'OPD' },
      {
        id: 'hospital-inventory',
        label: `${tenantWord} Inventory`,
        icon: null,
        requiredModule: 'OPD',
      },
      { id: 'reports', label: 'Reports', icon: null, requiredModule: null },
      { id: 'audit-logs', label: 'Audit Logs', icon: null, requiredModule: null },
      { id: 'support', label: 'Support Tickets', icon: null, requiredModule: null },
      { id: 'settings', label: 'Settings', icon: null, requiredModule: 'OPD' },
    ].filter((tab) => !tab.requiredModule || modules.includes(tab.requiredModule));

    usesGroupedSidebar = user?.hospitalType === 'HOSPITAL' || user?.hospitalType === 'CLINIC';
    if (usesGroupedSidebar) {
      tabs = [
        ...adminRawTabs.filter((t) => t.id === 'overview'),
        ...ADMIN_SIDEBAR_GROUPS.map((group) => ({
          id: group.id,
          label: group.label,
          subItems: group.tabIds.map((id) => adminRawTabs.find((t) => t.id === id)).filter(Boolean),
          isExpanded: expandedGroups.has(group.id),
        })).filter((group) => group.subItems.length > 0),
      ];
    } else {
      tabs = adminRawTabs;
    }
  } else if (effectiveRole === 'DOCTOR') {
    tabs = [
      { id: 'overview', label: 'Overview', icon: null },
      { id: 'patients', label: 'Patients', icon: null },
      ...(hasAppointments ? [{ id: 'appointments', label: 'Appointments', icon: null }] : []),
      { id: 'opd', label: 'OPD', icon: null },
      { id: 'follow-ups', label: 'Follow-ups', icon: null },
      ...(hasIPD ? [{ id: 'ipd', label: 'IPD', icon: null }] : []),
      ...(hasBilling ? [{ id: 'billing', label: 'Billing', icon: null }] : []),
      ...(hasOT ? [{ id: 'ot', label: 'Operation Theatre', icon: null }] : []),
      ...(hasICU ? [{ id: 'icu-dashboard', label: 'ICU Dashboard', icon: null }] : []),
      ...(hasICU ? [{ id: 'icu-beds', label: 'ICU Bed Board', icon: null }] : []),
      ...(canDoctorManageReception && hasInClinic && hasMedicalInventory
        ? [{ id: 'inventory', label: 'Medicine Inventory', icon: null }]
        : []),
      ...(canDoctorManageReception && hasHospitalInventory
        ? [{ id: 'hospital-inventory', label: `${tenantWord} Inventory`, icon: null }]
        : []),
    ];
  } else if (effectiveRole === 'RECEPTIONIST') {
    tabs = [
      { id: 'overview', label: 'Overview', icon: null },
      ...(hasOPD ? [{ id: 'patients', label: 'Patients', icon: null }] : []),
      ...(hasAppointments ? [{ id: 'appointments', label: 'Appointments', icon: null }] : []),
      ...(hasOPD ? [{ id: 'opd', label: 'OPD', icon: null }] : []),
      ...(hasOPD ? [{ id: 'follow-ups', label: 'Follow-ups', icon: null }] : []),
      ...(hasIPD ? [{ id: 'ipd', label: 'IPD', icon: null }] : []),
      ...(hasIPD ? [{ id: 'icu', label: 'ICU', icon: null }] : []),
      ...(hasBilling ? [{ id: 'billing', label: 'Billing', icon: null }] : []),
      ...(hasMedicalInventory && user?.inClinic !== false
        ? [{ id: 'inventory', label: 'Medicine Inventory', icon: null }]
        : []),
      ...(hasHospitalInventory
        ? [{ id: 'hospital-inventory', label: `${tenantWord} Inventory`, icon: null }]
        : []),
      ...(hasOT ? [{ id: 'ot', label: 'Operation Theatre', icon: null }] : []),
      ...(hasICU ? [{ id: 'icu-dashboard', label: 'ICU Dashboard', icon: null }] : []),
      ...(hasICU ? [{ id: 'icu-beds', label: 'ICU Bed Board', icon: null }] : []),
    ];
  } else if (effectiveRole === 'NURSE') {
    title = 'Nurse Portal';
    tabs = [
      { id: 'dashboard', label: 'Dashboard' },
      { id: 'my-patients', label: 'My Patients' },
      { id: 'my-tasks', label: 'My Tasks' },
      { id: 'my-shifts', label: 'My Shifts' },
      { id: 'my-attendance', label: 'My Attendance' },
      { id: 'forms', label: 'Forms' },
      ...(user?.modules?.includes('ICU') ? [{ id: 'icu-beds', label: 'ICU Beds' }] : []),
    ];
  } else if (effectiveRole === 'NURSE_INCHARGE') {
    title = 'Nurse Incharge';
    tabs = [
      { id: 'dashboard', label: 'Dashboard' },
      { id: 'my-nurses', label: 'My Nurses' },
      { id: 'my-ward-patients', label: 'My Ward Patients' },
      { id: 'unassigned-patients', label: 'Unassigned Patients' },
      { id: 'schedule', label: 'Schedule' },
      { id: 'attendance', label: 'Attendance' },
      { id: 'beds', label: 'Beds' },
      ...(user?.modules?.includes('ICU') ? [{ id: 'icu-beds', label: 'ICU Beds' }] : []),
      { id: 'coverage', label: 'Coverage' },
      { id: 'calendar', label: 'Calendar' },
    ];
  } else if (effectiveRole === 'OT_INCHARGE') {
    title = 'Operation Theatre';
    footerTitle = 'Theatre';
    tabs = [
      { id: 'board', label: 'OT Board' },
      { id: 'day', label: "Today's List" },
      { id: 'requests', label: 'Requests' },
    ];
  } else if (effectiveRole === 'PHARMACIST') {
    const isStandalonePharmacy = modules.includes('PHARMACY') && !modules.includes('OPD');
    tabs = [
      { id: 'dashboard', label: 'Dashboard' },
      { id: 'billing', label: 'Billing Counter' },
      { id: 'billing-history', label: 'Billing' },
      ...(!isStandalonePharmacy ? [{ id: 'prescriptions', label: 'Prescriptions' }] : []),
      { id: 'inventory', label: 'Inventory' },
      { id: 'purchase', label: 'Purchase Management' },
      { id: 'suppliers', label: 'Suppliers' },
      { id: 'manufacturers', label: 'Manufacturers' },
      { id: 'returns', label: 'Returns & Refunds' },
      { id: 'expiry', label: 'Expiry Management' },
      { id: 'reports', label: 'Reports & Analytics' },
      ...(user?.role === 'HOSPITAL_ADMIN'
        ? [
            { id: 'audit-logs', label: 'Audit Logs' },
            { id: 'settings', label: 'Settings' },
          ]
        : []),
    ];
  }

  return {
    title,
    tabs,
    usesGroupedSidebar,
    footerTitle,
    footerData,
    effectiveRole,
  };
}

export function getRoleNavigateUrl(tabId, user, activeDashboard = null) {
  const effectiveRole = getEffectiveRole(user, activeDashboard);
  const isPharmacyTenant = user?.hospitalType === 'PHARMACY';

  switch (effectiveRole) {
    case 'HOSPITAL_ADMIN':
      return isPharmacyTenant ? `/pharmacy/admin?tab=${tabId}` : `/hospital/admin?tab=${tabId}`;
    case 'DOCTOR':
      return `/hospital/doctor?tab=${tabId}`;
    case 'RECEPTIONIST':
      return `/hospital/receptionist?tab=${tabId}`;
    case 'PHARMACIST':
      return isPharmacyTenant
        ? `/pharmacy/pharmacy?tab=${tabId}`
        : `/hospital/pharmacy?tab=${tabId}`;
    case 'NURSE':
      return `/hospital/nurse?tab=${tabId}`;
    case 'NURSE_INCHARGE':
      return `/hospital/nurse-incharge?tab=${tabId}`;
    case 'OT_INCHARGE':
      return `/hospital/ot-incharge?tab=${tabId}`;
    default:
      return `/hospital/admin?tab=${tabId}`;
  }
}
