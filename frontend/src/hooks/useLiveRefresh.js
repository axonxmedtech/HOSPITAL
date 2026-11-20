import { useEffect, useRef } from 'react';

/**
 * Live refresh for components that load their own data.
 *
 * Dashboards reload their lists when the server says data changed (a REFRESH_DATA message on the
 * hospital's WebSocket). Panels and views that fetch for themselves — the medication chart on an
 * IPD case, the bed board, the day's collection, the OT day list — never heard that message, so
 * they kept showing what they loaded first until the user refreshed the page.
 *
 * useWebSocket now announces every change as a window event; this hook reloads the component on
 * it. Bursts of changes are coalesced into one reload, and a tab in the background reloads once
 * when it is looked at again instead of on every change.
 *
 * Pass a SILENT reload: no spinner and no reset of what the user has selected or typed.
 */
export const DATA_CHANGED = 'hms:data-changed';

/** Tells every open view that the hospital's data changed. */
export function announceDataChanged() {
  globalThis.dispatchEvent(new CustomEvent(DATA_CHANGED));
}

export default function useLiveRefresh(reload, enabled = true) {
  const reloadRef = useRef(reload);
  useEffect(() => {
    reloadRef.current = reload;
  });

  useEffect(() => {
    if (!enabled) return undefined;
    let timer = null;
    let missed = false;
    const run = () => {
      timer = null;
      if (document.hidden) {
        missed = true;
        return;
      }
      reloadRef.current?.();
    };
    const onChanged = () => {
      clearTimeout(timer);
      timer = setTimeout(run, 250);
    };
    const onVisible = () => {
      if (!document.hidden && missed) {
        missed = false;
        reloadRef.current?.();
      }
    };
    globalThis.addEventListener(DATA_CHANGED, onChanged);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearTimeout(timer);
      globalThis.removeEventListener(DATA_CHANGED, onChanged);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [enabled]);
}
