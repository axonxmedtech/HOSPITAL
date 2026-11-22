import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';

export const PAYER_TYPES = [
  { value: 'TPA', label: 'TPA (cashless)' },
  { value: 'INSURANCE', label: 'Insurance company' },
  { value: 'GOVT_SCHEME', label: 'Government scheme (PM-JAY, CGHS, ECHS …)' },
  { value: 'CORPORATE', label: 'Corporate' },
];

/** Mirrors IpdInsuranceService on the server. */
export const CLAIM_STATUS = {
  DRAFT: { label: 'Draft', cls: 'bg-gray-100 text-gray-700' },
  PREAUTH_SUBMITTED: { label: 'Pre-auth submitted', cls: 'bg-sky-50 text-sky-800' },
  QUERY: { label: 'Query from insurer', cls: 'bg-amber-50 text-amber-800' },
  PREAUTH_APPROVED: { label: 'Approved', cls: 'bg-emerald-50 text-emerald-800' },
  PREAUTH_REJECTED: { label: 'Pre-auth rejected', cls: 'bg-red-50 text-red-700' },
  ENHANCEMENT_SUBMITTED: { label: 'Enhancement submitted', cls: 'bg-sky-50 text-sky-800' },
  FINAL_SUBMITTED: { label: 'Final bill submitted', cls: 'bg-indigo-50 text-indigo-800' },
  SETTLED: { label: 'Settled', cls: 'bg-emerald-100 text-emerald-900' },
  CLAIM_REJECTED: { label: 'Claim rejected', cls: 'bg-red-50 text-red-700' },
  WITHDRAWN: { label: 'Withdrawn', cls: 'bg-gray-100 text-gray-500' },
};

/** What each step asks for. */
const STEP = {
  PREAUTH_SUBMITTED: { button: 'Submit pre-auth', amount: 'Amount requested (₹)' },
  QUERY: { button: 'Insurer raised a query', note: "The insurer's query *" },
  PREAUTH_APPROVED: { button: 'Record approval', amount: 'Amount approved (₹) *' },
  PREAUTH_REJECTED: { button: 'Pre-auth rejected', note: 'Reason *' },
  ENHANCEMENT_SUBMITTED: { button: 'Request enhancement', amount: 'New amount requested (₹)' },
  FINAL_SUBMITTED: { button: 'Submit final bill', amount: 'Final amount claimed (₹)' },
  CLAIM_REJECTED: { button: 'Claim rejected', note: 'Reason *' },
  WITHDRAWN: { button: 'Withdraw claim', note: 'Reason' },
};

const rupees = (n) => (n == null ? '—' : `₹${Number(n).toLocaleString('en-IN')}`);

function Dialog({ title, children, onClose, onSubmit, saving, submitLabel }) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50 px-4">
      <dialog
        open
        aria-modal="true"
        aria-label={title}
        className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-md p-6 max-h-[90vh] overflow-y-auto"
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
            className="px-3 py-1.5 bg-blue-600 text-white rounded disabled:opacity-60"
          >
            {saving ? 'Saving…' : submitLabel}
          </button>
        </div>
      </dialog>
    </div>
  );
}

const Field = ({ id, label, children }) => (
  <div className="mb-3">
    <label htmlFor={id} className="block text-sm font-medium mb-1">
      {label}
    </label>
    {children}
  </div>
);

/**
 * The admission's insurance / TPA claim: who pays, each step the insurer takes, and the
 * settlement. The approved amount is the insurer's share, so at discharge the patient pays
 * only the rest; the settlement is posted to the IPD bill when the money arrives.
 */
export default function InsurancePanel({ admissionId, canEdit = false, onChanged }) {
  const { success, error: toastError } = useToast();
  const [claims, setClaims] = useState(null);
  const [openForm, setOpenForm] = useState(null);
  const [step, setStep] = useState(null); // { claim, status, amount, claimNumber, note }
  const [settle, setSettle] = useState(null); // { claim, amount, deduction, deductionReason, reference }
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setClaims(await hospitalService.getIpdClaims(admissionId));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load insurance claims'));
      setClaims([]);
    }
  }, [admissionId, toastError]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (fn, okText) => {
    setSaving(true);
    try {
      await fn();
      success(okText);
      await load();
      await onChanged?.();
      return true;
    } catch (err) {
      toastError(extractApiError(err, 'Could not save'));
      return false;
    } finally {
      setSaving(false);
    }
  };

  const num = (v) => (v === '' || v == null ? null : Number(v));
  const current = (claims || [])[0];
  const hasOpen =
    current &&
    !['PREAUTH_REJECTED', 'SETTLED', 'CLAIM_REJECTED', 'WITHDRAWN'].includes(current.status);

  if (claims === null) return <p className="text-sm text-gray-500">Loading…</p>;

  return (
    <div>
      {!hasOpen && canEdit && (
        <button
          type="button"
          onClick={() =>
            setOpenForm({
              payerType: 'TPA',
              insurerName: '',
              tpaName: '',
              policyNumber: '',
              memberId: '',
              requestedAmount: '',
            })
          }
          className="mb-4 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-semibold"
        >
          + Record insurance / TPA claim
        </button>
      )}
      {claims.length === 0 && (
        <p className="text-sm text-gray-500">No insurance claim on this stay.</p>
      )}

      {claims.map((c) => {
        const st = CLAIM_STATUS[c.status] || CLAIM_STATUS.DRAFT;
        return (
          <div key={c.publicId} className="border rounded p-4 mb-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="font-semibold">
                {c.insurerName}
                {c.tpaName ? (
                  <span className="font-normal text-gray-600"> via {c.tpaName}</span>
                ) : null}
              </div>
              <span className={`text-xs px-2 py-0.5 rounded-full ${st.cls}`}>{st.label}</span>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm mt-2">
              <div>
                <span className="text-gray-500">Policy:</span> {c.policyNumber || '—'}
              </div>
              <div>
                <span className="text-gray-500">Member ID:</span> {c.memberId || '—'}
              </div>
              <div>
                <span className="text-gray-500">Claim no.:</span> {c.claimNumber || '—'}
              </div>
              <div>
                <span className="text-gray-500">Requested:</span> {rupees(c.requestedAmount)}
              </div>
              <div>
                <span className="text-gray-500">Approved:</span>{' '}
                <strong>{rupees(c.approvedAmount)}</strong>
              </div>
              <div>
                <span className="text-gray-500">Settled:</span> {rupees(c.settledAmount)}
              </div>
              {c.deductionAmount ? (
                <div className="col-span-2">
                  <span className="text-gray-500">Deduction:</span> {rupees(c.deductionAmount)} —{' '}
                  {c.deductionReason}
                </div>
              ) : null}
            </div>

            {canEdit && (c.nextSteps?.length > 0 || c.canSettle) && (
              <div className="flex flex-wrap gap-2 mt-3">
                {c.nextSteps.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() =>
                      setStep({ claim: c, status: s, amount: '', claimNumber: '', note: '' })
                    }
                    className="px-2.5 py-1 text-xs font-semibold rounded border border-gray-300 hover:bg-gray-50"
                  >
                    {STEP[s]?.button || s}
                  </button>
                ))}
                {c.canSettle && (
                  <button
                    type="button"
                    onClick={() =>
                      setSettle({
                        claim: c,
                        amount: c.approvedAmount ?? '',
                        deduction: '',
                        deductionReason: '',
                        reference: '',
                      })
                    }
                    className="px-2.5 py-1 text-xs font-semibold rounded bg-emerald-600 text-white hover:bg-emerald-700"
                  >
                    Record settlement
                  </button>
                )}
              </div>
            )}

            {c.events?.length > 0 && (
              <ol className="mt-3 border-t pt-2 space-y-1 text-xs text-gray-600">
                {c.events.map((e) => (
                  <li key={e.id}>
                    {new Date(e.createdAt).toLocaleString()} ·{' '}
                    {CLAIM_STATUS[e.status]?.label || e.status}
                    {e.amount != null ? ` · ${rupees(e.amount)}` : ''}
                    {e.note ? ` · ${e.note}` : ''}
                  </li>
                ))}
              </ol>
            )}
          </div>
        );
      })}

      {openForm && (
        <Dialog
          title="Insurance / TPA claim"
          onClose={() => setOpenForm(null)}
          saving={saving}
          submitLabel="Save claim"
          onSubmit={async () => {
            if (!openForm.insurerName.trim()) return toastError('Enter the insurer or scheme name');
            const ok = await run(
              () =>
                hospitalService.openIpdClaim(admissionId, {
                  ...openForm,
                  requestedAmount: num(openForm.requestedAmount),
                }),
              'Claim recorded'
            );
            if (ok) setOpenForm(null);
          }}
        >
          <Field id="cl-payer" label="Who pays">
            <select
              id="cl-payer"
              value={openForm.payerType}
              onChange={(e) => setOpenForm({ ...openForm, payerType: e.target.value })}
              className="w-full border p-2 rounded"
            >
              {PAYER_TYPES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </Field>
          {[
            ['insurerName', 'Insurer / scheme *'],
            ['tpaName', 'TPA'],
            ['policyNumber', 'Policy number'],
            ['memberId', 'Member / card ID'],
          ].map(([k, label]) => (
            <Field key={k} id={`cl-${k}`} label={label}>
              <input
                id={`cl-${k}`}
                value={openForm[k]}
                maxLength={150}
                onChange={(e) => setOpenForm({ ...openForm, [k]: e.target.value })}
                className="w-full border p-2 rounded"
              />
            </Field>
          ))}
          <Field id="cl-req" label="Estimated amount (₹)">
            <input
              id="cl-req"
              type="number"
              min="0"
              value={openForm.requestedAmount}
              onChange={(e) => setOpenForm({ ...openForm, requestedAmount: e.target.value })}
              className="w-full border p-2 rounded"
            />
          </Field>
        </Dialog>
      )}

      {step && (
        <Dialog
          title={STEP[step.status]?.button || step.status}
          onClose={() => setStep(null)}
          saving={saving}
          submitLabel="Save"
          onSubmit={async () => {
            const ok = await run(
              () =>
                hospitalService.stepIpdClaim(step.claim.publicId, {
                  status: step.status,
                  amount: num(step.amount),
                  claimNumber: step.claimNumber.trim() || null,
                  note: step.note.trim() || null,
                }),
              'Claim updated'
            );
            if (ok) setStep(null);
          }}
        >
          {STEP[step.status]?.amount && (
            <Field id="st-amount" label={STEP[step.status].amount}>
              <input
                id="st-amount"
                type="number"
                min="0"
                value={step.amount}
                onChange={(e) => setStep({ ...step, amount: e.target.value })}
                className="w-full border p-2 rounded"
              />
            </Field>
          )}
          {!step.claim.claimNumber && (
            <Field id="st-claimno" label="Claim / pre-auth number">
              <input
                id="st-claimno"
                value={step.claimNumber}
                maxLength={80}
                onChange={(e) => setStep({ ...step, claimNumber: e.target.value })}
                className="w-full border p-2 rounded"
              />
            </Field>
          )}
          <Field id="st-note" label={STEP[step.status]?.note || 'Note'}>
            <textarea
              id="st-note"
              rows={3}
              maxLength={500}
              value={step.note}
              onChange={(e) => setStep({ ...step, note: e.target.value })}
              className="w-full border p-2 rounded"
            />
          </Field>
        </Dialog>
      )}

      {settle && (
        <Dialog
          title="Record settlement"
          onClose={() => setSettle(null)}
          saving={saving}
          submitLabel="Post to bill"
          onSubmit={async () => {
            if (!settle.amount || Number(settle.amount) <= 0)
              return toastError('Enter the amount the insurer paid');
            if (Number(settle.deduction) > 0 && !settle.deductionReason.trim())
              return toastError('Enter the reason for the deduction');
            const ok = await run(
              () =>
                hospitalService.settleIpdClaim(settle.claim.publicId, {
                  amount: num(settle.amount),
                  deduction: num(settle.deduction),
                  deductionReason: settle.deductionReason.trim() || null,
                  reference: settle.reference.trim() || null,
                }),
              'Settlement posted to the bill'
            );
            if (ok) setSettle(null);
          }}
        >
          <p className="text-xs text-gray-500 mb-3">
            The amount is posted to the IPD bill as an insurance payment. A deduction becomes the
            patient&apos;s due.
          </p>
          <Field id="se-amount" label="Amount received (₹) *">
            <input
              id="se-amount"
              type="number"
              min="0"
              value={settle.amount}
              onChange={(e) => setSettle({ ...settle, amount: e.target.value })}
              className="w-full border p-2 rounded"
            />
          </Field>
          <Field id="se-deduction" label="Deduction (₹)">
            <input
              id="se-deduction"
              type="number"
              min="0"
              value={settle.deduction}
              onChange={(e) => setSettle({ ...settle, deduction: e.target.value })}
              className="w-full border p-2 rounded"
            />
          </Field>
          {Number(settle.deduction) > 0 && (
            <Field id="se-reason" label="Deduction reason *">
              <input
                id="se-reason"
                value={settle.deductionReason}
                maxLength={255}
                onChange={(e) => setSettle({ ...settle, deductionReason: e.target.value })}
                className="w-full border p-2 rounded"
              />
            </Field>
          )}
          <Field id="se-ref" label="UTR / reference">
            <input
              id="se-ref"
              value={settle.reference}
              maxLength={200}
              onChange={(e) => setSettle({ ...settle, reference: e.target.value })}
              className="w-full border p-2 rounded"
            />
          </Field>
        </Dialog>
      )}
    </div>
  );
}
