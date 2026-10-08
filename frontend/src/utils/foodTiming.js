/**
 * When a dose is taken relative to food.
 *
 * The vocabulary is the server's (entity/FoodTiming). It lives in its own column rather than
 * inside the free-text instructions field, because that field is general — "take with plenty of
 * water", "crush before giving" — and turning it into a four-value dropdown would have removed
 * the ability to record any of that.
 */
export const FOOD_TIMING_OPTIONS = [
  ['BEFORE_FOOD', 'Before food'],
  ['AFTER_FOOD', 'After food'],
  ['WITH_FOOD', 'With food'],
  ['NOT_SPECIFIED', 'Not specified'],
];

const LABELS = Object.fromEntries(FOOD_TIMING_OPTIONS);

/**
 * Medicine types and routes that bypass the gut, for which "before or after food" states
 * nothing. Both lists are the server's vocabulary (dto/AddIpdPrescriptionRequest), not just
 * the subset the IPD form currently offers, so an order entered through any future control
 * is judged by the same rule.
 */
const PARENTERAL_TYPES = new Set(['INJECTION', 'IV_FLUID']);
const PARENTERAL_ROUTES = new Set(['IV', 'IM', 'SUBCUTANEOUS']);

/**
 * Whether food timing is a meaningful question for this order.
 *
 * An injected dose never meets a meal, so recording "after food" against one is not a
 * harmless extra: it reaches the nurse's medication chart and reads as an instruction.
 * Type and route are checked independently because they can disagree — picking an
 * injection from the catalogue sets the type and leaves the route on its ORAL default.
 *
 * Unknown, absent and blank values are treated as applicable: this hides a question that
 * cannot apply, and must never hide one that might.
 */
export const isFoodTimingApplicable = (type, route) => {
  const norm = (v) => (v == null ? '' : String(v).trim().toUpperCase());
  return !PARENTERAL_TYPES.has(norm(type)) && !PARENTERAL_ROUTES.has(norm(route));
};

/**
 * How to show a stored value.
 *
 * Anything unrecognised is shown as it was written. Orders predate this field, and an older
 * client could still send something else; a clinical record shows what it holds rather than
 * hiding a value it does not recognise.
 */
export const describeFoodTiming = (value) => {
  if (!value) return null;
  return LABELS[value] ?? String(value);
};

export const FOOD_TIMING_TRANSLATIONS = {
  en: {
    BEFORE_FOOD: 'Before Food',
    AFTER_FOOD: 'After Food',
    WITH_FOOD: 'With Food',
  },
  mr: {
    BEFORE_FOOD: 'Before Food / जेवणापूर्वी',
    AFTER_FOOD: 'After Food / जेवणानंतर',
    WITH_FOOD: 'With Food / जेवणासोबत',
  },
  hi: {
    BEFORE_FOOD: 'Before Food / भोजन से पहले',
    AFTER_FOOD: 'After Food / भोजन के बाद',
    WITH_FOOD: 'With Food / भोजन के साथ',
  },
};

export const normalizeLanguageMode = (lang = 'en') => {
  if (!lang) return 'en';
  const l = String(lang).trim().toLowerCase().replace('-', '_');
  if (l === 'mr' || l === 'en_mr') return 'mr';
  if (l === 'hi' || l === 'en_hi') return 'hi';
  return 'en';
};

export const getFoodTimingTranslation = (timing, lang = 'en') => {
  if (!timing || timing === 'NOT_SPECIFIED') return null;
  const normalized = normalizeLanguageMode(lang);
  const langTable = FOOD_TIMING_TRANSLATIONS[normalized] || FOOD_TIMING_TRANSLATIONS.en;
  return langTable[timing] || LABELS[timing] || String(timing);
};

export default describeFoodTiming;
