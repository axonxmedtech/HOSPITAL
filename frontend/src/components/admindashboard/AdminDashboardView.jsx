import React, { useState, useEffect, useCallback } from 'react';
import adminDashboardService from '../../services/adminDashboardService';
import { useToast } from '../../context/ToastContext';

const TIMEFRAMES = ['TODAY', 'WEEKLY', 'MONTHLY', 'YEARLY'];

const SEVERITY_BADGE = {
  INFO: 'bg-blue-100 text-blue-700',
  WARNING: 'bg-amber-100 text-amber-700',
  CRITICAL: 'bg-red-100 text-red-700',
};

function StatCard({ label, value, accent }) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4">
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`text-2xl font-bold mt-1 ${accent || 'text-gray-900'}`}>{value}</p>
    </div>
  );
}

export default function AdminDashboardView() {
  const { success, error: toastError } = useToast();
  const [timeframe, setTimeframe] = useState('TODAY');
  const [exec, setExec] = useState(null);
  const [clinical, setClinical] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [alertForm, setAlertForm] = useState({ severity: 'WARNING', title: '', description: '' });
  const [remarkDraft, setRemarkDraft] = useState({});

  const load = useCallback(async () => {
    try {
      const [e, c, a] = await Promise.all([
        adminDashboardService.getExecutiveDashboard(timeframe),
        adminDashboardService.getClinicalDashboard(),
        adminDashboardService.getAlerts(),
      ]);
      setExec(e?.data || e);
      setClinical(c?.data || c);
      setAlerts(Array.isArray(a?.data) ? a.data : (Array.isArray(a) ? a : []));
    } catch (e) { /* best-effort */ }
  }, [timeframe]);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitAlert = async (e) => {
    e.preventDefault();
    try {
      await adminDashboardService.createAlert(alertForm);
      success('Alert logged');
      setAlertForm({ severity: 'WARNING', title: '', description: '' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to create alert')); }
  };

  const act = async (id, status) => {
    const remarks = remarkDraft[id];
    if (!remarks || !remarks.trim()) return toastError('Enter a remark explaining the action');
    try {
      await adminDashboardService.acknowledgeAlert(id, status, remarks);
      success(`Alert ${status.toLowerCase()}`);
      setRemarkDraft(p => ({ ...p, [id]: '' }));
      load();
    } catch (ex) { toastError(err(ex, 'Failed to update alert')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Executive & Clinical Dashboard</h2>
          <p className="text-sm text-gray-600 mt-1">Live operational metrics aggregated from billing, beds, OT, and pharmacy — plus real-time executive alerts.</p>
        </div>
        <select className="border border-gray-300 rounded-lg px-3 py-2 text-sm" value={timeframe} onChange={e => setTimeframe(e.target.value)}>
          {TIMEFRAMES.map(t => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>

      {exec && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard label="Bed Occupancy" value={`${exec.bedOccupancyRate}%`} />
          <StatCard label="Occupied / Total Beds" value={`${exec.occupiedBeds} / ${exec.totalBeds}`} />
          <StatCard label={`Revenue (${timeframe})`} value={`₹${exec.totalRevenue}`} accent="text-green-600" />
          <StatCard label="Outstanding AR" value={`₹${exec.outstandingAr}`} accent="text-amber-600" />
          <StatCard label="Stock Expiry Alerts (30d)" value={exec.stockExpiryAlerts} />
          <StatCard label="Active Alerts" value={exec.activeAlerts} />
          <StatCard label="Critical Alerts" value={exec.criticalAlerts} accent="text-red-600" />
        </div>
      )}

      {clinical && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard label="OT Scheduled" value={clinical.otBookingsScheduled} />
          <StatCard label="OT Completed" value={clinical.otBookingsCompleted} accent="text-green-600" />
          <StatCard label="OT Cancelled" value={clinical.otBookingsCancelled} accent="text-red-600" />
          <StatCard label="Active Clinical Alerts" value={clinical.activeClinicalAlerts} />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <form onSubmit={submitAlert} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
          <h3 className="font-semibold text-gray-800 text-sm">Log Executive Alert</h3>
          <select className={InputCls} value={alertForm.severity} onChange={e => setAlertForm(p => ({ ...p, severity: e.target.value }))}>
            <option value="INFO">INFO</option>
            <option value="WARNING">WARNING</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
          <input required placeholder="Title" className={InputCls} value={alertForm.title} onChange={e => setAlertForm(p => ({ ...p, title: e.target.value }))} />
          <textarea required rows={3} placeholder="Description" className={InputCls} value={alertForm.description} onChange={e => setAlertForm(p => ({ ...p, description: e.target.value }))} />
          <button className="w-full bg-slate-700 hover:bg-slate-800 text-white text-sm font-semibold py-2 rounded-lg">Log Alert</button>
        </form>
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
          <h3 className="font-semibold text-gray-800 text-sm mb-2">Alerts ({alerts.length})</h3>
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {alerts.map(a => (
              <div key={a.id} className="border border-gray-100 rounded-lg p-3 text-xs space-y-2">
                <div className="flex justify-between items-center">
                  <span className="font-semibold">{a.title}</span>
                  <span className={`px-2 py-0.5 rounded-full font-semibold ${SEVERITY_BADGE[a.severity] || 'bg-gray-100 text-gray-600'}`}>{a.severity}</span>
                </div>
                <p className="text-gray-600">{a.description}</p>
                <div className="flex justify-between items-center">
                  <span className="text-gray-400">Status: {a.status}</span>
                  {a.status !== 'RESOLVED' && (
                    <div className="flex gap-2">
                      <input
                        placeholder="Remark"
                        className="border border-gray-300 rounded-lg px-2 py-1 text-xs"
                        value={remarkDraft[a.id] || ''}
                        onChange={e => setRemarkDraft(p => ({ ...p, [a.id]: e.target.value }))}
                      />
                      {a.status === 'ACTIVE' && (
                        <button onClick={() => act(a.id, 'ACKNOWLEDGED')} className="bg-amber-600 hover:bg-amber-700 text-white font-semibold px-3 py-1 rounded-lg whitespace-nowrap">Acknowledge</button>
                      )}
                      <button onClick={() => act(a.id, 'RESOLVED')} className="bg-green-600 hover:bg-green-700 text-white font-semibold px-3 py-1 rounded-lg whitespace-nowrap">Resolve</button>
                    </div>
                  )}
                </div>
                {a.remarks && <p className="text-gray-500 italic">Remark: {a.remarks}</p>}
              </div>
            ))}
            {alerts.length === 0 && <p className="text-xs text-gray-400 italic">No alerts logged.</p>}
          </div>
        </div>
      </div>
    </div>
  );
}
