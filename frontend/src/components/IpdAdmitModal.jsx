import React, { useEffect, useId, useState } from 'react';
import { useToast } from '../context/ToastContext';
import authService from '../services/authService';
import hospitalService from '../services/hospitalService';
import wardService from '../services/wardService';
import { extractApiError } from '../utils/apiError';

const IpdAdmitModal = ({ isOpen, onClose, opd, onSuccess, initialDiagnosis }) => {
  const formId = useId();
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
  const [patientQuery, setPatientQuery] = useState('');
  const [activePatientIds, setActivePatientIds] = useState(() => new Set());
  const [optionsLoaded, setOptionsLoaded] = useState(false);

  const [primaryDiagnosis, setPrimaryDiagnosis] = useState(initialDiagnosis || opd?.problem || '');
  // Money at admission (billing hospitals only): optional package and advance.
  const hasBilling = (authService.getCurrentUser()?.modules || []).includes('BILLING');
  const [packages, setPackages] = useState([]);
  const [packageId, setPackageId] = useState('');
  const [minAdvance, setMinAdvance] = useState(0);
  const [advanceAmount, setAdvanceAmount] = useState('');
  const [advanceMode, setAdvanceMode] = useState('CASH');
  const [advanceReference, setAdvanceReference] = useState('');
  // A bed held by name (no registered patient): reception confirms this is the reserved patient.
  const [useReservation, setUseReservation] = useState(false);
  const [loading, setLoading] = useState(false);
  const { success, error: toastError } = useToast();

  useEffect(() => {
    if (!isOpen || !hasBilling) return;
    // Optional extras: the admission still works if either call fails.
    hospitalService
      .getIpdPackages()
      .then((list) => setPackages(list || []))
      .catch(() => setPackages([]));
    hospitalService
      .getIpdBillingSettings()
      .then((s) => setMinAdvance(Number(s?.minAdvance) || 0))
      .catch(() => setMinAdvance(0));
  }, [isOpen, hasBilling]);

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

          setActivePatientIds(activePatientIds);
          setPatients(patList.filter((p) => !activePatientIds.has(Number(p.id))));

          const docRes = await hospitalService.getDoctors('', 0, 200);
          const docList = (docRes?.content || docRes || []).filter((d) => d.isActive !== false);
          setDoctors(docList);
          if (docList.length === 1) setSelectedDoctorId(Number(docList[0].id));
        }
      } catch (err) {
        console.error('Failed to load admission options', err);
      } finally {
        setOptionsLoaded(true);
      }
    };
    loadData().catch((err) => console.error('Failed to load admission options', err));
    setPrimaryDiagnosis(initialDiagnosis || opd?.problem || '');
  }, [isOpen, opd, initialDiagnosis]);

  // Search the whole patient register, not just the 200 most recent, once the user types.
  useEffect(() => {
    if (!isOpen || opd) return undefined;
    const q = patientQuery.trim();
    if (q.length < 2) return undefined;
    let live = true;
    const t = setTimeout(async () => {
      try {
        const res = await hospitalService.getPatients(q, 0, 50);
        const list = res?.content || res || [];
        if (live) setPatients(list.filter((p) => !activePatientIds.has(Number(p.id))));
      } catch (err) {
        console.error('Patient search failed', err);
      }
    }, 300);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [patientQuery, isOpen, opd, activePatientIds]);

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
    loadBeds().catch((err) => console.error('Failed to load beds', err));
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

  const heldBed = beds.find((b) => Number(b.bedId) === Number(selectedBed) && b.reservedFor);

  const advance = Number(advanceAmount) || 0;

  /** The first problem with the form, or null when it can be sent. */
  const formProblem = () => {
    if (!selectedWard || !selectedBed) return 'Please select ward and bed';
    if (!opd && !selectedPatientId) return 'Please select a patient for admission';
    if (!opd && !selectedDoctorId) return 'Select the doctor the patient is admitted under';
    if (hasBilling && admissionType === 'ELECTIVE' && minAdvance > 0 && advance < minAdvance)
      return `A planned admission needs an advance of at least ₹${minAdvance}`;
    if (advance > 0 && advanceMode === 'UPI' && !advanceReference.trim())
      return 'Enter the UPI reference (UTR) for the advance';
    return null;
  };

  /** Package, reservation and advance are sent only when they apply. */
  const extras = () => {
    const out = {};
    if (hasBilling && packageId) out.packageId = Number(packageId);
    if (useReservation && heldBed?.reservationId) out.reservationId = heldBed.reservationId;
    if (hasBilling && advance > 0) {
      out.advanceAmount = advance;
      out.advanceMode = advanceMode;
      out.advanceReference = advanceReference.trim() || null;
    }
    return out;
  };

  const handleSubmit = async () => {
    const problem = formProblem();
    if (problem) {
      toastError(problem);
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
        ...extras(),
      };
      const res = await hospitalService.createIpdAdmission(payload);
      success('Patient admitted');
      onSuccess && onSuccess(res);
      onClose();
    } catch (err) {
      console.error('IPD admit failed', err);
      const msg = extractApiError(err, 'Failed to admit patient');
      toastError(msg);
    } finally {
      setLoading(false);
    }
  };

  // Which ward control to show: a hint when nothing is set up, the only ward, or a picker.
  let wardField = (
    <select
      id={`${formId}-ward`}
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
  );
  if (optionsLoaded && wards.length === 0) {
    wardField = (
      <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800">
        No wards or beds are set up yet. The admin adds them under Rooms → Wards &amp; Beds.
      </div>
    );
  } else if (wards?.length === 1) {
    wardField = (
      <div className="w-full px-3 py-2 bg-gray-50 border border-gray-200 text-gray-800 rounded-xl text-sm font-semibold flex items-center justify-between">
        <span>{wards[0].wardName}</span>
        <span className="text-xs px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full font-medium">
          Selected
        </span>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto border border-gray-100">
        <div className="flex justify-between items-center mb-5 pb-3 border-b border-gray-100">
          <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
            <span>🏥</span> Admit Patient
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 font-bold text-xl">
            ✕
          </button>
        </div>

        <div className="space-y-4">
          {/* Patient Selector (if direct admission without OPD) */}
          {!opd ? (
            <div>
              <label
                htmlFor={`${formId}-patient`}
                className="block text-xs font-semibold text-gray-700 mb-1"
              >
                Select Patient *
              </label>
              <input
                type="search"
                aria-label="Search patient"
                value={patientQuery}
                onChange={(e) => setPatientQuery(e.target.value)}
                placeholder="Search by name, mobile or ID"
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm mb-2 focus:ring-2 focus:ring-sky-500 focus:outline-none"
              />
              <select
                id={`${formId}-patient`}
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                value={selectedPatientId || ''}
                onChange={(e) => setSelectedPatientId(Number(e.target.value))}
              >
                <option value="">-- Choose Patient --</option>
                {patients.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} (ID: {p.customId || p.id}, Mobile: {p.mobile || p.phone || 'N/A'})
                  </option>
                ))}
              </select>
              <p className="text-xs text-gray-500 mt-1">
                Patient not listed? Register them first under Patients, then admit.
              </p>
            </div>
          ) : (
            <div className="bg-sky-50 p-3 rounded-xl border border-sky-100 text-xs text-sky-900">
              <span className="font-bold">Patient:</span> {opd.patient?.name || 'Selected Patient'}
            </div>
          )}

          {/* Doctor Selector */}
          {!opd && (
            <div>
              <label
                htmlFor={`${formId}-doctor`}
                className="block text-xs font-semibold text-gray-700 mb-1"
              >
                Attending Doctor *
              </label>
              <select
                id={`${formId}-doctor`}
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
              {optionsLoaded && doctors.length === 0 && (
                <p className="text-xs text-amber-800 bg-amber-50 rounded p-2 mt-1">
                  No doctors added yet. The admin adds them under Staff → Doctors.
                </p>
              )}
            </div>
          )}

          {/* Ward Selector */}
          <div>
            <label
              htmlFor={`${formId}-ward`}
              className="block text-xs font-semibold text-gray-700 mb-1"
            >
              Ward *
            </label>
            {wardField}
          </div>

          {/* Bed Selector */}
          <div>
            <label
              htmlFor={`${formId}-bed`}
              className="block text-xs font-semibold text-gray-700 mb-1"
            >
              Available Bed *
            </label>
            {selectedWard && (!beds || beds.length === 0) ? (
              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 flex items-center justify-between">
                <span>
                  ⚠️ No available beds in this ward. Use &quot;Wards & Beds&quot; tab to free up or
                  add a bed.
                </span>
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
                id={`${formId}-bed`}
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                value={selectedBed || ''}
                onChange={(e) => setSelectedBed(Number(e.target.value))}
              >
                <option value="">-- Select Bed --</option>
                {beds.map((b) => (
                  <option key={b.bedId} value={b.bedId}>
                    {b.bedCode} ({b.status})
                    {b.reservedFor ? ` — reserved for ${b.reservedFor}` : ''}
                  </option>
                ))}
              </select>
            )}
          </div>

          {heldBed && (
            <div className="text-sm rounded-xl p-3 bg-sky-50 text-sky-900">
              This bed is reserved for <strong>{heldBed.reservedFor}</strong>.
              {heldBed.reservedPatientId ? (
                ' Only that patient can be admitted into it.'
              ) : (
                <label className="flex items-center gap-2 mt-2">
                  <input
                    type="checkbox"
                    checked={useReservation}
                    onChange={(e) => setUseReservation(e.target.checked)}
                  />{' '}
                  This is the reserved patient
                </label>
              )}
            </div>
          )}

          {/* Admission Type */}
          <div>
            <label
              htmlFor={`${formId}-type`}
              className="block text-xs font-semibold text-gray-700 mb-1"
            >
              Admission Type
            </label>
            <select
              id={`${formId}-type`}
              className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
              value={admissionType}
              onChange={(e) => setAdmissionType(e.target.value)}
            >
              <option value="EMERGENCY">Emergency Admission</option>
              <option value="ELECTIVE">Elective Admission</option>
            </select>
          </div>

          {hasBilling && packages.length > 0 && (
            <div>
              <label
                htmlFor={`${formId}-package`}
                className="block text-xs font-semibold text-gray-700 mb-1"
              >
                Package (optional)
              </label>
              <select
                id={`${formId}-package`}
                className="w-full border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                value={packageId}
                onChange={(e) => setPackageId(e.target.value)}
              >
                <option value="">No package (charged per day)</option>
                {packages.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} — ₹{Number(p.price).toLocaleString('en-IN')}, {p.includedDays} day
                    {p.includedDays === 1 ? '' : 's'}
                  </option>
                ))}
              </select>
            </div>
          )}

          {hasBilling && (
            <div>
              <label
                htmlFor={`${formId}-advance`}
                className="block text-xs font-semibold text-gray-700 mb-1"
              >
                Advance (₹)
                {admissionType === 'ELECTIVE' && minAdvance > 0
                  ? ` — at least ₹${minAdvance} for a planned admission`
                  : ' (optional)'}
              </label>
              <div className="grid grid-cols-3 gap-2">
                <input
                  id={`${formId}-advance`}
                  type="number"
                  min="0"
                  className="col-span-1 border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                  value={advanceAmount}
                  onChange={(e) => setAdvanceAmount(e.target.value)}
                />
                <select
                  aria-label="Advance paid by"
                  className="col-span-1 border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none"
                  value={advanceMode}
                  onChange={(e) => setAdvanceMode(e.target.value)}
                >
                  <option value="CASH">Cash</option>
                  <option value="UPI">UPI</option>
                  <option value="CARD">Card</option>
                </select>
                <input
                  aria-label="Advance reference"
                  placeholder={advanceMode === 'UPI' ? 'UTR *' : 'Reference'}
                  disabled={advanceMode === 'CASH'}
                  maxLength={100}
                  className="col-span-1 border border-gray-300 rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-sky-500 focus:outline-none disabled:bg-gray-50"
                  value={advanceReference}
                  onChange={(e) => setAdvanceReference(e.target.value)}
                />
              </div>
            </div>
          )}

          {/* Primary Diagnosis */}
          <div>
            <label
              htmlFor={`${formId}-diagnosis`}
              className="block text-xs font-semibold text-gray-700 mb-1"
            >
              Primary Diagnosis / Condition
            </label>
            <textarea
              id={`${formId}-diagnosis`}
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
            className="px-4 py-2 bg-sky-700 hover:bg-sky-800 text-white rounded-xl text-sm font-semibold shadow-sm transition-all"
          >
            {loading ? 'Admitting...' : 'Confirm Admission'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default IpdAdmitModal;
