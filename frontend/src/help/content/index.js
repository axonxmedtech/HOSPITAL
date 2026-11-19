import { admin, superAdmin } from './admin';
import common from './common';
import doctor from './doctor';
import frontDesk from './frontDesk';
import nursing from './nursing';
import { ot, pharmacy } from './otPharmacy';
import { ADMIN, ALL, DESK, DOCTOR, RECEPTION } from './roles';

/** Every help answer. Filter with {@link entriesFor} before showing or searching. */
export const ALL_ENTRIES = [
  ...common,
  ...frontDesk,
  ...doctor,
  ...nursing,
  ...ot,
  ...pharmacy,
  ...admin,
  ...superAdmin,
];

/**
 * The roles a user's help is written for. A doctor who runs the desk (Solo / Both mode) also gets
 * front-desk help; the admin of a single-doctor hospital, who works as the doctor, gets doctor help.
 */
export function helpRolesFor(user) {
  const role = user?.role;
  if (!role) return [];
  const roles = [role];
  const desk = user.receptionMode === 'SOLO' || user.receptionMode === 'BOTH';
  if (role === DOCTOR && desk) roles.push(DESK, RECEPTION);
  if (role === ADMIN && user.isSingleDoctor) roles.push(DOCTOR);
  return roles;
}

/** Answers that apply to this user: their roles, and modules their hospital has. */
export function entriesFor(user) {
  const roles = helpRolesFor(user);
  const modules = new Set(user?.modules || []);
  const seen = new Set();
  return ALL_ENTRIES.filter((e) => {
    if (seen.has(e.id)) return false;
    const roleOk = e.roles.includes(ALL) || e.roles.some((r) => roles.includes(r));
    const modulesOk = !e.modules || e.modules.every((m) => modules.has(m));
    if (roleOk && modulesOk) {
      seen.add(e.id);
      return true;
    }
    return false;
  });
}

/** Answers to suggest first on a screen: ones written for it, else the role's own topics. */
export function suggestionsFor(entries, screen, count = 4) {
  const forScreen = entries.filter((e) => screen && e.screens?.includes(screen));
  const rest = entries.filter((e) => !forScreen.includes(e) && !String(e.id).startsWith('common-'));
  return [...forScreen, ...rest].slice(0, count);
}
