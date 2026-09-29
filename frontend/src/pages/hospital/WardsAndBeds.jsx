import React, { useEffect, useState } from 'react';
import BedListDrawer from '../../components/BedListDrawer';
import ConfirmationModal from '../../components/ConfirmationModal';
import WardCard from '../../components/WardCard';
import WardModal from '../../components/WardModal';
import { useToast } from '../../context/ToastContext';
import authService from '../../services/authService';
import hospitalService from '../../services/hospitalService';
import WardService from '../../services/wardService';
import { extractApiError } from '../../utils/apiError';

const WardsAndBeds = () => {
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

  useEffect(() => {
    fetchWards();
    if (nursingEnabled) fetchNurseIncharges();
  }, [nursingEnabled]);

  const fetchWards = async () => {
    setLoading(true);
    try {
      const data = await WardService.getWards();
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
      toastError(extractApiError(e, 'Failed to update ward incharge'));
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
        } finally {
          setDeleting(null);
        }
      },
    });
  };

  const currentUser = authService.getCurrentUser();
  const isAdmin = currentUser?.role === 'HOSPITAL_ADMIN';
  const displayedWards = wards;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
        <div>
          <h2 className="text-xl font-bold text-slate-800">General Wards & Beds</h2>
          <p className="text-xs text-slate-500 mt-1">
            General inpatient departments and non-critical care beds.
          </p>
        </div>
        {isAdmin && (
          <button
            type="button"
            onClick={() => {
              setEditWard(null);
              setEditOpen(true);
            }}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-sm font-semibold shadow-sm transition-all"
          >
            <span>+</span>
            <span>Create Ward</span>
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {loading && <div>Loading wards...</div>}
        {!loading && displayedWards.length === 0 && (
          <div className="text-slate-500 col-span-full py-8 text-center bg-white rounded-2xl border border-dashed border-slate-300">
            No general wards found. Use &quot;Create Ward&quot; to add one.
          </div>
        )}

        {displayedWards.map((w) => (
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
