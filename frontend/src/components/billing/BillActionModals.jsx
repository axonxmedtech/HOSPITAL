import React, { useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';

/**
 * Billing actions shared by every billing screen (reception, admin, doctor, OPD row, IPD):
 * collect a full or part payment (Cash / Card / UPI), give a discount, refund, cancel.
 *
 * Every figure comes from the server (net = gross - discount; balance = net - paid), so these
 * dialogs never do their own money arithmetic beyond pre-filling the balance.
 */

const MODES = [
  { id: 'CASH', label: 'Cash' },
  { id: 'CARD', label: 'Card' },
  { id: 'UPI', label: 'UPI / Online' },
];

const rupees = (v) => `₹${Number(v || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
const num = (v) => Number(v || 0);

const Shell = ({ title, subtitle, onClose, children, footer, labelledBy }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
    <button
      type="button"
      aria-label="Close"
      className="absolute inset-0 bg-gray-500/75"
      onClick={onClose}
    />
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={labelledBy}
      className="relative bg-white rounded-xl shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto"
    >
      <div className="px-6 pt-5 pb-4">
        <h3 id={labelledBy} className="text-lg font-semibold text-gray-900">
          {title}
        </h3>
        {subtitle && <p className="text-sm text-gray-500 mt-0.5">{subtitle}</p>}
        <div className="mt-4 space-y-4">{children}</div>
      </div>
      <div className="bg-gray-50 px-6 py-3 flex justify-end gap-2 rounded-b-xl">{footer}</div>
    </div>
  </div>
);

/** Gross / discount / paid / balance summary for a bill. */
export const BillSummary = ({ bill }) => {
  const gross = num(bill.grossAmount ?? bill.amount);
  const discount = num(bill.discountAmount);
  const paid = num(bill.paidAmount);
  const balance = num(bill.balance ?? gross - discount - paid);
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm bg-gray-50 rounded-lg p-3">
      <dt className="text-gray-500">Charges</dt>
      <dd className="text-right text-gray-900">{rupees(gross)}</dd>
      {discount > 0 && (
        <>
          <dt className="text-gray-500">Discount</dt>
          <dd className="text-right text-emerald-700">− {rupees(discount)}</dd>
        </>
      )}
      <dt className="text-gray-500">Paid so far</dt>
      <dd className="text-right text-gray-900">{rupees(paid)}</dd>
      <dt className="font-semibold text-gray-900">Balance</dt>
      <dd className="text-right font-bold text-red-700">{rupees(balance)}</dd>
    </dl>
  );
};

const btn = 'px-4 py-2 rounded-lg text-sm font-semibold disabled:opacity-50';
const input =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-transparent';

const ModePicker = ({ mode, setMode, name }) => (
  <div role="radiogroup" aria-label={name} className="grid grid-cols-3 gap-2">
    {MODES.map((m) => (
      <button
        key={m.id}
        type="button"
        role="radio"
        aria-checked={mode === m.id}
        onClick={() => setMode(m.id)}
        className={`px-3 py-2 rounded-lg border text-sm font-medium ${
          mode === m.id
            ? 'bg-gray-900 text-white border-gray-900'
            : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
        }`}
      >
        {m.label}
      </button>
    ))}
  </div>
);

/**
 * Collect a payment. Amount defaults to the full balance and can be lowered for a part-payment.
 * onDone(result) gets the server's updated totals (balance, status, …).
 */
export const CollectPaymentModal = ({ bill, onClose, onDone }) => {
  const { error: toastError } = useToast();
  const balance = num(bill.balance ?? num(bill.amount) - num(bill.paidAmount));
  const [amount, setAmount] = useState(String(balance));
  const [mode, setMode] = useState('CASH');
  const [reference, setReference] = useState('');
  const [saving, setSaving] = useState(false);

  const amt = Number(amount);
  const invalid = !(amt > 0) || amt > balance || (mode === 'UPI' && !reference.trim());

  const submit = async () => {
    if (!(amt > 0)) return toastError('Enter an amount greater than zero');
    if (amt > balance) return toastError(`Only ${rupees(balance)} is due on this bill`);
    if (mode === 'UPI' && !reference.trim()) return toastError('Enter the UPI UTR / reference');
    setSaving(true);
    try {
      const result = await hospitalService.payBilling(bill.id ?? bill.billingId, {
        amount: amt,
        mode,
        reference: reference.trim() || null,
      });
      onDone?.({ ...result, collected: amt, mode });
    } catch (e) {
      toastError(extractApiError(e, 'Could not record the payment'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Shell
      title="Collect Payment"
      labelledBy="collect-payment-title"
      subtitle={`${bill.patientName ? `${bill.patientName} · ` : ''}${bill.customId || ''}`}
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className={`${btn} bg-white border border-gray-300 text-gray-700`}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving || invalid}
            className={`${btn} bg-emerald-600 text-white hover:bg-emerald-700`}
          >
            {saving
              ? 'Saving…'
              : amt > 0 && amt < balance
                ? `Collect ${rupees(amt)} (part)`
                : `Collect ${rupees(amt || 0)}`}
          </button>
        </>
      }
    >
      <BillSummary bill={bill} />
      <div>
        <label htmlFor="pay-amount" className="block text-xs font-semibold text-gray-700 mb-1">
          Amount received (₹)
        </label>
        <input
          id="pay-amount"
          type="number"
          min="0"
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className={input}
        />
        <p className="text-xs text-gray-500 mt-1">
          Lower this to take a part-payment; the rest stays due on the bill.
        </p>
      </div>
      <div>
        <span className="block text-xs font-semibold text-gray-700 mb-1">Payment method</span>
        <ModePicker mode={mode} setMode={setMode} name="Payment method" />
      </div>
      {mode !== 'CASH' && (
        <div>
          <label htmlFor="pay-ref" className="block text-xs font-semibold text-gray-700 mb-1">
            {mode === 'UPI'
              ? 'UTR / transaction reference *'
              : 'Card reference / last 4 digits (optional)'}
          </label>
          <input
            id="pay-ref"
            type="text"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            className={input}
          />
        </div>
      )}
    </Shell>
  );
};

/** Discount / concession. Zero removes an existing discount. */
export const DiscountModal = ({ bill, onClose, onDone }) => {
  const { error: toastError, success } = useToast();
  const gross = num(bill.grossAmount ?? bill.amount);
  const paid = num(bill.paidAmount);
  const max = Math.max(0, gross - paid);
  const [amount, setAmount] = useState(bill.discountAmount ? String(bill.discountAmount) : '');
  const [percent, setPercent] = useState('');
  const [reason, setReason] = useState(bill.discountReason || '');
  const [saving, setSaving] = useState(false);

  const setByPercent = (p) => {
    setPercent(p);
    const v = Number(p);
    if (v >= 0 && v <= 100) setAmount(String(Math.round(((gross * v) / 100) * 100) / 100));
  };

  const amt = Number(amount || 0);
  const submit = async () => {
    if (amt < 0) return toastError('Discount cannot be negative');
    if (amt > max) return toastError(`The discount can be at most ${rupees(max)}`);
    if (amt > 0 && !reason.trim()) return toastError('Enter a reason for the discount');
    setSaving(true);
    try {
      const result = await hospitalService.discountBill(
        bill.id ?? bill.billingId,
        amt,
        reason.trim()
      );
      success(amt > 0 ? `Discount of ${rupees(amt)} applied` : 'Discount removed');
      onDone?.(result);
    } catch (e) {
      toastError(extractApiError(e, 'Could not apply the discount'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Shell
      title="Discount / Concession"
      labelledBy="discount-title"
      subtitle={`${bill.patientName ? `${bill.patientName} · ` : ''}${bill.customId || ''}`}
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className={`${btn} bg-white border border-gray-300 text-gray-700`}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving}
            className={`${btn} bg-gray-900 text-white hover:bg-gray-800`}
          >
            {saving ? 'Saving…' : 'Apply Discount'}
          </button>
        </>
      }
    >
      <BillSummary bill={bill} />
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="disc-amount" className="block text-xs font-semibold text-gray-700 mb-1">
            Discount (₹)
          </label>
          <input
            id="disc-amount"
            type="number"
            min="0"
            step="0.01"
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value);
              setPercent('');
            }}
            className={input}
          />
        </div>
        <div>
          <label htmlFor="disc-pct" className="block text-xs font-semibold text-gray-700 mb-1">
            or % of charges
          </label>
          <input
            id="disc-pct"
            type="number"
            min="0"
            max="100"
            value={percent}
            onChange={(e) => setByPercent(e.target.value)}
            className={input}
          />
        </div>
      </div>
      <p className="text-xs text-gray-500">Up to {rupees(max)}. Enter 0 to remove a discount.</p>
      <div>
        <label htmlFor="disc-reason" className="block text-xs font-semibold text-gray-700 mb-1">
          Reason *
        </label>
        <input
          id="disc-reason"
          type="text"
          value={reason}
          maxLength={255}
          placeholder="e.g. Senior citizen, staff family, doctor's concession"
          onChange={(e) => setReason(e.target.value)}
          className={input}
        />
      </div>
    </Shell>
  );
};

/** Admin: pay money back on a bill. */
export const RefundModal = ({ bill, onClose, onDone }) => {
  const { error: toastError, success } = useToast();
  const paid = num(bill.paidAmount);
  const [amount, setAmount] = useState('');
  const [mode, setMode] = useState('CASH');
  const [reason, setReason] = useState('');
  // Overcharged (default): the bill shrinks by the refund and stays settled. Unticked: the
  // money goes back and is owed again (e.g. to re-collect it by another method).
  const [reduceBill, setReduceBill] = useState(true);
  const [saving, setSaving] = useState(false);

  const amt = Number(amount || 0);
  const submit = async () => {
    if (!(amt > 0)) return toastError('Enter a refund amount');
    if (amt > paid) return toastError(`Only ${rupees(paid)} has been collected`);
    if (!reason.trim()) return toastError('Enter a reason for the refund');
    setSaving(true);
    try {
      const result = await hospitalService.refundBill(
        bill.id ?? bill.billingId,
        amt,
        mode,
        reason.trim(),
        reduceBill
      );
      success(`Refund of ${rupees(amt)} recorded`);
      onDone?.(result);
    } catch (e) {
      toastError(extractApiError(e, 'Could not record the refund'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Shell
      title="Refund"
      labelledBy="refund-title"
      subtitle={`${bill.patientName ? `${bill.patientName} · ` : ''}${bill.customId || ''}`}
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className={`${btn} bg-white border border-gray-300 text-gray-700`}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving}
            className={`${btn} bg-red-600 text-white hover:bg-red-700`}
          >
            {saving ? 'Saving…' : 'Record Refund'}
          </button>
        </>
      }
    >
      <BillSummary bill={bill} />
      <div>
        <label htmlFor="refund-amount" className="block text-xs font-semibold text-gray-700 mb-1">
          Amount to refund (₹) — up to {rupees(paid)}
        </label>
        <input
          id="refund-amount"
          type="number"
          min="0"
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className={input}
        />
      </div>
      <div>
        <span className="block text-xs font-semibold text-gray-700 mb-1">Refunded by</span>
        <ModePicker mode={mode} setMode={setMode} name="Refund method" />
      </div>
      <label className="flex items-start gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={reduceBill}
          onChange={(e) => setReduceBill(e.target.checked)}
          className="mt-0.5"
        />
        <span>
          Patient was overcharged — reduce the bill by this amount
          <span className="block text-xs text-gray-500">
            Untick if the money will be collected again (e.g. switching payment method).
          </span>
        </span>
      </label>
      <div>
        <label htmlFor="refund-reason" className="block text-xs font-semibold text-gray-700 mb-1">
          Reason *
        </label>
        <input
          id="refund-reason"
          type="text"
          value={reason}
          maxLength={255}
          onChange={(e) => setReason(e.target.value)}
          className={input}
        />
      </div>
    </Shell>
  );
};

/** Admin: cancel (void) a bill. Money already taken is refunded first. */
export const CancelBillModal = ({ bill, onClose, onDone }) => {
  const { error: toastError, success } = useToast();
  const paid = num(bill.paidAmount);
  const [mode, setMode] = useState('CASH');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!reason.trim()) return toastError('Enter a reason for cancelling');
    setSaving(true);
    try {
      const result = await hospitalService.cancelBill(
        bill.id ?? bill.billingId,
        reason.trim(),
        mode
      );
      success(`Bill ${bill.customId || ''} cancelled`);
      onDone?.(result);
    } catch (e) {
      toastError(extractApiError(e, 'Could not cancel the bill'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Shell
      title="Cancel Bill"
      labelledBy="cancel-bill-title"
      subtitle={`${bill.patientName ? `${bill.patientName} · ` : ''}${bill.customId || ''}`}
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className={`${btn} bg-white border border-gray-300 text-gray-700`}
          >
            Keep Bill
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving}
            className={`${btn} bg-red-600 text-white hover:bg-red-700`}
          >
            {saving ? 'Cancelling…' : 'Cancel Bill'}
          </button>
        </>
      }
    >
      <BillSummary bill={bill} />
      {paid > 0 ? (
        <div>
          <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3 mb-3">
            {rupees(paid)} was collected on this bill and will be recorded as refunded.
          </p>
          <span className="block text-xs font-semibold text-gray-700 mb-1">Refunded by</span>
          <ModePicker mode={mode} setMode={setMode} name="Refund method" />
        </div>
      ) : (
        <p className="text-sm text-gray-600">Nothing has been collected, so no refund is needed.</p>
      )}
      <div>
        <label htmlFor="cancel-reason" className="block text-xs font-semibold text-gray-700 mb-1">
          Reason *
        </label>
        <input
          id="cancel-reason"
          type="text"
          value={reason}
          maxLength={255}
          placeholder="e.g. Patient left before consultation, duplicate bill"
          onChange={(e) => setReason(e.target.value)}
          className={input}
        />
      </div>
    </Shell>
  );
};
