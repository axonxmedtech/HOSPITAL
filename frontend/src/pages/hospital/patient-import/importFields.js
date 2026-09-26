// Mirrors ImportFieldRegistry. These are mapping suggestions, never patient matching rules.
export const IMPORT_FIELDS = [
  {
    key: 'name',
    label: 'Full name (required)',
    aliases: ['name', 'patient name', 'full name', 'patientname', 'name of patient'],
  },
  {
    key: 'legacyId',
    label: 'Old patient ID / MRN',
    aliases: [
      'mrn',
      'patient id',
      'patient no',
      'patient number',
      'old patient id',
      'old id',
      'uhid',
      'registration no',
      'reg no',
      'registration number',
      'card no',
      'old card no',
    ],
  },
  { key: 'gender', label: 'Gender', aliases: ['gender', 'sex'] },
  {
    key: 'phone',
    label: 'Phone',
    aliases: [
      'phone',
      'phone no',
      'phone number',
      'ph no',
      'mobile',
      'mobile no',
      'mobile number',
      'mob',
      'mob no',
      'cell',
      'cell no',
      'contact no',
      'contact number',
    ],
  },
  { key: 'email', label: 'Email', aliases: ['email', 'email id', 'e mail', 'email address'] },
  {
    key: 'dateOfBirth',
    label: 'Date of birth',
    aliases: ['dob', 'date of birth', 'birth date', 'birthdate', 'd o b'],
  },
  { key: 'address', label: 'Address', aliases: ['address', 'addr', 'residential address'] },
  {
    key: 'medicalHistory',
    label: 'Medical history',
    aliases: ['medical history', 'past medical history'],
  },
];
export const EXCLUDE = '__exclude__';
export const MAX_FILE_BYTES = 50 * 1024 * 1024;
export const fileError = (file) => {
  if (!file || !/\.(csv|xlsx)$/i.test(file.name)) return 'Choose a .csv or .xlsx file.';
  if (!file.size) return 'The file is empty. Choose a file containing patient data.';
  if (file.size > MAX_FILE_BYTES) return 'The file is too large. The maximum is 50 MiB.';
  return null;
};
export const validateHeaders = (values) => {
  const headers = values.map((value) => String(value ?? '').trim());
  if (!headers.length || headers.length > 100)
    throw new Error('Use a header row with between 1 and 100 named columns.');
  const normalized = headers.map((h) => h.replace(/\s+/g, ' ').toLowerCase());
  if (headers.some((h) => !h || h.length > 2000) || new Set(normalized).size !== headers.length) {
    throw new Error(
      'Every column needs a unique, nonblank header no longer than 2,000 characters.'
    );
  }
  return headers;
};
export const suggestMapping = (headers) => {
  const matches = headers.map(
    (h) =>
      IMPORT_FIELDS.find((f) =>
        f.aliases.includes(
          h
            .toLowerCase()
            .replace(/[.\-_/#():,;]/g, ' ')
            .replace(/\s+/g, ' ')
            .trim()
        )
      )?.key || ''
  );
  return matches.map((key) => (key && matches.filter((m) => m === key).length === 1 ? key : ''));
};
export const buildImportChoices = (headers, choices) => {
  if (choices.length !== headers.length || choices.some((c) => !c))
    throw new Error('Choose a patient field or “Do not import” for every source column.');
  const targets = choices.filter((c) => c !== EXCLUDE);
  if (!targets.includes('name')) throw new Error('Map one source column to Full name (required).');
  if (
    new Set(targets).size !== targets.length ||
    targets.some((c) => !IMPORT_FIELDS.some((f) => f.key === c))
  )
    throw new Error('Each patient field may be mapped only once.');
  return {
    mapping: Object.fromEntries(
      headers.flatMap((h, i) => (choices[i] === EXCLUDE ? [] : [[h, choices[i]]]))
    ),
    excludedColumns: headers.filter((_, i) => choices[i] === EXCLUDE),
  };
};
