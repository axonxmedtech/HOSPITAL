const labels = [
  ['total', 'Total rows'],
  ['created', 'Created'],
  ['updated', 'Updated'],
  ['skipped', 'Skipped'],
  ['needsReview', 'Needs review'],
  ['failed', 'Failed'],
];
export default function ImportSummary({ counts, preview = false }) {
  if (!counts) return null;
  return (
    <dl
      className="grid grid-cols-2 md:grid-cols-6 gap-3"
      aria-label={preview ? 'Preview counts' : 'Import counts'}
    >
      {labels.map(([key, label]) => (
        <div key={key} className="rounded-xl border border-gray-200 bg-gray-50 p-4">
          <dt className="text-sm text-slate-600">
            {preview && ['created', 'updated'].includes(key)
              ? `Would be ${label.toLowerCase()}`
              : label}
          </dt>
          <dd className="mt-1 text-2xl font-semibold text-slate-800">{counts[key] ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
