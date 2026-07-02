import React, { useState } from 'react';
import { createColumnHelper } from '@tanstack/react-table';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';

const BillingTable = ({ billing, startIndex = 0, pagination, onUpdateStatus, onPrint, updatingBillId, printingBillId, onEditItems }) => {
    const { success, error } = useToast();
    const [expandedIds, setExpandedIds] = useState([]);
    
    // Pre-auth modal states
    const [isPreauthModalOpen, setIsPreauthModalOpen] = useState(false);
    const [selectedBill, setSelectedBill] = useState(null);
    const [payer, setPayer] = useState('');
    const [claimAmount, setClaimAmount] = useState('');
    const [approvedAmount, setApprovedAmount] = useState('');
    const [claimStatus, setClaimStatus] = useState('PENDING_AUTH');
    const [isSavingClaim, setIsSavingClaim] = useState(false);

    const toggleExpand = (id) => {
        setExpandedIds(prev =>
            prev.includes(id) ? prev.filter(item => item !== id) : [...prev, id]
        );
    };

    const handleOpenPreauth = (bill) => {
        setSelectedBill(bill);
        const claim = bill.insuranceClaim || {};
        setPayer(claim.payer || '');
        setClaimAmount(claim.claimAmount || bill.balance || bill.amount || '');
        setApprovedAmount(claim.approvedAmount || '');
        setClaimStatus(claim.status || 'PENDING_AUTH');
        setIsPreauthModalOpen(true);
    };

    const handleSavePreauth = async (e) => {
        e.preventDefault();
        if (!payer || !claimAmount) {
            error('Payer name and claim amount are required');
            return;
        }
        setIsSavingClaim(true);
        try {
            await hospitalService.postInsurancePreauth({
                billingId: selectedBill.id,
                payer: payer.trim(),
                claimAmount: parseFloat(claimAmount),
                approvedAmount: approvedAmount ? parseFloat(approvedAmount) : null,
                status: claimStatus
            });
            success('Cashless pre-auth claim details updated');
            setIsPreauthModalOpen(false);
            if (onUpdateStatus) {
                // Refresh by triggering a dummy status update on the same bill
                onUpdateStatus(selectedBill.id, selectedBill.paymentStatus, selectedBill);
            }
        } catch (err) {
            error(err.message || 'Failed to update pre-auth claim details');
        } finally {
            setIsSavingClaim(false);
        }
    };

    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'expander',
            header: () => null,
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
            cell: info => <span className="font-semibold text-gray-900" title="Bill Number">{info.getValue()}</span>,
        }),
        columnHelper.accessor('patientName', {
            header: 'Patient Name',
            cell: info => {
                const name = info.getValue() || info.row.original.patientId || '-';
                const claim = info.row.original.insuranceClaim;
                const advance = info.row.original.advanceApplied;
                return (
                    <div className="space-y-1">
                        <div className="font-semibold text-gray-950">{name}</div>
                        <div className="flex flex-wrap gap-1">
                            {claim && (
                                <span className="text-[10px] text-indigo-700 bg-indigo-50 border border-indigo-100 rounded px-1.5 py-0.5 font-bold flex items-center gap-1">
                                    <svg className="w-3 h-3 text-indigo-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                                    </svg>
                                    Cashless: {claim.payer}
                                </span>
                            )}
                            {advance && parseFloat(advance) > 0 && (
                                <span className="text-[10px] text-emerald-700 bg-emerald-50 border border-emerald-100 rounded px-1.5 py-0.5 font-bold">
                                    Advance Applied: ₹{advance}
                                </span>
                            )}
                        </div>
                    </div>
                );
            }
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
            cell: info => {
                const status = info.getValue() || 'PENDING';
                let type = 'warning';
                if (status === 'PAID') type = 'success';
                else if (status === 'CLOSED') type = 'neutral';
                
                const claim = info.row.original.insuranceClaim;
                return (
                    <div className="space-y-1">
                        <StatusBadge
                            status={status}
                            type={type}
                        />
                        {claim && (
                            <div className="text-[9px] font-extrabold uppercase tracking-wider text-indigo-700 bg-indigo-50/80 px-1 py-0.5 rounded text-center border border-indigo-100">
                                Claim: {claim.status}
                            </div>
                        )}
                    </div>
                );
            },
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
                        <>
                            <button
                                onClick={() => handleOpenPreauth(info.row.original)}
                                className="px-3 py-1 rounded-md text-sm font-medium bg-indigo-50 text-indigo-700 hover:bg-indigo-100 transition-colors border border-indigo-100"
                            >
                                Insurance Pre-Auth
                            </button>
                            {onEditItems && (
                                <button
                                    onClick={() => onEditItems(info.row.original)}
                                    className="px-3 py-1 rounded-md text-sm font-medium bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                                >
                                    Edit Charges
                                </button>
                            )}
                            <button
                                onClick={() => onUpdateStatus(info.row.original.id, 'PAID', info.row.original)}
                                disabled={!!updatingBillId}
                                className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${updatingBillId === info.row.original.id ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : updatingBillId ? 'opacity-50 cursor-not-allowed bg-gray-100 text-gray-700' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
                            >
                                {updatingBillId === info.row.original.id ? 'Processing...' : 'Mark Paid'}
                            </button>
                        </>
                    )}
                    {info.row.original.paymentStatus === 'PAID' && (
                        <button
                            onClick={() => onPrint(info.row.original.id)}
                            disabled={!!printingBillId}
                            className={`px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center gap-1 ${printingBillId === info.row.original.id ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : printingBillId ? 'opacity-50 cursor-not-allowed bg-gray-100 text-gray-700' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
                        >
                            <span>{printingBillId === info.row.original.id ? 'Printing...' : 'Print'}</span>
                        </button>
                    )}
                </div>
            ),
        }),
    ];

    const renderExpandedRow = (row) => {
        const items = row.original.items || [];
        const medicines = row.original.medicines || [];
        const claim = row.original.insuranceClaim;
        
        return (
            <div className="space-y-4 p-4 bg-slate-50/50 rounded-lg border border-dashed border-gray-200">
                {claim && (
                    <div className="bg-indigo-50/50 border border-indigo-100 rounded-lg p-3 text-sm">
                        <div className="font-bold text-indigo-900 flex items-center gap-1.5 mb-1.5">
                            <svg className="w-4 h-4 text-indigo-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                            </svg>
                            <span>Cashless Insurance Claim Details</span>
                        </div>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs text-indigo-950">
                            <div><span className="text-gray-500">Payer Name:</span> <span className="font-semibold">{claim.payer}</span></div>
                            <div><span className="text-gray-500">Claimed Amount:</span> <span className="font-semibold">₹{claim.claimAmount}</span></div>
                            <div><span className="text-gray-500">Approved Amount:</span> <span className="font-semibold">{claim.approvedAmount != null ? `₹${claim.approvedAmount}` : 'Pending review'}</span></div>
                            <div>
                                <span className="text-gray-500">Status:</span>{' '}
                                <span className="px-2 py-0.5 rounded font-bold uppercase text-[10px] bg-indigo-100 text-indigo-800">
                                    {claim.status}
                                </span>
                            </div>
                        </div>
                    </div>
                )}

                {!items.length && !medicines.length ? (
                    <div className="text-sm text-slate-600">No items or medicines billed</div>
                ) : (
                    <>
                        {items.length > 0 && (
                            <div>
                                <div className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Clinic Charges & Services</div>
                                <table className="min-w-full">
                                    <thead>
                                        <tr className="border-b border-gray-200">
                                            <th className="text-left text-xs text-slate-500 pb-1">Description</th>
                                            <th className="text-right text-xs text-slate-500 pb-1">Amount</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {items.map(it => (
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
                                <div className="text-xs font-bold uppercase tracking-wider text-teal-600 mb-1">Administered In-Clinic Medicines</div>
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
                                        {medicines.map(med => (
                                            <tr key={med.id} className="border-b border-gray-100 last:border-0">
                                                <td className="py-1.5 text-sm text-slate-700 font-medium">{med.medicineName}</td>
                                                <td className="py-1.5 text-sm text-center text-slate-700">{med.quantity}</td>
                                                <td className="py-1.5 text-sm text-right text-slate-700">₹{med.unitPrice}</td>
                                                <td className="py-1.5 text-sm text-right text-slate-700 font-semibold text-teal-600">₹{med.amount}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </>
                )}

                {row.original.markedPaidBy && (
                    <div className="mt-3 pt-2 border-t border-dashed border-gray-200 text-xs text-gray-500">
                        Marked Paid By: <span className="text-gray-700 font-semibold">{row.original.markedPaidBy}</span>
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

            {/* Cashless Preauth Modal */}
            {isPreauthModalOpen && selectedBill && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
                    <div className="bg-white rounded-xl max-w-lg w-full p-6 shadow-xl border border-gray-100 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-200">
                        <div className="flex justify-between items-center border-b border-gray-100 pb-3">
                            <div>
                                <h3 className="text-lg font-bold text-gray-900">Insurance Cashless Pre-Auth</h3>
                                <p className="text-xs text-gray-500 mt-0.5">Bill ID: {selectedBill.customId || selectedBill.id}</p>
                            </div>
                            <button onClick={() => setIsPreauthModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </button>
                        </div>

                        <form onSubmit={handleSavePreauth} className="space-y-4">
                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Insurance Payer Name</label>
                                <input
                                    type="text"
                                    value={payer}
                                    onChange={(e) => setPayer(e.target.value)}
                                    placeholder="e.g. Star Health Insurance"
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Requested Claim Amount (₹)</label>
                                <input
                                    type="number"
                                    value={claimAmount}
                                    onChange={(e) => setClaimAmount(e.target.value)}
                                    placeholder="0.00"
                                    min="0"
                                    step="0.01"
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Approved Claim Amount (₹ - Optional)</label>
                                <input
                                    type="number"
                                    value={approvedAmount}
                                    onChange={(e) => setApprovedAmount(e.target.value)}
                                    placeholder="Pending authorization"
                                    min="0"
                                    step="0.01"
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Pre-authorization Status</label>
                                <select
                                    value={claimStatus}
                                    onChange={(e) => setClaimStatus(e.target.value)}
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                >
                                    <option value="PENDING_AUTH">Pending Authorization</option>
                                    <option value="APPROVED">Approved</option>
                                    <option value="DENIED">Denied</option>
                                    <option value="SUBMITTED">Submitted</option>
                                    <option value="SETTLED">Settled</option>
                                </select>
                            </div>

                            <div className="flex justify-end gap-2 border-t border-gray-100 pt-4 mt-6">
                                <button
                                    type="button"
                                    onClick={() => setIsPreauthModalOpen(false)}
                                    className="px-4 py-2 border border-gray-300 rounded-lg text-sm font-semibold hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    disabled={isSavingClaim}
                                    className="px-4 py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-800 disabled:bg-gray-300"
                                >
                                    {isSavingClaim ? 'Saving...' : 'Save Claim Details'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </>
    );
};

export default BillingTable;
