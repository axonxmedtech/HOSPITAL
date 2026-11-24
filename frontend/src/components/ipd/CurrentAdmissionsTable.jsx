import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useToast } from '../../context/ToastContext';
import AdmissionFormModal from '../../pages/hospital/nurse/AdmissionFormModal';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';
import { printBlob } from '../../utils/printPdf';
import ActionMenu from '../ActionMenu';
import IcuAdmitModal from '../IcuAdmitModal';
import IcuDischargeToIpdModal from '../IcuDischargeToIpdModal';
import DischargeCheckoutModal from './DischargeCheckoutModal';
import IpdMoneyPanel from './IpdMoneyPanel';

export const isReadyToGo = (row) => (row.status || '').toUpperCase() === 'DISCHARGE_PLANNED';
export const isFormPending = (row) => !row.admissionConfirmed;

/** "17 Nov, 10:01 am": the day and time a desk needs, without seconds or the year. */
export const shortDateTime = (v) => {
  if (!v) return '-';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return '-';
  return d.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
};

const FILTERS = [
  { value: 'all', label: 'All', test: () => true },
  { value: 'ready', label: 'Ready to go', test: isReadyToGo },
  { value: 'form', label: 'Form pending', test: isFormPending },
];

/** Opens an IPD case in-app (no page reload); Back on the case returns to this exact list. */
export const useOpenIpdCase = () => {
  const navigate = useNavigate();
  const location = useLocation();
  return (ipdId, action) =>
    navigate(`/ipd/${ipdId}${action ? `?action=${action}` : ''}`, {
      state: { from: `${location.pathname}${location.search}` },
    });
};

/** Advance and package for one admission, without opening the case. */
function MoneyModal({ ipdId, isAdmin, onClose, onChanged }) {
  const { error: toastError } = useToast();
  const [data, setData] = useState(null);
  const reload = async () => {
    try {
      setData(await hospitalService.getIpdDetails(ipdId));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load the admission'));
      onClose();
    }
  };
  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ipdId]);
  const billing = data?.billing;
  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
      <dialog
        open
        aria-modal="true"
        aria-labelledby="ipd-money-title"
        className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-lg p-6 max-h-[92vh] overflow-y-auto"
      >
        <h3 id="ipd-money-title" className="text-lg font-semibold mb-1">
          Advance &amp; package
        </h3>
        {!data ? (
          <p className="text-sm text-gray-500">Loading…</p>
        ) : !billing ? (
          <p className="text-sm text-gray-500">This admission has no bill.</p>
        ) : (
          <>
            <p className="text-sm text-gray-600">
              {data.patient?.name} · {data.ipdNumber} · Bill ₹{billing.totalAmount} · Paid ₹
              {billing.paidAmount} · Balance ₹{billing.balance}
            </p>
            <IpdMoneyPanel
              ipdId={ipdId}
              data={data}
              isAdmin={isAdmin}
              canAct={data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED'}
              onChanged={async () => {
                await reload();
                onChanged?.();
              }}
            />
          </>
        )}
        <div className="flex justify-end mt-4">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1.5 rounded bg-gray-100 text-sm font-semibold"
          >
            Close
          </button>
        </div>
      </dialog>
    </div>
  );
}

/**
 * The "patients in a bed now" list, shared by reception, the admin and the ICU's Patients view.
 * Each row carries the desk's jobs (checkout, admission form, advance, bill, bed, ICU moves) so
 * most of them need no trip into the case. "Ready to go" lists the discharges the doctor has
 * planned, and a new one is announced as it arrives. Patients in critical care carry an ICU
 * badge; with the ICU module the desk moves them in and out of ICU from the row.
 */
export default function CurrentAdmissionsTable({
  rows,
  startIndex = 0,
  canDesk = false,
  hasBilling = false,
  hasIcu = false,
  showIcuBadge = true,
  isAdmin = false,
  onChanged,
}) {
  const { info, error: toastError } = useToast();
  const openCase = useOpenIpdCase();
  const [searchParams, setSearchParams] = useSearchParams();
  const filter = FILTERS.some((f) => f.value === searchParams.get('ipdFilter'))
    ? searchParams.get('ipdFilter')
    : 'all';
  const setFilter = (value) =>
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value === 'all') next.delete('ipdFilter');
        else next.set('ipdFilter', value);
        return next;
      },
      { replace: true }
    );

  const [checkoutId, setCheckoutId] = useState(null);
  const [formId, setFormId] = useState(null);
  const [moneyId, setMoneyId] = useState(null);
  const [printingId, setPrintingId] = useState(null);
  const [toIcu, setToIcu] = useState(null); // row moving into ICU
  const [outOfIcu, setOutOfIcu] = useState(null); // row moving out of ICU

  // Announce a discharge the doctor has just planned (the list reloads live).
  const plannedRef = useRef(null);
  useEffect(() => {
    const planned = rows.filter(isReadyToGo);
    const known = plannedRef.current;
    if (known && canDesk) {
      planned
        .filter((r) => !known.has(r.ipdId))
        .forEach((r) => info(`Discharge planned: ${r.patientName || r.ipdNumber} is ready to go`));
    }
    plannedRef.current = new Set(planned.map((r) => r.ipdId));
  }, [rows, canDesk, info]);

  const printBill = async (ipdId) => {
    if (printingId) return;
    setPrintingId(ipdId);
    try {
      const bill = await hospitalService.getIpdBill(ipdId);
      if (!bill?.billingId) throw new Error('This admission has no bill yet');
      await printBlob(await hospitalService.downloadReceipt(bill.billingId));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load the bill'));
    } finally {
      setPrintingId(null);
    }
  };

  const statusOf = (r) => {
    const ready = isReadyToGo(r);
    return (
      <>
        <span
          className={`inline-flex items-center whitespace-nowrap px-2 py-0.5 rounded-full text-xs font-medium border ${
            ready
              ? 'bg-amber-50 text-amber-800 border-amber-200'
              : 'bg-green-50 text-green-700 border-green-200'
          }`}
        >
          {ready ? 'Ready to go' : 'Admitted'}
        </span>
        {!r.admissionConfirmed && (
          <div
            className="mt-1 text-xs text-amber-700 whitespace-nowrap"
            title="The patient is in the bed; the admission form still has to be completed."
          >
            Form pending
          </div>
        )}
      </>
    );
  };

  const actionsOf = (r) => (
    <div className="flex items-center justify-end gap-1">
      {isReadyToGo(r) && (
        <button
          type="button"
          onClick={() => setCheckoutId(r.ipdId)}
          className="h-8 px-3 rounded-lg bg-success-600 hover:bg-success-700 text-white text-xs font-semibold whitespace-nowrap focus:outline-none focus-visible:ring-2 focus-visible:ring-success-500"
        >
          Checkout
        </button>
      )}
      <ActionMenu
        actions={[
          {
            label: 'Complete admission form',
            onClick: () => setFormId(r.ipdId),
            hidden: r.admissionConfirmed,
          },
          { label: 'Advance & package', onClick: () => setMoneyId(r.ipdId), hidden: !hasBilling },
          {
            label: printingId === r.ipdId ? 'Preparing bill…' : 'Print bill',
            onClick: () => printBill(r.ipdId),
            hidden: !hasBilling,
          },
          { label: 'Move to ICU', onClick: () => setToIcu(r), hidden: !hasIcu || r.inIcu },
          { label: 'Move out of ICU', onClick: () => setOutOfIcu(r), hidden: !hasIcu || !r.inIcu },
          { label: 'Change bed', onClick: () => openCase(r.ipdId, 'change-bed') },
          { label: 'Open case', onClick: () => openCase(r.ipdId) },
        ]}
      />
    </div>
  );

  const bedLine = (r) => (
    <span className="whitespace-nowrap inline-flex items-center gap-1.5">
      {r.bedNumber || '-'}
      {showIcuBadge && r.inIcu && (
        <span
          className="px-1.5 rounded bg-rose-50 text-rose-700 border border-rose-200 text-[10px] font-semibold leading-4"
          title={r.icuUnitLabel || 'Critical care'}
        >
          ICU
        </span>
      )}
    </span>
  );

  const nameButton = (r) => (
    <button
      type="button"
      onClick={() => openCase(r.ipdId)}
      className="font-medium text-primary-600 hover:text-primary-700 hover:underline text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded"
      title="Open the IPD case"
    >
      {r.patientName || '-'}
    </button>
  );

  const shown = rows.filter(FILTERS.find((f) => f.value === filter).test);
  const count = (f) => rows.filter(f.test).length;

  return (
    <div className="p-4">
      <div className="flex flex-wrap gap-2 mb-4" role="group" aria-label="Filter admissions">
        {FILTERS.map((f) => {
          const n = count(f);
          const active = filter === f.value;
          const urgent = f.value === 'ready' && n > 0;
          return (
            <button
              key={f.value}
              type="button"
              aria-pressed={active}
              onClick={() => setFilter(f.value)}
              className={`inline-flex items-center gap-1.5 h-8 px-3 rounded-full text-xs font-semibold border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${
                active
                  ? 'bg-primary-50 text-primary-700 border-primary-300'
                  : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
              }`}
            >
              {f.label}
              <span
                className={`min-w-5 px-1.5 rounded-full text-[11px] leading-5 text-center ${
                  urgent ? 'bg-amber-500 text-white' : active ? 'bg-primary-100' : 'bg-slate-100'
                }`}
              >
                {n}
              </span>
            </button>
          );
        })}
      </div>

      {shown.length === 0 ? (
        <p className="text-sm text-slate-500 py-8 text-center">
          {filter === 'ready'
            ? 'No discharges planned right now.'
            : 'Every admission form is complete.'}
        </p>
      ) : (
        <>
        {/* Phone and small tablet: one card per patient, nothing off-screen. */}
        <ul className="md:hidden space-y-2">
          {shown.map((r, idx) => (
            <li
              key={r.ipdId ?? idx}
              className={`border rounded-xl p-3 ${isReadyToGo(r) ? 'bg-amber-50/50 border-amber-200' : 'border-slate-200'}`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  {nameButton(r)}
                  <div className="text-xs text-slate-500">
                    {r.ipdNumber || r.ipdId} · {shortDateTime(r.admissionDateTime)}
                  </div>
                </div>
                <div className="text-right shrink-0">{statusOf(r)}</div>
              </div>
              <div className="mt-2 text-sm text-slate-700">
                {r.wardName || '-'} · {bedLine(r)}
              </div>
              <div className="mt-1 flex items-center justify-between gap-2">
                <span className="text-xs text-slate-500 truncate">{r.doctorName || '-'}</span>
                {canDesk && actionsOf(r)}
              </div>
            </li>
          ))}
        </ul>
        <div className="hidden md:block overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="text-xs font-semibold text-slate-500 uppercase tracking-wide border-b border-slate-200">
                <th className="px-3 py-2.5 w-10">#</th>
                <th className="px-3 py-2.5">Patient</th>
                <th className="px-3 py-2.5">Doctor</th>
                <th className="px-3 py-2.5">Ward / Bed</th>
                <th className="px-3 py-2.5">Admitted</th>
                <th className="px-3 py-2.5">Status</th>
                {canDesk && <th className="px-3 py-2.5 text-right">Actions</th>}
              </tr>
            </thead>
            <tbody>
              {shown.map((r, idx) => {
                const ready = isReadyToGo(r);
                return (
                  <tr
                    key={r.ipdId ?? idx}
                    className={`border-b border-slate-100 last:border-0 ${ready ? 'bg-amber-50/50' : 'hover:bg-slate-50/60'}`}
                  >
                    <td className="px-3 py-3 text-slate-400 tabular-nums">{startIndex + idx + 1}</td>
                    <td className="px-3 py-3">
                      {nameButton(r)}
                      <div className="text-xs text-slate-500 whitespace-nowrap">
                        {r.ipdNumber || r.ipdId}
                      </div>
                    </td>
                    <td className="px-3 py-3 text-slate-700">{r.doctorName || '-'}</td>
                    <td className="px-3 py-3">
                      <div className="text-slate-700">{r.wardName || '-'}</div>
                      <div className="text-xs text-slate-500">{bedLine(r)}</div>
                    </td>
                    <td className="px-3 py-3 text-slate-600 whitespace-nowrap">
                      {shortDateTime(r.admissionDateTime)}
                    </td>
                    <td className="px-3 py-3">{statusOf(r)}</td>
                    {canDesk && <td className="px-3 py-3">{actionsOf(r)}</td>}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        </>
      )}

      {checkoutId && (
        <DischargeCheckoutModal
          ipdId={checkoutId}
          onClose={() => setCheckoutId(null)}
          onDischarged={onChanged}
        />
      )}
      {formId && (
        <AdmissionFormModal
          admissionId={Number(formId)}
          onClose={() => setFormId(null)}
          onConfirmed={onChanged}
        />
      )}
      {toIcu && (
        <IcuAdmitModal
          isOpen
          initialPatient={{ ipdId: toIcu.ipdId, ipdNumber: toIcu.ipdNumber, patientName: toIcu.patientName, wardName: toIcu.wardName, bedNumber: toIcu.bedNumber }}
          onClose={() => setToIcu(null)}
          onSuccess={() => {
            setToIcu(null);
            onChanged?.();
          }}
        />
      )}
      {outOfIcu && (
        <IcuDischargeToIpdModal
          isOpen
          initialIcuPatient={{ ipdId: outOfIcu.ipdId, ipdNumber: outOfIcu.ipdNumber, patientName: outOfIcu.patientName, icuWardName: outOfIcu.wardName, bedNumber: outOfIcu.bedNumber }}
          onClose={() => setOutOfIcu(null)}
          onSuccess={() => {
            setOutOfIcu(null);
            onChanged?.();
          }}
        />
      )}
      {moneyId && (
        <MoneyModal
          ipdId={moneyId}
          isAdmin={isAdmin}
          onClose={() => setMoneyId(null)}
          onChanged={onChanged}
        />
      )}
    </div>
  );
}
