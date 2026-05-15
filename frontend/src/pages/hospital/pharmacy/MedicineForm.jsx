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
    medicineCode: '',
    medicineType: '',
    scheduleType: '',
    strength: '',
    unitOfMeasure: '',
    reorderLevel: '0',
    gstPercentage: '0',
    requiresPrescription: true,
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
        medicineCode: initialData.medicineCode || '',
        medicineType: initialData.medicineType || '',
        scheduleType: initialData.scheduleType || '',
        strength: initialData.strength || '',
        unitOfMeasure: initialData.unitOfMeasure || '',
        reorderLevel: initialData.reorderLevel != null ? String(initialData.reorderLevel) : '0',
        gstPercentage: initialData.gstPercentage != null ? String(initialData.gstPercentage) : '0',
        requiresPrescription: initialData.requiresPrescription != null ? initialData.requiresPrescription : true,
        isActive: initialData.isActive != null ? initialData.isActive : true,
      });
    } else {
      // reset for create mode
      setFormData({
        medicineName: '',
        genericName: '',
        categoryId: '',
        manufacturerId: '',
        dosageForm: '',
        medicineCode: '',
        medicineType: '',
        scheduleType: '',
        strength: '',
        unitOfMeasure: '',
        reorderLevel: '0',
        gstPercentage: '0',
        requiresPrescription: true,
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

  // Safe handler for integer inputs
  const handleIntegerChange = (e) => {
    const { name, value } = e.target;
    // Strip everything except digits
    const sanitized = value.replace(/\D/g, '');
    setFormData((prev) => ({
      ...prev,
      [name]: sanitized,
    }));
  };

  // Safe handler for decimal inputs
  const handleDecimalChange = (e) => {
    const { name, value } = e.target;
    // Strip everything except digits and dot
    let sanitized = value.replace(/[^0-9.]/g, '');
    // Ensure only one dot exists
    const parts = sanitized.split('.');
    if (parts.length > 2) {
      sanitized = parts[0] + '.' + parts.slice(1).join('');
    }
    setFormData((prev) => ({
      ...prev,
      [name]: sanitized,
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
        medicineCode: formData.medicineCode.trim() || null,
        medicineType: formData.medicineType.trim() || null,
        scheduleType: formData.scheduleType.trim() || null,
        strength: formData.strength.trim() || null,
        unitOfMeasure: formData.unitOfMeasure.trim() || null,
        reorderLevel: formData.reorderLevel === '' ? 0 : parseInt(formData.reorderLevel, 10),
        gstPercentage: formData.gstPercentage === '' ? 0 : parseFloat(formData.gstPercentage) || 0,
        requiresPrescription: !!formData.requiresPrescription,
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
            
            {/* --- SECTION A: Basic Information --- */}
            <div className="col-span-full mt-2 mb-1 pb-1 border-b border-gray-200 text-sm font-semibold text-gray-600 uppercase tracking-wider">
              Basic Information
            </div>

            {/* Medicine Name */}
            <div>
              <input
                name="medicineName"
                required
                placeholder="Medicine Name"
                value={formData.medicineName}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
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
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              />
              {errors.genericName && (
                <p className="text-xs text-red-600">{errors.genericName}</p>
              )}
            </div>

            {/* Medicine Code */}
            <div>
              <input
                name="medicineCode"
                placeholder="Medicine Code (Unique)"
                value={formData.medicineCode}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              />
            </div>


            {/* --- SECTION B: Classification --- */}
            <div className="col-span-full mt-4 mb-1 pb-1 border-b border-gray-200 text-sm font-semibold text-gray-600 uppercase tracking-wider">
              Classification
            </div>

            {/* Category */}
            <div>
              <select
                name="categoryId"
                required
                value={formData.categoryId}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
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
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              >
                <option value="">Select Manufacturer</option>
                {(Array.isArray(manufacturerOptions) ? manufacturerOptions : []).map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {opt.name || opt.manufacturerName || opt.manufacturer?.name || '-'}
                  </option>
                ))}
              </select>
              {errors.manufacturerId && (
                <p className="text-xs text-red-600 ml-1">{errors.manufacturerId}</p>
              )}
            </div>

            {/* Medicine Type */}
            <div>
              <select
                name="medicineType"
                value={formData.medicineType}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              >
                <option value="">Select Type</option>
                <option value="TABLET">TABLET</option>
                <option value="CAPSULE">CAPSULE</option>
                <option value="SYRUP">SYRUP</option>
                <option value="INJECTION">INJECTION</option>
                <option value="OINTMENT">OINTMENT</option>
              </select>
            </div>

            {/* Schedule Type */}
            <div>
              <select
                name="scheduleType"
                value={formData.scheduleType}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              >
                <option value="">Select Schedule</option>
                <option value="OTC">OTC</option>
                <option value="H">Schedule H</option>
                <option value="H1">Schedule H1</option>
                <option value="X">Schedule X</option>
              </select>
            </div>


            {/* --- SECTION C: Clinical Information --- */}
            <div className="col-span-full mt-4 mb-1 pb-1 border-b border-gray-200 text-sm font-semibold text-gray-600 uppercase tracking-wider">
              Clinical Information
            </div>

            {/* Dosage Form */}
            <div>
              <input
                name="dosageForm"
                placeholder="Dosage Form (e.g. Vial, Strip)"
                value={formData.dosageForm}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              />
            </div>

            {/* Strength */}
            <div>
              <input
                name="strength"
                placeholder="Strength (e.g. 500mg)"
                value={formData.strength}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              />
            </div>

            {/* Unit of Measure */}
            <div>
              <select
                name="unitOfMeasure"
                value={formData.unitOfMeasure}
                onChange={handleChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              >
                <option value="">Select UOM</option>
                <option value="STRIP">STRIP</option>
                <option value="BOTTLE">BOTTLE</option>
                <option value="ML">ML</option>
                <option value="TABLET">TABLET</option>
                <option value="CAPSULE">CAPSULE</option>
                <option value="TUBE">TUBE</option>
                <option value="VIAL">VIAL</option>
              </select>
            </div>

            {/* --- SECTION D: Inventory & Tax --- */}
            <div className="col-span-full mt-4 mb-1 pb-1 border-b border-gray-200 text-sm font-semibold text-gray-600 uppercase tracking-wider">
              Inventory & Tax
            </div>

            {/* Reorder Level */}
            <div>
              <input
                type="text"
                name="reorderLevel"
                placeholder="Reorder Level (e.g. 50)"
                value={formData.reorderLevel}
                onChange={handleIntegerChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              />
            </div>

            {/* GST Percentage */}
            <div>
              <input
                type="text"
                name="gstPercentage"
                placeholder="GST Percentage (e.g. 12.5)"
                value={formData.gstPercentage}
                onChange={handleDecimalChange}
                className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:border-gray-900 transition-all"
              />
            </div>

            {/* --- SECTION E: Compliance --- */}
            <div className="col-span-full mt-4 mb-1 pb-1 border-b border-gray-200 text-sm font-semibold text-gray-600 uppercase tracking-wider">
              Compliance
            </div>

            {/* Prescription toggle */}
            <div className="col-span-1 sm:col-span-1 flex items-center mt-2">
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  name="requiresPrescription"
                  checked={formData.requiresPrescription}
                  onChange={handleChange}
                  className="h-4 w-4 text-gray-900 border-gray-300 rounded focus:ring-gray-900"
                />
                <span className="text-sm font-medium">Requires Prescription (Rx)</span>
              </label>
            </div>

            {/* Active toggle */}
            <div className="col-span-1 sm:col-span-1 flex items-center mt-2">
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  name="isActive"
                  checked={formData.isActive}
                  onChange={handleChange}
                  className="h-4 w-4 text-gray-900 border-gray-300 rounded focus:ring-gray-900"
                />
                <span className="text-sm font-medium">Active Status</span>
              </label>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex justify-end space-x-3 mt-6 pt-4 border-t border-gray-100">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border border-gray-300 rounded text-sm hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 bg-gray-900 text-white rounded text-sm font-medium hover:bg-gray-800 transition-colors disabled:opacity-50"
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
