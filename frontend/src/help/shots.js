/**
 * Screenshots shown in help answers and guides. They are captured from the real app with demo
 * data by `node scripts/help/capture-screenshots.mjs` and saved to public/help/shots/<id>.jpg,
 * so they can be regenerated whenever a screen changes.
 *
 *  account — which demo login takes it (see the script)
 *  screen  — the menu label to open (found through the command bar)
 *  action  — an optional click after opening the screen (a button that opens a form)
 */
export const SHOTS = [
  // Admin
  {
    id: 'admin-setup',
    account: 'fresh',
    screen: 'Overview',
    caption: 'The setup checklist on a new hospital’s Overview',
  },
  {
    id: 'admin-doctors',
    account: 'admin',
    screen: 'Doctors',
    action: 'Add Doctor',
    caption: 'Staff → Doctors → Add Doctor',
  },
  {
    id: 'admin-wards',
    account: 'admin',
    screen: 'Wards & Beds',
    caption: 'Rooms → Wards & Beds, with the in-charge on each ward',
  },
  { id: 'admin-icu-wards', account: 'admin', screen: 'ICU Wards', caption: 'Rooms → ICU Wards' },
  { id: 'admin-fees', account: 'admin', screen: 'Fees', caption: 'Finance → Fees' },
  {
    id: 'admin-settings',
    account: 'admin',
    screen: 'Settings',
    caption: 'Administration → Settings',
  },
  {
    id: 'admin-plan',
    account: 'admin',
    screen: 'Plan & Usage',
    caption: 'Administration → Plan & Usage',
  },
  { id: 'admin-nurses', account: 'admin', screen: 'Nurses', caption: 'Staff → Nurses' },
  {
    id: 'admin-ot-theatres',
    account: 'admin',
    screen: 'OT Theatres',
    caption: 'Staff → OT Theatres',
  },
  {
    id: 'admin-hospital-inventory',
    account: 'admin',
    screen: 'Hospital Inventory',
    caption: 'Inventory → Hospital Inventory',
  },
  { id: 'admin-billing', account: 'admin', screen: 'Billing', caption: 'Finance → Billing' },
  { id: 'admin-audit', account: 'admin', screen: 'Audit Logs', caption: 'Reports → Audit Logs' },
  { id: 'admin-support', account: 'admin', screen: 'Support', caption: 'Administration → Support' },
  // Front desk
  {
    id: 'desk-patients',
    account: 'reception',
    screen: 'Patients',
    caption: 'Patients — search or Add Patient',
  },
  {
    id: 'desk-new-opd',
    account: 'reception',
    screen: 'OPD',
    action: 'New OPD / Case',
    caption: 'OPD → New OPD / Case',
  },
  {
    id: 'desk-opd',
    account: 'reception',
    screen: 'OPD',
    caption: 'OPD — the live queue with Collect in the Bill column',
  },
  {
    id: 'desk-admit',
    account: 'reception',
    screen: 'IPD',
    action: 'Admit Patient',
    caption: 'IPD → Admit Patient',
  },
  {
    id: 'desk-ipd',
    account: 'reception',
    screen: 'IPD',
    caption: 'IPD — admitted patients and admission requests',
  },
  {
    id: 'desk-appointments',
    account: 'reception',
    screen: 'Appointments',
    caption: 'Appointments',
  },
  {
    id: 'desk-daily',
    account: 'reception',
    screen: 'Daily Collection',
    caption: 'Daily Collection — the day’s cash, card and UPI',
  },
  // Doctor
  {
    id: 'doc-overview',
    account: 'doctor',
    screen: 'Overview',
    caption: 'Doctor Overview — current and next patient',
  },
  { id: 'doc-ipd', account: 'doctor', screen: 'IPD', caption: 'IPD — your admitted patients' },
  { id: 'doc-followups', account: 'doctor', screen: 'Follow-ups', caption: 'Follow-ups' },
  {
    id: 'doc-ot',
    account: 'doctor',
    screen: 'Operation Theatre',
    caption: 'Operation Theatre board',
  },
  // Nursing
  {
    id: 'ni-unassigned',
    account: 'nurseinc',
    screen: 'Unassigned Patients',
    caption: 'Unassigned Patients → Assign primary nurse',
  },
  {
    id: 'ni-schedule',
    account: 'nurseinc',
    screen: 'Schedule',
    caption: 'Schedule — the duty roster',
  },
  {
    id: 'ni-beds',
    account: 'nurseinc',
    screen: 'Beds',
    caption: 'Beds — Mark Cleaned or maintenance',
  },
  { id: 'nurse-patients', account: 'staffnurse', screen: 'My Patients', caption: 'My Patients' },
  { id: 'nurse-tasks', account: 'staffnurse', screen: 'My Tasks', caption: 'My Tasks' },
  // OT
  { id: 'ot-board', account: 'ot', screen: 'OT Board', caption: 'OT Board — each surgery’s stage' },
  {
    id: 'ot-requests',
    account: 'ot',
    screen: 'Requests',
    caption: 'Requests waiting to be scheduled',
  },
  { id: 'ot-day', account: 'ot', screen: "Today's List", caption: 'Today’s List' },
  // Pharmacy
  {
    id: 'ph-counter',
    account: 'pharmacist',
    screen: 'Billing Counter',
    caption: 'Billing Counter',
  },
  {
    id: 'ph-purchase',
    account: 'pharmacist',
    screen: 'Purchase Management',
    caption: 'Purchase Management',
  },
  {
    id: 'ph-expiry',
    account: 'pharmacist',
    screen: 'Expiry Management',
    caption: 'Expiry Management',
  },
  {
    id: 'ph-returns',
    account: 'pharmacist',
    screen: 'Returns & Refunds',
    caption: 'Returns & Refunds',
  },
  {
    id: 'ph-prescriptions',
    account: 'pharmacist',
    screen: 'Prescriptions',
    caption: 'Prescriptions waiting to be dispensed',
  },
  {
    id: 'pa-branches',
    account: 'pharmaadmin',
    screen: 'Pharmacists',
    caption: 'Pharmacy admin → Pharmacists',
  },
  // Super admin
  {
    id: 'sa-dashboard',
    account: 'superadmin',
    screen: 'Dashboard',
    caption: 'Super Admin Dashboard — demos, renewals and limits',
  },
  { id: 'sa-plans', account: 'superadmin', screen: 'Plans', caption: 'Plans with their limits' },
];

/** Which screenshot goes with which answer (entry id) or guide step (guideId/stepId). */
const ATTACH = {
  'admin-setup-guide': 'admin-setup',
  'admin-add-doctor': 'admin-doctors',
  'admin-wards': 'admin-wards',
  'admin-icu-wards': 'admin-icu-wards',
  'admin-fees': 'admin-fees',
  'admin-reception-mode': 'admin-settings',
  'admin-plan-usage': 'admin-plan',
  'admin-ot-theatres': 'admin-ot-theatres',
  'admin-hospital-inventory': 'admin-hospital-inventory',
  'admin-audit': 'admin-audit',
  'admin-support-ticket': 'admin-support',
  'desk-register-patient': 'desk-patients',
  'desk-find-patient': 'desk-patients',
  'desk-new-opd': 'desk-new-opd',
  'desk-collect-opd': 'desk-opd',
  'desk-cancel-visit': 'desk-opd',
  'desk-admit-direct': 'desk-admit',
  'desk-admit-request': 'desk-ipd',
  'desk-book-appointment': 'desk-appointments',
  'desk-daily-collection': 'desk-daily',
  'doc-queue': 'doc-overview',
  'doc-my-ipd': 'doc-ipd',
  'doc-followup': 'doc-followups',
  'doc-surgery-board': 'doc-ot',
  'ni-assign': 'ni-unassigned',
  'ni-schedule': 'ni-schedule',
  'ni-beds': 'ni-beds',
  'nurse-my-patients': 'nurse-patients',
  'nurse-tasks': 'nurse-tasks',
  'ot-requests': 'ot-requests',
  'ot-flow': 'ot-board',
  'ot-today': 'ot-day',
  'ph-sell': 'ph-counter',
  'ph-receive': 'ph-purchase',
  'ph-expiry': 'ph-expiry',
  'ph-returns': 'ph-returns',
  'ph-dispense': 'ph-prescriptions',
  'sa-renewals': 'sa-dashboard',
  'sa-limits': 'sa-plans',
  // guide steps
  'setup-hospital/plan': 'admin-plan',
  'setup-hospital/doctors': 'admin-doctors',
  'setup-hospital/reception-mode': 'admin-settings',
  'setup-hospital/fees': 'admin-fees',
  'setup-hospital/wards': 'admin-wards',
  'setup-hospital/icu': 'admin-icu-wards',
  'setup-hospital/nurses': 'admin-nurses',
  'setup-hospital/ot-theatres': 'admin-ot-theatres',
  'setup-hospital/hospital-stock': 'admin-hospital-inventory',
  'setup-clinic/plan': 'admin-plan',
  'setup-clinic/doctors': 'admin-doctors',
  'setup-clinic/reception-mode': 'admin-settings',
  'setup-clinic/fees': 'admin-fees',
  'setup-pharmacy/pharmacists': 'pa-branches',
  'setup-pharmacy/opening-stock': 'ph-purchase',
  'setup-pharmacy/first-sale': 'ph-counter',
  'first-reception/register': 'desk-patients',
  'first-reception/opd': 'desk-new-opd',
  'first-reception/collect': 'desk-opd',
  'first-reception/admit': 'desk-admit',
  'first-reception/close': 'desk-daily',
  'first-doctor/queue': 'doc-overview',
  'first-doctor/ipd': 'doc-ipd',
  'first-doctor/follow': 'doc-followups',
  'first-nurse/patients': 'nurse-patients',
  'first-nurse/tasks': 'nurse-tasks',
  'first-incharge/assign': 'ni-unassigned',
  'first-incharge/schedule': 'ni-schedule',
  'first-incharge/beds': 'ni-beds',
  'first-ot/requests': 'ot-requests',
  'first-ot/board': 'ot-board',
  'first-ot/day': 'ot-day',
  'first-pharmacist/sell': 'ph-counter',
  'first-pharmacist/dispense': 'ph-prescriptions',
  'first-pharmacist/stock-in': 'ph-purchase',
  'first-pharmacist/expiry': 'ph-expiry',
  'first-pharmacist/returns': 'ph-returns',
  'first-super-admin/plans': 'sa-plans',
  'first-super-admin/watch': 'sa-dashboard',
  'tut-opd/find': 'desk-patients',
  'tut-opd/visit': 'desk-new-opd',
  'tut-opd/bill': 'desk-opd',
  'tut-ipd/admit': 'desk-admit',
  'tut-ot/schedule': 'ot-requests',
  'tut-ot/preop': 'ot-board',
  'tut-pharmacy/purchase': 'ph-purchase',
  'tut-pharmacy/sell': 'ph-counter',
  'tut-day-close/collection': 'desk-daily',
};

const BY_ID = new Map(SHOTS.map((s) => [s.id, s]));

/** The screenshot for an answer id or a "guideId/stepId", or null. */
export function shotFor(key) {
  const shot = BY_ID.get(ATTACH[key]);
  return shot ? { ...shot, src: `/help/shots/${shot.id}.jpg` } : null;
}

export const _attach = ATTACH;
