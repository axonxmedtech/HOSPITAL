import React, { useState, useEffect } from 'react';
import hospitalService from '../services/hospitalService';

/**
 * PatientDetailsModal - Read-only view of patient information with tabs for additional data
 * 
 * Features:
 * - Basic patient information (read-only)
 * - Tabbed interface for: Info, Case Papers (OPD History + Prescriptions), Bills
 */
const PatientDetailsModal = ({ patient, onClose }) => {
    const [activeTab, setActiveTab] = useState('info');

    const [casePapers, setCasePapers] = useState([]);
    const [loadingCasePapers, setLoadingCasePapers] = useState(false);

    const [bills, setBills] = useState([]);
    const [loadingBills, setLoadingBills] = useState(false);
    const [printingBillId, setPrintingBillId] = useState(null);

    useEffect(() => {
        const fetchCasePapers = async () => {
            setLoadingCasePapers(true);
            try {
                const pid = patient.publicId || patient.id;
                const res = await hospitalService.getPatientConsultationDetails(pid);
                setCasePapers(res.medicalHistory || []);
            } catch (err) {
                console.error("Failed to fetch case papers:", err);
            } finally {
                setLoadingCasePapers(false);
            }
        };

        const fetchBills = async () => {
            setLoadingBills(true);
            try {
                const pid = patient.publicId || patient.id;
                const res = await hospitalService.getPatientBills(pid);
                setBills(res || []);
            } catch (err) {
                console.error("Failed to fetch patient bills:", err);
            } finally {
                setLoadingBills(false);
            }
        };

        if (patient) {
            if (activeTab === 'casepapers') {
                fetchCasePapers();
            } else if (activeTab === 'bills') {
                fetchBills();
            }
        }
    }, [activeTab, patient?.id, patient?.publicId]);

    if (!patient) return null;

    const tabs = [
        { id: 'info', label: 'Patient Info' },
        { id: 'casepapers', label: 'Case Papers' },
        { id: 'bills', label: 'Bills' },
    ];

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-lg shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col">
                {/* Header */}
                <div className="flex justify-between items-center p-6 border-b border-gray-200">
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900">Patient Details</h2>
                        <p className="text-sm text-gray-600 mt-1">
                            Patient ID: {patient.publicId || patient.id}
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                {/* Tabs */}
                <div className="border-b border-gray-200 px-6">
                    <nav className="flex space-x-4">
                        {tabs.map((tab) => (
                            <button
                                key={tab.id}
                                onClick={() => setActiveTab(tab.id)}
                                className={`py-3 px-4 text-sm font-medium border-b-2 transition-colors ${
                                    activeTab === tab.id
                                        ? 'border-gray-900 text-gray-900 font-semibold'
                                        : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                                }`}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </nav>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-6">
                    {activeTab === 'info' && (
                        <div className="space-y-6">
                            {/* Basic Information */}
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900 mb-4">Basic Information</h3>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <InfoField label="Full Name" value={patient.name} />
                                    <InfoField label="Patient ID" value={patient.publicId || patient.id} />
                                    <InfoField label="Age" value={patient.age} />
                                    <InfoField label="Gender" value={patient.gender} />
                                    <InfoField label="Phone Number" value={patient.phone} />
                                    <InfoField label="Email" value={patient.email || 'N/A'} />
                                    <InfoField label="Blood Group" value={patient.bloodGroup || 'N/A'} />
                                    <InfoField label="Registration Date" value={patient.createdAt ? new Date(patient.createdAt).toLocaleDateString() : 'N/A'} />
                                </div>
                            </div>

                            {/* Address */}
                            {patient.address && (
                                <div>
                                    <h3 className="text-lg font-semibold text-gray-900 mb-4">Address</h3>
                                    <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                                        <p className="text-gray-700">{patient.address}</p>
                                    </div>
                                </div>
                            )}

                            {/* Medical Information */}
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900 mb-4">Medical Information</h3>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <InfoField label="Allergies" value={patient.allergies || 'None reported'} />
                                    <InfoField label="Chronic Conditions" value={patient.chronicConditions || 'None reported'} />
                                    <InfoField label="Current Medications" value={patient.currentMedications || 'None'} />
                                    <InfoField label="Emergency Contact" value={patient.emergencyContact || 'N/A'} />
                                </div>
                            </div>
                        </div>
                    )}

                    {activeTab === 'casepapers' && (
                        <div className="space-y-4">
                            {loadingCasePapers ? (
                                <div className="text-center py-12 text-gray-500">
                                    <div className="animate-spin h-6 w-6 border-b-2 border-gray-900 mx-auto mb-2 rounded-full"></div>
                                    Loading clinical history...
                                </div>
                            ) : casePapers.length > 0 ? (
                                casePapers.map((record) => (
                                    <div key={record.id} className="bg-white rounded-lg p-5 border border-gray-200 shadow-sm space-y-3">
                                        <div className="flex justify-between items-start border-b border-gray-100 pb-2">
                                            <div>
                                                <span className="text-sm font-semibold text-gray-800">{record.doctorName || 'Doctor'}</span>
                                                <span className="mx-2 text-gray-300">•</span>
                                                <span className="text-xs text-gray-500">
                                                    {record.date ? new Date(record.date).toLocaleDateString('en-US', {
                                                        year: 'numeric',
                                                        month: 'short',
                                                        day: 'numeric'
                                                    }) : 'N/A'}
                                                </span>
                                            </div>
                                            {record.followUpDate && (
                                                <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full font-medium">
                                                    Follow-up: {new Date(record.followUpDate).toLocaleDateString()}
                                                </span>
                                            )}
                                        </div>
                                        
                                        {record.symptoms && (
                                            <div>
                                                <label className="text-xs text-gray-400 block font-medium">Symptoms</label>
                                                <p className="text-sm text-gray-700">{record.symptoms}</p>
                                            </div>
                                        )}
                                        
                                        {record.diagnosis && (
                                            <div>
                                                <label className="text-xs text-gray-400 block font-medium">Diagnosis</label>
                                                <p className="text-sm font-semibold text-gray-800">{record.diagnosis}</p>
                                            </div>
                                        )}
                                        
                                        {record.treatment && (
                                            <div>
                                                <label className="text-xs text-gray-400 block font-medium">Treatment & Notes</label>
                                                <p className="text-sm text-gray-700">{record.treatment}</p>
                                            </div>
                                        )}
                                        
                                        {record.prescriptions && record.prescriptions.length > 0 && (
                                            <div className="pt-2 border-t border-gray-100">
                                                <label className="text-xs text-teal-600 block font-semibold mb-1.5">Prescribed Medicines</label>
                                                <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                                    {record.prescriptions.map((med) => (
                                                        <div key={med.id} className="bg-slate-50 p-2.5 rounded-lg border border-gray-200 text-xs">
                                                            <span className="font-bold text-gray-800 block">{med.medicineName}</span>
                                                            <span className="text-gray-500 block mt-0.5">
                                                                {med.dosage} • {med.frequency} • {med.duration}
                                                            </span>
                                                            {med.instructions && (
                                                                <span className="text-gray-400 block mt-0.5 italic">{med.instructions}</span>
                                                            )}
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                ))
                            ) : (
                                <div className="text-center py-12 text-gray-400 text-sm">
                                    <p>No previous case papers found.</p>
                                </div>
                            )}
                        </div>
                    )}

                    {activeTab === 'bills' && (
                        <div className="space-y-4">
                            {loadingBills ? (
                                <div className="text-center py-12 text-gray-500">
                                    <div className="animate-spin h-6 w-6 border-b-2 border-gray-900 mx-auto mb-2 rounded-full"></div>
                                    Loading bills...
                                </div>
                            ) : bills.length > 0 ? (
                                <div className="overflow-x-auto border border-gray-200 rounded-lg">
                                    <table className="min-w-full text-sm text-left">
                                        <thead className="bg-gray-50 text-gray-600 font-medium border-b border-gray-200">
                                            <tr>
                                                <th className="px-4 py-3">S.No.</th>
                                                <th className="px-4 py-3">Bill No</th>
                                                <th className="px-4 py-3">Date</th>
                                                <th className="px-4 py-3">Status</th>
                                                <th className="px-4 py-3 text-right">Total</th>
                                                <th className="px-4 py-3 text-right">Paid</th>
                                                <th className="px-4 py-3 text-right">Balance</th>
                                                <th className="px-4 py-3 text-center">Action</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-gray-100 bg-white">
                                            {bills.map((bill, index) => {
                                                const total = bill.amount || 0;
                                                const paid = bill.paidAmount || 0;
                                                const bal = bill.balance ?? (total - paid);
                                                return (
                                                    <tr key={bill.id} className="hover:bg-gray-50">
                                                        <td className="px-4 py-3 text-gray-500">{index + 1}</td>
                                                        <td className="px-4 py-3 font-semibold text-gray-800">{bill.customId || bill.id}</td>
                                                        <td className="px-4 py-3 text-gray-500">{new Date(bill.createdAt).toLocaleDateString()}</td>
                                                        <td className="px-4 py-3">
                                                            <span className={`px-2 py-0.5 text-xs font-semibold rounded-full ${
                                                                bill.paymentStatus === 'PAID' ? 'bg-emerald-50 text-emerald-700' :
                                                                bill.paymentStatus === 'PARTIAL' ? 'bg-amber-50 text-amber-700' :
                                                                'bg-red-50 text-red-700'
                                                            }`}>
                                                                {bill.paymentStatus || 'PENDING'}
                                                            </span>
                                                        </td>
                                                        <td className="px-4 py-3 text-right text-gray-800 font-medium">₹{total}</td>
                                                        <td className="px-4 py-3 text-right text-emerald-600">₹{paid}</td>
                                                        <td className={`px-4 py-3 text-right font-semibold ${bal > 0 ? 'text-red-600' : 'text-gray-500'}`}>₹{bal}</td>
                                                        <td className="px-4 py-3 text-center">
                                                            <button
                                                                onClick={async () => {
                                                                    try {
                                                                        setPrintingBillId(bill.id);
                                                                        const blob = await hospitalService.downloadReceipt(bill.id);
                                                                        const fileURL = URL.createObjectURL(blob);
                                                                        const pdfWindow = window.open();
                                                                        if (pdfWindow) {
                                                                            pdfWindow.location.href = fileURL;
                                                                        } else {
                                                                            const link = document.createElement('a');
                                                                            link.href = fileURL;
                                                                            link.setAttribute('download', `receipt_${bill.customId || bill.id}.pdf`);
                                                                            document.body.appendChild(link);
                                                                            link.click();
                                                                            link.remove();
                                                                        }
                                                                    } catch (err) {
                                                                        console.error("Print failed:", err);
                                                                    } finally {
                                                                        setPrintingBillId(null);
                                                                    }
                                                                }}
                                                                disabled={printingBillId === bill.id}
                                                                className="px-2.5 py-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800 bg-indigo-50 hover:bg-indigo-100 rounded-md transition disabled:opacity-50"
                                                            >
                                                                {printingBillId === bill.id ? 'Printing...' : 'Print'}
                                                            </button>
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                            ) : (
                                <div className="text-center py-12 text-gray-400 text-sm">
                                    <p>No bills found for this patient.</p>
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="flex justify-end gap-3 p-6 border-t border-gray-200 bg-gray-50">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                    >
                        Close
                    </button>
                </div>
            </div>
        </div>
    );
};

// Helper component for displaying info fields
const InfoField = ({ label, value }) => (
    <div className="bg-white border border-gray-200 rounded-lg p-4">
        <label className="block text-xs font-medium text-gray-500 uppercase mb-1">
            {label}
        </label>
        <p className="text-sm font-medium text-gray-900">
            {value || 'N/A'}
        </p>
    </div>
);

export default PatientDetailsModal;
