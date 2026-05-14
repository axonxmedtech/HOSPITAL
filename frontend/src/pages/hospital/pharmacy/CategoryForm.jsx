import React, { useState, useEffect } from 'react';

/**
 * CategoryForm – pure UI component for adding/editing medicine categories.
 * 
 * Props:
 *   - isOpen: boolean - controls visibility
 *   - onClose: function - called when cancel button is clicked
 *   - onSubmit: function - called with validated formData
 *   - isSubmitting: boolean - indicates parent is performing async operation
 *   - mode: 'create' | 'edit' - UI title and button label
 *   - initialData: object | null - pre-fill data for edit mode
 */
const CategoryForm = ({
  isOpen = false,
  onClose,
  onSubmit,
  isSubmitting = false,
  mode = 'create',
  initialData = null,
}) => {
  const [formData, setFormData] = useState({
    categoryName: '',
    description: '',
    isActive: true,
  });

  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (!isOpen) return;

    if (mode === 'edit' && initialData) {
      setFormData({
        categoryName: initialData.categoryName || '',
        description: initialData.description || '',
        isActive: initialData.isActive !== false,
      });
    } else {
      setFormData({
        categoryName: '',
        description: '',
        isActive: true,
      });
    }
    setErrors({});
  }, [isOpen, mode, initialData]);

  if (!isOpen) return null;

  const validate = () => {
    const newErrors = {};
    if (!formData.categoryName.trim()) {
      newErrors.categoryName = 'Category name is required';
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

  const title = mode === 'edit' ? 'Edit Category' : 'Add Category';
  const submitBtnLabel = mode === 'edit' 
    ? (isSubmitting ? 'Updating...' : 'Update Category') 
    : (isSubmitting ? 'Saving...' : 'Save Category');

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div 
        className="bg-white rounded-2xl shadow-xl w-full max-w-lg overflow-hidden"
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
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Category Name */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1">
              Category Name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              name="categoryName"
              value={formData.categoryName}
              onChange={handleChange}
              placeholder="e.g. Antibiotics, Painkillers"
              className={`w-full px-4 py-2 border rounded-lg outline-none transition-all focus:ring-2 ${
                errors.categoryName 
                  ? 'border-red-300 focus:ring-red-100' 
                  : 'border-gray-200 focus:border-gray-900 focus:ring-gray-100'
              }`}
              disabled={isSubmitting}
            />
            {errors.categoryName && (
              <p className="mt-1 text-xs text-red-600 font-medium">{errors.categoryName}</p>
            )}
          </div>

          {/* Description */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1">
              Description
            </label>
            <textarea
              name="description"
              value={formData.description}
              onChange={handleChange}
              rows="3"
              placeholder="Enter category details..."
              className="w-full px-4 py-2 border border-gray-200 rounded-lg outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-100 transition-all resize-none"
              disabled={isSubmitting}
            />
          </div>

          {/* Active Status */}
          <div className="flex items-center space-x-2 pt-2">
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
              Mark as Active
            </label>
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

export default CategoryForm;
