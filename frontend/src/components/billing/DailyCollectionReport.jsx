import React, { useCallback, useEffect, useState } from 'react';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';
import { localIsoDate } from '../../utils/date';

/**
 * Daily cash-closing: every payment and refund taken on one day, totalled by Cash / Card / UPI,
 * with who recorded each. What the counter tallies against the cash drawer at shift end.
 */
const rupees = (v) =>
  `₹${Number(v || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const MODES = [
  ['CASH', 'Cash'],
  ['CARD', 'Card'],
  ['UPI', 'UPI / Online'],
];

/** Refund reason, or the payment's reference (UTR / card slip). */
function txnDetail(t) {
  if (t.kind !== 'REFUND') return t.reference || '—';
  return t.note ? `Refund: ${t.note}` : 'Refund';
}

const DailyCollectionReport = () => {
  const [date, setDate] = useState(localIsoDate());
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await hospitalService.getDailyCollection(date));
    } catch (e) {
      setError(extractApiError(e, "Couldn't load the collection report"));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => {
    load();
  }, [load]);

  const txns = data?.transactions || [];

  return (
    <div className="space-y-5 daily-collection-report">
      <style>{`@media print { body * { visibility: hidden; } .daily-collection-report, .daily-collection-report * { visibility: visible; } .daily-collection-report { position: absolute; left: 0; top: 0; width: 100%; } .no-print { display: none !important; } }`}</style>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-800">Daily Collection</h2>
          <p className="text-sm text-slate-500">
            Cash-closing for{' '}
            {new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { dateStyle: 'full' })}
          </p>
        </div>
        <div className="flex items-end gap-2 no-print">
          <label className="text-xs font-semibold text-gray-600">
            <span>Date</span>
            <input
              type="date"
              value={date}
              max={localIsoDate()}
              onChange={(e) => e.target.value && setDate(e.target.value)}
              className="block mt-1 px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </label>
          <button
            type="button"
            onClick={() => window.print()}
            disabled={!data}
            className="px-4 py-2 rounded-lg text-sm font-semibold bg-gray-900 text-white disabled:opacity-50"
          >
            Print
          </button>
        </div>
      </div>

      {loading && <div className="text-center text-gray-400 py-12">Loading…</div>}
      {error && (
        <div
          role="alert"
          className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800"
        >
          {error}{' '}
          <button type="button" onClick={load} className="underline font-semibold">
            Retry
          </button>
        </div>
      )}

      {data && !loading && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {MODES.map(([key, label]) => (
              <div key={key} className="bg-white border border-gray-200 rounded-xl p-4">
                <div className="text-xs font-semibold uppercase text-gray-500">{label}</div>
                <div className="text-2xl font-bold text-gray-900 mt-1">
                  {rupees(data.netByMode?.[key])}
                </div>
                {Number(data.refundedByMode?.[key]) > 0 && (
                  <div className="text-xs text-gray-500 mt-1">
                    {rupees(data.collectedByMode?.[key])} in − {rupees(data.refundedByMode?.[key])}{' '}
                    refunded
                  </div>
                )}
              </div>
            ))}
            <div className="bg-gray-900 text-white rounded-xl p-4">
              <div className="text-xs font-semibold uppercase text-gray-300">Net collected</div>
              <div className="text-2xl font-bold mt-1">{rupees(data.netCollected)}</div>
              <div className="text-xs text-gray-300 mt-1">
                {data.receiptCount} receipt{data.receiptCount === 1 ? '' : 's'}
                {Number(data.totalRefunded) > 0 ? ` · ${rupees(data.totalRefunded)} refunded` : ''}
              </div>
            </div>
          </div>

          <div className="bg-white border border-gray-200 rounded-xl overflow-x-auto">
            {txns.length === 0 ? (
              <div className="text-center text-gray-500 py-12">
                No payments were recorded on this day.
              </div>
            ) : (
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                  <tr>
                    <th className="text-left px-4 py-2">Time</th>
                    <th className="text-left px-4 py-2">Bill</th>
                    <th className="text-left px-4 py-2">Patient</th>
                    <th className="text-left px-4 py-2">Mode</th>
                    <th className="text-left px-4 py-2">Reference / note</th>
                    <th className="text-left px-4 py-2">Taken by</th>
                    <th className="text-right px-4 py-2">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {txns.map((t) => (
                    <tr
                      key={t.id ?? `${t.time}-${t.billNo}-${t.kind}`}
                      className={t.kind === 'REFUND' ? 'bg-red-50/40' : ''}
                    >
                      <td className="px-4 py-2 whitespace-nowrap">
                        {t.time
                          ? new Date(t.time).toLocaleTimeString([], {
                              hour: 'numeric',
                              minute: '2-digit',
                            })
                          : '—'}
                      </td>
                      <td className="px-4 py-2 whitespace-nowrap">
                        {t.billNo} <span className="text-xs text-gray-400">{t.billingType}</span>
                      </td>
                      <td className="px-4 py-2">{t.patientName}</td>
                      <td className="px-4 py-2">{t.mode}</td>
                      <td className="px-4 py-2 text-gray-600">{txnDetail(t)}</td>
                      <td className="px-4 py-2 text-gray-600">{t.recordedBy || '—'}</td>
                      <td
                        className={`px-4 py-2 text-right font-semibold whitespace-nowrap ${t.kind === 'REFUND' ? 'text-red-700' : 'text-gray-900'}`}
                      >
                        {t.kind === 'REFUND' ? '− ' : ''}
                        {rupees(t.amount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default DailyCollectionReport;
