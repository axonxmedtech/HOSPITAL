import React, { useEffect, useState } from 'react';
import BedListDrawer from '../../../components/BedListDrawer';
import Button from '../../../components/Button';
import ConfirmationModal from '../../../components/ConfirmationModal';
import IcuWardModal from '../../../components/IcuWardModal';
import { useToast } from '../../../context/ToastContext';
import authService from '../../../services/authService';
import hospitalService from '../../../services/hospitalService';
import icuWardService from '../../../services/icuWardService';
import { extractApiError } from '../../../utils/apiError';

const IcuWardsAndBeds = () => {
  const { success, error: toastError } = useToast();
  const [icuWards, setIcuWards] = useState([]);
  const [nurseIncharges, setNurseIncharges] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedWard, setSelectedWard] = useState(null);
  const [showBeds, setShowBeds] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingWard, setEditingWard] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [confirmState, setConfirmState] = useState({
    open: false,
    title: '',
    message: '',
    onConfirm: null,
  });

  const currentUser = authService.getCurrentUser();
  const isAdmin = currentUser?.role === 'HOSPITAL_ADMIN';
  const nursingEnabled = (currentUser?.modules || []).includes('NURSING');

  useEffect(() => {
    fetchIcuWards();
    if (nursingEnabled) fetchNurseIncharges();
  }, [nursingEnabled]);

  const fetchIcuWards = async () => {
    setLoading(true);
    try {
      const data = await icuWardService.getIcuWards();
      setIcuWards(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error(e);
      toastError(extractApiError(e, 'Failed to load ICU wards'));
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
      await icuWardService.setIncharge(ward.publicId, nurseProfileId);
      success('ICU Ward incharge updated');
      await fetchIcuWards();
    } catch (e) {
      toastError(extractApiError(e, 'Failed to update ICU ward incharge'));
    }
  };

  const onViewBeds = (ward) => {
    setSelectedWard(ward);
    setShowBeds(true);
  };

  const onEdit = (ward) => {
    setEditingWard(ward);
    setModalOpen(true);
  };

  const onDelete = (ward) => {
    setConfirmState({
      open: true,
      title: 'Delete ICU Ward',
      message: `Are you sure you want to delete ICU ward "${ward.wardName}"? This will delete the ICU ward and its unoccupied beds. Occupied wards cannot be deleted.`,
      onConfirm: async () => {
        setDeletingId(ward.publicId);
        try {
          await icuWardService.deleteIcuWard(ward.publicId);
          success('ICU ward deleted');
          await fetchIcuWards();
        } catch (e) {
          toastError(extractApiError(e, 'Failed to delete ICU ward'));
        } finally {
          setDeletingId(null);
        }
      },
    });
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="inline-flex items-center justify-center w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 font-bold text-sm">
              ICU
            </span>
            <h2 className="text-xl font-bold text-slate-800">Critical Care Wards & Beds</h2>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Dedicated intensive care units (ICU, MICU, SICU, NICU, PICU, CCU, HDU) with continuous
            monitoring and daily bed pricing.
          </p>
        </div>

        {isAdmin && (
          <Button
            variant="primary"
            onClick={() => {
              setEditingWard(null);
              setModalOpen(true);
            }}
            className="flex items-center gap-1.5 shadow-sm"
          >
            <span className="text-base leading-none">+</span>
            <span>Create ICU Ward</span>
          </Button>
        )}
      </div>

      {/* Grid of ICU Wards */}
      {loading ? (
        <div className="text-center py-12 text-slate-400 text-sm">Loading ICU wards...</div>
      ) : icuWards.length === 0 ? (
        <div className="bg-white rounded-2xl border border-dashed border-slate-300 p-12 text-center">
          <div className="w-12 h-12 rounded-full bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto mb-3 font-semibold text-lg">
            🏥
          </div>
          <h3 className="text-base font-semibold text-slate-800">No ICU Wards Configured</h3>
          <p className="text-xs text-slate-500 mt-1 max-w-md mx-auto">
            Create an intensive care unit (e.g. SICU, MICU) to allocate critical care beds and
            manage high-acuity admissions.
          </p>
          {isAdmin && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setEditingWard(null);
                setModalOpen(true);
              }}
              className="mt-4"
            >
              + Create First ICU Ward
            </Button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {icuWards.map((w) => (
            <div
              key={w.publicId || w.id}
              className="bg-white rounded-2xl border border-slate-200/80 shadow-sm hover:shadow-md transition-shadow p-5 flex flex-col justify-between"
            >
              <div>
                <div className="flex items-start justify-between gap-2 mb-2">
                  <h3 className="text-lg font-bold text-slate-800">{w.wardName}</h3>
                  <span className="inline-flex px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200/60">
                    {w.unitTypeLabel || w.unitType}
                  </span>
                </div>

                <div className="space-y-1.5 text-xs text-slate-600 mb-3">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Total Beds:</span>
                    <span className="font-semibold text-slate-800">{w.totalBeds}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Daily Bed Rate:</span>
                    <span className="font-semibold text-emerald-700">₹{w.bedPrice} / day</span>
                  </div>
                  {w.floorNumber !== null && w.floorNumber !== undefined && (
                    <div className="flex justify-between">
                      <span className="text-slate-400">Floor:</span>
                      <span className="text-slate-700">{w.floorNumber}</span>
                    </div>
                  )}
                  <div className="flex justify-between pt-1 border-t border-slate-100">
                    <span className="text-slate-400">Live Occupancy:</span>
                    <span className="font-medium text-slate-700">
                      <span className="text-emerald-600 font-bold">
                        {w.availableBeds ?? 0} Avail
                      </span>
                      {' / '}
                      <span className="text-rose-600 font-bold">
                        {w.occupiedBeds ?? 0} Occupied
                      </span>
                    </span>
                  </div>
                </div>

                <div className="mb-3">
                  <span
                    className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                      w.staffed ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'
                    }`}
                  >
                    {w.staffed
                      ? `STAFFED (${w.inchargeNurseName || 'Incharge Assigned'})`
                      : 'UNSTAFFED — No Nurse Incharge'}
                  </span>
                </div>

                {nursingEnabled && isAdmin && (
                  <div className="mt-2 pt-2 border-t border-slate-100">
                    <label
                      htmlFor={`incharge-${w.wardId}`}
                      className="block text-[11px] font-medium text-slate-500 mb-1"
                    >
                      Assign Nurse Incharge
                    </label>
                    <select
                      id={`incharge-${w.wardId}`}
                      className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-xs text-slate-700 focus:ring-2 focus:ring-indigo-500 outline-none"
                      value={w.inchargeNurseId || ''}
                      onChange={(e) =>
                        onSetIncharge(w, e.target.value ? Number(e.target.value) : null)
                      }
                    >
                      <option value="">— No incharge assigned —</option>
                      {(nurseIncharges || []).map((n) => (
                        <option key={n.nurseProfileId} value={n.nurseProfileId}>
                          {n.name}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Action Buttons */}
              <div className="mt-4 pt-3 border-t border-slate-100 flex items-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => onViewBeds(w)}
                  className="flex-1 text-xs"
                >
                  View Beds
                </Button>
                {isAdmin && (
                  <>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onEdit(w)}
                      className="text-xs"
                    >
                      Edit
                    </Button>
                    <Button
                      variant="alert"
                      size="sm"
                      onClick={() => onDelete(w)}
                      disabled={deletingId === w.publicId}
                      className="text-xs"
                    >
                      {deletingId === w.publicId ? '...' : 'Delete'}
                    </Button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Bed Drawer */}
      <BedListDrawer
        open={showBeds}
        ward={selectedWard}
        onClose={() => setShowBeds(false)}
        onStatusChange={fetchIcuWards}
      />

      {/* Create / Edit Modal */}
      <IcuWardModal
        open={modalOpen}
        initial={editingWard}
        onClose={() => {
          setModalOpen(false);
          setEditingWard(null);
        }}
        onSaved={() => {
          setModalOpen(false);
          setEditingWard(null);
          fetchIcuWards();
        }}
      />

      {/* Delete Confirmation */}
      <ConfirmationModal
        isOpen={confirmState.open}
        title={confirmState.title}
        message={confirmState.message}
        onConfirm={confirmState.onConfirm}
        onClose={() => setConfirmState((prev) => ({ ...prev, open: false }))}
      />
    </div>
  );
};

export default IcuWardsAndBeds;
