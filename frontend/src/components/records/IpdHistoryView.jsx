import { useNavigate } from 'react-router-dom';
import { formatWhen } from '../../utils/date';
import RecordsFilterBar from '../RecordsFilterBar';
import { patientFilePath } from './patientFile';
import useHistoryList from './useHistoryList';

const VIEWS = [
  { id: 'DISCHARGED', label: 'Discharged' },
  { id: 'CURRENT', label: 'Admitted now' },
  { id: 'CANCELLED', label: 'Cancelled' },
  { id: 'ALL', label: 'All' },
];

const STATUS_LABEL = {
  ADMITTED: 'Admitted',
  DISCHARGE_PLANNED: 'Discharge planned',
  DISCHARGED: 'Discharged',
  CANCELLED: 'Cancelled',
};

const STATUS_STYLE = {
  ADMITTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  DISCHARGE_PLANNED: 'bg-amber-50 text-amber-700 border-amber-200',
  DISCHARGED: 'bg-gray-100 text-gray-700 border-gray-200',
  CANCELLED: 'bg-red-50 text-red-700 border-red-200',
};

/**
 * IPD history: every admission — discharged, current, cancelled — with a date window and a
 * search, newest first. Read-only; opening a row shows the case (editing is closed once the
 * patient has left). A doctor sees their own patients; the server decides.
 */
export default function IpdHistoryView() {
  const navigate = useNavigate();
  const { view, setView, range, setRange, search, setSearch, page, setPage, data, loading, error } =
    useHistoryList('/hospital/ipd', { defaultView: 'DISCHARGED' });
  const rows = data.content || [];
  return (
    <div className="p-4 space-y-4">
      <RecordsFilterBar
        views={VIEWS}
        view={view}
        onViewChange={setView}
        range={range}
        onRangeChange={setRange}
        dateLabel={view === 'DISCHARGED' ? 'Discharge date' : 'Admission date'}
        search={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search patient, phone or IPD no.…"
      />
      <p className="text-xs text-gray-500">
        {view === 'DISCHARGED' ? 'Dates are discharge dates.' : 'Dates are admission dates.'}{' '}
        {data.totalElements ?? rows.length} record
        {(data.totalElements ?? rows.length) === 1 ? '' : 's'}
      </p>

      {error && (
        <div
          role="alert"
          className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg p-3"
        >
          {error}
        </div>
      )}

      <div className="overflow-x-auto border border-gray-200 rounded-xl">
        <table className="w-full text-sm text-left">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2">IPD No.</th>
              <th className="px-4 py-2">Patient</th>
              <th className="px-4 py-2">Doctor</th>
              <th className="px-4 py-2">Ward / Bed</th>
              <th className="px-4 py-2">Admitted</th>
              <th className="px-4 py-2">Discharged</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2 sr-only">Open</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-gray-500">
                  Loading…
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-gray-500">
                  No admissions match these filters.
                </td>
              </tr>
            ) : (
              rows.map(({ ipd, patient, doctor, ward, bed }) => (
                <tr key={ipd.id} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2 font-medium text-gray-900 whitespace-nowrap">
                    {ipd.ipdNumber}
                  </td>
                  <td className="px-4 py-2">
                    {patient ? (
                      <button
                        type="button"
                        onClick={() => navigate(patientFilePath(patient.publicId))}
                        className="text-sky-700 font-semibold hover:underline text-left"
                        title="Open patient file"
                      >
                        {patient.name}
                      </button>
                    ) : (
                      '—'
                    )}
                    {patient?.phone && <div className="text-xs text-gray-500">{patient.phone}</div>}
                  </td>
                  <td className="px-4 py-2">{doctor?.name || '—'}</td>
                  <td className="px-4 py-2">
                    {ward?.wardName || '—'}
                    {bed?.bedCode && <div className="text-xs text-gray-500">Bed {bed.bedCode}</div>}
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {formatWhen(ipd.admissionDatetime)}
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {ipd.dischargeDatetime ? formatWhen(ipd.dischargeDatetime) : '—'}
                  </td>
                  <td className="px-4 py-2">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full border text-xs font-semibold ${
                        STATUS_STYLE[ipd.status] || 'bg-gray-50 text-gray-700 border-gray-200'
                      }`}
                    >
                      {STATUS_LABEL[ipd.status] || ipd.status}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-right">
                    <button
                      type="button"
                      onClick={() => navigate(`/ipd/${ipd.id}`)}
                      className="text-sm font-semibold text-sky-700 hover:underline whitespace-nowrap"
                    >
                      Open case
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <Pager page={page} totalPages={data.totalPages || 0} onPage={setPage} />
    </div>
  );
}

export function Pager({ page, totalPages, onPage }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-2 text-sm">
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
        className="px-3 py-1.5 rounded-lg border border-gray-300 disabled:opacity-40"
      >
        Previous
      </button>
      <span className="text-gray-600">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
        className="px-3 py-1.5 rounded-lg border border-gray-300 disabled:opacity-40"
      >
        Next
      </button>
    </div>
  );
}
