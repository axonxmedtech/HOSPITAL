import { useCallback, useEffect, useState } from 'react';
import Button from '../../../components/Button';
import { useConfirm } from '../../../context/ConfirmContext';
import { useToast } from '../../../context/ToastContext';
import hospitalService from '../../../services/hospitalService';
import icuService from '../../../services/icuService';
import { extractApiError } from '../../../utils/apiError';

const PRIORITY_TONE = {
  URGENT: 'bg-rose-100 text-rose-700',
  HIGH: 'bg-rose-100 text-rose-700',
  LOW: 'bg-gray-100 text-gray-700',
};
const STATUS_TONE = {
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
};

const EMPTY = { bedId: '', taskDescription: '', assignedTo: '', priority: 'MEDIUM' };

/**
 * ICU housekeeping: extra jobs beyond the routine turnover of a bed (deep disinfection, a
 * broken monitor, a spill). Routine turnover needs no task: a vacated bed shows "Cleaning
 * Required" on the bed board and "Mark cleaned" frees it. A task is tied to a real ICU bed
 * (picked from the list, never typed), and completing it frees that bed on the server.
 */
export default function IcuHousekeeping({ refreshKey = 0, onChanged }) {
  const { success, error: toastError } = useToast();
  const confirm = useConfirm();
  const [tasks, setTasks] = useState([]);
  const [beds, setBeds] = useState([]);
  const [showDone, setShowDone] = useState(false);
  const [form, setForm] = useState(null); // null = closed
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const [list, board] = await Promise.all([
        hospitalService.getIcuCleaningTasks(),
        icuService.getBoard().catch(() => null),
      ]);
      setTasks(list || []);
      setBeds(board?.beds || []);
    } catch (err) {
      toastError(extractApiError(err, 'Could not load housekeeping tasks'));
    }
  }, [toastError]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  const done = () => {
    load();
    onChanged?.();
  };

  const create = async (e) => {
    e.preventDefault();
    if (!form.taskDescription.trim()) return toastError('Describe the task');
    const bed = beds.find((b) => String(b.bedId) === String(form.bedId));
    setSaving(true);
    try {
      await hospitalService.createIcuCleaningTask({
        taskDescription: form.taskDescription.trim(),
        assignedTo: form.assignedTo.trim() || null,
        priority: form.priority,
        ...(bed
          ? { bedId: bed.bedId, wardId: bed.wardId, wardName: bed.wardName, bedNumber: bed.bedCode }
          : {}),
      });
      success('Task added');
      setForm(null);
      done();
    } catch (err) {
      toastError(extractApiError(err, 'Could not add the task'));
    } finally {
      setSaving(false);
    }
  };

  const complete = async (task) => {
    const ok = await confirm({
      title: 'Complete this task?',
      message: task.bedNumber
        ? `${task.bedNumber} becomes available for the next admission.`
        : 'The task is closed.',
      confirmLabel: 'Mark completed',
    });
    if (!ok) return;
    try {
      await hospitalService.updateIcuCleaningTaskStatus(task.id, 'COMPLETED');
      success('Task completed');
      done();
    } catch (err) {
      toastError(extractApiError(err, 'Could not update the task'));
    }
  };

  const remove = async (task) => {
    const ok = await confirm({
      title: 'Delete this task?',
      message: `"${task.taskDescription}" will be removed.`,
      confirmLabel: 'Delete',
    });
    if (!ok) return;
    try {
      await hospitalService.deleteIcuCleaningTask(task.id);
      success('Task deleted');
      done();
    } catch (err) {
      toastError(extractApiError(err, 'Could not delete the task'));
    }
  };

  const open = tasks.filter((t) => t.status !== 'COMPLETED');
  const shown = showDone ? tasks : open;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-gray-600">
          Routine turnover needs no task: use <strong>Mark cleaned</strong> on the bed. Add a
          task for anything extra.
        </p>
        <div className="flex items-center gap-3">
          <label className="text-xs text-gray-600 flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={showDone}
              onChange={(e) => setShowDone(e.target.checked)}
            />
            Show completed
          </label>
          <Button size="sm" onClick={() => setForm({ ...EMPTY })}>
            + Add task
          </Button>
        </div>
      </div>

      {form && (
        <form onSubmit={create} className="border border-slate-200 rounded-2xl p-4 bg-slate-50 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label htmlFor="hk-bed" className="block text-xs font-medium text-gray-600 mb-1">
                Bed
              </label>
              <select
                id="hk-bed"
                value={form.bedId}
                onChange={(e) => setForm((f) => ({ ...f, bedId: e.target.value }))}
                className="w-full border p-2 rounded text-sm bg-white"
              >
                <option value="">Whole unit (no bed)</option>
                {beds.map((b) => (
                  <option key={b.bedId} value={b.bedId}>
                    {b.wardName} · {b.bedCode}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label
                htmlFor="hk-priority"
                className="block text-xs font-medium text-gray-600 mb-1"
              >
                Priority
              </label>
              <select
                id="hk-priority"
                value={form.priority}
                onChange={(e) => setForm((f) => ({ ...f, priority: e.target.value }))}
                className="w-full border p-2 rounded text-sm bg-white"
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="URGENT">Urgent</option>
              </select>
            </div>
            <div>
              <label htmlFor="hk-assigned" className="block text-xs font-medium text-gray-600 mb-1">
                Assigned to
              </label>
              <input
                id="hk-assigned"
                value={form.assignedTo}
                onChange={(e) => setForm((f) => ({ ...f, assignedTo: e.target.value }))}
                placeholder="Staff name (optional)"
                className="w-full border p-2 rounded text-sm"
              />
            </div>
          </div>
          <div>
            <label htmlFor="hk-desc" className="block text-xs font-medium text-gray-600 mb-1">
              Task *
            </label>
            <input
              id="hk-desc"
              value={form.taskDescription}
              onChange={(e) => setForm((f) => ({ ...f, taskDescription: e.target.value }))}
              placeholder="e.g. Terminal disinfection after an infectious case"
              className="w-full border p-2 rounded text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => setForm(null)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" loading={saving}>
              Add task
            </Button>
          </div>
        </form>
      )}

      {shown.length === 0 ? (
        <p className="text-sm text-gray-500 text-center py-8">
          {showDone ? 'No housekeeping tasks recorded.' : 'No open housekeeping tasks.'}
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="text-xs font-semibold text-slate-500 uppercase tracking-wide border-b border-slate-200 whitespace-nowrap">
                <th className="px-3 py-2">Bed</th>
                <th className="px-3 py-2">Task</th>
                <th className="px-3 py-2">Priority</th>
                <th className="px-3 py-2">Assigned to</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((t) => (
                <tr key={t.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-3 py-2 font-medium">
                    {t.bedNumber ? `${t.wardName || 'ICU'} · ${t.bedNumber}` : t.wardName || 'Unit'}
                  </td>
                  <td className="px-3 py-2">{t.taskDescription}</td>
                  <td className="px-3 py-2">
                    <span
                      className={`px-2 py-0.5 rounded text-[11px] font-bold ${PRIORITY_TONE[t.priority] || 'bg-amber-100 text-amber-700'}`}
                    >
                      {t.priority || 'MEDIUM'}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-gray-600">{t.assignedTo || '—'}</td>
                  <td className="px-3 py-2">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs font-bold ${STATUS_TONE[t.status] || 'bg-amber-100 text-amber-800'}`}
                    >
                      {t.status}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-right whitespace-nowrap space-x-2">
                    {t.status !== 'COMPLETED' && (
                      <button
                        type="button"
                        onClick={() => complete(t)}
                        className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold rounded"
                      >
                        Complete
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => remove(t)}
                      className="px-2.5 py-1 bg-rose-50 hover:bg-rose-100 text-rose-700 text-xs font-semibold rounded border border-rose-200"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
