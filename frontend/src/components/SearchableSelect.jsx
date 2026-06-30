import React, { useState, useEffect, useRef } from 'react';

/**
 * SearchableSelect — async search autocomplete dropdown.
 * Props:
 *   onSearch: async (query: string) => Item[]
 *   onSelect: (item: Item) => void
 *   getLabel: (item: Item) => string
 *   placeholder: string
 *   value: string  — current display value (controlled)
 *   disabled: boolean
 *   hint: string   — shown below input
 */
export default function SearchableSelect({
  onSearch, onSelect, getLabel,
  placeholder = 'Search...', value = '', disabled = false, hint = ''
}) {
  const [query, setQuery] = useState(value);
  const [results, setResults] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef(null);
  const containerRef = useRef(null);

  useEffect(() => { setQuery(value); }, [value]);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const runSearch = async (q) => {
    setLoading(true);
    try {
      const items = await onSearch(q);
      setResults(items || []);
    } catch {
      setResults([]);
    }
    setLoading(false);
  };

  const handleChange = (e) => {
    const q = e.target.value;
    setQuery(q);
    setOpen(true);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => runSearch(q), 300);
  };

  const handleFocus = () => {
    if (results.length === 0) runSearch(query);
    setOpen(true);
  };

  const handleSelect = (item) => {
    setQuery(getLabel(item));
    setOpen(false);
    setResults([]);
    onSelect(item);
  };

  return (
    <div ref={containerRef} className="relative">
      <input
        type="text"
        value={query}
        onChange={handleChange}
        onFocus={handleFocus}
        placeholder={placeholder}
        disabled={disabled}
        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
      />
      {hint && <p className="text-xs text-gray-400 mt-0.5">{hint}</p>}
      {open && (
        <div className="absolute z-50 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg max-h-56 overflow-auto">
          {loading && <div className="px-4 py-3 text-sm text-gray-400">Searching...</div>}
          {!loading && results.length === 0 && (
            <div className="px-4 py-3 text-sm text-gray-400">No results found</div>
          )}
          {!loading && results.map((item, i) => (
            <button
              key={i}
              type="button"
              onMouseDown={() => handleSelect(item)}
              className="w-full text-left px-4 py-2 text-sm hover:bg-blue-50 hover:text-blue-700 transition-colors"
            >
              {getLabel(item)}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
