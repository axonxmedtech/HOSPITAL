import React, { useState } from 'react';

/**
 * CdssAlertModal — shows CDSS safety alerts before a prescription save.
 * Props:
 *   alerts: CdssAlertDTO[]  — [{type, severity, title, message, suggestion}]
 *   onProceed: (overrideReason: string) => void
 *   onCancel: () => void
 */
export default function CdssAlertModal({ alerts, onProceed, onCancel }) {
  const [acknowledged, setAcknowledged] = useState({});
  const [overrideReason, setOverrideReason] = useState('');

  const highAlerts = alerts.filter(a => a.severity === 'HIGH');
  // Use full-array indices (matching the checkboxes) to check acknowledgement
  const highAlertIndices = alerts
    .map((a, i) => (a.severity === 'HIGH' ? i : null))
    .filter(i => i !== null);
  const allHighAcknowledged = highAlertIndices.every(i => acknowledged[i]);
  const canProceed = highAlerts.length === 0 || allHighAcknowledged;

  const severityStyle = (severity) =>
    severity === 'HIGH'
      ? 'bg-red-50 border-red-200 text-red-800'
      : 'bg-orange-50 border-orange-200 text-orange-800';

  const chipStyle = (severity) =>
    severity === 'HIGH'
      ? 'bg-red-100 text-red-700 text-xs font-bold px-2 py-0.5 rounded-full'
      : 'bg-orange-100 text-orange-700 text-xs font-bold px-2 py-0.5 rounded-full';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center gap-3 mb-4">
          <span className="text-2xl">⚠</span>
          <div>
            <h3 className="text-lg font-bold text-gray-800">Clinical Safety Alerts</h3>
            <p className="text-xs text-gray-500">Review before proceeding</p>
          </div>
        </div>

        <div className="space-y-3 mb-4">
          {alerts.map((alert, i) => (
            <div key={i} className={`border rounded-lg p-3 ${severityStyle(alert.severity)}`}>
              <div className="flex items-start justify-between gap-2">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className={chipStyle(alert.severity)}>
                      {alert.severity === 'HIGH' ? '🔴 HIGH' : '🟠 MEDIUM'}
                    </span>
                    <span className="text-sm font-semibold">{alert.title}</span>
                  </div>
                  <p className="text-xs leading-relaxed">{alert.message}</p>
                  {alert.suggestion && (
                    <p className="text-xs mt-1 font-medium opacity-80">
                      → {alert.suggestion}
                    </p>
                  )}
                </div>
                {alert.severity === 'HIGH' && (
                  <label className="flex items-center gap-1 cursor-pointer flex-shrink-0 mt-1">
                    <input
                      type="checkbox"
                      checked={!!acknowledged[i]}
                      onChange={e => setAcknowledged(prev => ({ ...prev, [i]: e.target.checked }))}
                      className="w-4 h-4 accent-red-600"
                    />
                    <span className="text-xs font-medium">I&apos;ve read this</span>
                  </label>
                )}
              </div>
            </div>
          ))}
        </div>

        {highAlerts.length > 0 && (
          <div className="mb-4">
            <label className="block text-xs font-medium text-gray-600 mb-1">
              Override reason (optional — for audit log)
            </label>
            <input
              type="text"
              value={overrideReason}
              onChange={e => setOverrideReason(e.target.value)}
              placeholder="e.g. Benefits outweigh risks, patient counselled"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-400"
            />
          </div>
        )}

        <div className="flex justify-end gap-3">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50"
          >
            Cancel Prescription
          </button>
          <button
            onClick={() => onProceed(overrideReason)}
            disabled={!canProceed}
            className="px-4 py-2 text-sm rounded-lg bg-red-600 text-white hover:bg-red-700 font-medium disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Proceed Anyway
          </button>
        </div>
        {highAlerts.length > 0 && !canProceed && (
          <p className="text-xs text-red-500 text-center mt-2">
            Check all HIGH alert boxes to enable &quot;Proceed Anyway&quot;
          </p>
        )}
      </div>
    </div>
  );
}
