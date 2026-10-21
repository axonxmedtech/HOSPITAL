import React, { useState, useEffect, useCallback } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import wardService from '../../services/wardService';
import authService from '../../services/authService';
import IpdAdmitModal from '../../components/IpdAdmitModal';
import ConfirmationModal from '../../components/ConfirmationModal';

const IcuDashboardView = () => {
  const { success, error: toastError } = useToast();
  const currentUser = authService.getCurrentUser();

  // State
  const [activeSubTab, setActiveSubTab] = useState('beds'); // 'beds' | 'patients' | 'cleaning'
  const [icuWards, setIcuWards] = useState([]);
  const [icuPatients, setIcuPatients] = useState([]);
  const [cleaningTasks, setCleaningTasks] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modals
  const [isAdmitModalOpen, setIsAdmitModalOpen] = useState(false);
  const [isCleaningModalOpen, setIsCleaningModalOpen] = useState(false);
  const [confirmModal, setConfirmModal] = useState({ open: false, title: '', message: '', onConfirm: null });

  // Cleaning Form State
  const [taskForm, setTaskForm] = useState({
    wardName: '',
    bedNumber: '',
    taskDescription: '',
    assignedTo: '',
    priority: 'MEDIUM',
  });
  const [submittingTask, setSubmittingTask] = useState(false);

  // Fetch Data
  const fetchIcuData = useCallback(async () => {
    setLoading(true);
    try {
      // 1. Fetch Wards & populate beds for each ward
      const rawWards = await wardService.getWards();
      const wardsWithBeds = await Promise.all(
        (rawWards || []).map(async (w) => {
          try {
            const beds = await wardService.getBeds(w.wardId || w.id);
            const occupiedCount = (beds || []).filter(
              (b) => b.status && b.status.toLowerCase() === 'occupied'
            ).length;
            return {
              ...w,
              beds: beds || [],
              totalBeds: w.totalBeds || beds?.length || 0,
              occupiedBeds: occupiedCount,
            };
          } catch (e) {
            return { ...w, beds: [], occupiedBeds: 0 };
          }
        })
      );
      setIcuWards(wardsWithBeds);

      // 2. Fetch IPD admissions (active admitted patients)
      let ipdList = [];
      try {
        const admittedSummaries = await hospitalService.getAdmittedIpdAdmissions();
        if (Array.isArray(admittedSummaries) && admittedSummaries.length > 0) {
          ipdList = admittedSummaries;
        } else {
          const pageRes = await hospitalService.getIpdAdmissions(0, 200, '');
          ipdList = pageRes?.content || (Array.isArray(pageRes) ? pageRes : []);
        }
      } catch (e) {
        const pageRes = await hospitalService.getIpdAdmissions(0, 200, '');
        ipdList = pageRes?.content || (Array.isArray(pageRes) ? pageRes : []);
      }
      setIcuPatients(ipdList || []);

      // 3. Fetch ICU cleaning tasks
      const cleaningRes = await hospitalService.getIcuCleaningTasks();
      setCleaningTasks(cleaningRes || []);
    } catch (err) {
      console.error('Error fetching ICU data:', err);
      toastError('Failed to load ICU dashboard data');
    } finally {
      setLoading(false);
    }
  }, [toastError]);

  useEffect(() => {
    fetchIcuData();
  }, [fetchIcuData]);

  // Handle Create Cleaning Task
  const handleCreateTask = async (e) => {
    e.preventDefault();
    if (!taskForm.taskDescription.trim()) {
      toastError('Task description is required');
      return;
    }

    setSubmittingTask(true);
    try {
      await hospitalService.createIcuCleaningTask(taskForm);
      success('Cleaning task created successfully');
      setIsCleaningModalOpen(false);
      setTaskForm({
        wardName: '',
        bedNumber: '',
        taskDescription: '',
        assignedTo: '',
        priority: 'MEDIUM',
      });
      fetchIcuData();
    } catch (err) {
      console.error(err);
      toastError(err.response?.data?.error || 'Failed to create cleaning task');
    } finally {
      setSubmittingTask(false);
    }
  };

  // Handle Update Task Status
  const handleUpdateStatus = async (taskId, newStatus) => {
    try {
      await hospitalService.updateIcuCleaningTaskStatus(taskId, newStatus);

      // If task marked COMPLETED, also update bed entity status to AVAILABLE if it was set to CLEANING
      const task = cleaningTasks.find((t) => (t.id || t.taskId) === taskId);
      if (task && newStatus === 'COMPLETED') {
        for (const ward of icuWards) {
          for (const bed of ward.beds || []) {
            const bedCode = bed?.bedCode || bed?.bedNumber;
            if (isBedMatch(task, ward, bed, bedCode)) {
              if (bed.bedId || bed.id) {
                try {
                  await wardService.updateBedStatus(bed.bedId || bed.id, 'AVAILABLE');
                } catch (e) {
                  // ignore if bed was already available
                }
              }
            }
          }
        }
      }

      success(`Task status updated to ${newStatus}`);
      fetchIcuData();
    } catch (err) {
      console.error(err);
      toastError('Failed to update task status');
    }
  };

  // Handle Delete Cleaning Task
  const handleDeleteTask = (task) => {
    setConfirmModal({
      open: true,
      title: 'Delete Cleaning Task',
      message: `Are you sure you want to delete the cleaning task "${task.taskDescription}"?`,
      onConfirm: async () => {
        try {
          await hospitalService.deleteIcuCleaningTask(task.id);
          success('Cleaning task deleted');
          fetchIcuData();
        } catch (err) {
          console.error(err);
          toastError('Failed to delete cleaning task');
        } finally {
          setConfirmModal({ open: false, title: '', message: '', onConfirm: null });
        }
      },
    });
  };

  const normalizeStr = (str) => (str || '').toString().trim().toLowerCase();

  const isBedMatch = (task, ward, bed, bedCode) => {
    if (!task || task.status === 'COMPLETED') return false;

    const taskBed = normalizeStr(task.bedNumber);
    if (!taskBed || taskBed === '-' || taskBed === 'all' || taskBed === 'none') return false;

    const tWard = normalizeStr(task.wardName);
    const wName = normalizeStr(ward?.wardName || ward?.name);

    // If task specifies ward, check ward match
    if (tWard && wName && tWard !== 'icu') {
      const wardMatches = tWard === wName || wName.includes(tWard) || tWard.includes(wName);
      if (!wardMatches) return false;
    }

    const bCode = normalizeStr(bedCode);
    const bNum = normalizeStr(bed?.bedNumber || bed?.bedCode);

    // Exact match
    if (taskBed === bCode || taskBed === bNum) return true;

    // Compare bed numbers extracted e.g. "ward 1 -b1" -> "b1"
    const extractBedSuffix = (s) => s.replace(/^.*(?:-|\bbed\b|\bward\b\s*\d+\s*)/i, '').trim();
    const cleanTaskBed = extractBedSuffix(taskBed);
    const cleanBedCode = extractBedSuffix(bCode);
    const cleanBedNum = extractBedSuffix(bNum);

    if (cleanTaskBed && (cleanTaskBed === cleanBedCode || cleanTaskBed === cleanBedNum)) {
      return true;
    }

    // Compare digits only if present
    const taskDigits = taskBed.replace(/\D/g, '');
    const bedDigits = (cleanBedNum || cleanBedCode).replace(/\D/g, '');
    if (taskDigits && bedDigits && taskDigits === bedDigits) {
      return true;
    }

    return false;
  };

  // Helper to determine bed cleaning status
  const getBedCleaningInfo = (ward, bed, bedCode) => {
    const isOccupied = bed?.status && bed.status.toLowerCase() === 'occupied';
    const isBedCleaningStatus =
      bed?.status && (bed.status.toLowerCase() === 'cleaning' || bed.status.toLowerCase() === 'dirty');

    // Find active non-COMPLETED task for this bed
    const activeTask = cleaningTasks.find((t) => isBedMatch(t, ward, bed, bedCode));

    if (activeTask) {
      if (activeTask.status === 'IN_PROGRESS') {
        return {
          status: 'IN_PROGRESS',
          label: '🧼 Cleaning In Progress',
          task: activeTask,
          badgeClass: 'bg-blue-100 text-blue-800 border-blue-200',
        };
      }
      return {
        status: 'PENDING',
        label: '⚠️ Needs Cleaning',
        task: activeTask,
        badgeClass: 'bg-amber-100 text-amber-800 border-amber-200',
      };
    }

    if (isBedCleaningStatus) {
      return {
        status: 'PENDING',
        label: '⚠️ Needs Cleaning',
        task: null,
        badgeClass: 'bg-amber-100 text-amber-800 border-amber-200',
      };
    }

    if (isOccupied) {
      return {
        status: 'OCCUPIED',
        label: '🔒 In Use',
        task: null,
        badgeClass: 'bg-gray-100 text-gray-700 border-gray-200',
      };
    }

    return {
      status: 'CLEANED',
      label: '✨ Cleaned',
      task: null,
      badgeClass: 'bg-emerald-100 text-emerald-800 border-emerald-200',
    };
  };

  // Handle Discharge Patient
  const handleDischargePatient = (ipdRow, patientName, bedCode) => {
    const ipdId = ipdRow.id || ipdRow.ipdId;
    setConfirmModal({
      open: true,
      title: 'Discharge ICU Patient',
      message: `Are you sure you want to discharge patient "${patientName}" (Bed: ${bedCode})? The bed will be automatically sent to the Cleaning Management section.`,
      onConfirm: async () => {
        try {
          await hospitalService.confirmDischarge(ipdId);
          success(`Patient ${patientName} discharged. Bed ${bedCode} sent to Cleaning Management.`);
          setActiveSubTab('cleaning');
          fetchIcuData();
        } catch (err) {
          console.error(err);
          toastError(err.response?.data?.error || err.response?.data?.message || 'Failed to discharge patient');
        } finally {
          setConfirmModal({ open: false, title: '', message: '', onConfirm: null });
        }
      },
    });
  };

  // Stats Calculations
  const totalBeds = icuWards.reduce((acc, w) => acc + (w.totalBeds || w.beds?.length || 0), 0);
  const occupiedBeds = icuWards.reduce((acc, w) => acc + (w.occupiedBeds || 0), 0);
  const availableBeds = totalBeds - occupiedBeds;
  const pendingCleaning = cleaningTasks.filter((t) => t.status === 'PENDING' || t.status === 'IN_PROGRESS').length;

  return (
    <div className="space-y-6">
      {/* Top Banner & Quick Actions */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <span>🏥</span> ICU Control Center
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            Real-time Intensive Care Unit monitoring, patient management, and cleaning operations
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsCleaningModalOpen(true)}
            className="px-4 py-2 bg-amber-50 text-amber-700 hover:bg-amber-100 border border-amber-200 rounded-xl text-sm font-semibold transition-all flex items-center gap-1.5"
          >
            <span>🧹</span>
            <span>+ Add Cleaning Task</span>
          </button>
          <button
            onClick={() => setIsAdmitModalOpen(true)}
            className="px-4 py-2 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-sm font-semibold shadow-sm transition-all flex items-center gap-1.5"
          >
            <span>➕</span>
            <span>Admit ICU Patient</span>
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Total ICU Beds</p>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-3xl font-extrabold text-gray-900">{totalBeds}</span>
            <span className="text-xs px-2.5 py-1 rounded-full bg-blue-50 text-blue-700 font-bold">Capacity</span>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Occupied Beds</p>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-3xl font-extrabold text-rose-600">{occupiedBeds}</span>
            <span className="text-xs px-2.5 py-1 rounded-full bg-rose-50 text-rose-700 font-bold">
              {totalBeds > 0 ? Math.round((occupiedBeds / totalBeds) * 100) : 0}% Rate
            </span>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Available Beds</p>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-3xl font-extrabold text-emerald-600">{availableBeds < 0 ? 0 : availableBeds}</span>
            <span className="text-xs px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-bold">Ready</span>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Active Cleaning Tasks</p>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-3xl font-extrabold text-amber-600">{pendingCleaning}</span>
            <span className="text-xs px-2.5 py-1 rounded-full bg-amber-50 text-amber-700 font-bold">Pending</span>
          </div>
        </div>
      </div>

      {/* Sub Tabs Navigation */}
      <div className="flex border-b border-gray-200 bg-white px-4 pt-3 rounded-t-2xl">
        <button
          onClick={() => setActiveSubTab('beds')}
          className={`pb-3 px-4 font-semibold text-sm transition-all border-b-2 ${
            activeSubTab === 'beds'
              ? 'border-sky-600 text-sky-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          🛏️ Wards & Bed Occupancy
        </button>
        <button
          onClick={() => setActiveSubTab('patients')}
          className={`pb-3 px-4 font-semibold text-sm transition-all border-b-2 ${
            activeSubTab === 'patients'
              ? 'border-sky-600 text-sky-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          🧑‍⚕️ ICU Active Patients ({icuPatients.length})
        </button>
        <button
          onClick={() => setActiveSubTab('cleaning')}
          className={`pb-3 px-4 font-semibold text-sm transition-all border-b-2 ${
            activeSubTab === 'cleaning'
              ? 'border-sky-600 text-sky-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          🧹 Cleaning Management ({cleaningTasks.length})
        </button>
      </div>

      {/* Sub Tab Content */}
      <div className="bg-white p-6 rounded-b-2xl border border-t-0 border-gray-200 shadow-sm">
        {loading ? (
          <div className="text-center py-12 text-gray-500">Loading ICU Dashboard data...</div>
        ) : (
          <>
            {/* SUB TAB 1: BEDS GRID */}
            {activeSubTab === 'beds' && (
              <div className="space-y-6">
                {icuWards.length === 0 ? (
                  <div className="text-center py-10 text-gray-400">No ICU wards configured.</div>
                ) : (
                  icuWards.map((ward) => (
                    <div key={ward.wardId || ward.id} className="border border-gray-200 rounded-xl p-5 bg-gray-50/50">
                      <div className="flex justify-between items-center mb-4">
                        <div>
                          <h3 className="text-lg font-bold text-gray-900">{ward.wardName || ward.name}</h3>
                          <p className="text-xs text-gray-500">
                            Floor: {ward.floorNumber || 1} | Incharge: {ward.nurseInchargeName || 'Unassigned'}
                          </p>
                        </div>
                        <div className="text-sm font-semibold text-gray-700">
                          Occupancy: <span className="text-rose-600 font-bold">{ward.occupiedBeds || 0}</span> /{' '}
                          {ward.totalBeds || ward.beds?.length || 0}
                        </div>
                      </div>

                      {/* Beds Cards Grid */}
                      <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-3">
                        {(ward.beds && ward.beds.length > 0
                          ? ward.beds
                          : Array.from({ length: ward.totalBeds || 0 })
                        ).map((bed, idx) => {
                          const bedCode = bed?.bedCode || bed?.bedNumber || `Bed ${idx + 1}`;
                          const isOccupied =
                            bed?.status && bed.status.toLowerCase() === 'occupied';
                          const cleaningInfo = getBedCleaningInfo(ward, bed, bedCode);

                          return (
                            <div
                              key={bed?.bedId || bed?.id || idx}
                              className={`p-3 rounded-xl border text-center transition-all ${
                                isOccupied
                                  ? 'bg-rose-50 border-rose-200'
                                  : cleaningInfo.status === 'PENDING'
                                  ? 'bg-amber-50 border-amber-200'
                                  : cleaningInfo.status === 'IN_PROGRESS'
                                  ? 'bg-blue-50 border-blue-200'
                                  : 'bg-emerald-50 border-emerald-200'
                              }`}
                            >
                              <div className="text-xs font-bold text-gray-900 uppercase tracking-wider">
                                {bedCode}
                              </div>

                              <div className="text-[11px] font-semibold mt-1 flex items-center justify-center gap-1">
                                <span
                                  className={`w-2 h-2 rounded-full ${
                                    isOccupied ? 'bg-rose-500' : 'bg-emerald-500'
                                  }`}
                                />
                                <span className={isOccupied ? 'text-rose-700 font-bold' : 'text-emerald-700 font-semibold'}>
                                  {isOccupied ? 'Unavailable' : 'Available'}
                                </span>
                              </div>

                              <div className="mt-2 pt-2 border-t border-gray-200/60">
                                <div className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1">
                                  Cleaning Status
                                </div>
                                <span
                                  className={`inline-block px-2 py-0.5 text-[11px] font-bold rounded-md border ${cleaningInfo.badgeClass}`}
                                >
                                  {cleaningInfo.label}
                                </span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}

            {/* SUB TAB 2: ACTIVE PATIENTS */}
            {activeSubTab === 'patients' && (
              <div className="overflow-x-auto">
                {icuPatients.length === 0 ? (
                  <div className="text-center py-12 text-gray-400 italic">No patients currently admitted in ICU.</div>
                ) : (
                  <table className="w-full text-sm text-left border-collapse">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200 text-gray-600 font-semibold">
                        <th className="px-4 py-3">S.No.</th>
                        <th className="px-4 py-3">IPD No.</th>
                        <th className="px-4 py-3">Patient Name</th>
                        <th className="px-4 py-3">Attending Doctor</th>
                        <th className="px-4 py-3">Ward</th>
                        <th className="px-4 py-3">Bed No.</th>
                        <th className="px-4 py-3">Admission Date</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {icuPatients.map((item, idx) => {
                        const row = item.ipd || item;
                        const patientName =
                          item.patient?.name || row.patientName || row.patient?.name || '-';
                        const doctorName =
                          item.doctor?.name || row.doctorName || row.doctor?.name || '-';
                        const wardName =
                          item.ward?.wardName || item.ward?.name || row.wardName || 'Ward';
                        const bedCode =
                          item.bed?.bedCode || item.bed?.bedNumber || row.bedNumber || '-';
                        const admTime = row.admissionDatetime || row.admissionDateTime;
                        return (
                          <tr key={idx} className="hover:bg-gray-50/50 transition-colors">
                            <td className="px-4 py-3 font-medium text-gray-500">{idx + 1}</td>
                            <td className="px-4 py-3 font-bold text-sky-700">
                              {row.ipdNumber || row.id || `-`}
                            </td>
                            <td className="px-4 py-3 font-semibold text-gray-900">
                              {patientName}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {doctorName}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {wardName}
                            </td>
                            <td className="px-4 py-3 font-bold text-rose-600">
                              {bedCode}
                            </td>
                            <td className="px-4 py-3 text-gray-600">
                              {admTime ? new Date(admTime).toLocaleString() : '-'}
                            </td>
                            <td className="px-4 py-3">
                              <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-rose-100 text-rose-800">
                                {row.status || 'ADMITTED'}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-right">
                              <button
                                onClick={() => handleDischargePatient(row, patientName, bedCode)}
                                className="px-3 py-1.5 bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs rounded-lg shadow-xs transition-all flex items-center gap-1 ml-auto"
                              >
                                <span>🚪</span> Discharge
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            )}

            {/* SUB TAB 3: CLEANING MANAGEMENT */}
            {activeSubTab === 'cleaning' && (
              <div className="space-y-4">
                <div className="flex justify-between items-center bg-amber-50/50 p-4 rounded-xl border border-amber-100">
                  <div className="text-xs text-amber-900 font-medium">
                    Manage ICU bed sanitation, disinfection schedules, and housekeeping assignments.
                  </div>
                  <button
                    onClick={() => setIsCleaningModalOpen(true)}
                    className="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-lg text-xs font-bold shadow-sm transition-all"
                  >
                    + New Task
                  </button>
                </div>

                <div className="overflow-x-auto">
                  {cleaningTasks.length === 0 ? (
                    <div className="text-center py-12 text-gray-400 italic">No ICU cleaning tasks recorded.</div>
                  ) : (
                    <table className="w-full text-sm text-left border-collapse">
                      <thead>
                        <tr className="bg-gray-50 border-b border-gray-200 text-gray-600 font-semibold">
                          <th className="px-4 py-3">S.No</th>
                          <th className="px-4 py-3">Ward</th>
                          <th className="px-4 py-3">Bed</th>
                          <th className="px-4 py-3">Task Description</th>
                          <th className="px-4 py-3">Priority</th>
                          <th className="px-4 py-3">Assigned To</th>
                          <th className="px-4 py-3">Created By</th>
                          <th className="px-4 py-3">Status</th>
                          <th className="px-4 py-3 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {cleaningTasks.map((task, idx) => (
                          <tr key={task.id || idx} className="hover:bg-gray-50/50 transition-colors">
                            <td className="px-4 py-3 font-medium text-gray-500">{idx + 1}</td>
                            <td className="px-4 py-3 font-semibold text-gray-900">{task.wardName || 'ICU'}</td>
                            <td className="px-4 py-3 font-bold text-gray-800">{task.bedNumber || '-'}</td>
                            <td className="px-4 py-3 text-gray-800 font-medium">{task.taskDescription}</td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                                  task.priority === 'URGENT' || task.priority === 'HIGH'
                                    ? 'bg-rose-100 text-rose-700'
                                    : task.priority === 'LOW'
                                    ? 'bg-gray-100 text-gray-700'
                                    : 'bg-amber-100 text-amber-700'
                                }`}
                              >
                                {task.priority || 'MEDIUM'}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-gray-600">{task.assignedTo || 'Unassigned'}</td>
                            <td className="px-4 py-3 text-xs text-gray-500">{task.createdBy || '-'}</td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                                  task.status === 'COMPLETED'
                                    ? 'bg-emerald-100 text-emerald-800'
                                    : task.status === 'IN_PROGRESS'
                                    ? 'bg-blue-100 text-blue-800'
                                    : 'bg-amber-100 text-amber-800'
                                }`}
                              >
                                {task.status}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-right space-x-2">
                              {task.status !== 'COMPLETED' && (
                                <button
                                  onClick={() => handleUpdateStatus(task.id, 'COMPLETED')}
                                  className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold rounded shadow-xs"
                                >
                                  Mark Complete
                                </button>
                              )}
                              <button
                                onClick={() => handleDeleteTask(task)}
                                className="px-2.5 py-1 bg-rose-50 hover:bg-rose-100 text-rose-700 text-xs font-semibold rounded border border-rose-200"
                              >
                                Delete
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* MODAL 1: ADMIT ICU PATIENT */}
      <IpdAdmitModal
        isOpen={isAdmitModalOpen}
        onClose={() => setIsAdmitModalOpen(false)}
        onSuccess={() => {
          fetchIcuData();
          setIsAdmitModalOpen(false);
        }}
      />

      {/* MODAL 2: ADD CLEANING TASK */}
      {isCleaningModalOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6 border border-gray-100">
            <div className="flex justify-between items-center mb-5">
              <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
                <span>🧹</span> New ICU Cleaning Task
              </h3>
              <button
                onClick={() => setIsCleaningModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 font-bold text-xl"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateTask} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Ward Name</label>
                <input
                  type="text"
                  placeholder="e.g. ICU Ward A"
                  value={taskForm.wardName}
                  onChange={(e) => setTaskForm({ ...taskForm, wardName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-xl text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Bed Number</label>
                <input
                  type="text"
                  placeholder="e.g. Bed-01"
                  value={taskForm.bedNumber}
                  onChange={(e) => setTaskForm({ ...taskForm, bedNumber: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-xl text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Task Description *</label>
                <textarea
                  required
                  rows={3}
                  placeholder="Sanitize bed rails, change ventilation filters..."
                  value={taskForm.taskDescription}
                  onChange={(e) => setTaskForm({ ...taskForm, taskDescription: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-xl text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Assigned To</label>
                  <input
                    type="text"
                    placeholder="Staff / Nurse name"
                    value={taskForm.assignedTo}
                    onChange={(e) => setTaskForm({ ...taskForm, assignedTo: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-xl text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Priority</label>
                  <select
                    value={taskForm.priority}
                    onChange={(e) => setTaskForm({ ...taskForm, priority: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-xl text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="URGENT">Urgent</option>
                  </select>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-gray-100">
                <button
                  type="button"
                  onClick={() => setIsCleaningModalOpen(false)}
                  className="px-4 py-2 border border-gray-300 rounded-xl text-sm font-semibold text-gray-700 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submittingTask}
                  className="px-4 py-2 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-sm font-semibold shadow-sm transition-all"
                >
                  {submittingTask ? 'Creating...' : 'Create Task'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CONFIRMATION MODAL */}
      <ConfirmationModal
        isOpen={confirmModal.open}
        title={confirmModal.title}
        message={confirmModal.message}
        onConfirm={confirmModal.onConfirm}
        onCancel={() => setConfirmModal({ open: false, title: '', message: '', onConfirm: null })}
      />
    </div>
  );
};

export default IcuDashboardView;
