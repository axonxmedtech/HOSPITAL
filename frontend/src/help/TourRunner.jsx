import { useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react';
import { helpStore } from './helpStore';
import { findTarget, tourStore } from './tourStore';

/**
 * Runs an on-screen tour: dims the page, spotlights the step's target and shows a card with
 * Back / Next / Skip. Steps can open a screen first. Keyboard: → next, ← back, Esc ends.
 * Built in-house (no tour library) so it follows React 19 and the app's own navigation.
 */

const PAD = 6;
const CARD_W = 320;

function cardPosition(rect) {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  if (vw < 640) return { left: 12, right: 12, bottom: 12, top: 'auto', width: 'auto' }; // phones: a sheet
  if (!rect) return { left: Math.max(12, (vw - CARD_W) / 2), top: Math.max(12, vh * 0.28) };
  const clampX = (x) => Math.min(Math.max(12, x), vw - CARD_W - 12);
  if (rect.right + PAD + 12 + CARD_W < vw && rect.height > vh * 0.5) {
    return { left: rect.right + PAD + 12, top: Math.min(Math.max(12, rect.top), vh - 260) };
  }
  if (rect.bottom + PAD + 220 < vh) return { left: clampX(rect.left), top: rect.bottom + PAD + 10 };
  return { left: clampX(rect.left), top: Math.max(12, rect.top - PAD - 230) };
}

export default function TourRunner() {
  const { tour, index } = useSyncExternalStore(tourStore.subscribe, tourStore.get, tourStore.get);
  const step = tour?.steps[index];
  // What is shown belongs to one step: until that step's screen is open and its target found,
  // nothing but the dimmed page is drawn, so a card never appears over the previous screen.
  const [view, setView] = useState({ step: null, rect: null });
  const nextRef = useRef(null);
  const elRef = useRef(null);

  // Open the step's screen, then look for its target for a short while (screens load data).
  useEffect(() => {
    if (!step) return undefined;
    elRef.current = null;
    if (step.goTo) helpStore.goTo(step.goTo);
    let alive = true;
    const started = Date.now();
    const onScreen = () => !step.goTo || helpStore.get().nav?.activeTab === step.goTo;
    const look = () => {
      if (!alive) return;
      const waited = Date.now() - started;
      // Give a newly opened screen a moment, so the old screen's heading is never picked.
      const settled = onScreen() && (!step.goTo || waited > 250);
      const el = settled && step.target ? findTarget(step.target) : null;
      if (el) {
        const r = el.getBoundingClientRect();
        if (r.top < 0 || r.bottom > window.innerHeight) el.scrollIntoView?.({ block: 'center' });
        elRef.current = el;
        setView({ step, rect: el.getBoundingClientRect() });
      } else if (settled && !step.target) {
        setView({ step, rect: null });
      } else if (waited < 2500) {
        setTimeout(look, 100);
      } else {
        setView({ step, rect: null }); // not found: show the card centred
      }
    };
    look();
    return () => {
      alive = false;
    };
  }, [step]);

  // Keep the spotlight on its element while the page scrolls or resizes.
  useEffect(() => {
    if (!step) return undefined;
    let frame;
    const follow = () => {
      const el = elRef.current;
      if (el) {
        const r = el.getBoundingClientRect();
        setView((v) => {
          const o = v.rect;
          if (v.step !== step) return v;
          if (
            o &&
            o.top === r.top &&
            o.left === r.left &&
            o.width === r.width &&
            o.height === r.height
          ) {
            return v;
          }
          return { step, rect: r };
        });
      }
      frame = requestAnimationFrame(follow);
    };
    frame = requestAnimationFrame(follow);
    return () => cancelAnimationFrame(frame);
  }, [step]);

  useEffect(() => {
    if (!tour) return undefined;
    const onKey = (e) => {
      const map = {
        Escape: () => tourStore.end('skipped'),
        ArrowRight: tourStore.next,
        ArrowLeft: tourStore.back,
      };
      if (!map[e.key]) return;
      e.preventDefault();
      e.stopPropagation();
      map[e.key]();
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [tour]);

  const ready = !!step && view.step === step;
  const rect = ready ? view.rect : null;

  useLayoutEffect(() => {
    if (ready) nextRef.current?.focus();
  }, [ready, step]);

  if (!tour || !step || !ready) {
    return tour ? <div className="fixed inset-0 z-[70]" aria-hidden="true" /> : null;
  }

  const last = index === tour.steps.length - 1;
  const pos = cardPosition(rect);
  return (
    <div className="fixed inset-0 z-[70]">
      {/* Clicks on the dimmed page do nothing during a tour. */}
      <div className="absolute inset-0" aria-hidden="true" />
      {rect ? (
        <div
          aria-hidden="true"
          className="absolute rounded-lg ring-2 ring-sky-400 pointer-events-none transition-all duration-200"
          style={{
            top: rect.top - PAD,
            left: rect.left - PAD,
            width: rect.width + PAD * 2,
            height: rect.height + PAD * 2,
            boxShadow: '0 0 0 9999px rgba(17, 24, 39, 0.55)',
          }}
        />
      ) : (
        <div aria-hidden="true" className="absolute inset-0 bg-gray-900/55" />
      )}
      <dialog
        open
        data-tour-card
        aria-modal="true"
        aria-labelledby="tour-step-title"
        aria-describedby="tour-step-text"
        className="absolute bg-white rounded-xl shadow-2xl border border-gray-200 p-4 space-y-3 sm:w-80"
        style={{ margin: 0, right: 'auto', bottom: 'auto', ...pos }}
      >
        <div className="flex items-center justify-between gap-2">
          <span className="text-[11px] font-semibold uppercase text-sky-700">
            {tour.title} · {index + 1} of {tour.steps.length}
          </span>
          <button
            type="button"
            onClick={() => tourStore.end('skipped')}
            className="text-xs text-gray-500 hover:text-gray-800"
          >
            Skip tour
          </button>
        </div>
        <h2 id="tour-step-title" className="text-base font-bold text-gray-900">
          {step.title}
        </h2>
        <p id="tour-step-text" className="text-sm text-gray-700">
          {step.text}
        </p>
        <div className="flex justify-end gap-2 pt-1">
          {index > 0 && (
            <button
              type="button"
              onClick={tourStore.back}
              className="px-3 py-1.5 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-100"
            >
              Back
            </button>
          )}
          <button
            ref={nextRef}
            type="button"
            onClick={tourStore.next}
            className="px-3 py-1.5 rounded-lg text-sm font-semibold bg-gray-900 text-white hover:bg-gray-800"
          >
            {last ? 'Finish' : 'Next'}
          </button>
        </div>
      </dialog>
    </div>
  );
}
