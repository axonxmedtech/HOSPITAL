import {
  ADMIN,
  ALL,
  DOCTOR,
  INCHARGE,
  NURSE,
  OT,
  PHARMACIST,
  RECEPTION,
  SUPER_ADMIN,
} from './content/roles';

/**
 * On-screen tours: each step can open a screen (`goTo`, the first one this dashboard has) and
 * highlight something on it (`target`). A step whose target is missing — a smaller plan, a phone
 * screen — still shows, centred, so a tour never gets stuck.
 *
 * target: a CSS selector, or { text } to find a button or link by its exact label.
 * HEADING highlights the open screen's title.
 */
export const HEADING = 'main h1, main h2';

const screen = (goTo, title, text, extra = {}) => ({
  goTo,
  target: HEADING,
  title,
  text,
  ...extra,
});

export const TOURS = [
  {
    id: 'basics',
    roles: [ALL],
    title: 'Find your way around',
    summary: 'The menu, help, the command bar and your account — in one minute.',
    steps: [
      {
        title: 'Welcome to AxonX',
        text: 'A one-minute look at where everything is. Use Next (or the → key); Esc ends the tour at any time.',
      },
      {
        target: 'aside nav',
        title: 'Your menu',
        text: 'Every screen you can use is here. Groups open with a click. You only see the screens your role and your plan include.',
        placement: 'right',
      },
      {
        target: '[aria-label="Help for this screen"]',
        title: 'Help on every screen',
        text: 'Ask a question in your own words, follow step-by-step guides with screenshots, or take a tour. F1 opens it too.',
      },
      {
        title: 'Jump anywhere with Ctrl+K',
        text: 'Press Ctrl+K (⌘K on Mac) and type a screen name — “opd”, “beds”, “billing” — or a question. Press ? to see every shortcut.',
      },
      {
        target: '[aria-label="User menu"]',
        title: 'Your account',
        text: 'Profile Settings (your details and password) and Sign Out are here.',
      },
      {
        title: 'You’re all set',
        text: 'The tour for your own work is under Help → Guides → Tours, whenever you want it.',
      },
    ],
  },
  {
    id: 'desk',
    roles: [RECEPTION, 'DESK'],
    title: 'Front desk tour',
    summary: 'Patients, OPD, admissions, billing and the day’s collection.',
    steps: [
      {
        goTo: 'patients',
        target: { text: 'Add Patient' },
        title: 'Register patients',
        text: 'Search first — one patient, one record. New patients are added here.',
      },
      {
        goTo: 'opd',
        target: { text: 'New OPD / Case' },
        title: 'Send a patient to the doctor',
        text: 'New OPD / Case puts the patient in the doctor’s queue. The list shows each visit’s bill with a Collect button.',
        modules: ['OPD'],
      },
      {
        goTo: 'ipd',
        target: { text: 'Admit Patient' },
        title: 'Admit',
        text: 'Admit directly here, or Admit on a doctor’s admission request. The list below has Ready to go and Form pending filters, and each row’s menu does the desk’s jobs.',
        modules: ['IPD'],
      },
      screen(
        'billing',
        'Billing',
        'Every bill with what is paid and pending. Collect, discount and print from here.',
        {
          modules: ['BILLING'],
        }
      ),
      screen(
        'daily-collection',
        'Close the day',
        'The day’s cash, card and UPI with who took each payment — print it for handover.',
        {
          modules: ['BILLING'],
        }
      ),
    ],
  },
  {
    id: 'doctor',
    roles: [DOCTOR],
    title: 'Doctor tour',
    summary: 'Your queue, consultations, admitted patients and follow-ups.',
    steps: [
      screen(
        'overview',
        'Your day',
        'Your current and next patient, the queue and today’s follow-ups.'
      ),
      screen(
        'opd',
        'OPD queue',
        'Open a visit → Start Consultation. Complete it, or Admit to IPD to send the patient to the front desk for a bed.',
        {
          modules: ['OPD'],
        }
      ),
      screen(
        'ipd',
        'Admitted patients',
        'Start ward round goes through your patients one screen at a time: vitals, medicines, tests, diet and your note, then Save & next.',
        {
          modules: ['IPD'],
        }
      ),
      screen('follow-ups', 'Follow-ups', 'Patients you asked to come back, by due date.'),
    ],
  },
  {
    id: 'nurse',
    roles: [NURSE],
    title: 'Nurse tour',
    summary: 'Your patients, tasks and shifts.',
    steps: [
      screen(
        'due-now',
        'Due now',
        'Every dose, vitals round and sample due across your patients — one tap each.'
      ),
      screen(
        'my-patients',
        'Your patients',
        'Patients in your care. Open one for the full chart: Summary, Orders, Charts and Forms.'
      ),
      screen('my-tasks', 'Your tasks', 'Start Task when you begin, Complete Task when done.'),
      screen('my-shifts', 'Your shifts', 'Your duty roster.'),
    ],
  },
  {
    id: 'incharge',
    roles: [INCHARGE],
    title: 'Nurse in-charge tour',
    summary: 'Assign patients, plan the roster and keep beds ready.',
    steps: [
      screen(
        'due-now',
        'Due now',
        'Doses, vitals and samples due in your wards. With a shared nurse login, choose Recording for once at the top.'
      ),
      screen(
        'unassigned-patients',
        'Patients without a nurse',
        'Assign a primary nurse to every admitted patient in your wards.'
      ),
      screen(
        'schedule',
        'Roster',
        'Set each nurse’s shifts; Attendance and Coverage are next to it.'
      ),
      screen('beds', 'Beds', 'Mark cleaned after a discharge, or put a bed under maintenance.'),
    ],
  },
  {
    id: 'ot',
    roles: [OT],
    title: 'OT tour',
    summary: 'Requests, the board and the day’s list.',
    steps: [
      screen(
        'requests',
        'Requests',
        'Surgeries the doctors asked for, most urgent first. Open one to schedule it.'
      ),
      screen(
        'board',
        'OT Board',
        'Each surgery moves Pre-op → Checklist → Start → Complete → Recovery → Close.'
      ),
      screen('day', 'Today’s List', 'Every case of the day by theatre and time, ready to print.'),
    ],
  },
  {
    id: 'pharmacist',
    roles: [PHARMACIST],
    title: 'Pharmacy tour',
    summary: 'The counter, purchases and expiry.',
    steps: [
      screen(
        'billing',
        'Billing Counter',
        'Add medicines, quantity and payment mode, then complete and print the sale.'
      ),
      screen(
        'purchase',
        'Purchases',
        'Record supplier invoices with batch and expiry to add stock.'
      ),
      screen('expiry', 'Expiry', 'Batches by expiry date — return them to the supplier in time.'),
    ],
  },
  {
    id: 'admin',
    roles: [ADMIN],
    types: ['HOSPITAL', 'CLINIC'],
    title: 'Admin tour',
    summary: 'Setup, staff, settings and your plan.',
    steps: [
      {
        goTo: 'overview',
        target:
          'section[aria-labelledby="setup-checklist-title"], section[aria-label="Setup guide"]',
        title: 'Your setup checklist',
        text: 'What is still missing before your staff can start, with a Set up button for each. It ticks itself as you go.',
      },
      screen(
        'doctors',
        'Staff',
        'Add doctors here; receptionists, nurses and pharmacists sit next to it under Staff.'
      ),
      screen(
        'settings',
        'Settings',
        'Reception mode, billing, nurse logins, vitals, forms, OT rules and more.'
      ),
      screen(
        'plan-usage',
        'Plan & Usage',
        'What your plan includes, how much you use and when it renews.'
      ),
    ],
  },
  {
    id: 'pharmacy-admin',
    roles: [ADMIN],
    types: ['PHARMACY'],
    title: 'Pharmacy owner tour',
    summary: 'Staff or branches, sales, settings and your plan.',
    steps: [
      screen('overview', 'Overview', 'Today’s sales and stock at a glance.'),
      screen('pharmacists', 'Pharmacists', 'Add the pharmacists who run your counter.'),
      screen('pharmacies', 'Branches', 'Create a branch for each outlet; each gets its own login.'),
      screen('settings', 'Settings', 'Barcode scanning on or off.'),
      screen('plan-usage', 'Plan & Usage', 'Your plan, limits and renewal.'),
    ],
  },
  {
    id: 'super-admin',
    roles: [SUPER_ADMIN],
    title: 'Super Admin tour',
    summary: 'Tenants, plans, demos and renewals.',
    steps: [
      screen('dashboard', 'Dashboard', 'Demos ending, renewals due and tenants close to a limit.'),
      screen(
        'hospital:hospitals',
        'Tenants',
        'Create and manage hospitals here; clinics and pharmacies have their own lists.'
      ),
      screen('hospital:plans', 'Plans', 'Modules, prices and limits for each plan.'),
      screen(
        'help_insights',
        'Help Insights',
        'Questions users could not find in help, and how each answer was rated — write the missing answers.'
      ),
    ],
  },
];

const has = (user, m) => (user?.modules || []).includes(m);

/**
 * Tours for this user, with only the steps their plan and dashboard can show. `screens` is the
 * set of screen ids on the current dashboard; a step for a screen it lacks is dropped.
 */
export function toursFor(user, helpRoles, screens) {
  if (!user) return [];
  const roles = new Set(helpRoles);
  const type = user.hospitalType || 'HOSPITAL';
  return TOURS.filter(
    (t) =>
      (t.roles.includes(ALL) || t.roles.some((r) => roles.has(r))) &&
      (!t.types || t.types.includes(type))
  )
    .map((t) => ({
      ...t,
      steps: t.steps.filter(
        (s) =>
          (s.modules || []).every((m) => has(user, m)) &&
          (!s.goTo || !screens || screens.has(s.goTo))
      ),
    }))
    .filter((t) => t.steps.length > 0);
}
