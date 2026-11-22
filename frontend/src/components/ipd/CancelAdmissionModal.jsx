import { useState } from 'react';

const REFUND_MODES = [
  { value: 'CASH', label: 'Cash' },
  { value: 'UPI', label: 'UPI' },
  { value: 'CARD', label: 'Card' },
];

/**
 * Cancels an admission made by mistake. The server frees the bed, voids the IPD bill and sends
 * the doctor's request back to "Requested"; this asks for the reason, and for how collected
 * money goes back when there is any (only the admin may cancel then).
 */
export default function CancelAdmissionModal({ isOpen, paidAmount, isAdmin, onClose, onConfirm }) {
  const [reason, setReason] = useState('');
  const [refundMode, setRefundMode] = useState('CASH');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  if (!isOpen) return null;

  const paid = Number(paidAmount) || 0;
  const blocked = paid > 0 && !isAdmin;

  const submit = async () => {
    if (!reason.trim()) {
      setError('Enter why the admission is being cancelled');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onConfirm(reason.trim(), paid > 0 ? refundMode : null);
      setReason('');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cancel-admission-title"
    >
      <div className="bg-white rounded-lg w-full max-w-md p-6 shadow-xl">
        <h3 id="cancel-admission-title" className="text-lg font-semibold mb-2">
          Cancel admission
        </h3>
        <p className="text-sm text-gray-600 mb-3">
          Use this only for an admission made by mistake: wrong patient, duplicate, or the patient
          left before reaching the bed. The bed is freed, the IPD bill is cancelled and the
          doctor&apos;s request goes back to the Requested list. For a real stay, discharge the
          patient instead.
        </p>
        {blocked ? (
          <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded p-2 mb-3">
            ₹{paid} has been collected on this admission. Only the hospital admin can cancel it,
            because the money has to be refunded.
          </p>
        ) : (
          <>
            <label htmlFor="cancel-adm-reason" className="block text-sm font-medium mb-1">
              Reason *
            </label>
            <textarea
              id="cancel-adm-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={200}
              rows={3}
              className="w-full border p-2 rounded mb-3"
              placeholder="e.g. Admitted under the wrong patient"
            />
            {paid > 0 && (
              <div className="mb-3">
                <label htmlFor="cancel-adm-refund" className="block text-sm font-medium mb-1">
                  Refund ₹{paid} by
                </label>
                <select
                  id="cancel-adm-refund"
                  value={refundMode}
                  onChange={(e) => setRefundMode(e.target.value)}
                  className="w-full border p-2 rounded"
                >
                  {REFUND_MODES.map((m) => (
                    <option key={m.value} value={m.value}>
                      {m.label}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </>
        )}
        {error && <p className="text-sm text-red-700 mb-2">{error}</p>}
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="px-3 py-1.5 bg-gray-100 rounded">
            Keep admission
          </button>
          {!blocked && (
            <button
              type="button"
              onClick={submit}
              disabled={saving}
              className="px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded disabled:opacity-60"
            >
              {saving ? 'Cancelling…' : 'Cancel admission'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
