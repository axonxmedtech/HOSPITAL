import React, { useEffect, useState, useCallback } from 'react';
import { useToast } from '../../../context/ToastContext';
import otService from '../../../services/otService';

/**
 * RecoveryModal - PACU recovery for a completed case.
 *
 * The theatre is already free (the case is COMPLETED); this is a separate record. Only
 * shown when the hospital's RECOVERY_TRACKING policy asks for it — the server rejects
 * admission otherwise, and this surfaces that message rather than pretending.
 */
const DESTINATIONS = ['WARD', 'ICU', 'HDU', 'HOME', 'MORTUARY'];
const fmt = (dt) =>
  dt ? new Date(dt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : null;

const RecoveryModal = ({ surgery, onClose }) => {
  const { success, error: toastError } = useToast();
  const sid = surgery.surgeryId;
  const [episode, setEpisode] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [aldrete, setAldrete] = useState('');
  const [destination, setDestination] = useState('WARD');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setEpisode(await otService.getRecovery(sid));
    } catch {
      setEpisode(null);
    } finally {
      setLoading(false);
    }
  }, [sid]);

  useEffect(() => {
    load();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const admit = async () => {
    setBusy(true);
    try {
      setEpisode(await otService.admitRecovery(sid));
      success('Admitted to recovery');
    } catch (e) {
      toastError(e?.response?.data?.error || 'Failed to admit to recovery');
    } finally {
      setBusy(false);
    }
  };

  const observe = async () => {
    setBusy(true);
    try {
      await otService.observeRecovery(sid, { aldreteScore: aldrete ? Number(aldrete) : null });
      setAldrete('');
      success('Observation recorded');
    } catch (e) {
      toastError(e?.response?.data?.error || 'Failed to record observation');
    } finally {
      setBusy(false);
    }
  };

  const discharge = async () => {
    setBusy(true);
    try {
      setEpisode(await otService.dischargeRecovery(sid, destination));
      success(`Discharged to ${destination}`);
    } catch (e) {
      toastError(e?.response?.data?.error || 'Failed to discharge');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-50 flex items-start justify-center z-50 p-4 overflow-y-auto"
      role="button"
      tabIndex={-1}
      aria-label="Close dialog"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose();
      }}
    >
      <div className="bg-white rounded-2xl w-full max-w-md my-8">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div>
            <h2 className="text-lg font-bold text-gray-900">Recovery (PACU)</h2>
            <p className="text-xs text-gray-400">
              {surgery.patientName} · {surgery.procedureName}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-700 text-2xl leading-none"
          >
            ×
          </button>
        </div>

        <div className="px-6 py-4 space-y-4">
          {loading ? (
            <div className="text-center text-gray-400 py-6">Loading…</div>
          ) : !episode ? (
            <div className="text-center space-y-3">
              <p className="text-sm text-gray-500">This patient is not in recovery yet.</p>
              <button
                disabled={busy}
                onClick={admit}
                className="px-4 py-2 rounded-lg text-sm font-semibold bg-gray-900 text-white hover:bg-gray-800"
              >
                Admit to recovery
              </button>
            </div>
          ) : (
            <>
              <div className="text-xs text-gray-500">
                Arrived {fmt(episode.arrivedAt)}
                {episode.dischargedAt && (
                  <>
                    {' '}
                    · Discharged {fmt(episode.dischargedAt)} → {episode.transferDestination}
                  </>
                )}
              </div>

              {!episode.dischargedAt && (
                <>
                  <div className="flex items-end gap-2">
                    <div className="flex-1">
                      <label
                        htmlFor="fld-199"
                        className="block text-xs font-medium text-gray-600 mb-1"
                      >
                        Aldrete score (0–10)
                      </label>
                      <input
                        id="fld-199"
                        type="number"
                        min="0"
                        max="10"
                        value={aldrete}
                        onChange={(e) => setAldrete(e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                      />
                    </div>
                    <button
                      disabled={busy}
                      onClick={observe}
                      className="px-4 py-2 rounded-lg text-sm font-semibold border border-gray-300 text-gray-700 hover:bg-gray-50"
                    >
                      Record
                    </button>
                  </div>

                  <div className="flex items-end gap-2 border-t border-gray-100 pt-3">
                    <div className="flex-1">
                      <label
                        htmlFor="fld-198"
                        className="block text-xs font-medium text-gray-600 mb-1"
                      >
                        Discharge to
                      </label>
                      <select
                        id="fld-198"
                        value={destination}
                        onChange={(e) => setDestination(e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                      >
                        {DESTINATIONS.map((d) => (
                          <option key={d} value={d}>
                            {d}
                          </option>
                        ))}
                      </select>
                    </div>
                    <button
                      disabled={busy}
                      onClick={discharge}
                      className="px-4 py-2 rounded-lg text-sm font-semibold bg-gray-900 text-white hover:bg-gray-800"
                    >
                      Discharge
                    </button>
                  </div>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default RecoveryModal;
