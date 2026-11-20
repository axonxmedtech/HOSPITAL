import React, { useEffect, useState } from 'react';
import { useConfirm } from '../context/ConfirmContext';
import { useToast } from '../context/ToastContext';
import useLiveRefresh from '../hooks/useLiveRefresh';
import authService from '../services/authService';
import WardService from '../services/wardService';
import { extractApiError } from '../utils/apiError';
import Button from './Button';

const BedListDrawer = ({ open, ward, onClose, onStatusChange }) => {
  const { error: toastError } = useToast();
  const confirm = useConfirm();
  const [beds, setBeds] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open && ward) void fetchBeds();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, ward]);

  const fetchBeds = async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const data = await WardService.getBeds(ward.wardId);
      setBeds(data);
    } catch (e) {
      console.error(e);
    } finally {
      if (!silent) setLoading(false);
    }
  };

  // A bed admitted to or freed by someone else changes here without reopening the list.
  useLiveRefresh(() => fetchBeds(true), open && !!ward);

  const [updatingBedId, setUpdatingBedId] = useState(null);
  const role = authService.getCurrentUser()?.role;
  const canManageBeds = ['HOSPITAL_ADMIN', 'NURSE_INCHARGE'].includes(role);
  // Reception may free a cleaned bed (no nurse in-charge needed), but not take one out of service.
  const canMarkCleaned = canManageBeds || role === 'RECEPTIONIST';

  const makeAvailable = async (bedId, cleaning = false, bedNumber = '') => {
    if (updatingBedId) return;
    if (
      !(await confirm({
        title: cleaning ? `Mark bed ${bedNumber} cleaned?` : `Put bed ${bedNumber} back in use?`,
        message: 'The bed becomes available, and the next admission can be given this bed.',
        confirmLabel: cleaning ? 'Mark cleaned' : 'Make available',
      }))
    )
      return;

    setUpdatingBedId(bedId);
    try {
      if (cleaning) await WardService.markBedCleaned(bedId);
      else await WardService.updateBedStatus(bedId, 'Back to available');
      onStatusChange && onStatusChange();
      void fetchBeds();
    } catch (e) {
      toastError(extractApiError(e, 'Failed to update bed status'));
    } finally {
      setUpdatingBedId(null);
    }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex">
      <button type="button" aria-label="Close" className="flex-1 bg-black/40" onClick={onClose} />
      <div className="w-96 bg-white p-4 shadow-xl h-full flex flex-col">
        <div className="flex items-center justify-between mb-4 shrink-0">
          <h3 className="text-lg font-semibold">Beds — {ward?.wardName}</h3>
          <button onClick={onClose} className="text-slate-500">
            Close
          </button>
        </div>

        {loading ? (
          <p>Loading...</p>
        ) : (
          <div className="space-y-3 flex-1 overflow-y-auto">
            {beds.length === 0 && <p className="text-sm text-slate-500">No beds found.</p>}
            {beds.map((b) => (
              <div key={b.bedId} className="flex items-center justify-between p-2 border rounded">
                <div>
                  <div className="font-medium">{b.bedCode}</div>
                  <div className="text-sm text-slate-500">{b.status}</div>
                </div>
                <div>
                  {canMarkCleaned && b.status === 'cleaning' && (
                    <Button
                      size="sm"
                      variant="success"
                      onClick={() => makeAvailable(b.bedId, true, b.bedNumber)}
                      disabled={!!updatingBedId}
                    >
                      {updatingBedId === b.bedId ? 'Updating...' : 'Mark Cleaned'}
                    </Button>
                  )}
                  {canManageBeds && b.status === 'maintenance' && (
                    <Button
                      size="sm"
                      variant="success"
                      onClick={() => makeAvailable(b.bedId, false, b.bedNumber)}
                      disabled={!!updatingBedId}
                    >
                      {updatingBedId === b.bedId ? 'Updating...' : 'Make Available'}
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default BedListDrawer;
