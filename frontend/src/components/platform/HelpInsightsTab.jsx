import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import { ALL_ENTRIES } from '../../help/content';
import { ROLE_NAMES } from '../../help/content/roles';
import apiClient from '../../services/apiService';

/**
 * Super Admin → Help Insights: what people asked in-app help that it could not answer (most
 * asked first), and how each answer was rated. Write the missing answers into the help content,
 * then Mark answered to take the question off the list. Questions arrive scrubbed of numbers and
 * email addresses, with no name attached.
 */

const QUESTION_BY_ID = new Map(ALL_ENTRIES.map((e) => [e.id, e.q]));
const PERIODS = [7, 30, 90];

const when = (v) => {
  if (!v) return '—';
  const d = new Date(v);
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
};

export default function HelpInsightsTab() {
  const { success, error: toastError } = useToast();
  const [days, setDays] = useState(30);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const res = await apiClient.get('/platform/help/insights', { params: { days } });
      setData(res.data);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    load();
  }, [load]);

  const resolve = async (q) => {
    try {
      await apiClient.put('/platform/help/questions/resolve', { key: q.key });
      setData((d) => ({ ...d, questions: d.questions.filter((x) => x.key !== q.key) }));
      success('Marked as answered');
    } catch {
      toastError('Could not mark it answered. Try again.');
    }
  };

  const questions = data?.questions || [];
  const answers = (data?.answers || []).filter((a) => QUESTION_BY_ID.has(a.entryId));
  const totalVotes = answers.reduce((n, a) => n + a.helpful + a.notHelpful, 0);
  const helpfulVotes = answers.reduce((n, a) => n + a.helpful, 0);

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <div className="flex gap-1 bg-gray-100 p-1 rounded-lg" role="group" aria-label="Period">
          {PERIODS.map((p) => (
            <button
              key={p}
              type="button"
              aria-pressed={days === p}
              onClick={() => setDays(p)}
              className={`px-3 py-1.5 rounded-md text-xs font-semibold ${
                days === p ? 'bg-white shadow text-gray-900' : 'text-gray-600'
              }`}
            >
              Last {p} days
            </button>
          ))}
        </div>
      </div>

      {failed && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 text-sm text-gray-700">
          Help insights could not be loaded.{' '}
          <button type="button" onClick={load} className="font-semibold text-sky-700 underline">
            Try again
          </button>
        </div>
      )}

      {!failed && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-white border border-gray-200 rounded-xl p-4">
              <p className="text-xs font-semibold uppercase text-gray-500">Unanswered questions</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">
                {loading ? '…' : questions.length}
              </p>
            </div>
            <div className="bg-white border border-gray-200 rounded-xl p-4">
              <p className="text-xs font-semibold uppercase text-gray-500">Answers rated</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">{loading ? '…' : totalVotes}</p>
            </div>
            <div className="bg-white border border-gray-200 rounded-xl p-4">
              <p className="text-xs font-semibold uppercase text-gray-500">Found helpful</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">
                {loading || !totalVotes ? '—' : `${Math.round((helpfulVotes / totalVotes) * 100)}%`}
              </p>
            </div>
          </div>

          <section className="bg-white border border-gray-200 rounded-xl">
            <header className="px-5 py-4 border-b border-gray-100">
              <h2 className="text-base font-semibold text-gray-900">
                Questions help could not answer
              </h2>
              <p className="text-xs text-gray-500 mt-0.5">
                Most asked first. Add an answer to the help content, then mark it answered.
              </p>
            </header>
            {!loading && questions.length === 0 && (
              <p className="px-5 py-6 text-sm text-gray-500">Nothing unanswered in this period.</p>
            )}
            <ul className="divide-y divide-gray-100">
              {questions.map((q) => (
                <li
                  key={q.key}
                  className="px-5 py-3 flex flex-wrap items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 break-words">{q.question}</p>
                    <p className="text-xs text-gray-500 mt-0.5">
                      <span>
                        Asked {q.count} {q.count === 1 ? 'time' : 'times'} · last{' '}
                        {when(q.lastAsked)}
                      </span>
                      {q.roles?.length > 0 && (
                        <span> · {q.roles.map((r) => ROLE_NAMES[r] || r).join(', ')}</span>
                      )}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => resolve(q)}
                    className="shrink-0 px-3 py-1.5 rounded-lg text-xs font-semibold border border-gray-300 text-gray-700 hover:bg-gray-50"
                  >
                    Mark answered
                  </button>
                </li>
              ))}
            </ul>
          </section>

          <section className="bg-white border border-gray-200 rounded-xl">
            <header className="px-5 py-4 border-b border-gray-100">
              <h2 className="text-base font-semibold text-gray-900">How answers were rated</h2>
              <p className="text-xs text-gray-500 mt-0.5">
                Least helpful first — these need rewriting.
              </p>
            </header>
            {!loading && answers.length === 0 && (
              <p className="px-5 py-6 text-sm text-gray-500">No ratings in this period.</p>
            )}
            {answers.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs uppercase text-gray-500">
                      <th className="px-5 py-2 font-semibold">Answer</th>
                      <th className="px-5 py-2 font-semibold text-right">Helpful</th>
                      <th className="px-5 py-2 font-semibold text-right">Not helpful</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {answers.map((a) => (
                      <tr key={a.entryId}>
                        <td className="px-5 py-2.5 text-gray-900">
                          {QUESTION_BY_ID.get(a.entryId)}
                        </td>
                        <td className="px-5 py-2.5 text-right text-emerald-700">{a.helpful}</td>
                        <td className="px-5 py-2.5 text-right text-rose-700">{a.notHelpful}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
