import React, { useState, useEffect, useCallback } from 'react';
import trainingService from '../../services/trainingService';
import hrService from '../../services/hrService';
import { useToast } from '../../context/ToastContext';

const TABS = ['Courses', 'Sessions', 'Attendance', 'Certifications'];
const CATEGORIES = ['INFECTION_CONTROL', 'FIRE_SAFETY', 'BMW', 'CPR', 'PATIENT_SAFETY', 'NABH', 'HAND_HYGIENE', 'MED_SAFETY', 'EMERGENCY_CODES', 'EQUIPMENT'];

const SESSION_BADGE = {
  PLANNED: 'bg-gray-200 text-gray-700',
  IN_PROGRESS: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700',
};

const CERT_BADGE = {
  VALID: 'bg-green-100 text-green-700',
  EXPIRING: 'bg-amber-100 text-amber-700',
  EXPIRED: 'bg-red-100 text-red-700',
  REVOKED: 'bg-gray-200 text-gray-600',
};

export default function TrainingView() {
  const { success, error: toastError } = useToast();
  const [tab, setTab] = useState('Courses');

  const [masters, setMasters] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [attendance, setAttendance] = useState([]);
  const [certifications, setCertifications] = useState([]);
  const [employees, setEmployees] = useState([]);

  const [masterForm, setMasterForm] = useState({ title: '', category: 'PATIENT_SAFETY', description: '', mandatory: false, validityPeriod: 0, targetRoles: '' });
  const [sessionForm, setSessionForm] = useState({ trainingMasterId: '', trainerId: '', sessionDate: '', startTime: '', endTime: '', venue: '' });
  const [attendanceForm, setAttendanceForm] = useState({ sessionId: '', employeeId: '', attendanceStatus: 'PRESENT', remarks: '' });

  const load = useCallback(async () => {
    try {
      const [m, s, a, c, e] = await Promise.all([
        trainingService.getMasters(),
        trainingService.getSessions(),
        trainingService.getAttendance(),
        trainingService.getCertifications(),
        hrService.getEmployees(),
      ]);
      setMasters(Array.isArray(m?.data) ? m.data : (Array.isArray(m) ? m : []));
      setSessions(Array.isArray(s?.data) ? s.data : (Array.isArray(s) ? s : []));
      setAttendance(Array.isArray(a?.data) ? a.data : (Array.isArray(a) ? a : []));
      setCertifications(Array.isArray(c?.data) ? c.data : (Array.isArray(c) ? c : []));
      setEmployees(Array.isArray(e?.data) ? e.data : (Array.isArray(e) ? e : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitMaster = async (e) => {
    e.preventDefault();
    try {
      await trainingService.createMaster({ ...masterForm, validityPeriod: Number(masterForm.validityPeriod) });
      success('Course created');
      setMasterForm({ title: '', category: 'PATIENT_SAFETY', description: '', mandatory: false, validityPeriod: 0, targetRoles: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to create course')); }
  };

  const submitSession = async (e) => {
    e.preventDefault();
    try {
      await trainingService.createSession({ ...sessionForm, trainingMasterId: Number(sessionForm.trainingMasterId), trainerId: Number(sessionForm.trainerId) });
      success('Session scheduled');
      setSessionForm({ trainingMasterId: '', trainerId: '', sessionDate: '', startTime: '', endTime: '', venue: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to schedule session')); }
  };

  const cancelSession = async (id) => {
    const reason = window.prompt('Reason for cancelling this session?');
    if (!reason) return;
    try {
      await trainingService.cancelSession(id, reason);
      success('Session cancelled');
      load();
    } catch (ex) { toastError(err(ex, 'Failed to cancel session')); }
  };

  const verifySession = async (id) => {
    try {
      await trainingService.verifySession(id);
      success('Session verified — attendees certified');
      load();
    } catch (ex) { toastError(err(ex, 'Verification failed')); }
  };

  const submitAttendance = async (e) => {
    e.preventDefault();
    try {
      await trainingService.markAttendance({ ...attendanceForm, sessionId: Number(attendanceForm.sessionId), employeeId: Number(attendanceForm.employeeId) });
      success('Attendance marked');
      setAttendanceForm({ sessionId: '', employeeId: '', attendanceStatus: 'PRESENT', remarks: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to mark attendance')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Training & Learning Management</h2>
        <p className="text-sm text-gray-600 mt-1">NABH competency evidence — courses, sessions, verified attendance, and expiry-tracked certifications.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === t ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Courses' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitMaster} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Create Course</h3>
            <input required placeholder="Title" className={InputCls} value={masterForm.title} onChange={e => setMasterForm(p => ({ ...p, title: e.target.value }))} />
            <select className={InputCls} value={masterForm.category} onChange={e => setMasterForm(p => ({ ...p, category: e.target.value }))}>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <textarea rows={2} placeholder="Description" className={InputCls} value={masterForm.description} onChange={e => setMasterForm(p => ({ ...p, description: e.target.value }))} />
            <label className="flex items-center gap-2 text-xs text-gray-600">
              <input type="checkbox" checked={masterForm.mandatory} onChange={e => setMasterForm(p => ({ ...p, mandatory: e.target.checked }))} />
              Mandatory course
            </label>
            <input type="number" min="0" placeholder="Validity (months, 0 = never expires)" className={InputCls} value={masterForm.validityPeriod} onChange={e => setMasterForm(p => ({ ...p, validityPeriod: e.target.value }))} />
            <input placeholder="Target roles (comma separated)" className={InputCls} value={masterForm.targetRoles} onChange={e => setMasterForm(p => ({ ...p, targetRoles: e.target.value }))} />
            <button className="w-full bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold py-2 rounded-lg">Create Course</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Courses ({masters.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {masters.map(m => (
                <div key={m.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>{m.title} — {m.category}{m.mandatory ? ' (mandatory)' : ''}</span>
                  <span className="text-gray-400">{m.validityPeriod > 0 ? `${m.validityPeriod}mo validity` : 'never expires'}</span>
                </div>
              ))}
              {masters.length === 0 && <p className="text-xs text-gray-400 italic">No courses created.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Sessions' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitSession} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Schedule Session</h3>
            <select required className={InputCls} value={sessionForm.trainingMasterId} onChange={e => setSessionForm(p => ({ ...p, trainingMasterId: e.target.value }))}>
              <option value="">Select course…</option>
              {masters.map(m => <option key={m.id} value={m.id}>{m.title}</option>)}
            </select>
            <input required type="number" placeholder="Trainer User ID" className={InputCls} value={sessionForm.trainerId} onChange={e => setSessionForm(p => ({ ...p, trainerId: e.target.value }))} />
            <label className="block text-xs text-gray-500">Session Date</label>
            <input required type="date" className={InputCls} value={sessionForm.sessionDate} onChange={e => setSessionForm(p => ({ ...p, sessionDate: e.target.value }))} />
            <div className="grid grid-cols-2 gap-2">
              <input required type="time" className={InputCls} value={sessionForm.startTime} onChange={e => setSessionForm(p => ({ ...p, startTime: e.target.value }))} />
              <input required type="time" className={InputCls} value={sessionForm.endTime} onChange={e => setSessionForm(p => ({ ...p, endTime: e.target.value }))} />
            </div>
            <input required placeholder="Venue" className={InputCls} value={sessionForm.venue} onChange={e => setSessionForm(p => ({ ...p, venue: e.target.value }))} />
            <button className="w-full bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold py-2 rounded-lg">Schedule</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Sessions ({sessions.length})</h3>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {sessions.map(s => (
                <div key={s.id} className="border border-gray-100 rounded-lg p-3 text-xs space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="font-semibold">Session #{s.id} — {s.venue} ({s.sessionDate})</span>
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${SESSION_BADGE[s.status] || 'bg-gray-100 text-gray-600'}`}>{s.status}</span>
                  </div>
                  {(s.status === 'PLANNED' || s.status === 'IN_PROGRESS') && (
                    <div className="flex gap-2">
                      <button onClick={() => verifySession(s.id)} className="bg-green-600 hover:bg-green-700 text-white font-semibold px-3 py-1 rounded-lg">Verify & Certify</button>
                      <button onClick={() => cancelSession(s.id)} className="bg-red-600 hover:bg-red-700 text-white font-semibold px-3 py-1 rounded-lg">Cancel</button>
                    </div>
                  )}
                </div>
              ))}
              {sessions.length === 0 && <p className="text-xs text-gray-400 italic">No sessions scheduled.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Attendance' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitAttendance} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Mark Attendance</h3>
            <select required className={InputCls} value={attendanceForm.sessionId} onChange={e => setAttendanceForm(p => ({ ...p, sessionId: e.target.value }))}>
              <option value="">Select session…</option>
              {sessions.map(s => <option key={s.id} value={s.id}>#{s.id} — {s.venue} ({s.sessionDate})</option>)}
            </select>
            <select required className={InputCls} value={attendanceForm.employeeId} onChange={e => setAttendanceForm(p => ({ ...p, employeeId: e.target.value }))}>
              <option value="">Select employee…</option>
              {employees.map(e => <option key={e.id} value={e.id}>{e.employeeCode} — {e.designation}</option>)}
            </select>
            <select className={InputCls} value={attendanceForm.attendanceStatus} onChange={e => setAttendanceForm(p => ({ ...p, attendanceStatus: e.target.value }))}>
              <option value="PRESENT">PRESENT</option>
              <option value="ABSENT">ABSENT</option>
              <option value="LATE">LATE</option>
            </select>
            <input placeholder="Remarks (required for post-hoc marks)" className={InputCls} value={attendanceForm.remarks} onChange={e => setAttendanceForm(p => ({ ...p, remarks: e.target.value }))} />
            <button className="w-full bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold py-2 rounded-lg">Mark</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Attendance ({attendance.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {attendance.map(a => (
                <div key={a.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>Session #{a.sessionId} — Employee {a.employeeId}</span>
                  <div className="flex items-center gap-2">
                    {a.verified && <span className="text-green-600">✓ verified</span>}
                    <span className="px-2 py-0.5 rounded-full font-semibold bg-gray-100 text-gray-600">{a.attendanceStatus}</span>
                  </div>
                </div>
              ))}
              {attendance.length === 0 && <p className="text-xs text-gray-400 italic">No attendance recorded.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Certifications' && (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h3 className="font-semibold text-gray-800 text-sm mb-2">Certifications ({certifications.length})</h3>
          <div className="space-y-1 max-h-[32rem] overflow-y-auto">
            {certifications.map(c => (
              <div key={c.id} className="flex justify-between items-center text-xs border-b border-gray-100 py-1.5">
                <span>Employee {c.employeeId} — {c.certificateRef}</span>
                <div className="flex items-center gap-2">
                  <span className="text-gray-400">{c.expiresAt ? `exp ${c.expiresAt}` : 'no expiry'}</span>
                  <span className={`px-2 py-0.5 rounded-full font-semibold ${CERT_BADGE[c.status] || 'bg-gray-100 text-gray-600'}`}>{c.status}</span>
                </div>
              </div>
            ))}
            {certifications.length === 0 && <p className="text-xs text-gray-400 italic">No certifications issued.</p>}
          </div>
        </div>
      )}
    </div>
  );
}
