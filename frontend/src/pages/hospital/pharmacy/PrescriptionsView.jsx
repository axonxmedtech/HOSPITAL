import React, { useState, useEffect, useMemo } from 'react';
import { format } from 'date-fns';
import hospitalService from '../../../services/hospitalService';
import { SkeletonTableRow } from '../../../components/Skeleton';

const PrescriptionsView = () => {
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedPrescription, setSelectedPrescription] = useState(null);
    const [prescriptions, setPrescriptions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [sourceFilter, setSourceFilter] = useState('ALL');

    useEffect(() => {
        fetchData();
    }, []);

    const fetchData = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await hospitalService.getPendingPrescriptions();
            setPrescriptions(data || []);
        } catch (err) {
            console.error("Error fetching prescriptions:", err);
            setError("Failed to load prescriptions. Please check backend connection.");
        } finally {
            setLoading(false);
        }
    };

    // 1. Group raw prescription rows by medicalRecordId on frontend
    // Each consultation is one unique visual report for the user
    const groupedPrescriptions = useMemo(() => {
        const map = new Map();

        prescriptions.forEach(item => {
            // If medicalRecordId isn't provided, fall back to creating a dummy group key using combo of Patient/Doctor/Time
            const key = item.medicalRecordId ? `MR_${item.medicalRecordId}` : `${item.patientName}_${item.createdAt}`;
            
            if (!map.has(key)) {
                map.set(key, {
                    id: item.medicalRecordId ? `RX-${item.medicalRecordId}` : `RX-RAW-${item.id}`,
                    patientName: item.patientName || 'Unknown Patient',
                    patientAge: item.patientAge || 'N/A',
                    patientGender: item.patientGender || 'N/A',
                    doctorName: item.doctorName || 'Unknown Doctor',
                    createdAt: item.createdAt,
                    status: item.status === 'ACTIVE' ? 'Pending' : item.status,
                    diagnosis: item.diagnosis || 'See Consultation',
                    notes: item.notes || 'No specific pharmacist notes provided.',
                    symptoms: item.symptoms || 'N/A',
                    patientId: item.patientId,
                    prescriptionSource: item.prescriptionSource || 'OPD',
                    medicines: []
                });
            }

            const entry = map.get(key);
            entry.medicines.push({
                id: item.id,
                name: item.medicineName,
                dosage: item.dosage || '-',
                frequency: item.frequency || '-',
                duration: item.duration || '-',
                instructions: item.instructions || ''
            });
        });

        // Convert Map values back to Array sorted by newest created first
        return Array.from(map.values()).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    }, [prescriptions]);

    const filteredPrescriptions = groupedPrescriptions.filter(p => {
        const matchesSearch = p.patientName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                             p.doctorName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                             p.id.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesSource = sourceFilter === 'ALL' || p.prescriptionSource === sourceFilter;
        return matchesSearch && matchesSource;
    });

    return (
        <div className="space-y-6 relative">
            {/* Header with Search */}
            <div className="bg-white border border-gray-200 rounded-lg p-4 flex flex-wrap items-center justify-between gap-4">
                <div>
                    <h1 className="text-xl font-bold text-gray-900">Live Doctor Prescriptions</h1>
                    <p className="text-sm text-gray-500 mt-0.5">View real-time prescribing data direct from consultation rooms.</p>
                </div>
                <div className="flex items-center gap-3 flex-1 sm:flex-none justify-end">
                     <select
                        value={sourceFilter}
                        onChange={(e) => setSourceFilter(e.target.value)}
                        className="px-3 py-2 border border-gray-300 rounded text-sm outline-none focus:border-gray-900 focus:ring-1 focus:ring-gray-900 bg-white font-medium text-gray-700 transition-all cursor-pointer"
                     >
                        <option value="ALL">All Visit Types</option>
                        <option value="APPOINTMENT">Appointments Only</option>
                        <option value="OPD">OPD Direct Only</option>
                        <option value="IPD">IPD Admissions Only</option>
                     </select>
                     <div className="relative w-full sm:w-80">
                         <input 
                            type="text" 
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Search by Patient Name..." 
                            className="pl-10 pr-4 py-2 border border-gray-300 rounded w-full text-sm outline-none focus:border-gray-900 focus:ring-1 focus:ring-gray-900 bg-white transition-all"
                         />
                         <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 transform -translate-y-1/2" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
                     </div>
                     <button 
                        onClick={fetchData}
                        className="p-2 text-gray-600 border border-gray-300 rounded bg-white hover:bg-gray-50"
                        title="Refresh list"
                     >
                        <svg className={`w-5 h-5 ${loading ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" /></svg>
                     </button>
                </div>
            </div>

            {error && (
                 <div className="bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded text-sm font-medium flex items-center gap-2">
                     <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" /></svg>
                     {error}
                 </div>
            )}

            {/* Prescriptions Table */}
            <div className="bg-white border border-gray-200 rounded-lg flex flex-col overflow-hidden min-h-[200px]">
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left border-collapse">
                         <thead className="bg-gray-50 text-xs text-gray-500 uppercase sticky top-0 border-b border-gray-200 z-10">
                             <tr>
                                 <th className="px-6 py-3 font-medium">Sr No.</th>
                                 <th className="px-6 py-3 font-medium">Patient Name</th>
                                 <th className="px-6 py-3 font-medium">Doctor Name</th>
                                 <th className="px-6 py-3 font-medium">Prescribed Date</th>
                                 <th className="px-6 py-3 font-medium">Visit Type</th>
                                 <th className="px-6 py-3 font-medium text-center">Meds Count</th>
                                 <th className="px-6 py-3 font-medium text-right">Action</th>
                             </tr>
                         </thead>
                         <tbody className="divide-y divide-gray-100 relative">
                            {loading ? (
                                <>{
                                    Array.from({ length: 5 }).map((_, i) => (
                                        <SkeletonTableRow key={i} cols={7} delay={i} />
                                    ))
                                }</>
                            ) : filteredPrescriptions.length > 0 ? filteredPrescriptions.map((p, idx) => (
                                <tr key={p.id} className="hover:bg-gray-50 transition-colors">
                                    <td className="px-6 py-4 text-gray-500 font-medium">{idx + 1}</td>
                                    <td className="px-6 py-4 font-bold text-gray-900">{p.patientName}</td>
                                    <td className="px-6 py-4 text-gray-700">{p.doctorName}</td>
                                    <td className="px-6 py-4 text-gray-500">
                                        {p.createdAt ? format(new Date(p.createdAt), 'dd MMM yyyy, hh:mm a') : 'Date Not Recorded'}
                                    </td>
                                    <td className="px-6 py-4 text-center">
                                        <span className="bg-gray-100 text-gray-700 px-2 py-0.5 rounded text-xs font-bold border border-gray-200">
                                            {p.medicines.length}
                                        </span>
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <button 
                                            onClick={() => setSelectedPrescription(p)}
                                            className="px-4 py-1.5 bg-white border border-gray-300 text-gray-700 font-bold text-xs rounded hover:bg-gray-900 hover:text-white transition-colors shadow-sm"
                                        >
                                            View Report
                                        </button>
                                    </td>
                                </tr>
                            )) : (
                                <tr>
                                    <td colSpan={7} className="px-6 py-16 text-center text-gray-400">
                                        <div className="flex flex-col items-center">
                                            <svg className="w-16 h-16 mb-3 opacity-20 text-gray-900" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                                            <h3 className="text-lg font-medium text-gray-600">No Data Found</h3>
                                            <p className="text-sm max-w-xs mt-1">There are currently no pending doctor prescriptions found in your live database.</p>
                                        </div>
                                    </td>
                                </tr>
                            )}
                         </tbody>
                    </table>
                </div>
            </div>

            {/* Prescription View Modal (Report overlay) */}
            {selectedPrescription && (
                <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 overflow-hidden">
                    {/* Backdrop */}
                    <div className="absolute inset-0 bg-gray-900/60 backdrop-blur-sm" onClick={() => setSelectedPrescription(null)}></div>
                    
                    {/* Modal Content */}
                    <div className="bg-white w-full max-w-2xl rounded-xl shadow-2xl relative z-10 flex flex-col max-h-[90vh] overflow-hidden animate-in zoom-in-95 duration-200 border border-gray-200">
                        
                        {/* Modal Header Actions */}
                        <div className="bg-gray-50 border-b border-gray-200 p-4 flex justify-between items-center">
                            <h3 className="font-bold text-gray-800 flex items-center gap-2">
                                <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                                Active Prescription Sheet
                            </h3>
                            <div className="flex items-center gap-2">
                                <button onClick={() => setSelectedPrescription(null)} className="p-1 text-gray-400 hover:text-gray-800 transition-colors">
                                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                                </button>
                            </div>
                        </div>

                        {/* The "Report" Layout (Paper Mimic) */}
                        <div className="flex-1 overflow-y-auto p-6 sm:p-8 bg-gray-100/40">
                            <div className="bg-white border border-gray-200 shadow-md rounded-md p-6 sm:p-8 max-w-xl mx-auto relative font-sans text-gray-900">
                                
                                {/* Hospital Mock Header */}
                                <div className="flex justify-between items-start border-b-2 border-gray-900 pb-4 mb-6">
                                    <div>
                                        <h2 className="text-xl font-black uppercase tracking-tight text-gray-900">Hospital HMS</h2>
                                        <p className="text-[10px] text-gray-500 uppercase font-bold tracking-widest">Medical Facility</p>
                                    </div>
                                    <div className="text-right text-xs text-gray-600">
                                        <p className="font-bold text-gray-900 text-sm">{selectedPrescription.doctorName}</p>
                                        <p>Consulting Specialist</p>
                                    </div>
                                </div>

                                {/* Info Header Grid */}
                                <div className="grid grid-cols-2 gap-4 text-xs mb-6 bg-gray-50 p-3 border border-gray-200">
                                    <div>
                                        <p className="mb-1"><span className="text-gray-500">Patient Name:</span> <span className="font-bold block text-sm text-gray-900">{selectedPrescription.patientName}</span></p>
                                        <p><span className="text-gray-500">Age/Gender:</span> <span className="font-medium">{selectedPrescription.patientAge} / {selectedPrescription.patientGender}</span></p>
                                    </div>
                                    <div className="text-right">
                                        <p className="mb-1"><span className="text-gray-500">Date:</span> <span className="font-bold block text-sm text-gray-900">{selectedPrescription.createdAt ? format(new Date(selectedPrescription.createdAt), 'dd MMM yyyy') : 'N/A'}</span></p>
                                        <p><span className="text-gray-500">Prescription No:</span> <span className="font-mono font-bold text-gray-900">{selectedPrescription.id}</span></p>
                                    </div>
                                </div>

                                {/* Clinical Info */}
                                {selectedPrescription.diagnosis && (
                                    <div className="mb-4 text-sm">
                                        <p className="font-bold text-gray-800 mb-0.5">Impression / Diagnosis:</p>
                                        <p className="text-gray-700 border-l-2 border-gray-300 pl-2 bg-gray-50 py-1 text-xs">{selectedPrescription.diagnosis}</p>
                                    </div>
                                )}

                                <div className="pt-2 mb-6">
                                    <div className="text-2xl font-black text-gray-900 mb-3 font-serif">Rx</div>
                                    
                                    {/* Medicine List Table Inside Report */}
                                    <table className="w-full text-xs border-collapse">
                                        <thead>
                                            <tr className="border-b border-gray-800 text-[10px] uppercase text-gray-500 font-bold">
                                                <th className="py-2 text-left">S.N.</th>
                                                <th className="py-2 text-left w-1/2">Item Particulars</th>
                                                <th className="py-2 text-center">Frequency</th>
                                                <th className="py-2 text-right">Duration</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {selectedPrescription.medicines.map((med, i) => (
                                                <tr key={med.id} className="border-b border-gray-200 border-dashed last:border-b-2 last:border-gray-800">
                                                    <td className="py-3 text-gray-400">{i+1}.</td>
                                                    <td className="py-3 align-top">
                                                        <p className="font-bold text-gray-900 text-sm uppercase">{med.name}</p>
                                                        {med.instructions && <p className="text-[10px] text-gray-500 mt-0.5 italic">&bull; {med.instructions}</p>}
                                                    </td>
                                                    <td className="py-3 text-center align-top font-medium text-gray-900">
                                                        <span className="bg-gray-100 border border-gray-200 px-2 py-0.5 rounded font-mono">{med.frequency}</span>
                                                        <div className="text-[10px] text-gray-500 mt-1">{med.dosage}</div>
                                                    </td>
                                                    <td className="py-3 text-right align-top font-medium text-gray-700">{med.duration}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>

                                {/* Notes */}
                                {selectedPrescription.notes && (
                                    <div className="mt-6 pt-2 text-xs text-gray-600">
                                        <p className="font-bold mb-1 text-gray-700">Instructions for Pharmacist / Patient:</p>
                                        <p className="italic text-gray-500 border p-2 rounded bg-yellow-50/20">"{selectedPrescription.notes}"</p>
                                    </div>
                                )}

                                {/* Doctor Sign Signature Placeholder */}
                                <div className="mt-10 flex justify-end">
                                    <div className="text-center border-t border-gray-800 pt-1.5 w-40 text-[10px] font-bold uppercase tracking-wider text-gray-800">
                                        Doctor Sign
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Action Bottom Bar */}
                        <div className="bg-white border-t border-gray-200 p-4 flex justify-end gap-3">
                            <button 
                                onClick={() => setSelectedPrescription(null)}
                                className="px-4 py-2 border border-gray-300 text-gray-700 rounded text-sm font-bold hover:bg-gray-50 transition-colors"
                            >
                                Close
                            </button>
                            <button 
                                onClick={() => window.print()}
                                className="px-4 py-2 border border-gray-300 bg-gray-50 text-gray-700 rounded text-sm font-bold hover:bg-gray-100 transition-colors"
                            >
                                Print 
                            </button>
                            <button 
                                onClick={() => onNavigate('billing', { 
                                    patient: { 
                                        id: selectedPrescription.patientId, // Ensure this exists in the grouped data
                                        patientName: selectedPrescription.patientName,
                                        pid: selectedPrescription.id.replace('RX-', '')
                                    },
                                    prescription: selectedPrescription
                                })}
                                className="px-6 py-2 bg-gray-900 text-white rounded text-sm font-bold hover:bg-gray-800 transition-colors shadow-md"
                            >
                                Process Bill
                            </button>
                        </div>

                    </div>
                </div>
            )}
        </div>
    );
};

export default PrescriptionsView;
