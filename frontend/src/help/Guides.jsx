import { useEffect, useState } from 'react';
import SetupChecklist from '../components/SetupChecklist';
import { helpRolesFor } from './content';
import { ADMIN } from './content/roles';
import { guidesFor, KINDS } from './guideData';
import { flattenTabs, helpStore } from './helpStore';
import { progressStore, useHelpProgress } from './progressStore';
import Shot from './Shot';
import { toursFor } from './tourData';
import { tourStore } from './tourStore';

/**
 * Help → Guides: setup guides, first-day guides and how-to tutorials for this user. Each step
 * can be ticked off (saved for the user, on any computer) and has a button to its screen when the user's
 * dashboard has it. Admins of a hospital or clinic also see the live setup checklist, which
 * ticks itself from what is really set up.
 */

function Progress({ done, total }) {
  return (
    <div className="flex items-center gap-2">
      <progress
        value={done}
        max={total}
        className="h-1.5 flex-1 overflow-hidden rounded-full [&::-webkit-progress-bar]:bg-gray-100 [&::-webkit-progress-value]:bg-sky-600 [&::-moz-progress-bar]:bg-sky-600"
      />
      <span className="text-[11px] text-gray-500 shrink-0">
        {done} of {total}
      </span>
    </div>
  );
}

function GuideView({ guide, progress, onToggle, onBack, onGo }) {
  const done = progress[guide.id] || [];
  const screens = new Map(flattenTabs(helpStore.get().nav?.tabs).map((t) => [t.id, t.label]));
  return (
    <div className="p-4 space-y-4 text-sm">
      <button type="button" onClick={onBack} className="text-xs font-semibold text-sky-700">
        ← All guides
      </button>
      <div className="space-y-1.5">
        <h3 className="text-base font-bold text-gray-900">{guide.title}</h3>
        <p className="text-gray-600">{guide.summary}</p>
        <Progress
          done={guide.steps.filter((s) => done.includes(s.id)).length}
          total={guide.steps.length}
        />
      </div>
      <ol className="space-y-2">
        {guide.steps.map((step, i) => {
          const isDone = done.includes(step.id);
          const label = step.goTo && screens.get(step.goTo);
          return (
            <li
              key={step.id}
              className={`rounded-xl border p-3 space-y-1.5 ${isDone ? 'bg-gray-50 border-gray-200' : 'bg-white border-gray-200'}`}
            >
              <div className="flex items-start gap-2.5">
                <input
                  type="checkbox"
                  checked={isDone}
                  onChange={() => onToggle(guide.id, step.id)}
                  aria-label={`Mark "${step.title}" done`}
                  className="mt-0.5 h-4 w-4 rounded border-gray-300 text-sky-600"
                />
                <div className="min-w-0 flex-1 space-y-1">
                  <p
                    className={`font-semibold ${isDone ? 'text-gray-500 line-through' : 'text-gray-900'}`}
                  >
                    <span>
                      {i + 1}. {step.title}
                    </span>
                    {step.who && (
                      <span className="ml-1.5 text-[11px] font-medium text-gray-500">
                        · {step.who}
                      </span>
                    )}
                  </p>
                  {!isDone && <p className="text-gray-700">{step.text}</p>}
                  {!isDone && <Shot shotKey={`${guide.id}/${step.id}`} />}
                  {!isDone && label && (
                    <button
                      type="button"
                      onClick={() => onGo(step.goTo)}
                      className="px-2.5 py-1 rounded-md text-xs font-semibold bg-sky-50 text-sky-700 hover:bg-sky-100"
                    >
                      Go to {label} →
                    </button>
                  )}
                </div>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

export default function Guides({ user, onGo }) {
  const roles = helpRolesFor(user);
  const guides = guidesFor(user, roles);
  const { guides: progress, tours: toursDone } = useHelpProgress();
  const screens = new Set(helpStore.destinations().map((d) => d.id));
  const tours = toursFor(user, roles, screens.size ? screens : null);
  const [openId, setOpenId] = useState(null);
  const open = guides.find((g) => g.id === openId);

  useEffect(() => {
    void progressStore.load(user);
  }, [user?.userId, user?.hospitalId]); // eslint-disable-line react-hooks/exhaustive-deps

  const toggle = (guideId, stepId) => progressStore.toggleStep(guideId, stepId);

  if (open) {
    return (
      <div className="overflow-y-auto h-full">
        <GuideView
          guide={open}
          progress={progress}
          onToggle={toggle}
          onBack={() => setOpenId(null)}
          onGo={onGo}
        />
      </div>
    );
  }

  const showChecklist = user?.role === ADMIN && user?.hospitalType !== 'PHARMACY';
  return (
    <div className="p-4 space-y-5 text-sm overflow-y-auto h-full">
      {showChecklist && <SetupChecklist alwaysShown onGo={onGo} />}
      {tours.length > 0 && (
        <section>
          <h3 className="text-xs font-semibold uppercase text-gray-500 mb-2">Tours</h3>
          <ul className="space-y-2">
            {tours.map((t) => (
              <li
                key={t.id}
                className="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-3"
              >
                <span className="min-w-0">
                  <span className="block font-semibold text-gray-900">{t.title}</span>
                  <span className="block text-xs text-gray-600">
                    {t.summary}
                    {toursDone[t.id] === 'done' && (
                      <span className="text-emerald-700"> · Done</span>
                    )}
                  </span>
                </span>
                <button
                  type="button"
                  onClick={() => tourStore.start(t)}
                  className="shrink-0 px-3 py-1.5 rounded-lg text-xs font-semibold bg-gray-900 text-white hover:bg-gray-800"
                >
                  {toursDone[t.id] === 'done' ? 'Again' : 'Start'}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
      {guides.length === 0 && <p className="text-gray-500">No guides for your role yet.</p>}
      {KINDS.map(([kind, heading]) => {
        const list = guides.filter((g) => g.kind === kind);
        if (!list.length) return null;
        return (
          <section key={kind}>
            <h3 className="text-xs font-semibold uppercase text-gray-500 mb-2">{heading}</h3>
            <ul className="space-y-2">
              {list.map((g) => {
                const done = g.steps.filter((s) => (progress[g.id] || []).includes(s.id)).length;
                return (
                  <li key={g.id}>
                    <button
                      type="button"
                      onClick={() => setOpenId(g.id)}
                      className="w-full text-left rounded-xl border border-gray-200 bg-white p-3 hover:border-sky-300 space-y-1.5"
                    >
                      <span className="block font-semibold text-gray-900">{g.title}</span>
                      <span className="block text-xs text-gray-600">{g.summary}</span>
                      <Progress done={done} total={g.steps.length} />
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
