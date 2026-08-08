import React, { useEffect, useState } from 'react';
import BedListDrawer from '../../components/BedListDrawer';
import ConfirmationModal from '../../components/ConfirmationModal';
import WardCard from '../../components/WardCard';
import WardModal from '../../components/WardModal';
import { useToast } from '../../context/ToastContext';
import authService from '../../services/authService';
import hospitalService from '../../services/hospitalService';
import WardService from '../../services/wardService';

/**
 * Copy per ward type. The three screens share this component because a ward is a ward — beds,
 * pricing and the incharge behave identically — and only the wording and the filter differ.
 * Duplicating the screen three times would mean fixing every bed bug three times.
 */
const LABELS = {
  IPD: {
    empty: 'No wards found. Use "Create Ward" to add one.',
  },
  ICU: {
    empty:
      'No ICU wards yet. Create one, then move a patient here from their ward when they need intensive care.',
  },
  OT: {
    empty:
      'No operating theatres yet. Each OT ward is one theatre and holds one case at a time, so create one per theatre.',
  },
};

/**
 * @param {('IPD'|'ICU'|'OT')} wardType which wards this screen manages.
 * @param {number} refreshToken bump to force a refetch. Wards can also be created from the
 *   dashboard-level modal, which cannot reach this component's state; without a signal the list
 *   would keep showing what it held before that save.
 */
const WardsAndBeds = ({ wardType = 'IPD', refreshToken = 0 }) => {
  const { success, error: toastError } = useToast();
  const [wards, setWards] = useState([]);
  const [nurseIncharges, setNurseIncharges] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedWard, setSelectedWard] = useState(null);
  const [showBeds, setShowBeds] = useState(false);
  const [editWard, setEditWard] = useState(null);
  const [editOpen, setEditOpen] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const [confirmState, setConfirmState] = useState({
    open: false,
    title: '',
    message: '',
    onConfirm: null,
  });

  // The nurse-incharge picker is a nursing feature: /hospital/nurses is @RequireModule("NURSING").
  // Without the module the call is rejected, so don't make it — and hide the picker.
  const nursingEnabled = (authService.getCurrentUser()?.modules || []).includes('NURSING');

  // wardType is a dependency: the three screens mount the same component, so switching between
  // them changes only this prop. Without it the list would keep showing the previous type's wards.
  // refreshToken covers saves made from the dashboard-level ward modal, which this component
  // cannot observe any other way.
  useEffect(() => {
    fetchWards();
    if (nursingEnabled) fetchNurseIncharges();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nursingEnabled, wardType, refreshToken]);

  const fetchWards = async () => {
    setLoading(true);
    try {
      const data = await WardService.getWards(wardType);
      setWards(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const fetchNurseIncharges = async () => {
    try {
      const data = await hospitalService.getNurses('', 0, 500);
      const list = data?.content || data || [];
      setNurseIncharges(list.filter((n) => n.isIncharge));
    } catch (e) {
      console.error(e);
    }
  };

  const onSetIncharge = async (ward, nurseProfileId) => {
    try {
      await hospitalService.setWardIncharge(ward.wardId, nurseProfileId);
      success('Ward incharge updated');
      await fetchWards();
    } catch (e) {
      toastError(e?.response?.data?.error || 'Failed to update ward incharge');
    }
  };

  const onViewBeds = (ward) => {
    setSelectedWard(ward);
    setShowBeds(true);
  };

  const onEdit = (ward) => {
    setEditWard(ward);
    setEditOpen(true);
  };

  const onDelete = (ward) => {
    setConfirmState({
      open: true,
      title: 'Delete Ward',
      message: `Delete ward "${ward.wardName}"? This will also delete all its unoccupied beds.`,
      onConfirm: async () => {
        setDeleting(ward.wardId);
        try {
          await WardService.deleteWard(ward.wardId);
          await fetchWards();
        } catch (e) {
          const msg = e.response?.data?.message || e.response?.data || 'Failed to delete ward';
          toastError(typeof msg === 'string' ? msg : 'Failed to delete ward');
        } finally {
          setDeleting(null);
        }
      },
    });
  };

  return (
    <div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {loading && <div>Loading wards...</div>}
        {!loading && wards.length === 0 && (
          <div className="text-slate-500">{(LABELS[wardType] || LABELS.IPD).empty}</div>
        )}

        {wards.map((w) => (
          <WardCard
            key={w.wardId}
            ward={w}
            onViewBeds={onViewBeds}
            onEdit={onEdit}
            onDelete={onDelete}
            deleting={deleting === w.wardId}
            inchargeOptions={nurseIncharges}
            onSetIncharge={nursingEnabled ? onSetIncharge : undefined}
          />
        ))}
      </div>

      <BedListDrawer
        open={showBeds}
        ward={selectedWard}
        onClose={() => setShowBeds(false)}
        onStatusChange={fetchWards}
      />

      <WardModal
        open={editOpen}
        wardType={wardType}
        initial={editWard}
        onClose={() => {
          setEditOpen(false);
          setEditWard(null);
        }}
        onSaved={() => {
          setEditOpen(false);
          setEditWard(null);
          fetchWards();
        }}
      />

      <ConfirmationModal
        isOpen={confirmState.open}
        title={confirmState.title}
        message={confirmState.message}
        onConfirm={confirmState.onConfirm}
        onCancel={() => setConfirmState({ open: false })}
      />
    </div>
  );
};

export default WardsAndBeds;
