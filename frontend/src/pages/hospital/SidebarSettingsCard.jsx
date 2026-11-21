import { useCallback, useEffect, useMemo, useState } from 'react';
import { useConfirm } from '../../context/ConfirmContext';
import { useToast } from '../../context/ToastContext';
import { SIDEBAR_CHANGED } from '../../hooks/useHiddenTabs';
import apiClient from '../../services/apiService';
import { extractApiError } from '../../utils/apiError';
import { tabsForRole } from '../../utils/sidebarNavigation';

const ROLE_LABELS = {
  HOSPITAL_ADMIN: 'Admin',
  DOCTOR: 'Doctor',
  RECEPTIONIST: 'Receptionist',
  NURSE: 'Staff nurse',
  NURSE_INCHARGE: 'Nurse in-charge',
  PHARMACIST: 'Pharmacist',
  OT_INCHARGE: 'OT in-charge',
};

/** Roles that exist only with a module. */
const ROLE_MODULE = {
  NURSE: 'NURSING',
  NURSE_INCHARGE: 'NURSING',
  PHARMACIST: 'PHARMACY',
  OT_INCHARGE: 'OT',
};

/**
 * Settings → Sidebar: which tabs each role sees. Hiding a tab only tidies the menu — it never
 * changes what a role is allowed to do; every screen keeps its own permission checks.
 */
export default function SidebarSettingsCard({ user, adminTabs }) {
  const confirm = useConfirm();
  const { success, error: toastError } = useToast();
  const roles = useMemo(
    () =>
      Object.keys(ROLE_LABELS).filter(
        (r) => !ROLE_MODULE[r] || (user?.modules || []).includes(ROLE_MODULE[r])
      ),
    [user]
  );
  const [role, setRole] = useState('DOCTOR');
  const [config, setConfig] = useState(null);
  const [hidden, setHidden] = useState(new Set());
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await apiClient.get('/hospital/settings/sidebar');
      setConfig(res.data?.roles || {});
    } catch (e) {
      toastError(extractApiError(e, 'Could not load sidebar settings'));
    }
  }, [toastError]);

  useEffect(() => {
    void load();
  }, [load]);

  const current = config?.[role];
  useEffect(() => {
    setHidden(new Set(current?.hidden || []));
  }, [current]);

  // The admin's own menu is the one on screen; other roles come from their dashboards' lists.
  const tabs = useMemo(
    () =>
      role === 'HOSPITAL_ADMIN' && adminTabs?.length
        ? adminTabs.filter((t) => !t.subItems)
        : tabsForRole(role, user),
    [role, user, adminTabs]
  );
  const always = new Set(current?.alwaysShown || []);
  const dirty =
    current &&
    (hidden.size !== current.hidden.length || current.hidden.some((t) => !hidden.has(t)));

  const toggle = (id) =>
    setHidden((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const changed = () => window.dispatchEvent(new Event(SIDEBAR_CHANGED));

  const save = async () => {
    setSaving(true);
    try {
      await apiClient.put(`/hospital/settings/sidebar/${role}`, { hidden: [...hidden] });
      success(`${ROLE_LABELS[role]} sidebar saved`);
      await load();
      changed();
    } catch (e) {
      toastError(extractApiError(e, 'Could not save'));
    } finally {
      setSaving(false);
    }
  };

  const restore = async () => {
    const ok = await confirm({
      title: `Restore the ${ROLE_LABELS[role]} sidebar?`,
      message: 'The default tabs for this role are shown again.',
      confirmLabel: 'Restore defaults',
    });
    if (!ok) return;
    try {
      await apiClient.delete(`/hospital/settings/sidebar/${role}`);
      success('Defaults restored');
      await load();
      changed();
    } catch (e) {
      toastError(extractApiError(e, 'Could not restore'));
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-5">
      <div>
        <h3 className="text-lg font-bold text-gray-900">Sidebar</h3>
        <p className="text-sm text-gray-500 mt-1">
          Choose which tabs each role sees. This only tidies the menu — it does not change what a
          role is allowed to do.
        </p>
      </div>

      <div className="flex flex-wrap gap-2" role="group" aria-label="Role">
        {roles.map((r) => (
          <button
            key={r}
            type="button"
            aria-pressed={role === r}
            onClick={() => setRole(r)}
            className={`px-3 py-1.5 rounded-lg text-sm font-semibold border ${
              role === r
                ? 'bg-sky-700 text-white border-sky-700'
                : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
            }`}
          >
            {ROLE_LABELS[r]}
          </button>
        ))}
      </div>

      {!config ? (
        <div className="text-sm text-gray-500">Loading…</div>
      ) : (
        <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {tabs.map((t) => {
            const locked = always.has(t.id);
            return (
              <li key={t.id}>
                <label
                  className={`flex items-center gap-3 px-3 py-2 rounded-lg border ${
                    locked ? 'bg-gray-50 border-gray-200' : 'border-gray-200 hover:bg-gray-50'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={!hidden.has(t.id)}
                    disabled={locked}
                    onChange={() => toggle(t.id)}
                    className="h-4 w-4"
                  />
                  <span className="text-sm text-gray-800">{t.label}</span>
                  {locked && <span className="ml-auto text-xs text-gray-400">always shown</span>}
                </label>
              </li>
            );
          })}
        </ul>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={save}
          disabled={!dirty || saving}
          className="px-4 py-2 rounded-lg text-sm font-semibold bg-sky-700 text-white disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
        <button
          type="button"
          onClick={restore}
          disabled={!current?.customised}
          className="px-4 py-2 rounded-lg text-sm font-semibold border border-gray-300 text-gray-700 disabled:opacity-50"
        >
          Restore defaults
        </button>
        {current && !current.customised && current.defaultHidden.length > 0 && (
          <span className="text-xs text-gray-500">
            Hidden by default:{' '}
            {current.defaultHidden
              .map((id) => tabs.find((t) => t.id === id)?.label)
              .filter(Boolean)
              .join(', ')}
          </span>
        )}
      </div>
    </div>
  );
}
