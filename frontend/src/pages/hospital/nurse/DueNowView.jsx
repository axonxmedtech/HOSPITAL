import { useCallback, useEffect, useMemo, useState } from 'react';
import ActionMenu from '../../../components/ActionMenu';
import Button from '../../../components/Button';
import { useToast } from '../../../context/ToastContext';
import useLiveRefresh from '../../../hooks/useLiveRefresh';
import authService from '../../../services/authService';
import hospitalService from '../../../services/hospitalService';
import nurseService from '../../../services/nurseService';
import { extractApiError } from '../../../utils/apiError';

const SLOT_LABEL = { MORNING: 'Morning', AFTERNOON: 'Afternoon', NIGHT: 'Night' };
const STATE_STYLE = {
  OVERDUE: { label: 'Overdue', cls: 'bg-rose-50 text-rose-700 border-rose-200' },
  DUE: { label: 'Due now', cls: 'bg-amber-50 text-amber-800 border-amber-200' },
  UPCOMING: { label: 'Later', cls: 'bg-slate-50 text-slate-600 border-slate-200' },
  DONE: { label: 'Done', cls: 'bg-green-50 text-green-700 border-green-200' },
  AS_NEEDED: { label: 'As needed', cls: 'bg-sky-50 text-sky-700 border-sky-200' },
  UNSCHEDULED: { label: 'Check order', cls: 'bg-slate-50 text-slate-600 border-slate-200' },
};
const KIND_LABEL = { DOSE: 'Dose', VITALS: 'Vitals', SAMPLE: 'Sample' };
const RECORDED_LABEL = {
  GIVEN: 'Given',
  DELAYED: 'Given late',
  REFUSED: 'Refused',
  SKIPPED: 'Skipped',
  NOT_AVAILABLE: 'Not available',
};
const RANK = { OVERDUE: 0, DUE: 1, AS_NEEDED: 2, UPCOMING: 3, UNSCHEDULED: 4, DONE: 5 };

export const FILTERS = [
  { value: 'now', label: 'Now', test: (i) => i.state === 'OVERDUE' || i.state === 'DUE' },
  { value: 'later', label: 'Later today', test: (i) => i.state === 'UPCOMING' },
  {
    value: 'other',
    label: 'As needed',
    test: (i) => i.state === 'AS_NEEDED' || i.state === 'UNSCHEDULED',
  },
  { value: 'done', label: 'Done', test: (i) => i.state === 'DONE' },
];

const time = (v) =>
  v ? new Date(v).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : '';

/** One line describing the job: what to give / measure / collect. */
export const describe = (i) => {
  if (i.kind === 'DOSE') {
    return {
      title: i.medicineName,
      detail: [i.dosage, i.route, i.quantityPerDose > 1 ? `${i.quantityPerDose} units` : null]
        .filter(Boolean)
        .join(' · '),
      when: i.slot ? `${SLOT_LABEL[i.slot]} · ${time(i.scheduledTime)}` : '',
    };
  }
  if (i.kind === 'VITALS') {
    return {
      title: 'Vitals',
      detail: i.lastRecordedAt ? `Last at ${time(i.lastRecordedAt)}` : 'None recorded yet',
      when: i.scheduledTime ? `Due ${time(i.scheduledTime)}` : '',
    };
  }
  return {
    title: i.testName,
    detail:
      i.priority && i.priority !== 'ROUTINE'
        ? `Sample to collect · ${i.priority.toLowerCase()}`
        : 'Sample to collect',
    when: '',
  };
};

/** Patients in ward and bed order, each with their jobs most urgent first. */
export const groupByPatient = (items) => {
  const byPatient = new Map();
  for (const i of items) {
    if (!byPatient.has(i.ipdAdmissionId)) {
      byPatient.set(i.ipdAdmissionId, { ...i, items: [] });
    }
    byPatient.get(i.ipdAdmissionId).items.push(i);
  }
  const groups = [...byPatient.values()];
  for (const g of groups) {
    g.items.sort(
      (a, b) =>
        RANK[a.state] - RANK[b.state] ||
        String(a.scheduledTime || '').localeCompare(String(b.scheduledTime || ''))
    );
  }
  return groups.sort(
    (a, b) =>
      (a.wardName || '').localeCompare(b.wardName || '') ||
      String(a.bedCode || '').localeCompare(String(b.bedCode || ''), undefined, { numeric: true })
  );
};

function StateBadge({ state }) {
  const s = STATE_STYLE[state] || STATE_STYLE.UPCOMING;
  return (
    <span
      className={`inline-flex whitespace-nowrap px-2 py-0.5 rounded-full text-xs font-medium border ${s.cls}`}
    >
      {s.label}
    </span>
  );
}

const fieldCls =
  'w-full border border-slate-300 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500';

/** The five readings a round takes, for one patient, without leaving the list. */
function QuickVitals({ patient, performedBy, onClose, onSaved }) {
  const { success, error: toastError } = useToast();
  const [f, setF] = useState({
    bpSystolic: '',
    bpDiastolic: '',
    pulse: '',
    temperature: '',
    spo2: '',
    respiratoryRate: '',
  });
  const [saving, setSaving] = useState(false);
  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));
  const num = (v) => (v === '' ? null : Number(v));

  const save = async () => {
    const payload = { ipdAdmissionId: patient.ipdAdmissionId };
    Object.keys(f).forEach((k) => {
      payload[k] = num(f[k]);
    });
    if (Object.keys(f).every((k) => payload[k] == null)) {
      toastError('Enter at least one reading');
      return;
    }
    if (performedBy) payload.performedByNurseId = Number(performedBy);
    setSaving(true);
    try {
      await nurseService.createVitals(payload);
      success(`Vitals recorded for ${patient.patientName}`);
      onSaved();
    } catch (err) {
      toastError(extractApiError(err, 'Could not record the vitals'));
    } finally {
      setSaving(false);
    }
  };

  const fields = [
    ['pulse', 'Pulse'],
    ['temperature', 'Temp (°F)'],
    ['spo2', 'SpO₂ (%)'],
    ['respiratoryRate', 'Resp. rate'],
  ];

  return (
    <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
      <dialog
        open
        aria-modal="true"
        aria-labelledby="quick-vitals-title"
        className="relative m-0 border-0 bg-white text-slate-900 rounded-2xl shadow-xl w-full max-w-md p-6"
      >
        <h3 id="quick-vitals-title" className="text-lg font-semibold">
          Record vitals
        </h3>
        <p className="text-sm text-slate-600 mb-4">
          {patient.patientName}
          {patient.bedCode ? ` · Bed ${patient.bedCode}` : ''}
        </p>
        <fieldset className="mb-3 border-0 p-0 m-0">
          <legend className="block text-xs font-medium text-slate-600 mb-1">BP (mmHg)</legend>
          <div className="flex items-center gap-2">
            <input
              aria-label="BP systolic"
              inputMode="numeric"
              value={f.bpSystolic}
              onChange={set('bpSystolic')}
              placeholder="120"
              className={fieldCls}
            />
            <span className="text-slate-400">/</span>
            <input
              aria-label="BP diastolic"
              inputMode="numeric"
              value={f.bpDiastolic}
              onChange={set('bpDiastolic')}
              placeholder="80"
              className={fieldCls}
            />
          </div>
        </fieldset>
        <div className="grid grid-cols-2 gap-3 mb-5">
          {fields.map(([k, label]) => (
            <div key={k}>
              <label htmlFor={`qv-${k}`} className="block text-xs font-medium text-slate-600 mb-1">
                {label}
              </label>
              <input
                id={`qv-${k}`}
                inputMode="decimal"
                value={f[k]}
                onChange={set(k)}
                className={fieldCls}
              />
            </div>
          ))}
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Cancel
          </Button>
          <Button size="sm" loading={saving} onClick={save}>
            Save vitals
          </Button>
        </div>
      </dialog>
    </div>
  );
}

/**
 * Due now: every dose, vitals round and sample due today across the nurse's patients, each one
 * tap away. Replaces opening each patient's Medication, Vitals and Investigations tabs in turn.
 * With a shared nurse login the in-charge records the care, so who performed it is chosen once
 * at the top instead of on every entry.
 */
export default function DueNowView({ onOpenPatient, refreshKey = 0 }) {
  const { success, error: toastError } = useToast();
  const user = authService.getCurrentUser();
  const [items, setItems] = useState(null);
  const [loadedAt, setLoadedAt] = useState(null);
  const [filter, setFilter] = useState('now');
  const [busy, setBusy] = useState(null); // key of the job being recorded
  const [vitalsFor, setVitalsFor] = useState(null);
  const [sharedLogin, setSharedLogin] = useState(false);
  const [roster, setRoster] = useState([]);
  const [performedBy, setPerformedBy] = useState('');

  const load = useCallback(async () => {
    try {
      const rows = await nurseService.getWorklist();
      setItems(Array.isArray(rows) ? rows : []);
      setLoadedAt(new Date());
    } catch (err) {
      toastError(extractApiError(err, 'Could not load the due list'));
      setItems((prev) => prev || []);
    }
  }, [toastError]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);
  useLiveRefresh(() => load());

  useEffect(() => {
    if (user?.role !== 'NURSE_INCHARGE') return;
    nurseService
      .getSeparateNurseLogin()
      .then((separate) => {
        if (separate) return;
        setSharedLogin(true);
        nurseService
          .getMyNurses()
          .then((list) => setRoster(Array.isArray(list) ? list : []))
          .catch(() => setRoster([]));
      })
      .catch(() => {});
  }, [user?.role]);

  const counts = useMemo(
    () => Object.fromEntries(FILTERS.map((f) => [f.value, (items || []).filter(f.test).length])),
    [items]
  );
  const groups = useMemo(
    () => groupByPatient((items || []).filter(FILTERS.find((f) => f.value === filter).test)),
    [items, filter]
  );

  const needsPerformer = sharedLogin && !performedBy;
  const performer = () => {
    if (!needsPerformer) return true;
    toastError('Choose who is giving the care (Recording for) first');
    return false;
  };

  const run = async (key, fn, okText) => {
    setBusy(key);
    try {
      await fn();
      success(okText);
      await load();
    } catch (err) {
      toastError(extractApiError(err, 'Could not record it'));
    } finally {
      setBusy(null);
    }
  };

  const recordDose = (i, status) => {
    if (!performer()) return;
    run(
      `${i.prescriptionId}-${i.slot || 'prn'}`,
      () =>
        nurseService.recordMedication({
          ipdAdmissionId: i.ipdAdmissionId,
          prescriptionId: i.prescriptionId,
          status,
          scheduledTime: i.scheduledTime || null,
          quantity: status === 'GIVEN' ? i.quantityPerDose || 1 : null,
          ...(performedBy ? { performedByNurseId: Number(performedBy) } : {}),
        }),
      `${i.medicineName}: ${RECORDED_LABEL[status].toLowerCase()}`
    );
  };

  const collect = (i) =>
    run(
      i.investigationPublicId,
      () => hospitalService.markInvestigationSample(i.investigationPublicId),
      `${i.testName}: sample collected`
    );

  const actionFor = (i, g) => {
    const key =
      i.kind === 'SAMPLE' ? i.investigationPublicId : `${i.prescriptionId}-${i.slot || 'prn'}`;
    const working = busy === key;
    if (i.state === 'DONE') {
      return (
        <span className="text-xs text-slate-500 whitespace-nowrap">
          {i.kind === 'DOSE'
            ? `${RECORDED_LABEL[i.recordedStatus] || 'Recorded'} ${time(i.recordedAt)}`
            : 'Up to date'}
        </span>
      );
    }
    if (i.kind === 'VITALS') {
      return (
        <Button
          size="sm"
          variant="secondary"
          onClick={() => performer() && setVitalsFor(g)}
          className="whitespace-nowrap"
        >
          Record vitals
        </Button>
      );
    }
    if (i.kind === 'SAMPLE') {
      return (
        <Button size="sm" variant="secondary" loading={working} onClick={() => collect(i)}>
          Collected ✓
        </Button>
      );
    }
    if (i.state === 'UNSCHEDULED') {
      return (
        <Button size="sm" variant="ghost" onClick={() => onOpenPatient?.(i.ipdAdmissionId)}>
          Open chart
        </Button>
      );
    }
    return (
      <div className="flex items-center gap-1">
        <Button
          size="sm"
          // Only a dose that is due now is the loud button; later and as-needed ones stay quiet.
          variant={i.state === 'DUE' || i.state === 'OVERDUE' ? 'primary' : 'secondary'}
          loading={working}
          onClick={() => recordDose(i, 'GIVEN')}
        >
          {i.state === 'AS_NEEDED' ? 'Give dose' : 'Given ✓'}
        </Button>
        {i.state !== 'AS_NEEDED' && (
          <ActionMenu
            actions={[
              { label: 'Refused by patient', onClick: () => recordDose(i, 'REFUSED') },
              { label: 'Skipped', onClick: () => recordDose(i, 'SKIPPED') },
              { label: 'Not available', onClick: () => recordDose(i, 'NOT_AVAILABLE') },
            ]}
          />
        )}
      </div>
    );
  };

  if (items === null) return <p className="text-sm text-slate-500 p-6">Loading…</p>;

  return (
    <div className="space-y-4">
      <div className="bg-white border border-slate-200 rounded-2xl p-4 flex flex-wrap items-center gap-3">
        <fieldset className="flex flex-wrap gap-2 border-0 p-0 m-0 min-w-0">
          <legend className="sr-only">Show</legend>
          {FILTERS.map((f) => {
            const active = filter === f.value;
            const urgent = f.value === 'now' && counts.now > 0;
            return (
              <button
                key={f.value}
                type="button"
                aria-pressed={active}
                onClick={() => setFilter(f.value)}
                className={`inline-flex items-center gap-1.5 h-8 px-3 rounded-full text-xs font-semibold border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${
                  active
                    ? 'bg-primary-50 text-primary-700 border-primary-300'
                    : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                }`}
              >
                {f.label}
                <span
                  className={`min-w-5 px-1.5 rounded-full text-[11px] leading-5 text-center ${
                    urgent ? 'bg-rose-500 text-white' : 'bg-slate-100'
                  }`}
                >
                  {counts[f.value]}
                </span>
              </button>
            );
          })}
        </fieldset>
        {loadedAt && (
          <span className={`text-xs text-slate-500 ${sharedLogin ? '' : 'ml-auto'}`}>
            Updated {time(loadedAt)}
          </span>
        )}
        {sharedLogin && (
          <div className="ml-auto flex items-center gap-2">
            <label htmlFor="due-performer" className="text-xs font-medium text-slate-600">
              Recording for
            </label>
            <select
              id="due-performer"
              value={performedBy}
              onChange={(e) => setPerformedBy(e.target.value)}
              className={`border rounded-lg px-2.5 py-1.5 text-sm bg-white ${
                performedBy ? 'border-slate-300' : 'border-amber-400'
              }`}
            >
              <option value="">Choose nurse…</option>
              {roster.map((n) => (
                <option key={n.nurseProfileId} value={n.nurseProfileId}>
                  {n.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {groups.length === 0 ? (
        <div className="bg-white border border-slate-200 rounded-2xl p-10 text-center">
          <p className="font-medium text-slate-800">
            {filter === 'now' ? 'Nothing due right now' : 'Nothing here'}
          </p>
          <p className="text-sm text-slate-500 mt-1">
            {filter === 'now'
              ? 'Doses, vitals and samples appear here as they fall due.'
              : 'Try another filter.'}
          </p>
        </div>
      ) : (
        groups.map((g) => (
          <section
            key={g.ipdAdmissionId}
            aria-label={g.patientName}
            className="bg-white border border-slate-200 rounded-2xl overflow-hidden"
          >
            <header className="px-4 py-3 bg-slate-50 border-b border-slate-200 flex flex-wrap items-center gap-x-3 gap-y-1">
              {g.bedCode && (
                <span className="px-2 py-0.5 rounded-md bg-white border border-slate-200 text-xs font-semibold text-slate-700">
                  {g.bedCode}
                </span>
              )}
              <span className="font-semibold text-slate-900">{g.patientName}</span>
              <span className="text-xs text-slate-500">
                {[g.ipdNumber, g.wardName].filter(Boolean).join(' · ')}
              </span>
              <button
                type="button"
                onClick={() => onOpenPatient?.(g.ipdAdmissionId)}
                className="ml-auto text-xs font-semibold text-primary-600 hover:text-primary-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded"
              >
                Open chart ›
              </button>
            </header>
            <ul className="divide-y divide-slate-100">
              {g.items.map((i) => {
                const d = describe(i);
                return (
                  <li
                    key={`${i.kind}-${i.prescriptionId || i.investigationPublicId || 'v'}-${i.slot || ''}`}
                    className="px-4 py-3 flex flex-wrap items-center gap-x-4 gap-y-2"
                  >
                    <span className="hidden sm:block w-14 shrink-0 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                      {KIND_LABEL[i.kind]}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="font-medium text-slate-900 truncate">{d.title}</div>
                      <div className="text-xs text-slate-500 truncate">
                        {[d.detail, d.when].filter(Boolean).join(' · ')}
                      </div>
                    </div>
                    {/* Phone: status and action on their own line, so the job name is never cut. */}
                    <div className="w-full sm:w-auto flex items-center justify-between sm:justify-end gap-3">
                      <StateBadge state={i.state} />
                      <div className="shrink-0">{actionFor(i, g)}</div>
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        ))
      )}

      {vitalsFor && (
        <QuickVitals
          patient={vitalsFor}
          performedBy={performedBy}
          onClose={() => setVitalsFor(null)}
          onSaved={() => {
            setVitalsFor(null);
            load();
          }}
        />
      )}
    </div>
  );
}
