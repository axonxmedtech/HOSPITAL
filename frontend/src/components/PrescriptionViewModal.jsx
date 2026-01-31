import React, { useEffect, useState } from 'react';
import hospitalService from '../services/hospitalService';

const PrescriptionViewModal = ({ isOpen, onClose, patient }) => {
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (isOpen && patient) {
            fetchPrescription();
        } else {
            setData(null);
            setError(null);
        }
    }, [isOpen, patient]);

    const fetchPrescription = async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await hospitalService.getLatestPrescription(patient.publicId || patient.id);
            setData(result);
        } catch (err) {
            console.error("Failed to fetch prescription", err);
            // Display specific backend error if available, otherwise generic message
            setError(err.response?.data || "No prescription records found for this patient.");
        } finally {
            setLoading(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col m-4 animate-fade-in-up">

                {/* Header */}
                <div className="p-6 border-b border-gray-200 flex justify-between items-center bg-white rounded-t-lg">
                    <div>
                        <h3 className="text-xl font-bold text-gray-800">Prescription Details</h3>
                        <p className="text-sm text-gray-600 mt-1">
                            <span className="font-semibold">{patient?.name}</span> • ID: {patient?.customId || patient?.publicId || patient?.id}
                        </p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition text-2xl">×</button>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-6">
                    {loading ? (
                        <div className="flex justify-center items-center h-48">
                            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600"></div>
                        </div>
                    ) : error ? (
                        <div className="text-center py-12 text-gray-500 bg-gray-50 rounded-lg border border-dashed border-gray-300">
                            <span className="text-4xl block mb-2">📄</span>
                            <p>{error}</p>
                        </div>
                    ) : data ? (
                        <div className="space-y-8">

                            {/* Medical Record Info */}
                            <div className="bg-gray-50 p-4 rounded-lg border border-gray-200">
                                <h4 className="text-sm font-bold text-gray-700 uppercase tracking-wide mb-3">Clinical Notes</h4>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    <div>
                                        <p className="text-xs text-gray-500 uppercase">Diagnosis</p>
                                        <p className="font-medium text-gray-800">{data.medicalRecord.diagnosis || '-'}</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500 uppercase">Symptoms</p>
                                        <p className="font-medium text-gray-800">{data.medicalRecord.symptoms || '-'}</p>
                                    </div>
                                    <div className="md:col-span-2">
                                        <p className="text-xs text-gray-500 uppercase">Treatment Notes</p>
                                        <p className="font-medium text-gray-800">{data.medicalRecord.treatmentNotes || '-'}</p>
                                    </div>
                                    {data.medicalRecord.followUpDate && (
                                        <div>
                                            <p className="text-xs text-gray-500 uppercase">Follow-up Date</p>
                                            <p className="font-medium text-blue-600">{data.medicalRecord.followUpDate}</p>
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Medicines Table */}
                            <div>
                                <h4 className="text-sm font-bold text-gray-700 uppercase tracking-wide mb-3 flex items-center">
                                    Prescribed Medicines
                                </h4>
                                <div className="border rounded-lg overflow-hidden">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gray-50">
                                            <tr>
                                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Medicine</th>
                                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Dosage</th>
                                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Frequency</th>
                                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Duration</th>
                                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Instructions</th>
                                            </tr>
                                        </thead>
                                        <tbody className="bg-white divide-y divide-gray-200">
                                            {data.prescriptions && data.prescriptions.length > 0 ? (
                                                data.prescriptions.map((med, idx) => (
                                                    <tr key={idx}>
                                                        <td className="px-4 py-3 text-sm font-medium text-gray-900">{med.medicineName}</td>
                                                        <td className="px-4 py-3 text-sm text-gray-600">{med.dosage}</td>
                                                        <td className="px-4 py-3 text-sm text-gray-600">{med.frequency}</td>
                                                        <td className="px-4 py-3 text-sm text-gray-600">{med.duration}</td>
                                                        <td className="px-4 py-3 text-sm text-gray-600">{med.instructions}</td>
                                                    </tr>
                                                ))
                                            ) : (
                                                <tr>
                                                    <td colSpan="5" className="px-4 py-4 text-center text-sm text-gray-500">
                                                        No medicines prescribed.
                                                    </td>
                                                </tr>
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                        </div>
                    ) : null}
                </div>

                {/* Footer */}
                <div className="p-4 border-t border-gray-100 flex justify-end bg-gray-50 rounded-b-xl">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 font-medium transition"
                    >
                        Close
                    </button>
                    {data && (
                        <button
                            onClick={() => window.print()}
                            className="ml-3 px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 font-medium flex items-center gap-2 transition"
                        >
                            <span>🖨️</span> Print
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default PrescriptionViewModal;
