import { useEffect, useState } from 'react';
import hospitalService from '../../services/hospitalService';
import nurseService from '../../services/nurseService';
import { FOOD_TIMING_OPTIONS, isFoodTimingApplicable } from '../../utils/foodTiming';
import FrequencyInput from '../FrequencyInput';
import { StockBadge } from '../stock/StockPicker';

const TYPES = ['TABLET', 'CAPSULE', 'SYRUP', 'INJECTION', 'IV_FLUID', 'OINTMENT', 'DROPS', 'INHALER'];
const ROUTES = ['ORAL', 'IV', 'IM'];

const todayStr = () => new Date().toLocaleDateString('en-CA');
const days = (text) => {
  const m = String(text || '').match(/\d+/);
  return m ? Number(m[0]) : '';
};

let seq = 0;
export const emptyDraft = () => ({
  key: `d${++seq}`,
  medicineId: null,
  hospitalItemName: null,
  medicineName: '',
  stock: null,
  type: 'TABLET',
  route: 'ORAL',
  dose: '',
  quantityPerDose: 1,
  frequency: '',
  durationDays: '',
  foodTiming: '',
});

/** A preset line ready to review: the name, dose, frequency and duration the preset holds. */
export const draftFromPreset = (item) => ({
  ...emptyDraft(),
  medicineId: item.medicineId || null,
  medicineName: item.medicineName || '',
  dose: item.dosage || '',
  frequency: item.frequency || '',
  durationDays: days(item.duration),
  quantityPerDose: item.quantity || 1,
});

/** What is missing before the order can be sent, or '' (the same rules as Prescribe Medicine). */
export const draftProblem = (d) => {
  if (!d.medicineName.trim()) return 'medicine name';
  if (!d.dose.trim()) return 'dose';
  if (!d.frequency.trim()) return 'frequency';
  if (!(Number(d.durationDays) > 0)) return 'duration (days)';
  return '';
};

export const draftToPayload = (d) => ({
  medicineId: d.medicineId,
  hospitalItemName: d.hospitalItemName,
  quantityPerDose: Number(d.quantityPerDose) || 1,
  medicineName: d.medicineName.trim(),
  type: d.type,
  route: d.route,
  dose: d.dose.trim(),
  frequency: d.frequency,
  durationDays: Number(d.durationDays),
  startDate: todayStr(),
  // Only where a meal time means something (never on an injection or IV line).
  foodTiming: isFoodTimingApplicable(d.type, d.route) ? d.foodTiming || null : null,
});

/**
 * One new medicine order as a compact row: search the catalogue and stock (medicines, saline,
 * IV fluids) or type a name the patient brings, then dose, frequency and duration. Picking from
 * the catalogue fills its default dose, frequency and duration. Several rows go out together
 * with the round.
 */
export default function MedicineDraftRow({ draft, onChange, onRemove, index }) {
  const [results, setResults] = useState({ meds: [], items: [], stockById: {} });
  const set = (patch) => onChange({ ...draft, ...patch });
  const picked = draft.medicineId || draft.hospitalItemName;

  useEffect(() => {
    const q = draft.medicineName || '';
    if (q.length < 3 || picked) {
      setResults({ meds: [], items: [], stockById: {} });
      return undefined;
    }
    const t = setTimeout(async () => {
      try {
        const [meds, stock] = await Promise.all([
          hospitalService.searchMedicines(q),
          nurseService.searchStock(q).catch(() => []),
        ]);
        const rows = Array.isArray(stock) ? stock : [];
        setResults({
          meds: meds || [],
          items: rows.filter((o) => o.source === 'HOSPITAL_ITEM'),
          stockById: Object.fromEntries(
            rows.filter((o) => o.source === 'MEDICINE').map((o) => [o.medicineId, o])
          ),
        });
      } catch {
        setResults({ meds: [], items: [], stockById: {} });
      }
    }, 400);
    return () => clearTimeout(t);
  }, [draft.medicineName, picked]);

  const pickMedicine = (m) => {
    const type = m.type?.toUpperCase() || draft.type;
    set({
      medicineId: m.id,
      hospitalItemName: null,
      medicineName: m.name,
      stock: results.stockById[m.id] || null,
      type,
      dose: m.defaultDosage || draft.dose,
      frequency: m.defaultFrequency || draft.frequency,
      durationDays: days(m.defaultDuration) || draft.durationDays,
      foodTiming: isFoodTimingApplicable(type, draft.route) ? draft.foodTiming : '',
    });
  };

  const pickItem = (o) => {
    const fluid = /fluid|saline|ringer|dextrose|iv/i.test(`${o.type} ${o.name}`);
    set({
      medicineId: null,
      hospitalItemName: o.itemName,
      medicineName: o.name,
      stock: o,
      type: fluid ? 'IV_FLUID' : draft.type,
      route: fluid ? 'IV' : draft.route,
      foodTiming: fluid ? '' : draft.foodTiming,
    });
  };

  const showList = results.meds.length > 0 || results.items.length > 0;
  const n = index + 1;
  const timing = isFoodTimingApplicable(draft.type, draft.route);

  return (
    <div className="border rounded-lg p-3 space-y-2 bg-white">
      <div className="flex gap-2 items-start">
        <div className="relative flex-1">
          <label htmlFor={`${draft.key}-name`} className="sr-only">
            Medicine {n}
          </label>
          <input
            id={`${draft.key}-name`}
            value={draft.medicineName}
            onChange={(e) =>
              set({ medicineName: e.target.value, medicineId: null, hospitalItemName: null, stock: null })
            }
            placeholder="Medicine (type 3 letters to search stock)"
            className="w-full border p-2 rounded text-sm"
          />
          {showList && (
            <div className="absolute z-20 mt-1 w-full max-h-56 overflow-y-auto bg-white border rounded shadow-lg text-sm">
              {results.items.map((o) => (
                <button
                  key={o.itemName}
                  type="button"
                  onClick={() => pickItem(o)}
                  className="w-full text-left px-3 py-1.5 hover:bg-gray-50 flex justify-between gap-2"
                >
                  <span>{o.name}</span>
                  <StockBadge option={o} />
                </button>
              ))}
              {results.meds.map((m) => (
                <button
                  key={m.id}
                  type="button"
                  onClick={() => pickMedicine(m)}
                  className="w-full text-left px-3 py-1.5 hover:bg-gray-50 flex justify-between gap-2"
                >
                  <span>{m.name}</span>
                  <span className="text-xs text-gray-500 flex items-center gap-1">
                    {m.type}
                    <StockBadge option={results.stockById[m.id]} />
                  </span>
                </button>
              ))}
            </div>
          )}
          {draft.stock?.outOfStock && (
            <p className="text-xs text-red-600 mt-1">Out of stock: the patient brings it.</p>
          )}
          {!picked && draft.medicineName.length >= 3 && !showList && (
            <p className="text-xs text-gray-500 mt-1">Not from stock: the patient brings it.</p>
          )}
        </div>
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remove medicine ${n}`}
          className="px-2 py-1.5 text-gray-400 hover:text-red-600"
        >
          ✕
        </button>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <select
          aria-label={`Type ${n}`}
          value={draft.type}
          onChange={(e) =>
            set({
              type: e.target.value,
              foodTiming: isFoodTimingApplicable(e.target.value, draft.route) ? draft.foodTiming : '',
            })
          }
          className="border p-1.5 rounded text-sm"
        >
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {t.replace('_', ' ')}
            </option>
          ))}
        </select>
        <select
          aria-label={`Route ${n}`}
          value={draft.route}
          onChange={(e) =>
            set({
              route: e.target.value,
              foodTiming: isFoodTimingApplicable(draft.type, e.target.value) ? draft.foodTiming : '',
            })
          }
          className="border p-1.5 rounded text-sm"
        >
          {ROUTES.map((r) => (
            <option key={r}>{r}</option>
          ))}
        </select>
        <input
          aria-label={`Dose ${n}`}
          value={draft.dose}
          onChange={(e) => set({ dose: e.target.value })}
          placeholder="Dose *"
          className="border p-1.5 rounded text-sm"
        />
        <input
          aria-label={`Days ${n}`}
          type="number"
          min="1"
          value={draft.durationDays}
          onChange={(e) => set({ durationDays: e.target.value })}
          placeholder="Days *"
          className="border p-1.5 rounded text-sm"
        />
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <FrequencyInput value={draft.frequency} onChange={(v) => set({ frequency: v })} />
        {picked && (
          <label className="text-xs text-gray-600 flex items-center gap-1">
            Units/dose
            <input
              type="number"
              min="1"
              value={draft.quantityPerDose}
              onChange={(e) => set({ quantityPerDose: e.target.value })}
              className="w-14 border p-1 rounded text-sm"
            />
          </label>
        )}
        {timing && (
          <select
            aria-label={`Food timing ${n}`}
            value={draft.foodTiming}
            onChange={(e) => set({ foodTiming: e.target.value })}
            className="border p-1.5 rounded text-sm"
          >
            <option value="">Food timing</option>
            {FOOD_TIMING_OPTIONS.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
        )}
      </div>
    </div>
  );
}
