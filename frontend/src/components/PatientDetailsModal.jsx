import React, { useState } from 'react';

/**
 * PatientDetailsModal - Read-only view of patient information with tabs for additional data
 * 
 * Features:
 * - Basic patient information (read-only)
 * - Tabbed interface for: Info, OPD History, IPD History, Prescriptions
 * - Expandable design for future additions
 * 
 * @param {Object} patient - Patient data object
 * @param {Function} onClose - Close modal callback
 */
const PatientDetailsModal = ({ patient, onClose }) => {
    const [activeTab, setActiveTab] = useState('info');

    if (!patient) return null;

    const tabs = [
        { id: 'info', label: 'Patient Info' },
        { id: 'opd', label: 'OPD History' },
        { id: 'ipd', label: 'IPD History' },
        { id: 'prescriptions', label: 'Prescriptions' },
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
                                        ? 'border-gray-900 text-gray-900'
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

                    {activeTab === 'opd' && (
                        <div className="text-center py-12">
                            <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">OPD History</h3>
                            <p className="text-gray-600">OPD visit history will be displayed here</p>
                            <p className="text-sm text-gray-500 mt-2">Coming soon...</p>
                        </div>
                    )}

                    {activeTab === 'ipd' && (
                        <div className="text-center py-12">
                            <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
                            </svg>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">IPD History</h3>
                            <p className="text-gray-600">Inpatient admission history will be displayed here</p>
                            <p className="text-sm text-gray-500 mt-2">Coming soon...</p>
                        </div>
                    )}

                    {activeTab === 'prescriptions' && (
                        <div className="text-center py-12">
                            <svg className="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">Prescriptions</h3>
                            <p className="text-gray-600">Patient prescription history will be displayed here</p>
                            <p className="text-sm text-gray-500 mt-2">Coming soon...</p>
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
