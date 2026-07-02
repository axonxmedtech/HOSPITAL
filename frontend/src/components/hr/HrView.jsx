import React, { useState, useEffect, useCallback } from 'react';
import hrService from '../../services/hrService';
import { useToast } from '../../context/ToastContext';

const TABS = ['Employees', 'Roster', 'Leave', 'Payroll'];
const SHIFTS = ['MORNING', 'EVENING', 'NIGHT', 'ON_CALL'];

const STATUS_BADGE = {
  ACTIVE: 'bg-green-100 text-green-700',
  ON_LEAVE: 'bg-amber-100 text-amber-700',
  EXITED: 'bg-gray-200 text-gray-600',
};

export default function HrView() {
  const { success, error: toastError } = useToast();
  const [tab, setTab] = useState('Employees');

  const [employees, setEmployees] = useState([]);
  const [roster, setRoster] = useState([]);
  const [leaveRequests, setLeaveRequests] = useState([]);
  const [payroll, setPayroll] = useState([]);

  const [empForm, setEmpForm] = useState({ userId: '', department: '', designation: '', joiningDate: '', licenseNumber: '', licenseExpiry: '' });
  const [rosterForm, setRosterForm] = useState({ employeeId: '', department: '', shift: 'MORNING', date: '' });
  const [leaveForm, setLeaveForm] = useState({ leaveType: 'CASUAL', startDate: '', endDate: '' });
  const [payrollMonth, setPayrollMonth] = useState('');
  const [payrollEntries, setPayrollEntries] = useState({});

  const load = useCallback(async () => {
    try {
      const [e, r, l, p] = await Promise.all([
        hrService.getEmployees(),
        hrService.getRoster(),
        hrService.getLeaveRequests(),
        hrService.getPayroll(),
      ]);
      setEmployees(Array.isArray(e?.data) ? e.data : (Array.isArray(e) ? e : []));
      setRoster(Array.isArray(r?.data) ? r.data : (Array.isArray(r) ? r : []));
      setLeaveRequests(Array.isArray(l?.data) ? l.data : (Array.isArray(l) ? l : []));
      setPayroll(Array.isArray(p?.data) ? p.data : (Array.isArray(p) ? p : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitEmployee = async (e) => {
    e.preventDefault();
    try {
      await hrService.onboardEmployee({ ...empForm, userId: Number(empForm.userId), licenseExpiry: empForm.licenseExpiry || null });
      success('Employee onboarded');
      setEmpForm({ userId: '', department: '', designation: '', joiningDate: '', licenseNumber: '', licenseExpiry: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to onboard employee')); }
  };

  const exitEmployee = async (id) => {
    if (!window.confirm('Mark this employee as exited?')) return;
    try {
      await hrService.exitEmployee(id);
      success('Employee exited');
      load();
    } catch (ex) { toastError(err(ex, 'Failed to exit employee')); }
  };

  const submitRoster = async (e) => {
    e.preventDefault();
    try {
      await hrService.createRosterSlot({ ...rosterForm, employeeId: Number(rosterForm.employeeId) });
      success('Shift scheduled');
      setRosterForm({ employeeId: '', department: '', shift: 'MORNING', date: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Scheduling blocked')); }
  };

  const submitLeave = async (e) => {
    e.preventDefault();
    try {
      await hrService.requestLeave(leaveForm);
      success('Leave request submitted');
      setLeaveForm({ leaveType: 'CASUAL', startDate: '', endDate: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to submit leave')); }
  };

  const decideLeave = async (id, status) => {
    try {
      await hrService.approveLeave(id, status);
      success(`Leave ${status.toLowerCase()}`);
      load();
    } catch (ex) { toastError(err(ex, 'Failed to update leave')); }
  };

  const submitPayroll = async (e) => {
    e.preventDefault();
    if (!payrollMonth) return toastError('Enter a salary month (YYYY-MM)');
    const entries = Object.entries(payrollEntries)
      .filter(([, v]) => v?.gross)
      .map(([employeeId, v]) => ({
        employeeId: Number(employeeId),
        grossSalary: Number(v.gross),
        deductions: v.deductions ? Number(v.deductions) : 0,
      }));
    if (entries.length === 0) return toastError('Enter at least one employee gross salary');
    try {
      await hrService.processPayroll({ salaryMonth: payrollMonth, entries });
      success('Payroll processed');
      setPayrollEntries({});
      load();
    } catch (ex) { toastError(err(ex, 'Payroll processing failed')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Human Resources & Workforce</h2>
        <p className="text-sm text-gray-600 mt-1">Employee onboarding, shift rostering, leave, and payroll — with overlap and license-expiry scheduling gates.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === t ? 'border-purple-600 text-purple-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Employees' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitEmployee} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Onboard Employee</h3>
            <input required type="number" placeholder="Linked User ID" className={InputCls} value={empForm.userId} onChange={e => setEmpForm(p => ({ ...p, userId: e.target.value }))} />
            <input required placeholder="Department" className={InputCls} value={empForm.department} onChange={e => setEmpForm(p => ({ ...p, department: e.target.value }))} />
            <input required placeholder="Designation" className={InputCls} value={empForm.designation} onChange={e => setEmpForm(p => ({ ...p, designation: e.target.value }))} />
            <label className="block text-xs text-gray-500">Joining Date</label>
            <input required type="date" className={InputCls} value={empForm.joiningDate} onChange={e => setEmpForm(p => ({ ...p, joiningDate: e.target.value }))} />
            <input placeholder="Professional License No. (if clinician)" className={InputCls} value={empForm.licenseNumber} onChange={e => setEmpForm(p => ({ ...p, licenseNumber: e.target.value }))} />
            <label className="block text-xs text-gray-500">License Expiry</label>
            <input type="date" className={InputCls} value={empForm.licenseExpiry} onChange={e => setEmpForm(p => ({ ...p, licenseExpiry: e.target.value }))} />
            <button className="w-full bg-purple-600 hover:bg-purple-700 text-white text-sm font-semibold py-2 rounded-lg">Onboard</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Employees ({employees.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {employees.map(e => (
                <div key={e.id} className="flex justify-between items-center text-xs border-b border-gray-100 py-1.5">
                  <span>{e.employeeCode} — {e.designation} ({e.department}){e.licenseExpiry ? ` — lic exp ${e.licenseExpiry}` : ''}</span>
                  <div className="flex items-center gap-2">
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${STATUS_BADGE[e.status] || 'bg-gray-100 text-gray-600'}`}>{e.status}</span>
                    {e.status !== 'EXITED' && (
                      <button onClick={() => exitEmployee(e.id)} className="text-red-600 hover:text-red-700 font-semibold">Exit</button>
                    )}
                  </div>
                </div>
              ))}
              {employees.length === 0 && <p className="text-xs text-gray-400 italic">No employees onboarded.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Roster' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitRoster} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Schedule Shift</h3>
            <select required className={InputCls} value={rosterForm.employeeId} onChange={e => setRosterForm(p => ({ ...p, employeeId: e.target.value }))}>
              <option value="">Select employee…</option>
              {employees.filter(e => e.status === 'ACTIVE').map(e => (
                <option key={e.id} value={e.id}>{e.employeeCode} — {e.designation}</option>
              ))}
            </select>
            <input required placeholder="Department" className={InputCls} value={rosterForm.department} onChange={e => setRosterForm(p => ({ ...p, department: e.target.value }))} />
            <select className={InputCls} value={rosterForm.shift} onChange={e => setRosterForm(p => ({ ...p, shift: e.target.value }))}>
              {SHIFTS.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
            <label className="block text-xs text-gray-500">Date</label>
            <input required type="date" className={InputCls} value={rosterForm.date} onChange={e => setRosterForm(p => ({ ...p, date: e.target.value }))} />
            <button className="w-full bg-purple-600 hover:bg-purple-700 text-white text-sm font-semibold py-2 rounded-lg">Schedule</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Roster ({roster.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {roster.map(r => (
                <div key={r.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>Employee {r.employeeId} — {r.department} — {r.shift}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400">{r.date}</span>
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${r.status === 'ON_LEAVE' ? 'bg-amber-100 text-amber-700' : 'bg-blue-100 text-blue-700'}`}>{r.status}</span>
                  </div>
                </div>
              ))}
              {roster.length === 0 && <p className="text-xs text-gray-400 italic">No shifts scheduled.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Leave' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitLeave} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Request Leave (self-service)</h3>
            <select className={InputCls} value={leaveForm.leaveType} onChange={e => setLeaveForm(p => ({ ...p, leaveType: e.target.value }))}>
              <option value="CASUAL">CASUAL</option>
              <option value="SICK">SICK</option>
              <option value="EARNED">EARNED</option>
              <option value="MATERNITY">MATERNITY</option>
            </select>
            <label className="block text-xs text-gray-500">Start Date</label>
            <input required type="date" className={InputCls} value={leaveForm.startDate} onChange={e => setLeaveForm(p => ({ ...p, startDate: e.target.value }))} />
            <label className="block text-xs text-gray-500">End Date</label>
            <input required type="date" className={InputCls} value={leaveForm.endDate} onChange={e => setLeaveForm(p => ({ ...p, endDate: e.target.value }))} />
            <button className="w-full bg-purple-600 hover:bg-purple-700 text-white text-sm font-semibold py-2 rounded-lg">Submit Request</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Leave Requests ({leaveRequests.length})</h3>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {leaveRequests.map(l => (
                <div key={l.id} className="flex justify-between items-center text-xs border-b border-gray-100 py-1.5">
                  <span>Employee {l.employeeId} — {l.leaveType} ({l.startDate} → {l.endDate})</span>
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded-full font-semibold bg-gray-100 text-gray-600">{l.status}</span>
                    {l.status === 'PENDING' && (
                      <>
                        <button onClick={() => decideLeave(l.id, 'APPROVED')} className="text-green-600 hover:text-green-700 font-semibold">Approve</button>
                        <button onClick={() => decideLeave(l.id, 'REJECTED')} className="text-red-600 hover:text-red-700 font-semibold">Reject</button>
                      </>
                    )}
                  </div>
                </div>
              ))}
              {leaveRequests.length === 0 && <p className="text-xs text-gray-400 italic">No leave requests.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Payroll' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitPayroll} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3 lg:col-span-1">
            <h3 className="font-semibold text-gray-800 text-sm">Process Monthly Payroll</h3>
            <input required placeholder="Salary Month (YYYY-MM)" className={InputCls} value={payrollMonth} onChange={e => setPayrollMonth(e.target.value)} />
            <div className="max-h-64 overflow-y-auto space-y-2 border border-gray-100 rounded-lg p-2">
              {employees.filter(e => e.status !== 'EXITED').map(e => (
                <div key={e.id} className="grid grid-cols-3 gap-1 items-center text-xs">
                  <span className="truncate">{e.employeeCode}</span>
                  <input type="number" placeholder="Gross" className="border border-gray-300 rounded px-1 py-1"
                    value={payrollEntries[e.id]?.gross || ''}
                    onChange={ev => setPayrollEntries(p => ({ ...p, [e.id]: { ...p[e.id], gross: ev.target.value } }))} />
                  <input type="number" placeholder="Deductions" className="border border-gray-300 rounded px-1 py-1"
                    value={payrollEntries[e.id]?.deductions || ''}
                    onChange={ev => setPayrollEntries(p => ({ ...p, [e.id]: { ...p[e.id], deductions: ev.target.value } }))} />
                </div>
              ))}
              {employees.length === 0 && <p className="text-xs text-gray-400 italic">No employees to pay.</p>}
            </div>
            <button className="w-full bg-purple-600 hover:bg-purple-700 text-white text-sm font-semibold py-2 rounded-lg">Process Payroll</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Payroll Log ({payroll.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {payroll.map(p => (
                <div key={p.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>Employee {p.employeeId} — {p.salaryMonth}</span>
                  <span className="font-semibold">Net ₹{p.netSalary}</span>
                </div>
              ))}
              {payroll.length === 0 && <p className="text-xs text-gray-400 italic">No payroll processed.</p>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
