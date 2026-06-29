import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';
import PatientClinicalRecord from '../../components/nurse/PatientClinicalRecord';

export default function NurseDashboard() {
  const [tab, setTab] = useState('patients');
  const [patients, setPatients] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [selectedAdmission, setSelectedAdmission] = useState(null);
  const [wardFilter, setWardFilter] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (tab === 'patients') loadPatients();
    if (tab === 'tasks') loadTasks();
  }, [tab]);

  async function loadPatients() {
    setLoading(true);
    try {
      const res = await nurseService.getMyPatients();
      setPatients(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function loadTasks() {
    setLoading(true);
    try {
      const res = await nurseService.getMyTasks();
      setTasks(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleExecuteTask(task, status) {
    try {
      await nurseService.executeTask(task.ipdAdmissionId, task.id, status);
      loadTasks();
    } catch (e) {
      alert(e.response?.data || 'Failed to update task');
    }
  }

  const wards = [...new Set(patients.map(p => p.wardName).filter(Boolean))];
  const filteredPatients = wardFilter
    ? patients.filter(p => p.wardName === wardFilter)
    : patients;

  if (selectedAdmission) {
    return (
      <PatientClinicalRecord
        admission={selectedAdmission}
        onBack={() => setSelectedAdmission(null)}
      />
    );
  }

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-gray-800 mb-6">Nurse Dashboard</h1>

      <div className="flex gap-2 mb-6 border-b border-gray-200">
        {['patients', 'tasks'].map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 transition-colors ${
              tab === t
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'patients' ? 'My Patients' : 'My Tasks'}
          </button>
        ))}
      </div>

      {loading && <div className="text-center py-8 text-gray-500">Loading...</div>}

      {!loading && tab === 'patients' && (
        <div>
          {wards.length > 0 && (
            <div className="mb-4">
              <select
                value={wardFilter}
                onChange={e => setWardFilter(e.target.value)}
                className="border border-gray-300 rounded px-3 py-2 text-sm"
              >
                <option value="">All Wards</option>
                {wards.map(w => <option key={w} value={w}>{w}</option>)}
              </select>
            </div>
          )}
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 text-left text-gray-600 uppercase text-xs tracking-wide">
                  <th className="px-4 py-3">Patient</th>
                  <th className="px-4 py-3">UHID</th>
                  <th className="px-4 py-3">Ward / Bed</th>
                  <th className="px-4 py-3">Admitted</th>
                  <th className="px-4 py-3">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredPatients.length === 0 && (
                  <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">No admitted patients</td></tr>
                )}
                {filteredPatients.map(p => (
                  <tr key={p.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-800">{p.patientName}</td>
                    <td className="px-4 py-3 text-gray-500">{p.uhid || '—'}</td>
                    <td className="px-4 py-3 text-gray-500">{p.wardName} / {p.bedNumber}</td>
                    <td className="px-4 py-3 text-gray-500">
                      {p.admissionDate ? new Date(p.admissionDate).toLocaleDateString() : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setSelectedAdmission(p)}
                        className="text-blue-600 hover:underline text-sm"
                      >
                        Open
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!loading && tab === 'tasks' && (
        <div className="space-y-3">
          {tasks.length === 0 && (
            <div className="text-center py-8 text-gray-400">No pending tasks</div>
          )}
          {tasks.map(task => {
            const isOverdue = task.scheduledAt && new Date(task.scheduledAt) < new Date();
            return (
              <div
                key={task.id}
                className={`flex items-center justify-between p-4 rounded-lg border ${
                  isOverdue ? 'border-red-200 bg-red-50' : 'border-gray-200 bg-white'
                }`}
              >
                <div>
                  <p className="font-medium text-gray-800">{task.orderDescription}</p>
                  <p className="text-sm text-gray-500 mt-0.5">
                    Patient: {task.patientName} &nbsp;·&nbsp;
                    {task.orderType} &nbsp;·&nbsp;
                    {task.scheduledAt
                      ? new Date(task.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                      : 'No time set'}
                    {isOverdue && <span className="ml-2 text-red-600 font-semibold">OVERDUE</span>}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleExecuteTask(task, 'DONE')}
                    className="px-3 py-1.5 bg-green-600 text-white text-sm rounded hover:bg-green-700"
                  >
                    Done
                  </button>
                  <button
                    onClick={() => handleExecuteTask(task, 'SKIPPED')}
                    className="px-3 py-1.5 bg-gray-200 text-gray-700 text-sm rounded hover:bg-gray-300"
                  >
                    Skip
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
