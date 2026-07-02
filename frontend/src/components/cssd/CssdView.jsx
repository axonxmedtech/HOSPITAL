import React, { useState, useEffect, useCallback } from 'react';
import cssdService from '../../services/cssdService';
import { useToast } from '../../context/ToastContext';

const TABS = ['Trays', 'Return', 'Sterilize', 'Issue'];
const METHODS = ['STEAM', 'ETO', 'PLASMA', 'DRY_HEAT'];

const TRAY_BADGE = {
  DIRTY: 'bg-gray-200 text-gray-700',
  IN_STERILIZER: 'bg-blue-100 text-blue-700',
  STERILE: 'bg-green-100 text-green-700',
  ISSUED: 'bg-indigo-100 text-indigo-700',
  QUARANTINED: 'bg-red-100 text-red-700',
};

export default function CssdView() {
  const { success, error: toastError } = useToast();
  const [tab, setTab] = useState('Trays');

  const [trays, setTrays] = useState([]);
  const [cycles, setCycles] = useState([]);
  const [issues, setIssues] = useState([]);

  const [trayForm, setTrayForm] = useState({ trayName: '', specialty: '', barcode: '' });
  const [returnForm, setReturnForm] = useState({ trayBarcode: '', fromDepartment: '', condition: 'DIRTY' });
  const [cycleForm, setCycleForm] = useState({ machineId: '', method: 'STEAM', temperature: '', pressure: '', duration: '', trayIds: [] });
  const [verifyForm, setVerifyForm] = useState({ cycleId: '', chemicalResult: 'PASS', biologicalResult: 'PASS', approvedBySig: '' });
  const [issueForm, setIssueForm] = useState({ trayBarcode: '', issuedToDepartment: '', receivedBy: '' });

  const load = useCallback(async () => {
    try {
      const [t, c, i] = await Promise.all([
        cssdService.getTrays(),
        cssdService.getCycles(),
        cssdService.getIssues(),
      ]);
      setTrays(Array.isArray(t?.data) ? t.data : (Array.isArray(t) ? t : []));
      setCycles(Array.isArray(c?.data) ? c.data : (Array.isArray(c) ? c : []));
      setIssues(Array.isArray(i?.data) ? i.data : (Array.isArray(i) ? i : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitTray = async (e) => {
    e.preventDefault();
    try {
      await cssdService.registerTray(trayForm);
      success('Tray registered');
      setTrayForm({ trayName: '', specialty: '', barcode: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to register tray')); }
  };

  const submitReturn = async (e) => {
    e.preventDefault();
    try {
      await cssdService.returnTray(returnForm);
      success('Tray return logged');
      setReturnForm({ trayBarcode: '', fromDepartment: '', condition: 'DIRTY' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to log return')); }
  };

  const toggleCycleTray = (id) => {
    setCycleForm(p => ({
      ...p,
      trayIds: p.trayIds.includes(id) ? p.trayIds.filter(x => x !== id) : [...p.trayIds, id],
    }));
  };

  const submitCycle = async (e) => {
    e.preventDefault();
    try {
      await cssdService.startCycle({
        ...cycleForm,
        temperature: Number(cycleForm.temperature),
        pressure: Number(cycleForm.pressure),
        duration: Number(cycleForm.duration),
      });
      success('Sterilization cycle started');
      setCycleForm({ machineId: '', method: 'STEAM', temperature: '', pressure: '', duration: '', trayIds: [] });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to start cycle')); }
  };

  const submitVerify = async (e) => {
    e.preventDefault();
    if (!verifyForm.cycleId) return toastError('Select a cycle to verify');
    try {
      await cssdService.verifyCycle(Number(verifyForm.cycleId), {
        chemicalResult: verifyForm.chemicalResult,
        biologicalResult: verifyForm.biologicalResult,
        approvedBySig: verifyForm.approvedBySig,
      });
      success('Cycle verified');
      setVerifyForm({ cycleId: '', chemicalResult: 'PASS', biologicalResult: 'PASS', approvedBySig: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Verification failed')); }
  };

  const submitIssue = async (e) => {
    e.preventDefault();
    try {
      await cssdService.issueTray({ ...issueForm, receivedBy: issueForm.receivedBy ? Number(issueForm.receivedBy) : null });
      success('Tray issued');
      setIssueForm({ trayBarcode: '', issuedToDepartment: '', receivedBy: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Issue blocked')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-teal-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">CSSD — Sterile Instrument Lifecycle</h2>
        <p className="text-sm text-gray-600 mt-1">Return, autoclave, supervisor-verify, and issue sterile trays — with automatic quarantine on failed indicators and expiry gating on checkout.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === t ? 'border-teal-600 text-teal-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Trays' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitTray} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Register Tray</h3>
            <input required placeholder="Tray Name (e.g. Major Laparotomy Set)" className={InputCls} value={trayForm.trayName} onChange={e => setTrayForm(p => ({ ...p, trayName: e.target.value }))} />
            <input placeholder="Specialty (e.g. Ortho, General)" className={InputCls} value={trayForm.specialty} onChange={e => setTrayForm(p => ({ ...p, specialty: e.target.value }))} />
            <input required placeholder="Barcode" className={InputCls} value={trayForm.barcode} onChange={e => setTrayForm(p => ({ ...p, barcode: e.target.value }))} />
            <button className="w-full bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold py-2 rounded-lg">Register</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Trays ({trays.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {trays.map(t => (
                <div key={t.id} className="flex justify-between items-center text-xs border-b border-gray-100 py-1.5">
                  <span>{t.barcode} — {t.trayName} {t.specialty ? `(${t.specialty})` : ''}</span>
                  <div className="flex items-center gap-2">
                    {t.expiryDate && <span className="text-gray-400">exp {t.expiryDate}</span>}
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${TRAY_BADGE[t.status] || 'bg-gray-100 text-gray-600'}`}>{t.status}</span>
                  </div>
                </div>
              ))}
              {trays.length === 0 && <p className="text-xs text-gray-400 italic">No trays registered.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Return' && (
        <form onSubmit={submitReturn} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3 max-w-lg">
          <h3 className="font-semibold text-gray-800 text-sm">Log Dirty Return</h3>
          <input required placeholder="Tray Barcode" className={InputCls} value={returnForm.trayBarcode} onChange={e => setReturnForm(p => ({ ...p, trayBarcode: e.target.value }))} />
          <input required placeholder="From Department (e.g. OT-1)" className={InputCls} value={returnForm.fromDepartment} onChange={e => setReturnForm(p => ({ ...p, fromDepartment: e.target.value }))} />
          <select className={InputCls} value={returnForm.condition} onChange={e => setReturnForm(p => ({ ...p, condition: e.target.value }))}>
            <option value="DIRTY">DIRTY</option>
            <option value="DAMAGED">DAMAGED</option>
            <option value="MISSING">MISSING</option>
          </select>
          <button className="w-full bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold py-2 rounded-lg">Log Return</button>
        </form>
      )}

      {tab === 'Sterilize' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <form onSubmit={submitCycle} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Start Autoclave Cycle</h3>
            <input required placeholder="Machine ID (e.g. STER-01)" className={InputCls} value={cycleForm.machineId} onChange={e => setCycleForm(p => ({ ...p, machineId: e.target.value }))} />
            <select className={InputCls} value={cycleForm.method} onChange={e => setCycleForm(p => ({ ...p, method: e.target.value }))}>
              {METHODS.map(m => <option key={m} value={m}>{m}</option>)}
            </select>
            <div className="grid grid-cols-3 gap-2">
              <input required type="number" step="0.1" placeholder="Temp °C" className={InputCls} value={cycleForm.temperature} onChange={e => setCycleForm(p => ({ ...p, temperature: e.target.value }))} />
              <input required type="number" step="0.01" placeholder="Pressure" className={InputCls} value={cycleForm.pressure} onChange={e => setCycleForm(p => ({ ...p, pressure: e.target.value }))} />
              <input required type="number" placeholder="Mins" className={InputCls} value={cycleForm.duration} onChange={e => setCycleForm(p => ({ ...p, duration: e.target.value }))} />
            </div>
            <div>
              <p className="text-xs text-gray-500 mb-1">Select DIRTY trays to load:</p>
              <div className="max-h-32 overflow-y-auto space-y-1 border border-gray-100 rounded-lg p-2">
                {trays.filter(t => t.status === 'DIRTY').map(t => (
                  <label key={t.id} className="flex items-center gap-2 text-xs">
                    <input type="checkbox" checked={cycleForm.trayIds.includes(t.id)} onChange={() => toggleCycleTray(t.id)} />
                    {t.barcode} — {t.trayName}
                  </label>
                ))}
                {trays.filter(t => t.status === 'DIRTY').length === 0 && <p className="text-xs text-gray-400 italic">No dirty trays available.</p>}
              </div>
            </div>
            <button className="w-full bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold py-2 rounded-lg">Start Cycle</button>
          </form>

          <form onSubmit={submitVerify} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Verify & Release Load</h3>
            <select required className={InputCls} value={verifyForm.cycleId} onChange={e => setVerifyForm(p => ({ ...p, cycleId: e.target.value }))}>
              <option value="">Cycle…</option>
              {cycles.filter(c => c.status === 'IN_PROGRESS').map(c => (
                <option key={c.id} value={c.id}>{c.cycleNumber} ({c.machineId})</option>
              ))}
            </select>
            <div className="grid grid-cols-2 gap-2">
              <select className={InputCls} value={verifyForm.chemicalResult} onChange={e => setVerifyForm(p => ({ ...p, chemicalResult: e.target.value }))}>
                <option value="PASS">Chemical: PASS</option>
                <option value="FAIL">Chemical: FAIL</option>
              </select>
              <select className={InputCls} value={verifyForm.biologicalResult} onChange={e => setVerifyForm(p => ({ ...p, biologicalResult: e.target.value }))}>
                <option value="PASS">Biological: PASS</option>
                <option value="FAIL">Biological: FAIL</option>
              </select>
            </div>
            <input placeholder="Supervisor signature note" className={InputCls} value={verifyForm.approvedBySig} onChange={e => setVerifyForm(p => ({ ...p, approvedBySig: e.target.value }))} />
            <button className="w-full bg-green-600 hover:bg-green-700 text-white text-sm font-semibold py-2 rounded-lg">Approve & Release Load</button>
            <div className="text-xs text-gray-500 max-h-40 overflow-y-auto space-y-1 pt-2 border-t border-gray-100">
              {cycles.map(c => (
                <div key={c.id} className="flex justify-between">
                  <span>{c.cycleNumber}</span>
                  <span className="font-semibold">{c.status}</span>
                </div>
              ))}
            </div>
          </form>
        </div>
      )}

      {tab === 'Issue' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <form onSubmit={submitIssue} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Issue Sterile Tray (BR-1/BR-3 gate)</h3>
            <input required placeholder="Tray Barcode" className={InputCls} value={issueForm.trayBarcode} onChange={e => setIssueForm(p => ({ ...p, trayBarcode: e.target.value }))} />
            <input required placeholder="Issued To Department (e.g. OT-2)" className={InputCls} value={issueForm.issuedToDepartment} onChange={e => setIssueForm(p => ({ ...p, issuedToDepartment: e.target.value }))} />
            <input type="number" placeholder="Received By (user ID)" className={InputCls} value={issueForm.receivedBy} onChange={e => setIssueForm(p => ({ ...p, receivedBy: e.target.value }))} />
            <button className="w-full bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold py-2 rounded-lg">Issue Tray</button>
          </form>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Issue Log ({issues.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {issues.map(i => (
                <div key={i.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>Tray #{i.trayId} → {i.issuedToDepartment}</span>
                  <span className="text-gray-400">{i.issueTime}</span>
                </div>
              ))}
              {issues.length === 0 && <p className="text-xs text-gray-400 italic">No issues yet.</p>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
