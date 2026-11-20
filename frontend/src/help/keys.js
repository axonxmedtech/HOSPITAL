import { helpStore } from './helpStore';
import { GO_TO, isTyping } from './shortcuts';

const isCommandBarKey = (e) =>
  (e.ctrlKey || e.metaKey) && !e.altKey && (e.key === 'k' || e.key === 'K');

/** A page dialog (a form, a tour step, a screenshot) handles its own Esc. */
const pageDialogOpen = () =>
  !!document.querySelector('dialog[open][aria-modal="true"], [role="dialog"][aria-modal="true"]');

function toggleCommandBar(e) {
  e.preventDefault();
  if (helpStore.get().paletteOpen) helpStore.closePalette();
  else helpStore.openPalette();
  return true;
}

function escape() {
  const state = helpStore.get();
  if (state.paletteOpen) {
    helpStore.closePalette();
    return true;
  }
  if (state.panelOpen && !pageDialogOpen()) {
    helpStore.closePanel();
    return true;
  }
  return false;
}

/** The second key of "G then a letter": opens the first of that letter's screens this user has. */
function jump(key) {
  const target = GO_TO[key.toLowerCase()];
  return !!target && target.tabs.some((tab) => helpStore.goTo(tab));
}

/** Single-key shortcuts; never while typing or with a modifier held. */
function singleKey(e, pending) {
  if (isTyping(e) || e.ctrlKey || e.metaKey || e.altKey) return false;
  if (e.key === '?') {
    helpStore.openPanel('shortcuts');
    return true;
  }
  if (pending.current && Date.now() - pending.current < 1200) {
    pending.current = 0;
    return jump(e.key);
  }
  if (e.key === 'g' || e.key === 'G') {
    pending.current = Date.now();
    return true;
  }
  return false;
}

/** Handles one keydown for the help shortcuts, returns true if it was a help shortcut. */
export function handleHelpKey(e, pending) {
  if (isCommandBarKey(e)) return toggleCommandBar(e);
  if (e.key === 'F1') {
    e.preventDefault();
    helpStore.openPanel('assistant');
    return true;
  }
  if (e.key === 'Escape') return escape();
  return singleKey(e, pending);
}
