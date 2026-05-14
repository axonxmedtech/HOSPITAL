import React, { useState, useEffect } from 'react';

/**
 * ManufacturerForm – pure UI component for adding/editing medicine manufacturers.
 * 
 * Props:
 *   - isOpen: boolean - controls visibility
 *   - onClose: function - called when cancel/close button is clicked
 *   - onSubmit: function - called with validated formData
 *   - isSubmitting: boolean - indicates parent is performing async operation
 *   - mode: 'create' | 'edit' - UI title and button label
 *   - initialData: object | null - pre-fill data for edit mode
 */
const ManufacturerForm = ({
  isOpen = false,
  onClose,
  onSubmit,
  isSubmitting = false,
  mode = 'create',
  initialData = null,
}) => {
  const [formData, setFormData] = useState({
    manufacturerName: '',
    contactPerson: '',
    phone: '',
    email: '',
    address: '',
    licenseNumber: '',
    isActive: true,
  });

  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (!isOpen) return;

    if (mode === 'edit' && initialData) {
      setFormData({
        manufacturerName: initialData.manufacturerName || '',
        contactPerson: initialData.contactPerson || '',
        phone: initialData.phone || '',
        email: initialData.email || '',
        address: initialData.address || '',
        licenseNumber: initialData.licenseNumber || '',
        isActive: initialData.isActive !== false,
      });
    } else {
      setFormData({
        manufacturerName: '',
        contactPerson: '',
        phone: '',
        email: '',
        address: '',
        licenseNumber: '',
        isActive: true,
      });
    }
    setErrors({});
  }, [isOpen, mode, initialData]);

  if (!isOpen) return null;

  const validate = () => {
    const newErrors = {};
    if (!formData.manufacturerName.trim()) {
      newErrors.manufacturerName = 'Manufacturer name is required';
    }
    return newErrors;
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validationErrors = validate();
    setErrors(validationErrors);

    if (Object.keys(validationErrors).length === 0) {
      onSubmit(formData);
    }
  };

  const title = mode === 'edit' ? 'Edit Manufacturer' : 'Add Manufacturer';
  const submitBtnLabel = mode === 'edit' 
    ? (isSubmitting ? 'Updating...' : 'Update Manufacturer') 
    : (isSubmitting ? 'Saving...' : 'Save Manufacturer');

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div 
        className="bg-white rounded-2xl shadow-xl w-full max-w-2xl overflow-hidden my-8"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <h3 className="text-xl font-bold text-gray-900">{title}</h3>
          <button 
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Manufacturer Name */}
            <div className="md:col-span-2">
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Manufacturer Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="manufacturerName"
                value={formData.manufacturerName}
                onChange={handleChange}
                placeholder="e.g. Cipla Ltd, Sun Pharma"
                className={`w-full px-4 py-2 border rounded-lg outline-none transition-all focus:ring-2 ${
                  errors.manufacturerName 
                    ? 'border-red-300 focus:ring-red-100' 
                    : 'border-gray-200 focus:border-gray-900 focus:ring-gray-100'
                }`}
                disabled={isSubmitting}
              />
              {errors.manufacturerName && (
                <p className="mt-1 text-xs text-red-600 font-medium">{errors.manufacturerName}</p>
              )}
            </div>

            {/* Contact Person */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Contact Person
              </label>
              <input
                type="text"
                name="contactPerson"
                value={formData.contactPerson}
                onChange={handleChange}
                placeholder="Manager Name"
                className="w-full px-4 py-2 border border-gray-200 rounded-lg outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-100 transition-all"
                disabled={isSubmitting}
              />
            </div>

            {/* Phone */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Phone Number
              </label>
              <input
                type="text"
                name="phone"
                value={formData.phone}
                onChange={handleChange}
                placeholder="+91 XXXXXXXXXX"
                className="w-full px-4 py-2 border border-gray-200 rounded-lg outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-100 transition-all"
                disabled={isSubmitting}
              />
            </div>

            {/* Email */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Email Address
              </label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                placeholder="contact@manufacturer.com"
                className="w-full px-4 py-2 border border-gray-200 rounded-lg outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-100 transition-all"
                disabled={isSubmitting}
              />
            </div>

            {/* License Number */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                License Number
              </label>
              <input
                type="text"
                name="licenseNumber"
                value={formData.licenseNumber}
                onChange={handleChange}
                placeholder="e.g. DL-123456"
                className="w-full px-4 py-2 border border-gray-200 rounded-lg outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-100 transition-all"
                disabled={isSubmitting}
              />
            </div>

            {/* Address */}
            <div className="md:col-span-2">
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Address
              </label>
              <textarea
                name="address"
                value={formData.address}
                onChange={handleChange}
                rows="2"
                placeholder="Full manufacturer address..."
                className="w-full px-4 py-2 border border-gray-200 rounded-lg outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-100 transition-all resize-none"
                disabled={isSubmitting}
              />
            </div>

            {/* Active Status */}
            <div className="md:col-span-2 flex items-center space-x-2 pt-2">
              <input
                type="checkbox"
                id="isActive"
                name="isActive"
                checked={formData.isActive}
                onChange={handleChange}
                className="w-4 h-4 text-gray-900 border-gray-300 rounded focus:ring-gray-900"
                disabled={isSubmitting}
              />
              <label htmlFor="isActive" className="text-sm font-medium text-gray-700 cursor-pointer">
                Mark as Active Manufacturer
              </label>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end space-x-3 pt-4 border-t border-gray-100 mt-6">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-semibold text-gray-600 hover:text-gray-900 hover:bg-gray-50 rounded-lg transition-all"
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-6 py-2 bg-gray-900 text-white text-sm font-bold rounded-lg hover:bg-gray-800 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-sm"
            >
              {submitBtnLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ManufacturerForm;
