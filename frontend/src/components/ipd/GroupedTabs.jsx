import { useRef } from 'react';

/**
 * The patient chart's many tabs (up to 13) as four groups, so the bar fits on one line and a
 * tab is found by what it is for: what is going on, what was ordered, what was charted, and the
 * paperwork. A group with more than one tab shows its tabs as a second, smaller row. Which tabs
 * exist is still the caller's call (module, Files & Access); a group with none is not shown.
 */
export const TAB_GROUPS = [
  { id: 'summary', label: 'Summary', tabs: ['overview', 'rounds', 'insurance'] },
  { id: 'orders', label: 'Orders', tabs: ['medication', 'investigations'] },
  {
    id: 'charts',
    label: 'Charts',
    tabs: ['vitals', 'sugar', 'io', 'ventilator', 'scores', 'notes'],
  },
  {
    id: 'forms',
    label: 'Forms',
    tabs: ['assessment', 'vulnerability', 'consent', 'documents'],
  },
];

/**
 * The groups that have tabs, each with its present tabs in the group's order (most used first);
 * a tab no group names goes to the end of Summary.
 */
export const groupTabs = (tabs) => {
  const known = new Set(TAB_GROUPS.flatMap((g) => g.tabs));
  const byId = new Map(tabs.map((t) => [t.id, t]));
  return TAB_GROUPS.map((g) => ({
    ...g,
    items: [
      ...g.tabs.map((id) => byId.get(id)).filter(Boolean),
      ...(g.id === 'summary' ? tabs.filter((t) => !known.has(t.id)) : []),
    ],
  })).filter((g) => g.items.length > 0);
};

export default function GroupedTabs({ tabs, value, onChange }) {
  const groups = groupTabs(tabs);
  const active = groups.find((g) => g.items.some((t) => t.id === value)) || groups[0];
  // The tab last open in each group, so going back to a group is still one click.
  const lastRef = useRef({});
  if (active) lastRef.current[active.id] = value;
  if (!active) return null;
  const openGroup = (g) => {
    const last = lastRef.current[g.id];
    onChange(g.items.some((t) => t.id === last) ? last : g.items[0].id);
  };

  return (
    <div className="mt-4">
      <div
        role="tablist"
        aria-label="Patient chart"
        className="border-b border-slate-200 flex gap-1 overflow-x-auto overflow-y-hidden"
      >
        {groups.map((g) => {
          const on = g.id === active.id;
          return (
            <button
              key={g.id}
              type="button"
              role="tab"
              aria-selected={on}
              // Opening a group shows the tab last used there, else its first tab.
              onClick={() => !on && openGroup(g)}
              className={`px-4 py-2 text-sm font-semibold border-b-2 -mb-px whitespace-nowrap focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded-t ${
                on
                  ? 'border-primary-600 text-primary-700'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              {g.label}
            </button>
          );
        })}
      </div>
      {active.items.length > 1 && (
        <div
          role="tablist"
          aria-label={active.label}
          className="mt-3 flex gap-1.5 overflow-x-auto pb-2"
        >
          {active.items.map((t) => {
            const on = t.id === value;
            return (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={on}
                onClick={() => onChange(t.id)}
                className={`shrink-0 h-8 px-3 rounded-full text-xs font-semibold border transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${
                  on
                    ? 'bg-primary-50 text-primary-700 border-primary-300'
                    : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                }`}
              >
                {t.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
