import { describe, expect, it } from 'vitest';
import { startingBedId, startingWardId } from './IcuAdmitModal';

describe('ICU admit pre-selection', () => {
  it('starts on the ward "Admit here" came from, else the only ward', () => {
    expect(startingWardId([{ wardId: 1 }, { wardId: 2 }], { wardId: 2, bedId: 9 })).toBe('2');
    expect(startingWardId([{ wardId: 5 }], null)).toBe('5');
    expect(startingWardId([{ wardId: 1 }, { wardId: 2 }], null)).toBe('');
  });

  it('pre-selects the "Admit here" bed only in its own ward, else the only free bed', () => {
    const beds = [{ bedId: 8 }, { bedId: 9 }];
    expect(startingBedId(beds, { wardId: 2, bedId: 9 }, '2')).toBe('9');
    expect(startingBedId(beds, { wardId: 2, bedId: 9 }, '3')).toBe('');
    expect(startingBedId([{ bedId: 4 }], null, '3')).toBe('4');
  });
});
