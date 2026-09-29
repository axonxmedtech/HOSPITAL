import React, { useEffect, useState } from 'react';
import { useToast } from '../context/ToastContext';
import hospitalService from '../services/hospitalService';
import icuWardService from '../services/icuWardService';
import wardService from '../services/wardService';

/**
 * IcuAdmitModal
 * Dedicated modal to admit patients into ICU:
 * 1. Transfer an already-admitted General IPD patient to ICU
 * 2. Admit an OPD patient directly into an ICU ward
 */
const IcuAdmitModal = ({ isOpen, onClose, onSuccess, initialPatient = null }) => {
  const { success, error: toastError } = useToast();

  const [sourceType, setSourceType] = useState('IPD'); // 'IPD' | 'OPD'
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // ICU wards and beds
  const [icuWards, setIcuWards] = useState([]);
  const [selectedIcuWardId, setSelectedIcuWardId] = useState('');
  const [availableBeds, setAvailableBeds] = useState([]);
  const [selectedBedId, setSelectedBedId] = useState('');
  const [bedsWardId, setBedsWardId] = useState('');
  const [bedsLoading, setBedsLoading] = useState(false);

  // Source: IPD patients
  const [ipdPatients, setIpdPatients] = useState([]);
  const [ipdSearch, setIpdSearch] = useState('');
  const [selectedIpdId, setSelectedIpdId] = useState('');

  // Source: OPD patients
  const [opdPatients, setOpdPatients] = useState([]);
  const [opdSearch, setOpdSearch] = useState('');
  const [selectedOpdId, setSelectedOpdId] = useState('');
  const [admissionType, setAdmissionType] = useState('EMERGENCY');
  const [primaryDiagnosis, setPrimaryDiagnosis] = useState('');

  // Initial data load when modal opens
  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    const loadInitialData = async () => {
      setLoading(true);
      try {
        if (initialPatient) {
          setSourceType('IPD');
          const initialId =
            initialPatient.ipdId || initialPatient.id || initialPatient.admissionId || '';
          setSelectedIpdId(String(initialId));

          const wards = await icuWardService.getIcuWards().catch(() => []);
          if (isMounted) {
            setIcuWards(wards || []);
            if (wards && wards.length === 1) {
              setSelectedIcuWardId(String(wards[0].wardId));
            }
          }
          return;
        }

        const [wards, ipdRes] = await Promise.all([
          icuWardService.getIcuWards().catch(() => []),
          hospitalService.getAdmittedIpdAdmissions().catch(() => []),
        ]);

        if (isMounted) {
          setIcuWards(wards || []);
          if (wards && wards.length === 1) {
            setSelectedIcuWardId(String(wards[0].wardId));
          }
          setIpdPatients(ipdRes || []);
        }

        // Load OPD queue for direct admission
        try {
          const opdRes = await hospitalService.getOpds('', 0, 100, '', 'QUEUED');
          const list = opdRes?.content || opdRes || [];
          if (isMounted) {
            setOpdPatients(Array.isArray(list) ? list : []);
          }
        } catch {
          // Non-blocking
        }
      } catch (err) {
        console.error('Failed to load ICU admit options', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    loadInitialData();
    return () => {
      isMounted = false;
    };
  }, [isOpen, initialPatient]);

  // Load available beds whenever selected ICU ward changes
  useEffect(() => {
    setAvailableBeds([]);
    setSelectedBedId('');
    setBedsWardId('');
    setBedsLoading(false);
    if (!isOpen || !selectedIcuWardId) {
      setAvailableBeds([]);
      setSelectedBedId('');
      return;
    }

    let isMounted = true;
    const loadBeds = async () => {
      setBedsLoading(true);
      try {
        const b = await wardService.getAvailableBeds(selectedIcuWardId);
        if (isMounted) {
          const bedsList = b || [];
          setAvailableBeds(bedsList);
          setBedsWardId(selectedIcuWardId);
          if (bedsList.length === 1) {
            setSelectedBedId(String(bedsList[0].bedId));
          } else {
            setSelectedBedId('');
          }
        }
      } catch (err) {
        console.error('Failed to load ICU beds', err);
        if (isMounted) {
          setAvailableBeds([]);
          setSelectedBedId('');
        }
      } finally {
        if (isMounted) setBedsLoading(false);
      }
    };

    loadBeds();
    return () => {
      isMounted = false;
    };
  }, [isOpen, selectedIcuWardId]);

  const validBed =
    !bedsLoading &&
    bedsWardId === selectedIcuWardId &&
    availableBeds.some((bed) => String(bed.bedId) === selectedBedId);

  if (!isOpen) return null;

  // Filtered lists
  const filteredIpdPatients = (ipdPatients || []).filter((item) => {
    if (!ipdSearch.trim()) return true;
    const q = ipdSearch.toLowerCase();
    const row = item.ipd || item;
    const name = (row.patientName || row.patient?.name || '').toLowerCase();
    const ipdNo = (row.ipdNumber || row.id || '').toString().toLowerCase();
    const doc = (row.doctorName || row.doctor?.name || '').toLowerCase();
    return name.includes(q) || ipdNo.includes(q) || doc.includes(q);
  });

  const filteredOpdPatients = (opdPatients || []).filter((o) => {
    if (!opdSearch.trim()) return true;
    const q = opdSearch.toLowerCase();
    const name = (o.patientName || o.patient?.name || '').toLowerCase();
    const opdNo = (o.opdNumber || o.id || '').toString().toLowerCase();
    return name.includes(q) || opdNo.includes(q);
  });

  const selectedIpdPatientObj = (ipdPatients || []).find((item) => {
    const row = item.ipd || item;
    const id = row.ipdId || row.id || row.ipd?.id || null;
    return String(id) === String(selectedIpdId);
  });

  const selectedOpdPatientObj = (opdPatients || []).find(
    (o) => String(o.id) === String(selectedOpdId)
  );

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!selectedIcuWardId) {
      toastError('Please select an ICU Ward');
      return;
    }
    if (!validBed) {
      toastError('Please select an available ICU Bed');
      return;
    }

    setSubmitting(true);
    try {
      if (sourceType === 'IPD') {
        if (!selectedIpdId) {
          toastError('Please select an IPD patient to admit to ICU');
          setSubmitting(false);
          return;
        }

        // Transfer IPD patient to ICU bed (which opens active IcuStay)
        await hospitalService.changeBed(
          Number(selectedIpdId),
          Number(selectedBedId),
          Number(selectedIcuWardId)
        );
        success('Patient successfully admitted to ICU ward');
      } else {
        if (!selectedOpdId) {
          toastError('Please select an OPD patient to admit to ICU');
          setSubmitting(false);
          return;
        }

        const payload = {
          opdId: Number(selectedOpdId),
          wardId: Number(selectedIcuWardId),
          bedId: Number(selectedBedId),
          admissionType: admissionType || 'EMERGENCY',
          primaryDiagnosis: primaryDiagnosis || 'Admitted to ICU',
        };

        await hospitalService.createIpdAdmission(payload);
        success('Patient successfully admitted to ICU directly');
      }

      onSuccess && onSuccess();
      onClose();
    } catch (err) {
      console.error('ICU admission failed', err);
      const data = err.response?.data;
      const msg =
        typeof data === 'string'
          ? data
          : data?.error || data?.message || 'Failed to admit patient to ICU';
      toastError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-xl overflow-hidden animate-scale-in">
        {/* Header */}
        <div className="px-6 py-4 bg-gradient-to-r from-rose-700 to-rose-600 text-white flex justify-between items-center">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-white/10 rounded-lg">
              <svg
                className="w-5 h-5 text-rose-100"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 4v16m8-8H4"
                />
              </svg>
            </div>
            <div>
              <h3 className="text-lg font-bold">Admit Patient to ICU</h3>
              <p className="text-xs text-rose-100">Direct admission or step-up transfer from IPD</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>

        {/* Source Type Selector (hidden when opened for a specific patient) */}
        {!initialPatient && (
          <div className="px-6 pt-4 pb-2 border-b border-gray-100 flex gap-2">
            <button
              type="button"
              onClick={() => setSourceType('IPD')}
              className={`flex-1 py-2 px-3 rounded-lg text-xs font-semibold uppercase tracking-wider transition-all flex items-center justify-center gap-2 ${
                sourceType === 'IPD'
                  ? 'bg-rose-50 text-rose-700 border border-rose-200 shadow-xs'
                  : 'bg-gray-50 text-gray-500 hover:bg-gray-100'
              }`}
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
                />
              </svg>
              From Admitted IPD ({ipdPatients.length})
            </button>
            <button
              type="button"
              onClick={() => setSourceType('OPD')}
              className={`flex-1 py-2 px-3 rounded-lg text-xs font-semibold uppercase tracking-wider transition-all flex items-center justify-center gap-2 ${
                sourceType === 'OPD'
                  ? 'bg-rose-50 text-rose-700 border border-rose-200 shadow-xs'
                  : 'bg-gray-50 text-gray-500 hover:bg-gray-100'
              }`}
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                />
              </svg>
              From OPD / Queue ({opdPatients.length})
            </button>
          </div>
        )}

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
          {loading ? (
            <div className="py-12 text-center text-sm text-gray-500">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-rose-600 mx-auto mb-2"></div>
              Loading patient and ICU data...
            </div>
          ) : (
            <>
              {/* Patient Selection */}
              {initialPatient ? (
                <div>
                  <span className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                    Patient to Admit to ICU
                  </span>
                  <div className="p-3 bg-rose-50/50 border border-rose-100 rounded-xl flex items-center justify-between">
                    <div>
                      <div className="text-sm font-bold text-gray-900">
                        {initialPatient.patientName || initialPatient.patient?.name || 'Patient'}
                      </div>
                      <div className="text-xs text-gray-500 mt-0.5">
                        IPD #{initialPatient.ipdNumber || initialPatient.id || ''} • Current Ward:{' '}
                        {initialPatient.wardName || initialPatient.ward || 'General'}{' '}
                        {initialPatient.bedNumber || initialPatient.bed
                          ? `(Bed ${initialPatient.bedNumber || initialPatient.bed})`
                          : ''}
                      </div>
                    </div>
                    <span className="text-[11px] font-semibold text-rose-700 bg-rose-100/70 border border-rose-200 px-2.5 py-0.5 rounded-md">
                      Current Patient
                    </span>
                  </div>
                </div>
              ) : sourceType === 'IPD' ? (
                <div>
                  <label
                    htmlFor="icu-admit-ipd-select"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Select General IPD Patient <span className="text-rose-600">*</span>
                  </label>
                  <div className="mb-2">
                    <input
                      type="text"
                      placeholder="Search admitted patient by name, IPD #, doctor..."
                      value={ipdSearch}
                      onChange={(e) => setIpdSearch(e.target.value)}
                      className="w-full text-xs px-3 py-2 border border-gray-300 rounded-lg focus:ring-1 focus:ring-rose-500"
                    />
                  </div>
                  <select
                    id="icu-admit-ipd-select"
                    value={selectedIpdId}
                    onChange={(e) => setSelectedIpdId(e.target.value)}
                    required
                    className="w-full text-sm px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-rose-500"
                  >
                    <option value="">
                      -- Choose Admitted Patient ({filteredIpdPatients.length}) --
                    </option>
                    {filteredIpdPatients.map((item) => {
                      const row = item.ipd || item;
                      const id = row.ipdId || row.id || row.ipd?.id || null;
                      const patientName = row.patientName || row.patient?.name || 'Patient';
                      const ipdNo = row.ipdNumber || row.id || '';
                      const ward = row.wardName || row.ward?.name || 'Ward';
                      const bed = row.bedNumber || row.bed?.bedCode || 'Bed';
                      return (
                        <option key={id} value={id}>
                          {patientName} (IPD #{ipdNo}) — {ward} / {bed}
                        </option>
                      );
                    })}
                  </select>
                  {selectedIpdPatientObj && (
                    <div className="mt-2 p-2.5 bg-rose-50/60 border border-rose-100 rounded-lg text-xs text-rose-900">
                      <strong>Current Location:</strong>{' '}
                      {selectedIpdPatientObj.wardName || selectedIpdPatientObj.ward?.name} / Bed{' '}
                      {selectedIpdPatientObj.bedNumber || selectedIpdPatientObj.bed?.bedCode}
                      {selectedIpdPatientObj.doctorName && (
                        <span className="ml-3">
                          <strong>Doctor:</strong> {selectedIpdPatientObj.doctorName}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              ) : (
                <div>
                  <label
                    htmlFor="icu-admit-opd-select"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Select OPD Patient <span className="text-rose-600">*</span>
                  </label>
                  <div className="mb-2">
                    <input
                      type="text"
                      placeholder="Search OPD queue by name or token..."
                      value={opdSearch}
                      onChange={(e) => setOpdSearch(e.target.value)}
                      className="w-full text-xs px-3 py-2 border border-gray-300 rounded-lg focus:ring-1 focus:ring-rose-500"
                    />
                  </div>
                  <select
                    id="icu-admit-opd-select"
                    value={selectedOpdId}
                    onChange={(e) => {
                      const id = e.target.value;
                      setSelectedOpdId(id);
                      const found = opdPatients.find((o) => String(o.id) === String(id));
                      if (found?.problem) setPrimaryDiagnosis(found.problem);
                    }}
                    required
                    className="w-full text-sm px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-rose-500"
                  >
                    <option value="">
                      -- Choose OPD Patient ({filteredOpdPatients.length}) --
                    </option>
                    {filteredOpdPatients.map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.patientName || o.patient?.name} (OPD #{o.opdNumber || o.id})
                        {o.doctorName ? ` — Dr. ${o.doctorName}` : ''}
                      </option>
                    ))}
                  </select>

                  <div className="grid grid-cols-2 gap-3 mt-3">
                    <div>
                      <label
                        htmlFor="icu-admit-admission-type"
                        className="block text-xs font-medium text-gray-700 mb-1"
                      >
                        Admission Type
                      </label>
                      <select
                        id="icu-admit-admission-type"
                        value={admissionType}
                        onChange={(e) => setAdmissionType(e.target.value)}
                        className="w-full text-xs px-2.5 py-2 border border-gray-300 rounded-lg"
                      >
                        <option value="EMERGENCY">EMERGENCY</option>
                        <option value="ELECTIVE">ELECTIVE</option>
                      </select>
                    </div>
                    <div>
                      <label
                        htmlFor="icu-admit-primary-diagnosis"
                        className="block text-xs font-medium text-gray-700 mb-1"
                      >
                        Primary Diagnosis / Reason
                      </label>
                      <input
                        id="icu-admit-primary-diagnosis"
                        type="text"
                        placeholder="e.g. Acute respiratory failure"
                        value={primaryDiagnosis}
                        onChange={(e) => setPrimaryDiagnosis(e.target.value)}
                        className="w-full text-xs px-2.5 py-2 border border-gray-300 rounded-lg"
                      />
                    </div>
                  </div>
                </div>
              )}

              {/* ICU Destination Ward & Bed */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2 border-t border-gray-100">
                <div>
                  <label
                    htmlFor="icu-admit-target-ward"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Target ICU Ward <span className="text-rose-600">*</span>
                  </label>
                  <select
                    id="icu-admit-target-ward"
                    value={selectedIcuWardId}
                    onChange={(e) => {
                      if (e.target.value === selectedIcuWardId) return;
                      setSelectedBedId('');
                      setAvailableBeds([]);
                      setBedsWardId('');
                      setSelectedIcuWardId(e.target.value);
                    }}
                    required
                    className="w-full text-sm px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-rose-500"
                  >
                    <option value="">-- Select ICU Ward --</option>
                    {icuWards.map((w) => (
                      <option key={w.wardId} value={w.wardId}>
                        {w.wardName} {w.unitType ? `(${w.unitType})` : ''}
                      </option>
                    ))}
                  </select>
                  {icuWards.length === 0 && (
                    <p className="text-xs text-amber-600 mt-1">No dedicated ICU wards found.</p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor="icu-admit-target-bed"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Available ICU Bed <span className="text-rose-600">*</span>
                  </label>
                  <select
                    id="icu-admit-target-bed"
                    value={selectedBedId}
                    onChange={(e) => setSelectedBedId(e.target.value)}
                    required
                    disabled={!selectedIcuWardId || availableBeds.length === 0}
                    className="w-full text-sm px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-rose-500 disabled:bg-gray-100 disabled:text-gray-400"
                  >
                    <option value="">
                      {!selectedIcuWardId
                        ? '-- Choose ICU Ward First --'
                        : availableBeds.length === 0
                          ? '-- No Available Beds in this ICU --'
                          : `-- Select Available Bed (${availableBeds.length}) --`}
                    </option>
                    {availableBeds.map((b) => (
                      <option key={b.bedId} value={b.bedId}>
                        {b.bedCode || b.bedNumber || `Bed #${b.bedId}`} (Available)
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex justify-end gap-3 pt-4 border-t border-gray-100">
                <button
                  type="button"
                  onClick={onClose}
                  disabled={submitting}
                  className="px-4 py-2 text-xs font-semibold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-xl transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={
                    submitting ||
                    !validBed ||
                    (sourceType === 'IPD' ? !selectedIpdId : !selectedOpdId)
                  }
                  className="px-5 py-2 text-xs font-bold text-white bg-rose-600 hover:bg-rose-700 rounded-xl transition-colors shadow-md disabled:bg-gray-300 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  {submitting ? (
                    <>
                      <div className="animate-spin rounded-full h-3.5 w-3.5 border-b-2 border-white"></div>
                      Admitting...
                    </>
                  ) : (
                    'Confirm ICU Admission'
                  )}
                </button>
              </div>
            </>
          )}
        </form>
      </div>
    </div>
  );
};

export default IcuAdmitModal;
