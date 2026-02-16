import React, { useState, useEffect } from 'react';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';
import { validateForm } from '../utils/validation';
import Button from './Button';

const PatientModal = ({ isOpen, onClose, onSuccess, initialData }) => {
    const [formData, setFormData] = useState({});
    const [errors, setErrors] = useState({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const { success, error: toastError } = useToast();
    const isEdit = !!initialData;

    useEffect(() => {
        if (isOpen) {
            if (initialData) {
                setFormData(initialData);
            } else {
                setFormData({});
            }
            setErrors({});
            setIsSubmitting(false);
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
            if (isEdit) {
                await hospitalService.updatePatient(formData.id, formData);
                success('Patient updated successfully');
                console.log('[PatientModal] Patient updated');
            } else {
                const result = await hospitalService.addPatient(formData);
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
                    {/* Row: Name + Phone */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Full Name <span className="text-error-500">*</span>
                            </label>
                            <input
                                type="text"
                                value={formData.name || ''}
                                onChange={(e) => handleChange('name', e.target.value)}
                                className={`input-field ${errors.name ? 'border-error-300 focus:ring-error-500' : ''}`}
                                placeholder="Enter patient's full name"
                            />
                            {errors.name && <p className="text-red-600 text-sm mt-1 flex items-center gap-1">
                                {errors.name}
                            </p>}
                        </div>

                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Phone Number <span className="text-error-500">*</span>
                            </label>
                            <input
                                type="tel"
                                value={formData.phone || ''}
                                onChange={(e) => handleChange('phone', e.target.value)}
                                className={`input-field ${errors.phone ? 'border-error-300 focus:ring-error-500' : ''}`}
                                placeholder="Enter phone number"
                            />
                            {errors.phone && <p className="text-red-600 text-sm mt-1 flex items-center gap-1">
                                {errors.phone}
                            </p>}
                        </div>
                    </div>

                    {/* Row: Age + Gender */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Age <span className="text-error-500">*</span>
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
                            {errors.age && <p className="text-red-600 text-sm mt-1 flex items-center gap-1">
                                {errors.age}
                            </p>}
                        </div>
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Gender <span className="text-error-500">*</span>
                            </label>
                            <select
                                value={formData.gender || ''}
                                onChange={(e) => handleChange('gender', e.target.value)}
                                className={`input-field ${errors.gender ? 'border-error-300 focus:ring-error-500' : ''}`}
                            >
                                <option value="">Select gender</option>
                                <option value="MALE">Male</option>
                                <option value="FEMALE">Female</option>
                                <option value="OTHER">Other</option>
                            </select>
                            {errors.gender && <p className="text-red-600 text-sm mt-1 flex items-center gap-1">
                                {errors.gender}
                            </p>}
                        </div>
                    </div>

                    {/* Row: Email + (Address will be full width) */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Email Address <span className="text-neutral-400 text-xs">(Optional)</span>
                            </label>
                            <input
                                type="email"
                                value={formData.email || ''}
                                onChange={(e) => handleChange('email', e.target.value)}
                                className={`input-field ${errors.email ? 'border-error-300 focus:ring-error-500' : ''}`}
                                placeholder="Enter email address"
                            />
                            {errors.email && <p className="text-red-600 text-sm mt-1 flex items-center gap-1">
                                {errors.email}
                            </p>}
                        </div>
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Address <span className="text-neutral-400 text-xs">(Optional)</span>
                            </label>
                            <textarea
                                value={formData.address || ''}
                                onChange={(e) => handleChange('address', e.target.value)}
                                className="input-field resize-none"
                                rows="3"
                                placeholder="Enter complete address"
                            />
                        </div>
                    </div>

                    {/* Medical History */}
                    <div>
                        <label className="block text-sm font-semibold text-neutral-700 mb-2">
                            Medical History / Allergies <span className="text-neutral-400 text-xs">(Optional)</span>
                        </label>
                        <textarea
                            value={formData.medicalHistory || ''}
                            onChange={(e) => handleChange('medicalHistory', e.target.value)}
                            className="input-field resize-none"
                            rows="2"
                            placeholder="Any medical conditions, allergies, or important notes..."
                        />
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
                            {isEdit ? 'Update Patient' : 'Save Patient'}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default PatientModal;
