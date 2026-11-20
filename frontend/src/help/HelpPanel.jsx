import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import authService from '../services/authService';
import { entriesFor, suggestionsFor } from './content';
import { ADMIN, ROLE_NAMES } from './content/roles';
import { sendFeedback } from './feedback';
import Guides from './Guides';
import { flattenTabs, helpStore, useHelpState } from './helpStore';
import { CONFIDENT_SCORE, rank } from './search';
import { shortcutGroups } from './shortcuts';
import Shot from './Shot';

/**
 * Help & Support: a side panel available to every role. It never covers the page, so a user can
 * follow the steps while it stays open.
 *
 *  - Assistant: ask in your own words; it answers from the help written for your role and setup.
 *  - Topics: every answer for your role, by subject.
 *  - Shortcuts: the keyboard shortcuts.
 *  - Guides: setup guides (admin), a first-day guide for each role, and how-to tutorials.
 * Admins can raise a support ticket; other roles are pointed to their admin.
 */

const SUPPORT_PHONE = '9022915865';

/** "Was this helpful?" under an answer. One vote per answer shown. */
function Rate({ entry, screen, isAdmin }) {
  const [vote, setVote] = useState(null);
  const cast = (helpful) => {
    setVote(helpful ? 'yes' : 'no');
    sendFeedback({ kind: helpful ? 'HELPFUL' : 'NOT_HELPFUL', entryId: entry.id, screen });
  };
  if (vote === 'yes') return <p className="text-xs text-emerald-700">Thanks — glad it helped.</p>;
  if (vote === 'no') {
    return (
      <p className="text-xs text-gray-600">
        Thanks for telling us — we will improve this answer. Try the related answers below
        {isAdmin ? ', or raise a support ticket.' : ', or ask your hospital admin.'}
      </p>
    );
  }
  return (
    <div className="flex items-center gap-2 text-xs text-gray-500">
      <span>Was this helpful?</span>
      <button
        type="button"
        onClick={() => cast(true)}
        className="px-2 py-0.5 rounded border border-gray-200 hover:border-emerald-300 hover:text-emerald-700"
      >
        Yes
      </button>
      <button
        type="button"
        onClick={() => cast(false)}
        className="px-2 py-0.5 rounded border border-gray-200 hover:border-rose-300 hover:text-rose-700"
      >
        No
      </button>
    </div>
  );
}

function Answer({ entry, onGo, screen, isAdmin }) {
  const dest =
    entry.goTo && flattenTabs(helpStore.get().nav?.tabs).find((t) => t.id === entry.goTo);
  return (
    <div className="space-y-2">
      <p className="font-semibold text-gray-900">{entry.q}</p>
      <p className="text-gray-700">{entry.a}</p>
      {entry.steps?.length > 0 && (
        <ol className="list-decimal pl-5 space-y-1 text-gray-700">
          {entry.steps.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ol>
      )}
      <Shot shotKey={entry.id} />
      {dest && (
        <button
          type="button"
          onClick={() => onGo(entry.goTo)}
          className="mt-1 px-2.5 py-1 rounded-md text-xs font-semibold bg-sky-50 text-sky-700 hover:bg-sky-100"
        >
          Go to {dest.label} →
        </button>
      )}
      <Rate entry={entry} screen={screen} isAdmin={isAdmin} />
    </div>
  );
}

function Chips({ entries, onPick }) {
  if (!entries.length) return null;
  return (
    <div className="flex flex-wrap gap-1.5">
      {entries.map((e) => (
        <button
          key={e.id}
          type="button"
          onClick={() => onPick(e)}
          className="text-left px-2.5 py-1 rounded-full text-xs bg-white border border-gray-200 text-gray-700 hover:border-sky-300 hover:text-sky-800"
        >
          {e.q}
        </button>
      ))}
    </div>
  );
}

function Assistant({ entries, screen, user, initialQuestion, initialAnswerId, onGo, onSupport }) {
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const endRef = useRef(null);
  const isAdmin = user?.role === ADMIN;
  const firstName = (user?.name || '').split(' ')[0];

  const reply = (question) => {
    const ranked = rank(entries, question, { screen });
    const best = ranked[0];
    const related = ranked.slice(1, 4).map((r) => r.entry);
    if (best && best.score >= CONFIDENT_SCORE) {
      return { from: 'bot', entry: best.entry, related };
    }
    // Help had no good answer: tell AxonX (scrubbed, without a name) so one gets written.
    sendFeedback({ kind: 'UNANSWERED', question, screen });
    return { from: 'bot', miss: true, related: ranked.slice(0, 4).map((r) => r.entry) };
  };

  const ask = (question) => {
    const q = question.trim();
    if (!q) return;
    // Worked out once, outside the state update: React may run updates twice, and reply() can
    // report an unanswered question.
    const answer = reply(q);
    setMessages((m) => [...m, { from: 'user', text: q }, answer]);
    setText('');
  };

  const showEntry = (entry) =>
    setMessages((m) => [
      ...m,
      { from: 'user', text: entry.q },
      {
        from: 'bot',
        entry,
        related: rank(entries, entry.q, { screen })
          .slice(1, 4)
          .map((r) => r.entry),
      },
    ]);

  // Answer what the panel was opened with, once (development mode runs effects twice).
  const startedWith = useRef(null);
  useEffect(() => {
    const key = `${initialQuestion || ''}|${initialAnswerId || ''}`;
    if (startedWith.current === key) return;
    startedWith.current = key;
    if (initialQuestion) ask(initialQuestion);
    else if (initialAnswerId) {
      const e = entries.find((x) => x.id === initialAnswerId);
      if (e) showEntry(e);
    }
  }, [initialQuestion, initialAnswerId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [messages]);

  const suggestions = suggestionsFor(entries, screen);

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-y-auto space-y-3 p-4 text-sm" aria-live="polite">
        <div className="bg-sky-50 rounded-xl p-3 space-y-2">
          <p className="text-gray-800">
            Hi{firstName ? ` ${firstName}` : ''}, I’m your AxonX help assistant. Ask me how to do
            something in your own words — for example “how do I admit a patient” or “cancel a
            visit”.
          </p>
          <Chips entries={suggestions} onPick={showEntry} />
        </div>
        {messages.map((m, i) =>
          m.from === 'user' ? (
            <div key={i} className="flex justify-end">
              <p className="max-w-[85%] bg-gray-900 text-white rounded-xl px-3 py-2">{m.text}</p>
            </div>
          ) : (
            <div key={i} className="bg-white border border-gray-200 rounded-xl p-3 space-y-3">
              {m.entry ? (
                <Answer entry={m.entry} onGo={onGo} screen={screen} isAdmin={isAdmin} />
              ) : (
                <p className="text-gray-700">
                  I couldn’t find an exact answer. Try different words, or pick one of these:
                </p>
              )}
              {m.related?.length > 0 && (
                <div className="space-y-1.5">
                  <p className="text-[11px] font-semibold uppercase text-gray-400">Related</p>
                  <Chips entries={m.related} onPick={showEntry} />
                </div>
              )}
              {m.miss && (
                <p className="text-xs text-gray-500">
                  {isAdmin ? (
                    <>
                      Still stuck?{' '}
                      <button
                        type="button"
                        onClick={onSupport}
                        className="font-semibold text-sky-700 underline"
                      >
                        Raise a support ticket
                      </button>{' '}
                      or call {SUPPORT_PHONE}.
                    </>
                  ) : (
                    'Still stuck? Your hospital admin can help.'
                  )}
                </p>
              )}
              {m.miss && (
                <p className="text-[11px] text-gray-400">
                  We noted this question (without your name) so the help can be improved. Please
                  don’t type patient names or numbers into help.
                </p>
              )}
            </div>
          )
        )}
        <div ref={endRef} />
      </div>
      <form
        className="border-t border-gray-200 p-3 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          ask(text);
        }}
      >
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Ask a question…"
          aria-label="Ask a question"
          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-transparent"
        />
        <button
          type="submit"
          disabled={!text.trim()}
          className="px-3 py-2 rounded-lg text-sm font-semibold bg-gray-900 text-white disabled:opacity-40"
        >
          Ask
        </button>
      </form>
    </div>
  );
}

function Topics({ entries, onPick }) {
  const [filter, setFilter] = useState('');
  const groups = useMemo(() => {
    const f = filter.trim().toLowerCase();
    const map = new Map();
    for (const e of entries) {
      if (f && !`${e.q} ${e.a}`.toLowerCase().includes(f)) continue;
      if (!map.has(e.category)) map.set(e.category, []);
      map.get(e.category).push(e);
    }
    return [...map.entries()];
  }, [entries, filter]);
  return (
    <div className="p-4 space-y-4 text-sm overflow-y-auto h-full">
      <input
        type="search"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        placeholder="Filter topics…"
        aria-label="Filter topics"
        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
      />
      {groups.length === 0 && <p className="text-gray-500">Nothing matches that.</p>}
      {groups.map(([category, list]) => (
        <section key={category}>
          <h3 className="text-xs font-semibold uppercase text-gray-500 mb-1.5">{category}</h3>
          <ul className="space-y-1">
            {list.map((e) => (
              <li key={e.id}>
                <button
                  type="button"
                  onClick={() => onPick(e)}
                  className="w-full text-left px-2 py-1.5 rounded-lg hover:bg-gray-50 text-gray-800"
                >
                  {e.q}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function Shortcuts() {
  return (
    <div className="p-4 space-y-5 text-sm overflow-y-auto h-full">
      {shortcutGroups().map((g) => (
        <section key={g.title}>
          <h3 className="text-xs font-semibold uppercase text-gray-500 mb-2">{g.title}</h3>
          <ul className="space-y-1.5">
            {g.items.map((it) => (
              <li key={it.text} className="flex items-center justify-between gap-3">
                <span className="text-gray-700">{it.text}</span>
                <span className="flex gap-1 shrink-0">
                  {it.keys.map((k) => (
                    <kbd
                      key={k}
                      className="min-w-[1.75rem] text-center px-1.5 py-0.5 rounded border border-gray-300 bg-gray-50 text-xs font-semibold text-gray-700"
                    >
                      {k}
                    </kbd>
                  ))}
                </span>
              </li>
            ))}
          </ul>
        </section>
      ))}
      <p className="text-xs text-gray-500">
        Single-letter shortcuts are ignored while you are typing in a field.
      </p>
    </div>
  );
}

export default function HelpPanel() {
  const { panelOpen, panelView, question, answerId, nav } = useHelpState();
  const navigate = useNavigate();
  const user = authService.getCurrentUser();
  const entries = user ? entriesFor(user) : [];
  const [picked, setPicked] = useState(null);

  useEffect(() => {
    if (!panelOpen) setPicked(null);
  }, [panelOpen]);

  if (!panelOpen || !user) return null;
  const isAdmin = user.role === ADMIN;
  const screen = nav?.activeTab || null;
  const goTo = (tabId) => {
    if (!helpStore.goTo(tabId)) return;
  };
  const openSupport = () => {
    if (!helpStore.goTo('support')) navigate('/hospital/admin?tab=support');
  };

  const activeView = panelView === 'setup' ? 'guides' : panelView;
  const views = [
    ['assistant', 'Assistant'],
    ['guides', 'Guides'],
    ['topics', 'Topics'],
    ['shortcuts', 'Shortcuts'],
  ];

  return (
    <aside
      aria-label="Help and support"
      className="fixed top-0 right-0 z-40 h-full w-full sm:w-[400px] bg-gray-50 border-l border-gray-200 shadow-2xl flex flex-col"
    >
      <header className="bg-white border-b border-gray-200 px-4 pt-3">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base font-bold text-gray-900">Help &amp; Support</h2>
            <p className="text-xs text-gray-500">
              For {ROLE_NAMES[user.role] || 'you'} · {entries.length} answers
            </p>
          </div>
          <button
            type="button"
            onClick={helpStore.closePanel}
            aria-label="Close help"
            className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100"
          >
            ✕
          </button>
        </div>
        <nav className="flex gap-1 mt-2" aria-label="Help sections">
          {views.map(([id, label]) => (
            <button
              key={id}
              type="button"
              aria-pressed={activeView === id}
              onClick={() => {
                setPicked(null);
                helpStore.setView(id);
              }}
              className={`px-3 py-2 text-xs font-semibold border-b-2 ${
                activeView === id
                  ? 'border-sky-600 text-sky-700'
                  : 'border-transparent text-gray-500 hover:text-gray-800'
              }`}
            >
              {label}
            </button>
          ))}
        </nav>
      </header>
      <div className="flex-1 min-h-0">
        {panelView === 'assistant' && (
          <Assistant
            key={`${question || ''}|${answerId || ''}|${picked?.id || ''}`}
            entries={entries}
            screen={screen}
            user={user}
            initialQuestion={question}
            initialAnswerId={picked?.id || answerId}
            onGo={goTo}
            onSupport={openSupport}
          />
        )}
        {panelView === 'topics' && (
          <Topics
            entries={entries}
            onPick={(e) => {
              setPicked(e);
              helpStore.setView('assistant');
            }}
          />
        )}
        {panelView === 'shortcuts' && <Shortcuts />}
        {(panelView === 'guides' || panelView === 'setup') && <Guides user={user} onGo={goTo} />}
      </div>
      <footer className="bg-white border-t border-gray-200 px-4 py-2 text-xs text-gray-500">
        {isAdmin ? (
          <>
            Need more help?{' '}
            <button
              type="button"
              onClick={openSupport}
              className="font-semibold text-sky-700 underline"
            >
              Raise a support ticket
            </button>{' '}
            · {SUPPORT_PHONE}
          </>
        ) : (
          'Can’t find it? Ask your hospital admin.'
        )}
      </footer>
    </aside>
  );
}
