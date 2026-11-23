/**
 * Bills put on hold at the pharmacy counter (Hold Bill / F4), kept in this browser for the
 * signed-in user so a pharmacist can serve the next customer and come back. A convenience only:
 * nothing is reserved or sold until the bill is completed, and stock is checked again then.
 * Storage can be unavailable (private window, blocked site data); every access is guarded.
 */
const MAX_HELD = 10;
const keyFor = (userId) => `hms.pharmacy.heldBills.${userId || 'anon'}`;

export function loadHeldBills(userId) {
  try {
    const raw = window.localStorage.getItem(keyFor(userId));
    const list = raw ? JSON.parse(raw) : [];
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function save(userId, list) {
  try {
    window.localStorage.setItem(keyFor(userId), JSON.stringify(list));
    return true;
  } catch {
    return false;
  }
}

/** Adds a held bill; returns the new list, or null when the limit is reached or storage fails. */
export function holdBill(userId, bill) {
  const list = loadHeldBills(userId);
  if (list.length >= MAX_HELD) return null;
  const next = [
    ...list,
    { ...bill, id: `${Date.now()}-${list.length}`, heldAt: new Date().toISOString() },
  ];
  return save(userId, next) ? next : null;
}

export function removeHeldBill(userId, id) {
  const next = loadHeldBills(userId).filter((b) => b.id !== id);
  save(userId, next);
  return next;
}

export const HELD_BILL_LIMIT = MAX_HELD;
