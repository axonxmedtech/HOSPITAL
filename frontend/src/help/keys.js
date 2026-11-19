import { helpStore } from './helpStore';
import { GO_TO, isTyping } from './shortcuts';

/** Handles one keydown for the help shortcuts, returns true if it was a help shortcut. */
export function handleHelpKey(e, pending) {
  const state = helpStore.get();
  const key = e.key;

  if ((e.ctrlKey || e.metaKey) && !e.altKey && (key === 'k' || key === 'K')) {
    e.preventDefault();
    if (state.paletteOpen) helpStore.closePalette();
    else helpStore.openPalette();
    return true;
  }
  if (key === 'F1') {
    e.preventDefault();
    helpStore.openPanel('assistant');
    return true;
  }
  if (key === 'Escape') {
    if (state.paletteOpen) {
      helpStore.closePalette();
      return true;
    }
    if (state.panelOpen && !document.querySelector('[role="dialog"][aria-modal="true"]')) {
      helpStore.closePanel();
      return true;
    }
    return false;
  }
  // Single-key shortcuts never fire while typing or with a modifier held.
  if (isTyping(e) || e.ctrlKey || e.metaKey || e.altKey) return false;

  if (key === '?') {
    helpStore.openPanel('shortcuts');
    return true;
  }
  if (pending.current && Date.now() - pending.current < 1200) {
    pending.current = 0;
    const target = GO_TO[key.toLowerCase()];
    if (!target) return false;
    for (const tab of target.tabs) if (helpStore.goTo(tab)) return true;
    return false;
  }
  if (key === 'g' || key === 'G') {
    pending.current = Date.now();
    return true;
  }
  return false;
}
