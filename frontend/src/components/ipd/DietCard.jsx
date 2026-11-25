import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';
import { printBlob } from '../../utils/printPdf';

import { formatDay, formatWhen } from '../../utils/date';
/** Mirrors DietOrderService.DIET_TYPES on the server. */
export const DIET_TYPES = [
  { value: 'NORMAL', label: 'Normal' },
  { value: 'SOFT', label: 'Soft' },
  { value: 'LIQUID', label: 'Liquid' },
  { value: 'CLEAR_LIQUID', label: 'Clear liquid' },
  { value: 'DIABETIC', label: 'Diabetic' },
  { value: 'LOW_SALT', label: 'Low salt' },
  { value: 'RENAL', label: 'Renal' },
  { value: 'HIGH_PROTEIN', label: 'High protein' },
  { value: 'NBM', label: 'Nil by mouth (NBM)' },
  { value: 'OTHER', label: 'Other (describe)' },
];

export const dietLabel = (v) => DIET_TYPES.find((d) => d.value === v)?.label || v || 'Not set';

/**
 * The patient's current diet. The doctor (or admin) changes it; everyone else reads it. A change
 * ends the previous diet, which stays in the history.
 */
export default function DietCard({ admissionId, canEdit = false, refreshKey = 0 }) {
  const { success, error: toastError } = useToast();
  const [orders, setOrders] = useState(null);
  const [form, setForm] = useState(null);
  const [showHistory, setShowHistory] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setOrders(await hospitalService.getIpdDiet(admissionId));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load the diet'));
      setOrders([]);
    }
  }, [admissionId, toastError]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  const current = (orders || []).find((o) => !o.endedAt);

  let currentView;
  if (orders === null) {
    currentView = <p className="text-sm text-gray-500">Loading…</p>;
  } else if (current) {
    currentView = (
      <div className="text-sm">
        <span
          className={`font-semibold ${current.dietType === 'NBM' ? 'text-red-700' : 'text-gray-900'}`}
        >
          {dietLabel(current.dietType)}
        </span>
        {current.instructions && <span className="text-gray-700"> — {current.instructions}</span>}
        <div className="text-xs text-gray-500">since {formatWhen(current.startedAt)}</div>
      </div>
    );
  } else {
    currentView = <p className="text-sm text-gray-500">No diet set.</p>;
  }

  const save = async () => {
    if (!form.dietType) return toastError('Select a diet');
    if (form.dietType === 'OTHER' && !form.instructions.trim())
      return toastError('Describe the diet in the instructions');
    setSaving(true);
    try {
      await hospitalService.setIpdDiet(admissionId, {
        dietType: form.dietType,
        instructions: form.instructions.trim() || null,
      });
      success('Diet updated');
      setForm(null);
      await load();
    } catch (err) {
      toastError(extractApiError(err, 'Could not save the diet'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mb-4">
      <div className="flex items-center justify-between gap-2 mb-1">
        <h3 className="font-semibold">Diet</h3>
        {canEdit && !form && (
          <button
            type="button"
            onClick={() =>
              setForm({
                dietType: current?.dietType || '',
                instructions: current?.instructions || '',
              })
            }
            className="text-xs font-semibold text-sky-700 hover:underline"
          >
            {current ? 'Change' : 'Set diet'}
          </button>
        )}
      </div>
      {currentView}

      {form && (
        <div className="mt-2 border rounded p-2 bg-gray-50 space-y-2">
          <select
            aria-label="Diet"
            value={form.dietType}
            onChange={(e) => setForm({ ...form, dietType: e.target.value })}
            className="w-full border p-2 rounded bg-white text-sm"
          >
            <option value="">Select…</option>
            {DIET_TYPES.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label}
              </option>
            ))}
          </select>
          <input
            aria-label="Diet instructions"
            value={form.instructions}
            maxLength={500}
            onChange={(e) => setForm({ ...form, instructions: e.target.value })}
            placeholder="Instructions (e.g. 1800 kcal, no sugar)"
            className="w-full border p-2 rounded bg-white text-sm"
          />
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setForm(null)}
              className="px-2 py-1 text-sm bg-gray-100 rounded"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={save}
              disabled={saving}
              className="px-2 py-1 text-sm bg-sky-700 text-white rounded disabled:opacity-60"
            >
              Save diet
            </button>
          </div>
        </div>
      )}

      {(orders || []).length > 1 && (
        <button
          type="button"
          onClick={() => setShowHistory((v) => !v)}
          className="mt-1 text-xs text-gray-500 hover:underline"
        >
          {showHistory ? 'Hide earlier diets' : `Earlier diets (${orders.length - 1})`}
        </button>
      )}
      {showHistory && (
        <ul className="mt-1 text-xs text-gray-600 space-y-0.5">
          {orders
            .filter((o) => o.endedAt)
            .map((o) => (
              <li key={o.publicId}>
                {dietLabel(o.dietType)}
                {o.instructions ? ` — ${o.instructions}` : ''} · {formatDay(o.startedAt)} to{' '}
                {formatDay(o.endedAt)}
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}

/** Prints the kitchen's diet sheet (every patient in a bed, with their current diet). */
export function DietSheetButton({ wardId = null, className = '' }) {
  const { error: toastError } = useToast();
  const [busy, setBusy] = useState(false);
  return (
    <button
      type="button"
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        try {
          await printBlob(await hospitalService.downloadDietSheet(wardId));
        } catch (err) {
          toastError(extractApiError(err, 'Could not load the diet sheet'));
        } finally {
          setBusy(false);
        }
      }}
      className={
        className ||
        'px-3 py-1.5 rounded-lg text-sm font-semibold text-gray-800 bg-white border border-gray-300 hover:bg-gray-50 disabled:opacity-60'
      }
    >
      {busy ? 'Preparing…' : 'Print diet sheet'}
    </button>
  );
}
