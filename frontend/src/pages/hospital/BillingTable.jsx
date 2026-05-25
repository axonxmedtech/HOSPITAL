import React, { useState } from 'react';
import { createColumnHelper } from '@tanstack/react-table';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';

const BillingTable = ({ billing, startIndex = 0, pagination, onUpdateStatus, onDownload, updatingBillId, downloadingBillId }) => {
    const [expandedIds, setExpandedIds] = useState([]);

    const toggleExpand = (id) => {
        setExpandedIds(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
    };
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'expand',
            header: '',
            cell: info => (
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
            cell: info => startIndex + info.row.index + 1,
        }),
        columnHelper.accessor(row => row.customId || row.id, {
            id: 'id',
            header: 'Bill No',
            cell: info => <span title="Bill Number">{info.getValue()}</span>,
        }),
        columnHelper.accessor('patientName', {
            header: 'Patient Name',
            cell: info => info.getValue() || info.row.original.patientId || '-',
        }),
        columnHelper.accessor('amount', {
            header: 'Amount',
            cell: info => {
                const total = info.getValue() || 0;
                const paid = info.row.original.paidAmount || 0;
                const bal = info.row.original.balance ?? (total - paid);
                if (paid > 0 && info.row.original.paymentStatus !== 'PAID') {
                    return (
                        <div className="text-xs leading-tight">
                            <div className="text-gray-500">Total: ₹{total}</div>
                            <div className="text-emerald-600">Paid: ₹{paid}</div>
                            <div className="font-bold text-red-700 mt-0.5 border-t border-dashed border-gray-200 pt-0.5">Bal: ₹{bal}</div>
                        </div>
                    );
                }
                return `₹${total}`;
            },
        }),
        columnHelper.accessor('paymentStatus', {
            header: 'Status',
            cell: info => (
                <StatusBadge
                    status={info.getValue()}
                    type={info.getValue() === 'PAID' ? 'success' : 'warning'}
                />
            ),
        }),
        columnHelper.accessor('createdAt', {
            header: 'Date',
            cell: info => new Date(info.getValue()).toLocaleDateString(),
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">Actions</div>,
            cell: info => (
                <div className="flex justify-end gap-2">
                    {(info.row.original.paymentStatus === 'PENDING' || info.row.original.paymentStatus === 'PARTIAL') && (
                        <button
                            onClick={() => onUpdateStatus(info.row.original.id, 'PAID', info.row.original)}
                            disabled={!!updatingBillId}
                            className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${updatingBillId === info.row.original.id ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : updatingBillId ? 'opacity-50 cursor-not-allowed bg-gray-100 text-gray-700' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
                        >
                            {updatingBillId === info.row.original.id ? 'Processing...' : 'Mark Paid'}
                        </button>
                    )}
                    {info.row.original.paymentStatus === 'PAID' && (
                        <button
                            onClick={() => onDownload(info.row.original.id)}
                            disabled={!!downloadingBillId}
                            className={`px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center gap-1 ${downloadingBillId === info.row.original.id ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : downloadingBillId ? 'opacity-50 cursor-not-allowed bg-gray-100 text-gray-700' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
                        >
                            <span>{downloadingBillId === info.row.original.id ? 'Downloading...' : 'Download'}</span>
                        </button>
                    )}
                </div>
            ),
        }),
    ];

    const renderExpandedRow = (row) => {
        const items = row.original.items || [];
        if (!items.length) return <div className="text-sm text-slate-600">No items</div>;
        return (
            <div>
                <table className="min-w-full">
                    <thead>
                        <tr>
                            <th className="text-left text-xs text-slate-500 px-2">Item</th>
                            <th className="text-right text-xs text-slate-500 px-2">Amount</th>
                        </tr>
                    </thead>
                    <tbody>
                        {items.map(it => (
                            <tr key={it.id} className="border-t">
                                <td className="px-2 py-1 text-sm text-slate-700">{it.description}</td>
                                <td className="px-2 py-1 text-sm text-right text-slate-700">₹{it.amount}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        );
    };

    return (
        <DataTable
            data={billing}
            columns={columns}
            pagination={pagination}
            expandedRowIds={expandedIds}
            renderExpandedRow={renderExpandedRow}
            idAccessor={'id'}
        />
    );
};

export default BillingTable;
