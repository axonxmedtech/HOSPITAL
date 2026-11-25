import { useEffect, useRef, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import wardService from '../../services/wardService';
import { extractApiError } from '../../utils/apiError';
import { newIdempotencyKey } from '../../utils/idempotencyKey';
import { printBlob } from '../../utils/printPdf';
import { DischargeTypeFields, dischargeNoteRequired, leavesWithDues } from './DischargeType';

const rupees = (n) => `₹${(Number(n) || 0).toLocaleString('en-IN')}`;

/**
 * What the patient owes at the desk, bill by bill. The insurer's approved share comes off the IPD
 * bill first (it is not the patient's to pay), and an unused advance is what is left over when
 * the IPD bill is in credit.
 */
export const checkoutMoney = (billing) => {
  if (!billing) return { bills: [], due: 0, unusedAdvance: 0, insurer: 0 };
  const insurer = Number(billing.insuranceExpected) || 0;
  let insurerLeft = insurer;
  const bills = (billing.outstandingBills || [])
    .map((o) => {
      let due = Number(o.balance) || 0;
      if (o.billingType === 'IPD' && insurerLeft > 0) {
        const covered = Math.min(insurerLeft, due);
        insurerLeft -= covered;
        due -= covered;
      }
      return { billingId: o.billingId, customId: o.customId, billingType: o.billingType, due };
    })
    .filter((b) => b.due > 0);
  const due = bills.reduce((sum, b) => sum + b.due, 0);
  const unusedAdvance = Math.max(0, -((Number(billing.balance) || 0) - insurer));
  return { bills, due, unusedAdvance, insurer };
};

const PAY_MODES = [
  ['CASH', 'Cash'],
  ['UPI', 'UPI'],
  ['CARD', 'Card'],
];

/**
 * Pays the bills one after another (never in parallel: each payment changes the balance the
 * next one is checked against). `onPaid` runs after each, so a later refusal still knows that
 * money was taken.
 */
export const payInTurn = (bills, payment, onPaid, keyFor) =>
  bills.reduce(
    (chain, bill) =>
      chain
        .then(() =>
          hospitalService.payBilling(bill.billingId, {
            ...payment,
            amount: bill.due,
            // keyFor(billingId): one idempotency key per bill for this checkout, so retrying a
            // checkout whose response was lost never collects a bill twice.
            ...(keyFor ? { idempotencyKey: keyFor(bill.billingId) } : {}),
          })
        )
        .then(onPaid),
    Promise.resolve()
  );

const submitLabel = (busy, collecting, due) => {
  if (busy) return 'Working…';
  return collecting ? `Collect ${rupees(due)} & discharge` : 'Confirm discharge';
};

function MoneySummary({ billing, money }) {
  return (
    <div className="text-sm border border-slate-200 rounded-xl p-3 mb-3 space-y-1">
      <div className="flex justify-between">
        <span>Total bill</span>
        <span>{rupees(billing.totalAmount)}</span>
      </div>
      <div className="flex justify-between text-slate-600">
        <span>Paid so far (advance included)</span>
        <span>{rupees(billing.paidAmount)}</span>
      </div>
      {money.insurer > 0 && (
        <div className="flex justify-between text-slate-600">
          <span>Insurer pays</span>
          <span>{rupees(money.insurer)}</span>
        </div>
      )}
      <div className="flex justify-between font-semibold border-t border-slate-200 pt-1">
        <span>Patient pays now</span>
        <span>{rupees(money.due)}</span>
      </div>
      {money.bills.length > 1 && (
        <p className="text-xs text-slate-500">
          {money.bills.map((b) => `${b.customId} (${b.billingType})`).join(', ')}
        </p>
      )}
    </div>
  );
}

function PaymentFields({ mode, onMode, reference, onReference }) {
  const cash = mode === 'CASH';
  return (
    <div className={`grid gap-2 mb-3 ${cash ? 'grid-cols-1' : 'grid-cols-2'}`}>
      <div>
        <label htmlFor="checkout-mode" className="block text-xs font-medium text-slate-600 mb-1">
          Paid by
        </label>
        <select
          id="checkout-mode"
          value={mode}
          onChange={(e) => onMode(e.target.value)}
          className="w-full border border-slate-300 p-2 rounded-lg text-sm bg-white"
        >
          {PAY_MODES.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </select>
      </div>
      {!cash && (
        <div>
          <label htmlFor="checkout-ref" className="block text-xs font-medium text-slate-600 mb-1">
            {mode === 'UPI' ? 'UTR *' : 'Card last 4 digits'}
          </label>
          <input
            id="checkout-ref"
            value={reference}
            onChange={(e) => onReference(e.target.value)}
            className="w-full border border-slate-300 p-2 rounded-lg text-sm"
          />
        </div>
      )}
    </div>
  );
}

function RefundBox({ amount, refundMode, onRefundMode }) {
  return (
    <div className="text-sm rounded-xl p-3 mb-3 bg-emerald-50 text-emerald-900">
      <p className="mb-2">
        {rupees(amount)} of the advance is unused and goes back to the patient.
      </p>
      <select
        aria-label="Return the advance by"
        value={refundMode}
        onChange={(e) => onRefundMode(e.target.value)}
        className="w-full border border-slate-300 p-2 rounded-lg bg-white"
      >
        <option value="CASH">Return by cash</option>
        <option value="UPI">Return by UPI</option>
        <option value="CARD">Return to card</option>
      </select>
    </div>
  );
}

function DoneStep({ showBed, bedCleaned, onMarkBed, printing, onPrint, onClose }) {
  return (
    <>
      <p className="text-sm bg-emerald-50 text-emerald-900 rounded-xl p-3 mb-4">
        The admission is closed and the bed is waiting for cleaning. Print what the patient takes
        home.
      </p>
      <div className="flex flex-wrap justify-end gap-2">
        {showBed && (
          <button
            type="button"
            onClick={onMarkBed}
            disabled={bedCleaned}
            className="px-3 py-2 rounded-xl border border-slate-300 bg-white hover:bg-slate-50 text-sm font-semibold disabled:opacity-60"
          >
            {bedCleaned ? 'Bed available ✓' : 'Mark bed cleaned'}
          </button>
        )}
        <button
          type="button"
          onClick={onPrint}
          disabled={printing}
          className="px-3 py-2 rounded-xl bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold disabled:opacity-60"
        >
          {printing ? 'Preparing…' : 'Print summary + bill'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="px-3 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-sm font-semibold"
        >
          Done
        </button>
      </div>
    </>
  );
}

/**
 * Reception's whole discharge in one pop-up: discharge type, the patient's share collected
 * inline (every unpaid bill, insurer share taken off), any unused advance returned, the
 * discharge confirmed, then one print of the summary and the final bill, and the bed marked
 * cleaned. Opens from the IPD case or straight from the IPD list.
 *
 * `data` is the IPD details when the caller already has them; otherwise they are loaded.
 */
export default function DischargeCheckoutModal({
  ipdId,
  data: givenData,
  canMarkBed = true,
  onClose,
  onDischarged,
}) {
  const { success, error: toastError } = useToast();
  const [data, setData] = useState(givenData || null);
  const [type, setType] = useState(givenData?.dischargeType || 'NORMAL');
  const [note, setNote] = useState(givenData?.dischargeNote || '');
  const [mode, setMode] = useState('CASH');
  const [reference, setReference] = useState('');
  const [refundMode, setRefundMode] = useState('CASH');
  const [collectNow, setCollectNow] = useState(true);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [bedCleaned, setBedCleaned] = useState(false);
  const closeRef = useRef(onClose);
  // One idempotency key per bill for the life of this checkout (see payInTurn).
  const payKeys = useRef(new Map());
  const payKeyFor = (billingId) => {
    if (!payKeys.current.has(billingId))
      payKeys.current.set(billingId, newIdempotencyKey('ipdpay'));
    return payKeys.current.get(billingId);
  };
  closeRef.current = onClose;

  useEffect(() => {
    if (givenData) return undefined;
    let active = true;
    hospitalService
      .getIpdDetails(ipdId)
      .then((d) => {
        if (!active) return;
        setData(d);
        setType(d?.dischargeType || 'NORMAL');
        setNote(d?.dischargeNote || '');
      })
      .catch((err) => {
        if (!active) return;
        toastError(extractApiError(err, 'Could not load the admission'));
        closeRef.current();
      });
    return () => {
      active = false;
    };
    // Loaded once per admission; a new givenData object from a parent re-render is not a reload.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ipdId, toastError]);

  const money = checkoutMoney(data?.billing);
  const mayLeaveOwing = leavesWithDues(type);
  const collecting = money.due > 0 && (!mayLeaveOwing || collectNow);
  const missingNote = dischargeNoteRequired(type) && !note.trim();
  const missingUtr = collecting && mode === 'UPI' && !reference.trim();
  const bedId = data?.admission?.bedId;
  const subtitle = [
    data?.patient?.name,
    data?.ipdNumber,
    data?.admission?.bed && `Bed ${data.admission.bed}`,
  ]
    .filter(Boolean)
    .join(' · ');

  const checkout = async () => {
    setBusy(true);
    let paidSomething = false;
    try {
      if (collecting) {
        await payInTurn(
          money.bills,
          { mode, reference: reference.trim() || null },
          () => {
            paidSomething = true;
          },
          payKeyFor
        );
      }
      await hospitalService.confirmDischarge(ipdId, {
        dischargeType: type,
        dischargeNote: note.trim() || null,
        ...(money.unusedAdvance > 0 ? { refundMode } : {}),
      });
      success(collecting ? `${rupees(money.due)} collected · discharged` : 'Discharge completed');
      setDone(true);
      onDischarged?.();
    } catch (err) {
      toastError(extractApiError(err, 'Could not complete the discharge'));
      // A payment may have gone through before the refusal: show what is still owed now.
      if (paidSomething) {
        hospitalService
          .getIpdDetails(ipdId)
          .then(setData)
          .catch(() => {
            /* the next open shows it */
          });
      }
    } finally {
      setBusy(false);
    }
  };

  const printPack = async () => {
    if (printing) return;
    setPrinting(true);
    try {
      await printBlob(await hospitalService.downloadDischargePack(ipdId));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load the print-out'));
    } finally {
      setPrinting(false);
    }
  };

  const markBedCleaned = async () => {
    try {
      await wardService.markBedCleaned(bedId);
      setBedCleaned(true);
      success('Bed marked cleaned and available');
    } catch (err) {
      toastError(extractApiError(err, 'Could not mark the bed cleaned'));
    }
  };

  let body;
  if (!data) {
    body = <p className="text-sm text-slate-500">Loading…</p>;
  } else if (done) {
    body = (
      <DoneStep
        showBed={canMarkBed && !!bedId}
        bedCleaned={bedCleaned}
        onMarkBed={markBedCleaned}
        printing={printing}
        onPrint={printPack}
        onClose={onClose}
      />
    );
  } else {
    body = (
      <>
        <DischargeTypeFields
          type={type}
          note={note}
          onType={setType}
          onNote={setNote}
          idPrefix="checkout-dt"
        />
        {data.billing && <MoneySummary billing={data.billing} money={money} />}
        {money.due > 0 && mayLeaveOwing && (
          <label className="flex items-center gap-2 text-sm mb-3">
            <input
              type="checkbox"
              checked={collectNow}
              onChange={(e) => setCollectNow(e.target.checked)}
            />
            Collect {rupees(money.due)} now (otherwise the bill stays open)
          </label>
        )}
        {collecting && (
          <PaymentFields
            mode={mode}
            onMode={setMode}
            reference={reference}
            onReference={setReference}
          />
        )}
        {money.unusedAdvance > 0 && (
          <RefundBox
            amount={money.unusedAdvance}
            refundMode={refundMode}
            onRefundMode={setRefundMode}
          />
        )}
        <p className="text-xs text-slate-500 mb-4">
          This closes the admission, stops running infusions and sends the bed for cleaning.
        </p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-sm"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={checkout}
            disabled={busy || missingNote || missingUtr}
            className="px-3 py-2 rounded-xl bg-success-600 hover:bg-success-700 text-white text-sm font-semibold disabled:opacity-50"
          >
            {submitLabel(busy, collecting, money.due)}
          </button>
        </div>
      </>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
      <dialog
        open
        aria-modal="true"
        aria-labelledby="checkout-title"
        className="relative m-0 border-0 text-slate-900 bg-white rounded-2xl shadow-xl w-full max-w-lg p-6 max-h-[92vh] overflow-y-auto"
      >
        <h3 id="checkout-title" className="text-lg font-semibold mb-1">
          {done ? 'Discharged' : 'Discharge checkout'}
        </h3>
        {subtitle && <p className="text-sm text-slate-600 mb-4">{subtitle}</p>}
        {body}
      </dialog>
    </div>
  );
}
