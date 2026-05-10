import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import hospitalService from '../../services/hospitalService';
import authService from '../../services/authService';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';

const IpdDetails = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [data, setData] = useState(null);
    const user = authService.getCurrentUser() || {};
    const isDoctor = authService.isDoctor();
    const isReceptionist = authService.isReceptionist();
    const { success, error: toastError } = useToast();

    const [followupModal, setFollowupModal] = useState({ isOpen: false, diagnosis: '', notes: '', saving: false });
    const [dischargeModal, setDischargeModal] = useState({ isOpen: false, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false });
    const [medicineModal, setMedicineModal] = useState({ isOpen: false, medicineName: '', type: 'TABLET', route: 'ORAL', dose: '', frequency: '', durationDays: 0, startDate: '', saving: false });
    const [billModal, setBillModal] = useState({ isOpen: false, loading: false, bill: null });
    const [payment, setPayment] = useState({ amount: '', mode: 'CASH', saving: false });

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const resp = await hospitalService.getIpdDetails(id);
                setData(resp);
            } catch (err) {
                console.error('Failed to load IPD details', err);
                setData(null);
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [id]);

    if (loading) return <div className="p-6">Loading...</div>;
    if (!data) return <EmptyState title="Not Found" message="IPD record not found" />;

    const onAddFollowUp = () => {
        setFollowupModal({ isOpen: true, diagnosis: '', notes: '', saving: false });
    };

    const closeFollowupModal = () => setFollowupModal({ isOpen: false, diagnosis: '', notes: '', saving: false });

    const saveFollowup = async () => {
        if (!followupModal.diagnosis) return toastError('Diagnosis is required');
        setFollowupModal(prev => ({ ...prev, saving: true }));
        try {
            await hospitalService.addIpdFollowup(id, { diagnosis: followupModal.diagnosis, notes: followupModal.notes });
            success('Follow-up saved');
            closeFollowupModal();
            // reload data
            setLoading(true);
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
        } catch (err) {
            console.error('Failed to save follow-up', err);
            toastError(err.response?.data || err.message || 'Failed to save follow-up');
        } finally {
            setFollowupModal(prev => ({ ...prev, saving: false }));
            setLoading(false);
        }
    };

    const onAddMedicine = () => {
        setMedicineModal({ isOpen: true, medicineName: '', type: 'TABLET', route: 'ORAL', dose: '', frequency: '', durationDays: 0, startDate: '', saving: false });
    };

    const onStopMedicine = async (prescriptionId) => {
        if (!window.confirm('Stop this medicine?')) return;
        try {
            await hospitalService.stopPrescription(prescriptionId);
            success('Medicine stopped');
            setLoading(true);
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
        } catch (err) {
            console.error('Failed to stop medicine', err);
            toastError(err.response?.data || err.message || 'Failed to stop medicine');
        } finally {
            setLoading(false);
        }
    };

    const onPlanDischarge = () => {
        setDischargeModal({ isOpen: true, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false });
    };

    const savePlanDischarge = async () => {
        if (!dischargeModal.finalDiagnosis) return toastError('Final diagnosis is required');
        setDischargeModal(prev => ({ ...prev, saving: true }));
        try {
            const payload = {
                finalDiagnosis: dischargeModal.finalDiagnosis,
                treatmentGiven: dischargeModal.treatmentGiven,
                dischargeNotes: dischargeModal.dischargeNotes,
                followUpDate: dischargeModal.followUpDate || null
            };
            await hospitalService.planDischarge(id, payload);
            success('Discharge planned');
            setDischargeModal({ isOpen: false, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false });
            setLoading(true);
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
        } catch (err) {
            console.error('Failed to plan discharge', err);
            toastError(err.response?.data || err.message || 'Failed to plan discharge');
        } finally {
            setDischargeModal(prev => ({ ...prev, saving: false }));
            setLoading(false);
        }
    };

    const onConfirmDischarge = async () => {
        if (!window.confirm('Confirm discharge? This will finalize and free the bed.')) return;
        try {
            setLoading(true);
            await hospitalService.confirmDischarge(id);
            success('Discharge completed');
            // navigate back to list
            navigate('/');
        } catch (err) {
            console.error('Failed to confirm discharge', err);
            const msg = err.response?.data || err.message || 'Failed to confirm discharge';
            toastError(msg);
        } finally {
            setLoading(false);
        }
    };

    const openBillModal = async () => {
        setBillModal({ isOpen: true, loading: true, bill: null });
        try {
            const b = await hospitalService.getIpdBill(id);
            setBillModal({ isOpen: true, loading: false, bill: b });
        } catch (err) {
            console.error('Failed to load bill', err);
            toastError('Failed to load bill');
            setBillModal({ isOpen: false, loading: false, bill: null });
        }
    };

    return (
        <div className="p-6">
            <div className="mb-3">
                <button
                    onClick={() => navigate(-1)}
                    className="text-sm text-gray-600 hover:text-gray-900 flex items-center gap-2"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                    </svg>
                    Back
                </button>
            </div>
            <PageHeader title={`IPD ${data.ipdNumber || ''}`} subtitle={`${data.patient?.name || ''} • ${data.patient?.age || ''} • ${data.patient?.gender || ''}`} />

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">
                <div className="col-span-2 bg-white border rounded p-4">
                    <h3 className="font-semibold mb-2">Admission Info</h3>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                        <div><strong>Admitted:</strong> {data.admission?.admissionDateTime ? new Date(data.admission.admissionDateTime).toLocaleString() : '-'}</div>
                        <div><strong>Type:</strong> {data.admission?.admissionType || '-'}</div>
                        <div><strong>Doctor:</strong> {data.admission?.doctor || '-'}</div>
                        <div><strong>Diagnosis:</strong> {data.admission?.primaryDiagnosis || '-'}</div>
                        <div><strong>Ward / Bed:</strong> {data.admission?.ward || '-'} / {data.admission?.bed || '-'}</div>
                        <div><strong>Status:</strong> {data.status || '-'}</div>
                    </div>

                    <hr className="my-4" />

                    <h3 className="font-semibold mb-2">Daily Follow-ups</h3>
                    {data.medicalRecords && data.medicalRecords.length > 0 ? (
                        <ul className="space-y-2">
                            {data.medicalRecords.map((m, i) => (
                                <li key={i} className="p-2 border rounded">
                                    <div className="text-xs text-gray-500">{m.date} • {m.doctor}</div>
                                    <div className="text-sm font-medium">{m.diagnosis}</div>
                                    <div className="text-sm text-gray-700">{m.notes}</div>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <div className="text-sm text-gray-500">No follow-ups recorded.</div>
                    )}

                    <div className="mt-3">
                        {isDoctor && data.status !== 'DISCHARGE_PLANNED' && data.status !== 'DISCHARGED' && (
                            <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={onAddFollowUp}>+ Add Follow-up</button>
                        )}
                    </div>

                    {followupModal.isOpen && (
                        <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                            <div className="bg-white rounded-lg w-full max-w-lg p-6">
                                <h3 className="text-lg font-semibold mb-3">Add Follow-up</h3>
                                <div className="mb-3">
                                    <label className="block text-sm font-medium mb-1">Diagnosis</label>
                                    <textarea value={followupModal.diagnosis} onChange={e => setFollowupModal(prev => ({ ...prev, diagnosis: e.target.value }))} className="w-full border p-2 rounded" rows={3} />
                                </div>
                                <div className="mb-3">
                                    <label className="block text-sm font-medium mb-1">Notes</label>
                                    <textarea value={followupModal.notes} onChange={e => setFollowupModal(prev => ({ ...prev, notes: e.target.value }))} className="w-full border p-2 rounded" rows={4} />
                                </div>
                                <div className="flex justify-end gap-3">
                                    <button onClick={closeFollowupModal} className="px-3 py-1 bg-gray-100 rounded">Cancel</button>
                                    <button onClick={saveFollowup} disabled={followupModal.saving} className="px-3 py-1 bg-green-600 text-white rounded">{followupModal.saving ? 'Saving...' : 'Save'}</button>
                                </div>
                            </div>
                        </div>
                    )}

                        {medicineModal.isOpen && (
                            <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                                <div className="bg-white rounded-lg w-full max-w-lg p-6">
                                    <h3 className="text-lg font-semibold mb-3">Add Medicine</h3>
                                    <div className="grid grid-cols-2 gap-3">
                                        <div>
                                            <label className="block text-sm font-medium mb-1">Medicine Name</label>
                                            <input value={medicineModal.medicineName} onChange={e => setMedicineModal(prev => ({ ...prev, medicineName: e.target.value }))} className="w-full border p-2 rounded" />
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium mb-1">Type</label>
                                            <select value={medicineModal.type} onChange={e => setMedicineModal(prev => ({ ...prev, type: e.target.value }))} className="w-full border p-2 rounded">
                                                <option>TABLET</option>
                                                <option>SYRUP</option>
                                                <option>INJECTION</option>
                                            </select>
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium mb-1">Route</label>
                                            <select value={medicineModal.route} onChange={e => setMedicineModal(prev => ({ ...prev, route: e.target.value }))} className="w-full border p-2 rounded">
                                                <option>ORAL</option>
                                                <option>IV</option>
                                                <option>IM</option>
                                            </select>
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium mb-1">Dose</label>
                                            <input value={medicineModal.dose} onChange={e => setMedicineModal(prev => ({ ...prev, dose: e.target.value }))} className="w-full border p-2 rounded" />
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium mb-1">Frequency</label>
                                            <input value={medicineModal.frequency} onChange={e => setMedicineModal(prev => ({ ...prev, frequency: e.target.value }))} className="w-full border p-2 rounded" />
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium mb-1">Duration (days)</label>
                                            <input type="number" value={medicineModal.durationDays} onChange={e => setMedicineModal(prev => ({ ...prev, durationDays: parseInt(e.target.value || '0') }))} className="w-full border p-2 rounded" />
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium mb-1">Start Date</label>
                                            <input type="date" value={medicineModal.startDate} onChange={e => setMedicineModal(prev => ({ ...prev, startDate: e.target.value }))} className="w-full border p-2 rounded" />
                                        </div>
                                    </div>
                                    <div className="flex justify-end gap-3 mt-4">
                                        <button onClick={() => setMedicineModal(prev => ({ ...prev, isOpen: false }))} className="px-3 py-1 bg-gray-100 rounded">Cancel</button>
                                        <button onClick={async () => {
                                            if (!medicineModal.medicineName) return toastError('Medicine name required');
                                            setMedicineModal(prev => ({ ...prev, saving: true }));
                                            try {
                                                const payload = {
                                                    medicineId: null,
                                                    medicineName: medicineModal.medicineName,
                                                    type: medicineModal.type,
                                                    route: medicineModal.route,
                                                    dose: medicineModal.dose,
                                                    frequency: medicineModal.frequency,
                                                    durationDays: medicineModal.durationDays,
                                                    startDate: medicineModal.startDate || null
                                                };
                                                await hospitalService.addIpdPrescription(id, payload);
                                                success('Medicine added');
                                                setMedicineModal(prev => ({ ...prev, isOpen: false }));
                                                setLoading(true);
                                                const resp = await hospitalService.getIpdDetails(id);
                                                setData(resp);
                                            } catch (err) {
                                                console.error('Failed to add medicine', err);
                                                toastError(err.response?.data || err.message || 'Failed to add medicine');
                                            } finally {
                                                setMedicineModal(prev => ({ ...prev, saving: false }));
                                                setLoading(false);
                                            }
                                        }} disabled={medicineModal.saving} className="px-3 py-1 bg-blue-600 text-white rounded">{medicineModal.saving ? 'Saving...' : 'Save'}</button>
                                    </div>
                                </div>
                            </div>
                        )}

                    <hr className="my-4" />

                    <h3 className="font-semibold mb-2">Current Medicines</h3>
                    {data.activePrescriptions && data.activePrescriptions.length > 0 ? (
                        <ul className="space-y-2">
                            {data.activePrescriptions.map((p, i) => (
                                <li key={i} className="p-2 border rounded flex justify-between items-center">
                                    <div>
                                        <div className="font-medium">{p.name}</div>
                                        <div className="text-xs text-gray-600">{p.type} • {p.route} • {p.frequency}</div>
                                    </div>
                                    <div>
                                        {isDoctor ? (
                                            <div className="flex gap-2">
                                                <button className="px-2 py-1 bg-yellow-500 text-white rounded" onClick={() => onStopMedicine(p.id)}>Stop</button>
                                            </div>
                                        ) : (
                                            <div className="text-xs text-gray-500">{p.status}</div>
                                        )}
                                    </div>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <div className="text-sm text-gray-500">No active medicines.</div>
                    )}

                    {isDoctor && data.status !== 'DISCHARGE_PLANNED' && data.status !== 'DISCHARGED' && (
                        <div className="mt-3">
                            <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={onAddMedicine}>+ Add Medicine</button>
                        </div>
                    )}
                </div>

                <aside className="bg-white border rounded p-4">
                    <h3 className="font-semibold mb-2">Billing</h3>
                    {isReceptionist ? (
                        <div>
                            {data.billing ? (
                                <div className="text-sm">
                                    <div><strong>Total:</strong> ₹{data.billing.totalAmount}</div>
                                    <div><strong>Paid:</strong> ₹{data.billing.paidAmount}</div>
                                    <div><strong>Balance:</strong> ₹{data.billing.balance}</div>
                                </div>
                            ) : (
                                <div className="text-sm text-gray-500">No billing records found.</div>
                            )}
                            <div className="mt-3 flex gap-2">
                                <button className="px-3 py-1 bg-indigo-600 text-white rounded" onClick={openBillModal}>View Bill</button>
                                <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={openBillModal}>Take Payment</button>
                                <button className="px-3 py-1 bg-gray-600 text-white rounded" onClick={() => window.print()}>Print Bill</button>
                            </div>
                        </div>
                    ) : (
                        <div className="text-sm text-gray-500">Billing is not visible to your role.</div>
                    )}

                    <hr className="my-4" />

                    <h3 className="font-semibold mb-2">Discharge</h3>
                    {isDoctor && data.status === 'ADMITTED' && (
                        <button className="px-3 py-1 bg-yellow-600 text-white rounded" onClick={onPlanDischarge}>📝 Plan Discharge</button>
                    )}
                    {isReceptionist && data.status === 'DISCHARGE_PLANNED' && (
                        <div>
                            <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={onConfirmDischarge}>✅ Confirm Discharge</button>
                        </div>
                    )}
                </aside>
            </div>

            {/* Discharge Modal */}
            {dischargeModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg w-full max-w-lg p-6">
                        <h3 className="text-lg font-semibold mb-3">Plan Discharge</h3>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Final Diagnosis</label>
                            <textarea value={dischargeModal.finalDiagnosis} onChange={e => setDischargeModal(prev => ({ ...prev, finalDiagnosis: e.target.value }))} className="w-full border p-2 rounded" rows={2} />
                        </div>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Treatment Given</label>
                            <textarea value={dischargeModal.treatmentGiven} onChange={e => setDischargeModal(prev => ({ ...prev, treatmentGiven: e.target.value }))} className="w-full border p-2 rounded" rows={3} />
                        </div>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Discharge Notes</label>
                            <textarea value={dischargeModal.dischargeNotes} onChange={e => setDischargeModal(prev => ({ ...prev, dischargeNotes: e.target.value }))} className="w-full border p-2 rounded" rows={3} />
                        </div>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Follow-up Date</label>
                            <input type="date" value={dischargeModal.followUpDate} onChange={e => setDischargeModal(prev => ({ ...prev, followUpDate: e.target.value }))} className="w-full border p-2 rounded" />
                        </div>
                        <div className="flex justify-end gap-3">
                            <button onClick={() => setDischargeModal({ isOpen: false, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false })} className="px-3 py-1 bg-gray-100 rounded">Cancel</button>
                            <button onClick={savePlanDischarge} disabled={dischargeModal.saving} className="px-3 py-1 bg-yellow-600 text-white rounded">{dischargeModal.saving ? 'Saving...' : 'Plan Discharge'}</button>
                        </div>
                    </div>
                </div>
            )}

            {billModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg w-full max-w-2xl p-6">
                        <h3 className="text-lg font-semibold mb-3">IPD Bill</h3>
                        {billModal.loading ? (
                            <div>Loading...</div>
                        ) : billModal.bill ? (
                            <div>
                                <div className="mb-3 text-sm">
                                    <div><strong>Total:</strong> ₹{billModal.bill.totalAmount}</div>
                                    <div><strong>Paid:</strong> ₹{billModal.bill.paidAmount}</div>
                                    <div><strong>Balance:</strong> ₹{billModal.bill.balance}</div>
                                </div>
                                <div className="mb-3">
                                    <h4 className="font-medium">Items</h4>
                                    <ul className="mt-2 space-y-2">
                                        {billModal.bill.items.map((it, i) => (
                                            <li key={i} className="flex justify-between border p-2 rounded">
                                                <div>{it.description}</div>
                                                <div>₹{it.amount}</div>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                                <div className="mt-4">
                                    <h4 className="font-medium mb-2">Take Payment</h4>
                                    <div className="grid grid-cols-2 gap-3">
                                        <input value={payment.amount} onChange={e => setPayment(prev => ({ ...prev, amount: e.target.value }))} placeholder="Amount" className="border p-2 rounded" />
                                        <select value={payment.mode} onChange={e => setPayment(prev => ({ ...prev, mode: e.target.value }))} className="border p-2 rounded">
                                            <option value="CASH">CASH</option>
                                            <option value="CARD">CARD</option>
                                            <option value="UPI">UPI</option>
                                        </select>
                                    </div>
                                    <div className="flex justify-end gap-3 mt-4">
                                        <button onClick={() => setBillModal({ isOpen: false, loading: false, bill: null })} className="px-3 py-1 bg-gray-100 rounded">Close</button>
                                        <button onClick={async () => {
                                            if (!payment.amount) return toastError('Enter amount');
                                            setPayment(prev => ({ ...prev, saving: true }));
                                            try {
                                                const payload = { amount: Number(payment.amount), mode: payment.mode };
                                                const resp = await hospitalService.payBilling(billModal.bill.billingId, payload);
                                                success('Payment recorded');
                                                setBillModal({ isOpen: false, loading: false, bill: null });
                                                // reload details
                                                setLoading(true);
                                                const resp2 = await hospitalService.getIpdDetails(id);
                                                setData(resp2);
                                            } catch (err) {
                                                console.error(err);
                                                toastError('Failed to record payment');
                                            } finally {
                                                setPayment(prev => ({ ...prev, saving: false }));
                                                setLoading(false);
                                            }
                                        }} className="px-3 py-1 bg-green-600 text-white rounded">Pay</button>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <div>
                                <div className="text-sm text-gray-500 mb-4">No bill available.</div>
                                <div className="flex justify-end">
                                    <button onClick={() => setBillModal({ isOpen: false, loading: false, bill: null })} className="px-3 py-1 bg-gray-100 rounded">Close</button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default IpdDetails;
