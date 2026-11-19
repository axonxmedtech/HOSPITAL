import React, { useState } from 'react';
import authService from '../../services/authService';
import { canManageBilling } from '../../utils/billingAccess';
import { CollectPaymentModal } from './BillActionModals';

/**
 * The "Bill" cell on an OPD row: amount + paid / part-paid / unpaid, and — for whoever handles
 * billing in this hospital (admin always; doctor and/or receptionist per the Billing setting) —
 * a Collect button that takes the payment right there, without going to the Billing tab.
 *
 * `opd.bill` comes from the OPD list endpoint. No bill yet means the hospital bills at the end
 * of the visit ("Pay Last") and the consultation hasn't finished.
 */
const rupees = (v) => `₹${Number(v || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

const OpdBillCell = ({ opd, onChanged, onPaid }) => {
  const [paying, setPaying] = useState(false);
  const bill = opd?.bill;
  const user = authService.getCurrentUser();

  if (!(user?.modules || []).includes('BILLING')) return <span className="text-gray-400">—</span>;
  if (!bill) {
    return <span className="text-xs text-gray-400">After consultation</span>;
  }

  const balance = Number(bill.balance || 0);
  const status = bill.status;
  const chip =
    status === 'CANCELLED'
      ? ['Cancelled', 'bg-gray-100 text-gray-500']
      : balance <= 0
        ? ['Paid', 'bg-emerald-50 text-emerald-700']
        : Number(bill.paidAmount) > 0
          ? [`Due ${rupees(balance)}`, 'bg-blue-50 text-blue-700']
          : ['Unpaid', 'bg-amber-50 text-amber-800'];
  const canCollect =
    canManageBilling(user) && balance > 0 && status !== 'CANCELLED' && status !== 'CLOSED';

  return (
    <div className="flex items-center gap-2 whitespace-nowrap">
      <div className="leading-tight">
        <div className="text-sm font-semibold text-gray-900">{rupees(bill.amount)}</div>
        <span
          className={`inline-block mt-0.5 text-[11px] font-semibold px-1.5 py-0.5 rounded ${chip[1]}`}
        >
          {chip[0]}
        </span>
      </div>
      {canCollect && (
        <button
          type="button"
          onClick={() => setPaying(true)}
          className="px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-600 text-white hover:bg-emerald-700"
        >
          Collect
        </button>
      )}
      {paying && (
        <CollectPaymentModal
          bill={{ ...bill, patientName: opd.patient?.name }}
          onClose={() => setPaying(false)}
          onDone={(result) => {
            setPaying(false);
            onPaid?.({ ...bill, patientName: opd.patient?.name }, result);
            onChanged?.();
          }}
        />
      )}
    </div>
  );
};

export default OpdBillCell;
