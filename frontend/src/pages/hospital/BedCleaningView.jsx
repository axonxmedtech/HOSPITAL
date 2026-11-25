import { useCallback, useEffect, useMemo, useState } from 'react';
import Button from '../../components/Button';
import IcuAdmitModal from '../../components/IcuAdmitModal';
import IpdAdmitModal from '../../components/IpdAdmitModal';
import { ReservationsList, ReserveBedModal, holdLabel } from '../../components/ipd/BedReservations';
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
  available: { label: 'Available', badge: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  cleaning: { label: 'Needs cleaning', badge: 'bg-amber-50 text-amber-800 border-amber-200' },
  occupied: { label: 'Occupied', badge: 'bg-rose-50 text-rose-700 border-rose-200' },
  maintenance: { label: 'Maintenance', badge: 'bg-slate-100 text-slate-600 border-slate-200' },
};

/** The desk's two questions: which beds can take a patient, and which need cleaning first. */
const FILTERS = [
  { id: 'available', label: 'Free beds' },
  { id: 'cleaning', label: 'Needs cleaning' },
  { id: 'all', label: 'All beds' },
];

/**
 * An operating theatre is booked through the OT module, never admitted into. Same rule the OT
 * screens use for wards that predate OT Theatres ("OT" in the name), as a whole word.
 */
export const isTheatreWard = (name) => /\bOT\b|operation theat(re|er)/i.test(name || '');

const EMPTY_TEXT = {
  available: 'No bed is free right now.',
  cleaning: 'No bed is waiting for cleaning.',
  all: 'No beds are set up yet.',
};

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
  const [filter, setFilter] = useState('available');
  const [busyBed, setBusyBed] = useState(null);
  const [reservations, setReservations] = useState([]);
  const [reserving, setReserving] = useState(null); // { bed, wardName }
  const [admitting, setAdmitting] = useState(null); // { wardId, bedId, isIcu }

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
        ]
          .filter((w, i, all) => w.id && all.findIndex((x) => x.id === w.id) === i)
          // An ICU bed is admitted through the ICU form (the general one lists ward beds only).
          .map((w) => ({ ...w, isIcu: (icu || []).some((u) => u.wardId === w.id) }));
        const withBeds = await Promise.all(
          list.map(async (w) => ({
            ...w,
            beds: (await wardService.getBeds(w.id).catch(() => [])) || [],
          }))
        );
        setWards(withBeds);
        setReservations((await wardService.getReservations().catch(() => [])) || []);
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
          <h2 className="text-2xl font-bold text-slate-900">Beds</h2>
          <p className="text-sm text-slate-500 mt-0.5">
            Admit straight into a free bed. A vacated bed needs cleaning before anyone can use it.
          </p>
        </div>
        <fieldset className="flex flex-wrap gap-2 border-0 p-0 m-0 min-w-0">
          <legend className="sr-only">Show</legend>
          {FILTERS.map((f) => {
            const active = filter === f.id;
            const n = f.id === 'all' ? null : counts[f.id];
            const urgent = f.id === 'cleaning' && n > 0;
            return (
              <button
                key={f.id}
                type="button"
                aria-pressed={active}
                onClick={() => setFilter(f.id)}
                className={`inline-flex items-center gap-1.5 h-8 px-3 rounded-full text-xs font-semibold border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${
                  active
                    ? 'bg-primary-50 text-primary-700 border-primary-300'
                    : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                }`}
              >
                {f.label}
                {n != null && (
                  <span
                    className={`min-w-5 px-1.5 rounded-full text-[11px] leading-5 text-center ${
                      urgent ? 'bg-amber-500 text-white' : 'bg-slate-100'
                    }`}
                  >
                    {n}
                  </span>
                )}
              </button>
            );
          })}
        </fieldset>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {Object.entries(STATUS).map(([key, meta]) => (
          <div key={key} className="bg-white border border-slate-200 rounded-2xl px-4 py-3">
            <div className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
              {meta.label}
            </div>
            <div className="text-2xl font-bold text-slate-900 mt-1">{counts[key]}</div>
          </div>
        ))}
      </div>

      <ReservationsList reservations={reservations} onChanged={() => load(true)} />

      {loading && <div className="text-sm text-slate-500">Loading beds…</div>}
      {!loading && visible.length === 0 && (
        <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center text-sm text-slate-500">
          {EMPTY_TEXT[filter]}
        </div>
      )}

      {visible.map((w) => (
        <section key={w.id} className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
          <h3 className="px-4 py-3 bg-slate-50 border-b border-slate-200 font-semibold text-slate-900 text-sm">
            {w.name}
            {w.type ? (
              <span className="ml-2 text-xs font-normal text-slate-500">{w.type}</span>
            ) : null}
          </h3>
          <ul className="divide-y divide-slate-100">
            {w.beds.map((b) => {
              const key = (b.status || '').toLowerCase();
              const meta = STATUS[key] || {
                label: b.status,
                badge: 'bg-slate-100 text-slate-600 border-slate-200',
              };
              const since = waited(b.statusSince);
              return (
                <li key={b.bedId} className="px-4 py-3 flex items-center gap-3 flex-wrap">
                  <span className="font-semibold text-slate-900 text-sm min-w-[8rem]">
                    {b.bedCode}
                  </span>
                  <span
                    className={`px-2 py-0.5 text-xs font-medium rounded-full border ${meta.badge}`}
                  >
                    {meta.label}
                  </span>
                  {since && (
                    <span className="text-xs text-slate-500">
                      {key === 'cleaning' ? `waiting ${since}` : `for ${since}`}
                    </span>
                  )}
                  {b.reservedFor && <span className="text-xs text-sky-700">{holdLabel(b)}</span>}
                  {key === 'available' && !isTheatreWard(w.name) && (
                    <div className="ml-auto flex items-center gap-2">
                      {!b.reservedFor && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => setReserving({ bed: b, wardName: w.name })}
                        >
                          Reserve
                        </Button>
                      )}
                      <Button
                        size="sm"
                        onClick={() =>
                          setAdmitting({ wardId: w.id, bedId: b.bedId, isIcu: w.isIcu })
                        }
                      >
                        {b.reservedFor ? 'Admit reserved patient' : 'Admit here'}
                      </Button>
                    </div>
                  )}
                  {key === 'cleaning' && (
                    <Button
                      size="sm"
                      variant="success"
                      className="ml-auto"
                      loading={busyBed === b.bedId}
                      onClick={() => markCleaned(b, w.name)}
                    >
                      Mark cleaned
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      ))}
      {admitting &&
        (admitting.isIcu ? (
          <IcuAdmitModal
            isOpen
            initialBed={admitting}
            onClose={() => setAdmitting(null)}
            onSuccess={() => {
              setAdmitting(null);
              load(true);
            }}
          />
        ) : (
          <IpdAdmitModal
            isOpen
            initialBed={admitting}
            onClose={() => setAdmitting(null)}
            onSuccess={() => {
              setAdmitting(null);
              load(true);
            }}
          />
        ))}
      {reserving && (
        <ReserveBedModal
          bed={reserving.bed}
          wardName={reserving.wardName}
          onClose={() => setReserving(null)}
          onDone={async () => {
            setReserving(null);
            await load(true);
          }}
        />
      )}
    </div>
  );
}
