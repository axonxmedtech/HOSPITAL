import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import nurseService from '../../services/nurseService';
import hospitalService from '../../services/hospitalService';
import PatientClinicalRecord from '../../components/nurse/PatientClinicalRecord';
import MedicationAdministrationModal from '../../components/nurse/MedicationAdministrationModal';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import PageHeader from '../../components/PageHeader';
import authService from '../../services/authService';
import { useToast } from '../../context/ToastContext';
import ProfileModal from '../../components/ProfileModal';

export default function NurseDashboard() {
  const navigate = useNavigate();
  const { success, error: toastError } = useToast();
  const [user, setUser] = useState(() => authService.getCurrentUser() || {});

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

  // Sidebar / Navbar states
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  // Shift handover states
  const [shiftMode, setShiftMode] = useState('FIXED');
  const [manualShiftStart, setManualShiftStart] = useState('');
  const [shiftActivity, setShiftActivity] = useState(null);
  const [shiftActivityLoading, setShiftActivityLoading] = useState(false);

  const getShiftWindow = (mode, manualStart) => {
    if (mode === 'MANUAL' && manualStart) {
      const today = new Date();
      const [h, m] = manualStart.split(':').map(Number);
      const start = new Date(today.getFullYear(), today.getMonth(), today.getDate(), h, m, 0);
      const end = new Date(start.getTime() + 8 * 60 * 60 * 1000);
      const pad = n => String(n).padStart(2, '0');
      const endLabel = `${pad(end.getHours())}:${pad(end.getMinutes())}`;
      return {
        label: `Manual Shift · ${manualStart}–${endLabel}`,
        start: start.toISOString(),
        end: end.toISOString(),
      };
    }
    const hour = new Date().getHours();
    const d = new Date();
    const fmt = date =>
      `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    const today = fmt(d);
    if (hour >= 7 && hour < 15)
      return { label: 'Morning Shift · 07:00–15:00', start: `${today}T07:00:00`, end: `${today}T15:00:00` };
    if (hour >= 15 && hour < 23)
      return { label: 'Evening Shift · 15:00–23:00', start: `${today}T15:00:00`, end: `${today}T23:00:00` };
    const isAfterMidnight = hour < 7;
    const shiftDate = fmt(isAfterMidnight ? new Date(d.getTime() - 86400000) : d);
    const nextDate = fmt(isAfterMidnight ? d : new Date(d.getTime() + 86400000));
    return { label: 'Night Shift · 23:00–07:00', start: `${shiftDate}T23:00:00`, end: `${nextDate}T07:00:00` };
  };

  useEffect(() => {
    loadTasks();
  }, []);

  useEffect(() => {
    if (tab === 'patients') loadPatients();
  }, [tab]);

  useEffect(() => {
    hospitalService.getHospitalOperationsSettings()
      .then(data => {
        const mode = data.shiftMode || 'FIXED';
        setShiftMode(mode);
        if (mode === 'MANUAL') {
          const hour = new Date().getHours();
          const defaultStart =
            hour >= 15 && hour < 23 ? '15:00' : hour >= 7 && hour < 15 ? '07:00' : '23:00';
          setManualShiftStart(defaultStart);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const window = getShiftWindow(shiftMode, manualShiftStart);
    if (!window) return;
    setShiftActivityLoading(true);
    hospitalService.getShiftActivity(window.start, window.end)
      .then(data => setShiftActivity(data))
      .catch(() => setShiftActivity(null))
      .finally(() => setShiftActivityLoading(false));
  }, [shiftMode, manualShiftStart]);

  async function loadPatients() {
    setPatientsLoading(true);
    try {
      const res = await nurseService.getMyPatients();
      setPatients(res.data || []);
    } catch (e) {
      console.error(e);
      toastError('Failed to load patients');
    } finally {
      setPatientsLoading(false);
    }
  }

  async function loadTasks() {
    setTasksLoading(true);
    try {
      const res = await nurseService.getMyTasks();
      setTasks(res.data || []);
    } catch (e) {
      console.error(e);
      toastError('Failed to load tasks');
    } finally {
      setTasksLoading(false);
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
      return { label: `OVERDUE ${mins} min`, cls: 'text-red-600 font-bold text-xs' };
    }
    if (diffMs <= MS_60) {
      const mins = Math.floor(diffMs / 60000);
      return { label: `Due in ${mins} min`, cls: 'text-amber-600 font-bold text-xs' };
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
      success('Medication administered successfully');
      loadTasks();
    } catch {
      setErrorId(task.id);
      toastError('Failed to record administration');
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
      success('Task snoozed');
      loadTasks();
    } catch {
      setErrorId(task.id);
      toastError('Failed to snooze task');
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
      success('Status recorded successfully');
      loadTasks();
    } catch {
      setErrorId(task.id);
      toastError('Failed to record status');
    } finally {
      setSubmittingId(null);
    }
  }

  const handleLogout = () => {
    const loginUrl = authService.getLoginUrl();
    authService.logout();
    navigate(loginUrl);
  };

  const shiftWindow = getShiftWindow(shiftMode, manualShiftStart);

  const tabs = [
    { id: 'tasks', label: 'My Tasks', icon: null },
    { id: 'patients', label: 'My Patients', icon: null },
  ];

  return (
    <div className="flex h-screen bg-white">
      {/* Sidebar */}
      <Sidebar
        title="HMS Portal"
        tabs={tabs}
        activeTab={tab}
        onTabChange={setTab}
        footerTitle="Nurse"
        footerData={user?.hospitalName}
        variant="plain"
        isCollapsed={sidebarCollapsed}
        showOnMobile={true}
      />

      {/* Main Content Wrapper */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Navbar */}
        <Navbar
          title={tabs.find(t => t.id === tab)?.label}
          user={user}
          onLogout={handleLogout}
          onProfile={() => setProfileOpen(true)}
          onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
        />

        {/* Main Content Area */}
        <main className="flex-1 overflow-x-hidden overflow-y-auto bg-white p-8">
          {selectedAdmission ? (
            <PatientClinicalRecord
              admission={selectedAdmission}
              onBack={() => {
                setSelectedAdmission(null);
                loadPatients();
              }}
            />
          ) : (
            <div className="space-y-6">
              {/* standardized header */}
              <PageHeader
                title={tab === 'tasks' ? 'Nurse Tasks' : 'Ward Patients'}
                subtitle={tab === 'tasks' ? 'Administer clinical tasks and check schedules.' : 'Review admitted cases in your assigned unit.'}
                filter={tab === 'patients' && wards.length > 0 ? (
                  <select
                    value={wardFilter}
                    onChange={e => setWardFilter(e.target.value)}
                    className="border border-gray-200 rounded-xl px-3 py-1.5 text-sm bg-white text-gray-700 shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="">All Wards</option>
                    {wards.map(w => <option key={w} value={w}>{w}</option>)}
                  </select>
                ) : null}
              />

              {(tasksLoading || patientsLoading) && (
                <div className="flex justify-center items-center py-20">
                  <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
                </div>
              )}

              {/* Patient Tab View */}
              {!patientsLoading && tab === 'patients' && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm text-left">
                      <thead>
                        <tr className="bg-gray-50 border-b border-gray-100 text-xs font-bold text-gray-600 uppercase tracking-wider">
                          <th className="px-6 py-4">Patient Name</th>
                          <th className="px-6 py-4">UHID</th>
                          <th className="px-6 py-4">Ward / Bed</th>
                          <th className="px-6 py-4">Admission Date</th>
                          <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {filteredPatients.length === 0 && (
                          <tr>
                            <td colSpan={5} className="px-6 py-10 text-center text-gray-400 font-medium bg-gray-50/20 border-b border-gray-100">
                              No active patients found.
                            </td>
                          </tr>
                        )}
                        {filteredPatients.map(p => (
                          <tr key={p.id} className="hover:bg-gray-50/50 transition-colors">
                            <td className="px-6 py-4.5 font-semibold text-gray-900">{p.patientName}</td>
                            <td className="px-6 py-4.5 text-gray-500 font-medium">{p.uhid || '—'}</td>
                            <td className="px-6 py-4.5 text-gray-600 font-medium">
                              <span className="inline-flex items-center px-2 py-0.5 rounded bg-gray-100 text-gray-700 text-xs font-semibold">
                                {p.wardName} · Bed {p.bedNumber}
                              </span>
                            </td>
                            <td className="px-6 py-4.5 text-gray-500">
                              {p.admissionDate ? new Date(p.admissionDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'}
                            </td>
                            <td className="px-6 py-4.5 text-right">
                              <button
                                onClick={() => setSelectedAdmission(p)}
                                className="px-4 py-2 bg-blue-50 hover:bg-blue-100 text-blue-700 font-semibold text-xs rounded-xl shadow-sm transition-all active:scale-95 cursor-pointer"
                              >
                                View Record
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Tasks Tab View */}
              {!tasksLoading && tab === 'tasks' && (
                <div className="space-y-4">
                  {/* Shift Handover Summary Card */}
                  <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                    <div className="flex items-center justify-between mb-4">
                      <div>
                        <h3 className="text-base font-bold text-gray-900">Shift Handover</h3>
                        <p className="text-xs text-gray-500 mt-0.5">{shiftWindow?.label || 'Loading shift…'}</p>
                      </div>
                      {shiftMode === 'MANUAL' && (
                        <div className="flex items-center gap-2">
                          <label className="text-xs text-gray-500 shrink-0">My shift started at</label>
                          <input
                            type="time"
                            value={manualShiftStart}
                            onChange={e => setManualShiftStart(e.target.value)}
                            className="border border-gray-200 rounded-lg px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                          />
                        </div>
                      )}
                    </div>
                    <div className="grid grid-cols-2 gap-6">
                      {/* Pending Now */}
                      <div>
                        <p className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-3">Pending Now</p>
                        <div className="space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-sm text-gray-600">Overdue</span>
                            <span className={`text-sm font-bold ${overdueTaskList.length > 0 ? 'text-red-600' : 'text-gray-400'}`}>
                              {overdueTaskList.length}
                            </span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-sm text-gray-600">Due soon</span>
                            <span className={`text-sm font-bold ${dueSoonTaskList.length > 0 ? 'text-amber-600' : 'text-gray-400'}`}>
                              {dueSoonTaskList.length}
                            </span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-sm text-gray-600">Upcoming</span>
                            <span className="text-sm font-bold text-gray-500">{upcomingTaskList.length}</span>
                          </div>
                        </div>
                      </div>
                      {/* This Shift's Activity */}
                      <div>
                        <p className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-3">This Shift</p>
                        {shiftActivityLoading ? (
                          <div className="space-y-2">
                            <div className="h-5 bg-gray-100 rounded animate-pulse" />
                            <div className="h-5 bg-gray-100 rounded animate-pulse" />
                          </div>
                        ) : (
                          <div className="space-y-2">
                            <div className="flex items-center justify-between">
                              <span className="text-sm text-gray-600">Tasks completed</span>
                              <span className="text-sm font-bold text-gray-800">
                                {shiftActivity != null ? shiftActivity.completedTaskCount : '—'}
                              </span>
                            </div>
                            <div className="flex items-center justify-between">
                              <span className="text-sm text-gray-600">New admissions</span>
                              <span className="text-sm font-bold text-gray-800">
                                {shiftActivity != null ? shiftActivity.newAdmissionCount : '—'}
                              </span>
                            </div>
                            {shiftActivity == null && (
                              <p className="text-xs text-gray-400 mt-1">Data unavailable</p>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {tasks.length > 0 && (
                    <div className="flex flex-wrap gap-2 mb-2">
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
                    <div className="text-center py-16 bg-white rounded-2xl border border-gray-200 shadow-sm">
                      <span className="text-4xl">🎉</span>
                      <h3 className="text-base font-semibold text-gray-700 mt-3">No pending tasks!</h3>
                      <p className="text-sm text-gray-400 mt-1">All patient assessments and administrations are up to date.</p>
                    </div>
                  )}

                  {sortedTasks.map(task => {
                    const bucket = getTaskBucket(task);
                    const { label: timeLabel, cls: timeLabelCls } = getTimeLabel(task);
                    const cardCls = bucket === 'overdue'
                      ? 'border-l-4 border-red-500 bg-red-50/30 border border-red-100 shadow-sm'
                      : bucket === 'dueSoon'
                      ? 'border-l-4 border-amber-500 bg-amber-50/30 border border-amber-100 shadow-sm'
                      : 'border-l-4 border-neutral-300 bg-white border border-gray-200 shadow-sm';

                    return (
                      <div key={task.id} className={`flex items-center justify-between p-5 rounded-2xl transition-all ${cardCls}`}>
                        <div className="flex-1 min-w-0 pr-4">
                          <span className={`${timeLabelCls} inline-block mb-1`}>{timeLabel}</span>
                          <h4 className="font-bold text-gray-900 text-base leading-snug">{task.orderDescription}</h4>
                          <p className="text-sm text-gray-500 mt-1 font-medium">
                            <span className="inline-flex items-center px-2 py-0.5 rounded bg-gray-100 text-gray-700 text-xs font-bold mr-2">
                              {task.orderType}
                            </span>
                            {task.patientName && <span className="text-gray-700 font-semibold">{task.patientName}</span>}
                            {task.bedNumber ? ` · Bed ${task.bedNumber}` : task.wardName ? ` · ${task.wardName}` : ''}
                          </p>
                        </div>
                        <div className="shrink-0">
                          {task.orderType === 'MEDICATION' ? (
                            <div>
                              {notGivenOpenId === task.id ? (
                                <div className="flex flex-col gap-2.5 min-w-[240px] bg-white border border-gray-200 shadow-xl p-4 rounded-xl">
                                  <p className="text-xs font-bold text-gray-700">Record Non-Administration</p>
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
                                        className={`flex-1 py-1 rounded-lg text-xs font-semibold border transition-all ${
                                          notGivenStatus === opt.key
                                            ? 'bg-gray-900 text-white border-gray-900 shadow-sm'
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
                                    placeholder="Enter reason (required)"
                                    className="text-xs border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                  />
                                  {errorId === task.id && (
                                    <p className="text-xs text-red-600 font-medium">⚠️ Failed to record. Try again.</p>
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
                                      className="flex-1 px-3 py-1.5 text-xs font-bold bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-all"
                                    >
                                      {submittingId === task.id ? 'Saving...' : 'Confirm'}
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
                                      className="px-3.5 py-2 text-xs font-bold bg-green-600 hover:bg-green-700 text-white rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50 cursor-pointer"
                                    >
                                      {submittingId === task.id ? '…' : '✓ Given'}
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => handleSnooze(task)}
                                      disabled={submittingId === task.id}
                                      className="px-3.5 py-2 text-xs font-bold bg-amber-500 hover:bg-amber-600 text-white rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50 cursor-pointer"
                                    >
                                      ⏰ Snooze
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => { setNotGivenOpenId(task.id); setNotGivenReason(''); setNotGivenStatus('HELD'); }}
                                      disabled={submittingId === task.id}
                                      className="px-3.5 py-2 text-xs font-semibold bg-white hover:bg-gray-50 text-gray-700 border border-gray-355 rounded-xl shadow-sm transition-all active:scale-95 disabled:opacity-50 cursor-pointer"
                                    >
                                      ✗ Not Given
                                    </button>
                                  </div>
                                  {errorId === task.id && (
                                    <p className="text-xs text-red-600">⚠️ Failed to record. Try again.</p>
                                  )}
                                </div>
                              )}
                            </div>
                          ) : (
                            <button
                              onClick={() => setAdministerTarget(task)}
                              className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-sm transition-all active:scale-95 cursor-pointer"
                            >
                              📋 Execute Task
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </main>
      </div>

      {/* Administer medication modal */}
      {administerTarget && (
        <MedicationAdministrationModal
          task={administerTarget}
          onClose={() => setAdministerTarget(null)}
          onSave={async (payload) => {
            try {
              await nurseService.executeTask(administerTarget.ipdAdmissionId, administerTarget.id, payload);
              success('Task executed successfully');
              setAdministerTarget(null);
              loadTasks();
            } catch (err) {
              toastError('Failed to execute task');
            }
          }}
        />
      )}

      {/* Profile Settings Modal */}
      <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />
    </div>
  );
}
