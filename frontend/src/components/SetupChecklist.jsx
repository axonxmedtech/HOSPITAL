import { useEffect, useState } from 'react';
import authService from '../services/authService';
import hospitalService from '../services/hospitalService';

/**
 * "Get your hospital ready": what a new hospital still has to set up, with a button to each
 * screen. A fresh hospital used to open on an empty dashboard, and its first receptionist met an
 * empty doctor list with no hint why. Hidden once every step is done, or when dismissed.
 */
const dismissKey = () =>
  `hms.setupChecklist.dismissed.${authService.getCurrentUser()?.hospitalId ?? 'default'}`;

const readDismissed = () => {
  try {
    return window.localStorage.getItem(dismissKey()) === '1';
  } catch {
    return false;
  }
};

export default function SetupChecklist({ onGo }) {
  const [status, setStatus] = useState(null);
  const [dismissed, setDismissed] = useState(readDismissed);

  useEffect(() => {
    if (dismissed) return undefined;
    let live = true;
    hospitalService
      .getSetupStatus()
      .then((s) => live && setStatus(s))
      // The checklist is a helper: if it can't load, the dashboard simply goes without it.
      .catch(() => live && setStatus(null));
    return () => {
      live = false;
    };
  }, [dismissed]);

  if (dismissed || !status || status.complete || !Array.isArray(status.steps)) return null;

  const dismiss = () => {
    try {
      window.localStorage.setItem(dismissKey(), '1');
    } catch {
      /* private window: hide for this visit only */
    }
    setDismissed(true);
  };

  return (
    <section
      aria-labelledby="setup-checklist-title"
      className="bg-white border border-sky-200 rounded-xl p-5 shadow-sm"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id="setup-checklist-title" className="text-lg font-semibold text-gray-900">
            Get your hospital ready
          </h2>
          <p className="text-sm text-gray-600 mt-0.5">
            {status.done} of {status.total} done. Finish these so your staff can start working.
          </p>
        </div>
        <button
          type="button"
          onClick={dismiss}
          className="text-xs text-gray-500 hover:text-gray-800 whitespace-nowrap"
        >
          Hide
        </button>
      </div>
      <ol className="mt-4 divide-y divide-gray-100">
        {status.steps.map((step) => (
          <li key={step.key} className="flex items-center justify-between gap-4 py-2.5">
            <div className="flex items-start gap-3 min-w-0">
              <span
                aria-hidden="true"
                className={`mt-0.5 h-5 w-5 shrink-0 rounded-full flex items-center justify-center text-xs font-bold ${
                  step.done ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-100 text-gray-400'
                }`}
              >
                {step.done ? '✓' : ''}
              </span>
              <div className="min-w-0">
                <p
                  className={`text-sm font-medium ${step.done ? 'text-gray-500 line-through' : 'text-gray-900'}`}
                >
                  {step.title}
                  <span className="sr-only">{step.done ? ' (done)' : ' (to do)'}</span>
                </p>
                {!step.done && <p className="text-xs text-gray-500">{step.hint}</p>}
              </div>
            </div>
            {!step.done && onGo && (
              <button
                type="button"
                onClick={() => onGo(step.tab)}
                className="px-3 py-1 rounded-md text-xs font-semibold bg-sky-50 text-sky-700 hover:bg-sky-100 whitespace-nowrap"
              >
                Set up
              </button>
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}
