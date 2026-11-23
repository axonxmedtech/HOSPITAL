import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';

const STATUS = {
  ORDERED: { label: 'Ordered', cls: 'bg-amber-50 text-amber-800' },
  SAMPLE_COLLECTED: { label: 'Sample collected', cls: 'bg-sky-50 text-sky-800' },
  COMPLETED: { label: 'Result in', cls: 'bg-emerald-50 text-emerald-800' },
  CANCELLED: { label: 'Cancelled', cls: 'bg-gray-100 text-gray-500' },
};

const stamp = (at) =>
  at
    ? new Date(at).toLocaleString(undefined, {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';

/**
 * Investigations for one inpatient. The doctor orders and cancels; the ward (nurse, reception)
 * marks the sample collected and records the result. The server enforces the same split.
 */
export default function InvestigationsPanel({ admissionId, canOrder = false, canWork = true }) {
  const { success, error: toastError } = useToast();
  const [list, setList] = useState(null);
  const [form, setForm] = useState({ tests: '', priority: 'ROUTINE', notes: '' });
  const [saving, setSaving] = useState(false);
  const [resultFor, setResultFor] = useState(null); // { publicId, testName, text }
  const [cancelFor, setCancelFor] = useState(null); // { publicId, testName, reason }

  const load = useCallback(async () => {
    try {
      setList(await hospitalService.getIpdInvestigations(admissionId));
    } catch (err) {
      toastError(extractApiError(err, 'Could not load investigations'));
      setList([]);
    }
  }, [admissionId, toastError]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (fn, okText) => {
    setSaving(true);
    try {
      await fn();
      success(okText);
      await load();
      return true;
    } catch (err) {
      toastError(extractApiError(err, 'Could not save'));
      return false;
    } finally {
      setSaving(false);
    }
  };

  const order = async () => {
    const tests = form.tests
      .split(/[\n,]/)
      .map((t) => t.trim())
      .filter(Boolean);
    if (tests.length === 0) return toastError('Enter at least one test');
    const ok = await act(
      () =>
        hospitalService.orderIpdInvestigations(admissionId, {
          tests,
          priority: form.priority,
          notes: form.notes.trim() || null,
        }),
      `${tests.length} test${tests.length === 1 ? '' : 's'} ordered`
    );
    if (ok) setForm({ tests: '', priority: 'ROUTINE', notes: '' });
  };

  return (
    <div>
      {canOrder && (
        <div className="border rounded p-3 mb-4 bg-gray-50">
          <label htmlFor="inv-tests" className="block text-sm font-medium mb-1">
            Order tests (one per line, or separated by commas)
          </label>
          <textarea
            id="inv-tests"
            rows={2}
            value={form.tests}
            onChange={(e) => setForm({ ...form, tests: e.target.value })}
            placeholder="CBC, LFT, Urine routine"
            className="w-full border p-2 rounded mb-2 bg-white"
          />
          <div className="flex flex-wrap gap-2 items-center">
            <select
              aria-label="Priority"
              value={form.priority}
              onChange={(e) => setForm({ ...form, priority: e.target.value })}
              className="border p-2 rounded bg-white text-sm"
            >
              <option value="ROUTINE">Routine</option>
              <option value="URGENT">Urgent</option>
            </select>
            <input
              aria-label="Note for the ward"
              value={form.notes}
              maxLength={500}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
              placeholder="Note (e.g. fasting sample)"
              className="flex-1 min-w-[10rem] border p-2 rounded bg-white text-sm"
            />
            <button
              type="button"
              onClick={order}
              disabled={saving}
              className="px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-semibold disabled:opacity-60"
            >
              Order
            </button>
          </div>
        </div>
      )}

      {list === null && <p className="text-sm text-gray-500">Loading…</p>}
      {list?.length === 0 && <p className="text-sm text-gray-500">No investigations ordered.</p>}
      {list?.length > 0 && (
        <ul className="space-y-2">
          {list.map((o) => {
            const st = STATUS[o.status] || STATUS.ORDERED;
            const open = o.status === 'ORDERED' || o.status === 'SAMPLE_COLLECTED';
            return (
              <li key={o.publicId} className="p-3 border rounded">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="font-medium">
                    {o.testName}
                    {o.priority === 'URGENT' && (
                      <span className="ml-2 text-xs font-bold text-red-700">URGENT</span>
                    )}
                  </div>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${st.cls}`}>{st.label}</span>
                </div>
                <div className="text-xs text-gray-500 mt-0.5">
                  Ordered {stamp(o.orderedAt)}
                  {o.sampleCollectedAt ? ` · sample ${stamp(o.sampleCollectedAt)}` : ''}
                  {o.resultAt ? ` · result ${stamp(o.resultAt)}` : ''}
                </div>
                {o.notes && <div className="text-xs text-gray-600 mt-1">Note: {o.notes}</div>}
                {o.resultText && (
                  <div className="text-sm mt-1 whitespace-pre-line bg-emerald-50/50 rounded p-2">
                    {o.resultText}
                  </div>
                )}
                {o.cancelReason && (
                  <div className="text-xs text-gray-500 mt-1">Cancelled: {o.cancelReason}</div>
                )}
                <div className="flex gap-3 mt-2 text-sm">
                  {canWork && o.status === 'ORDERED' && (
                    <button
                      type="button"
                      disabled={saving}
                      onClick={() =>
                        act(
                          () => hospitalService.markInvestigationSample(o.publicId),
                          'Sample marked collected'
                        )
                      }
                      className="text-sky-700 hover:underline"
                    >
                      Sample collected
                    </button>
                  )}
                  {canWork && o.status !== 'CANCELLED' && (
                    <button
                      type="button"
                      onClick={() =>
                        setResultFor({
                          publicId: o.publicId,
                          testName: o.testName,
                          text: o.resultText || '',
                        })
                      }
                      className="text-emerald-700 hover:underline"
                    >
                      {o.resultText ? 'Correct result' : 'Record result'}
                    </button>
                  )}
                  {canOrder && open && (
                    <button
                      type="button"
                      onClick={() =>
                        setCancelFor({ publicId: o.publicId, testName: o.testName, reason: '' })
                      }
                      className="text-red-700 hover:underline"
                    >
                      Cancel test
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {resultFor && (
        <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50 px-4">
          <dialog
            open
            aria-modal="true"
            aria-label={`Result for ${resultFor.testName}`}
            className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-md p-6"
          >
            <h3 className="text-lg font-semibold mb-3">Result — {resultFor.testName}</h3>
            <textarea
              aria-label="Result"
              rows={5}
              maxLength={4000}
              value={resultFor.text}
              onChange={(e) => setResultFor({ ...resultFor, text: e.target.value })}
              className="w-full border p-2 rounded"
            />
            <div className="flex justify-end gap-3 mt-4">
              <button
                type="button"
                onClick={() => setResultFor(null)}
                className="px-3 py-1.5 bg-gray-100 rounded"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={saving}
                onClick={async () => {
                  if (!resultFor.text.trim()) return toastError('Enter the result');
                  const ok = await act(
                    () =>
                      hospitalService.recordInvestigationResult(
                        resultFor.publicId,
                        resultFor.text.trim()
                      ),
                    'Result saved'
                  );
                  if (ok) setResultFor(null);
                }}
                className="px-3 py-1.5 bg-emerald-600 text-white rounded disabled:opacity-60"
              >
                Save result
              </button>
            </div>
          </dialog>
        </div>
      )}

      {cancelFor && (
        <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50 px-4">
          <dialog
            open
            aria-modal="true"
            aria-label={`Cancel ${cancelFor.testName}`}
            className="relative m-0 border-0 text-gray-900 bg-white rounded-lg w-full max-w-md p-6"
          >
            <h3 className="text-lg font-semibold mb-3">Cancel {cancelFor.testName}</h3>
            <label htmlFor="inv-cancel-reason" className="block text-sm font-medium mb-1">
              Reason *
            </label>
            <input
              id="inv-cancel-reason"
              value={cancelFor.reason}
              maxLength={255}
              onChange={(e) => setCancelFor({ ...cancelFor, reason: e.target.value })}
              className="w-full border p-2 rounded"
            />
            <div className="flex justify-end gap-3 mt-4">
              <button
                type="button"
                onClick={() => setCancelFor(null)}
                className="px-3 py-1.5 bg-gray-100 rounded"
              >
                Keep test
              </button>
              <button
                type="button"
                disabled={saving}
                onClick={async () => {
                  if (!cancelFor.reason.trim())
                    return toastError('Enter why the test is cancelled');
                  const ok = await act(
                    () =>
                      hospitalService.cancelInvestigation(
                        cancelFor.publicId,
                        cancelFor.reason.trim()
                      ),
                    'Test cancelled'
                  );
                  if (ok) setCancelFor(null);
                }}
                className="px-3 py-1.5 bg-red-600 text-white rounded disabled:opacity-60"
              >
                Cancel test
              </button>
            </div>
          </dialog>
        </div>
      )}
    </div>
  );
}
