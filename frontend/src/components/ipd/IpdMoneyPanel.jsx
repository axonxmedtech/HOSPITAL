import { useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';
import { printBlob } from '../../utils/printPdf';

const MODES = [
  { value: 'CASH', label: 'Cash' },
  { value: 'UPI', label: 'UPI' },
  { value: 'CARD', label: 'Card' },
];

const rupees = (n) => `₹${Number(n || 0).toLocaleString('en-IN')}`;

function Modal({ title, children, onClose, onSubmit, saving, submitLabel }) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50 px-4">
      <dialog
        open
        aria-modal="true"
        aria-label={title}
        className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-md p-6 shadow-xl"
      >
        <h3 className="text-lg font-semibold mb-3">{title}</h3>
        {children}
        <div className="flex justify-end gap-3 mt-4">
          <button type="button" onClick={onClose} className="px-3 py-1.5 bg-gray-100 rounded">
            Cancel
          </button>
          <button
            type="button"
            onClick={onSubmit}
            disabled={saving}
            className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded disabled:opacity-60"
          >
            {saving ? 'Saving…' : submitLabel}
          </button>
        </div>
      </dialog>
    </div>
  );
}

/**
 * Advances and the package of one admission, inside the IPD billing card. The server decides
 * who may do what (front desk takes advances and applies packages; only the admin removes one);
 * the buttons follow the same rules so nobody is offered an action that will be refused.
 */
export default function IpdMoneyPanel({ ipdId, data, isAdmin, canAct, onChanged }) {
  const { success, error: toastError } = useToast();
  const billing = data?.billing || {};
  const admission = data?.admission || {};
  const advances = billing.advances || [];
  const grace = admission.graceHours ? ` (+${admission.graceHours} h grace)` : '';
  const ruleText =
    admission.bedChargeRule === 'HOURS_24' ? `24 hours from admission${grace}` : 'calendar date';
  const unused = Math.max(
    0,
    -(Number(billing.balance || 0) - Number(billing.insuranceExpected || 0))
  );

  const [advance, setAdvance] = useState(null); // { amount, mode, reference }
  const [pkg, setPkg] = useState(null); // { packages, packageId }
  const [removing, setRemoving] = useState(null); // { reason }
  const [saving, setSaving] = useState(false);
  const [printing, setPrinting] = useState(null);

  useEffect(() => {
    if (!pkg || pkg.packages) return;
    hospitalService
      .getIpdPackages()
      .then((list) => setPkg((p) => (p ? { ...p, packages: list || [] } : p)))
      .catch((err) => {
        toastError(extractApiError(err, 'Could not load packages'));
        setPkg(null);
      });
  }, [pkg, toastError]);

  const run = async (fn, done, okText) => {
    setSaving(true);
    try {
      const result = await fn();
      success(okText);
      done();
      await onChanged?.();
      return result;
    } catch (err) {
      toastError(extractApiError(err, 'Could not save'));
      return null;
    } finally {
      setSaving(false);
    }
  };

  const printReceipt = async (paymentId) => {
    setPrinting(paymentId);
    try {
      await printBlob(await hospitalService.downloadAdvanceReceipt(paymentId));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load the receipt'));
    } finally {
      setPrinting(null);
    }
  };

  const submitAdvance = async () => {
    const amount = Number(advance.amount);
    if (!amount || amount <= 0) return toastError('Enter the advance amount');
    if (advance.mode === 'UPI' && !advance.reference.trim())
      return toastError('Enter the UPI reference (UTR)');
    const result = await run(
      () =>
        hospitalService.takeIpdAdvance(ipdId, {
          amount,
          mode: advance.mode,
          reference: advance.reference.trim() || null,
        }),
      () => setAdvance(null),
      `Advance of ${rupees(amount)} recorded`
    );
    if (result?.paymentId) await printReceipt(result.paymentId);
  };

  return (
    <div className="mt-3 border-t pt-3 text-sm space-y-2">
      <div>
        <strong>Package:</strong>{' '}
        {admission.packageName ? (
          <>
            {admission.packageName} ({admission.packageDays} day
            {admission.packageDays === 1 ? '' : 's'}
            {billing.packagePrice ? `, ${rupees(billing.packagePrice)}` : ''})
            {isAdmin && canAct && (
              <button
                type="button"
                onClick={() => setRemoving({ reason: '' })}
                className="ml-2 text-xs font-semibold text-red-700 hover:underline"
              >
                Remove
              </button>
            )}
          </>
        ) : (
          <>
            None
            {canAct && (
              <button
                type="button"
                onClick={() => setPkg({ packageId: '' })}
                className="ml-2 text-xs font-semibold text-sky-700 hover:underline"
              >
                Apply package
              </button>
            )}
          </>
        )}
      </div>
      <div className="text-xs text-gray-500">Bed days counted by {ruleText}.</div>

      {Number(billing.insuranceExpected) > 0 && (
        <div className="text-indigo-800">
          <strong>Insurer&apos;s share (approved):</strong> {rupees(billing.insuranceExpected)} ·{' '}
          <strong>Patient&apos;s share:</strong>{' '}
          {rupees(Math.max(0, Number(billing.balance || 0) - Number(billing.insuranceExpected)))}
        </div>
      )}
      <div>
        <strong>Advance:</strong> {rupees(billing.advancePaid)}
        {unused > 0 && (
          <span className="ml-1 text-emerald-700">
            ({rupees(unused)} unused — returned at discharge)
          </span>
        )}
      </div>
      {advances.length > 0 && (
        <ul className="text-xs text-gray-600 space-y-0.5">
          {advances.map((a) => (
            <li key={a.id} className="flex items-center justify-between gap-2">
              <span>
                ADV-{a.id} · {rupees(a.amount)} · {a.mode}
                {a.createdAt ? ` · ${new Date(a.createdAt).toLocaleDateString()}` : ''}
              </span>
              <button
                type="button"
                onClick={() => printReceipt(a.id)}
                disabled={printing === a.id}
                className="text-sky-700 hover:underline disabled:opacity-50"
              >
                {printing === a.id ? 'Preparing…' : 'Receipt'}
              </button>
            </li>
          ))}
        </ul>
      )}
      {canAct && (
        <button
          type="button"
          onClick={() => setAdvance({ amount: '', mode: 'CASH', reference: '' })}
          className="px-3 py-1 border border-emerald-600 text-emerald-700 rounded hover:bg-emerald-50"
        >
          Take Advance
        </button>
      )}

      {advance && (
        <Modal
          title="Take advance"
          onClose={() => setAdvance(null)}
          onSubmit={submitAdvance}
          saving={saving}
          submitLabel="Record & print receipt"
        >
          <p className="text-xs text-gray-500 mb-3">
            An advance can be more than the charges so far. Whatever is unused is returned at
            discharge.
          </p>
          <label htmlFor="adv-amount" className="block text-sm font-medium mb-1">
            Amount (₹)
          </label>
          <input
            id="adv-amount"
            type="number"
            min="1"
            value={advance.amount}
            onChange={(e) => setAdvance({ ...advance, amount: e.target.value })}
            className="w-full border p-2 rounded mb-3"
          />
          <label htmlFor="adv-mode" className="block text-sm font-medium mb-1">
            Paid by
          </label>
          <select
            id="adv-mode"
            value={advance.mode}
            onChange={(e) => setAdvance({ ...advance, mode: e.target.value })}
            className="w-full border p-2 rounded mb-3"
          >
            {MODES.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
          {advance.mode !== 'CASH' && (
            <>
              <label htmlFor="adv-ref" className="block text-sm font-medium mb-1">
                {advance.mode === 'UPI' ? 'UTR / reference *' : 'Reference'}
              </label>
              <input
                id="adv-ref"
                value={advance.reference}
                maxLength={100}
                onChange={(e) => setAdvance({ ...advance, reference: e.target.value })}
                className="w-full border p-2 rounded"
              />
            </>
          )}
        </Modal>
      )}

      {pkg && (
        <Modal
          title="Apply package"
          onClose={() => setPkg(null)}
          onSubmit={() => {
            if (!pkg.packageId) return toastError('Select a package');
            return run(
              () => hospitalService.applyIpdPackage(ipdId, pkg.packageId),
              () => setPkg(null),
              'Package applied'
            );
          }}
          saving={saving}
          submitLabel="Apply"
        >
          <p className="text-xs text-gray-500 mb-3">
            The package covers bed, nursing and doctor visits for its days. Those charges already on
            the bill are credited back. Medicines and extra days are charged as usual.
          </p>
          {!pkg.packages && <p className="text-sm text-gray-500">Loading packages…</p>}
          {pkg.packages?.length === 0 && (
            <p className="text-sm text-gray-500">
              No packages yet. The admin can add them in Settings.
            </p>
          )}
          {pkg.packages?.length > 0 && (
            <>
              <label htmlFor="apply-pkg" className="block text-sm font-medium mb-1">
                Package
              </label>
              <select
                id="apply-pkg"
                value={pkg.packageId}
                onChange={(e) => setPkg({ ...pkg, packageId: e.target.value })}
                className="w-full border p-2 rounded"
              >
                <option value="">Select…</option>
                {pkg.packages.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} — {rupees(p.price)}, {p.includedDays} day
                    {p.includedDays === 1 ? '' : 's'}
                  </option>
                ))}
              </select>
            </>
          )}
        </Modal>
      )}

      {removing && (
        <Modal
          title="Remove package"
          onClose={() => setRemoving(null)}
          onSubmit={() => {
            if (!removing.reason.trim()) return toastError('Enter why the package is removed');
            return run(
              () => hospitalService.removeIpdPackage(ipdId, removing.reason.trim()),
              () => setRemoving(null),
              'Package removed'
            );
          }}
          saving={saving}
          submitLabel="Remove package"
        >
          <p className="text-xs text-gray-500 mb-3">
            The package price is reversed and the bed and nursing days are charged again. Doctor
            visits it covered are not charged again.
          </p>
          <label htmlFor="remove-pkg-reason" className="block text-sm font-medium mb-1">
            Reason *
          </label>
          <input
            id="remove-pkg-reason"
            value={removing.reason}
            maxLength={200}
            onChange={(e) => setRemoving({ reason: e.target.value })}
            className="w-full border p-2 rounded"
          />
        </Modal>
      )}
    </div>
  );
}
