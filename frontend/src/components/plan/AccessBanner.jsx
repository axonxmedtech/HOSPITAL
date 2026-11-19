import useAccessStatus from '../../hooks/useAccessStatus';

/** Which states get a banner, and how loud it is. A running demo only gets the label. */
const TONE = {
  DEMO_ENDED: 'bg-red-50 border-red-200 text-red-800',
  EXPIRED: 'bg-red-50 border-red-200 text-red-800',
  GRACE: 'bg-amber-50 border-amber-200 text-amber-900',
  EXPIRING: 'bg-amber-50 border-amber-200 text-amber-900',
};

/** A strip under the navbar when the demo has ended or the plan is expiring / expired. */
export default function AccessBanner() {
  const status = useAccessStatus();
  const tone = status && TONE[status.state];
  if (!tone || !status.message) return null;
  return (
    <output className={`block border-b px-4 sm:px-6 py-2 text-sm ${tone}`}>
      {status.message}
      {status.supportPhone && (
        <a
          href={`tel:${status.supportPhone}`}
          className="ml-2 font-semibold underline whitespace-nowrap"
        >
          Call {status.supportPhone}
        </a>
      )}
    </output>
  );
}
