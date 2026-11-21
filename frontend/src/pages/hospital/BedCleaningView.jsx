import { useCallback, useEffect, useMemo, useState } from 'react';
import { useConfirm } from '../../context/ConfirmContext';
import { useToast } from '../../context/ToastContext';
import useLiveRefresh from '../../hooks/useLiveRefresh';
import icuWardService from '../../services/icuWardService';
import wardService from '../../services/wardService';
import { extractApiError } from '../../utils/apiError';

/**
 * Reception's bed board: every ward's beds with their state, and the cleaning turnover.
 *
 * A bed vacated by a discharge, a transfer or a surgery goes to "Needs cleaning" and cannot be
 * given to anyone until it is marked cleaned — then it is Available again. Every change is
 * recorded in the bed's status history on the server, with who did it and when.
 */
const STATUS = {
  cleaning: { label: 'Needs cleaning', badge: 'bg-amber-100 text-amber-800 border-amber-200' },
  available: { label: 'Available', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  occupied: { label: 'Occupied', badge: 'bg-rose-50 text-rose-700 border-rose-200' },
  maintenance: { label: 'Maintenance', badge: 'bg-gray-100 text-gray-600 border-gray-200' },
};

const FILTERS = [
  { id: 'cleaning', label: 'Needs cleaning' },
  { id: 'all', label: 'All beds' },
];

function waited(since) {
  if (!since) return null;
  const mins = Math.max(0, Math.round((Date.now() - new Date(since).getTime()) / 60000));
  if (mins < 60) return `${mins} min`;
  const h = Math.floor(mins / 60);
  if (h < 24) return `${h} h ${mins % 60} min`;
  return `${Math.floor(h / 24)} d ${h % 24} h`;
}

export default function BedCleaningView() {
  const confirm = useConfirm();
  const { success, error: toastError } = useToast();
  const [wards, setWards] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('cleaning');
  const [busyBed, setBusyBed] = useState(null);

  const load = useCallback(
    async (silent = false) => {
      if (!silent) setLoading(true);
      try {
        // General wards and ICU units alike: a bed vacated in either must be cleaned.
        const [general, icu] = await Promise.all([
          wardService.getWards().catch(() => []),
          icuWardService.getIcuWards().catch(() => []),
        ]);
        const list = [
          ...(general || []).map((w) => ({
            id: w.wardId || w.id,
            name: w.wardName,
            type: w.wardType,
          })),
          ...(icu || []).map((w) => ({
            id: w.wardId,
            name: w.wardName,
            type: w.unitType || 'ICU',
          })),
        ].filter((w, i, all) => w.id && all.findIndex((x) => x.id === w.id) === i);
        const withBeds = await Promise.all(
          list.map(async (w) => ({
            ...w,
            beds: (await wardService.getBeds(w.id).catch(() => [])) || [],
          }))
        );
        setWards(withBeds);
      } catch (e) {
        if (!silent) toastError(extractApiError(e, 'Could not load beds'));
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [toastError]
  );

  useEffect(() => {
    void load();
  }, [load]);
  useLiveRefresh(() => load(true));

  const counts = useMemo(() => {
    const c = { cleaning: 0, available: 0, occupied: 0, maintenance: 0 };
    wards.forEach((w) =>
      w.beds.forEach((b) => {
        const s = (b.status || '').toLowerCase();
        if (s in c) c[s] += 1;
      })
    );
    return c;
  }, [wards]);

  const markCleaned = async (bed, wardName) => {
    if (busyBed) return;
    const ok = await confirm({
      title: `Mark ${bed.bedCode} cleaned?`,
      message: `${wardName}: the bed becomes Available and can be given to the next admission.`,
      confirmLabel: 'Mark cleaned',
    });
    if (!ok) return;
    setBusyBed(bed.bedId);
    try {
      await wardService.markBedCleaned(bed.bedId, 'Marked cleaned by reception');
      success(`${bed.bedCode} is available`);
      await load(true);
    } catch (e) {
      toastError(extractApiError(e, 'Could not mark the bed cleaned'));
    } finally {
      setBusyBed(null);
    }
  };

  const visible = wards
    .map((w) => ({
      ...w,
      beds: w.beds.filter((b) => filter === 'all' || (b.status || '').toLowerCase() === filter),
    }))
    .filter((w) => w.beds.length > 0);

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Beds</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            A vacated bed needs cleaning before anyone can be admitted to it.
          </p>
        </div>
        <div className="inline-flex p-1 bg-gray-100 rounded-xl" role="group" aria-label="Show">
          {FILTERS.map((f) => (
            <button
              key={f.id}
              type="button"
              aria-pressed={filter === f.id}
              onClick={() => setFilter(f.id)}
              className={`px-4 py-1.5 text-sm font-semibold rounded-lg ${
                filter === f.id ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
              }`}
            >
              {f.label}
              {f.id === 'cleaning' ? ` (${counts.cleaning})` : ''}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {Object.entries(STATUS).map(([key, meta]) => (
          <div key={key} className="bg-white border border-gray-200 rounded-xl px-4 py-3">
            <div className="text-xs font-semibold text-gray-500">{meta.label}</div>
            <div className="text-2xl font-bold text-gray-900">{counts[key]}</div>
          </div>
        ))}
      </div>

      {loading && <div className="text-sm text-gray-500">Loading beds…</div>}
      {!loading && visible.length === 0 && (
        <div className="bg-white border border-gray-200 rounded-xl p-8 text-center text-sm text-gray-500">
          {filter === 'cleaning' ? 'No bed is waiting for cleaning.' : 'No beds are set up yet.'}
        </div>
      )}

      {visible.map((w) => (
        <section key={w.id} className="bg-white border border-gray-200 rounded-xl">
          <h3 className="px-4 py-3 border-b border-gray-100 font-semibold text-gray-900 text-sm">
            {w.name}
            {w.type ? (
              <span className="ml-2 text-xs font-normal text-gray-500">{w.type}</span>
            ) : null}
          </h3>
          <ul className="divide-y divide-gray-100">
            {w.beds.map((b) => {
              const key = (b.status || '').toLowerCase();
              const meta = STATUS[key] || { label: b.status, badge: 'bg-gray-100 text-gray-600' };
              const since = waited(b.statusSince);
              return (
                <li key={b.bedId} className="px-4 py-3 flex items-center gap-3 flex-wrap">
                  <span className="font-semibold text-gray-900 text-sm min-w-[8rem]">
                    {b.bedCode}
                  </span>
                  <span
                    className={`px-2 py-0.5 text-xs font-semibold rounded-full border ${meta.badge}`}
                  >
                    {meta.label}
                  </span>
                  {since && (
                    <span className="text-xs text-gray-500">
                      {key === 'cleaning' ? `waiting ${since}` : `for ${since}`}
                    </span>
                  )}
                  {key === 'cleaning' && (
                    <button
                      type="button"
                      onClick={() => markCleaned(b, w.name)}
                      disabled={busyBed === b.bedId}
                      className="ml-auto px-3 py-1.5 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50"
                    >
                      {busyBed === b.bedId ? 'Saving…' : 'Mark cleaned'}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}
