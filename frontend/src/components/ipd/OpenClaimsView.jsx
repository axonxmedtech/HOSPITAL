import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';
import { CLAIM_STATUS } from './InsurancePanel';

const rupees = (n) => (n == null ? '—' : `₹${Number(n).toLocaleString('en-IN')}`);

/** The TPA desk: every open insurance claim, most recently changed first. */
export default function OpenClaimsView() {
  const { error: toastError } = useToast();
  const navigate = useNavigate();
  const [claims, setClaims] = useState(null);

  useEffect(() => {
    hospitalService
      .getOpenClaims()
      .then((list) => setClaims(list || []))
      .catch((err) => {
        toastError(extractApiError(err, 'Could not load insurance claims'));
        setClaims([]);
      });
  }, [toastError]);

  if (claims === null) return <p className="text-sm text-gray-500 p-4">Loading…</p>;
  if (claims.length === 0)
    return <p className="text-sm text-gray-500 p-4">No open insurance claims.</p>;

  return (
    <div className="bg-white border rounded-xl overflow-x-auto">
      <table className="min-w-full text-sm">
        <thead className="bg-gray-50 text-left text-xs text-gray-500 uppercase">
          <tr>
            <th className="px-4 py-2">IPD</th>
            <th className="px-4 py-2">Insurer / TPA</th>
            <th className="px-4 py-2">Claim no.</th>
            <th className="px-4 py-2">Status</th>
            <th className="px-4 py-2 text-right">Approved</th>
            <th className="px-4 py-2" />
          </tr>
        </thead>
        <tbody className="divide-y">
          {claims.map((c) => {
            const st = CLAIM_STATUS[c.status] || CLAIM_STATUS.DRAFT;
            return (
              <tr key={c.publicId}>
                <td className="px-4 py-2">
                  {c.ipdNumber}
                  {c.admissionStatus === 'DISCHARGED' && (
                    <span className="ml-1 text-xs text-gray-500">(discharged)</span>
                  )}
                </td>
                <td className="px-4 py-2">
                  {c.insurerName}
                  {c.tpaName ? <span className="text-gray-500"> via {c.tpaName}</span> : null}
                </td>
                <td className="px-4 py-2">{c.claimNumber || '—'}</td>
                <td className="px-4 py-2">
                  <span className={`text-xs px-2 py-0.5 rounded-full ${st.cls}`}>{st.label}</span>
                </td>
                <td className="px-4 py-2 text-right">{rupees(c.approvedAmount)}</td>
                <td className="px-4 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => navigate(`/ipd/${c.ipdAdmissionId}`)}
                    className="text-sky-700 hover:underline"
                  >
                    Open
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
