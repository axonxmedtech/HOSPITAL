import React, { useState, useEffect, useCallback } from 'react';
import patientPortalService from '../../services/patientPortalService';

export default function PatientPortalDashboard() {
  const [dashboard, setDashboard] = useState(null);
  const [appointments, setAppointments] = useState([]);
  const [reports, setReports] = useState([]);
  const [prescriptions, setPrescriptions] = useState([]);
  const [billing, setBilling] = useState([]);

  const load = useCallback(async () => {
    try {
      const [d, a, r, p, b] = await Promise.all([
        patientPortalService.getDashboard(),
        patientPortalService.getAppointments(),
        patientPortalService.getReports(),
        patientPortalService.getPrescriptions(),
        patientPortalService.getBilling(),
      ]);
      setDashboard(d?.data || d);
      setAppointments(Array.isArray(a?.data) ? a.data : (Array.isArray(a) ? a : []));
      setReports(Array.isArray(r?.data) ? r.data : (Array.isArray(r) ? r : []));
      setPrescriptions(Array.isArray(p?.data) ? p.data : (Array.isArray(p) ? p : []));
      setBilling(Array.isArray(b?.data) ? b.data : (Array.isArray(b) ? b : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="min-h-screen bg-gray-50 p-6 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">My Health Portal</h1>

      {dashboard && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Upcoming Appointments</p>
            <p className="text-2xl font-bold mt-1">{dashboard.upcomingAppointments}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Released Reports</p>
            <p className="text-2xl font-bold mt-1">{dashboard.releasedReports}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Active Prescriptions</p>
            <p className="text-2xl font-bold mt-1">{dashboard.activePrescriptions}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Outstanding Balance</p>
            <p className="text-2xl font-bold mt-1 text-amber-600">₹{dashboard.outstandingBalance}</p>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Appointments</h2>
        <div className="space-y-1 text-xs">
          {appointments.map(a => (
            <div key={a.id} className="flex justify-between border-b border-gray-100 py-1.5">
              <span>{a.doctorName} — {a.appointmentDate} {a.appointmentTime}</span>
              <span className="text-gray-400">{a.status}</span>
            </div>
          ))}
          {appointments.length === 0 && <p className="text-gray-400 italic">No appointments.</p>}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Released Reports</h2>
        <div className="space-y-2 text-xs">
          {reports.map(r => (
            <div key={`${r.category}-${r.orderId}`} className="border border-gray-100 rounded-lg p-2">
              <div className="flex justify-between items-center">
                <span className="font-semibold">{r.testName} ({r.category})</span>
                {r.isAbnormal && <span className="text-red-600 font-semibold">Abnormal</span>}
              </div>
              <p className="text-gray-600 mt-1">{r.summary}</p>
              {r.resultFileUrl && (
                <a href={r.resultFileUrl} target="_blank" rel="noreferrer" className="text-indigo-600 underline">Download</a>
              )}
            </div>
          ))}
          {reports.length === 0 && <p className="text-gray-400 italic">No released reports yet.</p>}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Prescriptions</h2>
        <div className="space-y-1 text-xs">
          {prescriptions.map(p => (
            <div key={p.id} className="flex justify-between border-b border-gray-100 py-1.5">
              <span>{p.medicineName} — {p.dosage} ({p.frequency})</span>
              <span className="text-gray-400">{p.status}</span>
            </div>
          ))}
          {prescriptions.length === 0 && <p className="text-gray-400 italic">No prescriptions.</p>}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Billing</h2>
        <div className="space-y-1 text-xs">
          {billing.map(b => (
            <div key={b.id} className="flex justify-between border-b border-gray-100 py-1.5">
              <span>Bill #{b.id}</span>
              <span className={b.paymentStatus === 'PAID' ? 'text-green-600' : 'text-amber-600'}>₹{b.amount} — {b.paymentStatus}</span>
            </div>
          ))}
          {billing.length === 0 && <p className="text-gray-400 italic">No bills.</p>}
        </div>
      </div>
    </div>
  );
}
