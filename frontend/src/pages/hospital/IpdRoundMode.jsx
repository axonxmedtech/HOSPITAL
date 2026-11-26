import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import DietCard from '../../components/ipd/DietCard';
import DischargeSummaryModal from '../../components/ipd/DischargeSummaryModal';
import DoctorRoundsTimeline from '../../components/ipd/DoctorRoundsTimeline';
import InvestigationsPanel from '../../components/ipd/InvestigationsPanel';
import MedicineDraftRow, {
  draftFromPreset,
  draftProblem,
  draftToPayload,
  emptyDraft,
} from '../../components/ipd/MedicineDraftRow';
import { useConfirm } from '../../context/ConfirmContext';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import nurseService from '../../services/nurseService';
import { extractApiError } from '../../utils/apiError';
import { keyForAct, newIdempotencyKey } from '../../utils/idempotencyKey';

const today = () => new Date().toLocaleDateString('en-CA');
const isToday = (at) => !!at && new Date(at).toLocaleDateString('en-CA') === today();
const ACTIVE = ['ADMITTED', 'DISCHARGE_PLANNED'];

const fmtTime = (v) =>
  v
    ? new Date(v).toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';

/** The latest reading as one line of values, or null when none has been recorded. */
export const vitalsLine = (v) => {
  if (!v) return null;
  const parts = [
    v.bpSystolic && v.bpDiastolic ? `BP ${v.bpSystolic}/${v.bpDiastolic}` : null,
    v.pulse ? `Pulse ${v.pulse}` : null,
    v.temperature ? `Temp ${v.temperature}°` : null,
    v.spo2 ? `SpO₂ ${v.spo2}%` : null,
    v.respiratoryRate ? `RR ${v.respiratoryRate}` : null,
    v.painScore != null ? `Pain ${v.painScore}/10` : null,
  ].filter(Boolean);
  return parts.length ? parts.join(' · ') : null;
};

const sortForRound = (rows) =>
  [...rows].sort(
    (a, b) =>
      (a.wardName || '').localeCompare(b.wardName || '') ||
      String(a.bedNumber || '').localeCompare(String(b.bedNumber || ''), undefined, {
        numeric: true,
      })
  );

/**
 * Round mode: the doctor's daily round on one screen per patient, then "Save & next".
 *
 * Everything a round touches sits side by side instead of behind tabs: the latest vitals, the
 * current medicines (stop in one click), tests and their results, diet, and the round note with
 * any number of new medicines (typed, or added from a prescription preset in one click). One save
 * writes the round and every new order. Patients come in ward and bed order from the doctor's own
 * admitted list, ICU included; the full case stays one click away.
 */
export default function IpdRoundMode() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const confirm = useConfirm();
  const { success, error: toastError } = useToast();
  const exitTo = location.state?.from || '/hospital/doctor?tab=ipd';

  const [list, setList] = useState([]);
  const [listLoaded, setListLoaded] = useState(false);
  const [data, setData] = useState(null);
  const [vitals, setVitals] = useState(null);
  const [roundedToday, setRoundedToday] = useState(false);
  const [doneIds, setDoneIds] = useState(() => new Set());
  const [refreshKey, setRefreshKey] = useState(0);

  const [diagnosis, setDiagnosis] = useState('');
  const [notes, setNotes] = useState('');
  const [drafts, setDrafts] = useState([]);
  // One nonce per medicine draft: a refused draft stays on screen as the same object, so its
  // resend reuses the key and is prescribed once even if the first attempt's reply was lost.
  const draftNonces = useRef(new WeakMap());
  const nonceFor = (d) => {
    if (!draftNonces.current.has(d)) draftNonces.current.set(d, newIdempotencyKey('rx'));
    return draftNonces.current.get(d);
  };
  const [presets, setPresets] = useState([]);
  const [saving, setSaving] = useState(false);
  const [planOpen, setPlanOpen] = useState(false);

  useEffect(() => {
    hospitalService
      .getAdmittedIpdAdmissions()
      .then((rows) => setList(sortForRound(Array.isArray(rows) ? rows : [])))
      .catch(() => setList([]))
      .finally(() => setListLoaded(true));
    hospitalService
      .getPrescriptionPresets()
      .then((rows) => setPresets(Array.isArray(rows) ? rows : []))
      .catch(() => setPresets([]));
  }, []);

  // Started with "first": go to the first patient in round order once the list is in.
  useEffect(() => {
    if (id === 'first' && list.length > 0) {
      navigate(`/ipd/round/${list[0].ipdId}`, { replace: true, state: location.state });
    }
  }, [id, list, navigate, location.state]);

  const loadPatient = useCallback(async () => {
    if (id === 'first') return null;
    try {
      const [d, v, rounds] = await Promise.all([
        hospitalService.getIpdDetails(id),
        nurseService.getVitals(id).catch(() => []),
        hospitalService.getIpdRounds(id).catch(() => []),
      ]);
      setData(d);
      const readings = Array.isArray(v) ? v : [];
      setVitals(
        readings.reduce(
          (latest, r) =>
            !latest || new Date(r.recordedAt) > new Date(latest.recordedAt) ? r : latest,
          null
        )
      );
      const roundList = Array.isArray(rounds) ? rounds : [];
      setRoundedToday(roundList.some((r) => isToday(r.at)));
      return { d, roundList };
    } catch (err) {
      toastError(extractApiError(err, 'Could not load the patient'));
      return null;
    }
  }, [id, toastError]);

  // A new patient starts a fresh round, the diagnosis carried over from the last one.
  useEffect(() => {
    setData(null);
    setDrafts([]);
    setNotes('');
    loadPatient().then((res) => {
      if (!res) return;
      const last = res.roundList.find((r) => r.diagnosis);
      setDiagnosis(last?.diagnosis || res.d?.admission?.primaryDiagnosis || '');
    });
  }, [loadPatient]);

  const index = list.findIndex((r) => String(r.ipdId) === String(id));
  const prev = index > 0 ? list[index - 1] : null;
  const next = index >= 0 && index < list.length - 1 ? list[index + 1] : null;
  const goTo = (row) =>
    navigate(`/ipd/round/${row.ipdId}`, { replace: true, state: location.state });

  const active = ACTIVE.includes((data?.status || '').toUpperCase());
  const dirty = notes.trim() || drafts.length > 0;

  const leave = async (target) => {
    if (
      dirty &&
      !(await confirm({
        title: 'Leave without saving?',
        message: 'The round note and new medicines typed here will be lost.',
        confirmLabel: 'Leave',
      }))
    )
      return;
    if (target === 'exit') navigate(exitTo);
    else goTo(target);
  };

  const addPreset = (presetId) => {
    const preset = presets.find((p) => String(p.id) === String(presetId));
    if (!preset) return;
    setDrafts((ds) => [...ds, ...(preset.items || []).map(draftFromPreset)]);
  };

  const saveRound = async (thenNext) => {
    if (!diagnosis.trim()) return toastError('Enter the diagnosis for this round');
    const problems = drafts
      .map((d, i) => [i + 1, draftProblem(d)])
      .filter(([, p]) => p)
      .map(([n, p]) => `medicine ${n}: ${p}`);
    if (problems.length) return toastError(`Fill in ${problems.join('; ')}`);

    setSaving(true);
    try {
      await hospitalService.addIpdFollowup(id, {
        diagnosis: diagnosis.trim(),
        notes: notes.trim(),
        administeredItems: [],
      });
    } catch (err) {
      setSaving(false);
      return toastError(extractApiError(err, 'Could not save the round'));
    }
    // The round is saved; each order goes on its own, and any refused one stays here to fix.
    const left = [];
    for (const d of drafts) {
      try {
        const payload = draftToPayload(d);
        await hospitalService.addIpdPrescription(id, {
          ...payload,
          idempotencyKey: keyForAct(nonceFor(d), payload),
        });
      } catch (err) {
        left.push(d);
        toastError(`${d.medicineName}: ${extractApiError(err, 'not prescribed')}`);
      }
    }
    setSaving(false);
    setDrafts(left);
    setNotes('');
    setDoneIds((s) => new Set(s).add(String(id)));
    setRoundedToday(true);
    setRefreshKey((k) => k + 1);
    const ordered = drafts.length - left.length;
    success(
      ordered
        ? `Round saved · ${ordered} medicine${ordered === 1 ? '' : 's'} ordered`
        : 'Round saved'
    );
    if (thenNext && next && left.length === 0) goTo(next);
    else loadPatient();
  };

  const stopMedicine = async (rx) => {
    if (
      !(await confirm({
        title: `Stop ${rx.name}?`,
        message: 'It is marked discontinued and no further doses are due.',
        confirmLabel: 'Stop',
      }))
    )
      return;
    try {
      await hospitalService.stopPrescription(rx.id);
      success(`${rx.name} stopped`);
      loadPatient();
    } catch (err) {
      toastError(extractApiError(err, 'Could not stop the medicine'));
    }
  };

  const planDischarge = async (payload) => {
    try {
      await hospitalService.planDischarge(id, payload);
      success('Discharge planned. Reception is told.');
      setPlanOpen(false);
      loadPatient();
    } catch (err) {
      toastError(extractApiError(err, 'Could not plan the discharge'));
    }
  };

  const strip = useMemo(
    () =>
      list.map((r) => ({
        ...r,
        done: doneIds.has(String(r.ipdId)),
        current: String(r.ipdId) === String(id),
      })),
    [list, doneIds, id]
  );

  return (
    <div className="min-h-screen bg-[#fafafa]">
      <header className="bg-white border-b px-4 py-3 flex flex-wrap items-center gap-3 sticky top-0 z-30">
        <button
          type="button"
          onClick={() => leave('exit')}
          className="text-sm text-gray-600 hover:text-gray-900"
        >
          ← Exit round
        </button>
        <span className="font-semibold text-gray-900">Ward round</span>
        <span className="text-sm text-gray-500">
          {index >= 0 ? `Patient ${index + 1} of ${list.length}` : ''}
        </span>
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            disabled={!prev}
            onClick={() => leave(prev)}
            className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-sm disabled:opacity-40"
          >
            ‹ Previous
          </button>
          <button
            type="button"
            disabled={!next}
            onClick={() => leave(next)}
            className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-sm disabled:opacity-40"
          >
            Next ›
          </button>
        </div>
      </header>

      {strip.length > 1 && (
        <nav
          aria-label="Patients on this round"
          className="px-4 py-2 flex gap-2 overflow-x-auto bg-white border-b"
        >
          {strip.map((r) => (
            <button
              key={r.ipdId}
              type="button"
              aria-current={r.current ? 'true' : undefined}
              onClick={() => !r.current && leave(r)}
              className={`shrink-0 px-2.5 py-1 rounded-full text-xs border ${
                r.current
                  ? 'bg-primary-600 text-white border-primary-600'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              {r.done ? '✓ ' : ''}
              {r.bedNumber ? `${r.bedNumber} · ` : ''}
              {r.patientName}
            </button>
          ))}
        </nav>
      )}

      {id === 'first' && listLoaded && list.length === 0 ? (
        <p className="p-6 text-sm text-gray-500">You have no admitted patients to round on.</p>
      ) : !data ? (
        <p className="p-6 text-sm text-gray-500">Loading…</p>
      ) : (
        <main className="p-4 grid grid-cols-1 lg:grid-cols-5 gap-4">
          <section className="lg:col-span-5 bg-white border border-slate-200 rounded-2xl p-4 flex flex-wrap items-start gap-x-6 gap-y-1">
            <div>
              <h1 className="text-lg font-bold text-gray-900">
                {data.patient?.name}
                <span className="ml-2 text-sm font-normal text-gray-500">
                  {[
                    data.patient?.age ? `${data.patient.age}y` : null,
                    data.patient?.gender,
                    data.ipdNumber,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </span>
              </h1>
              <p className="text-sm text-gray-600">
                {data.admission?.ward} / {data.admission?.bed} ·{' '}
                {data.admission?.primaryDiagnosis || 'No diagnosis'}
              </p>
            </div>
            <div className="flex flex-wrap gap-2 items-center ml-auto">
              {roundedToday && (
                <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                  Rounded today ✓
                </span>
              )}
              {data.status === 'DISCHARGE_PLANNED' && (
                <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
                  Discharge planned
                </span>
              )}
              <button
                type="button"
                onClick={() => navigate(`/ipd/${id}`, { state: { from: `/ipd/round/${id}` } })}
                className="px-3 py-2 rounded-xl border border-slate-300 bg-white hover:bg-slate-50 text-sm font-medium"
              >
                Open full case
              </button>
              {data.status === 'ADMITTED' && (
                <button
                  type="button"
                  onClick={() => setPlanOpen(true)}
                  className="px-3 py-2 rounded-xl bg-yellow-600 hover:bg-yellow-700 text-white text-sm font-semibold"
                >
                  Plan discharge
                </button>
              )}
            </div>
          </section>

          <div className="lg:col-span-3 space-y-4">
            <section className="bg-white border border-slate-200 rounded-2xl p-4">
              <h2 className="font-semibold text-slate-900 mb-1">Latest vitals</h2>
              {vitalsLine(vitals) ? (
                <p className="text-sm text-gray-900">
                  {vitalsLine(vitals)}
                  <span className="ml-2 text-xs text-gray-500">{fmtTime(vitals.recordedAt)}</span>
                </p>
              ) : (
                <p className="text-sm text-gray-500">No vitals recorded yet.</p>
              )}
            </section>

            <section className="bg-white border border-slate-200 rounded-2xl p-4">
              <h2 className="font-semibold text-slate-900 mb-2">Current medicines</h2>
              {(data.activePrescriptions || []).length === 0 ? (
                <p className="text-sm text-gray-500">None running.</p>
              ) : (
                <ul className="divide-y">
                  {data.activePrescriptions.map((rx) => (
                    <li
                      key={rx.id}
                      className="py-1.5 flex items-center justify-between gap-2 text-sm"
                    >
                      <span>
                        <span className="font-medium">{rx.name}</span>
                        <span className="text-gray-500">
                          {' '}
                          ·{' '}
                          {[
                            rx.dosage,
                            rx.frequency,
                            rx.route,
                            rx.durationDays ? `${rx.durationDays} d` : null,
                          ]
                            .filter(Boolean)
                            .join(' · ')}
                        </span>
                      </span>
                      {active && (
                        <button
                          type="button"
                          onClick={() => stopMedicine(rx)}
                          className="shrink-0 px-2 py-0.5 rounded border border-red-200 text-red-700 text-xs hover:bg-red-50"
                        >
                          Stop
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="bg-white border border-slate-200 rounded-2xl p-4">
              <h2 className="font-semibold text-slate-900 mb-2">Tests</h2>
              <InvestigationsPanel
                key={`inv-${id}-${refreshKey}`}
                admissionId={Number(id)}
                canOrder={active}
                canWork={active}
              />
            </section>

            <section className="bg-white border border-slate-200 rounded-2xl p-4">
              <DietCard admissionId={Number(id)} canEdit={active} refreshKey={refreshKey} />
            </section>
          </div>

          <div className="lg:col-span-2 space-y-4">
            <section className="bg-white border border-slate-200 rounded-2xl p-4 space-y-3 lg:sticky lg:top-28">
              <h2 className="font-semibold text-slate-900">Today’s round</h2>
              <div>
                <label htmlFor="round-dx" className="block text-xs font-medium text-gray-600 mb-1">
                  Diagnosis *
                </label>
                <input
                  id="round-dx"
                  value={diagnosis}
                  onChange={(e) => setDiagnosis(e.target.value)}
                  disabled={!active}
                  className="w-full border p-2 rounded text-sm"
                />
              </div>
              <div>
                <label
                  htmlFor="round-notes"
                  className="block text-xs font-medium text-gray-600 mb-1"
                >
                  Round note
                </label>
                <textarea
                  id="round-notes"
                  rows={4}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  disabled={!active}
                  placeholder="Findings, plan, instructions for the nurse"
                  className="w-full border p-2 rounded text-sm"
                />
              </div>

              {active && (
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="text-xs font-medium text-gray-600">
                      New medicines{drafts.length ? ` (${drafts.length})` : ''}
                    </span>
                    <div className="flex gap-2">
                      {presets.length > 0 && (
                        <select
                          aria-label="Add from preset"
                          value=""
                          onChange={(e) => addPreset(e.target.value)}
                          className="border p-1 rounded text-xs"
                        >
                          <option value="">+ From preset…</option>
                          {presets.map((p) => (
                            <option key={p.id} value={p.id}>
                              {p.name} ({(p.items || []).length})
                            </option>
                          ))}
                        </select>
                      )}
                      <button
                        type="button"
                        onClick={() => setDrafts((ds) => [...ds, emptyDraft()])}
                        className="px-2.5 py-1 rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50 text-xs font-semibold"
                      >
                        + Medicine
                      </button>
                    </div>
                  </div>
                  {drafts.map((d, i) => (
                    <MedicineDraftRow
                      key={d.key}
                      index={i}
                      draft={d}
                      onChange={(nd) =>
                        setDrafts((ds) => ds.map((x) => (x.key === d.key ? nd : x)))
                      }
                      onRemove={() => setDrafts((ds) => ds.filter((x) => x.key !== d.key))}
                    />
                  ))}
                </div>
              )}

              {active ? (
                <div className="flex justify-end gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => saveRound(false)}
                    disabled={saving}
                    className="px-4 py-2 rounded-xl border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-sm font-semibold disabled:opacity-50"
                  >
                    Save
                  </button>
                  <button
                    type="button"
                    onClick={() => saveRound(true)}
                    disabled={saving}
                    className="px-4 py-2 rounded-xl bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                  >
                    {saving ? 'Saving…' : next ? 'Save & next ›' : 'Save round'}
                  </button>
                </div>
              ) : (
                <p className="text-sm text-gray-500">
                  This admission is closed; nothing can be added.
                </p>
              )}
            </section>

            <section className="bg-white border border-slate-200 rounded-2xl p-4">
              <h2 className="font-semibold text-slate-900 mb-2">Earlier rounds</h2>
              <DoctorRoundsTimeline admissionId={Number(id)} refreshKey={refreshKey} />
            </section>
          </div>
        </main>
      )}

      {planOpen && data && (
        <DischargeSummaryModal
          currentMedicines={data.activePrescriptions || []}
          admissionId={Number(id)}
          dischargeType="NORMAL"
          dischargeNote=""
          onClose={() => setPlanOpen(false)}
          onSave={planDischarge}
        />
      )}
    </div>
  );
}
