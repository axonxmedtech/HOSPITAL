import React, { useState, useEffect } from 'react';
import suppliersApi from '../services/pharmacy/suppliersApi';
import { useToast } from '../context/ToastContext';

/**
 * SupplierForm – modal used for both adding and editing a supplier.
 *
 * Props:
 *   - isOpen: boolean – controls modal visibility.
 *   - onClose: function – called to close the modal.
 *   - onSuccess: function – called after a successful create or update (e.g., refresh list).
 *   - mode: 'create' | 'edit' – determines whether we are creating or editing.
 *   - supplier: object – the supplier data to pre‑fill when mode === 'edit'.
 */
const SupplierForm = ({
  isOpen = false,
  onClose,
  onSuccess,
  mode = 'create',
  supplier = null,
}) => {
  const { success, error: toastError } = useToast();
  const [formData, setFormData] = useState({
    supplierName: '',
    contactPerson: '',
    phone: '',
    email: '',
    address: '',
    gstNumber: '',
    drugLicenseNumber: '',
    creditDays: '',
    isActive: true,
  });
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Populate form when entering edit mode or when the modal is opened anew.
  useEffect(() => {
    if (!isOpen) return;
    if (mode === 'edit' && supplier) {
      setFormData({
        supplierName: supplier.supplierName || '',
        contactPerson: supplier.contactPerson || '',
        phone: supplier.phone || '',
        email: supplier.email || '',
        address: supplier.address || '',
        gstNumber: supplier.gstNumber || '',
        drugLicenseNumber: supplier.drugLicenseNumber || '',
        creditDays: supplier.creditDays != null ? supplier.creditDays : '',
        isActive: supplier.isActive != null ? supplier.isActive : true,
      });
    } else {
      // reset to blanks for create mode
      setFormData({
        supplierName: '',
        contactPerson: '',
        phone: '',
        email: '',
        address: '',
        gstNumber: '',
        drugLicenseNumber: '',
        creditDays: '',
        isActive: true,
      });
    }
  }, [mode, supplier, isOpen]);

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      if (mode === 'create') {
        await suppliersApi.create(formData);
        success('Supplier added successfully');
      } else {
        await suppliersApi.update(supplier.id, formData);
        success('Supplier updated successfully');
      }
      if (onSuccess) onSuccess();
      if (onClose) onClose();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to save supplier';
      toastError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const modalTitle = mode === 'create' ? 'Add Supplier' : 'Edit Supplier';
  const submitLabel = mode === 'create' ? (isSubmitting ? 'Saving...' : 'Save') : (isSubmitting ? 'Updating...' : 'Update');

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-lg w-full max-w-2xl max-h-[90vh] overflow-auto p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-2xl font-bold mb-4">{modalTitle}</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <input
              name="supplierName"
              required
              placeholder="Supplier Name"
              value={formData.supplierName}
              onChange={handleChange}
              className="input-field"
            />
            <input
              name="contactPerson"
              placeholder="Contact Person"
              value={formData.contactPerson}
              onChange={handleChange}
              className="input-field"
            />
            <input
              name="phone"
              placeholder="Phone"
              value={formData.phone}
              onChange={handleChange}
              className="input-field"
            />
            <input
              name="email"
              type="email"
              placeholder="Email"
              value={formData.email}
              onChange={handleChange}
              className="input-field"
            />
          </div>
          <input
            name="address"
            placeholder="Address"
            value={formData.address}
            onChange={handleChange}
            className="input-field w-full"
          />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <input
              name="gstNumber"
              placeholder="GST Number"
              value={formData.gstNumber}
              onChange={handleChange}
              className="input-field"
            />
            <input
              name="drugLicenseNumber"
              placeholder="Drug License No."
              value={formData.drugLicenseNumber}
              onChange={handleChange}
              className="input-field"
            />
            <input
              name="creditDays"
              type="number"
              placeholder="Credit Days"
              value={formData.creditDays}
              onChange={handleChange}
              className="input-field"
            />
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

export default SupplierForm;
