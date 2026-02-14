import React, { useState, useEffect, useMemo } from 'react';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';
import MedicineAutocomplete from './MedicineAutocomplete';

const ConsultationModal = ({ isOpen, onClose, onSuccess, appointment, patient, opd }) => {
    console.log("ConsultationModal render:", { isOpen, appointment, patient, opd });
    const [activeTab, setActiveTab] = useState('clinical'); // 'clinical' or 'prescription'
    const [patientDetails, setPatientDetails] = useState(null);
    const [loadingDetails, setLoadingDetails] = useState(false);

    // Normalize patient prop when modal opens — don't mutate props
    // We'll compute a patient identifier (publicId or numeric id) when fetching details

    const [formData, setFormData] = useState({
        appointmentId: appointment?.id || null,
        patientId: patient?.publicId || patient?.id,
        opdId: opd?.id || null,
        symptoms: '',
        diagnosis: '',
        treatmentNotes: '',
        followUpDate: '',
        followUpRequired: false,
        labRequired: false,
        labTests: [],
        prescription: [] // Array of { medicineName, dosage, frequency, duration, instructions }
    });

    // Hardcoded lab test options for now
    const LAB_OPTIONS = ["CBC", "LFT", "RFT", "RBS", "Lipid Profile", "TSH", "HbA1c", "Urine Routine"];

    // Update form data when modal opens or patient changes
    // Update form data when modal opens or patient changes
    useEffect(() => {
        if (isOpen && patient) {
            setFormData(prev => ({
                ...prev,
                appointmentId: appointment?.id || null,
                patientId: patient?.publicId || patient?.id,
                opdId: opd?.id || prev.opdId,
                symptoms: prev.patientId === (patient?.publicId || patient?.id) ? prev.symptoms : '',
                diagnosis: prev.patientId === (patient?.publicId || patient?.id) ? prev.diagnosis : '',
                treatmentNotes: prev.patientId === (patient?.publicId || patient?.id) ? prev.treatmentNotes : '',
                followUpDate: prev.patientId === (patient?.publicId || patient?.id) ? prev.followUpDate : '',
                prescription: prev.patientId === (patient?.publicId || patient?.id) ? prev.prescription : []
            }));
        }
    }, [isOpen, patient?.id, patient?.publicId, appointment?.id, opd?.id]);

    // Default medicine added row
    const [newMedicine, setNewMedicine] = useState({
        medicineName: '',
        dosage: '',
        frequency: '',
        duration: '',
        instructions: ''
    });

    const { success, error: toastError } = useToast();

    // Fetch patient details when modal opens
    useEffect(() => {
        const fetchPatientDetails = async () => {
            // Determine patient identifier: appointment patientId, then patient.publicId, then patient.id
            const pid = appointment?.patientId || patient?.publicId || patient?.id || (opd && opd.patient && (opd.patient.publicId || opd.patient.id));
            if (isOpen && pid) {
                setLoadingDetails(true);
                try {
                    const details = await hospitalService.getPatientConsultationDetails(pid);
                    setPatientDetails(details);
                } catch (err) {
                    console.error('Failed to fetch patient details:', err);
                    setPatientDetails(null);
                } finally {
                    setLoadingDetails(false);
                }
            } else {
                // No patient id available yet
                setPatientDetails(null);
            }
        };
        fetchPatientDetails();
    }, [isOpen, appointment?.patientId, patient?.publicId, patient?.id, opd?.id]);

    if (!isOpen || (!appointment && !patient)) return null;

    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
    };

    const handleAddMedicine = () => {
        if (!newMedicine.medicineName) return;
        setFormData(prev => ({
            ...prev,
            prescription: [...prev.prescription, newMedicine]
        }));
        setNewMedicine({
            medicineName: '',
            dosage: '',
            frequency: '',
            duration: '',
            instructions: ''
        });
    };

    const handleRemoveMedicine = (index) => {
        setFormData(prev => ({
            ...prev,
            prescription: prev.prescription.filter((_, i) => i !== index)
        }));
    };

    const handleSubmit = async () => {
        // Warning if user typed a medicine but didn't click Add
        if (newMedicine.medicineName) {
            toastError("Please click '+ Add Medicine' or clear the medicine fields before submitting.");
            return;
        }

        // Prepare payload; include selected lab tests if any
        const payload = { ...formData };
        if (!payload.labRequired) payload.labTests = [];

        console.log("Submitting Consultation Data:", JSON.stringify(payload, null, 2));

        try {
            await hospitalService.submitConsultation(payload);
            success('Consultation submitted successfully');
            onSuccess();
            onClose();
        } catch (err) {
            console.error("Consultation failed", err);
            toastError(err.response?.data || 'Failed to submit consultation');
        }
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-6xl h-[90vh] flex flex-col m-4 animate-fade-in-up">

                {/* Header */}
                <div className="p-6 border-b border-gray-200 flex justify-between items-center bg-white rounded-t-lg">
                    <div>
                        <h3 className="text-xl font-bold text-gray-800">Patient Consultation</h3>
                        <p className="text-sm text-gray-600 mt-1">
                            <span className="font-semibold text-gray-800">{patientDetails?.patient?.name || 'Unknown'}</span>
                            <span className="mx-2">•</span>
                            <span className="text-gray-500">ID: {patientDetails?.patient?.customId || patientDetails?.patient?.publicId || patientDetails?.patient?.id}</span>
                        </p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition">
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                {/* 2-Column Layout */}
                <div className="flex flex-1 overflow-hidden">

                    {/* LEFT PANEL - Patient Profile */}
                    <div className="w-1/3 border-r border-gray-200 bg-gray-50 overflow-y-auto">
                        {loadingDetails ? (
                            <div className="p-6 space-y-4">
                                <div className="animate-pulse">
                                    <div className="h-4 bg-gray-300 rounded w-3/4 mb-2"></div>
                                    <div className="h-4 bg-gray-300 rounded w-1/2"></div>
                                </div>
                            </div>
                        ) : patientDetails ? (
                            <div className="p-6 space-y-6">
                                {/* Demographics */}
                                <div>
                                    <h4 className="text-sm font-bold text-gray-700 uppercase tracking-wide mb-3 flex items-center">
                                        Patient Information
                                    </h4>
                                    <div className="space-y-2 text-sm">
                                        <div className="flex justify-between py-2 border-b border-gray-200">
                                            <span className="text-gray-500">Age</span>
                                            <span className="font-semibold text-gray-800">{patientDetails.patient.age} years</span>
                                        </div>
                                        <div className="flex justify-between py-2 border-b border-gray-200">
                                            <span className="text-gray-500">Gender</span>
                                            <span className="font-semibold text-gray-800">{patientDetails.patient.gender}</span>
                                        </div>
                                        <div className="flex justify-between py-2 border-b border-gray-200">
                                            <span className="text-gray-500">Phone</span>
                                            <span className="font-semibold text-gray-800">{patientDetails.patient.phone}</span>
                                        </div>
                                        <div className="flex justify-between py-2 border-b border-gray-200">
                                            <span className="text-gray-500">Status</span>
                                            <span className={`px-2 py-1 rounded text-xs font-semibold ${patientDetails.patient.status === 'COMPLETED' ? 'bg-gray-100 text-gray-800' :
                                                (patientDetails.patient.status === 'CONSULTING' || patientDetails.patient.status === 'IN_PROGRESS') ? 'bg-gray-100 text-gray-800' :
                                                    'bg-gray-100 text-gray-800'
                                                }`}>
                                                {patientDetails.patient.status}
                                            </span>
                                        </div>
                                        {patientDetails.patient.address && (
                                            <div className="pt-2">
                                                <span className="text-gray-500 block mb-1">Address</span>
                                                <span className="text-gray-800 text-xs">{patientDetails.patient.address}</span>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                {/* Medical History */}
                                <div>
                                    <h4 className="text-sm font-bold text-gray-700 uppercase tracking-wide mb-3 flex items-center">
                                        Medical History
                                    </h4>
                                    {patientDetails.medicalHistory && patientDetails.medicalHistory.length > 0 ? (
                                        <div className="space-y-3">
                                            {patientDetails.medicalHistory.map((record, index) => (
                                                <div key={index} className="bg-white rounded-lg p-3 shadow-sm border border-gray-200">
                                                    <div className="text-xs text-gray-500 mb-1">
                                                        {new Date(record.date).toLocaleDateString('en-US', {
                                                            year: 'numeric',
                                                            month: 'short',
                                                            day: 'numeric'
                                                        })}
                                                    </div>
                                                    {record.symptoms && (
                                                        <div className="text-sm mb-1">
                                                            <span className="text-gray-500">Symptoms:</span>
                                                            <span className="text-gray-700 ml-1">{record.symptoms}</span>
                                                        </div>
                                                    )}
                                                    {record.diagnosis && (
                                                        <div className="text-sm font-semibold text-gray-800">
                                                            {record.diagnosis}
                                                        </div>
                                                    )}
                                                </div>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="text-center py-6 text-gray-400 text-sm">
                                            <p>No previous consultations</p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        ) : (
                            <div className="p-6 text-center text-gray-400">
                                <p>Loading patient details...</p>
                            </div>
                        )}
                    </div>

                    {/* RIGHT PANEL - Clinical Notes & Prescription */}
                    <div className="flex-1 flex flex-col overflow-hidden">

                        {/* Tabs */}
                        <div className="flex border-b border-gray-200 bg-white">
                            <button
                                className={`flex-1 py-4 text-sm font-semibold transition-colors ${activeTab === 'clinical' ? 'text-gray-900 border-b-2 border-gray-900 bg-gray-50' : 'text-gray-500 hover:bg-gray-50'}`}
                                onClick={() => setActiveTab('clinical')}
                            >
                                Clinical Notes
                            </button>
                            <button
                                className={`flex-1 py-4 text-sm font-semibold transition-colors ${activeTab === 'prescription' ? 'text-gray-900 border-b-2 border-gray-900 bg-gray-50' : 'text-gray-500 hover:bg-gray-50'}`}
                                onClick={() => setActiveTab('prescription')}
                            >
                                Prescription ({formData.prescription.length})
                            </button>
                        </div>

                        {/* Tab Content */}
                        <div className="flex-1 overflow-y-auto p-6 bg-white">
                            {activeTab === 'clinical' ? (
                                <div className="space-y-4">
                                    <div>
                                        <label className="block text-sm font-semibold text-gray-700 mb-2">Symptoms</label>
                                        <textarea
                                            value={formData.symptoms}
                                            onChange={(e) => handleChange('symptoms', e.target.value)}
                                            className="w-full border border-gray-300 rounded-lg px-4 py-3 focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                                            rows="3"
                                            placeholder="Enter patient's symptoms..."
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm font-semibold text-gray-700 mb-2">Diagnosis</label>
                                        <textarea
                                            value={formData.diagnosis}
                                            onChange={(e) => handleChange('diagnosis', e.target.value)}
                                            className="w-full border border-gray-300 rounded-lg px-4 py-3 focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                                            rows="3"
                                            placeholder="Enter diagnosis..."
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm font-semibold text-gray-700 mb-2">Treatment Notes</label>
                                        <textarea
                                            value={formData.treatmentNotes}
                                            onChange={(e) => handleChange('treatmentNotes', e.target.value)}
                                            className="w-full border border-gray-300 rounded-lg px-4 py-3 focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                                            rows="4"
                                            placeholder="Enter treatment plan and notes..."
                                        />
                                    </div>

                                    <div>
                                        <div className="flex items-center gap-3">
                                            <input
                                                id="followup-checkbox"
                                                type="checkbox"
                                                checked={formData.followUpRequired}
                                                onChange={(e) => setFormData(prev => ({ ...prev, followUpRequired: e.target.checked }))}
                                                className="h-4 w-4"
                                            />
                                            <label htmlFor="followup-checkbox" className="text-sm font-medium text-gray-700">Schedule Follow-up</label>
                                        </div>
                                        {formData.followUpRequired && (
                                            <div className="mt-3">
                                                <label className="block text-sm font-semibold text-gray-700 mb-2">Follow-up Date</label>
                                                <input
                                                    type="date"
                                                    value={formData.followUpDate}
                                                    onChange={(e) => handleChange('followUpDate', e.target.value)}
                                                    className="w-full border border-gray-300 rounded-lg px-4 py-3 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                                                />
                                            </div>
                                        )}

                                        <div className="mt-4">
                                            <div className="flex items-center gap-3">
                                                <input
                                                    id="lab-checkbox"
                                                    type="checkbox"
                                                    checked={formData.labRequired}
                                                    onChange={(e) => setFormData(prev => ({ ...prev, labRequired: e.target.checked }))}
                                                    className="h-4 w-4"
                                                />
                                                <label htmlFor="lab-checkbox" className="text-sm font-medium text-gray-700">Order Lab Tests</label>
                                            </div>

                                            {formData.labRequired && (
                                                <div className="mt-3 grid grid-cols-2 gap-2">
                                                    {LAB_OPTIONS.map((opt) => (
                                                        <label key={opt} className="inline-flex items-center gap-2">
                                                            <input
                                                                type="checkbox"
                                                                checked={formData.labTests.includes(opt)}
                                                                onChange={(e) => {
                                                                    if (e.target.checked) {
                                                                        setFormData(prev => ({ ...prev, labTests: [...prev.labTests, opt] }));
                                                                    } else {
                                                                        setFormData(prev => ({ ...prev, labTests: prev.labTests.filter(t => t !== opt) }));
                                                                    }
                                                                }}
                                                                className="h-4 w-4"
                                                            />
                                                            <span className="text-sm">{opt}</span>
                                                        </label>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ) : (
                                <div className="space-y-4">
                                    {/* Prescription List */}
                                    {formData.prescription.length > 0 && (
                                        <div className="mb-6">
                                            <h4 className="text-sm font-semibold text-gray-700 mb-3">Prescribed Medicines ({formData.prescription.length})</h4>
                                            <div className="space-y-2">
                                                {formData.prescription.map((med, index) => (
                                                    <div key={index} className="flex items-center justify-between bg-gray-50 p-3 rounded-lg border border-gray-200">
                                                        <div className="flex-1">
                                                            <div className="font-semibold text-gray-800">{med.medicineName}</div>
                                                            <div className="text-xs text-gray-500 mt-1">
                                                                {med.dosage} • {med.frequency} • {med.duration}
                                                                {med.instructions && ` • ${med.instructions}`}
                                                            </div>
                                                        </div>
                                                        <button
                                                            onClick={() => handleRemoveMedicine(index)}
                                                            className="ml-3 text-gray-500 hover:text-gray-700"
                                                        >
                                                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                                            </svg>
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    <div className="bg-gray-50 p-4 rounded-lg border border-gray-200">
                                        <h4 className="text-sm font-semibold text-gray-700 mb-3">Add Medicine</h4>
                                        <div className="grid grid-cols-2 gap-3">
                                            <div className="col-span-2">
                                                <MedicineAutocomplete
                                                    value={newMedicine.medicineName}
                                                    onChange={(name) => setNewMedicine(prev => ({ ...prev, medicineName: name }))}
                                                    onSelect={(med) => {
                                                        setNewMedicine(prev => ({
                                                            ...prev,
                                                            medicineName: med.name,
                                                            dosage: med.defaultDosage || prev.dosage,
                                                            frequency: med.defaultFrequency || prev.frequency,
                                                            duration: med.defaultDuration || prev.duration,
                                                            // manufacturer: med.manufacturer // Optional if we want to show it
                                                        }));
                                                    }}
                                                />
                                            </div>
                                            <input
                                                type="text"
                                                placeholder="Dosage (e.g., 500mg)"
                                                value={newMedicine.dosage}
                                                onChange={(e) => setNewMedicine({ ...newMedicine, dosage: e.target.value })}
                                                className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500"
                                            />
                                            <input
                                                type="text"
                                                placeholder="Frequency (e.g., 1-0-1)"
                                                value={newMedicine.frequency}
                                                onChange={(e) => setNewMedicine({ ...newMedicine, frequency: e.target.value })}
                                                className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500"
                                            />
                                            <input
                                                type="text"
                                                placeholder="Duration (e.g., 5 Days)"
                                                value={newMedicine.duration}
                                                onChange={(e) => setNewMedicine({ ...newMedicine, duration: e.target.value })}
                                                className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500"
                                            />
                                            <input
                                                type="text"
                                                placeholder="Instructions (e.g., After food)"
                                                value={newMedicine.instructions}
                                                onChange={(e) => setNewMedicine({ ...newMedicine, instructions: e.target.value })}
                                                className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500"
                                            />
                                        </div>
                                        <button
                                            onClick={handleAddMedicine}
                                            disabled={!newMedicine.medicineName}
                                            className="mt-3 w-full bg-primary-600 text-white py-2 rounded-lg hover:bg-primary-700 transition disabled:bg-gray-300 disabled:cursor-not-allowed text-sm font-semibold"
                                        >
                                            + Add Medicine
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Footer Actions */}
                        <div className="p-6 border-t border-gray-200 bg-gray-50 flex justify-end space-x-3">
                            <button
                                onClick={onClose}
                                className="px-6 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-100 transition font-semibold"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleSubmit}
                                className="px-6 py-2.5 bg-green-600 text-white rounded-lg hover:bg-green-700 transition font-semibold shadow-md"
                            >
                                Complete Consultation
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ConsultationModal;
