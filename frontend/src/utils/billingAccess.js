/**
 * Can this user manage bills (see the Billing tab, mark bills paid, take payments)?
 *
 * <p>Mirrors the backend exactly — the BILLING module gate on the billing controller plus
 * BillingController.validateBillingAccess — so the UI never offers a billing screen whose every
 * request the server refuses. The dashboards each used to derive this differently: the doctor's
 * tab ignored the module, the receptionist's ignored the billing handler and solo mode, and the
 * IPD "Take Payment" button ignored the module and solo mode.
 *
 * <ul>
 *   <li>The hospital or clinic must have the BILLING module.</li>
 *   <li>HOSPITAL_ADMIN always may (including a single-doctor clinic admin).</li>
 *   <li>DOCTOR may when billing is handled by the doctor (DOCTOR or BOTH), or in solo mode.</li>
 *   <li>RECEPTIONIST may when billing is handled by reception (RECEPTIONIST or BOTH), and not in
 *       solo mode.</li>
 * </ul>
 */
export const canManageBilling = (user) => {
  const modules = user?.modules || [];
  if (!modules.includes('BILLING')) return false;

  const role = user?.role;
  const handler = user?.billingHandler;
  const solo = user?.receptionMode === 'SOLO';

  if (role === 'HOSPITAL_ADMIN') return true;
  if (role === 'DOCTOR') return handler === 'DOCTOR' || handler === 'BOTH' || solo;
  if (role === 'RECEPTIONIST') return (handler === 'RECEPTIONIST' || handler === 'BOTH') && !solo;
  return false;
};

export default canManageBilling;
