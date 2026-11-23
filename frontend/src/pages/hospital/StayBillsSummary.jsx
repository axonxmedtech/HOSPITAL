import { useCallback, useEffect, useState } from 'react';
import useLiveRefresh from '../../hooks/useLiveRefresh';
import hospitalService from '../../services/hospitalService';

const inr = (v) => `₹${Number(v || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;

/**
 * All the bills of one hospital stay together: the bill of the OPD visit that led to the
 * admission and the IPD bill, with a combined total (e.g. OPD ₹500 + IPD ₹550 = ₹1,050).
 * Each bill keeps its own payments; this only adds them up. Shown when there is more than one.
 */
export default function StayBillsSummary({ ipdId }) {
  const [data, setData] = useState(null);

  const load = useCallback(async () => {
    try {
      setData(await hospitalService.getIpdRelatedBills(ipdId));
    } catch {
      setData(null);
    }
  }, [ipdId]);

  useEffect(() => {
    void load();
  }, [load]);
  useLiveRefresh(load);

  const bills = data?.bills || [];
  if (bills.length < 2) return null;

  return (
    <div
      className="mt-3 border border-gray-200 rounded-lg p-3 text-sm"
      aria-label="Bills for this stay"
    >
      <div className="font-semibold text-gray-800 mb-2">Bills for this stay</div>
      <ul className="space-y-1">
        {bills.map((b) => (
          <li key={b.billingId} className="flex items-center justify-between gap-2">
            <span className="text-gray-700">
              <span className="font-medium">{b.category}</span>
              {b.customId ? <span className="text-gray-400"> · {b.customId}</span> : null}
            </span>
            <span
              className={b.status === 'CANCELLED' ? 'text-gray-400 line-through' : 'text-gray-900'}
            >
              {inr(b.totalAmount)}
              <span className="ml-1 text-xs text-gray-500">
                {b.status === 'CLOSED' ? 'PAID' : b.status}
              </span>
            </span>
          </li>
        ))}
      </ul>
      <div className="mt-2 pt-2 border-t border-gray-100 space-y-0.5">
        <div className="flex justify-between font-semibold">
          <span>
            Total (
            {bills
              .filter((b) => b.status !== 'CANCELLED')
              .map((b) => b.category)
              .join(' + ')}
            )
          </span>
          <span>{inr(data.totalAmount)}</span>
        </div>
        <div className="flex justify-between text-gray-600">
          <span>Paid</span>
          <span>{inr(data.paidAmount)}</span>
        </div>
        <div className="flex justify-between text-gray-900">
          <span>Balance</span>
          <span>{inr(data.balance)}</span>
        </div>
      </div>
    </div>
  );
}
