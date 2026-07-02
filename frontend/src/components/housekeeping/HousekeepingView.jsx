import React, { useState, useEffect, useCallback } from 'react';
import housekeepingService from '../../services/housekeepingService';
import { useToast } from '../../context/ToastContext';

const TABS = ['Cleaning Tasks', 'Biomedical Waste', 'Facility Complaints'];
const TASK_TYPES = ['ROUTINE', 'DEEP', 'TERMINAL', 'EMERGENCY'];
const WASTE_TYPES = ['YELLOW', 'RED', 'BLUE', 'WHITE', 'GENERAL'];
const COMPLAINT_TYPES = ['LEAKAGE', 'LIGHTING', 'ELECTRICAL', 'AC', 'PLUMBING'];

const TASK_BADGE = {
  DIRTY: 'bg-gray-200 text-gray-700',
  IN_PROGRESS: 'bg-blue-100 text-blue-700',
  PENDING_VERIFICATION: 'bg-amber-100 text-amber-700',
  COMPLETED: 'bg-green-100 text-green-700',
};

const WASTE_COLOR = {
  YELLOW: 'bg-yellow-100 text-yellow-800',
  RED: 'bg-red-100 text-red-700',
  BLUE: 'bg-blue-100 text-blue-700',
  WHITE: 'bg-gray-100 text-gray-700',
  GENERAL: 'bg-gray-100 text-gray-500',
};

export default function HousekeepingView() {
  const { success, error: toastError } = useToast();
  const [tab, setTab] = useState('Cleaning Tasks');

  const [tasks, setTasks] = useState([]);
  const [waste, setWaste] = useState([]);
  const [complaints, setComplaints] = useState([]);

  const [taskForm, setTaskForm] = useState({ location: '', taskType: 'ROUTINE', priority: 'ROUTINE', assignedTo: '' });
  const [sigDraft, setSigDraft] = useState({});
  const [wasteForm, setWasteForm] = useState({ wasteType: 'YELLOW', quantity: '', barcodeTag: '', vendor: '', manifestNumber: '' });
  const [complaintForm, setComplaintForm] = useState({ location: '', complaintType: 'ELECTRICAL' });
  const [resolutionDraft, setResolutionDraft] = useState({});

  const load = useCallback(async () => {
    try {
      const [t, w, c] = await Promise.all([
        housekeepingService.getTasks(),
        housekeepingService.getWasteLog(),
        housekeepingService.getComplaints(),
      ]);
      setTasks(Array.isArray(t?.data) ? t.data : (Array.isArray(t) ? t : []));
      setWaste(Array.isArray(w?.data) ? w.data : (Array.isArray(w) ? w : []));
      setComplaints(Array.isArray(c?.data) ? c.data : (Array.isArray(c) ? c : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitTask = async (e) => {
    e.preventDefault();
    try {
      await housekeepingService.createTask(taskForm);
      success('Cleaning task created');
      setTaskForm({ location: '', taskType: 'ROUTINE', priority: 'ROUTINE', assignedTo: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to create task')); }
  };

  const completeTask = async (id) => {
    try {
      await housekeepingService.completeTask(id);
      success('Task marked complete — awaiting supervisor verification');
      load();
    } catch (ex) { toastError(err(ex, 'Failed to complete task')); }
  };

  const verifyTask = async (id) => {
    const sig = sigDraft[id];
    if (!sig || !sig.trim()) return toastError('Enter a supervisor signature note');
    try {
      await housekeepingService.verifyTask(id, sig);
      success('Task verified — location released');
      setSigDraft(p => ({ ...p, [id]: '' }));
      load();
    } catch (ex) { toastError(err(ex, 'Verification failed')); }
  };

  const submitWaste = async (e) => {
    e.preventDefault();
    try {
      await housekeepingService.logWaste({ ...wasteForm, quantity: Number(wasteForm.quantity) });
      success('Waste collection logged');
      setWasteForm({ wasteType: 'YELLOW', quantity: '', barcodeTag: '', vendor: '', manifestNumber: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to log waste')); }
  };

  const submitComplaint = async (e) => {
    e.preventDefault();
    try {
      await housekeepingService.openComplaint(complaintForm);
      success('Facility complaint filed');
      setComplaintForm({ location: '', complaintType: 'ELECTRICAL' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to file complaint')); }
  };

  const confirm = async (id, role) => {
    const resolution = role === 'ENGINEER' ? resolutionDraft[id] : undefined;
    try {
      await housekeepingService.confirmComplaint(id, role, resolution);
      success(`${role === 'ENGINEER' ? 'Engineer' : 'Nurse'} confirmation recorded`);
      load();
    } catch (ex) { toastError(err(ex, 'Confirmation failed')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-cyan-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Housekeeping & Facility Management</h2>
        <p className="text-sm text-gray-600 mt-1">Bed/room turnover tasks, biomedical waste logging, and facility complaints — beds only release after supervisor-verified cleaning.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === t ? 'border-cyan-600 text-cyan-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Cleaning Tasks' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitTask} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Create Cleaning Task</h3>
            <input required placeholder="Location (e.g. BED-201, OT-2)" className={InputCls} value={taskForm.location} onChange={e => setTaskForm(p => ({ ...p, location: e.target.value }))} />
            <select className={InputCls} value={taskForm.taskType} onChange={e => setTaskForm(p => ({ ...p, taskType: e.target.value }))}>
              {TASK_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
            <select className={InputCls} value={taskForm.priority} onChange={e => setTaskForm(p => ({ ...p, priority: e.target.value }))}>
              <option value="ROUTINE">ROUTINE</option>
              <option value="URGENT">URGENT</option>
            </select>
            <input placeholder="Assigned Housekeeper (optional)" className={InputCls} value={taskForm.assignedTo} onChange={e => setTaskForm(p => ({ ...p, assignedTo: e.target.value }))} />
            <button className="w-full bg-cyan-600 hover:bg-cyan-700 text-white text-sm font-semibold py-2 rounded-lg">Create Task</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Tasks ({tasks.length})</h3>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {tasks.map(t => (
                <div key={t.id} className="border border-gray-100 rounded-lg p-3 text-xs space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="font-semibold">{t.location} — {t.taskType}</span>
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${TASK_BADGE[t.status] || 'bg-gray-100 text-gray-600'}`}>{t.status}</span>
                  </div>
                  {t.status === 'DIRTY' && (
                    <button onClick={() => completeTask(t.id)} className="bg-blue-600 hover:bg-blue-700 text-white font-semibold px-3 py-1 rounded-lg">Mark Complete</button>
                  )}
                  {t.status === 'PENDING_VERIFICATION' && (
                    <div className="flex gap-2">
                      <input
                        placeholder="Supervisor signature note"
                        className="border border-gray-300 rounded-lg px-2 py-1 text-xs flex-1"
                        value={sigDraft[t.id] || ''}
                        onChange={e => setSigDraft(p => ({ ...p, [t.id]: e.target.value }))}
                      />
                      <button onClick={() => verifyTask(t.id)} className="bg-green-600 hover:bg-green-700 text-white font-semibold px-3 py-1 rounded-lg whitespace-nowrap">Verify & Release</button>
                    </div>
                  )}
                </div>
              ))}
              {tasks.length === 0 && <p className="text-xs text-gray-400 italic">No tasks yet.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Biomedical Waste' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitWaste} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Log Waste Collection</h3>
            <select className={InputCls} value={wasteForm.wasteType} onChange={e => setWasteForm(p => ({ ...p, wasteType: e.target.value }))}>
              {WASTE_TYPES.map(w => <option key={w} value={w}>{w}</option>)}
            </select>
            <input required type="number" step="0.01" placeholder="Weight (Kg)" className={InputCls} value={wasteForm.quantity} onChange={e => setWasteForm(p => ({ ...p, quantity: e.target.value }))} />
            <input required placeholder="Barcode Tag" className={InputCls} value={wasteForm.barcodeTag} onChange={e => setWasteForm(p => ({ ...p, barcodeTag: e.target.value }))} />
            <input required placeholder="Disposal Vendor" className={InputCls} value={wasteForm.vendor} onChange={e => setWasteForm(p => ({ ...p, vendor: e.target.value }))} />
            <input placeholder="Manifest Number (optional)" className={InputCls} value={wasteForm.manifestNumber} onChange={e => setWasteForm(p => ({ ...p, manifestNumber: e.target.value }))} />
            <button className="w-full bg-cyan-600 hover:bg-cyan-700 text-white text-sm font-semibold py-2 rounded-lg">Log Collection</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Waste Log ({waste.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {waste.map(w => (
                <div key={w.id} className="flex justify-between items-center text-xs border-b border-gray-100 py-1.5">
                  <span>{w.barcodeTag} — {w.vendor}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400">{w.quantity} kg</span>
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${WASTE_COLOR[w.wasteType] || 'bg-gray-100 text-gray-600'}`}>{w.wasteType}</span>
                  </div>
                </div>
              ))}
              {waste.length === 0 && <p className="text-xs text-gray-400 italic">No waste logged.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Facility Complaints' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitComplaint} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">File Facility Complaint</h3>
            <input required placeholder="Location (e.g. WARD-B-AC-02)" className={InputCls} value={complaintForm.location} onChange={e => setComplaintForm(p => ({ ...p, location: e.target.value }))} />
            <select className={InputCls} value={complaintForm.complaintType} onChange={e => setComplaintForm(p => ({ ...p, complaintType: e.target.value }))}>
              {COMPLAINT_TYPES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <button className="w-full bg-cyan-600 hover:bg-cyan-700 text-white text-sm font-semibold py-2 rounded-lg">File Complaint</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Complaints ({complaints.length})</h3>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {complaints.map(c => (
                <div key={c.id} className="border border-gray-100 rounded-lg p-3 text-xs space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="font-semibold">{c.location} — {c.complaintType}</span>
                    <span className="px-2 py-0.5 rounded-full font-semibold bg-gray-100 text-gray-600">{c.status}</span>
                  </div>
                  {c.status !== 'CLOSED' && (
                    <div className="flex flex-wrap gap-2 items-center">
                      {!c.engineerConfirmed && (
                        <>
                          <input
                            placeholder="Resolution note"
                            className="border border-gray-300 rounded-lg px-2 py-1 text-xs"
                            value={resolutionDraft[c.id] || ''}
                            onChange={e => setResolutionDraft(p => ({ ...p, [c.id]: e.target.value }))}
                          />
                          <button onClick={() => confirm(c.id, 'ENGINEER')} className="bg-blue-600 hover:bg-blue-700 text-white font-semibold px-3 py-1 rounded-lg">Engineer Confirm</button>
                        </>
                      )}
                      {!c.nurseConfirmed && (
                        <button onClick={() => confirm(c.id, 'NURSE')} className="bg-green-600 hover:bg-green-700 text-white font-semibold px-3 py-1 rounded-lg">Nurse Confirm</button>
                      )}
                    </div>
                  )}
                  {c.resolution && <p className="text-gray-500 italic">Fix: {c.resolution}</p>}
                </div>
              ))}
              {complaints.length === 0 && <p className="text-xs text-gray-400 italic">No complaints filed.</p>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
