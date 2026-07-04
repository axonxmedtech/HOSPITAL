import React, { useEffect, useRef } from 'react';
import PrescriptionPresetsManager from './PrescriptionPresetsManager';

/**
 * Modal wrapper around PrescriptionPresetsManager, used from
 * ConsultationModal so a doctor can manage prescription presets without
 * leaving the consultation screen.
 */
const ManagePrescriptionPresetsModal = ({ isOpen, onClose }) => {
    const closeBtnRef = useRef(null);

    useEffect(() => {
        if (isOpen) {
            setTimeout(() => closeBtnRef.current?.focus(), 50);
        }
    }, [isOpen]);

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[70] p-4"
            onClick={onClose}
            role="presentation"
        >
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="manage-prescription-presets-title"
                className="bg-gray-50 rounded-2xl shadow-2xl w-full max-w-2xl max-h-[85vh] overflow-y-auto"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex justify-between items-center p-4 border-b border-gray-200 bg-white rounded-t-2xl sticky top-0">
                    <h2 id="manage-prescription-presets-title" className="text-base font-bold text-gray-900">Manage Prescription Presets</h2>
                    <button ref={closeBtnRef} onClick={onClose} className="text-gray-400 hover:text-gray-700 text-xl leading-none" aria-label="Close">
                        &times;
                    </button>
                </div>
                <div className="p-4">
                    <PrescriptionPresetsManager />
                </div>
            </div>
        </div>
    );
};

export default ManagePrescriptionPresetsModal;
