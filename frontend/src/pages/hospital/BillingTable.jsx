import React from 'react';
import { createColumnHelper } from '@tanstack/react-table';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';

const BillingTable = ({ billing, startIndex = 0, pagination, onUpdateStatus, onDownload }) => {
    const columnHelper = createColumnHelper();

    const columns = [
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
        columnHelper.accessor('patientId', {
            header: 'Patient ID',
        }),
        columnHelper.accessor('amount', {
            header: 'Amount',
            cell: info => `₹${info.getValue()}`,
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
                    {info.row.original.paymentStatus === 'PENDING' && (
                        <button
                            onClick={() => onUpdateStatus(info.row.original.id, 'PAID')}
                            className="bg-gray-100 text-gray-700 hover:bg-gray-200 px-3 py-1 rounded-md text-sm font-medium transition-colors"
                        >
                            Mark Paid
                        </button>
                    )}
                    {info.row.original.paymentStatus === 'PAID' && (
                        <button
                            onClick={() => onDownload(info.row.original.id)}
                            className="bg-gray-100 text-gray-700 hover:bg-gray-200 px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center gap-1"
                        >
                            <span>Download</span>
                        </button>
                    )}
                </div>
            ),
        }),
    ];

    return <DataTable data={billing} columns={columns} pagination={pagination} />;
};

export default BillingTable;
