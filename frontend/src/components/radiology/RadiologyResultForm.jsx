import { useState } from 'react';
import { enterRadiologyResult } from '../../services/radiologyService';

export default function RadiologyResultForm({ publicId, onClose, onSuccess }) {
  const [findings, setFindings] = useState('');
  const [impression, setImpression] = useState('');
  const [isAbnormal, setIsAbnormal] = useState(false);
  const [resultFileUrl, setResultFileUrl] = useState('');
  const [isCritical, setIsCritical] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!findings.trim() || !impression.trim()) {
      setError('Please fill in both findings and clinical impression.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await enterRadiologyResult(publicId, {
        findings,
        impression,
        isAbnormal,
        resultFileUrl: resultFileUrl || undefined,
        isCritical,
      });
      onSuccess();
    } catch (err) {
      setError(err.response?.data || 'Failed to submit report. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[92vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div>
            <h2 className="text-lg font-bold text-gray-900">📝 Enter Radiology Report</h2>
            <p className="text-xs text-gray-500 mt-0.5">Specify scanning observations and clinical impressions</p>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
          {/* Findings */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-2">Findings *</label>
            <textarea
              value={findings}
              onChange={e => setFindings(e.target.value)}
              rows={5}
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300"
              placeholder="Describe radiological findings (e.g. bones, lung fields, cardiac silhouette, etc.)..."
              required
            />
          </div>

          {/* Impression */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-2">Impression *</label>
            <textarea
              value={impression}
              onChange={e => setImpression(e.target.value)}
              rows={3}
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300"
              placeholder="Final clinical impression (e.g. Normal Study, Cardiomegaly, Pneumonia, etc.)..."
              required
            />
          </div>

          {/* Image/File attachment */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-2">
              Scan Image URL / Reference URL <span className="font-normal text-gray-400">(optional)</span>
            </label>
            <input
              type="url"
              value={resultFileUrl}
              onChange={e => setResultFileUrl(e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-300"
              placeholder="https://pacs.hospital.com/study/12345"
            />
          </div>

          {/* Abnormal checkbox */}
          <div>
            <label className="flex items-center gap-3 cursor-pointer group w-fit">
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
              <span className="text-sm font-medium text-red-700">Mark as Abnormal Findings</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer group w-fit mt-3">
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
              <span className="text-sm font-medium text-red-800">🚨 Critical Finding (fires immediate alert)</span>
            </label>
          </div>

          {/* Radiologist verification note */}
          <div className="bg-blue-50 border border-blue-100 rounded-xl px-4 py-3 text-xs text-blue-800">
            ℹ️ This report will be pending radiologist verification after submission. It cannot be released to the
            patient/doctor until a radiologist reviews and signs off.
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
            {submitting ? '⏳ Submitting…' : '✓ Submit Report'}
          </button>
        </div>
      </div>
    </div>
  );
}
