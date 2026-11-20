import { useEffect, useMemo, useRef, useState } from 'react';
import authService from '../services/authService';
import { entriesFor } from './content';
import { helpStore, useHelpState } from './helpStore';
import { rank, tokens } from './search';

/**
 * The command bar (Ctrl/⌘+K): type a few letters to jump to any screen this user can open, or to
 * find a help answer. Arrow keys move, Enter opens, Esc closes.
 */

function matchScreens(destinations, query) {
  const q = query.trim().toLowerCase();
  if (!q) return destinations.slice(0, 8);
  const words = tokens(q);
  return destinations
    .map((d) => {
      const label = `${d.label} ${d.group || ''}`.toLowerCase();
      let score = 0;
      if (label.startsWith(q)) score += 5;
      else if (label.includes(q)) score += 3;
      score += words.filter((w) => label.includes(w)).length;
      return { d, score };
    })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 6)
    .map((x) => x.d);
}

export default function CommandPalette() {
  const { paletteOpen, nav } = useHelpState();
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const inputRef = useRef(null);
  const user = authService.getCurrentUser();
  const role = user?.role;
  const entries = useMemo(() => (role ? entriesFor(authService.getCurrentUser()) : []), [role]);

  useEffect(() => {
    if (paletteOpen) {
      setQuery('');
      setActive(0);
      setTimeout(() => inputRef.current?.focus(), 0);
    }
  }, [paletteOpen]);

  const results = useMemo(() => {
    const screens = matchScreens(helpStore.destinations(), query).map((d) => ({
      kind: 'screen',
      key: `s:${d.id}`,
      label: d.label,
      hint: d.group || 'Go to screen',
      run: () => helpStore.goTo(d.id),
    }));
    const answers = query.trim()
      ? rank(entries, query, { screen: nav?.activeTab })
          .slice(0, 5)
          .map(({ entry }) => ({
            kind: 'help',
            key: `h:${entry.id}`,
            label: entry.q,
            hint: 'Help',
            run: () => helpStore.openPanel('assistant', { answerId: entry.id }),
          }))
      : [];
    const askAll = query.trim()
      ? [
          {
            kind: 'ask',
            key: 'ask',
            label: `Ask help: “${query.trim()}”`,
            hint: 'Assistant',
            run: () => helpStore.openPanel('assistant', { question: query.trim() }),
          },
        ]
      : [];
    return [...screens, ...answers, ...askAll];
  }, [query, entries, nav]);

  if (!paletteOpen || !user) return null;

  const choose = (r) => {
    helpStore.closePalette();
    r?.run();
  };

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      choose(results[active]);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      helpStore.closePalette();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[12vh] px-4">
      <button
        type="button"
        aria-label="Close command bar"
        className="absolute inset-0 bg-gray-900/30 cursor-default"
        onClick={helpStore.closePalette}
      />
      <dialog
        open
        aria-modal="true"
        aria-label="Command bar"
        className="relative m-0 p-0 w-full max-w-lg bg-white rounded-xl shadow-2xl border border-gray-200 overflow-hidden"
      >
        <input
          ref={inputRef}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setActive(0);
          }}
          onKeyDown={onKeyDown}
          placeholder="Go to a screen or search help…"
          aria-label="Go to a screen or search help"
          className="w-full px-4 py-3 text-sm border-b border-gray-200 focus:outline-none"
        />
        <ul className="max-h-80 overflow-y-auto py-1" aria-label="Results">
          {results.length === 0 && (
            <li className="px-4 py-3 text-sm text-gray-500">Nothing found.</li>
          )}
          {results.map((r, i) => (
            <li key={r.key}>
              <button
                type="button"
                onMouseEnter={() => setActive(i)}
                onClick={() => choose(r)}
                aria-current={i === active ? 'true' : undefined}
                className={`w-full flex items-center justify-between gap-3 px-4 py-2 text-left text-sm ${
                  i === active ? 'bg-sky-50 text-sky-900' : 'text-gray-800'
                }`}
              >
                <span className="truncate">{r.label}</span>
                <span className="shrink-0 text-[11px] text-gray-400">{r.hint}</span>
              </button>
            </li>
          ))}
        </ul>
      </dialog>
    </div>
  );
}
