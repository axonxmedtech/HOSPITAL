import useAccessStatus from '../../hooks/useAccessStatus';

const daysText = (d) => (d === 1 ? '1 day left' : `${d} days left`);

/**
 * The DEMO label shown beside the hospital name: "DEMO · 5 days left", or "DEMO ENDED".
 * Renders nothing for a regular paid account.
 */
export default function AccessLabel({ className = '' }) {
  const status = useAccessStatus();
  if (!status?.demo) return null;
  const ended = status.state === 'DEMO_ENDED';
  const text = ended
    ? 'Demo ended'
    : status.daysLeft >= 0
      ? `Demo · ${daysText(status.daysLeft)}`
      : 'Demo';
  return (
    <span
      title={status.message || undefined}
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold uppercase tracking-wide whitespace-nowrap ${
        ended ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-800'
      } ${className}`}
    >
      {text}
    </span>
  );
}
