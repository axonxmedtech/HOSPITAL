import { useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import nurseService from '../../services/nurseService';
import { extractApiError } from '../../utils/apiError';

const ROLE_LABELS = {
  NURSE: 'Nurse',
  NURSE_INCHARGE: 'Nurse In-charge',
  DOCTOR: 'Doctor',
  HOSPITAL_ADMIN: 'Admin',
  RECEPTIONIST: 'Reception',
};

/**
 * Admin setting: which roles may record a medicine as given (and undo a wrong entry). Giving from
 * stock takes it off the inventory and adds it to the IPD bill, so this is who may do that too.
 */
export default function MedicationAccessCard() {
  const { success, error: toastError } = useToast();
  const [allRoles, setAllRoles] = useState(Object.keys(ROLE_LABELS));
  const [roles, setRoles] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    nurseService
      .getMedicationAccess()
      .then((a) => {
        setRoles(a?.roles || []);
        if (Array.isArray(a?.allRoles)) setAllRoles(a.allRoles);
      })
      .catch((err) => toastError(extractApiError(err, 'Could not load the setting')));
  }, [toastError]);

  const toggle = (r) =>
    setRoles((cur) => (cur.includes(r) ? cur.filter((x) => x !== r) : [...cur, r]));

  const save = async () => {
    setSaving(true);
    try {
      const a = await nurseService.setMedicationAccess(roles);
      setRoles(a?.roles || roles);
      success('Saved: who can record medicines');
    } catch (err) {
      toastError(extractApiError(err, 'Could not save'));
    } finally {
      setSaving(false);
    }
  };

  if (!roles) return <p className="text-sm text-gray-500">Loading…</p>;

  return (
    <section className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
      <div>
        <h3 className="text-lg font-semibold text-gray-900">Who can record medicines</h3>
        <p className="text-sm text-gray-600 mt-1">
          These roles can mark a medicine or saline as given on an admitted patient&apos;s chart,
          give an extra item, and undo a wrong entry. Given from stock, it is taken off the
          inventory and added to the IPD bill.
        </p>
      </div>
      <fieldset className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <legend className="sr-only">Roles allowed to record medicines</legend>
        {allRoles.map((r) => (
          <label key={r} className="flex items-center gap-2 text-sm text-gray-800">
            <input type="checkbox" checked={roles.includes(r)} onChange={() => toggle(r)} />
            {ROLE_LABELS[r] || r}
          </label>
        ))}
      </fieldset>
      <div className="flex justify-end">
        <button
          type="button"
          onClick={save}
          disabled={saving || roles.length === 0}
          className="px-4 py-2 rounded-lg text-sm font-semibold bg-gray-900 text-white disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
      {roles.length === 0 && <p className="text-xs text-red-700">Allow at least one role.</p>}
    </section>
  );
}
