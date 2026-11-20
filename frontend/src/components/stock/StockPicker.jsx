import { useEffect, useId, useState } from 'react';
import nurseService from '../../services/nurseService';

/**
 * Search both inventories — medicines and hospital items (saline, IV fluids, consumables) — and
 * pick one. Each result shows what is in stock, and flags it when it is low or out, so whoever
 * orders or gives it knows to get it reordered or to ask the patient to bring it.
 */
export function StockBadge({ option }) {
  if (!option) return null;
  if (option.outOfStock) {
    return (
      <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-red-100 text-red-700">
        OUT OF STOCK
      </span>
    );
  }
  if (option.low) {
    return (
      <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800">
        LOW · {option.available} left
      </span>
    );
  }
  return (
    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700">
      {option.available} in stock
    </span>
  );
}

export default function StockPicker({
  label = 'Search medicine or item',
  onSelect,
  selected,
  placeholder,
}) {
  const id = useId();
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let live = true;
    const t = setTimeout(() => {
      nurseService
        .searchStock(query.trim())
        .then((list) => live && setOptions(Array.isArray(list) ? list.slice(0, 30) : []))
        .catch(() => live && setOptions([]));
    }, 250);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [query]);

  return (
    <div className="relative">
      <label htmlFor={id} className="block text-xs font-medium text-gray-600 mb-1">
        {label}
      </label>
      <input
        id={id}
        type="search"
        value={selected && !open ? selected.name : query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={placeholder || 'Type to search the medicine and hospital inventories'}
        autoComplete="off"
        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
      />
      {selected && !open && (
        <p className="mt-1 flex items-center gap-2 text-xs text-gray-600">
          {selected.inventory}
          <StockBadge option={selected} />
          {selected.unitPrice != null && (
            <span>₹{Number(selected.unitPrice).toLocaleString('en-IN')} each</span>
          )}
        </p>
      )}
      {open && (
        <ul
          role="listbox"
          aria-label={label}
          className="absolute z-30 mt-1 w-full max-h-64 overflow-y-auto bg-white border border-gray-200 rounded-lg shadow-lg"
        >
          {options.length === 0 ? (
            <li className="px-3 py-2 text-xs text-gray-500">
              Nothing in stock matches. You can still type the name; the patient brings it.
            </li>
          ) : (
            options.map((o) => (
              <li key={`${o.source}-${o.medicineId ?? o.itemName}`}>
                <button
                  type="button"
                  role="option"
                  aria-selected={false}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    onSelect(o);
                    setQuery('');
                    setOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 hover:bg-gray-50 flex items-center justify-between gap-2"
                >
                  <span>
                    <span className="text-sm text-gray-900">{o.name}</span>
                    <span className="block text-[11px] text-gray-500">
                      {o.inventory}
                      {o.type ? ` · ${o.type}` : ''}
                    </span>
                  </span>
                  <StockBadge option={o} />
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
