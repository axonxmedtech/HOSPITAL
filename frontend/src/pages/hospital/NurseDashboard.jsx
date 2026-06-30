import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';
import PatientClinicalRecord from '../../components/nurse/PatientClinicalRecord';
import MedicationAdministrationModal from '../../components/nurse/MedicationAdministrationModal';

export default function NurseDashboard() {
  const [tab, setTab] = useState('tasks');
  const [patients, setPatients] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [selectedAdmission, setSelectedAdmission] = useState(null);
  const [wardFilter, setWardFilter] = useState('');
  const [tasksLoading, setTasksLoading] = useState(false);
  const [patientsLoading, setPatientsLoading] = useState(false);
  const [administerTarget, setAdministerTarget] = useState(null);
  const [notGivenOpenId, setNotGivenOpenId] = useState(null);
  const [notGivenStatus, setNotGivenStatus] = useState('HELD');
  const [notGivenReason, setNotGivenReason] = useState('');
  const [submittingId, setSubmittingId] = useState(null);
  const [errorId, setErrorId] = useState(null);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    loadTasks();
  }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (tab === 'patients') loadPatients();
  }, [tab]);

  async function loadPatients() {
    setPatientsLoading(true);
    try {
      const res = await nurseService.getMyPatients();
      setPatients(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setPatientsLoading(false);
    }
  }

  async function loadTasks() {
    setTasksLoading(true);
    try {
      const res = await nurseService.getMyTasks();
      setTasks(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setTasksLoading(false);
    }
  }

  async function handleExecuteTask(task, status) {
    try {
      await nurseService.executeTask(task.ipdAdmissionId, task.id, status);
      loadTasks();
    } catch (e) {
      alert(e.response?.data || 'Failed to update task');
    }
  }

  const wards = [...new Set(patients.map(p => p.wardName).filter(Boolean))];
  const filteredPatients = wardFilter
    ? patients.filter(p => p.wardName === wardFilter)
    : patients;

  const now = new Date();
  const MS_60 = 60 * 60 * 1000;

  const overdueTaskList = tasks
    .filter(t => t.scheduledAt && new Date(t.scheduledAt) < now)
    .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

  const dueSoonTaskList = tasks
    .filter(t => t.scheduledAt && new Date(t.scheduledAt) >= now && new Date(t.scheduledAt) - now <= MS_60)
    .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

  const upcomingTaskList = tasks
    .filter(t => !t.scheduledAt || (new Date(t.scheduledAt) >= now && new Date(t.scheduledAt) - now > MS_60))
    .sort((a, b) => {
      if (!a.scheduledAt) return 1;
      if (!b.scheduledAt) return -1;
      return new Date(a.scheduledAt) - new Date(b.scheduledAt);
    });

  const sortedTasks = [...overdueTaskList, ...dueSoonTaskList, ...upcomingTaskList];

  const overdueCount = overdueTaskList.length;
  const dueSoonCount = dueSoonTaskList.length;
  const patientCount = new Set(tasks.map(t => t.patientName).filter(Boolean)).size;

  function getTaskBucket(task) {
    if (!task.scheduledAt) return 'upcoming';
    const diffMs = new Date(task.scheduledAt) - now;
    if (diffMs < 0) return 'overdue';
    if (diffMs <= MS_60) return 'dueSoon';
    return 'upcoming';
  }

  function getTimeLabel(task) {
    if (!task.scheduledAt) return { label: 'No time set', cls: 'text-gray-400 text-xs' };
    const scheduled = new Date(task.scheduledAt);
    const diffMs = scheduled - now;
    if (diffMs < 0) {
      const mins = Math.floor(-diffMs / 60000);
      return { label: `OVERDUE ${mins} min`, cls: 'text-red-600 font-semibold text-xs' };
    }
    if (diffMs <= MS_60) {
      const mins = Math.floor(diffMs / 60000);
      return { label: `Due in ${mins} min`, cls: 'text-amber-600 font-semibold text-xs' };
    }
    return {
      label: `Due at ${scheduled.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`,
      cls: 'text-gray-500 text-xs',
    };
  }

  const INJECTABLE_KEYWORDS = ['IV', 'IM', 'SUB-Q', 'SUBQ', 'SUBCUTANEOUS', 'INTRAMUSCULAR', 'INTRAVENOUS'];
  function isInjectable(task) {
    const desc = (task.orderDescription || '').toUpperCase();
    return INJECTABLE_KEYWORDS.some(k => desc.includes(k));
  }

  async function handleGiven(task) {
    if (isInjectable(task)) {
      setAdministerTarget(task);
      return;
    }
    setSubmittingId(task.id);
    setErrorId(null);
    try {
      await nurseService.executeTask(task.ipdAdmissionId, task.id, {
        status: 'DONE', route: 'ORAL', administeredQuantity: 1.0,
      });
      loadTasks();
    } catch {
      setErrorId(task.id);
    } finally {
      setSubmittingId(null);
    }
  }

  async function handleSnooze(task) {
    setSubmittingId(task.id);
    setErrorId(null);
    try {
      await nurseService.executeTask(task.ipdAdmissionId, task.id, {
        status: 'HELD', notes: 'Snoozed — retry shortly',
      });
      loadTasks();
    } catch {
      setErrorId(task.id);
    } finally {
      setSubmittingId(null);
    }
  }

  async function handleNotGiven(task) {
    if (!notGivenReason.trim()) return;
    setSubmittingId(task.id);
    setErrorId(null);
    try {
      await nurseService.executeTask(task.ipdAdmissionId, task.id, {
        status: notGivenStatus, notes: notGivenReason.trim(),
      });
      setNotGivenOpenId(null);
      setNotGivenReason('');
      setNotGivenStatus('HELD');
      loadTasks();
    } catch {
      setErrorId(task.id);
    } finally {
      setSubmittingId(null);
    }
  }

  if (selectedAdmission) {
    return (
      <PatientClinicalRecord
        admission={selectedAdmission}
        onBack={() => setSelectedAdmission(null)}
      />
    );
  }

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-gray-800 mb-6">Nurse Dashboard</h1>

      <div className="flex gap-2 mb-6 border-b border-gray-200">
        {['patients', 'tasks'].map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 transition-colors ${
              tab === t
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'patients' ? 'My Patients' : 'My Tasks'}
          </button>
        ))}
      </div>

      {(tasksLoading || patientsLoading) && <div className="text-center py-8 text-gray-500">Loading...</div>}

      {!patientsLoading && tab === 'patients' && (
        <div>
          {wards.length > 0 && (
            <div className="mb-4">
              <select
                value={wardFilter}
                onChange={e => setWardFilter(e.target.value)}
                className="border border-gray-300 rounded px-3 py-2 text-sm"
              >
                <option value="">All Wards</option>
                {wards.map(w => <option key={w} value={w}>{w}</option>)}
              </select>
            </div>
          )}
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 text-left text-gray-600 uppercase text-xs tracking-wide">
                  <th className="px-4 py-3">Patient</th>
                  <th className="px-4 py-3">UHID</th>
                  <th className="px-4 py-3">Ward / Bed</th>
                  <th className="px-4 py-3">Admitted</th>
                  <th className="px-4 py-3">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredPatients.length === 0 && (
                  <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">No admitted patients</td></tr>
                )}
                {filteredPatients.map(p => (
                  <tr key={p.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-800">{p.patientName}</td>
                    <td className="px-4 py-3 text-gray-500">{p.uhid || '—'}</td>
                    <td className="px-4 py-3 text-gray-500">{p.wardName} / {p.bedNumber}</td>
                    <td className="px-4 py-3 text-gray-500">
                      {p.admissionDate ? new Date(p.admissionDate).toLocaleDateString() : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setSelectedAdmission(p)}
                        className="text-blue-600 hover:underline text-sm"
                      >
                        Open
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!tasksLoading && tab === 'tasks' && (
        <div className="space-y-3">
          {tasks.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-4">
              {overdueCount > 0 && (
                <span className="text-xs font-semibold px-3 py-1.5 rounded-full border bg-red-50 text-red-700 border-red-200">
                  ⚠️ {overdueCount} Overdue
                </span>
              )}
              {dueSoonCount > 0 && (
                <span className="text-xs font-semibold px-3 py-1.5 rounded-full border bg-amber-50 text-amber-700 border-amber-200">
                  🕐 {dueSoonCount} Due Soon
                </span>
              )}
              <span className="text-xs font-semibold px-3 py-1.5 rounded-full border bg-blue-50 text-blue-700 border-blue-200">
                👤 {patientCount} Patient{patientCount !== 1 ? 's' : ''}
              </span>
            </div>
          )}

          {sortedTasks.length === 0 && (
            <div className="text-center py-8 text-gray-400">No pending tasks</div>
          )}

          {sortedTasks.map(task => {
            const bucket = getTaskBucket(task);
            const { label: timeLabel, cls: timeLabelCls } = getTimeLabel(task);
            const cardCls = bucket === 'overdue'
              ? 'border-l-4 border-red-400 bg-red-50 border border-red-200'
              : bucket === 'dueSoon'
              ? 'border-l-4 border-amber-400 bg-amber-50 border border-amber-200'
              : 'border-l-4 border-gray-200 bg-white border border-gray-200';
            return (
              <div key={task.id} className={`flex items-center justify-between p-4 rounded-lg ${cardCls}`}>
                <div className="flex-1 min-w-0">
                  <span className={timeLabelCls}>{timeLabel}</span>
                  <p className="font-medium text-gray-800 mt-0.5">{task.orderDescription}</p>
                  <p className="text-sm text-gray-500 mt-0.5">
                    {task.orderType}
                    {task.patientName && ` · ${task.patientName}`}
                    {task.bedNumber ? ` · Bed ${task.bedNumber}` : task.wardName ? ` · ${task.wardName}` : ''}
                  </p>
                </div>
                <div className="ml-4 shrink-0">
                  {task.orderType === 'MEDICATION' ? (
                    <div>
                      {notGivenOpenId === task.id ? (
                        <div className="flex flex-col gap-2 min-w-[220px]">
                          <p className="text-xs font-semibold text-gray-600">Why wasn't this given?</p>
                          <div className="flex gap-1.5">
                            {[
                              { key: 'HELD', label: 'Hold' },
                              { key: 'REFUSED', label: 'Refuse' },
                              { key: 'SKIPPED', label: 'Skip' },
                            ].map(opt => (
                              <button
                                key={opt.key}
                                type="button"
                                onClick={() => setNotGivenStatus(opt.key)}
                                className={`px-2.5 py-1 text-xs font-semibold rounded-lg border transition-all ${
                                  notGivenStatus === opt.key
                                    ? 'bg-gray-800 text-white border-gray-800'
                                    : 'bg-white text-gray-600 border-gray-300 hover:border-gray-400'
                                }`}
                              >
                                {opt.label}
                              </button>
                            ))}
                          </div>
                          <input
                            type="text"
                            value={notGivenReason}
                            onChange={e => setNotGivenReason(e.target.value)}
                            placeholder="Reason (required)"
                            className="text-xs border border-gray-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-300"
                          />
                          {errorId === task.id && (
                            <p className="text-xs text-red-600">⚠ Failed to record. Try again.</p>
                          )}
                          <div className="flex gap-2">
                            <button
                              type="button"
                              onClick={() => { setNotGivenOpenId(null); setNotGivenReason(''); setNotGivenStatus('HELD'); }}
                              className="flex-1 px-3 py-1.5 text-xs font-medium border border-gray-200 rounded-lg text-gray-600 hover:bg-gray-50 transition-colors"
                            >
                              Cancel
                            </button>
                            <button
                              type="button"
                              onClick={() => handleNotGiven(task)}
                              disabled={!notGivenReason.trim() || submittingId === task.id}
                              className="flex-1 px-3 py-1.5 text-xs font-semibold bg-gray-800 text-white rounded-lg hover:bg-gray-900 disabled:opacity-50 transition-all"
                            >
                              {submittingId === task.id ? '…' : 'Confirm'}
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex flex-col gap-1.5 items-end">
                          <div className="flex gap-1.5">
                            <button
                              type="button"
                              onClick={() => handleGiven(task)}
                              disabled={submittingId === task.id}
                              className="px-3 py-1.5 text-xs font-semibold bg-green-600 hover:bg-green-700 text-white rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50"
                            >
                              {submittingId === task.id ? '…' : '✓ Given'}
                            </button>
                            <button
                              type="button"
                              onClick={() => handleSnooze(task)}
                              disabled={submittingId === task.id}
                              className="px-3 py-1.5 text-xs font-semibold bg-amber-500 hover:bg-amber-600 text-white rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50"
                            >
                              ⏰ Snooze
                            </button>
                            <button
                              type="button"
                              onClick={() => { setNotGivenOpenId(task.id); setNotGivenReason(''); setNotGivenStatus('HELD'); }}
                              disabled={submittingId === task.id}
                              className="px-3 py-1.5 text-xs font-semibold bg-white hover:bg-gray-50 text-gray-700 border border-gray-300 rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50"
                            >
                              ✗ Not Given
                            </button>
                          </div>
                          {errorId === task.id && (
                            <p className="text-xs text-red-600">⚠ Failed to record. Try again.</p>
                          )}
                        </div>
                      )}
                    </div>
                  ) : (
                    <button
                      onClick={() => setAdministerTarget(task)}
                      className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all active:scale-95"
                    >
                      📋 Execute
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
      {administerTarget && (
        <MedicationAdministrationModal
          task={administerTarget}
          onClose={() => setAdministerTarget(null)}
          onSave={async (payload) => {
            await nurseService.executeTask(administerTarget.ipdAdmissionId, administerTarget.id, payload);
            setAdministerTarget(null);
            loadTasks();
          }}
        />
      )}
    </div>
  );
}
