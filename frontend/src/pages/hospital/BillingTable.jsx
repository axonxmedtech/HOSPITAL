import { createColumnHelper } from '@tanstack/react-table';
import React, { useState } from 'react';
import ActionMenu from '../../components/ActionMenu';
import {
  CancelBillModal,
  CollectPaymentModal,
  DiscountModal,
  RefundModal,
} from '../../components/billing/BillActionModals';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import authService from '../../services/authService';

import { formatDay } from '../../utils/date';
const BillingTable = ({
  billing,
  startIndex = 0,
  pagination,
  onPrint,
  printingBillId,
  onEditItems,
  // Called after any payment / discount / refund / cancel so the screen reloads its bills.
  onChanged,
  // Called after a payment with (bill, result) — e.g. to offer "Print Receipt".
  onPaid,
}) => {
  const [expandedIds, setExpandedIds] = useState([]);
  // { type: 'pay' | 'discount' | 'refund' | 'cancel', bill }
  const [action, setAction] = useState(null);
  const isAdmin = authService.getCurrentUser()?.role === 'HOSPITAL_ADMIN';
  const closeAction = () => setAction(null);
  const done = (result) => {
    const finished = action;
    setAction(null);
    if (finished?.type === 'pay') onPaid?.(finished.bill, result);
    onChanged?.();
  };

  const toggleExpand = (id) => {
    setExpandedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };
  const columnHelper = createColumnHelper();

  const columns = [
    columnHelper.display({
      id: 'expand',
      header: '',
      cell: (info) => (
        <button
          onClick={() => toggleExpand(info.row.original.id)}
          className="p-1 rounded-md hover:bg-neutral-100"
          aria-label="Toggle items"
        >
          {expandedIds.includes(info.row.original.id) ? '▾' : '▸'}
        </button>
      ),
      enableSorting: false,
    }),
    columnHelper.display({
      id: 'sno',
      header: 'S.No.',
      cell: (info) => startIndex + info.row.index + 1,
    }),
    columnHelper.accessor((row) => row.customId || row.id, {
      id: 'id',
      header: 'Bill No',
      cell: (info) => <span title="Bill Number">{info.getValue()}</span>,
    }),
    columnHelper.accessor('patientName', {
      header: 'Patient Name',
      cell: (info) => info.getValue() || info.row.original.patientId || '-',
    }),
    columnHelper.accessor((row) => (row.billingType || 'OPD').toUpperCase(), {
      id: 'category',
      header: 'Category',
      cell: (info) => {
        const c = info.getValue();
        return (
          <span
            className={`px-2 py-0.5 text-xs font-semibold rounded-full border ${
              c === 'IPD'
                ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
                : 'bg-sky-50 text-sky-700 border-sky-200'
            }`}
          >
            {c}
          </span>
        );
      },
    }),
    columnHelper.accessor('amount', {
      header: 'Amount',
      cell: (info) => {
        const row = info.row.original;
        // amount = net payable after discount; grossAmount = charges before it (server-computed).
        const net = Number(info.getValue() || 0);
        const gross = Number(row.grossAmount ?? net);
        const discount = Number(row.discountAmount || 0);
        const paid = Number(row.paidAmount || 0);
        const bal = Number(row.balance ?? net - paid);
        const showBreakdown =
          discount > 0 ||
          (paid > 0 && row.paymentStatus !== 'PAID' && row.paymentStatus !== 'CLOSED');
        if (!showBreakdown) return `₹${net}`;
        return (
          <div className="text-xs leading-tight">
            {discount > 0 && (
              <>
                <div className="text-gray-400 line-through">₹{gross}</div>
                <div className="text-emerald-700">− ₹{discount} discount</div>
              </>
            )}
            <div className="text-gray-700 font-semibold">₹{net}</div>
            {paid > 0 && bal > 0 && <div className="text-emerald-600">Paid: ₹{paid}</div>}
            {bal > 0 && row.paymentStatus !== 'CANCELLED' && (
              <div className="font-bold text-red-700 mt-0.5 border-t border-dashed border-gray-200 pt-0.5">
                Bal: ₹{bal}
              </div>
            )}
          </div>
        );
      },
    }),
    columnHelper.accessor('paymentStatus', {
      header: 'Status',
      cell: (info) => {
        const status = info.getValue() || 'PENDING';
        let type = 'warning';
        if (status === 'PAID') type = 'success';
        else if (status === 'CLOSED') type = 'neutral';
        else if (status === 'CANCELLED') type = 'danger';
        return <StatusBadge status={status} type={type} />;
      },
    }),
    columnHelper.accessor('createdAt', {
      header: 'Date',
      cell: (info) => formatDay(info.getValue()),
    }),
    columnHelper.display({
      id: 'actions',
      header: () => <div className="text-right">Actions</div>,
      cell: (info) => {
        const bill = info.row.original;
        const st = bill.paymentStatus;
        const open = st === 'PENDING' || st === 'PARTIAL';
        const paid = Number(bill.paidAmount || 0);
        const menu = [
          {
            label: 'Edit charges',
            onClick: () => onEditItems(bill),
            hidden: !open || !onEditItems,
          },
          {
            label: 'Discount',
            onClick: () => setAction({ type: 'discount', bill }),
            hidden: !open,
          },
          {
            label: 'Refund',
            onClick: () => setAction({ type: 'refund', bill }),
            hidden: !isAdmin || paid <= 0 || st === 'CANCELLED',
          },
          {
            label: 'Cancel Bill',
            onClick: () => setAction({ type: 'cancel', bill }),
            hidden: !isAdmin || st === 'CANCELLED' || st === 'CLOSED' || bill.billingType === 'IPD',
            danger: true,
          },
        ];
        return (
          <div className="flex justify-end items-center gap-2">
            {open && (
              <button
                onClick={() => setAction({ type: 'pay', bill })}
                className="px-3 py-1 rounded-md text-sm font-semibold bg-emerald-600 text-white hover:bg-emerald-700 transition-colors"
              >
                Collect
              </button>
            )}
            {(paid > 0 || st === 'PAID' || st === 'CLOSED' || st === 'CANCELLED') && (
              <button
                onClick={() => onPrint(bill.id)}
                disabled={!!printingBillId}
                className={`px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center gap-1 ${printingBillId === bill.id ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : printingBillId ? 'opacity-50 cursor-not-allowed bg-gray-100 text-gray-700' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
              >
                <span>{printingBillId === bill.id ? 'Printing...' : 'Print'}</span>
              </button>
            )}
            {menu.some((m) => !m.hidden) && <ActionMenu actions={menu} />}
          </div>
        );
      },
    }),
  ];

  const renderExpandedRow = (row) => {
    const items = row.original.items || [];
    const medicines = row.original.medicines || [];
    if (
      !items.length &&
      !medicines.length &&
      !Number(row.original.discountAmount) &&
      row.original.paymentStatus !== 'CANCELLED'
    )
      return <div className="text-sm text-slate-600">No items or medicines billed</div>;
    return (
      <div className="space-y-4 p-2 bg-slate-50/50 rounded-lg border border-dashed border-gray-200">
        {items.length > 0 && (
          <div>
            <div className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">
              Charges &amp; Services
            </div>
            <table className="min-w-full">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left text-xs text-slate-500 pb-1">Description</th>
                  <th className="text-right text-xs text-slate-500 pb-1">Amount</th>
                </tr>
              </thead>
              <tbody>
                {items.map((it) => (
                  <tr key={it.id} className="border-b border-gray-100 last:border-0">
                    <td className="py-1.5 text-sm text-slate-700">{it.description}</td>
                    <td className="py-1.5 text-sm text-right text-slate-700">₹{it.amount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {medicines.length > 0 && (
          <div>
            <div className="text-xs font-bold uppercase tracking-wider text-teal-600 mb-1">
              Administered In-Clinic Medicines
            </div>
            <table className="min-w-full">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="text-left text-xs text-slate-500 pb-1">Medicine Name</th>
                  <th className="text-center text-xs text-slate-500 pb-1">Qty</th>
                  <th className="text-right text-xs text-slate-500 pb-1">Unit Price</th>
                  <th className="text-right text-xs text-slate-500 pb-1">Amount</th>
                </tr>
              </thead>
              <tbody>
                {medicines.map((med) => (
                  <tr key={med.id} className="border-b border-gray-100 last:border-0">
                    <td className="py-1.5 text-sm text-slate-700 font-medium">
                      {med.medicineName}
                    </td>
                    <td className="py-1.5 text-sm text-center text-slate-700">{med.quantity}</td>
                    <td className="py-1.5 text-sm text-right text-slate-700">₹{med.unitPrice}</td>
                    <td className="py-1.5 text-sm text-right text-slate-700 font-semibold text-teal-600">
                      ₹{med.amount}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {Number(row.original.discountAmount) > 0 && (
          <div className="text-xs text-emerald-700">
            Discount ₹{row.original.discountAmount}
            {row.original.discountReason ? ` — ${row.original.discountReason}` : ''}
            {row.original.discountedBy ? ` (by ${row.original.discountedBy})` : ''}
          </div>
        )}
        {Number(row.original.refundedAmount) > 0 && (
          <div className="text-xs text-red-700">Refunded ₹{row.original.refundedAmount}</div>
        )}
        {row.original.paymentStatus === 'CANCELLED' && (
          <div className="text-xs text-red-700">
            Cancelled{row.original.cancelReason ? `: ${row.original.cancelReason}` : ''}
            {row.original.cancelledBy ? ` (by ${row.original.cancelledBy})` : ''}
          </div>
        )}
        {row.original.markedPaidBy && (
          <div className="mt-3 pt-2 border-t border-dashed border-gray-200 text-xs text-gray-500">
            Marked Paid By:{' '}
            <span className="text-gray-700 font-semibold">{row.original.markedPaidBy}</span>
          </div>
        )}
      </div>
    );
  };

  return (
    <>
      <DataTable
        data={billing}
        columns={columns}
        pagination={pagination}
        expandedRowIds={expandedIds}
        renderExpandedRow={renderExpandedRow}
        idAccessor={'id'}
      />
      {action?.type === 'pay' && (
        <CollectPaymentModal bill={action.bill} onClose={closeAction} onDone={done} />
      )}
      {action?.type === 'discount' && (
        <DiscountModal bill={action.bill} onClose={closeAction} onDone={done} />
      )}
      {action?.type === 'refund' && (
        <RefundModal bill={action.bill} onClose={closeAction} onDone={done} />
      )}
      {action?.type === 'cancel' && (
        <CancelBillModal bill={action.bill} onClose={closeAction} onDone={done} />
      )}
    </>
  );
};

export default BillingTable;
