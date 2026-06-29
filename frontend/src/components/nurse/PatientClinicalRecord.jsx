import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';
import NurseAssessmentForm from './NurseAssessmentForm';
import VitalsForm from './VitalsForm';

export default function PatientClinicalRecord({ admission, onBack }) {
  const [tab, setTab] = useState('assessment');
  const [assessment, setAssessment] = useState(null);
  const [vitals, setVitals] = useState([]);
  const [orders, setOrders] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => { loadData(); }, [tab]);

  async function loadData() {
    setLoading(true);
    try {
      if (tab === 'assessment') {
        const r = await nurseService.getAssessment(admission.id);
        setAssessment(r.data);
      } else if (tab === 'vitals') {
        const r = await nurseService.getVitals(admission.id);
        setVitals(r.data);
      } else if (tab === 'orders') {
        const [ordRes, taskRes] = await Promise.all([
          nurseService.getOrders(admission.id),
          nurseService.getTasks(admission.id),
        ]);
        setOrders(ordRes.data);
        setTasks(taskRes.data);
      }
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }

  const fallRiskColor = { LOW: 'text-green-600', MEDIUM: 'text-yellow-600', HIGH: 'text-red-600' };

  return (
    <div className="p-6">
      <button onClick={onBack} className="text-blue-600 hover:underline text-sm mb-4">
        ← Back to patients
      </button>
      <h2 className="text-xl font-bold text-gray-800 mb-1">{admission.patientName}</h2>
      <p className="text-sm text-gray-500 mb-4">
        {admission.uhid} &nbsp;·&nbsp; {admission.wardName} / {admission.bedNumber}
      </p>

      <div className="flex gap-2 mb-6 border-b border-gray-200">
        {['assessment', 'vitals', 'orders'].map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 transition-colors ${
              tab === t ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'orders' ? 'Orders & Tasks' : t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {loading && <div className="text-center py-8 text-gray-400">Loading...</div>}

      {!loading && tab === 'assessment' && (
        assessment
          ? (
            <div className="grid grid-cols-2 gap-4 text-sm">
              {[
                ['Blood Pressure', assessment.bloodPressure],
                ['Pulse', assessment.pulse ? `${assessment.pulse} bpm` : '—'],
                ['Temperature', assessment.temperature ? `${assessment.temperature} °F` : '—'],
                ['SpO2', assessment.spo2 ? `${assessment.spo2}%` : '—'],
                ['Height', assessment.height ? `${assessment.height} cm` : '—'],
                ['Weight', assessment.weight ? `${assessment.weight} kg` : '—'],
                ['Pain Score', assessment.painScore ?? '—'],
                ['Allergies', assessment.allergies || 'None'],
                ['General Condition', assessment.generalCondition || '—'],
                ['Chief Complaint', assessment.chiefComplaintOnAdmission || '—'],
              ].map(([label, val]) => (
                <div key={label}>
                  <span className="text-gray-500">{label}: </span>
                  <span className="font-medium">{val}</span>
                </div>
              ))}
              <div>
                <span className="text-gray-500">Fall Risk: </span>
                <span className={`font-semibold ${fallRiskColor[assessment.fallRisk] || ''}`}>
                  {assessment.fallRisk}
                </span>
              </div>
              <div className="col-span-2 text-xs text-gray-400 mt-2">
                Assessed by {assessment.assessedByName} on{' '}
                {assessment.assessedAt ? new Date(assessment.assessedAt).toLocaleString() : '—'}
              </div>
            </div>
          )
          : <NurseAssessmentForm admissionId={admission.id} onSaved={loadData} />
      )}

      {!loading && tab === 'vitals' && (
        <div>
          <div className="mb-4">
            <VitalsForm admissionId={admission.id} onSaved={loadData} />
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 text-left text-gray-600 uppercase text-xs tracking-wide">
                <th className="px-4 py-2">Time</th>
                <th className="px-4 py-2">BP</th>
                <th className="px-4 py-2">Pulse</th>
                <th className="px-4 py-2">Temp</th>
                <th className="px-4 py-2">SpO2</th>
                <th className="px-4 py-2">Recorded By</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {vitals.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-4 text-center text-gray-400">No vitals recorded yet</td></tr>
              )}
              {vitals.map(v => (
                <tr key={v.id}>
                  <td className="px-4 py-2">{v.recordedAt ? new Date(v.recordedAt).toLocaleString() : '—'}</td>
                  <td className="px-4 py-2">{v.bloodPressure || '—'}</td>
                  <td className="px-4 py-2">{v.pulse ?? '—'}</td>
                  <td className="px-4 py-2">{v.temperature ?? '—'}</td>
                  <td className="px-4 py-2">{v.spo2 ? `${v.spo2}%` : '—'}</td>
                  <td className="px-4 py-2">{v.recordedByName || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!loading && tab === 'orders' && (
        <div className="space-y-3">
          {orders.length === 0 && (
            <div className="text-center py-6 text-gray-400">No orders yet</div>
          )}
          {orders.map(order => {
            const orderTasks = tasks.filter(t => t.doctorOrderId === order.id);
            const pendingCount = orderTasks.filter(t => t.status === 'PENDING').length;
            return (
              <div key={order.id} className="border border-gray-200 rounded-lg p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <span className={`text-xs px-2 py-0.5 rounded font-medium mr-2 ${
                      order.orderType === 'MEDICATION' ? 'bg-blue-100 text-blue-700' :
                      order.orderType === 'INVESTIGATION' ? 'bg-purple-100 text-purple-700' :
                      'bg-gray-100 text-gray-700'
                    }`}>{order.orderType}</span>
                    <span className="font-medium text-gray-800">{order.description}</span>
                  </div>
                  <span className={`text-xs px-2 py-0.5 rounded ${
                    order.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                    order.status === 'CANCELLED' ? 'bg-red-100 text-red-700' :
                    'bg-gray-100 text-gray-600'
                  }`}>{order.status}</span>
                </div>
                <div className="mt-1 text-sm text-gray-500">
                  Frequency: {order.frequency || '—'} &nbsp;·&nbsp; By: {order.createdByName}
                  {pendingCount > 0 && (
                    <span className="ml-2 bg-yellow-100 text-yellow-700 text-xs px-2 py-0.5 rounded">
                      {pendingCount} pending task{pendingCount > 1 ? 's' : ''}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
