import { useState } from 'react';

/** How a patient leaves the hospital. Mirrors IpdClinicalState.DISCHARGE_TYPES on the server. */
export const DISCHARGE_TYPES = [
  { value: 'NORMAL', label: 'Normal discharge (home)' },
  { value: 'LAMA', label: 'LAMA / DAMA (left against medical advice)' },
  { value: 'REFERRED', label: 'Referred to another hospital' },
  { value: 'EXPIRED', label: 'Expired (death)' },
  { value: 'ABSCONDED', label: 'Absconded' },
];

const SHORT = {
  NORMAL: 'Normal',
  LAMA: 'LAMA',
  REFERRED: 'Referred',
  EXPIRED: 'Expired',
  ABSCONDED: 'Absconded',
};

export const dischargeTypeLabel = (t) => SHORT[t] || t || 'Normal';

/** Note is required where the record is meaningless without it. */
const NOTE_LABELS = {
  EXPIRED: 'Time and cause of death *',
  REFERRED: 'Referred to (hospital) *',
};
export const dischargeNoteLabel = (t) => NOTE_LABELS[t] || 'Note (optional)';

export const dischargeNoteRequired = (t) => t === 'EXPIRED' || t === 'REFERRED';

/** A patient who died or absconded can leave with dues; the bill stays open. */
export const leavesWithDues = (t) => t === 'EXPIRED' || t === 'ABSCONDED';

export function DischargeTypeFields({ type, note, onType, onNote, idPrefix = 'dt' }) {
  return (
    <>
      <div className="mb-3">
        <label htmlFor={`${idPrefix}-type`} className="block text-sm font-medium mb-1">
          Discharge type
        </label>
        <select
          id={`${idPrefix}-type`}
          value={type}
          onChange={(e) => onType(e.target.value)}
          className="w-full border p-2 rounded"
        >
          {DISCHARGE_TYPES.map((d) => (
            <option key={d.value} value={d.value}>
              {d.label}
            </option>
          ))}
        </select>
      </div>
      {type !== 'NORMAL' && (
        <div className="mb-3">
          <label htmlFor={`${idPrefix}-note`} className="block text-sm font-medium mb-1">
            {dischargeNoteLabel(type)}
          </label>
          <input
            id={`${idPrefix}-note`}
            value={note}
            maxLength={500}
            onChange={(e) => onNote(e.target.value)}
            className="w-full border p-2 rounded"
          />
        </div>
      )}
    </>
  );
}

/**
 * Confirms a discharge with its type. Unpaid bills are listed up front: they block a normal,
 * LAMA or referral discharge, while a death or absconding leaves the bill open to collect later.
 */
export function ConfirmDischargeModal({ initialType, initialNote, owing, onClose, onConfirm }) {
  const [type, setType] = useState(initialType || 'NORMAL');
  const [note, setNote] = useState(initialNote || '');
  const [saving, setSaving] = useState(false);
  const missingNote = dischargeNoteRequired(type) && !note.trim();
  const blockedByDues = owing && !leavesWithDues(type);

  const submit = async () => {
    setSaving(true);
    try {
      await onConfirm({ dischargeType: type, dischargeNote: note.trim() || null });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
      <dialog
        open
        aria-modal="true"
        aria-labelledby="confirm-discharge-title"
        className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-lg p-6"
      >
        <h3 id="confirm-discharge-title" className="text-lg font-semibold mb-3">
          Confirm Discharge
        </h3>
        <DischargeTypeFields
          type={type}
          note={note}
          onType={setType}
          onNote={setNote}
          idPrefix="confirm-dt"
        />
        {owing && (
          <p
            className={`text-sm rounded p-2 mb-3 ${blockedByDues ? 'bg-amber-50 text-amber-800' : 'bg-slate-50 text-slate-700'}`}
          >
            Still unpaid: {owing}.{' '}
            {blockedByDues
              ? 'Collect these (Take Payment here, or Collect on the Billing / OPD screen) first — the discharge will be refused until every bill is settled.'
              : 'The bill stays open so the balance can still be collected.'}
          </p>
        )}
        <p className="text-sm text-gray-600 mb-4">
          This finalises the admission, stops running infusions and sends the bed for cleaning.
        </p>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="px-3 py-1 bg-gray-100 rounded">
            Cancel
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving || missingNote}
            className="px-3 py-1 bg-green-600 text-white rounded disabled:opacity-50"
          >
            {saving ? 'Discharging...' : 'Confirm Discharge'}
          </button>
        </div>
      </dialog>
    </div>
  );
}
