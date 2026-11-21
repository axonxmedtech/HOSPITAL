import { useNavigate } from 'react-router-dom';
import { formatWhen } from '../../utils/date';
import RecordsFilterBar from '../RecordsFilterBar';
import HistoryRows from './HistoryRows';
import { Pager } from './IpdHistoryView';
import { patientFilePath } from './patientFile';
import useHistoryList from './useHistoryList';

const VIEWS = [
  { id: 'DONE', label: 'Completed' },
  { id: 'CANCELLED', label: 'Cancelled' },
  { id: 'ACTIVE', label: 'Open' },
  { id: 'ALL', label: 'All' },
];

const STATUS_LABEL = {
  REQUESTED: 'Requested',
  APPROVED: 'Approved',
  SCHEDULED: 'Scheduled',
  PRE_OP: 'Pre-op',
  IN_PROGRESS: 'In progress',
  POSTPONED: 'Postponed',
  COMPLETED: 'Completed',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
};

/** OT history: every surgery — completed, closed, cancelled or still open. Read-only. */
export default function OtHistoryView() {
  const navigate = useNavigate();
  const h = useHistoryList('/hospital/surgeries/history', { defaultView: 'DONE' });
  const rows = h.data.content || [];
  return (
    <div className="space-y-4">
      <RecordsFilterBar
        views={VIEWS}
        view={h.view}
        onViewChange={h.setView}
        range={h.range}
        onRangeChange={h.setRange}
        dateLabel="Operation date"
        search={h.search}
        onSearchChange={h.setSearch}
        searchPlaceholder="Search patient, phone or procedure…"
      />
      <p className="text-xs text-gray-500">
        Dates are operation dates (or the request date when never scheduled).{' '}
        {h.data.totalElements ?? rows.length} surger
        {(h.data.totalElements ?? rows.length) === 1 ? 'y' : 'ies'}
      </p>
      {h.error && (
        <div
          role="alert"
          className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg p-3"
        >
          {h.error}
        </div>
      )}
      <div className="overflow-x-auto border border-gray-200 rounded-xl bg-white">
        <table className="w-full text-sm text-left">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2">Patient</th>
              <th className="px-4 py-2">Procedure</th>
              <th className="px-4 py-2">Surgeon</th>
              <th className="px-4 py-2">Theatre</th>
              <th className="px-4 py-2">Operation date</th>
              <th className="px-4 py-2">Completed</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2 sr-only">Open</th>
            </tr>
          </thead>
          <tbody>
            <HistoryRows
              loading={h.loading}
              count={rows.length}
              colSpan={8}
              emptyText="No surgeries match these filters."
            >
              {rows.map((s) => (
                <tr key={s.publicId} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2">
                    {s.patientPublicId ? (
                      <button
                        type="button"
                        onClick={() => navigate(patientFilePath(s.patientPublicId))}
                        className="text-sky-700 font-semibold hover:underline text-left"
                        title="Open patient file"
                      >
                        {s.patientName}
                      </button>
                    ) : (
                      s.patientName || '—'
                    )}
                    {s.ipdNumber && <div className="text-xs text-gray-500">{s.ipdNumber}</div>}
                  </td>
                  <td className="px-4 py-2">{s.procedureName}</td>
                  <td className="px-4 py-2">{s.surgeonName || '—'}</td>
                  <td className="px-4 py-2">{s.otRoomName || s.otWardName || '—'}</td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {formatWhen(s.scheduledAt || s.requestedAt)}
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {s.completedAt ? formatWhen(s.completedAt) : '—'}
                  </td>
                  <td className="px-4 py-2">{STATUS_LABEL[s.status] || s.status}</td>
                  <td className="px-4 py-2 text-right">
                    {s.ipdAdmissionId && (
                      <button
                        type="button"
                        onClick={() => navigate(`/ipd/${s.ipdAdmissionId}`)}
                        className="text-sm font-semibold text-sky-700 hover:underline whitespace-nowrap"
                      >
                        Open case
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </HistoryRows>
          </tbody>
        </table>
      </div>
      <Pager page={h.page} totalPages={h.data.totalPages || 0} onPage={h.setPage} />
    </div>
  );
}
