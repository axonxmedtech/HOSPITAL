import { useState } from 'react';

const ROUTES = ['ORAL', 'IV', 'IM', 'SUB-Q', 'TOPICAL', 'INHALATION', 'RECTAL', 'SUBLINGUAL'];

export default function MedicationAdministrationModal({ task, onClose, onSave }) {
  const [status, setStatus] = useState('DONE'); // DONE (Given), HELD, REFUSED, SKIPPED
  const [route, setRoute] = useState('ORAL');
  const [administeredQuantity, setAdministeredQuantity] = useState(1.0);
  const [injectionSite, setInjectionSite] = useState('');
  const [preVitals, setPreVitals] = useState('');
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const isInjectable = route === 'IV' || route === 'IM' || route === 'SUB-Q';

  const isMedication = task.orderType === 'MEDICATION' || task.type === 'MEDICATION' || task.orderType === null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isMedication && isInjectable && status === 'DONE' && !injectionSite.trim()) {
      setError('Injection site is required for IV, IM, and SUB-Q routes.');
      return;
    }
    setSubmitting(true);
    setError('');

    const payload = {
      status,
      notes: notes.trim() || undefined,
      administeredQuantity: (status === 'DONE' && isMedication) ? Number(administeredQuantity) : undefined,
      route: (status === 'DONE' && isMedication) ? route : undefined,
      injectionSite: (status === 'DONE' && isMedication && isInjectable) ? injectionSite.trim() : undefined,
      preVitals: preVitals.trim() || undefined,
    };

    try {
      await onSave(payload);
    } catch (err) {
      setError(err.response?.data || 'Failed to record administration. Please try again.');
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[95vh] flex flex-col overflow-hidden border border-gray-100">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-100 bg-gray-50/50 flex items-center justify-between">
          <div>
            <h3 className="text-base font-bold text-gray-900">📋 Record Nurse Task</h3>
            <p className="text-xs text-gray-500 mt-0.5 font-mono truncate max-w-[320px]">
              {task.orderDescription || task.description || 'Task Instruction'}
            </p>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          {/* Status Selection */}
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
              Task Execution Status
            </label>
            <div className="grid grid-cols-4 gap-2">
              {[
                { key: 'DONE', label: 'Done', color: 'border-green-200 text-green-700 bg-green-50/30 active:bg-green-600' },
                { key: 'HELD', label: 'Hold', color: 'border-amber-200 text-amber-700 bg-amber-50/30 active:bg-amber-600' },
                { key: 'REFUSED', label: 'Refuse', color: 'border-red-200 text-red-700 bg-red-50/30 active:bg-red-600' },
                { key: 'SKIPPED', label: 'Skip', color: 'border-gray-200 text-gray-700 bg-gray-50/30 active:bg-gray-600' },
              ].map(opt => (
                <button
                  key={opt.key}
                  type="button"
                  onClick={() => setStatus(opt.key)}
                  className={`py-2 px-3 text-sm font-semibold rounded-xl border transition-all text-center ${
                    status === opt.key
                      ? 'bg-blue-600 border-blue-600 text-white shadow-sm'
                      : opt.color + ' hover:border-gray-300'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {status === 'DONE' && (
            <>
              {isMedication && (
                <>
                  {/* Route & Quantity */}
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-semibold text-gray-700 mb-1.5">Route</label>
                      <select
                        value={route}
                        onChange={e => setRoute(e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                      >
                        {ROUTES.map(r => (
                          <option key={r} value={r}>{r}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-700 mb-1.5">Quantity Administered</label>
                      <input
                        type="number"
                        step="0.1"
                        min="0.1"
                        value={administeredQuantity}
                        onChange={e => setAdministeredQuantity(e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                        required
                      />
                    </div>
                  </div>

                  {/* Injection Site */}
                  {isInjectable && (
                    <div>
                      <label className="block text-xs font-semibold text-gray-700 mb-1.5">Injection Site *</label>
                      <input
                        type="text"
                        value={injectionSite}
                        onChange={e => setInjectionSite(e.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                        placeholder="e.g. Left Arm Deltoid, Right Thigh"
                        required={isInjectable}
                      />
                    </div>
                  )}
                </>
              )}

              {/* Pre-vitals */}
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1.5">Pre-Admin Vitals / Assessment</label>
                <input
                  type="text"
                  value={preVitals}
                  onChange={e => setPreVitals(e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                  placeholder="e.g. BP: 120/80, HR: 72 bpm"
                />
              </div>
            </>
          )}

          {/* Notes / Remarks */}
          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1.5">
              {status === 'DONE' ? 'Notes / Remarks (optional)' : 'Reason for Hold / Refusal / Skip *'}
            </label>
            <textarea
              value={notes}
              onChange={e => setNotes(e.target.value)}
              rows={3}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
              placeholder={status === 'DONE' ? 'Enter clinical comments...' : 'Provide detail on why the dose was not given...'}
              required={status !== 'DONE'}
            />
          </div>

          {/* Error */}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded-xl px-4 py-2.5 text-xs">
              ⚠ {error}
            </div>
          )}
        </form>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3 bg-gray-50/30">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium border border-gray-200 rounded-xl text-gray-600 hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="px-6 py-2 bg-blue-600 text-white text-sm font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-60 transition-all shadow-sm active:scale-95"
          >
            {submitting ? 'Recording…' : 'Record Dose'}
          </button>
        </div>
      </div>
    </div>
  );
}
