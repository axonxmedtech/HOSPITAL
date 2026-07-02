import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';
import NurseAssessmentForm from './NurseAssessmentForm';
import VitalsForm from './VitalsForm';
import MedicationAdministrationModal from './MedicationAdministrationModal';
import RiskAssessmentPanel from '../ipd/RiskAssessmentPanel';

export default function PatientClinicalRecord({ admission, onBack }) {
  const [tab, setTab] = useState('assessment');
  const [assessment, setAssessment] = useState(null);
  const [vitals, setVitals] = useState([]);
  const [orders, setOrders] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [administerTarget, setAdministerTarget] = useState(null);

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
      } else if (tab === 'orders' || tab === 'mar') {
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
        {['assessment', 'vitals', 'orders', 'mar', 'risk'].map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 transition-colors ${
              tab === t ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'orders' ? 'Orders' : t === 'mar' ? 'MAR (Medication Chart)' : t === 'risk' ? 'Risk Assessment' : t.charAt(0).toUpperCase() + t.slice(1)}
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

      {!loading && tab === 'risk' && (
        <RiskAssessmentPanel
          admissionId={admission.id}
          patientId={admission.patientId}
          isLocked={false}
        />
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
      {!loading && tab === 'mar' && (
        <div className="space-y-6">
          {orders.filter(o => o.orderType === 'MEDICATION').length === 0 && (
            <div className="text-center py-8 text-gray-400">No active medication orders</div>
          )}
          {orders.filter(o => o.orderType === 'MEDICATION').map(order => {
            const orderTasks = [...tasks.filter(t => t.doctorOrderId === order.id)]
              .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));

            return (
              <div key={order.id} className="bg-white rounded-2xl border border-gray-200 overflow-hidden shadow-sm">
                {/* Medication Details Header */}
                <div className="bg-slate-50 border-b border-gray-150 px-5 py-4 flex justify-between items-start">
                  <div>
                    <h4 className="font-bold text-gray-900 text-sm sm:text-base">{order.description}</h4>
                    <p className="text-xs text-gray-500 mt-1">
                      Frequency: <span className="font-semibold text-gray-700">{order.frequency}</span>
                      {order.notes && ` · Notes: ${order.notes}`}
                    </p>
                  </div>
                  <span className={`text-xs px-2.5 py-1 rounded-full font-semibold ${
                    order.status === 'ACTIVE' ? 'bg-green-100 text-green-700 border border-green-200' : 'bg-gray-100 text-gray-600 border border-gray-200'
                  }`}>
                    {order.status}
                  </span>
                </div>

                {/* Timeline and Administrations List */}
                <div className="p-5">
                  <h5 className="text-xs font-bold uppercase tracking-wider text-gray-400 mb-3">Scheduled Doses</h5>
                  {orderTasks.length === 0 ? (
                    <p className="text-xs text-gray-400 italic">No scheduled doses recorded</p>
                  ) : (
                    <div className="space-y-4 relative pl-4 border-l border-gray-200 ml-2">
                      {orderTasks.map(t => {
                        const isOverdue = t.status === 'PENDING' && t.scheduledAt && new Date(t.scheduledAt) < new Date();
                        const timeStr = t.scheduledAt
                          ? new Date(t.scheduledAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })
                          : 'No time set';

                        let statusBadge = 'bg-yellow-50 text-yellow-700 border border-yellow-200';
                        if (t.status === 'DONE') statusBadge = 'bg-green-50 text-green-700 border border-green-200';
                        if (t.status === 'SKIPPED') statusBadge = 'bg-gray-50 text-gray-600 border border-gray-200';
                        if (t.status === 'REFUSED') statusBadge = 'bg-red-50 text-red-700 border border-red-200';
                        if (t.status === 'HELD') statusBadge = 'bg-amber-50 text-amber-700 border border-amber-200';

                        return (
                          <div key={t.id} className="relative group">
                            {/* Dot indicator */}
                            <div className={`absolute -left-[21px] top-1.5 w-3 h-3 rounded-full border-2 border-white ${
                              t.status === 'PENDING' ? (isOverdue ? 'bg-red-500' : 'bg-yellow-500') :
                              t.status === 'DONE' ? 'bg-green-500' :
                              t.status === 'REFUSED' ? 'bg-red-500' : 'bg-amber-500'
                            }`} />

                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pl-2">
                              <div>
                                <p className="text-xs font-semibold text-gray-700">
                                  Scheduled: {timeStr}
                                  {isOverdue && <span className="ml-2 text-red-600 font-extrabold text-[10px] tracking-wider animate-pulse">OVERDUE</span>}
                                </p>

                                {t.status !== 'PENDING' ? (
                                  <div className="mt-1 space-y-0.5 text-xs text-gray-500">
                                    <p>
                                      Recorded Status: <span className="font-semibold">{t.status === 'DONE' ? 'GIVEN (Done)' : t.status}</span>
                                      {t.executedByName && ` by ${t.executedByName}`}
                                      {t.executedAt && ` at ${new Date(t.executedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`}
                                    </p>
                                    {t.status === 'DONE' && (
                                      <p className="text-slate-600 font-medium bg-slate-50 border border-slate-100 rounded px-2 py-1 mt-1 w-fit">
                                        Qty: {t.administeredQuantity || '1.0'} · Route: {t.route || 'ORAL'}
                                        {t.injectionSite && ` · Site: ${t.injectionSite}`}
                                        {t.preVitals && ` · Pre-Vitals: ${t.preVitals}`}
                                      </p>
                                    )}
                                    {t.notes && (
                                      <p className="italic text-gray-500 mt-0.5">Remarks: "{t.notes}"</p>
                                    )}
                                  </div>
                                ) : (
                                  <p className="text-xs text-gray-400 italic mt-0.5">Awaiting administration</p>
                                )}
                              </div>

                              {t.status === 'PENDING' && (
                                <button
                                  onClick={() => setAdministerTarget(t)}
                                  className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all active:scale-95 self-start sm:self-center"
                                >
                                  💊 Administer
                                </button>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Medication administration dialogue */}
      {administerTarget && (
        <MedicationAdministrationModal
          task={{
            ...administerTarget,
            orderDescription: orders.find(o => o.id === administerTarget.doctorOrderId)?.description
          }}
          onClose={() => setAdministerTarget(null)}
          onSave={async (payload) => {
            await nurseService.executeTask(admission.id, administerTarget.id, payload);
            setAdministerTarget(null);
            loadData();
          }}
        />
      )}
    </div>
  );
}
