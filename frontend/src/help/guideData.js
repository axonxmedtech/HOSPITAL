import {
  ADMIN,
  DOCTOR,
  INCHARGE,
  NURSE,
  OT,
  PHARMACIST,
  RECEPTION,
  SUPER_ADMIN,
} from './content/roles';

/**
 * Step-by-step guides shown under Help → Guides.
 *
 *  - setup:     getting a new hospital, clinic or pharmacy ready (admin), tailored to its plan.
 *  - first-day: what each role does and where, for a new member of staff.
 *  - tutorial:  one piece of work from start to finish across roles (an OPD visit, an admission…).
 *
 * A step can be limited to certain modules (`modules`, all required) or any other condition
 * (`when(user)`), so a clinic never sees ward steps and a hospital without OT never sees theatres.
 * `goTo` is a screen id of the reader's dashboard; the button only shows when they have it.
 * `who` names the role that does the step in a tutorial.
 */

const has = (user, m) => (user?.modules || []).includes(m);
const type = (user) => user?.hospitalType || 'HOSPITAL';
const pharmacyMode = (user) => {
  if (has(user, 'MULTI_PHARMACY')) return 'MULTI';
  if (has(user, 'SINGLE_PHARMACIST_ADMIN')) return 'SOLO';
  return 'SINGLE';
};
const notSolo = (user) => user?.receptionMode !== 'SOLO';

// ── Setting up a hospital or clinic ─────────────────────────────────────────

const facilitySteps = [
  {
    id: 'plan',
    title: 'Check what your plan includes',
    text: 'Administration → Plan & Usage shows the parts of the software you have (OPD, IPD, ICU, OT, Pharmacy…) and how many beds, doctors, staff logins and theatres you can add. To change the plan, call 9022915865.',
    goTo: 'plan-usage',
  },
  {
    id: 'doctors',
    title: 'Add your doctors',
    text: 'Staff → Doctors → Add Doctor. Enter name, specialization, phone, and the email and password the doctor will sign in with. Every OPD visit and admission needs a doctor, so do this first.',
    goTo: 'doctors',
  },
  {
    id: 'reception-mode',
    title: 'Decide who runs the front desk and billing',
    text: 'Settings → Operations Settings → Reception Mode. Separate reception: receptionists register patients and doctors only consult. Solo: the doctor does everything (good for a single-doctor clinic). Both: doctors and reception can both work the desk. In the same card choose who handles billing.',
    goTo: 'settings',
  },
  {
    id: 'receptionists',
    title: 'Add your receptionists',
    text: 'Staff → Receptionists → Add Receptionist with their email and password. Skip this in Solo mode — receptionist logins are switched off there.',
    goTo: 'receptionists',
    when: notSolo,
  },
  {
    id: 'fees',
    title: 'Set your consultation and case-paper fees',
    text: 'Finance → Fees → Edit. Until you set them, OPD bills use ₹500 + ₹100. New bills use the new fees straight away.',
    goTo: 'fees',
    modules: ['BILLING'],
  },
  {
    id: 'payment-timing',
    title: 'Choose when the OPD fee is taken',
    text: 'Settings → Print & Payment: take the fee at registration (before the doctor) or after the consultation. Here you also set what prints on case papers and bills.',
    goTo: 'settings',
    modules: ['BILLING'],
  },
  {
    id: 'wards',
    title: 'Create wards and beds',
    text: 'Rooms → Wards & Beds → Create Ward: name, bed price per day and number of beds — the beds are made for you. Create one ward per kind of room (general, semi-private, private…).',
    goTo: 'wards',
    modules: ['IPD'],
  },
  {
    id: 'icu',
    title: 'Set up ICU beds and ICU charts',
    text: 'Rooms → ICU Wards → add the ICU (unit type ICU, MICU, NICU or HDU), price and beds. Then in Settings choose the Ventilator Parameters, Severity Scores and ICU Alert Thresholds your unit uses.',
    goTo: 'icu-wards',
    modules: ['ICU'],
  },
  {
    id: 'nurse-login',
    title: 'Decide how nurses sign in',
    text: 'Settings → Operations Settings → Separate Nurse Login. On: every nurse has a login and charts under their own name. Off: only nurse in-charges sign in and pick "Performed By" when charting for a nurse.',
    goTo: 'settings',
    modules: ['NURSING'],
  },
  {
    id: 'nurses',
    title: 'Add nurses and give each ward an in-charge',
    text: 'Staff → Nurses → Add Nurse, choosing each nurse’s ward (a nurse without a ward is seen by no in-charge). Then on Rooms → Wards & Beds pick the Nurse Incharge on each ward card.',
    goTo: 'nurses',
    modules: ['NURSING'],
  },
  {
    id: 'ot-theatres',
    title: 'Add your operation theatres',
    text: 'Staff → OT Theatres → add each theatre with its turnover (cleaning) minutes. Surgeries are always scheduled into a theatre.',
    goTo: 'ot-theatres',
    modules: ['OT'],
  },
  {
    id: 'ot-rules',
    title: 'Set OT roles and safety steps',
    text: 'Settings → Operations Settings: switch on an OT in-charge if you have one, then add them under Staff → OT Incharge. Settings → OT Permissions sets who may schedule, start and close surgeries; OT Policies sets which checklists are required.',
    goTo: 'settings',
    modules: ['OT'],
  },
  {
    id: 'pharmacists',
    title: 'Add your pharmacists',
    text: 'Pharmacy → Pharmacists → Add Pharmacist. They sell at the counter, dispense ward prescriptions, and manage medicine stock and purchases.',
    goTo: 'pharmacists',
    modules: ['PHARMACY'],
  },
  {
    id: 'medicine-stock',
    title: 'Load your medicine stock',
    text: 'Inventory → Medicine Inventory: add the medicines you keep, with batch, expiry, quantity, price and reorder level. Doctors then see stock while prescribing and doses given are taken off it and billed.',
    goTo: 'inventory',
    modules: ['MEDICAL_INVENTORY'],
    when: (user) => user?.inClinic !== false,
  },
  {
    id: 'hospital-stock',
    title: 'Add saline, IV fluids and consumables',
    text: 'Inventory → Hospital Inventory → record a purchase: item, quantity, unit price, expiry and reorder level. Doctors can then order them on the ward and every unit given is billed.',
    goTo: 'hospital-inventory',
    modules: ['HOSPITAL_INVENTORY'],
  },
  {
    id: 'medicine-roles',
    title: 'Choose who may record medicines as given',
    text: 'Settings → Medicines: tick the roles (nurse, in-charge, doctor, admin, reception). All are allowed unless you change it.',
    goTo: 'settings',
    modules: ['IPD'],
  },
  {
    id: 'forms-vitals',
    title: 'Pick your vitals and forms',
    text: 'Settings → Vitals: switch vitals on or off and add your own. Settings → Nursing Records (or IPD Forms) turns each form on or off and sets who may edit it.',
    goTo: 'settings',
  },
  {
    id: 'presets',
    title: 'Save common notes and prescriptions as presets',
    text: 'Presets → Quick Notes, Symptom, Diagnosis and Prescription Presets. Doctors pick a preset to fill a consultation in one click.',
    goTo: 'prescription-presets',
    modules: ['OPD'],
  },
  {
    id: 'import',
    title: 'Bring in patients from your old software (optional)',
    text: 'Patients → Import Patients: upload the spreadsheet, check the preview (duplicates and errors are shown) and commit.',
    goTo: 'patients',
  },
  {
    id: 'test-run',
    title: 'Do a test run before going live',
    text: 'Register a test patient, create an OPD visit, let the doctor complete the consultation and collect the bill. If you have IPD, admit them, record a dose and discharge. Then open Daily Collection to see it all add up. Follow the "An OPD visit" and "Admission to discharge" guides for the clicks.',
    goTo: 'opd',
    modules: ['OPD'],
  },
];

// ── Setting up a pharmacy ───────────────────────────────────────────────────

const pharmacySteps = [
  {
    id: 'plan',
    title: 'Check what your plan includes',
    text: 'Plan & Usage shows your plan (Solo pharmacist, Single pharmacy or Multi-branch), its expiry and how many staff logins you can add. To upgrade, call 9022915865.',
    goTo: 'plan-usage',
  },
  {
    id: 'branches',
    title: 'Create your branches',
    text: 'Pharmacies → + Create Branch: name, address, phone, and the login email and password for that outlet. Each branch keeps its own stock and sales; Overview and Analytics can show one branch or all together.',
    goTo: 'pharmacies',
    when: (user) => pharmacyMode(user) === 'MULTI',
  },
  {
    id: 'pharmacists',
    title: 'Add your pharmacists',
    text: 'Pharmacists → Add Pharmacist with their email and password. They sign in on the pharmacy login with Department Access = Pharmacist and run the counter.',
    goTo: 'pharmacists',
    when: (user) => pharmacyMode(user) === 'SINGLE',
  },
  {
    id: 'solo-switch',
    title: 'Switch between Admin and Pharmacy',
    text: 'You are the only pharmacist, so the Pharmacy / Admin switch at the top of the screen moves you between the counter (sales, stock, purchases) and the admin side (reports, settings, plan).',
    when: (user) => pharmacyMode(user) === 'SOLO',
  },
  {
    id: 'barcode',
    title: 'Decide whether you use barcodes',
    text: 'Settings → Barcode Workflow. On: scan at the Billing Counter and print barcode labels from Inventory. Turn it off if you do not use barcodes.',
    goTo: 'settings',
  },
  {
    id: 'suppliers',
    title: 'Add suppliers and manufacturers',
    text: 'On the pharmacy side, open Suppliers and Manufacturers and add the ones you buy from. Purchases are recorded against a supplier.',
    goTo: 'suppliers',
  },
  {
    id: 'opening-stock',
    title: 'Enter your opening stock',
    text: 'Purchase Management → record each supplier invoice: medicine, batch, expiry, quantity and rates. Each purchase becomes its own batch, so older stock sells first. Set each medicine’s reorder level so low stock is flagged.',
    goTo: 'purchase',
  },
  {
    id: 'first-sale',
    title: 'Make a test sale',
    text: 'Billing Counter → add a medicine, set the quantity, choose the payment mode and complete the sale. The bill prints with batch and expiry, and the stock goes down.',
    goTo: 'billing',
  },
];

// ── First day, per role ─────────────────────────────────────────────────────

const firstDay = [
  {
    id: 'first-reception',
    kind: 'first-day',
    roles: [RECEPTION, 'DESK'],
    title: 'Your first day at the front desk',
    summary: 'Register patients, send them to the doctor, admit, bill and close the day.',
    steps: [
      {
        id: 'overview',
        title: 'Start on Overview',
        text: 'Overview shows today’s OPD, admissions and collections at a glance.',
        goTo: 'overview',
      },
      {
        id: 'register',
        title: 'Register a patient',
        text: 'Patients → Add Patient: name, phone, age and gender. Search before you register — one patient, one record.',
        goTo: 'patients',
      },
      {
        id: 'opd',
        title: 'Send them to a doctor',
        text: 'OPD → New OPD / Case: pick the patient and doctor. The patient joins that doctor’s queue; print the case paper from the row.',
        goTo: 'opd',
        modules: ['OPD'],
      },
      {
        id: 'collect',
        title: 'Collect the bill',
        text: 'In OPD, click Collect in the Bill column, choose Cash, Card or UPI and confirm. Part payments are fine — the rest stays as balance.',
        goTo: 'opd',
        modules: ['BILLING'],
      },
      {
        id: 'appointments',
        title: 'Book appointments',
        text: 'Appointments → add: patient, doctor, date and time.',
        goTo: 'appointments',
        modules: ['APPOINTMENTS'],
      },
      {
        id: 'admit',
        title: 'Admit a patient',
        text: 'IPD → Admit Patient, Admit on a doctor’s admission request, or Beds → Admit here on a free bed. The admission form opens right after: Save & Print Form + Consent, then Save & Mark as Admitted.',
        goTo: 'ipd',
        modules: ['IPD'],
      },
      {
        id: 'discharge',
        title: 'Discharge',
        text: 'When the doctor plans a discharge the patient shows under IPD → Ready to go. Click Checkout: collect the patient’s share, confirm, print the summary and bill together, and mark the bed cleaned.',
        goTo: 'ipd',
        modules: ['IPD'],
      },
      {
        id: 'close',
        title: 'Close the day',
        text: 'Daily Collection totals the day’s cash, card and UPI with who took each payment. Print it for handover.',
        goTo: 'daily-collection',
        modules: ['BILLING'],
      },
      {
        id: 'help',
        title: 'Stuck? Ask Help',
        text: 'Press Ctrl+K (⌘K on Mac) to jump to any screen, or open Help & Support and ask in your own words.',
      },
    ],
  },
  {
    id: 'first-doctor',
    kind: 'first-day',
    roles: [DOCTOR],
    title: 'Your first day as a doctor',
    summary: 'See your queue, consult, admit, prescribe and discharge.',
    steps: [
      {
        id: 'queue',
        title: 'See who is waiting',
        text: 'Overview shows your current and next patient; OPD (Live) lists your whole queue.',
        goTo: 'overview',
      },
      {
        id: 'consult',
        title: 'Consult',
        text: 'Open the visit → Start Consultation. Record complaints, findings, diagnosis and prescription (presets fill them in one click), then Complete Consultation.',
        goTo: 'opd',
        modules: ['OPD'],
      },
      {
        id: 'admit',
        title: 'Advise admission',
        text: 'Click Admit to IPD instead of Complete Consultation. The front desk sees your request and gives the bed.',
        modules: ['IPD'],
      },
      {
        id: 'follow',
        title: 'Follow-ups',
        text: 'Follow-ups lists patients you asked to come back, by due date.',
        goTo: 'follow-ups',
      },
      {
        id: 'ipd',
        title: 'Your admitted patients',
        text: 'IPD → open a patient. Prescribe under Medication → + Add Medicine (stock shows as you type), write Doctors Round Notes, and check vitals and charts.',
        goTo: 'ipd',
        modules: ['IPD'],
      },
      {
        id: 'discharge',
        title: 'Plan the discharge',
        text: 'Plan Discharge on the IPD page: type and summary. The front desk settles the bill and confirms.',
        modules: ['IPD'],
      },
      {
        id: 'surgery',
        title: 'Request a surgery',
        text: 'Create Surgery Request on the IPD page; follow it on Operation Theatre.',
        goTo: 'ot',
        modules: ['OT'],
      },
    ],
  },
  {
    id: 'first-nurse',
    kind: 'first-day',
    roles: [NURSE],
    title: 'Your first day as a nurse',
    summary: 'Find your patients, chart vitals, give medicines and finish your tasks.',
    steps: [
      {
        id: 'patients',
        title: 'Find your patients',
        text: 'My Patients lists the patients your in-charge gave you. Click one to open the chart.',
        goTo: 'my-patients',
      },
      {
        id: 'vitals',
        title: 'Record vitals',
        text: 'On the chart: Vitals → Record Vitals. Out-of-range values are highlighted for the doctor.',
      },
      {
        id: 'meds',
        title: 'Give medicines and saline',
        text: 'Medication → Record Medication Administration: pick the order, keep Status = Given, check the quantity and save. Refused or skipped? Choose that status instead.',
      },
      {
        id: 'notes',
        title: 'Write nursing notes',
        text: 'Nurse Round Notes on the chart; every note is timed and signed with your name.',
      },
      {
        id: 'tasks',
        title: 'Do your tasks',
        text: 'My Tasks lists what you were asked to do. Start Task when you begin, Complete Task when done.',
        goTo: 'my-tasks',
      },
      {
        id: 'shifts',
        title: 'Check your shifts',
        text: 'My Shifts shows your roster; My Attendance your days marked present.',
        goTo: 'my-shifts',
      },
    ],
  },
  {
    id: 'first-incharge',
    kind: 'first-day',
    roles: [INCHARGE],
    title: 'Your first day as nurse in-charge',
    summary: 'Run your ward: nurses, patients, roster, attendance and beds.',
    steps: [
      {
        id: 'dashboard',
        title: 'Start on Dashboard',
        text: 'Your wards’ patients, nurses on duty and open tasks.',
        goTo: 'dashboard',
      },
      {
        id: 'nurses',
        title: 'Know your nurses',
        text: 'My Nurses lists the nurses in your wards. A missing nurse needs their ward set by the admin.',
        goTo: 'my-nurses',
      },
      {
        id: 'assign',
        title: 'Assign patients',
        text: 'Unassigned Patients → Assign primary nurse. Reassign later from My Ward Patients.',
        goTo: 'unassigned-patients',
      },
      {
        id: 'schedule',
        title: 'Make the roster',
        text: 'Schedule sets shifts; Attendance marks who came; Coverage shows who covers your ward on a date.',
        goTo: 'schedule',
      },
      {
        id: 'beds',
        title: 'Keep beds ready',
        text: 'Beds: Mark cleaned after a discharge, or put a bed under maintenance.',
        goTo: 'beds',
      },
      {
        id: 'chart',
        title: 'Chart for a nurse (if nurses have no login)',
        text: 'When you record care for a nurse, choose who did it: once at the top of Due Now (Recording for), or in "Performed By" on the chart.',
      },
    ],
  },
  {
    id: 'first-ot',
    kind: 'first-day',
    roles: [OT],
    title: 'Your first day as OT in-charge',
    summary: 'Schedule requests and move each surgery through the board.',
    steps: [
      {
        id: 'requests',
        title: 'Pick up requests',
        text: 'Requests lists surgeries doctors asked for, most urgent first.',
        goTo: 'requests',
      },
      {
        id: 'schedule',
        title: 'Schedule',
        text: 'Open a request → Schedule: theatre, date, time and team. Clashes are refused.',
        goTo: 'requests',
      },
      {
        id: 'board',
        title: 'Run the board',
        text: 'OT Board: Pre-op → Checklist → Start → Complete → Recovery → Close. Each step unlocks the next.',
        goTo: 'board',
      },
      {
        id: 'day',
        title: 'Print the day list',
        text: 'Today’s List shows every case by theatre and time; print it for the notice board.',
        goTo: 'day',
      },
    ],
  },
  {
    id: 'first-pharmacist',
    kind: 'first-day',
    roles: [PHARMACIST],
    title: 'Your first day as pharmacist',
    summary: 'Sell, dispense, receive stock and watch expiry.',
    steps: [
      {
        id: 'dashboard',
        title: 'Start on Dashboard',
        text: 'Today’s sales, low stock and expiring batches.',
        goTo: 'dashboard',
      },
      {
        id: 'sell',
        title: 'Sell at the counter',
        text: 'Billing Counter: add medicines, quantity and payment mode, complete the sale and print.',
        goTo: 'billing',
      },
      {
        id: 'dispense',
        title: 'Dispense ward prescriptions',
        text: 'Prescriptions → open an order → Dispense with the quantity.',
        goTo: 'prescriptions',
        when: (u) => type(u) !== 'PHARMACY',
      },
      {
        id: 'stock-in',
        title: 'Receive stock',
        text: 'Purchase Management: record the supplier invoice with batch and expiry.',
        goTo: 'purchase',
      },
      {
        id: 'expiry',
        title: 'Watch expiry',
        text: 'Expiry Management lists batches by expiry; return expired stock to the supplier from Returns & Refunds.',
        goTo: 'expiry',
      },
      {
        id: 'returns',
        title: 'Customer returns',
        text: 'Returns & Refunds → patient return: find the bill number, enter quantities, choose whether it goes back on the shelf.',
        goTo: 'returns',
      },
    ],
  },
  {
    id: 'first-super-admin',
    kind: 'first-day',
    roles: [SUPER_ADMIN],
    title: 'Running AxonX as Super Admin',
    summary: 'Onboard tenants, set plans and limits, manage demos and renewals.',
    steps: [
      {
        id: 'plans',
        title: 'Prepare plans',
        text: 'Hospital / Clinic / Pharmacy → Plans: modules, price and limits (blank = unlimited, 0 = not included).',
        goTo: 'hospital:plans',
      },
      {
        id: 'create',
        title: 'Create a tenant',
        text: 'Open Hospitals (or Clinics / Pharmacies) → add: name, admin login, plan and period. Tick Demo account and enter days for a trial.',
        goTo: 'hospital:hospitals',
      },
      {
        id: 'watch',
        title: 'Watch renewals and demos',
        text: 'Dashboard lists demos ending soon, renewals and tenants near a limit, with quick extend buttons.',
        goTo: 'dashboard',
      },
      {
        id: 'tickets',
        title: 'Answer tickets',
        text: 'Tickets lists support tickets raised by hospital admins.',
        goTo: 'hospital:tickets',
      },
      {
        id: 'help',
        title: 'Improve the help',
        text: 'Help Insights lists what users asked that help could not answer, and answers rated not helpful. Write those answers into the help content, then Mark answered.',
        goTo: 'help_insights',
      },
    ],
  },
];

// ── Tutorials: one piece of work, start to finish ───────────────────────────

const tutorials = [
  {
    id: 'tut-opd',
    kind: 'tutorial',
    roles: [ADMIN, RECEPTION, DOCTOR, 'DESK'],
    modules: ['OPD'],
    title: 'An OPD visit, start to finish',
    summary: 'From the patient walking in to the bill and follow-up.',
    steps: [
      {
        id: 'find',
        who: 'Front desk',
        title: 'Find or register the patient',
        text: 'Patients: search by name, phone or ID; Add Patient if they are new.',
        goTo: 'patients',
      },
      {
        id: 'visit',
        who: 'Front desk',
        title: 'Create the OPD visit',
        text: 'OPD → New OPD / Case: patient and doctor. If your hospital takes the fee first, choose the payment mode here.',
        goTo: 'opd',
      },
      {
        id: 'consult',
        who: 'Doctor',
        title: 'Consultation',
        text: 'The doctor opens the visit → Start Consultation, records findings and prescription, then Complete Consultation (or Admit to IPD).',
      },
      {
        id: 'print',
        who: 'Front desk',
        title: 'Print the prescription',
        text: 'In OPD, Prescription on the visit row views and prints it.',
        goTo: 'opd',
      },
      {
        id: 'bill',
        who: 'Front desk',
        title: 'Collect the bill',
        text: 'OPD → Collect in the Bill column (or from Billing). Cash, Card or UPI; part payment allowed.',
        goTo: 'opd',
        modules: ['BILLING'],
      },
      {
        id: 'followup',
        who: 'Doctor',
        title: 'Follow-up',
        text: 'A follow-up date set in the consultation puts the patient on Follow-ups for that day.',
        goTo: 'follow-ups',
      },
    ],
  },
  {
    id: 'tut-ipd',
    kind: 'tutorial',
    roles: [ADMIN, RECEPTION, DOCTOR, NURSE, INCHARGE, 'DESK'],
    modules: ['IPD'],
    title: 'Admission to discharge',
    summary: 'Admit, treat, give medicines, bill and discharge an inpatient.',
    steps: [
      {
        id: 'admit',
        who: 'Front desk',
        title: 'Admit',
        text: 'IPD → Admit Patient, Admit on the doctor’s request, or Beds → Admit here: doctor, ward, free bed, type, diagnosis and advance.',
        goTo: 'ipd',
      },
      {
        id: 'form',
        who: 'Front desk',
        title: 'Complete the admission form',
        text: 'It opens right after Admit: check it, Save & Print Form + Consent for signing, then Save & Mark as Admitted. Later: IPD row menu → Complete admission form.',
      },
      {
        id: 'orders',
        who: 'Doctor',
        title: 'Round and prescribe',
        text: 'IPD → Start ward round: one screen per patient — vitals, medicines, tests, diet, the round note and + Medicine / + From preset — then Save & next.',
      },
      {
        id: 'care',
        who: 'Nurse',
        title: 'Chart and give doses',
        text: 'Due Now lists every dose, vitals round and sample due: Given ✓, Record vitals, Collected ✓. Doses from stock leave the inventory and go on the bill.',
      },
      {
        id: 'advance',
        who: 'Front desk',
        title: 'Take advances',
        text: 'IPD row menu (⋮) → Advance & package, or Take Payment on the IPD page’s Billing card. Print bill shows the running bill.',
        modules: ['BILLING'],
      },
      {
        id: 'plan',
        who: 'Doctor',
        title: 'Plan the discharge',
        text: 'Plan Discharge: type (normal, LAMA, referred…) and summary.',
      },
      {
        id: 'settle',
        who: 'Front desk',
        title: 'Settle and discharge',
        text: 'IPD → Ready to go → Checkout: the patient’s share (insurer’s share taken off) is collected, unused advance returned, and the summary and bill print together.',
        modules: ['BILLING'],
      },
      {
        id: 'bed',
        who: 'Nurse in-charge / front desk',
        title: 'Free the bed',
        text: 'The bed goes to cleaning: Mark bed cleaned at the end of the checkout, or Beds → Needs cleaning → Mark cleaned.',
      },
    ],
  },
  {
    id: 'tut-ot',
    kind: 'tutorial',
    roles: [ADMIN, RECEPTION, DOCTOR, OT, 'DESK'],
    modules: ['OT'],
    title: 'A surgery, from request to close',
    summary: 'Request, schedule, run and close a surgery.',
    steps: [
      {
        id: 'request',
        who: 'Doctor',
        title: 'Request',
        text: 'IPD page → Create Surgery Request: procedure, urgency, preferred date.',
      },
      {
        id: 'schedule',
        who: 'OT in-charge / front desk',
        title: 'Schedule',
        text: 'Open the request → Schedule: theatre, date, time and team.',
        goTo: 'requests',
      },
      {
        id: 'preop',
        who: 'OT team',
        title: 'Pre-op and checklist',
        text: 'On the board: pre-operative checklist, anaesthesia clearance, then the WHO time-out.',
        goTo: 'board',
      },
      {
        id: 'run',
        who: 'OT team',
        title: 'Start and complete',
        text: 'Start when the patient is in theatre; Complete when the procedure ends, with notes.',
      },
      {
        id: 'close',
        who: 'OT team',
        title: 'Recovery and close',
        text: 'Recovery until fit to return; Close releases the theatre for turnover cleaning.',
      },
    ],
  },
  {
    id: 'tut-pharmacy',
    kind: 'tutorial',
    roles: [ADMIN, PHARMACIST],
    modules: ['PHARMACY'],
    title: 'Pharmacy: stock in to sale',
    summary: 'Buy, store, sell, return and handle expiry.',
    steps: [
      {
        id: 'supplier',
        title: 'Supplier',
        text: 'Suppliers → add the supplier once.',
        goTo: 'suppliers',
      },
      {
        id: 'purchase',
        title: 'Purchase',
        text: 'Purchase Management → record the invoice with batch, expiry, quantity and rates.',
        goTo: 'purchase',
      },
      {
        id: 'sell',
        title: 'Sell',
        text: 'Billing Counter → add medicines, payment mode, complete and print.',
        goTo: 'billing',
      },
      {
        id: 'return',
        title: 'Returns',
        text: 'Returns & Refunds → patient return by bill number; refund is calculated.',
        goTo: 'returns',
      },
      {
        id: 'expiry',
        title: 'Expiry',
        text: 'Expiry Management → return expiring stock to the supplier in time.',
        goTo: 'expiry',
      },
    ],
  },
  {
    id: 'tut-day-close',
    kind: 'tutorial',
    roles: [ADMIN, RECEPTION, 'DESK'],
    modules: ['BILLING'],
    title: 'Closing the day',
    summary: 'Check the cash, the OPD register and pending bills before you leave.',
    steps: [
      {
        id: 'collection',
        title: 'Daily Collection',
        text: 'Totals by Cash, Card and UPI with who took each payment. Match it with the cash drawer and print for handover.',
        goTo: 'daily-collection',
      },
      {
        id: 'register',
        title: 'OPD register',
        text: 'OPD → Date view → Download PDF for the day’s register.',
        goTo: 'opd',
        modules: ['OPD'],
      },
      {
        id: 'pending',
        title: 'Unpaid bills',
        text: 'Billing lists bills still pending or partial — collect or follow up.',
        goTo: 'billing',
      },
    ],
  },
];

const setupGuides = [
  {
    id: 'setup-hospital',
    kind: 'setup',
    roles: [ADMIN],
    types: ['HOSPITAL'],
    title: 'Set up your hospital',
    summary: 'Everything a new hospital needs before the first patient, matched to your plan.',
    steps: facilitySteps,
  },
  {
    id: 'setup-clinic',
    kind: 'setup',
    roles: [ADMIN],
    types: ['CLINIC'],
    title: 'Set up your clinic',
    summary: 'Doctors, front desk, fees and medicines — ready for your first patient.',
    steps: facilitySteps,
  },
  {
    id: 'setup-pharmacy',
    kind: 'setup',
    roles: [ADMIN],
    types: ['PHARMACY'],
    title: 'Set up your pharmacy',
    summary: 'Staff or branches, suppliers, opening stock and your first sale.',
    steps: pharmacySteps,
  },
];

export const ALL_GUIDES = [...setupGuides, ...firstDay, ...tutorials];

const stepApplies = (step, user) =>
  (step.modules || []).every((m) => has(user, m)) && (!step.when || step.when(user));

/** The guides for this user, each with only the steps that apply to their setup; setup first. */
export function guidesFor(user, helpRoles) {
  if (!user) return [];
  const roles = new Set(helpRoles);
  return ALL_GUIDES.filter(
    (g) =>
      g.roles.some((r) => roles.has(r)) &&
      (!g.types || g.types.includes(type(user))) &&
      (g.modules || []).every((m) => has(user, m))
  )
    .map((g) => ({ ...g, steps: g.steps.filter((s) => stepApplies(s, user)) }))
    .filter((g) => g.steps.length > 0);
}

// Progress (ticked steps) lives in progressStore.js, which also saves it to the server.

export const KINDS = [
  ['setup', 'Set up'],
  ['first-day', 'Getting started'],
  ['tutorial', 'How-to tutorials'],
];

/** Exported for tests. */
export const _internals = { facilitySteps, pharmacySteps, pharmacyMode };
