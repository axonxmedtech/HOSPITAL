import React, { useState, useEffect, useCallback } from 'react';
import biomedicalService from '../../services/biomedicalService';
import { useToast } from '../../context/ToastContext';

const TABS = ['Equipment', 'Breakdown Tickets', 'Calibration'];

const STATUS_BADGE = {
  ACTIVE: 'bg-green-100 text-green-700',
  DOWN: 'bg-red-100 text-red-700',
  CALIBRATION_OVERDUE: 'bg-amber-100 text-amber-700',
  RETIRED: 'bg-gray-200 text-gray-600',
};

const PRIORITY_BADGE = {
  LOW: 'bg-gray-100 text-gray-600',
  MEDIUM: 'bg-amber-100 text-amber-700',
  CRITICAL: 'bg-red-100 text-red-700',
};

export default function BiomedicalView() {
  const { success, error: toastError } = useToast();
  const [tab, setTab] = useState('Equipment');

  const [equipment, setEquipment] = useState([]);
  const [tickets, setTickets] = useState([]);
  const [calibrations, setCalibrations] = useState([]);

  const [eqForm, setEqForm] = useState({ equipmentName: '', category: '', manufacturer: '', model: '', serialNumber: '', department: '', location: '', warrantyExpiry: '' });
  const [ticketForm, setTicketForm] = useState({ equipmentId: '', priority: 'MEDIUM', remarks: '' });
  const [calForm, setCalForm] = useState({ equipmentId: '', calibrationDate: '', dueDate: '', agency: '', certificateReference: '', result: 'PASS' });

  const load = useCallback(async () => {
    try {
      const [e, t, c] = await Promise.all([
        biomedicalService.getEquipment(),
        biomedicalService.getTickets(),
        biomedicalService.getCalibrations(),
      ]);
      setEquipment(Array.isArray(e?.data) ? e.data : (Array.isArray(e) ? e : []));
      setTickets(Array.isArray(t?.data) ? t.data : (Array.isArray(t) ? t : []));
      setCalibrations(Array.isArray(c?.data) ? c.data : (Array.isArray(c) ? c : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitEquipment = async (e) => {
    e.preventDefault();
    try {
      const payload = { ...eqForm, warrantyExpiry: eqForm.warrantyExpiry || null };
      await biomedicalService.registerEquipment(payload);
      success('Equipment registered');
      setEqForm({ equipmentName: '', category: '', manufacturer: '', model: '', serialNumber: '', department: '', location: '', warrantyExpiry: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to register equipment')); }
  };

  const submitTicket = async (e) => {
    e.preventDefault();
    try {
      await biomedicalService.openBreakdownTicket({ ...ticketForm, equipmentId: Number(ticketForm.equipmentId) });
      success('Breakdown ticket opened');
      setTicketForm({ equipmentId: '', priority: 'MEDIUM', remarks: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to open ticket')); }
  };

  const submitCalibration = async (e) => {
    e.preventDefault();
    try {
      await biomedicalService.recordCalibration({ ...calForm, equipmentId: Number(calForm.equipmentId) });
      success('Calibration recorded');
      setCalForm({ equipmentId: '', calibrationDate: '', dueDate: '', agency: '', certificateReference: '', result: 'PASS' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to record calibration')); }
  };

  const closeTicket = async (id) => {
    if (!window.confirm('Confirm the device was tested and repair verified?')) return;
    try {
      await biomedicalService.closeTicket(id, true);
      success('Ticket closed — device restored to active');
      load();
    } catch (ex) { toastError(err(ex, 'Failed to close ticket')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Biomedical Engineering (BEMS)</h2>
        <p className="text-sm text-gray-600 mt-1">Asset registry, breakdown tickets, and calibration certification — devices are automatically locked when calibration lapses or a fault is reported.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === t ? 'border-blue-600 text-blue-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Equipment' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitEquipment} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Register Asset</h3>
            <input required placeholder="Equipment Name (e.g. Ventilator V60)" className={InputCls} value={eqForm.equipmentName} onChange={e => setEqForm(p => ({ ...p, equipmentName: e.target.value }))} />
            <input required placeholder="Category (e.g. ICU, OT, Radiology)" className={InputCls} value={eqForm.category} onChange={e => setEqForm(p => ({ ...p, category: e.target.value }))} />
            <div className="grid grid-cols-2 gap-2">
              <input placeholder="Manufacturer" className={InputCls} value={eqForm.manufacturer} onChange={e => setEqForm(p => ({ ...p, manufacturer: e.target.value }))} />
              <input placeholder="Model" className={InputCls} value={eqForm.model} onChange={e => setEqForm(p => ({ ...p, model: e.target.value }))} />
            </div>
            <input required placeholder="Serial Number" className={InputCls} value={eqForm.serialNumber} onChange={e => setEqForm(p => ({ ...p, serialNumber: e.target.value }))} />
            <div className="grid grid-cols-2 gap-2">
              <input required placeholder="Department" className={InputCls} value={eqForm.department} onChange={e => setEqForm(p => ({ ...p, department: e.target.value }))} />
              <input placeholder="Location / Room" className={InputCls} value={eqForm.location} onChange={e => setEqForm(p => ({ ...p, location: e.target.value }))} />
            </div>
            <label className="block text-xs text-gray-500">Warranty Expiry</label>
            <input type="date" className={InputCls} value={eqForm.warrantyExpiry} onChange={e => setEqForm(p => ({ ...p, warrantyExpiry: e.target.value }))} />
            <button className="w-full bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold py-2 rounded-lg">Register</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Assets ({equipment.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {equipment.map(e => (
                <div key={e.id} className="flex justify-between items-center text-xs border-b border-gray-100 py-1.5">
                  <span>{e.assetCode} — {e.equipmentName} ({e.department})</span>
                  <span className={`px-2 py-0.5 rounded-full font-semibold ${STATUS_BADGE[e.status] || 'bg-gray-100 text-gray-600'}`}>{e.status}</span>
                </div>
              ))}
              {equipment.length === 0 && <p className="text-xs text-gray-400 italic">No equipment registered.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Breakdown Tickets' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitTicket} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Report Breakdown</h3>
            <select required className={InputCls} value={ticketForm.equipmentId} onChange={e => setTicketForm(p => ({ ...p, equipmentId: e.target.value }))}>
              <option value="">Select asset…</option>
              {equipment.filter(e => e.status !== 'RETIRED').map(e => (
                <option key={e.id} value={e.id}>{e.assetCode} — {e.equipmentName}</option>
              ))}
            </select>
            <select className={InputCls} value={ticketForm.priority} onChange={e => setTicketForm(p => ({ ...p, priority: e.target.value }))}>
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="CRITICAL">CRITICAL</option>
            </select>
            <textarea required rows={3} placeholder="Problem description" className={InputCls} value={ticketForm.remarks} onChange={e => setTicketForm(p => ({ ...p, remarks: e.target.value }))} />
            <button className="w-full bg-red-600 hover:bg-red-700 text-white text-sm font-semibold py-2 rounded-lg">File Ticket</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Tickets ({tickets.length})</h3>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {tickets.map(t => (
                <div key={t.id} className="border border-gray-100 rounded-lg p-3 text-xs space-y-1">
                  <div className="flex justify-between items-center">
                    <span className="font-semibold">#{t.id} — Asset {t.equipmentId}</span>
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${PRIORITY_BADGE[t.priority] || 'bg-gray-100 text-gray-600'}`}>{t.priority}</span>
                  </div>
                  <p className="text-gray-600">{t.remarks}</p>
                  <div className="flex justify-between items-center">
                    <span className="text-gray-400">Status: {t.status}</span>
                    {t.status !== 'CLOSED' && (
                      <button onClick={() => closeTicket(t.id)} className="bg-blue-600 hover:bg-blue-700 text-white font-semibold px-3 py-1 rounded-lg">Confirm Repair & Close</button>
                    )}
                  </div>
                </div>
              ))}
              {tickets.length === 0 && <p className="text-xs text-gray-400 italic">No tickets filed.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Calibration' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitCalibration} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Record Calibration</h3>
            <select required className={InputCls} value={calForm.equipmentId} onChange={e => setCalForm(p => ({ ...p, equipmentId: e.target.value }))}>
              <option value="">Select asset…</option>
              {equipment.map(e => (
                <option key={e.id} value={e.id}>{e.assetCode} — {e.equipmentName}</option>
              ))}
            </select>
            <label className="block text-xs text-gray-500">Calibration Date</label>
            <input required type="date" className={InputCls} value={calForm.calibrationDate} onChange={e => setCalForm(p => ({ ...p, calibrationDate: e.target.value }))} />
            <label className="block text-xs text-gray-500">Due Date</label>
            <input required type="date" className={InputCls} value={calForm.dueDate} onChange={e => setCalForm(p => ({ ...p, dueDate: e.target.value }))} />
            <input required placeholder="Calibration Agency" className={InputCls} value={calForm.agency} onChange={e => setCalForm(p => ({ ...p, agency: e.target.value }))} />
            <input placeholder="Certificate Reference" className={InputCls} value={calForm.certificateReference} onChange={e => setCalForm(p => ({ ...p, certificateReference: e.target.value }))} />
            <select className={InputCls} value={calForm.result} onChange={e => setCalForm(p => ({ ...p, result: e.target.value }))}>
              <option value="PASS">PASS</option>
              <option value="FAIL">FAIL</option>
            </select>
            <button className="w-full bg-green-600 hover:bg-green-700 text-white text-sm font-semibold py-2 rounded-lg">Record Calibration</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Calibration Log ({calibrations.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {calibrations.map(c => (
                <div key={c.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>Asset {c.equipmentId} — {c.agency}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400">due {c.dueDate}</span>
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${c.result === 'PASS' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>{c.result}</span>
                  </div>
                </div>
              ))}
              {calibrations.length === 0 && <p className="text-xs text-gray-400 italic">No calibration records.</p>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
