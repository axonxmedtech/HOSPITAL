import React, { useCallback, useEffect, useMemo, useState } from 'react';
import EmptyState from '../../../components/EmptyState';
import { useOpenIpdCase } from '../../../components/ipd/CurrentAdmissionsTable';
import LoadingSpinner from '../../../components/LoadingSpinner';
import { useToast } from '../../../context/ToastContext';
import icuService from '../../../services/icuService';
import wardService from '../../../services/wardService';
import { extractApiError } from '../../../utils/apiError';
import { bedStatusMeta, respiratorySummary, since } from './icuBoardShared';

/**
 * IcuBedBoard - the ICU bed grid (ICU Phase 2), and the ICU's hub.
 *
 * Bed state is whatever `beds.status` already says and the patient is whoever the admission
 * record already names — this board displays those two, it never stores a third.
 *
 * Each bed offers the one job it needs: an occupied bed opens the patient's IPD case in-app
 * (Back returns here), a bed awaiting cleaning is marked cleaned, a free bed takes a patient
 * ("Admit here"). The last two appear only when the host passes them, i.e. for the roles the
 * server lets do them; without them the board is read-only (staff nurses).
 */
const BedCard = ({ bed, onOpen, onAdmitHere, onMarkCleaned }) => {
  const meta = bedStatusMeta(bed.status);
  const occupied = bed.status === 'occupied';
  const clickable = occupied && bed.ipdAdmissionId != null;
  const resp = respiratorySummary(bed);
  const flagged = bed.occupancyConsistent === false;
  const action =
    bed.status === 'cleaning' && onMarkCleaned
      ? { label: 'Mark cleaned', run: () => onMarkCleaned(bed) }
      : bed.status === 'available' && onAdmitHere
        ? { label: 'Admit here', run: () => onAdmitHere(bed) }
        : null;

  return (
    <div
      className={`bg-white border rounded-xl transition
        ${flagged ? 'border-red-300 ring-1 ring-red-200' : 'border-gray-200'}
        ${clickable ? 'hover:border-primary-400 hover:shadow-sm' : ''}`}
    >
      <button
        type="button"
        disabled={!clickable}
        onClick={() => clickable && onOpen(bed.ipdAdmissionId)}
        title={clickable ? 'Open the case' : undefined}
        className={`text-left w-full p-3 rounded-xl ${clickable ? 'cursor-pointer' : 'cursor-default'}`}
      >
        <div className="flex items-start justify-between gap-2">
          <span className="font-semibold text-gray-900 text-sm">{bed.bedCode}</span>
          <span
            className={`shrink-0 px-2 py-0.5 rounded-full text-[11px] font-medium ${meta.badge}`}
          >
            {meta.label}
          </span>
        </div>

        {occupied && bed.patientName && (
          <div className="mt-2 space-y-0.5">
            <div className="text-sm text-gray-900 truncate">{bed.patientName}</div>
            <div className="text-xs text-gray-500">
              {[bed.age != null ? `${bed.age}y` : null, bed.gender, bed.ipdNumber]
                .filter(Boolean)
                .join(' · ')}
            </div>
            {bed.consultantName && (
              <div className="text-xs text-gray-500 truncate">Dr. under: {bed.consultantName}</div>
            )}
            {bed.primaryDiagnosis && (
              <div className="text-xs text-gray-600 truncate" title={bed.primaryDiagnosis}>
                {bed.primaryDiagnosis}
              </div>
            )}
            {/* Recorded observations only — the board states values, never a severity verdict. */}
            {resp && <div className="text-xs text-gray-700 mt-1">{resp}</div>}
            <div className="text-[11px] text-gray-400">In unit {since(bed.admittedAt)}</div>
            {bed.admissionConfirmed === false && (
              <div className="text-[11px] text-amber-700">
                Bed allocated · admission form to complete
              </div>
            )}
          </div>
        )}

        {occupied && !bed.patientName && (
          <div className="mt-2 text-xs text-gray-500">Occupied — patient not in your scope</div>
        )}

        {flagged && (
          <div className="mt-2 text-[11px] text-red-700 leading-snug">⚠ {bed.occupancyNote}</div>
        )}
      </button>
      {action && (
        <div className="px-3 pb-3">
          <button
            type="button"
            onClick={action.run}
            className="w-full py-1.5 rounded-lg text-xs font-semibold border border-gray-300 text-gray-800 hover:bg-gray-50"
          >
            {action.label}
          </button>
        </div>
      )}
    </div>
  );
};

const IcuBedBoard = ({
  refreshKey = 0,
  initialWardId = null,
  onAdmitHere = null,
  canMarkCleaned = false,
  hideSummary = false,
}) => {
  const { success, error: toastError } = useToast();
  const openCase = useOpenIpdCase();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [wardId, setWardId] = useState(initialWardId);
  const [statusFilter, setStatusFilter] = useState('all');

  const load = useCallback(() => {
    setLoading(true);
    icuService
      .getBoard()
      .then(setData)
      .catch((e) => toastError(extractApiError(e, 'Failed to load the ICU bed board')))
      .finally(() => setLoading(false));
  }, [toastError]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  useEffect(() => {
    if (initialWardId != null) setWardId(initialWardId);
  }, [initialWardId]);

  const markCleaned = async (bed) => {
    try {
      await wardService.markBedCleaned(bed.bedId);
      success(`${bed.bedCode} is clean and available`);
      load();
    } catch (err) {
      toastError(extractApiError(err, 'Could not mark the bed cleaned'));
    }
  };

  const units = useMemo(() => data?.units || [], [data]);

  useEffect(() => {
    // Keep the selection valid as units come and go.
    if (units.length === 0) return;
    setWardId((prev) => (prev != null && units.some((u) => u.wardId === prev) ? prev : null));
  }, [units]);

  const beds = useMemo(() => {
    let rows = data?.beds || [];
    if (wardId != null) rows = rows.filter((b) => b.wardId === wardId);
    if (statusFilter !== 'all') rows = rows.filter((b) => b.status === statusFilter);
    return rows;
  }, [data, wardId, statusFilter]);

  if (loading && !data) return <LoadingSpinner />;
  if (!data) return null;

  if (!data.hasCriticalCareUnits) {
    return (
      <EmptyState
        icon={null}
        title="No critical care units yet"
        message="The admin adds them under Rooms → ICU Wards & Beds; their beds then appear here."
      />
    );
  }

  if (units.length === 0) {
    return (
      <EmptyState
        icon={null}
        title="No ICU units in your scope"
        message="This hospital has critical care units, but none of them are assigned to you."
      />
    );
  }

  const scope = wardId == null ? data.totals : units.find((u) => u.wardId === wardId)?.counts || {};

  return (
    <div className="space-y-4">
      <div className="bg-white border border-gray-200 rounded-xl p-4 flex flex-wrap items-end gap-4">
        <div>
          <label htmlFor="icu-unit" className="block text-xs font-medium text-gray-500 mb-1">
            Unit
          </label>
          <select
            id="icu-unit"
            value={wardId ?? ''}
            onChange={(e) => setWardId(e.target.value === '' ? null : Number(e.target.value))}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
          >
            <option value="">All units</option>
            {units.map((u) => (
              <option key={u.wardId} value={u.wardId}>
                {u.wardName} ({u.unitTypeLabel})
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="icu-status" className="block text-xs font-medium text-gray-500 mb-1">
            Status
          </label>
          <select
            id="icu-status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
          >
            <option value="all">All</option>
            <option value="occupied">Occupied</option>
            <option value="available">Available</option>
            <option value="cleaning">Cleaning Required</option>
            <option value="maintenance">Under Maintenance</option>
          </select>
        </div>
        {!hideSummary && (
          <div className="ml-auto flex flex-wrap gap-4 text-sm">
            <span className="text-gray-500">
              Beds <span className="font-semibold text-gray-900">{scope.totalBeds ?? 0}</span>
            </span>
            <span className="text-gray-500">
              Occupied <span className="font-semibold text-blue-700">{scope.occupied ?? 0}</span>
            </span>
            <span className="text-gray-500">
              Available <span className="font-semibold text-green-700">{scope.available ?? 0}</span>
            </span>
            {scope.occupancyMismatches > 0 && (
              <span className="text-red-700 font-medium">
                {scope.occupancyMismatches} need attention
              </span>
            )}
          </div>
        )}
      </div>

      {beds.length === 0 ? (
        <EmptyState icon={null} title="No beds" message="No beds match the current filter." />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          {beds.map((b) => (
            <BedCard
              key={b.bedId}
              bed={b}
              onOpen={(id) => openCase(id)}
              onAdmitHere={onAdmitHere}
              onMarkCleaned={canMarkCleaned ? markCleaned : null}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default IcuBedBoard;
