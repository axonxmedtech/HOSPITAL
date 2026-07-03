import React from 'react';
import NotePresetsManager from './NotePresetsManager';

/**
 * Modal wrapper around NotePresetsManager, used from ConsultationModal so a
 * doctor can manage quick notes without leaving the consultation screen.
 */
const ManageNotePresetsModal = ({ isOpen, onClose, fieldType }) => {
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
                className="bg-gray-50 rounded-2xl shadow-2xl w-full max-w-lg max-h-[85vh] overflow-y-auto"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex justify-between items-center p-4 border-b border-gray-200 bg-white rounded-t-2xl sticky top-0">
                    <h2 className="text-base font-bold text-gray-900">Manage Quick Notes</h2>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-700 text-xl leading-none" aria-label="Close">
                        &times;
                    </button>
                </div>
                <div className="p-4">
                    <NotePresetsManager fieldType={fieldType} />
                </div>
            </div>
        </div>
    );
};

export default ManageNotePresetsModal;
