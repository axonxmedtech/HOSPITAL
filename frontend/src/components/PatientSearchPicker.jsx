import { useEffect, useId, useRef, useState } from 'react';
import hospitalService from '../services/hospitalService';

/**
 * The patient field of an OPD form. Searches the whole hospital on the server as the user types
 * (name or phone), instead of filtering whatever page of patients happened to be loaded, so a
 * patient registered a minute ago — or the 101st patient — is always found. When nobody matches,
 * it offers "Register new patient", which opens the registration form with the typed name.
 *
 * Controlled: the parent keeps the text (it resets it when the form opens) and is told the
 * chosen patient, or null as soon as the text is edited again.
 */
export default function PatientSearchPicker({
  text,
  onTextChange,
  onSelect,
  onRegisterNew,
  placeholder = 'Search patient by name or phone…',
  inputClassName = '',
}) {
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const latest = useRef(0);
  const listId = useId();
  const query = (text || '').trim();

  useEffect(() => {
    if (!open || query.length < 2) {
      setResults([]);
      return undefined;
    }
    const id = ++latest.current;
    setLoading(true);
    const t = setTimeout(async () => {
      try {
        const data = await hospitalService.getPatients(query, 0, 10);
        if (id !== latest.current) return;
        setResults(Array.isArray(data) ? data : data?.content || []);
      } catch {
        if (id === latest.current) setResults([]);
      } finally {
        if (id === latest.current) setLoading(false);
      }
    }, 250);
    return () => clearTimeout(t);
  }, [query, open]);

  const choose = (p) => {
    onSelect(p);
    onTextChange(p.phone ? `${p.name} (${p.phone})` : p.name);
    setOpen(false);
  };

  return (
    <div className="relative">
      <div className="relative">
        <span className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-neutral-400">
          <svg
            className="w-5 h-5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
        </span>
        <input
          type="text"
          role="combobox"
          aria-controls={listId}
          aria-expanded={open && query.length >= 2}
          aria-label="Patient"
          className={
            inputClassName ||
            'w-full border border-gray-300 rounded-xl pl-10 pr-4 py-2 focus:ring-2 focus:ring-primary-500 text-sm focus:border-transparent text-slate-800'
          }
          value={text}
          onChange={(e) => {
            onTextChange(e.target.value);
            onSelect(null);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 200)}
          placeholder={placeholder}
          autoComplete="off"
        />
      </div>

      {open && query.length >= 2 && (
        <div
          id={listId}
          role="listbox"
          className="absolute left-0 right-0 mt-1 bg-white border border-neutral-200 rounded-xl shadow-xl z-50 max-h-64 overflow-y-auto divide-y divide-neutral-100"
        >
          {loading && results.length === 0 && (
            <div className="px-4 py-3 text-sm text-neutral-500 text-center">Searching…</div>
          )}
          {!loading && results.length === 0 && (
            <div className="px-4 py-3 text-sm text-neutral-500 text-center">
              No matching patients found
            </div>
          )}
          {results.map((p) => (
            <button
              type="button"
              role="option"
              aria-selected="false"
              key={p.id}
              onMouseDown={() => choose(p)}
              className="w-full px-4 py-3 hover:bg-neutral-50 cursor-pointer transition-colors duration-150 flex flex-col gap-0.5 text-left border-0"
            >
              <span className="font-semibold text-neutral-800 text-sm">{p.name}</span>
              <span className="text-xs text-neutral-500">
                {[p.phone && `📞 ${p.phone}`, p.age != null && `${p.age} Yrs`, p.gender]
                  .filter(Boolean)
                  .join(' | ')}
              </span>
            </button>
          ))}
          {onRegisterNew && (
            <button
              type="button"
              onMouseDown={() => {
                setOpen(false);
                onRegisterNew(query);
              }}
              className="w-full px-4 py-3 text-left text-sm font-semibold text-sky-700 hover:bg-sky-50 cursor-pointer border-0"
            >
              + Register new patient{query ? ` “${query}”` : ''}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
