import React, { useState, useEffect } from 'react';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';
import { validateForm } from '../utils/validation';
import Button from './Button';
import CharCountInput from './CharCountInput';

const PatientModal = ({ isOpen, onClose, onSuccess, initialData }) => {
    const [formData, setFormData] = useState({});
    const [errors, setErrors] = useState({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [duplicatePatient, setDuplicatePatient] = useState(null);
    const [showMoreDetails, setShowMoreDetails] = useState(false);
    const { success, error: toastError } = useToast();
    const isEdit = !!initialData;

    useEffect(() => {
        if (isOpen) {
            if (initialData) {
                setFormData({ insurance: 'NO', ...initialData });
            } else {
                setFormData({ insurance: 'NO' });
            }
            setErrors({});
            setIsSubmitting(false);
            setDuplicatePatient(null);
            setShowMoreDetails(!!initialData); // expand on edit, collapse on new
        }
    }, [isOpen, initialData]);

    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
        if (errors[field]) {
            setErrors(prev => ({ ...prev, [field]: null }));
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrors({});
        setIsSubmitting(true);

        const rules = {
            name: ['required', 'name'],
            age: ['required', 'age'],
            gender: ['required'],
            phone: ['required', 'phone'],
            email: ['email'] // optional but valid if present
        };

        const validationErrors = validateForm(formData, rules);
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            setIsSubmitting(false);
            return;
        }

        try {
            // Strip insurance field so it is not sent to backend/database
            const { insurance, ...savePayload } = formData;

            if (isEdit) {
                await hospitalService.updatePatient(formData.id, savePayload);
                success('Patient updated successfully');
                console.log('[PatientModal] Patient updated');
            } else {
                const result = await hospitalService.addPatient(savePayload);
                success('Patient added successfully');
                console.log('[PatientModal] Patient added, calling onSuccess');
            }
            onSuccess();
            onClose();
        } catch (err) {
            console.error("Failed to save patient", err);
            const msg = err.response?.data?.message || 'Operation failed';
            toastError(msg);
        } finally {
            setIsSubmitting(false);
        }
    };

    useEffect(() => {
        if (!formData.phone || formData.phone.length < 10 || isEdit) {
            setDuplicatePatient(null);
            return;
        }
        const timer = setTimeout(async () => {
            try {
                const results = await hospitalService.getPatients(formData.phone, 0, 3);
                const patients = results.content || results || [];
                const match = patients.find(p =>
                    (p.phone || p.mobile || '').replace(/\D/g, '') === formData.phone.replace(/\D/g, '')
                );
                setDuplicatePatient(match || null);
            } catch {
                setDuplicatePatient(null);
            }
        }, 500);
        return () => clearTimeout(timer);
    }, [formData.phone, isEdit]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-2xl shadow-organic w-full max-w-3xl animate-scale-in overflow-hidden max-h-[90vh]">
                {/* Header */}
                <div className="bg-white px-8 py-6 border-b border-gray-200">
                    <div className="flex justify-between items-center">
                        <div>
                            <h3 className="text-2xl font-bold text-neutral-800">
                                {isEdit ? 'Edit Patient' : 'Add New Patient'}
                            </h3>
                            <p className="text-sm text-neutral-600 mt-1">
                                {isEdit ? 'Update patient information' : 'Enter patient details to create a new record'}
                            </p>
                        </div>
                        <button 
                            onClick={onClose} 
                            className="w-10 h-10 rounded-xl bg-white/80 hover:bg-white flex items-center justify-center text-neutral-400 hover:text-neutral-600 transition-all duration-200 hover:scale-105"
                        >
                            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        </button>
                    </div>
                </div>

                {/* Form */}
                <form onSubmit={handleSubmit} className="p-6 space-y-4 max-h-[76vh] overflow-auto">
                    {duplicatePatient && (
                        <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 flex items-start justify-between gap-3">
                            <div>
                                <p className="text-sm font-semibold text-amber-800">⚠️ Patient may already exist</p>
                                <p className="text-xs text-amber-700 mt-0.5">
                                    {duplicatePatient.name} &middot; {duplicatePatient.phone || duplicatePatient.mobile || '—'} &middot; UHID: {duplicatePatient.uhid || duplicatePatient.id}
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={onClose}
                                className="text-xs font-semibold text-amber-700 underline whitespace-nowrap mt-0.5"
                            >
                                Cancel &amp; Search
                            </button>
                        </div>
                    )}
                    {/* Zone A: Required fields */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                        <CharCountInput
                            label="Full Name"
                            required
                            value={formData.name || ''}
                            onChange={(e) => handleChange('name', e.target.value)}
                            maxLength={50}
                            placeholder="Enter patient's full name"
                            error={errors.name}
                        />
                        <CharCountInput
                            label="Phone Number"
                            required
                            type="tel"
                            value={formData.phone || ''}
                            onChange={(e) => handleChange('phone', e.target.value)}
                            maxLength={15}
                            placeholder="Enter phone number"
                            error={errors.phone}
                        />
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Age <span className="text-red-600">*</span>
                            </label>
                            <input
                                type="number"
                                min="0"
                                max="120"
                                value={formData.age || ''}
                                onChange={(e) => handleChange('age', e.target.value)}
                                className={`input-field ${errors.age ? 'border-error-300 focus:ring-error-500' : ''}`}
                                placeholder="Age"
                            />
                            {errors.age && <p className="text-red-600 text-sm mt-1">{errors.age}</p>}
                        </div>
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Gender <span className="text-red-600">*</span>
                            </label>
                            <div className="flex items-center gap-6 h-11">
                                {['MALE', 'FEMALE', 'OTHER'].map(g => (
                                    <label key={g} className="flex items-center gap-2 cursor-pointer">
                                        <input
                                            type="radio"
                                            name="gender"
                                            value={g}
                                            checked={formData.gender === g}
                                            onChange={() => handleChange('gender', g)}
                                            className="w-4 h-4 accent-blue-600"
                                        />
                                        <span className="text-sm text-neutral-700">
                                            {g.charAt(0) + g.slice(1).toLowerCase()}
                                        </span>
                                    </label>
                                ))}
                            </div>
                            {errors.gender && <p className="text-red-600 text-sm mt-1">{errors.gender}</p>}
                        </div>
                    </div>

                    {/* Zone B: Optional details (collapsible) */}
                    <div>
                        <button
                            type="button"
                            onClick={() => setShowMoreDetails(prev => !prev)}
                            className="flex items-center gap-1.5 text-sm text-blue-600 hover:text-blue-800 font-medium transition-colors"
                        >
                            <svg className={`w-4 h-4 transition-transform ${showMoreDetails ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                            </svg>
                            {showMoreDetails ? 'Hide details' : '+ Add more details'}
                        </button>

                        {showMoreDetails && (
                            <div className="mt-4 space-y-4">
                                <CharCountInput
                                    label="Email Address"
                                    type="email"
                                    value={formData.email || ''}
                                    onChange={(e) => handleChange('email', e.target.value)}
                                    maxLength={50}
                                    placeholder="Enter email address"
                                    error={errors.email}
                                />
                                <CharCountInput
                                    label="Address"
                                    textarea
                                    rows={3}
                                    value={formData.address || ''}
                                    onChange={(e) => handleChange('address', e.target.value)}
                                    maxLength={500}
                                    placeholder="Enter complete address"
                                />
                                <CharCountInput
                                    label="Medical History / Allergies"
                                    textarea
                                    rows={3}
                                    value={formData.medicalHistory || ''}
                                    onChange={(e) => handleChange('medicalHistory', e.target.value)}
                                    maxLength={500}
                                    placeholder="Any medical conditions, allergies, or important notes..."
                                />
                            </div>
                        )}
                    </div>

                    {/* Action Buttons */}
                    <div className="flex gap-4 pt-4">
                        <Button
                            type="button"
                            variant="secondary"
                            onClick={onClose}
                            className="flex-1"
                            disabled={isSubmitting}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="submit"
                            variant="primary"
                            className="flex-1"
                            loading={isSubmitting}
                        >
                            {isEdit ? 'Update Patient' : 'Register Patient'}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default PatientModal;
