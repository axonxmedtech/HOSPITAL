/**
 * A hospital's commercial state in a list row: "Demo · 3d left", "Demo ended", "Grace period",
 * "Expired", "Expiring soon" or "Paid". Works from the hospital row itself (isDemo, demoEndsAt and
 * the subscription status the hourly job keeps current), so the list needs no extra requests.
 */
export function subscriptionBadge(h) {
  if (h?.isDemo) {
    const end = h.demoEndsAt ? new Date(h.demoEndsAt) : null;
    if (end && end.getTime() <= Date.now())
      return ['Demo ended', 'bg-red-100 text-red-700 border-red-200'];
    if (end) {
      const days = Math.max(0, Math.ceil((end.getTime() - Date.now()) / 86400000));
      return [`Demo · ${days}d left`, 'bg-amber-100 text-amber-800 border-amber-200'];
    }
    return ['Demo', 'bg-amber-100 text-amber-800 border-amber-200'];
  }
  switch (h?.subscriptionStatus) {
    case 'EXPIRED':
      return ['Expired', 'bg-red-100 text-red-700 border-red-200'];
    case 'GRACE':
      return ['Grace period', 'bg-orange-100 text-orange-800 border-orange-200'];
    case 'WARNING':
      return ['Expiring soon', 'bg-amber-50 text-amber-800 border-amber-200'];
    default:
      return ['Paid', 'bg-emerald-50 text-emerald-700 border-emerald-200'];
  }
}

export default function SubscriptionBadge({ hospital }) {
  const [text, tone] = subscriptionBadge(hospital);
  return (
    <span
      className={`px-2.5 py-1 inline-flex text-xs leading-5 font-semibold rounded-full border whitespace-nowrap ${tone}`}
    >
      {text}
    </span>
  );
}
