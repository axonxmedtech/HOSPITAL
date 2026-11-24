import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Button from '../../../components/Button';
import IcuAdmitModal from '../../../components/IcuAdmitModal';
import CurrentAdmissionsTable from '../../../components/ipd/CurrentAdmissionsTable';
import IcuHistoryView from '../../../components/records/IcuHistoryView';
import SubTabs from '../../../components/records/SubTabs';
import { useToast } from '../../../context/ToastContext';
import useLiveRefresh from '../../../hooks/useLiveRefresh';
import icuService from '../../../services/icuService';
import { extractApiError } from '../../../utils/apiError';
import IcuBedBoard from './IcuBedBoard';
import IcuDashboard from './IcuDashboard';
import IcuHousekeeping from './IcuHousekeeping';

/** An ICU patient row in the shape the shared admissions table reads. */
export const toAdmissionRow = (p) => ({
  ipdId: p.ipdId,
  ipdNumber: p.ipdNumber,
  patientName: p.patientName,
  doctorName: p.intensivistName
    ? `${p.doctorName || '—'} · ICU: ${p.intensivistName}`
    : p.doctorName,
  wardName: p.icuWardName,
  bedNumber: p.bedNumber,
  admissionDateTime: p.admissionDateTime,
  status: p.status,
  admissionConfirmed: p.admissionConfirmed !== false,
  inIcu: true,
  icuUnitLabel: p.unitType,
});

const VIEWS = ['beds', 'patients', 'housekeeping', 'history'];

/**
 * The ICU in one place. An ICU stay is part of an IPD admission (the patient is in a critical-care
 * bed), so every job here is the IPD job: the same case page, the same checkout, the same bed
 * turnover. Replaces the separate "ICU", "ICU Dashboard" and "ICU Bed Board" screens.
 *
 *  - Beds: the hub. Occupied → open the case; Cleaning Required → Mark cleaned; free → Admit here.
 *  - Patients: who is in critical care, with the desk's row actions (checkout once the doctor
 *    has planned the discharge, Move out of ICU, admission form, bill).
 *  - Housekeeping: extra jobs beyond routine turnover (desk roles).
 *  - History: every ICU stay, including patients who have left.
 *
 * `canDesk`: reception, the admin, or a doctor in Solo/Both mode (the server's front-desk rule).
 * `canHousekeep`: roles the housekeeping API accepts tasks from.
 */
export default function IcuWorkspace({
  canDesk = false,
  canHousekeep = false,
  hasBilling = false,
  isAdmin = false,
}) {
  const { error: toastError } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const views = VIEWS.filter((v) => v !== 'housekeeping' || canHousekeep);
  const view = views.includes(searchParams.get('icuView')) ? searchParams.get('icuView') : 'beds';
  const setView = (v) =>
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (v === 'beds') next.delete('icuView');
        else next.set('icuView', v);
        next.delete('ipdFilter');
        return next;
      },
      { replace: true }
    );

  const [refreshKey, setRefreshKey] = useState(0);
  const bump = useCallback(() => setRefreshKey((k) => k + 1), []);
  useLiveRefresh(bump);

  const [patients, setPatients] = useState([]);
  const [unitId, setUnitId] = useState(null);
  const [admit, setAdmit] = useState(null); // null closed; {} any bed; { wardId, bedId } that bed

  useEffect(() => {
    let active = true;
    icuService
      .getIcuPatients()
      .then((rows) => active && setPatients((rows || []).map(toAdmissionRow)))
      .catch((err) => active && toastError(extractApiError(err, 'Could not load ICU patients')));
    return () => {
      active = false;
    };
  }, [refreshKey, toastError]);

  const ready = patients.filter((p) => (p.status || '').toUpperCase() === 'DISCHARGE_PLANNED');

  return (
    <div className="p-4 sm:p-5 space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-500 max-w-2xl">
          ICU patients are IPD patients in a critical-care bed. Their case, bill and discharge are
          the same as on the IPD.
        </p>
        {canDesk && (
          <Button size="sm" onClick={() => setAdmit({})}>
            Move patient to ICU
          </Button>
        )}
      </div>

      <IcuDashboard
        compact
        refreshKey={refreshKey}
        onOpenBedBoard={(wardId) => {
          setUnitId(wardId ?? null);
          setView('beds');
        }}
      />

      <SubTabs
        options={views.map((v) => ({
          value: v,
          label:
            v === 'beds'
              ? 'Beds'
              : v === 'patients'
                ? `Patients (${patients.length})${ready.length ? ` · ${ready.length} ready to go` : ''}`
                : v === 'housekeeping'
                  ? 'Housekeeping'
                  : 'History',
        }))}
        value={view}
        onChange={setView}
      />

      <div>
        {view === 'beds' && (
          <IcuBedBoard
            hideSummary
            refreshKey={refreshKey}
            initialWardId={unitId}
            canMarkCleaned={canDesk}
            onAdmitHere={canDesk ? (bed) => setAdmit({ wardId: bed.wardId, bedId: bed.bedId }) : null}
          />
        )}
        {view === 'patients' &&
          (patients.length === 0 ? (
            <p className="text-sm text-slate-500 text-center py-10">
              No patients in critical care right now.
            </p>
          ) : (
            <CurrentAdmissionsTable
              rows={patients}
              canDesk={canDesk}
              hasBilling={hasBilling}
              hasIcu
              showIcuBadge={false}
              isAdmin={isAdmin}
              onChanged={bump}
            />
          ))}
        {view === 'housekeeping' && <IcuHousekeeping refreshKey={refreshKey} onChanged={bump} />}
        {view === 'history' && <IcuHistoryView />}
      </div>

      {admit && (
        <IcuAdmitModal
          isOpen
          initialBed={admit.bedId ? admit : null}
          onClose={() => setAdmit(null)}
          onSuccess={() => {
            setAdmit(null);
            bump();
          }}
        />
      )}
    </div>
  );
}
