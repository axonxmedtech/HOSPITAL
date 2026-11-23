import { useState } from 'react';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';
import DateSelect from '../DateSelect';
import { DischargeTypeFields, dischargeNoteRequired } from './DischargeType';

/** Mirrors DischargeSummaryRules.CONDITIONS on the server. */
export const CONDITIONS = [
  { value: 'IMPROVED', label: 'Improved' },
  { value: 'STABLE', label: 'Stable' },
  { value: 'UNCHANGED', label: 'Unchanged' },
  { value: 'DETERIORATED', label: 'Deteriorated' },
];

let medicineKey = 0;
/** A stable key per medicine row, so removing one row never mixes up the others. */
const nextKey = () => {
  medicineKey += 1;
  return `med-${medicineKey}`;
};

const blankMedicine = () => ({
  key: nextKey(),
  name: '',
  dose: '',
  frequency: '',
  durationDays: '',
  instructions: '',
});

/** A death or absconding has no "condition at discharge". */
const needsCondition = (type) => type !== 'EXPIRED' && type !== 'ABSCONDED';

const fromSummary = (summary, dischargeType, dischargeNote) => ({
  finalDiagnosis: summary?.finalDiagnosis || '',
  treatmentGiven: summary?.treatmentGiven || '',
  proceduresDone: summary?.proceduresDone || '',
  investigations: summary?.investigations || '',
  conditionAtDischarge: summary?.conditionAtDischarge || '',
  advice: summary?.advice || '',
  dischargeNotes: summary?.dischargeNotes || '',
  dischargeMedications: (summary?.dischargeMedications || []).map((m) => ({
    ...blankMedicine(),
    ...m,
    key: nextKey(),
    durationDays: m.durationDays ?? '',
  })),
  followUpDate: summary?.followUpDate || '',
  followUpTime: summary?.followUpTime ? String(summary.followUpTime).slice(0, 5) : '',
  dischargeType: dischargeType || 'NORMAL',
  dischargeTypeNote: dischargeNote || '',
});

/** Pure check, run before saving; the server enforces the same rules. */
export const validateSummary = (f) => {
  if (!f.finalDiagnosis.trim()) return 'Enter the final diagnosis';
  if (!f.treatmentGiven.trim()) return 'Enter the course in hospital (treatment given)';
  if (needsCondition(f.dischargeType) && !f.conditionAtDischarge)
    return "Select the patient's condition at discharge";
  if (dischargeNoteRequired(f.dischargeType) && !f.dischargeTypeNote.trim())
    return f.dischargeType === 'EXPIRED'
      ? 'Enter the time and cause of death'
      : 'Enter the hospital the patient is referred to';
  if (f.dischargeMedications.some((m) => !m.name.trim()))
    return 'Every medicine to take home needs a name (or remove the empty row)';
  return null;
};

/**
 * The doctor's discharge summary: written when planning the discharge and editable until
 * reception confirms it. What is saved here is what prints on the summary handed to the patient.
 */
export default function DischargeSummaryModal({
  summary,
  dischargeType,
  dischargeNote,
  currentMedicines = [],
  admissionId = null,
  editing = false,
  onClose,
  onSave,
}) {
  const [f, setF] = useState(() => fromSummary(summary, dischargeType, dischargeNote));
  const submitLabel = editing ? 'Save summary' : 'Plan Discharge';
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const set = (key) => (value) => setF((prev) => ({ ...prev, [key]: value }));
  const setMed = (i, key, value) =>
    setF((prev) => ({
      ...prev,
      dischargeMedications: prev.dischargeMedications.map((m, j) =>
        j === i ? { ...m, [key]: value } : m
      ),
    }));

  /** Fills "Key investigations" from the results recorded on this admission. */
  const copyResults = async () => {
    try {
      const list = await hospitalService.getIpdInvestigations(admissionId);
      const lines = (list || [])
        .filter((o) => o.status === 'COMPLETED' && o.resultText)
        .map((o) => `${o.testName}: ${o.resultText}`);
      if (lines.length === 0) {
        setError('No test results recorded on this admission yet');
        return;
      }
      setError('');
      setF((prev) => ({
        ...prev,
        investigations: [prev.investigations.trim(), ...lines].filter(Boolean).join('\n'),
      }));
    } catch (err) {
      setError(extractApiError(err, 'Could not load the test results'));
    }
  };

  const copyCurrentMedicines = () =>
    setF((prev) => ({
      ...prev,
      dischargeMedications: [
        ...prev.dischargeMedications.filter((m) => m.name.trim()),
        ...currentMedicines.map((p) => ({
          ...blankMedicine(),
          name: p.name || '',
          dose: p.dosage || '',
          frequency: p.frequency || '',
        })),
      ],
    }));

  const submit = async () => {
    const problem = validateSummary(f);
    if (problem) {
      setError(problem);
      return;
    }
    setError('');
    setSaving(true);
    try {
      await onSave({
        finalDiagnosis: f.finalDiagnosis.trim(),
        treatmentGiven: f.treatmentGiven.trim(),
        proceduresDone: f.proceduresDone.trim() || null,
        investigations: f.investigations.trim() || null,
        conditionAtDischarge: needsCondition(f.dischargeType) ? f.conditionAtDischarge : null,
        advice: f.advice.trim() || null,
        dischargeNotes: f.dischargeNotes.trim() || null,
        dischargeMedications: f.dischargeMedications.map((m) => ({
          name: m.name.trim(),
          dose: m.dose.trim() || null,
          frequency: m.frequency.trim() || null,
          durationDays: m.durationDays === '' ? null : Number(m.durationDays),
          instructions: m.instructions.trim() || null,
        })),
        followUpDate: f.followUpDate || null,
        followUpTime: f.followUpDate && f.followUpTime ? f.followUpTime : null,
        dischargeType: f.dischargeType || 'NORMAL',
        dischargeTypeNote: f.dischargeTypeNote.trim() || null,
      });
    } finally {
      setSaving(false);
    }
  };

  const area = (id, label, key, rows = 2, required = false, placeholder = '') => (
    <div className="mb-3">
      <label htmlFor={id} className="block text-sm font-medium mb-1">
        {label}
        {required ? ' *' : ''}
      </label>
      <textarea
        id={id}
        value={f[key]}
        onChange={(e) => set(key)(e.target.value)}
        rows={rows}
        placeholder={placeholder}
        className="w-full border p-2 rounded"
      />
    </div>
  );

  return (
    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50 px-4">
      <dialog
        open
        aria-modal="true"
        aria-labelledby="ds-title"
        className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-3xl max-h-[92vh] overflow-y-auto p-6"
      >
        <h3 id="ds-title" className="text-lg font-semibold mb-1">
          {editing ? 'Edit discharge summary' : 'Plan discharge'}
        </h3>
        <p className="text-xs text-gray-500 mb-4">
          This is the discharge summary printed for the patient. It can be edited until reception
          confirms the discharge.
        </p>

        <DischargeTypeFields
          type={f.dischargeType}
          note={f.dischargeTypeNote}
          onType={set('dischargeType')}
          onNote={set('dischargeTypeNote')}
          idPrefix="ds-dt"
        />
        {area('ds-final', 'Final diagnosis', 'finalDiagnosis', 2, true)}
        {area(
          'ds-course',
          'Course in hospital / treatment given',
          'treatmentGiven',
          4,
          true,
          'How the patient presented, what was done, how they responded'
        )}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4">
          {area(
            'ds-proc',
            'Procedures',
            'proceduresDone',
            2,
            false,
            'Surgeries done in OT are added automatically'
          )}
          <div>
            {area('ds-inv', 'Key investigations', 'investigations', 2, false, 'Important results')}
            {admissionId && (
              <button
                type="button"
                onClick={copyResults}
                className="-mt-2 mb-3 text-xs font-semibold text-blue-700 hover:underline"
              >
                Copy test results
              </button>
            )}
          </div>
        </div>

        {needsCondition(f.dischargeType) && (
          <div className="mb-3">
            <label htmlFor="ds-cond" className="block text-sm font-medium mb-1">
              Condition at discharge *
            </label>
            <select
              id="ds-cond"
              value={f.conditionAtDischarge}
              onChange={(e) => set('conditionAtDischarge')(e.target.value)}
              className="w-full border p-2 rounded"
            >
              <option value="">Select…</option>
              {CONDITIONS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="mb-3">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-1">
            <span className="block text-sm font-medium">Medicines to take at home</span>
            <div className="flex gap-2">
              {currentMedicines.length > 0 && (
                <button
                  type="button"
                  onClick={copyCurrentMedicines}
                  className="px-2 py-1 text-xs font-semibold rounded border border-blue-300 text-blue-700 hover:bg-blue-50"
                >
                  Copy current medicines
                </button>
              )}
              <button
                type="button"
                onClick={() =>
                  setF((prev) => ({
                    ...prev,
                    dischargeMedications: [...prev.dischargeMedications, blankMedicine()],
                  }))
                }
                className="px-2 py-1 text-xs font-semibold rounded border border-gray-300 hover:bg-gray-50"
              >
                + Add medicine
              </button>
            </div>
          </div>
          {f.dischargeMedications.length === 0 ? (
            <p className="text-xs text-gray-500">No medicines to take home.</p>
          ) : (
            <div className="space-y-2">
              {f.dischargeMedications.map((m, i) => (
                <div key={m.key} className="grid grid-cols-12 gap-2 items-start">
                  <input
                    aria-label={`Medicine ${i + 1} name`}
                    value={m.name}
                    onChange={(e) => setMed(i, 'name', e.target.value)}
                    placeholder="Medicine"
                    className="col-span-12 sm:col-span-4 border p-1.5 rounded text-sm"
                  />
                  <input
                    aria-label={`Medicine ${i + 1} dose`}
                    value={m.dose}
                    onChange={(e) => setMed(i, 'dose', e.target.value)}
                    placeholder="Dose"
                    className="col-span-4 sm:col-span-2 border p-1.5 rounded text-sm"
                  />
                  <input
                    aria-label={`Medicine ${i + 1} frequency`}
                    value={m.frequency}
                    onChange={(e) => setMed(i, 'frequency', e.target.value)}
                    placeholder="1-0-1"
                    className="col-span-4 sm:col-span-2 border p-1.5 rounded text-sm"
                  />
                  <input
                    aria-label={`Medicine ${i + 1} days`}
                    type="number"
                    min={1}
                    max={365}
                    value={m.durationDays}
                    onChange={(e) => setMed(i, 'durationDays', e.target.value)}
                    placeholder="Days"
                    className="col-span-4 sm:col-span-1 border p-1.5 rounded text-sm"
                  />
                  <input
                    aria-label={`Medicine ${i + 1} instructions`}
                    value={m.instructions}
                    onChange={(e) => setMed(i, 'instructions', e.target.value)}
                    placeholder="After food"
                    className="col-span-10 sm:col-span-2 border p-1.5 rounded text-sm"
                  />
                  <button
                    type="button"
                    aria-label={`Remove medicine ${i + 1}`}
                    onClick={() =>
                      setF((prev) => ({
                        ...prev,
                        dischargeMedications: prev.dischargeMedications.filter((_, j) => j !== i),
                      }))
                    }
                    className="col-span-2 sm:col-span-1 text-red-600 text-sm hover:underline"
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {area(
          'ds-advice',
          'Advice on discharge',
          'advice',
          2,
          false,
          'Diet, activity, wound care, when to come back early'
        )}
        {area('ds-notes', 'Other notes', 'dischargeNotes', 2)}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4">
          <div className="mb-3">
            <span className="block text-sm font-medium mb-1">Follow-up date</span>
            <DateSelect
              value={f.followUpDate}
              onChange={set('followUpDate')}
              min={new Date().toLocaleDateString('en-CA')}
            />
          </div>
          <div className="mb-3">
            <label htmlFor="ds-fu-time" className="block text-sm font-medium mb-1">
              Follow-up time
            </label>
            <input
              id="ds-fu-time"
              type="time"
              value={f.followUpTime}
              disabled={!f.followUpDate}
              onChange={(e) => set('followUpTime')(e.target.value)}
              className="w-full border p-2 rounded disabled:bg-gray-50"
            />
          </div>
        </div>
        {f.followUpDate && (
          <p className="text-xs text-gray-500 -mt-2 mb-3">
            When the patient goes home, the follow-up visit is booked with the treating doctor (the
            next free slot from this time, 10:00 if no time is given).
          </p>
        )}

        {error && <p className="text-sm text-red-700 mb-2">{error}</p>}
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="px-3 py-1.5 bg-gray-100 rounded">
            Cancel
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={saving}
            className="px-3 py-1.5 bg-yellow-600 hover:bg-yellow-700 text-white rounded disabled:opacity-60"
          >
            {saving ? 'Saving…' : submitLabel}
          </button>
        </div>
      </dialog>
    </div>
  );
}
