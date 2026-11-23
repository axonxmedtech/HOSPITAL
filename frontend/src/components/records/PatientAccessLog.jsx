import { useEffect, useState } from 'react';
import apiClient from '../../services/apiService';
import { extractApiError } from '../../utils/apiError';
import { formatWhen } from '../../utils/date';
import { Pager } from './IpdHistoryView';

/** What was opened, in the words the staff use. */
const RECORD_LABELS = {
  PATIENT_FILE: 'Patient file',
  PATIENT_DETAILS: 'Patient details',
  CLINICAL_TIMELINE: 'Clinical timeline',
  CONSULTATION_DETAILS: 'Consultation history',
  CONSULTATION: 'Consultation',
  OPD_VISIT: 'OPD visit',
  CASE_PAPER: 'Case paper',
  OPD_DOCUMENTS: 'OPD documents',
  OPD_MEDICINES: 'OPD medicine list',
  PRESCRIPTION: 'Prescription',
  IPD_CASE: 'IPD case',
  IPD_MEDICINES: 'IPD medicine list',
  IPD_PRESCRIPTION: 'IPD prescription',
  NURSING_CHART: 'Nursing chart',
  ICU_STAY: 'ICU stay',
  BILLS: 'Bills',
  RECEIPT: 'Bill receipt',
  DOCUMENT_LIST: 'Documents list',
  DOCUMENT: 'Document',
};
const ACTION_LABELS = { VIEW: 'Opened', PRINT: 'Printed', DOWNLOAD: 'Downloaded' };
const ROLE_LABELS = {
  HOSPITAL_ADMIN: 'Admin',
  DOCTOR: 'Doctor',
  RECEPTIONIST: 'Reception',
  NURSE: 'Nurse',
  NURSE_INCHARGE: 'Nurse in-charge',
  PHARMACIST: 'Pharmacist',
  OT_INCHARGE: 'OT in-charge',
};

const readable = (map, v) => map[v] || (v ? v.replaceAll('_', ' ').toLowerCase() : '—');

/**
 * Who opened, printed or downloaded this patient's records (hospital admin only). The server
 * counts a quick re-open of the same record by the same person as one entry.
 */
export default function PatientAccessLog({ publicId }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let live = true;
    apiClient
      .get(`/hospital/patients/${encodeURIComponent(publicId)}/access-log`, {
        params: { page, size: 25 },
      })
      .then((r) => {
        if (!live) return;
        setData(r.data);
        setError('');
      })
      .catch((e) => live && setError(extractApiError(e, 'Could not load the access log')));
    return () => {
      live = false;
    };
  }, [publicId, page]);

  const rows = data?.content || [];
  let body;
  if (error) {
    body = (
      <p className="px-5 py-4 text-sm text-red-700" role="alert">
        {error}
      </p>
    );
  } else if (!data) {
    body = <p className="px-5 py-4 text-sm text-gray-500">Loading…</p>;
  } else if (rows.length === 0) {
    body = (
      <p className="px-5 py-4 text-sm text-gray-500">
        No one has opened this patient’s records yet.
      </p>
    );
  } else {
    body = (
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
            <tr>
              <th className="px-5 py-2.5">When</th>
              <th className="px-5 py-2.5">Who</th>
              <th className="px-5 py-2.5">Did what</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.map((r, i) => (
              <tr key={`${r.accessedAt}-${r.userEmail}-${r.recordType}-${i}`}>
                <td className="px-5 py-2.5 whitespace-nowrap text-gray-600">
                  {formatWhen(r.accessedAt)}
                </td>
                <td className="px-5 py-2.5">
                  <span className="font-medium text-gray-900">{r.userName}</span>
                  <span className="block text-xs text-gray-500">
                    {readable(ROLE_LABELS, r.userRole)}
                    {r.userName !== r.userEmail ? ` · ${r.userEmail}` : ''}
                  </span>
                </td>
                <td className="px-5 py-2.5 text-gray-800">
                  {readable(ACTION_LABELS, r.action)}{' '}
                  {readable(RECORD_LABELS, r.recordType).toLowerCase()}
                  {r.recordRef &&
                    r.recordType !== 'PATIENT_FILE' &&
                    r.recordType !== 'PATIENT_DETAILS' && (
                      <span className="text-gray-500"> ({r.recordRef})</span>
                    )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <section className="bg-white rounded-xl border border-gray-200 shadow-sm">
      <header className="px-5 py-3 border-b border-gray-100">
        <h2 className="font-semibold text-gray-900">
          Access log{data ? ` (${data.totalElements})` : ''}
        </h2>
        <p className="text-xs text-gray-500 mt-0.5">
          Everyone who opened, printed or downloaded this patient’s records. Only admins see this.
        </p>
      </header>
      {body}
      {data && (
        <div className="px-5 py-3">
          <Pager page={page} totalPages={data.totalPages || 0} onPage={setPage} />
        </div>
      )}
    </section>
  );
}
