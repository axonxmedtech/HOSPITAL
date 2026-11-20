/**
 * Finding the right help answer for a question typed in everyday words.
 *
 * No server and no AI: the answers are a curated set, so a small ranked keyword search is
 * predictable and instant. Words are normalised (case, plurals, -ing/-ed), common words dropped,
 * and synonyms expanded ("admit" also finds "admission" and "IPD"), then each answer is scored by
 * where the words appear — the question counts most, then its keywords, then the answer text.
 * Answers for the screen the user is on get a small boost.
 */

const STOP = new Set(
  'a an the to of for in on at by is are was be can i my me we our you your it this that how do does did what why when where which who with from and or not no get got please there their them as if so any some into'.split(
    ' '
  )
);

/** Everyday words on the left also match the words on the right. */
const SYNONYMS = {
  admit: ['admission', 'ipd', 'inpatient'],
  admission: ['admit', 'ipd'],
  ipd: ['admission', 'admit', 'inpatient', 'ward'],
  inpatient: ['ipd', 'admission'],
  opd: ['visit', 'outpatient', 'consultation', 'queue'],
  visit: ['opd', 'consultation'],
  queue: ['opd', 'waiting'],
  bill: ['billing', 'payment', 'invoice', 'charge'],
  billing: ['bill', 'payment'],
  pay: ['payment', 'bill', 'collect'],
  payment: ['pay', 'collect', 'bill'],
  collect: ['payment', 'pay'],
  money: ['payment', 'bill'],
  refund: ['return', 'money', 'back'],
  discount: ['concession', 'reduce'],
  concession: ['discount'],
  cancel: ['delete', 'remove', 'undo'],
  delete: ['cancel', 'remove'],
  remove: ['delete', 'cancel'],
  undo: ['cancel', 'mistake', 'wrong'],
  mistake: ['wrong', 'undo', 'correct'],
  wrong: ['mistake', 'change', 'correct'],
  medicine: ['medication', 'drug', 'tablet', 'prescription'],
  medication: ['medicine', 'drug'],
  drug: ['medicine', 'medication'],
  tablet: ['medicine'],
  saline: ['iv', 'fluid', 'drip', 'bottle'],
  drip: ['saline', 'iv', 'infusion'],
  iv: ['saline', 'fluid', 'drip'],
  prescription: ['medicine', 'prescribe', 'order'],
  prescribe: ['prescription', 'medicine', 'order'],
  give: ['administer', 'given', 'mar'],
  administer: ['give', 'mar'],
  stock: ['inventory', 'quantity', 'available'],
  inventory: ['stock'],
  discharge: ['leave', 'release', 'home'],
  bed: ['ward', 'room'],
  ward: ['bed', 'room'],
  room: ['bed', 'ward'],
  doctor: ['physician', 'consultant'],
  nurse: ['nursing', 'staff'],
  staff: ['user', 'employee', 'login', 'account'],
  login: ['sign', 'password', 'account'],
  password: ['login', 'reset', 'forgot'],
  forgot: ['password', 'reset'],
  surgery: ['operation', 'ot', 'theatre'],
  operation: ['surgery', 'ot'],
  ot: ['surgery', 'operation', 'theatre'],
  theatre: ['ot', 'surgery'],
  icu: ['critical', 'intensive'],
  vitals: ['bp', 'pulse', 'temperature', 'spo2'],
  bp: ['vitals', 'pressure'],
  report: ['reports', 'summary', 'analytics'],
  print: ['pdf', 'download'],
  appointment: ['booking', 'book', 'slot'],
  book: ['appointment'],
  fee: ['fees', 'charge', 'price'],
  price: ['fee', 'charge'],
  limit: ['plan', 'upgrade', 'maximum'],
  plan: ['subscription', 'limit', 'upgrade'],
  upgrade: ['plan', 'limit'],
  demo: ['trial'],
  shortcut: ['keyboard', 'key'],
  setting: ['settings', 'configure', 'setup'],
  setup: ['setting', 'configure', 'start'],
  shift: ['schedule', 'roster'],
  attendance: ['present', 'absent'],
  task: ['tasks', 'todo'],
};

/** Lowercase, strip punctuation, crude stemming so "admitted", "admits", "admitting" meet. */
export function normalise(word) {
  let w = word.toLowerCase().replace(/[^a-z0-9]/g, '');
  if (w.length > 5 && w.endsWith('ing')) w = w.slice(0, -3);
  else if (w.length > 4 && w.endsWith('ed')) w = w.slice(0, -2);
  else if (w.length > 4 && w.endsWith('es') && !w.endsWith('ses')) w = w.slice(0, -2);
  else if (w.length > 3 && w.endsWith('s') && !w.endsWith('ss')) w = w.slice(0, -1);
  if (w.endsWith('itt')) w = w.slice(0, -1); // admitt(ed) -> admit
  return w;
}

export function tokens(text) {
  return String(text || '')
    .split(/\s+/)
    .map(normalise)
    .filter((w) => w && !STOP.has(w));
}

/** The query's words plus their synonyms, each with a weight (typed words count more). */
function expand(query) {
  const out = new Map();
  for (const t of tokens(query)) {
    out.set(t, Math.max(out.get(t) || 0, 1));
    for (const s of SYNONYMS[t] || []) {
      const n = normalise(s);
      if (!out.has(n)) out.set(n, 0.5);
    }
  }
  return out;
}

const indexCache = new WeakMap();
function indexOf(entry) {
  let idx = indexCache.get(entry);
  if (!idx) {
    idx = {
      q: new Set(tokens(entry.q)),
      tags: new Set(tokens((entry.tags || []).join(' '))),
      body: new Set(tokens(`${entry.a || ''} ${(entry.steps || []).join(' ')}`)),
      qText: String(entry.q || '').toLowerCase(),
    };
    indexCache.set(entry, idx);
  }
  return idx;
}

/**
 * Ranks the entries for a question. Entries must already be filtered to the user's role and
 * setup. Returns [{ entry, score }] best first; empty when nothing matches.
 */
export function rank(entries, query, { screen } = {}) {
  const words = expand(query);
  if (words.size === 0) return [];
  const phrase = String(query || '')
    .trim()
    .toLowerCase();
  const results = [];
  for (const entry of entries) {
    const score = scoreEntry(entry, words, phrase, screen);
    if (score > 0) results.push({ entry, score });
  }
  results.sort((x, y) => y.score - x.score);
  return results;
}

/** Where a word appears: the question counts most, then its keywords, then the answer text. */
function wordHit(idx, w) {
  if (idx.q.has(w)) return 3;
  if (idx.tags.has(w)) return 2;
  return idx.body.has(w) ? 1 : 0;
}

function scoreEntry(entry, words, phrase, screen) {
  const idx = indexOf(entry);
  let score = 0;
  let typedHits = 0;
  for (const [w, weight] of words) {
    const hit = wordHit(idx, w);
    if (hit && weight === 1) typedHits += 1;
    score += hit * weight;
  }
  if (score === 0) return 0;
  if (phrase.length > 6 && idx.qText.includes(phrase)) score += 4;
  // Answers that cover more of what was typed beat answers that repeat one word.
  score += typedHits * 1.5;
  if (screen && entry.screens?.includes(screen)) score += 1;
  return score;
}

/** Below this, the best match is too weak to present as "the answer". */
export const CONFIDENT_SCORE = 4;
