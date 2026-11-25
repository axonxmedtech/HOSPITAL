import React, { useState, useEffect } from 'react';
import EmptyState from '../../../components/EmptyState';
import LoadingSpinner from '../../../components/LoadingSpinner';
import { useToast } from '../../../context/ToastContext';
import nurseService from '../../../services/nurseService';

/**
 * NurseOverviewView - dashboard landing for a nurse (Phase 1).
 * Stat cards (from the Due Now list) + recent assigned patients.
 */
const StatCard = ({ label, value, accent, hint, onClick }) => {
  const body = (
    <>
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">{label}</p>
      <p className={`text-3xl font-bold mt-2 ${accent || 'text-gray-900'}`}>{value}</p>
      {hint && <p className="text-xs text-gray-500 mt-1">{hint}</p>}
    </>
  );
  if (!onClick) return <div className="bg-white border border-gray-200 rounded-xl p-5">{body}</div>;
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex flex-col items-start justify-start text-left bg-white border border-gray-200 rounded-xl p-5 hover:border-primary-300 hover:shadow-sm transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
    >
      {body}
    </button>
  );
};

const isNow = (i) => i.state === 'OVERDUE' || i.state === 'DUE';
/** Counts for the overview cards, from the Due now list. */
export const dueCounts = (items) => {
  const now = (items || []).filter(isNow);
  return {
    doses: now.filter((i) => i.kind === 'DOSE').length,
    overdue: now.filter((i) => i.state === 'OVERDUE').length,
    vitals: now.filter((i) => i.kind === 'VITALS').length,
    samples: now.filter((i) => i.kind === 'SAMPLE').length,
  };
};

const NurseOverviewView = ({ onOpenPatient, onOpenDueNow, refreshKey }) => {
  const { error: toastError } = useToast();
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState(null);
  const [due, setDue] = useState(null);

  useEffect(() => {
    let active = true;
    nurseService
      .getWorklist()
      .then((rows) => active && setDue(dueCounts(rows)))
      .catch(() => active && setDue(null));
    return () => {
      active = false;
    };
  }, [refreshKey]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    nurseService
      .getDashboard()
      .then((d) => {
        if (active) setData(d);
      })
      .catch(() => {
        if (active) toastError('Failed to load dashboard');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [refreshKey, toastError]);

  if (loading) return <LoadingSpinner />;

  const recent = data?.recentPatients || [];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Assigned Patients" value={data?.assignedPatientCount ?? 0} />
        <StatCard
          label="Doses due now"
          value={due ? due.doses : '…'}
          accent={due?.overdue ? 'text-rose-600' : 'text-gray-900'}
          hint={due?.overdue ? `${due.overdue} overdue` : 'Open Due Now'}
          onClick={onOpenDueNow}
        />
        <StatCard
          label="Vitals due"
          value={due ? due.vitals : '…'}
          onClick={onOpenDueNow}
        />
        <StatCard
          label="Samples to collect"
          value={due ? due.samples : '…'}
          onClick={onOpenDueNow}
        />
      </div>

      <div className="bg-white border border-gray-200 rounded-xl">
        <div className="px-5 py-4 border-b border-gray-100">
          <h3 className="font-bold text-gray-800">Recent Patients</h3>
        </div>
        {recent.length === 0 ? (
          <EmptyState
            icon={null}
            title="No patients assigned yet"
            message="Your ward in-charge will assign patients to you."
          />
        ) : (
          <ul className="divide-y divide-gray-100">
            {recent.map((p) => (
              <li key={p.ipdAdmissionId}>
                <button
                  type="button"
                  className="w-full text-left px-5 py-3 flex items-center justify-between hover:bg-gray-50 cursor-pointer"
                  onClick={() => onOpenPatient(p.ipdAdmissionId)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      onOpenPatient(p.ipdAdmissionId);
                    }
                  }}
                >
                  <div>
                    <p className="font-medium text-gray-900">{p.patientName || '—'}</p>
                    <p className="text-xs text-gray-500">
                      {p.ipdNumber}
                      {p.bedCode ? ` · Bed ${p.bedCode}` : ''}
                      {p.primaryDiagnosis ? ` · ${p.primaryDiagnosis}` : ''}
                    </p>
                  </div>
                  <span className="text-xs font-semibold text-gray-400">View →</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default NurseOverviewView;
