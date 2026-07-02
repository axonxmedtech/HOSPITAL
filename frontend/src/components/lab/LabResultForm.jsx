import { useState } from 'react';
import { enterLabResult } from '../../services/labService';

// Default empty parameter row
const EMPTY_PARAM = { name: '', value: '', unit: '', referenceRange: '', flag: 'Normal' };

const FLAG_COLORS = {
  Normal: 'text-emerald-700 bg-emerald-50',
  Low: 'text-amber-700 bg-amber-50',
  High: 'text-orange-700 bg-orange-50',
  Critical: 'text-red-700 bg-red-50',
};

/**
 * LabResultForm — Modal for entering lab test results.
 * Renders a dynamic parameter table (name | value | unit | ref range | flag).
 * Parameters serialized to JSON on submit.
 *
 * Props:
 *   publicId   — public ID of the lab order
 *   onClose    — called when user dismisses without submitting
 *   onSuccess  — called after successful result submission
 */
export default function LabResultForm({ publicId, onClose, onSuccess }) {
  const [params, setParams] = useState([{ ...EMPTY_PARAM }]);
  const [summary, setSummary] = useState('');
  const [isAbnormal, setIsAbnormal] = useState(false);
  const [isCritical, setIsCritical] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const updateParam = (idx, field, value) => {
    setParams(prev => prev.map((p, i) => i === idx ? { ...p, [field]: value } : p));
  };

  const addParam = () => setParams(prev => [...prev, { ...EMPTY_PARAM }]);
  const removeParam = (idx) => setParams(prev => prev.filter((_, i) => i !== idx));

  const handleSubmit = async (e) => {
    e.preventDefault();
    // Auto-detect abnormal: any non-Normal flag
    const hasAbnormal = params.some(p => p.flag && p.flag !== 'Normal');
    setSubmitting(true);
    setError('');
    try {
      const hasCritical = params.some(p => p.flag === 'Critical');
      await enterLabResult(publicId, {
        parameters: JSON.stringify(params),
        resultSummary: summary,
        isAbnormal: isAbnormal || hasAbnormal,
        isCritical: isCritical || hasCritical,
      });
      onSuccess();
    } catch (err) {
      setError(err.response?.data || 'Failed to submit result. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[92vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div>
            <h2 className="text-lg font-bold text-gray-900">📋 Enter Lab Result</h2>
            <p className="text-xs text-gray-500 mt-0.5">Fill in each test parameter and mark abnormal values</p>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Body — scrollable */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
          {/* Parameter table */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-gray-700">Test Parameters</h3>
              <button
                type="button"
                onClick={addParam}
                className="text-xs px-3 py-1.5 bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors font-medium border border-blue-100"
              >
                + Add Row
              </button>
            </div>

            <div className="border border-gray-200 rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50">
                  <tr>
                    {['Parameter', 'Value', 'Unit', 'Ref Range', 'Flag', ''].map(h => (
                      <th key={h} className="px-3 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {params.map((p, idx) => (
                    <tr key={idx} className={p.flag && p.flag !== 'Normal' ? 'bg-amber-50/50' : 'bg-white'}>
                      <td className="px-2 py-2">
                        <input
                          value={p.name}
                          onChange={e => updateParam(idx, 'name', e.target.value)}
                          className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300"
                          placeholder="Hemoglobin"
                        />
                      </td>
                      <td className="px-2 py-2">
                        <input
                          value={p.value}
                          onChange={e => updateParam(idx, 'value', e.target.value)}
                          className="w-20 border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                          placeholder="13.5"
                        />
                      </td>
                      <td className="px-2 py-2">
                        <input
                          value={p.unit}
                          onChange={e => updateParam(idx, 'unit', e.target.value)}
                          className="w-16 border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                          placeholder="g/dL"
                        />
                      </td>
                      <td className="px-2 py-2">
                        <input
                          value={p.referenceRange}
                          onChange={e => updateParam(idx, 'referenceRange', e.target.value)}
                          className="w-24 border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                          placeholder="12-16"
                        />
                      </td>
                      <td className="px-2 py-2">
                        <select
                          value={p.flag}
                          onChange={e => updateParam(idx, 'flag', e.target.value)}
                          className={`border border-gray-200 rounded-lg px-2 py-1.5 text-xs font-medium focus:outline-none focus:ring-2 focus:ring-blue-300 ${FLAG_COLORS[p.flag] || ''}`}
                        >
                          <option value="Normal">Normal</option>
                          <option value="Low">Low</option>
                          <option value="High">High</option>
                          <option value="Critical">Critical</option>
                        </select>
                      </td>
                      <td className="px-2 py-2">
                        {params.length > 1 && (
                          <button
                            type="button"
                            onClick={() => removeParam(idx)}
                            className="w-7 h-7 flex items-center justify-center text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                          >
                            ✕
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Summary */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-2">Overall Clinical Summary</label>
            <textarea
              value={summary}
              onChange={e => setSummary(e.target.value)}
              rows={3}
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300 resize-none"
              placeholder="Overall interpretation of the results, clinical significance..."
            />
          </div>

          {/* Checkboxes */}
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-3 cursor-pointer group">
              <div className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-all ${
                isAbnormal ? 'bg-red-500 border-red-500' : 'border-gray-300 group-hover:border-red-400'
              }`}>
                {isAbnormal && <span className="text-white text-xs font-bold">✓</span>}
              </div>
              <input
                type="checkbox"
                checked={isAbnormal}
                onChange={e => setIsAbnormal(e.target.checked)}
                className="sr-only"
              />
              <span className="text-sm font-medium text-red-700">Mark as Abnormal Result</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer group">
              <div className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-all ${
                isCritical ? 'bg-red-700 border-red-700' : 'border-gray-300 group-hover:border-red-500'
              }`}>
                {isCritical && <span className="text-white text-xs font-bold">✓</span>}
              </div>
              <input
                type="checkbox"
                checked={isCritical}
                onChange={e => setIsCritical(e.target.checked)}
                className="sr-only"
              />
              <span className="text-sm font-medium text-red-800">🚨 Critical Value (fires immediate alert)</span>
            </label>
          </div>

          {/* Pathologist verification note */}
          <div className="bg-blue-50 border border-blue-100 rounded-xl px-4 py-3 text-xs text-blue-800">
            ℹ️ This result will be pending pathologist verification after submission. It cannot be released to the
            patient/doctor until a pathologist reviews and signs off.
          </div>

          {/* Error */}
          {error && (
            <div className="flex items-center gap-3 bg-red-50 border border-red-200 rounded-xl px-4 py-3 text-sm text-red-700">
              <span>⚠</span>
              <span>{error}</span>
            </div>
          )}
        </form>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 text-sm font-medium border border-gray-200 rounded-xl text-gray-600 hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="px-6 py-2.5 text-sm font-semibold bg-emerald-600 text-white rounded-xl hover:bg-emerald-700 disabled:opacity-60 disabled:cursor-not-allowed transition-all active:scale-95 shadow-sm"
          >
            {submitting ? '⏳ Submitting…' : '✓ Submit Result'}
          </button>
        </div>
      </div>
    </div>
  );
}
