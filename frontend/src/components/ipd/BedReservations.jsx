import { useState } from 'react';
import { useToast } from '../../context/ToastContext';
import wardService from '../../services/wardService';
import { extractApiError } from '../../utils/apiError';

/** "2026-11-22T10:00" for a datetime-local input, in local time. */
const localInput = (d) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

export const holdLabel = (b) =>
  b?.reservedFor
    ? `Reserved for ${b.reservedFor} until ${new Date(b.reservedUntil).toLocaleString(undefined, {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })}`
    : '';

/**
 * Holds a free bed for a planned admission. While the hold is in force only the reserved patient
 * can be admitted into the bed; after "hold until" it lapses by itself.
 */
export function ReserveBedModal({ bed, wardName, onClose, onDone }) {
  const { success, error: toastError } = useToast();
  const inTwoHours = new Date(Date.now() + 2 * 3600 * 1000);
  const [form, setForm] = useState({
    patientName: '',
    patientPhone: '',
    expectedAt: localInput(inTwoHours),
    reservedUntil: '',
    notes: '',
  });
  const [saving, setSaving] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const save = async () => {
    if (!form.patientName.trim()) return toastError('Enter who the bed is reserved for');
    if (!form.expectedAt) return toastError('Enter when the patient is expected');
    setSaving(true);
    try {
      await wardService.reserveBed({
        bedId: bed.bedId,
        patientName: form.patientName.trim(),
        patientPhone: form.patientPhone.trim() || null,
        expectedAt: `${form.expectedAt}:00`,
        reservedUntil: form.reservedUntil ? `${form.reservedUntil}:00` : null,
        notes: form.notes.trim() || null,
      });
      success(`${bed.bedCode} reserved for ${form.patientName.trim()}`);
      onDone?.();
    } catch (err) {
      toastError(extractApiError(err, 'Could not reserve the bed'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50 px-4">
      <dialog
        open
        aria-modal="true"
        aria-label={`Reserve ${bed.bedCode}`}
        className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-md p-6"
      >
        <h3 className="text-lg font-semibold mb-1">Reserve {bed.bedCode}</h3>
        <p className="text-xs text-gray-500 mb-4">
          {wardName}. Only this patient can be admitted into the bed until the hold ends (6 hours
          after the expected time unless you set it).
        </p>
        <label htmlFor="rsv-name" className="block text-sm font-medium mb-1">
          Patient name *
        </label>
        <input
          id="rsv-name"
          value={form.patientName}
          maxLength={150}
          onChange={set('patientName')}
          className="w-full border p-2 rounded mb-3"
        />
        <label htmlFor="rsv-phone" className="block text-sm font-medium mb-1">
          Phone
        </label>
        <input
          id="rsv-phone"
          value={form.patientPhone}
          maxLength={20}
          onChange={set('patientPhone')}
          className="w-full border p-2 rounded mb-3"
        />
        <div className="grid grid-cols-2 gap-3 mb-3">
          <div>
            <label htmlFor="rsv-expected" className="block text-sm font-medium mb-1">
              Expected at *
            </label>
            <input
              id="rsv-expected"
              type="datetime-local"
              value={form.expectedAt}
              onChange={set('expectedAt')}
              className="w-full border p-2 rounded"
            />
          </div>
          <div>
            <label htmlFor="rsv-until" className="block text-sm font-medium mb-1">
              Hold until
            </label>
            <input
              id="rsv-until"
              type="datetime-local"
              value={form.reservedUntil}
              onChange={set('reservedUntil')}
              className="w-full border p-2 rounded"
            />
          </div>
        </div>
        <label htmlFor="rsv-notes" className="block text-sm font-medium mb-1">
          Notes
        </label>
        <input
          id="rsv-notes"
          value={form.notes}
          maxLength={500}
          onChange={set('notes')}
          placeholder="e.g. Planned surgery, Dr. Mehta"
          className="w-full border p-2 rounded"
        />
        <div className="flex justify-end gap-3 mt-4">
          <button type="button" onClick={onClose} className="px-3 py-1.5 bg-gray-100 rounded">
            Cancel
          </button>
          <button
            type="button"
            onClick={save}
            disabled={saving}
            className="px-3 py-1.5 bg-sky-700 text-white rounded disabled:opacity-60"
          >
            {saving ? 'Saving…' : 'Reserve bed'}
          </button>
        </div>
      </dialog>
    </div>
  );
}

/** Beds held right now, with the option to release one. */
export function ReservationsList({ reservations, onChanged }) {
  const { success, error: toastError } = useToast();
  const [busy, setBusy] = useState(null);
  if (!reservations || reservations.length === 0) return null;
  const release = async (r) => {
    setBusy(r.publicId);
    try {
      await wardService.cancelReservation(r.publicId, 'Released at the desk');
      success(`Reservation for ${r.patientName} released`);
      await onChanged?.();
    } catch (err) {
      toastError(extractApiError(err, 'Could not release the reservation'));
    } finally {
      setBusy(null);
    }
  };
  return (
    <section className="bg-white border border-sky-200 rounded-xl">
      <h3 className="px-4 py-3 border-b border-sky-100 font-semibold text-sky-900 text-sm">
        Reserved beds ({reservations.length})
      </h3>
      <ul className="divide-y divide-gray-100">
        {reservations.map((r) => (
          <li key={r.publicId} className="px-4 py-3 flex flex-wrap items-center gap-3 text-sm">
            <span className="font-semibold min-w-[8rem]">
              {r.ward} / {r.bed}
            </span>
            <span>
              {r.patientName}
              {r.patientPhone ? ` · ${r.patientPhone}` : ''}
            </span>
            <span className="text-xs text-gray-500">
              expected {new Date(r.expectedAt).toLocaleString()} · held until{' '}
              {new Date(r.reservedUntil).toLocaleString()}
            </span>
            {r.notes && <span className="text-xs text-gray-500">{r.notes}</span>}
            <button
              type="button"
              disabled={busy === r.publicId}
              onClick={() => release(r)}
              className="ml-auto px-3 py-1 text-sm rounded-lg border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
            >
              Release
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
