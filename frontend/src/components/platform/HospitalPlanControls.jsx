import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import platformService from '../../services/platformService';
import { extractApiError } from '../../utils/apiError';

/**
 * Super Admin controls for one hospital, shown in its edit dialog: the demo (start, extend, end,
 * convert to paid), the subscription's expiry (extend), and every plan limit with its current use
 * and an optional per-hospital override.
 */

export const STATE_TEXT = {
  ACTIVE: 'Active',
  EXPIRING: 'Expiring soon',
  GRACE: 'Grace period',
  EXPIRED: 'Expired',
  DEMO: 'Demo running',
  DEMO_ENDED: 'Demo ended',
};

export const STATE_TONE = {
  ACTIVE: 'bg-emerald-50 text-emerald-700',
  EXPIRING: 'bg-amber-50 text-amber-800',
  GRACE: 'bg-orange-50 text-orange-800',
  EXPIRED: 'bg-red-50 text-red-700',
  DEMO: 'bg-amber-100 text-amber-800',
  DEMO_ENDED: 'bg-red-100 text-red-700',
};

const formatDate = (iso) =>
  iso
    ? new Date(iso).toLocaleString('en-IN', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      })
    : '—';

/** " · 3 days left" while there is time left; nothing once ended or expired. */
const daysLeftText = (d) => {
  if (d.daysLeft < 0 || d.state === 'DEMO_ENDED' || d.state === 'EXPIRED') return '';
  return ` · ${d.daysLeft} day${d.daysLeft === 1 ? '' : 's'} left`;
};

const btn = 'px-3 py-1.5 rounded-lg text-xs font-semibold disabled:opacity-50';
const input =
  'border border-gray-300 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900';

export default function HospitalPlanControls({ hospitalPublicId, onChanged }) {
  const { success, error: toastError } = useToast();
  const [data, setData] = useState(null);
  const [days, setDays] = useState(14);
  const [overrides, setOverrides] = useState({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const d = await platformService.getHospitalLimits(hospitalPublicId);
      setData(d);
      setOverrides(Object.fromEntries((d.limits || []).map((l) => [l.key, l.override ?? ''])));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load plan details'));
    }
  }, [hospitalPublicId, toastError]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (fn, message) => {
    setBusy(true);
    try {
      await fn();
      success(message);
      await load();
      onChanged?.();
    } catch (err) {
      toastError(extractApiError(err, 'That did not work'));
    } finally {
      setBusy(false);
    }
  };

  const demo = (action, d) =>
    run(
      () => platformService.hospitalDemoAction(hospitalPublicId, action, d),
      {
        START: `Demo started for ${d} days`,
        EXTEND: `Demo extended by ${d} days`,
        END: 'Demo ended',
        CONVERT: 'Converted to a paid account',
      }[action]
    );

  const saveLimits = () =>
    run(
      () =>
        platformService.setHospitalLimits(
          hospitalPublicId,
          Object.fromEntries(
            Object.entries(overrides).map(([k, v]) => [
              k,
              v === '' || v === null ? null : Number(v),
            ])
          )
        ),
      'Limits saved'
    );

  if (!data) return <p className="text-sm text-gray-500">Loading plan details…</p>;
  const isDemo = !!data.isDemo;
  const daysOk = Number(days) >= 1 && Number(days) <= 365;

  return (
    <div className="space-y-4">
      {/* Status */}
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span
          className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${STATE_TONE[data.state] || ''}`}
        >
          {STATE_TEXT[data.state] || data.state}
        </span>
        {data.endsAt && (
          <span className="text-gray-600">
            {isDemo ? 'Demo ends' : 'Ends'} {formatDate(data.endsAt)}
            {daysLeftText(data)}
          </span>
        )}
      </div>

      {/* Demo and subscription */}
      <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 space-y-2">
        <h4 className="text-sm font-semibold text-gray-800">
          {isDemo ? 'Demo' : 'Demo & subscription'}
        </h4>
        <div className="flex flex-wrap items-center gap-2">
          <label className="text-xs text-gray-700 flex items-center gap-1.5">
            <span>Days</span>
            <input
              type="number"
              min="1"
              max="365"
              value={days}
              onChange={(e) => setDays(e.target.value)}
              className={`${input} w-20`}
              aria-label="Days"
            />
          </label>
          {isDemo ? (
            <>
              <button
                type="button"
                disabled={busy || !daysOk}
                onClick={() => demo('EXTEND', Number(days))}
                className={`${btn} bg-gray-900 text-white`}
              >
                Extend demo
              </button>
              {[7, 15, 30].map((d) => (
                <button
                  key={d}
                  type="button"
                  disabled={busy}
                  onClick={() => demo('EXTEND', d)}
                  className={`${btn} bg-white border border-gray-300 text-gray-700`}
                >
                  +{d}d
                </button>
              ))}
              {data.state !== 'DEMO_ENDED' && (
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => demo('END')}
                  className={`${btn} bg-red-50 text-red-700 border border-red-200`}
                >
                  End demo now
                </button>
              )}
              <button
                type="button"
                disabled={busy}
                onClick={() => demo('CONVERT')}
                className={`${btn} bg-emerald-600 text-white`}
              >
                Convert to paid
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                disabled={busy || !daysOk}
                onClick={() =>
                  run(
                    () => platformService.extendSubscription(hospitalPublicId, Number(days)),
                    `Subscription extended by ${days} days`
                  )
                }
                className={`${btn} bg-gray-900 text-white`}
              >
                Extend subscription
              </button>
              <button
                type="button"
                disabled={busy || !daysOk}
                onClick={() => demo('START', Number(days))}
                className={`${btn} bg-white border border-amber-300 text-amber-800`}
              >
                Make it a demo
              </button>
            </>
          )}
        </div>
        {isDemo && (
          <p className="text-xs text-gray-600">
            Convert to paid when they buy; then assign their plan below to start its billing period.
          </p>
        )}
      </div>

      {/* Limits */}
      <div className="border border-gray-200 rounded-lg overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 text-xs uppercase text-gray-600">
            <tr>
              <th className="px-3 py-2 text-left">Limit</th>
              <th className="px-3 py-2 text-left">In use</th>
              <th className="px-3 py-2 text-left">Plan</th>
              <th className="px-3 py-2 text-left">Override</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {(data.limits || []).map((l) => {
              const full = l.limit !== null && l.limit !== undefined && l.used >= l.limit;
              return (
                <tr key={l.key}>
                  <td className="px-3 py-2 text-gray-900">{l.label}</td>
                  <td
                    className={`px-3 py-2 ${full ? 'text-red-700 font-semibold' : 'text-gray-700'}`}
                  >
                    {l.used}
                    {l.limit !== null && l.limit !== undefined ? ` / ${l.limit}` : ''}
                  </td>
                  <td className="px-3 py-2 text-gray-600">{l.planLimit ?? 'Unlimited'}</td>
                  <td className="px-3 py-2">
                    <input
                      type="number"
                      min="0"
                      value={overrides[l.key] ?? ''}
                      onChange={(e) => setOverrides((o) => ({ ...o, [l.key]: e.target.value }))}
                      placeholder="Plan's"
                      aria-label={`${l.label} override`}
                      className={`${input} w-24`}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <div className="bg-gray-50 px-3 py-2 flex items-center justify-between gap-2">
          <p className="text-xs text-gray-500">
            Blank = use the plan&apos;s limit. Lowering a limit never removes anything.
          </p>
          <button
            type="button"
            disabled={busy}
            onClick={saveLimits}
            className={`${btn} bg-gray-900 text-white`}
          >
            Save limits
          </button>
        </div>
      </div>
    </div>
  );
}
