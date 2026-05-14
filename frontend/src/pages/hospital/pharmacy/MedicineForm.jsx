// frontend/src/pages/hospital/pharmacy/MedicineForm.jsx

import React, { useState, useEffect } from 'react';
import { useToast } from '../../../context/ToastContext';


/**
 * MedicineForm – modal used for both adding and editing a medicine.
 *
 * Props:
 *   - isOpen: boolean – controls modal visibility.
 *   - onClose: function – called to close the modal.
 *   - onSave: function – called with form data when the user submits.
 *   - mode: 'create' | 'edit' – determines the dialog title / button label.
 *   - initialData: object – pre‑fill values when editing (null for create).
 *   - categoryOptions: array – [{ id, name }…] for the Category dropdown.
 *   - manufacturerOptions: array – [{ id, name }…] for the Manufacturer dropdown.
 */
const MedicineForm = ({
  isOpen = false,
  onClose,
  onSave,
  mode = 'create',
  initialData = null,
  categoryOptions = [],
  manufacturerOptions = [],
}) => {
  const toast = useToast();

  // -----------------------------------------------------------------
  // Local form state
  // -----------------------------------------------------------------
  const [formData, setFormData] = useState({
    medicineName: '',
    genericName: '',
    categoryId: '',
    manufacturerId: '',
    dosageForm: '',
    isActive: true,
  });

  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);


  // Populate form when entering edit mode or when the modal opens
  useEffect(() => {
    if (!isOpen) return;
    if (mode === 'edit' && initialData) {
      setFormData({
        medicineName: initialData.medicineName || '',
        genericName: initialData.genericName || '',
        categoryId: initialData.categoryId || '',
        manufacturerId: initialData.manufacturerId || '',
        dosageForm: initialData.dosageForm || '',

        isActive:
          initialData.isActive != null ? initialData.isActive : true,
      });
    } else {
      // reset for create mode
      setFormData({
        medicineName: '',
        genericName: '',
        categoryId: '',
        manufacturerId: '',
        dosageForm: '',

        isActive: true,
      });
    }
    setErrors({});
  }, [mode, initialData, isOpen]);

  if (!isOpen) return null;

  // -----------------------------------------------------------------
  // Handlers
  // -----------------------------------------------------------------

  const validate = () => {
    const newErrors = {};
    if (!formData.medicineName.trim()) newErrors.medicineName = 'Required';
    if (!formData.categoryId) newErrors.categoryId = 'Select a category';
    if (!formData.manufacturerId)
      newErrors.manufacturerId = 'Select a manufacturer';

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // Handle input changes for both text and checkbox fields
  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setIsSubmitting(true);
    try {
      const payload = {
        medicineName: formData.medicineName.trim(),
        genericName: formData.genericName.trim() || null,
        categoryId: formData.categoryId ? Number(formData.categoryId) : null,
        manufacturerId: formData.manufacturerId ? Number(formData.manufacturerId) : null,
        dosageForm: formData.dosageForm.trim() || null,
        isActive: !!formData.isActive,
      };
      console.log('Submitting payload:', payload);
      await onSave(payload);
    } catch (err) {
      console.error('SAVE ERROR', err);
      const message = err?.response?.data?.message || err.message || 'Failed to save medicine';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };



  const modalTitle = mode === 'create' ? 'Add Medicine' : 'Edit Medicine';
  const submitLabel =
    mode === 'create'
      ? isSubmitting
        ? 'Saving...'
        : 'Save'
      : isSubmitting
        ? 'Updating...'
        : 'Update';

  // -----------------------------------------------------------------
  // Render
  // -----------------------------------------------------------------
  return (
    <div
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl shadow-lg w-full max-w-2xl max-h-[90vh] overflow-auto p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-2xl font-bold mb-4">{modalTitle}</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Medicine Name */}
            <div>
              <input
                name="medicineName"
                required
                placeholder="Medicine Name"
                value={formData.medicineName}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-primary-600"
              />
              {errors.medicineName && (
                <p className="text-xs text-red-600">{errors.medicineName}</p>
              )}
            </div>
            {/* Generic Name */}
            <div>
              <input
                name="genericName"
                placeholder="Generic Name"
                value={formData.genericName}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-primary-600"
              />
              {errors.genericName && (
                <p className="text-xs text-red-600">{errors.genericName}</p>
              )}
            </div>
            {/* Category */}
            <div>
              <select
                name="categoryId"
                required
                value={formData.categoryId}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-primary-600"
              >
                <option value="">Select Category</option>
                {(Array.isArray(categoryOptions) ? categoryOptions : []).map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {opt.name || opt.categoryName || opt.category?.name || '-'}
                  </option>
                ))}
              </select>
              {errors.categoryId && (
                <p className="text-xs text-red-600 ml-1">{errors.categoryId}</p>
              )}
            </div>
            {/* Manufacturer */}
            <div>
              <select
                name="manufacturerId"
                required
                value={formData.manufacturerId}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-primary-600"
              >
                <option value="">Select Manufacturer</option>
                {(Array.isArray(manufacturerOptions) ? manufacturerOptions : []).map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {opt.name ||
                      opt.manufacturerName ||
                      opt.manufacturer?.name ||
                      '-'}
                  </option>
                ))}
              </select>
              {errors.manufacturerId && (
                <p className="text-xs text-red-600 ml-1">{errors.manufacturerId}</p>
              )}
            </div>
            {/* Dosage Form */}
            <div>
              <input
                name="dosageForm"
                placeholder="Dosage Form"
                value={formData.dosageForm}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-primary-600"
              />
            </div>

            {/* Active toggle */}
            <div className="flex items-center">
              <label className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  name="isActive"
                  checked={formData.isActive}
                  onChange={handleChange}
                  className="h-4 w-4 text-primary-600 border-gray-300 rounded"
                />
                <span className="text-sm">Active</span>
              </label>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex justify-end space-x-3 mt-4">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border border-gray-300 rounded text-sm hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 bg-gray-900 text-white rounded text-sm font-medium hover:bg-gray-800 disabled:opacity-50"
            >
              {submitLabel}
            </button>
          </div>
        </form>


      </div>
    </div>
  );
};

export default MedicineForm;
