import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import platformService from '../../services/platformService';
import { extractApiError } from '../../utils/apiError';
import { STATE_TEXT, STATE_TONE } from './HospitalPlanControls';

/**
 * The Super Admin's commercial overview: who is on a demo and when it ends, who must renew, and
 * who is close to a plan limit (a ready upgrade conversation). Each row opens the hospital, and a
 * running demo can be extended right here.
 */

const CARDS = [
  { key: 'active', label: 'Paid & active', tone: 'text-emerald-700' },
  { key: 'demo', label: 'Demos running', tone: 'text-amber-700' },
  { key: 'demoEndingSoon', label: 'Demos ending in 3 days', tone: 'text-orange-700' },
  { key: 'demoEnded', label: 'Demos ended', tone: 'text-red-700' },
  { key: 'expiringSoon', label: 'Renewals due (7 days)', tone: 'text-amber-700' },
  { key: 'grace', label: 'In grace period', tone: 'text-orange-700' },
  { key: 'expired', label: 'Expired', tone: 'text-red-700' },
  { key: 'blocked', label: 'Blocked by you', tone: 'text-gray-700' },
];

const when = (iso) =>
  iso
    ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';

function daysLabel(row) {
  if (row.state === 'DEMO_ENDED') return `Ended ${when(row.endsAt)}`;
  if (row.state === 'EXPIRED') return `Expired ${when(row.endsAt)}`;
  if (row.daysLeft < 0) return '—';
  return `${row.daysLeft} day${row.daysLeft === 1 ? '' : 's'} left · ${when(row.endsAt)}`;
}

function StateChip({ state }) {
  return (
    <span
      className={`px-2 py-0.5 rounded-full text-[11px] font-semibold whitespace-nowrap ${STATE_TONE[state] || ''}`}
    >
      {STATE_TEXT[state] || state}
    </span>
  );
}

function Panel({ title, subtitle, empty, children, count }) {
  return (
    <section className="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-100">
        <h3 className="text-base font-semibold text-gray-900">
          {title} <span className="text-gray-400 font-normal">({count})</span>
        </h3>
        {subtitle && <p className="text-xs text-gray-500 mt-0.5">{subtitle}</p>}
      </div>
      {count === 0 ? <p className="px-5 py-6 text-sm text-gray-500">{empty}</p> : children}
    </section>
  );
}

export default function SubscriptionOverview({ onOpenHospital }) {
  const { success, error: toastError } = useToast();
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(null);

  const load = useCallback(() => {
    platformService
      .getSubscriptionOverview()
      .then(setData)
      .catch((err) => toastError(extractApiError(err, 'Could not load the subscription overview')));
  }, [toastError]);

  useEffect(() => {
    load();
  }, [load]);

  const extend = async (row, days) => {
    setBusy(row.publicId);
    try {
      await platformService.hospitalDemoAction(row.publicId, 'EXTEND', days);
      success(`${row.name}: demo extended by ${days} days`);
      load();
    } catch (err) {
      toastError(extractApiError(err, 'Could not extend the demo'));
    } finally {
      setBusy(null);
    }
  };

  if (!data) return <p className="text-sm text-gray-500">Loading subscriptions…</p>;
  const { counts = {}, demos = [], renewals = [], nearLimit = [] } = data;
  const open = (row) => onOpenHospital?.(row);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-bold text-gray-900">Subscriptions &amp; demos</h2>
        <p className="text-sm text-gray-600">
          Who is on a demo, who must renew, and who is close to a plan limit.
        </p>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {CARDS.map((c) => (
          <div key={c.key} className="bg-white border border-gray-200 rounded-xl p-4">
            <p className="text-xs uppercase text-gray-500">{c.label}</p>
            <p className={`text-2xl font-bold mt-1 ${c.tone}`}>{counts[c.key] ?? 0}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <Panel
          title="Demos"
          subtitle="Ending soonest first. Call them before it runs out."
          empty="No demo accounts."
          count={demos.length}
        >
          <ul className="divide-y divide-gray-100">
            {demos.map((row) => {
              const urgent = row.state === 'DEMO' && row.daysLeft >= 0 && row.daysLeft <= 3;
              return (
                <li
                  key={row.publicId}
                  className={`px-5 py-3 flex flex-wrap items-center gap-3 ${urgent ? 'bg-orange-50/60' : ''}`}
                >
                  <button
                    type="button"
                    onClick={() => open(row)}
                    className="text-sm font-medium text-gray-900 hover:underline text-left"
                  >
                    {row.name}
                  </button>
                  <StateChip state={row.state} />
                  <span className="text-xs text-gray-600">{daysLabel(row)}</span>
                  <span className="ml-auto flex gap-1.5">
                    {[7, 15].map((d) => (
                      <button
                        key={d}
                        type="button"
                        disabled={busy === row.publicId}
                        onClick={() => extend(row, d)}
                        className="px-2 py-1 rounded-md text-[11px] font-semibold bg-white border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                      >
                        +{d}d
                      </button>
                    ))}
                  </span>
                </li>
              );
            })}
          </ul>
        </Panel>

        <Panel
          title="Renewals"
          subtitle="Expiring within 7 days, in the grace period, or expired."
          empty="No renewals due."
          count={renewals.length}
        >
          <ul className="divide-y divide-gray-100">
            {renewals.map((row) => (
              <li key={row.publicId} className="px-5 py-3 flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  onClick={() => open(row)}
                  className="text-sm font-medium text-gray-900 hover:underline text-left"
                >
                  {row.name}
                </button>
                <StateChip state={row.state} />
                <span className="text-xs text-gray-600">{row.planName || '—'}</span>
                <span className="text-xs text-gray-600 ml-auto">{daysLabel(row)}</span>
              </li>
            ))}
          </ul>
        </Panel>
      </div>

      <Panel
        title="Close to a plan limit"
        subtitle="80% or more of a limit used. A good moment to offer an upgrade."
        empty="Nobody is close to a limit."
        count={nearLimit.length}
      >
        <ul className="divide-y divide-gray-100">
          {nearLimit.map((row) => (
            <li key={row.publicId} className="px-5 py-3 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => open(row)}
                className="text-sm font-medium text-gray-900 hover:underline text-left"
              >
                {row.name}
              </button>
              <span className="text-xs text-gray-600">{row.planName || '—'}</span>
              <span className="ml-auto flex flex-wrap gap-1.5">
                {row.limits.map((l) => (
                  <span
                    key={l.key}
                    className={`px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                      l.used >= l.limit ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-800'
                    }`}
                  >
                    {l.label} {l.used}/{l.limit}
                  </span>
                ))}
              </span>
            </li>
          ))}
        </ul>
      </Panel>
    </div>
  );
}
