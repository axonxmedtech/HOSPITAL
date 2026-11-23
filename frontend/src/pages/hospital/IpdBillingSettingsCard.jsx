import { useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';

/**
 * How inpatients are charged: how bed days are counted, the advance asked for a planned
 * admission, and the doctor-visit fee. Nursing charges are set per ward (Wards & Beds).
 * Changing the bed rule applies to patients admitted from now on, never to those in a bed.
 */
export default function IpdBillingSettingsCard() {
  const { success, error: toastError } = useToast();
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    hospitalService
      .getIpdBillingSettings()
      .then((s) =>
        setForm({
          bedChargeRule: s?.bedChargeRule || 'CALENDAR_DAY',
          graceHours: s?.graceHours ?? 0,
          minAdvance: s?.minAdvance ?? '',
          doctorVisitFee: s?.doctorVisitFee ?? '',
        })
      )
      .catch((err) => toastError(extractApiError(err, 'Failed to load IPD billing rules')));
  }, [toastError]);

  if (!form) {
    return <div className="text-sm text-gray-500 p-6">Loading…</div>;
  }

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const save = async () => {
    setSaving(true);
    try {
      await hospitalService.updateIpdBillingSettings({
        bedChargeRule: form.bedChargeRule,
        graceHours: form.bedChargeRule === 'HOURS_24' ? Number(form.graceHours) || 0 : 0,
        minAdvance: form.minAdvance === '' ? null : Number(form.minAdvance),
        doctorVisitFee: form.doctorVisitFee === '' ? null : Number(form.doctorVisitFee),
      });
      success('IPD billing rules saved');
    } catch (err) {
      toastError(extractApiError(err, 'Failed to save IPD billing rules'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 mt-6">
      <h3 className="text-lg font-bold text-gray-900 mb-1">IPD Billing Rules</h3>
      <p className="text-sm text-gray-500 mb-5">
        How inpatient stays are charged. The bed rule applies to patients admitted after you save;
        patients already in a bed keep the rule they were admitted under. Nursing charges are set on
        each ward.
      </p>

      <fieldset className="mb-5">
        <legend className="text-sm font-semibold text-gray-800 mb-2">Count bed days by</legend>
        <label className="flex items-start gap-2 mb-2 text-sm">
          <input
            type="radio"
            name="bed-rule"
            value="CALENDAR_DAY"
            checked={form.bedChargeRule === 'CALENDAR_DAY'}
            onChange={set('bedChargeRule')}
            className="mt-1"
          />
          <span>
            <strong>Calendar date</strong> — every date the patient is in counts. In at 11:30 PM,
            out next morning = 2 days.
          </span>
        </label>
        <label className="flex items-start gap-2 text-sm">
          <input
            type="radio"
            name="bed-rule"
            value="HOURS_24"
            checked={form.bedChargeRule === 'HOURS_24'}
            onChange={set('bedChargeRule')}
            className="mt-1"
          />
          <span>
            <strong>24 hours from admission</strong> — a new day starts every 24 hours. In at 11:30
            PM, out next morning = 1 day.
          </span>
        </label>
        {form.bedChargeRule === 'HOURS_24' && (
          <div className="mt-3 ml-6">
            <label htmlFor="ipd-grace" className="block text-sm text-gray-700 mb-1">
              Grace hours before a new day is charged (0–12)
            </label>
            <input
              id="ipd-grace"
              type="number"
              min="0"
              max="12"
              value={form.graceHours}
              onChange={set('graceHours')}
              className="w-32 border p-2 rounded"
            />
          </div>
        )}
      </fieldset>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-5">
        <div>
          <label
            htmlFor="ipd-min-advance"
            className="block text-sm font-semibold text-gray-800 mb-1"
          >
            Minimum advance for a planned admission (₹)
          </label>
          <input
            id="ipd-min-advance"
            type="number"
            min="0"
            value={form.minAdvance}
            onChange={set('minAdvance')}
            placeholder="Empty = not required"
            className="w-full border p-2 rounded"
          />
          <p className="text-xs text-gray-500 mt-1">Emergency admissions are never held up.</p>
        </div>
        <div>
          <label htmlFor="ipd-visit-fee" className="block text-sm font-semibold text-gray-800 mb-1">
            Doctor visit fee per round (₹)
          </label>
          <input
            id="ipd-visit-fee"
            type="number"
            min="0"
            value={form.doctorVisitFee}
            onChange={set('doctorVisitFee')}
            placeholder="Empty = rounds are free"
            className="w-full border p-2 rounded"
          />
          <p className="text-xs text-gray-500 mt-1">
            Charged when a doctor records a round, at most once per doctor per day.
          </p>
        </div>
      </div>

      <button
        type="button"
        onClick={save}
        disabled={saving}
        className="px-4 py-2 bg-sky-700 hover:bg-sky-800 text-white rounded-xl text-sm font-semibold disabled:opacity-60"
      >
        {saving ? 'Saving…' : 'Save rules'}
      </button>
    </div>
  );
}
