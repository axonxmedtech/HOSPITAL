import React, { useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';

/**
 * "Change doctor" and "Cancel visit" for an OPD visit still waiting for the doctor. Without them
 * a patient who left without being seen, or was sent to the wrong doctor, sat in the queue for
 * ever: a visit could only leave the queue through a consultation.
 */

const REASONS = [
  'Left without being seen',
  'Registered twice by mistake',
  'Wrong patient selected',
  'Patient asked to cancel',
];

const btn = 'px-4 py-2 rounded-lg text-sm font-semibold disabled:opacity-50';
const input =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-transparent';

/** What happens to the visit's bill, in words, for the cancel dialog. */
function billOutcome(paid, isAdmin) {
  if (!(paid > 0)) return 'Any unpaid bill for this visit is cancelled with it.';
  const amount = `₹${paid.toLocaleString('en-IN')}`;
  return isAdmin
    ? `The ${amount} collected will be refunded and the bill cancelled.`
    : `${amount} was already collected: the bill stays until the admin refunds it from Billing.`;
}

export const isWaitingVisit = (opd) => opd?.status === 'QUEUED';

const Dialog = ({ id, title, subtitle, onClose, children, footer }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
    <button
      type="button"
      aria-label="Close"
      className="absolute inset-0 bg-gray-500/75"
      onClick={onClose}
    />
    <dialog
      open
      aria-modal="true"
      aria-labelledby={id}
      className="relative m-0 p-0 border-0 text-gray-900 bg-white rounded-xl shadow-xl w-full max-w-md text-left"
    >
      <div className="px-6 pt-5 pb-4">
        <h3 id={id} className="text-lg font-semibold text-gray-900">
          {title}
        </h3>
        {subtitle && <p className="text-sm text-gray-500 mt-0.5">{subtitle}</p>}
        <div className="mt-4 space-y-4">{children}</div>
      </div>
      <div className="bg-gray-50 px-6 py-3 flex justify-end gap-2 rounded-b-xl">{footer}</div>
    </dialog>
  </div>
);

export function CancelVisitModal({ opd, isAdmin, onClose, onDone }) {
  const { error: toastError, success } = useToast();
  const [choice, setChoice] = useState(REASONS[0]);
  const [other, setOther] = useState('');
  const [refundMode, setRefundMode] = useState('CASH');
  const [saving, setSaving] = useState(false);
  const paid = Number(opd?.bill?.paidAmount || 0);
  const reason = choice === 'OTHER' ? other.trim() : choice;

  const submit = async () => {
    setSaving(true);
    try {
      const res = await hospitalService.cancelOpd(opd.id, reason, refundMode);
      success(res?.message || 'OPD visit cancelled');
      onDone?.(res);
      onClose();
    } catch (err) {
      toastError(extractApiError(err, 'Could not cancel the visit'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      id="cancel-visit-title"
      title="Cancel OPD visit"
      subtitle={`${opd.caseId || 'OPD'} · ${opd.patient?.name || opd.patientName || ''}`}
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className={`${btn} bg-white border border-gray-300 text-gray-700`}
          >
            Keep visit
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving || !reason}
            className={`${btn} bg-red-600 text-white hover:bg-red-700`}
          >
            {saving ? 'Cancelling...' : 'Cancel visit'}
          </button>
        </>
      }
    >
      <div>
        <label
          htmlFor="cancel-visit-reason"
          className="block text-sm font-medium text-gray-700 mb-1"
        >
          Reason
        </label>
        <select
          id="cancel-visit-reason"
          value={choice}
          onChange={(e) => setChoice(e.target.value)}
          className={input}
        >
          {REASONS.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
          <option value="OTHER">Other...</option>
        </select>
        {choice === 'OTHER' && (
          <input
            aria-label="Other reason"
            value={other}
            maxLength={255}
            onChange={(e) => setOther(e.target.value)}
            placeholder="Write the reason"
            className={`${input} mt-2`}
          />
        )}
      </div>
      <p className="text-sm text-gray-600">
        The patient is taken off the doctor&apos;s queue. {billOutcome(paid, isAdmin)}
      </p>
      {paid > 0 && isAdmin && (
        <div>
          <label
            htmlFor="cancel-visit-refund"
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            Refund as
          </label>
          <select
            id="cancel-visit-refund"
            value={refundMode}
            onChange={(e) => setRefundMode(e.target.value)}
            className={input}
          >
            <option value="CASH">Cash</option>
            <option value="CARD">Card</option>
            <option value="UPI">UPI / Online</option>
          </select>
        </div>
      )}
    </Dialog>
  );
}

export function ChangeDoctorModal({ title, subtitle, currentDoctorId, onSave, onClose, onDone }) {
  const { error: toastError, success } = useToast();
  const [doctors, setDoctors] = useState(null);
  const [doctorId, setDoctorId] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let live = true;
    hospitalService
      .getDoctors('', 0, 200)
      .then((res) => {
        const list = Array.isArray(res) ? res : res?.content || [];
        if (live) setDoctors(list.filter((d) => d.isActive !== false && d.id !== currentDoctorId));
      })
      .catch((err) => {
        if (live) {
          setDoctors([]);
          toastError(extractApiError(err, 'Could not load doctors'));
        }
      });
    return () => {
      live = false;
    };
  }, [currentDoctorId, toastError]);

  const submit = async () => {
    setSaving(true);
    try {
      await onSave(Number(doctorId));
      const d = doctors.find((x) => String(x.id) === String(doctorId));
      success(`Moved to ${d?.name || 'the new doctor'}`);
      onDone?.();
      onClose();
    } catch (err) {
      toastError(extractApiError(err, 'Could not change the doctor'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      id="change-doctor-title"
      title={title}
      subtitle={subtitle}
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className={`${btn} bg-white border border-gray-300 text-gray-700`}
          >
            Close
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving || !doctorId}
            className={`${btn} bg-sky-600 text-white hover:bg-sky-700`}
          >
            {saving ? 'Saving...' : 'Change doctor'}
          </button>
        </>
      }
    >
      {doctors === null && <p className="text-sm text-gray-500">Loading doctors...</p>}
      {doctors?.length === 0 && (
        <p className="text-sm text-amber-800 bg-amber-50 rounded p-2">
          There is no other doctor to move to. The admin can add one under Staff → Doctors.
        </p>
      )}
      {doctors?.length > 0 && (
        <div>
          <label
            htmlFor="change-doctor-select"
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            New doctor
          </label>
          <select
            id="change-doctor-select"
            value={doctorId}
            onChange={(e) => setDoctorId(e.target.value)}
            className={input}
          >
            <option value="">Select a doctor...</option>
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
                {d.specialization ? ` — ${d.specialization}` : ''}
              </option>
            ))}
          </select>
        </div>
      )}
    </Dialog>
  );
}

/** Row buttons for a waiting OPD visit. Renders nothing once the doctor has seen the patient. */
export default function OpdVisitActions({
  opd,
  isAdmin = false,
  canChangeDoctor = true,
  onChanged,
}) {
  const [open, setOpen] = useState(null);
  if (!isWaitingVisit(opd)) return null;
  const small = 'px-3 py-1 rounded-md text-xs font-semibold transition-colors';
  return (
    <>
      {canChangeDoctor && (
        <button
          type="button"
          onClick={() => setOpen('doctor')}
          className={`${small} bg-sky-50 text-sky-700 hover:bg-sky-100`}
        >
          Change doctor
        </button>
      )}
      <button
        type="button"
        onClick={() => setOpen('cancel')}
        className={`${small} bg-red-50 text-red-700 hover:bg-red-100`}
      >
        Cancel visit
      </button>
      {open === 'cancel' && (
        <CancelVisitModal
          opd={opd}
          isAdmin={isAdmin}
          onClose={() => setOpen(null)}
          onDone={onChanged}
        />
      )}
      {open === 'doctor' && (
        <ChangeDoctorModal
          title="Change doctor"
          subtitle={`${opd.caseId || 'OPD'} · ${opd.patient?.name || opd.patientName || ''} · now with ${opd.doctor?.name || 'no doctor'}`}
          currentDoctorId={opd.doctor?.id}
          onSave={(doctorId) => hospitalService.changeOpdDoctor(opd.id, doctorId)}
          onClose={() => setOpen(null)}
          onDone={onChanged}
        />
      )}
    </>
  );
}
