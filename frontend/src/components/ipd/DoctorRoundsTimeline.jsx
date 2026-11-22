import { useEffect, useState } from 'react';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';

const when = (at) =>
  at
    ? new Date(at).toLocaleString(undefined, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';

/**
 * The doctor's rounds on one admission, newest first, each with its date and time. Read-only:
 * the doctor adds a round from the case page; the ward reads it here.
 */
export default function DoctorRoundsTimeline({ admissionId, refreshKey = 0 }) {
  const [rounds, setRounds] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;
    hospitalService
      .getIpdRounds(admissionId)
      .then((list) => alive && setRounds(list || []))
      .catch((err) => alive && setError(extractApiError(err, 'Could not load the rounds')));
    return () => {
      alive = false;
    };
  }, [admissionId, refreshKey]);

  if (error) return <p className="text-sm text-red-700">{error}</p>;
  if (rounds === null) return <p className="text-sm text-gray-500">Loading rounds…</p>;
  if (rounds.length === 0) return <p className="text-sm text-gray-500">No rounds recorded yet.</p>;

  return (
    <ol className="space-y-2">
      {rounds.map((r, i) => (
        <li key={`${r.at}-${i}`} className="p-2 border rounded">
          <div className="text-xs text-gray-500">
            {when(r.at)}
            {r.doctor ? ` · ${r.doctor}` : ''}
          </div>
          {r.diagnosis && <div className="text-sm font-medium">{r.diagnosis}</div>}
          {r.notes && <div className="text-sm text-gray-700 whitespace-pre-line">{r.notes}</div>}
        </li>
      ))}
    </ol>
  );
}
