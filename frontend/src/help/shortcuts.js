/** Mac users see ⌘, everyone else Ctrl. */
export const isMac = () =>
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent || '');

export const modKey = () => (isMac() ? '⌘' : 'Ctrl');

/** "G then a letter" jumps; each goes to the first of its screens this dashboard has. */
export const GO_TO = {
  o: { label: 'OPD', tabs: ['opd'] },
  i: { label: 'IPD', tabs: ['ipd'] },
  p: { label: 'Patients', tabs: ['patients', 'my-patients'] },
  a: { label: 'Appointments', tabs: ['appointments'] },
  b: { label: 'Billing', tabs: ['billing', 'pharmacy-billing'] },
  d: { label: 'Dashboard / Overview', tabs: ['overview', 'dashboard'] },
  t: { label: 'Operation Theatre', tabs: ['ot', 'board'] },
  s: { label: 'Settings', tabs: ['settings'] },
};

/** The shortcut sheet, grouped as shown in Help → Shortcuts. */
export function shortcutGroups() {
  const mod = modKey();
  return [
    {
      title: 'Anywhere',
      items: [
        { keys: [mod, 'K'], text: 'Command bar: go to any screen or search help' },
        { keys: ['?'], text: 'Show these shortcuts' },
        { keys: ['F1'], text: 'Open help' },
        { keys: ['Esc'], text: 'Close help, the command bar or a dialog' },
      ],
    },
    {
      title: 'Jump to a screen (press G, then the letter)',
      items: Object.entries(GO_TO).map(([k, v]) => ({
        keys: ['G', k.toUpperCase()],
        text: v.label,
      })),
    },
    {
      title: 'In the command bar',
      items: [
        { keys: ['↑', '↓'], text: 'Move through results' },
        { keys: ['Enter'], text: 'Open the selected result' },
      ],
    },
  ];
}

/** True while the user is typing, when single-key shortcuts must not fire. */
export function isTyping(event) {
  const el = event.target;
  if (!el || !el.tagName) return false;
  const tag = el.tagName.toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable === true;
}
