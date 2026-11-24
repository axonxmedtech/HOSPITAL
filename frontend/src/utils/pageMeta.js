/**
 * What each dashboard screen's header says and offers, in one place.
 *
 *  - subtitle: one plain sentence on what the screen is for (never generated from the tab id).
 *  - search:   placeholder, only where the list below really filters by it.
 *  - add:      label of the header's add button, only where the screen has no add of its own.
 *
 * A screen missing from the table gets its title and nothing else, rather than a made-up line.
 */
const PAGES = {
  patients: {
    subtitle: 'Everyone registered, with their visits and admissions',
    search: 'Search by name, phone or patient ID',
    add: 'Add Patient',
  },
  appointments: {
    subtitle: 'Booked visits by day',
    search: 'Search by patient or doctor',
    add: 'Add Appointment',
  },
  opd: {
    subtitle: 'Patients waiting for, or seeing, a doctor',
    search: 'Search by patient, doctor or case number',
    add: 'New OPD',
  },
  ipd: {
    subtitle: 'Patients in a bed now — in the wards and in ICU',
    search: 'Search by patient, IPD number or doctor',
    add: 'Admit Patient',
  },
  icu: { subtitle: 'Beds, patients in critical care, housekeeping and history' },
  beds: { subtitle: 'Free beds, beds to clean and every bed by ward' },
  'follow-ups': {
    subtitle: 'Patients asked to come back, by due date',
    search: 'Search by patient or doctor',
  },
  ot: { subtitle: 'Surgery requests, the theatre board and history' },
  pathology: { subtitle: 'Lab tests ordered and their results' },
  wards: { subtitle: 'General wards, their beds, prices and nurse in-charge' },
  'icu-wards': { subtitle: 'Critical-care units, their beds and daily rates' },
  doctors: {
    subtitle: 'Doctors who consult and admit patients',
    search: 'Search doctors',
    add: 'Add Doctor',
  },
  pharmacists: {
    subtitle: 'Staff who sell and dispense medicines',
    search: 'Search pharmacists',
    add: 'Add Pharmacist',
  },
  receptionists: {
    subtitle: 'Front-desk staff',
    search: 'Search receptionists',
    add: 'Add Receptionist',
  },
  'ot-incharges': {
    subtitle: 'Staff who run the operation theatres',
    search: 'Search OT in-charges',
    add: 'Add OT In-charge',
  },
  'ot-theatres': { subtitle: 'Operation theatres that surgeries are booked into' },
  nurses: {
    subtitle: 'Nurses, their ward and shift',
    search: 'Search nurses',
    add: 'Add Nurse',
  },
  'nurse-assignments': { subtitle: 'The primary nurse of each admitted patient' },
  'nurse-tasks': { subtitle: 'Tasks given to nurses and where they stand', add: 'New Task' },
  'time-slots': { subtitle: 'Shift templates and appointment slots' },
  calendar: { subtitle: 'Surgeries, events and shifts by day' },
  billing: {
    subtitle: 'Every bill — pending, part paid, paid and cancelled',
    search: 'Search by bill number or patient',
  },
  'daily-collection': { subtitle: 'The day’s payments by mode, for shift closing' },
  fees: { subtitle: 'Consultation, case-paper and custom charges' },
  analytics: { subtitle: 'Patients, consultations, occupancy and revenue over time' },
  'ot-analytics': { subtitle: 'Surgical-care indicators for the period you choose' },
  'audit-logs': { subtitle: 'Who did what, and when', search: 'Search actions or details' },
  'quick-notes': { subtitle: 'One-click phrases for treatment notes' },
  'symptom-presets': { subtitle: 'One-click complaints for the Symptoms box' },
  'diagnosis-presets': { subtitle: 'One-click diagnoses for the Diagnosis box' },
  'prescription-presets': { subtitle: 'Sets of medicines a doctor adds in one click' },
  'in-clinic-presets': { subtitle: 'Sets of stock items given in the clinic' },
  settings: { subtitle: 'How your facility works: desk, billing, vitals, forms and more' },
  'pharmacy-billing': { subtitle: 'Every pharmacy sale' },
  inventory: { subtitle: 'Medicines in stock, batches and expiry' },
  'hospital-inventory': { subtitle: 'Equipment, consumables and service stock' },
  'plan-usage': { subtitle: 'What your plan includes and how much of it is in use' },
  support: { subtitle: 'Answers to common questions, and support tickets' },
};

export function pageMeta(tabId) {
  return PAGES[tabId] || {};
}

export default PAGES;
