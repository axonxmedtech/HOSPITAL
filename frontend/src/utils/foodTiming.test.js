import { describe, expect, it } from 'vitest';
import {
  describeFoodTiming,
  FOOD_TIMING_OPTIONS,
  isFoodTimingApplicable,
  getFoodTimingTranslation,
} from './foodTiming';

describe('food timing', () => {
  it('offers exactly the vocabulary the server accepts', () => {
    expect(FOOD_TIMING_OPTIONS.map(([v]) => v)).toEqual([
      'BEFORE_FOOD',
      'AFTER_FOOD',
      'WITH_FOOD',
      'NOT_SPECIFIED',
    ]);
  });

  it('labels the known values readably', () => {
    expect(describeFoodTiming('BEFORE_FOOD')).toBe('Before food');
    expect(describeFoodTiming('WITH_FOOD')).toBe('With food');
  });

  it('shows nothing when the order never stated one', () => {
    expect(describeFoodTiming(null)).toBeNull();
    expect(describeFoodTiming('')).toBeNull();
  });

  /** Historical and unrecognised values must render, not disappear. */
  it('renders an unrecognised value as written rather than hiding it', () => {
    expect(describeFoodTiming('After meals, twice')).toBe('After meals, twice');
    expect(describeFoodTiming('EMPTY_STOMACH')).toBe('EMPTY_STOMACH');
  });
});

describe('food timing applicability', () => {
  it('applies to medicines that are actually swallowed', () => {
    expect(isFoodTimingApplicable('TABLET', 'ORAL')).toBe(true);
    expect(isFoodTimingApplicable('SYRUP', 'ORAL')).toBe(true);
    expect(isFoodTimingApplicable('CAPSULE', 'ORAL')).toBe(true);
  });

  it('does not apply to an injected medicine type', () => {
    expect(isFoodTimingApplicable('INJECTION', 'ORAL')).toBe(false);
    expect(isFoodTimingApplicable('IV_FLUID', 'ORAL')).toBe(false);
  });

  it('does not apply to a parenteral route, whatever the type says', () => {
    // Picking an injection from the catalogue sets the type and leaves the route on ORAL,
    // so the two genuinely disagree in practice and either one has to be enough.
    expect(isFoodTimingApplicable('TABLET', 'IV')).toBe(false);
    expect(isFoodTimingApplicable('TABLET', 'IM')).toBe(false);
    expect(isFoodTimingApplicable('TABLET', 'SUBCUTANEOUS')).toBe(false);
  });

  it('reads values in any case, as the server does', () => {
    expect(isFoodTimingApplicable('injection', 'oral')).toBe(false);
    expect(isFoodTimingApplicable('Tablet', ' iv ')).toBe(false);
    expect(isFoodTimingApplicable('tablet', 'oral')).toBe(true);
  });

  it('treats an unknown or missing order as applicable', () => {
    // Better to ask a question that turns out not to matter than to hide one that does.
    expect(isFoodTimingApplicable(null, null)).toBe(true);
    expect(isFoodTimingApplicable(undefined, undefined)).toBe(true);
    expect(isFoodTimingApplicable('', '')).toBe(true);
    expect(isFoodTimingApplicable('OINTMENT', 'TOPICAL')).toBe(true);
  });
});

describe('food timing translations', () => {
  it('translates BEFORE_FOOD and AFTER_FOOD to Marathi and Hindi', () => {
    expect(getFoodTimingTranslation('BEFORE_FOOD', 'mr')).toBe('जेवणापूर्वी');
    expect(getFoodTimingTranslation('AFTER_FOOD', 'mr')).toBe('जेवणानंतर');

    expect(getFoodTimingTranslation('BEFORE_FOOD', 'hi')).toBe('भोजन से पहले');
    expect(getFoodTimingTranslation('AFTER_FOOD', 'hi')).toBe('भोजन के बाद');

    expect(getFoodTimingTranslation('BEFORE_FOOD', 'en')).toBe('Before Food');
    expect(getFoodTimingTranslation('AFTER_FOOD', 'en')).toBe('After Food');
  });

  it('handles NOT_SPECIFIED and null by returning null', () => {
    expect(getFoodTimingTranslation('NOT_SPECIFIED', 'mr')).toBeNull();
    expect(getFoodTimingTranslation(null, 'mr')).toBeNull();
    expect(getFoodTimingTranslation('', 'hi')).toBeNull();
  });

  it('falls back to English when language is not supported', () => {
    expect(getFoodTimingTranslation('BEFORE_FOOD', 'fr')).toBe('Before Food');
  });

  it('returns unknown values verbatim', () => {
    expect(getFoodTimingTranslation('CUSTOM_TIMING', 'mr')).toBe('CUSTOM_TIMING');
  });
});
