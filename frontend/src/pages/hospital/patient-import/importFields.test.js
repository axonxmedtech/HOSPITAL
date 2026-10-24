import { describe, expect, it } from 'vitest';
import {
  buildImportChoices,
  EXCLUDE,
  fileError,
  MAX_FILE_BYTES,
  suggestMapping,
  validateHeaders,
} from './importFields';

describe('patient import column choices', () => {
  it('does not silently exclude unmapped columns', () => {
    expect(() => buildImportChoices(['Name', 'Notes'], ['name', ''])).toThrow(
      'every source column'
    );
    expect(buildImportChoices(['Name', 'Notes'], ['name', EXCLUDE])).toEqual({
      mapping: { Name: 'name' },
      excludedColumns: ['Notes'],
    });
  });
  it('requires name and refuses duplicate or unknown target fields', () => {
    expect(() => buildImportChoices(['Name'], [EXCLUDE])).toThrow('Full name');
    expect(() => buildImportChoices(['Name', 'Other'], ['name', 'name'])).toThrow('only once');
    expect(() => buildImportChoices(['Name', 'Other'], ['name', 'hospitalId'])).toThrow(
      'only once'
    );
  });
  it('suggests unambiguous registry aliases only', () => {
    expect(suggestMapping(['Patient Name', 'MRN', 'Mobile', 'DOB', 'Notes'])).toEqual([
      'name',
      'legacyId',
      'phone',
      'dateOfBirth',
      '',
    ]);
    expect(suggestMapping(['Phone', 'Mobile', 'Contact'])).toEqual(['', '', '']);
  });
  it('validates headers without inspecting or matching patients', () => {
    expect(validateHeaders([' Name ', 'Phone'])).toEqual(['Name', 'Phone']);
    for (const headers of [[], ['Name', ' name '], [''], ['a'.repeat(2001)], Array(101).fill('X')])
      expect(() => validateHeaders(headers)).toThrow();
  });
  it('accepts CSV/XLSX and rejects unsupported, empty and oversized files', () => {
    expect(fileError({ name: 'a.CSV', size: MAX_FILE_BYTES })).toBeNull();
    expect(fileError({ name: 'a.xlsx', size: 10 })).toBeNull();
    expect(fileError({ name: 'a.xls', size: 10 })).toContain('.csv or .xlsx');
    expect(fileError({ name: 'a.csv', size: 0 })).toContain('empty');
    expect(fileError({ name: 'a.csv', size: MAX_FILE_BYTES + 1 })).toContain('50 MiB');
  });
});
