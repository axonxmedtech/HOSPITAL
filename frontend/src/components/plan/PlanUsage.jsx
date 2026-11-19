import { useEffect, useState } from 'react';
import apiClient from '../../services/apiService';
import { extractApiError } from '../../utils/apiError';

const STATE_TEXT = {
  ACTIVE: 'Active',
  EXPIRING: 'Expiring soon',
  GRACE: 'Expired — grace period',
  EXPIRED: 'Expired',
  DEMO: 'Demo',
  DEMO_ENDED: 'Demo ended',
};

const formatDate = (iso) =>
  iso
    ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : '—';

/** One limit as a bar: "Beds 8 / 10". Unlimited shows the count alone. */
function UsageRow({ row }) {
  const { label, used, limit } = row;
  const unlimited = limit === null || limit === undefined;
  let pct = 0;
  if (!unlimited) pct = limit === 0 ? 100 : Math.min(100, (used / limit) * 100);
  // Full class names, so Tailwind sees them: the bar colour goes on the progress value.
  let bar = '[&::-webkit-progress-value]:bg-emerald-500 [&::-moz-progress-bar]:bg-emerald-500';
  if (!unlimited && pct >= 100)
    bar = '[&::-webkit-progress-value]:bg-red-500 [&::-moz-progress-bar]:bg-red-500';
  else if (!unlimited && pct >= 80)
    bar = '[&::-webkit-progress-value]:bg-amber-500 [&::-moz-progress-bar]:bg-amber-500';
  let figure = `${used} used · unlimited`;
  if (!unlimited) figure = limit === 0 ? 'Not included' : `${used} / ${limit}`;
  return (
    <li className="py-3">
      <div className="flex justify-between text-sm">
        <span className="font-medium text-gray-900">{label}</span>
        <span className="text-gray-700">{figure}</span>
      </div>
      {!unlimited && limit > 0 && (
        <progress
          aria-label={label}
          max={limit}
          value={Math.min(used, limit)}
          className={`mt-1.5 block w-full h-2 appearance-none overflow-hidden rounded-full [&::-webkit-progress-bar]:bg-gray-100 bg-gray-100 ${bar}`}
        >
          {Math.round(pct)}%
        </progress>
      )}
    </li>
  );
}

/** Plan & Usage: the hospital's plan, its expiry or demo end, and every limit with its use. */
export default function PlanUsage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    apiClient
      .get('/hospital/plan-usage')
      .then((res) => setData(res.data))
      .catch((err) => setError(extractApiError(err, 'Could not load your plan')));
  }, []);

  if (error) return <p className="text-sm text-red-700">{error}</p>;
  if (!data) return <p className="text-sm text-gray-500">Loading your plan…</p>;

  const endsLabel = data.demo ? 'Demo ends' : 'Plan expires';
  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Plan &amp; Usage</h2>
        <p className="text-sm text-gray-600 mt-1">
          What your plan includes and how much of it is in use.
        </p>
      </div>
      <section className="bg-white border border-gray-200 rounded-xl p-5 grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <p className="text-xs uppercase text-gray-500">Plan</p>
          <p className="text-lg font-semibold text-gray-900">{data.planName || '—'}</p>
        </div>
        <div>
          <p className="text-xs uppercase text-gray-500">Status</p>
          <p className="text-lg font-semibold text-gray-900">
            {STATE_TEXT[data.state] || data.state}
          </p>
        </div>
        <div>
          <p className="text-xs uppercase text-gray-500">{endsLabel}</p>
          <p className="text-lg font-semibold text-gray-900">{formatDate(data.endsAt)}</p>
        </div>
        {data.message && <p className="sm:col-span-3 text-sm text-gray-700">{data.message}</p>}
      </section>
      <section className="bg-white border border-gray-200 rounded-xl px-5">
        <h3 className="pt-4 text-sm font-semibold text-gray-900">Limits</h3>
        <ul className="divide-y divide-gray-100">
          {(data.limits || []).map((row) => (
            <UsageRow key={row.key} row={row} />
          ))}
        </ul>
      </section>
      <p className="text-sm text-gray-700">
        Need more beds, doctors or staff logins? Call{' '}
        <a className="font-semibold underline" href={`tel:${data.supportPhone}`}>
          {data.supportPhone}
        </a>{' '}
        to upgrade your plan.
      </p>
    </div>
  );
}
