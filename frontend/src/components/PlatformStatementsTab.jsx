import React, { useState, useEffect, useCallback } from 'react';
import { useToast } from '../context/ToastContext';
import platformService from '../services/platformService';

const extractError = (err, fallback) => {
  const d = err?.response?.data;
  if (!d) return fallback;
  if (typeof d === 'string') return d;
  if (d.errors && typeof d.errors === 'object') {
    return Object.values(d.errors).join(', ');
  }
  return d.message || d.error || d.detail || fallback;
};

export default function PlatformStatementsTab({ hospitalType = 'HOSPITAL' }) {
  const { success, error: toastError } = useToast();
  const [statements, setStatements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [editingStatement, setEditingStatement] = useState(null);
  const [formData, setFormData] = useState({
    category: 'MEDICINE_INSTRUCTION',
    englishText: '',
    marathiText: '',
    hindiText: '',
    displayOrder: 0,
    isActive: true,
  });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Delete confirm modal state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const loadStatements = useCallback(async () => {
    setLoading(true);
    try {
      const data = await platformService.getStatements(hospitalType || 'HOSPITAL');
      setStatements(Array.isArray(data) ? data : []);
    } catch (err) {
      toastError(extractError(err, 'Failed to load consultation statements.'));
    } finally {
      setLoading(false);
    }
  }, [hospitalType, toastError]);

  useEffect(() => {
    loadStatements();
  }, [loadStatements]);

  const openCreateModal = () => {
    setEditingStatement(null);
    setFormData({
      category: 'MEDICINE_INSTRUCTION',
      englishText: '',
      marathiText: '',
      hindiText: '',
      displayOrder: (statements.length + 1) * 10,
      isActive: true,
    });
    setError('');
    setShowModal(true);
  };

  const openEditModal = (stmt) => {
    setEditingStatement(stmt);
    setFormData({
      category: stmt.category || 'MEDICINE_INSTRUCTION',
      englishText: stmt.englishText || '',
      marathiText: stmt.marathiText || '',
      hindiText: stmt.hindiText || '',
      displayOrder: stmt.displayOrder ?? 0,
      isActive: stmt.isActive !== false,
    });
    setError('');
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.englishText.trim()) {
      setError('English text is required');
      return;
    }
    if (!formData.marathiText.trim()) {
      setError('Marathi text is required');
      return;
    }
    if (!formData.hindiText.trim()) {
      setError('Hindi text is required');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      const payload = {
        category: formData.category,
        englishText: formData.englishText.trim(),
        marathiText: formData.marathiText.trim(),
        hindiText: formData.hindiText.trim(),
        displayOrder: Number(formData.displayOrder) || 0,
        isActive: formData.isActive,
      };

      if (editingStatement) {
        await platformService.updateStatement(editingStatement.id, payload, hospitalType);
        success('Statement updated successfully');
      } else {
        await platformService.createStatement(payload, hospitalType);
        success('Statement created successfully');
      }
      setShowModal(false);
      loadStatements();
    } catch (err) {
      setError(extractError(err, 'Failed to save statement.'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await platformService.deleteStatement(deleteTarget.id, hospitalType);
      success('Statement deleted successfully');
      setDeleteTarget(null);
      loadStatements();
    } catch (err) {
      toastError(extractError(err, 'Failed to delete statement.'));
    } finally {
      setDeleting(false);
    }
  };

  const filteredStatements = statements.filter((s) => {
    const matchesCategory = !categoryFilter || s.category === categoryFilter;
    const q = search.trim().toLowerCase();
    const matchesSearch =
      !q ||
      s.englishText?.toLowerCase().includes(q) ||
      s.marathiText?.toLowerCase().includes(q) ||
      s.hindiText?.toLowerCase().includes(q);
    return matchesCategory && matchesSearch;
  });

  return (
    <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
      {/* Action Bar */}
      <div className="p-4 sm:p-6 border-b border-gray-100 flex flex-col sm:flex-row items-center justify-between gap-4 bg-gray-50/50">
        <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto">
          {/* Search */}
          <div className="relative w-full sm:w-64">
            <input
              type="text"
              aria-label="Search instructions"
              placeholder="Search instructions..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            />
            <svg
              className="w-4 h-4 text-gray-400 absolute left-3 top-2.5"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              />
            </svg>
          </div>

          {/* Category Filter */}
          <select
            aria-label="Filter by category"
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
          >
            <option value="">All Categories</option>
            <option value="MEDICINE_INSTRUCTION">Medicine Instruction</option>
            <option value="DOCTOR_ADVICE">Doctor Advice</option>
          </select>
        </div>

        <button
          onClick={openCreateModal}
          className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add Statement
        </button>
      </div>

      {/* Table */}
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
          <thead className="bg-gray-50 text-gray-600 font-semibold text-xs uppercase tracking-wider">
            <tr>
              <th className="py-3 px-4">Category</th>
              <th className="py-3 px-4">English Text</th>
              <th className="py-3 px-4">Marathi Text (मराठी)</th>
              <th className="py-3 px-4">Hindi Text (हिंदी)</th>
              <th className="py-3 px-4 text-center">Order</th>
              <th className="py-3 px-4 text-center">Status</th>
              <th className="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {loading ? (
              <tr>
                <td colSpan="7" className="py-12 text-center text-gray-500">
                  <div className="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600 mb-2"></div>
                  <p>Loading consultation statements...</p>
                </td>
              </tr>
            ) : filteredStatements.length === 0 ? (
              <tr>
                <td colSpan="7" className="py-12 text-center text-gray-500">
                  <p className="font-medium text-gray-700">No statements found</p>
                  <p className="text-xs text-gray-400 mt-1">
                    Add standard medicine instructions and doctor advice for patient prescriptions.
                  </p>
                </td>
              </tr>
            ) : (
              filteredStatements.map((stmt) => (
                <tr key={stmt.id} className="hover:bg-gray-50/80 transition-colors">
                  <td className="py-3 px-4 whitespace-nowrap">
                    <span
                      className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                        stmt.category === 'MEDICINE_INSTRUCTION'
                          ? 'bg-blue-100 text-blue-800 border border-blue-200'
                          : 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                      }`}
                    >
                      {stmt.category === 'MEDICINE_INSTRUCTION'
                        ? 'Medicine Instruction'
                        : 'Doctor Advice'}
                    </span>
                  </td>
                  <td className="py-3 px-4 font-medium text-gray-900 max-w-xs truncate">
                    {stmt.englishText}
                  </td>
                  <td className="py-3 px-4 text-gray-800 max-w-xs truncate font-devanagari">
                    {stmt.marathiText}
                  </td>
                  <td className="py-3 px-4 text-gray-800 max-w-xs truncate font-devanagari">
                    {stmt.hindiText}
                  </td>
                  <td className="py-3 px-4 text-center text-gray-500">{stmt.displayOrder ?? 0}</td>
                  <td className="py-3 px-4 text-center whitespace-nowrap">
                    <span
                      className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                        stmt.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                      }`}
                    >
                      {stmt.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right whitespace-nowrap">
                    <div className="inline-flex items-center gap-2">
                      <button
                        onClick={() => openEditModal(stmt)}
                        className="text-blue-600 hover:text-blue-800 text-xs font-medium px-2 py-1 rounded hover:bg-blue-50 transition-colors"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => setDeleteTarget(stmt)}
                        className="text-red-600 hover:text-red-800 text-xs font-medium px-2 py-1 rounded hover:bg-red-50 transition-colors"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Add / Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl overflow-hidden border border-gray-100 animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
              <h3 className="font-bold text-gray-900 text-lg">
                {editingStatement ? 'Edit Statement' : 'Add Consultation Statement'}
              </h3>
              <button
                onClick={() => setShowModal(false)}
                className="text-gray-400 hover:text-gray-600 rounded-lg p-1 hover:bg-gray-100 transition-colors"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              {error && (
                <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg">
                  {error}
                </div>
              )}

              <div>
                <label
                  htmlFor="stmt-category"
                  className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                >
                  Category *
                </label>
                <select
                  id="stmt-category"
                  value={formData.category}
                  onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="MEDICINE_INSTRUCTION">
                    Medicine Instruction (e.g. Take with water)
                  </option>
                  <option value="DOCTOR_ADVICE">Doctor Advice / Quick Notes</option>
                </select>
              </div>

              <div>
                <label
                  htmlFor="stmt-english-text"
                  className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                >
                  English Text *
                </label>
                <input
                  id="stmt-english-text"
                  type="text"
                  required
                  value={formData.englishText}
                  onChange={(e) => setFormData({ ...formData, englishText: e.target.value })}
                  placeholder="e.g. Take with warm water"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label
                  htmlFor="stmt-marathi-text"
                  className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                >
                  Marathi Translation (मराठी Devanagari) *
                </label>
                <input
                  id="stmt-marathi-text"
                  type="text"
                  required
                  value={formData.marathiText}
                  onChange={(e) => setFormData({ ...formData, marathiText: e.target.value })}
                  placeholder="उदा. कोमट पाण्यासोबत घ्या"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-devanagari"
                />
              </div>

              <div>
                <label
                  htmlFor="stmt-hindi-text"
                  className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                >
                  Hindi Translation (हिंदी Devanagari) *
                </label>
                <input
                  id="stmt-hindi-text"
                  type="text"
                  required
                  value={formData.hindiText}
                  onChange={(e) => setFormData({ ...formData, hindiText: e.target.value })}
                  placeholder="उदा. गुनगुने पानी के साथ लें"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-devanagari"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label
                    htmlFor="stmt-display-order"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Display Order
                  </label>
                  <input
                    id="stmt-display-order"
                    type="number"
                    value={formData.displayOrder}
                    onChange={(e) => setFormData({ ...formData, displayOrder: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div className="flex items-center pt-5">
                  <label
                    htmlFor="stmt-is-active"
                    className="inline-flex items-center gap-2 cursor-pointer text-sm font-medium text-gray-700"
                  >
                    <input
                      id="stmt-is-active"
                      type="checkbox"
                      checked={formData.isActive}
                      onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                      className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 h-4 w-4"
                    />
                    Active
                  </label>
                </div>
              </div>

              <div className="pt-4 border-t border-gray-100 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-semibold rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors disabled:opacity-50"
                >
                  {submitting ? 'Saving...' : editingStatement ? 'Update' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 shadow-2xl text-center">
            <div className="w-12 h-12 rounded-full bg-red-100 text-red-600 flex items-center justify-center mx-auto mb-4">
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                />
              </svg>
            </div>
            <h4 className="text-base font-bold text-gray-900 mb-1">Delete Statement</h4>
            <p className="text-xs text-gray-500 mb-6">
              Are you sure you want to delete &ldquo;{deleteTarget.englishText}&rdquo;? Doctors will
              no longer see this quick option.
            </p>
            <div className="flex items-center justify-center gap-3">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="px-4 py-2 border border-gray-300 text-gray-700 text-xs font-semibold rounded-lg hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors disabled:opacity-50"
              >
                {deleting ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
