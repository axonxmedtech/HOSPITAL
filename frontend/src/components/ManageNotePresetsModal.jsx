import React, { useEffect, useRef } from 'react';
import NotePresetsManager from './NotePresetsManager';

/**
 * Modal wrapper around NotePresetsManager, used from ConsultationModal so a
 * doctor can manage quick notes without leaving the consultation screen.
 */
const ManageNotePresetsModal = ({ isOpen, onClose, fieldType }) => {
    const closeBtnRef = useRef(null);

    // Auto-focus the close button on open, matching ConfirmationModal's
    // accessibility pattern (BUG-039).
    useEffect(() => {
        if (isOpen) {
            setTimeout(() => closeBtnRef.current?.focus(), 50);
        }
    }, [isOpen]);

    // Dismiss modal on Escape key (BUG-039).
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
                aria-labelledby="manage-note-presets-title"
                className="bg-gray-50 rounded-2xl shadow-2xl w-full max-w-lg max-h-[85vh] overflow-y-auto"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex justify-between items-center p-4 border-b border-gray-200 bg-white rounded-t-2xl sticky top-0">
                    <h2 id="manage-note-presets-title" className="text-base font-bold text-gray-900">Manage Quick Notes</h2>
                    <button ref={closeBtnRef} onClick={onClose} className="text-gray-400 hover:text-gray-700 text-xl leading-none" aria-label="Close">
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
