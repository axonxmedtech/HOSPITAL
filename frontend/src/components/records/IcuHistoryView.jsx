import { useNavigate } from 'react-router-dom';
import { formatDays, formatWhen } from '../../utils/date';
import RecordsFilterBar from '../RecordsFilterBar';
import HistoryRows from './HistoryRows';
import { Pager } from './IpdHistoryView';
import { patientFilePath } from './patientFile';
import useHistoryList from './useHistoryList';

const VIEWS = [
  { id: 'CLOSED', label: 'Moved out' },
  { id: 'CURRENT', label: 'In ICU now' },
  { id: 'ALL', label: 'All' },
];

const DISPOSITION = {
  WARD: 'To ward',
  HOME: 'Discharged home',
  LAMA: 'LAMA',
  REFERRED_OUT: 'Referred out',
  EXPIRED: 'Expired',
};

/** ICU history: every ICU stay — in the unit now or already moved out. Read-only. */
export default function IcuHistoryView() {
  const navigate = useNavigate();
  const h = useHistoryList('/hospital/icu/history', { defaultView: 'CLOSED' });
  const rows = h.data.content || [];
  return (
    <div className="space-y-4">
      <RecordsFilterBar
        views={VIEWS}
        view={h.view}
        onViewChange={h.setView}
        range={h.range}
        onRangeChange={h.setRange}
        dateLabel={h.view === 'CLOSED' ? 'Date moved out of ICU' : 'ICU admission date'}
        search={h.search}
        onSearchChange={h.setSearch}
        searchPlaceholder="Search patient, phone or IPD no.…"
      />
      <p className="text-xs text-gray-500">
        {h.view === 'CLOSED'
          ? 'Dates are when the patient left the ICU.'
          : 'Dates are ICU admission dates.'}{' '}
        {h.data.totalElements ?? rows.length} stay
        {(h.data.totalElements ?? rows.length) === 1 ? '' : 's'}
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
              <th className="px-4 py-2">IPD No.</th>
              <th className="px-4 py-2">Unit</th>
              <th className="px-4 py-2">In ICU</th>
              <th className="px-4 py-2">Out of ICU</th>
              <th className="px-4 py-2">Days</th>
              <th className="px-4 py-2">Outcome</th>
              <th className="px-4 py-2">Doctor</th>
              <th className="px-4 py-2 sr-only">Open</th>
            </tr>
          </thead>
          <tbody>
            <HistoryRows
              loading={h.loading}
              count={rows.length}
              colSpan={9}
              emptyText="No ICU stays match these filters."
            >
              {rows.map((r) => (
                <tr key={r.publicId} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2">
                    {r.patient ? (
                      <button
                        type="button"
                        onClick={() => navigate(patientFilePath(r.patient.publicId))}
                        className="text-sky-700 font-semibold hover:underline text-left"
                        title="Open patient file"
                      >
                        {r.patient.name}
                      </button>
                    ) : (
                      '—'
                    )}
                    {r.patient?.phone && (
                      <div className="text-xs text-gray-500">{r.patient.phone}</div>
                    )}
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">{r.ipdNumber || '—'}</td>
                  <td className="px-4 py-2">{r.unitName || '—'}</td>
                  <td className="px-4 py-2 whitespace-nowrap">{formatWhen(r.admittedAt)}</td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {r.dischargedAt ? formatWhen(r.dischargedAt) : 'Still in ICU'}
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">{formatDays(r.days)}</td>
                  <td className="px-4 py-2">
                    {DISPOSITION[r.disposition] || r.disposition || '—'}
                  </td>
                  <td className="px-4 py-2">{r.intensivistName || r.doctorName || '—'}</td>
                  <td className="px-4 py-2 text-right">
                    <button
                      type="button"
                      onClick={() => navigate(`/ipd/${r.ipdAdmissionId}`)}
                      className="text-sm font-semibold text-sky-700 hover:underline whitespace-nowrap"
                    >
                      Open case
                    </button>
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
