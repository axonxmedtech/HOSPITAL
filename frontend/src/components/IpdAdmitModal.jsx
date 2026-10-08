import React, { useEffect, useState } from 'react';
import { useToast } from '../context/ToastContext';
import hospitalService from '../services/hospitalService';
import wardService from '../services/wardService';

const IpdAdmitModal = ({ isOpen, onClose, opd, onSuccess, initialDiagnosis }) => {
  const [wards, setWards] = useState([]);
  const [selectedWard, setSelectedWard] = useState(null);
  const [beds, setBeds] = useState([]);
  const [selectedBed, setSelectedBed] = useState(null);
  const [admissionType, setAdmissionType] = useState('EMERGENCY');

  // Direct Admission fields (when opd is null)
  const [patients, setPatients] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [selectedPatientId, setSelectedPatientId] = useState(null);
  const [selectedDoctorId, setSelectedDoctorId] = useState(null);

  const [primaryDiagnosis, setPrimaryDiagnosis] = useState(initialDiagnosis || opd?.problem || '');
  const [loading, setLoading] = useState(false);
  const { success, error: toastError } = useToast();

  useEffect(() => {
    if (!isOpen) return;
    setSelectedWard(null);
    setSelectedBed(null);
    const loadData = async () => {
      try {
        let w = await wardService.getWardsForAdmission();
        if (!w || w.length === 0) {
          w = await wardService.getWards();
        }
        setWards(w || []);

        // Load patients & doctors for direct admissions when opd is not provided
        if (!opd) {
          const patRes = await hospitalService.getPatients('', 0, 200);
          const patList = patRes?.content || patRes || [];

          // Filter out patients who are already actively admitted in IPD/ICU
          let activePatientIds = new Set();
          try {
            const activeSummaries = await hospitalService.getAdmittedIpdAdmissions();
            (activeSummaries || []).forEach((a) => {
              const pId = a.patientId || a.patient?.id || a.ipd?.patientId;
              if (pId) activePatientIds.add(Number(pId));
            });
          } catch (e) {
            console.error('Failed to load active admissions filter', e);
          }

          setPatients(patList.filter((p) => !activePatientIds.has(Number(p.id))));

          const docRes = await hospitalService.getDoctors();
          const docList = docRes?.content || docRes || [];
          setDoctors(docList);
        }
      } catch (err) {
        console.error('Failed to load admission options', err);
      }
    };
    loadData();
    setPrimaryDiagnosis(initialDiagnosis || opd?.problem || '');
  }, [isOpen, opd, initialDiagnosis]);

  useEffect(() => {
    setSelectedBed(null);
    const loadBeds = async () => {
      if (!selectedWard) {
        setBeds([]);
        return;
      }
      try {
        const b = await wardService.getAvailableBeds(selectedWard);
        const availableBeds = (b || []).filter(
          (bed) => bed.status && bed.status.toLowerCase() === 'available'
        );
        setBeds(availableBeds);
      } catch (err) {
        console.error('Failed to load beds', err);
      }
    };
    loadBeds();
  }, [selectedWard]);

  // Auto-select ward if only one is available
  useEffect(() => {
    if (wards && wards.length === 1) {
      setSelectedWard(Number(wards[0].wardId));
    }
  }, [wards]);

  // Auto-select bed if only one is available
  useEffect(() => {
    if (beds && beds.length === 1) {
      setSelectedBed(Number(beds[0].bedId));
    }
  }, [beds]);

  if (!isOpen) return null;

  const handleSubmit = async () => {
    if (!selectedWard || !selectedBed) {
      toastError('Please select ward and bed');
      return;
    }
    if (!opd && !selectedPatientId) {
      toastError('Please select a patient for admission');
      return;
    }

    setLoading(true);
    try {
      const payload = {
        opdId: opd ? opd.id : null,
        patientId: opd ? opd.patient?.id : selectedPatientId,
        doctorId: opd ? opd.doctor?.id : selectedDoctorId,
        wardId: selectedWard,
        bedId: selectedBed,
        admissionType,
        primaryDiagnosis,
      };
      const res = await hospitalService.createIpdAdmission(payload);
      success('Patient admitted to ICU / IPD successfully');
      onSuccess && onSuccess(res);
      onClose();
    } catch (err) {
      console.error('IPD admit failed', err);
      const data = err.response?.data;
      const msg =
        typeof data === 'string' ? data : data?.error || data?.message || 'Failed to admit patient';
      toastError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto border border-gray-100">
        <div className="flex justify-between items-center mb-5 pb-3 border-b border-gray-100">
          <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
            <span>🏥</span> Admit Patient to ICU / IPD
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 font-bold text-xl">
            ✕
          </button>
        </div>

        <div className="space-y-4">
          {/* Patient Selector (if direct admission without OPD) */}
          {!opd ? (
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1">Select Patient *</label>
              <select
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                value={selectedPatientId || ''}
                onChange={(e) => setSelectedPatientId(Number(e.target.value))}
              >
                <option value="">-- Choose Patient --</option>
                {patients.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} (ID: {p.customId || p.id}, Mobile: {p.mobile || 'N/A'})
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div className="bg-sky-50 p-3 rounded-xl border border-sky-100 text-xs text-sky-900">
              <span className="font-bold">Patient:</span> {opd.patient?.name || 'Selected Patient'}
            </div>
          )}

          {/* Doctor Selector */}
          {!opd && (
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1">Attending Doctor (Optional)</label>
              <select
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                value={selectedDoctorId || ''}
                onChange={(e) => setSelectedDoctorId(Number(e.target.value))}
              >
                <option value="">-- Choose Doctor --</option>
                {doctors.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name || d.doctorName} ({d.specialization || 'Doctor'})
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Ward Selector */}
          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Ward *</label>
            {wards && wards.length === 1 ? (
              <div className="w-full px-3 py-2 bg-gray-50 border border-gray-200 text-gray-800 rounded-xl text-sm font-semibold flex items-center justify-between">
                <span>{wards[0].wardName}</span>
                <span className="text-xs px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full font-medium">
                  Selected
                </span>
              </div>
            ) : (
              <select
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                value={selectedWard || ''}
                onChange={(e) => setSelectedWard(Number(e.target.value))}
              >
                <option value="">-- Select Ward --</option>
                {wards.map((w) => (
                  <option key={w.wardId} value={w.wardId}>
                    {w.wardName} ({w.wardType || 'Ward'})
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Bed Selector */}
          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Available Bed *</label>
            {selectedWard && (!beds || beds.length === 0) ? (
              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 flex items-center justify-between">
                <span>⚠️ No available beds in this ward. Use &quot;Wards & Beds&quot; tab to free up or add a bed.</span>
              </div>
            ) : beds && beds.length === 1 ? (
              <div className="w-full px-3 py-2 bg-gray-50 border border-gray-200 text-gray-800 rounded-xl text-sm font-semibold flex items-center justify-between">
                <span>
                  {beds[0].bedCode} — {beds[0].status}
                </span>
                <span className="text-xs px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full font-medium">
                  Selected
                </span>
              </div>
            ) : (
              <select
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                value={selectedBed || ''}
                onChange={(e) => setSelectedBed(Number(e.target.value))}
              >
                <option value="">-- Select Bed --</option>
                {beds.map((b) => (
                  <option key={b.bedId} value={b.bedId}>
                    {b.bedCode} ({b.status})
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Admission Type */}
          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Admission Type</label>
            <select
              className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
              value={admissionType}
              onChange={(e) => setAdmissionType(e.target.value)}
            >
              <option value="EMERGENCY">Emergency Admission</option>
              <option value="ELECTIVE">Elective Admission</option>
            </select>
          </div>

          {/* Primary Diagnosis */}
          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Primary Diagnosis / Condition</label>
            <textarea
              className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
              rows={3}
              placeholder="e.g. Acute Respiratory Distress, Post-Op Monitoring..."
              value={primaryDiagnosis}
              onChange={(e) => setPrimaryDiagnosis(e.target.value)}
            />
          </div>
        </div>

        {/* Modal Actions */}
        <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-gray-100">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-xl text-sm font-semibold text-gray-700 hover:bg-gray-50 transition-all"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={loading}
            className="px-4 py-2 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-sm font-semibold shadow-sm transition-all"
          >
            {loading ? 'Admitting...' : 'Confirm Admission'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default IpdAdmitModal;
