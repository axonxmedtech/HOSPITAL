import React, { useEffect, useState } from 'react';
import { useToast } from '../context/ToastContext';
import hospitalService from '../services/hospitalService';
import icuService from '../services/icuService';
import wardService from '../services/wardService';

/**
 * IcuDischargeToIpdModal
 * Discharge / step-down patient from ICU into a General IPD Ward bed.
 */
const IcuDischargeToIpdModal = ({ isOpen, onClose, onSuccess, initialIcuPatient = null }) => {
  const { success, error: toastError } = useToast();

  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // ICU patients
  const [icuPatients, setIcuPatients] = useState([]);
  const [selectedIpdId, setSelectedIpdId] = useState('');
  const [patientSearch, setPatientSearch] = useState('');

  // General IPD wards & available beds
  const [generalWards, setGeneralWards] = useState([]);
  const [selectedWardId, setSelectedWardId] = useState('');
  const [availableBeds, setAvailableBeds] = useState([]);
  const [selectedBedId, setSelectedBedId] = useState('');

  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    const loadData = async () => {
      setLoading(true);
      try {
        const [patients, wards] = await Promise.all([
          icuService.getIcuPatients().catch(() => []),
          wardService.getWards().catch(() => []),
        ]);

        if (isMounted) {
          setIcuPatients(patients || []);
          setGeneralWards(wards || []);

          if (initialIcuPatient) {
            const initialId =
              initialIcuPatient.ipdId ||
              initialIcuPatient.id ||
              initialIcuPatient.admissionId ||
              initialIcuPatient.ipd?.id ||
              '';
            setSelectedIpdId(String(initialId));
          } else if (patients && patients.length === 1) {
            setSelectedIpdId(String(patients[0].ipdId || ''));
          }

          if (wards && wards.length === 1) {
            setSelectedWardId(String(wards[0].wardId));
          }
        }
      } catch (err) {
        console.error('Failed to load ICU step-down data', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    loadData();
    return () => {
      isMounted = false;
    };
  }, [isOpen, initialIcuPatient]);

  // Load available beds for chosen general ward
  useEffect(() => {
    if (!selectedWardId) {
      setAvailableBeds([]);
      setSelectedBedId('');
      return;
    }

    let isMounted = true;
    const loadBeds = async () => {
      try {
        const b = await wardService.getAvailableBeds(selectedWardId);
        if (isMounted) {
          const list = b || [];
          setAvailableBeds(list);
          if (list.length === 1) {
            setSelectedBedId(String(list[0].bedId));
          } else {
            setSelectedBedId('');
          }
        }
      } catch (err) {
        console.error('Failed to load beds for general ward', err);
        if (isMounted) setAvailableBeds([]);
      }
    };

    loadBeds();
    return () => {
      isMounted = false;
    };
  }, [selectedWardId]);

  if (!isOpen) return null;

  const filteredIcuPatients = (icuPatients || []).filter((p) => {
    if (!patientSearch.trim()) return true;
    const q = patientSearch.toLowerCase();
    const name = (p.patientName || '').toLowerCase();
    const ipdNo = (p.ipdNumber || '').toString().toLowerCase();
    const ward = (p.icuWardName || '').toLowerCase();
    return name.includes(q) || ipdNo.includes(q) || ward.includes(q);
  });

  const selectedPatientObj = (icuPatients || []).find(
    (p) => String(p.ipdId) === String(selectedIpdId)
  );

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!selectedIpdId) {
      toastError('Please select a patient in ICU');
      return;
    }
    if (!selectedWardId) {
      toastError('Please select a target General Ward');
      return;
    }
    if (!selectedBedId) {
      toastError('Please select an available bed in the general ward');
      return;
    }

    setSubmitting(true);
    try {
      // Step down to general ward bed (which closes the active IcuStay with DISP_WARD)
      await hospitalService.changeBed(Number(selectedIpdId), Number(selectedBedId));
      success('Patient successfully discharged from ICU and moved to General IPD ward');
      onSuccess && onSuccess();
      onClose();
    } catch (err) {
      console.error('Discharge to IPD failed', err);
      const data = err.response?.data;
      const msg =
        typeof data === 'string'
          ? data
          : data?.error || data?.message || 'Failed to discharge patient to IPD';
      toastError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-xl overflow-hidden animate-scale-in">
        {/* Header */}
        <div className="px-6 py-4 bg-gradient-to-r from-amber-600 to-amber-700 text-white flex justify-between items-center">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-white/10 rounded-lg">
              <svg
                className="w-5 h-5 text-amber-100"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"
                />
              </svg>
            </div>
            <div>
              <h3 className="text-lg font-bold">Discharge from ICU to IPD</h3>
              <p className="text-xs text-amber-100">
                Step-down patient transfer to General IPD ward
              </p>
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

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
          {loading ? (
            <div className="py-12 text-center text-sm text-gray-500">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-amber-600 mx-auto mb-2"></div>
              Loading patient and ward data...
            </div>
          ) : (
            <>
              {/* Select Patient */}
              {initialIcuPatient ? (
                <div>
                  <span className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1">
                    ICU Patient to Step-down / Discharge
                  </span>
                  <div className="p-3 bg-amber-50/70 border border-amber-200 rounded-xl flex items-center justify-between">
                    <div>
                      <div className="text-sm font-bold text-gray-900">
                        {initialIcuPatient.patientName ||
                          initialIcuPatient.patient?.name ||
                          'Patient'}
                      </div>
                      <div className="text-xs text-amber-900 mt-0.5">
                        IPD #{initialIcuPatient.ipdNumber || initialIcuPatient.id || ''} • Current
                        ICU: {initialIcuPatient.icuWardName || initialIcuPatient.wardName || 'ICU'}{' '}
                        {initialIcuPatient.bedNumber ? `(Bed ${initialIcuPatient.bedNumber})` : ''}
                      </div>
                    </div>
                    <span className="text-[11px] font-semibold text-amber-800 bg-amber-100 border border-amber-300 px-2.5 py-0.5 rounded-md">
                      Current ICU Patient
                    </span>
                  </div>
                </div>
              ) : (
                <div>
                  <label
                    htmlFor="icu-discharge-patient-select"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Select Patient in ICU <span className="text-rose-600">*</span>
                  </label>
                  {icuPatients.length > 3 && (
                    <div className="mb-2">
                      <input
                        type="text"
                        placeholder="Search ICU patient by name, IPD #..."
                        value={patientSearch}
                        onChange={(e) => setPatientSearch(e.target.value)}
                        className="w-full text-xs px-3 py-2 border border-gray-300 rounded-lg focus:ring-1 focus:ring-amber-500"
                      />
                    </div>
                  )}
                  <select
                    id="icu-discharge-patient-select"
                    value={selectedIpdId}
                    onChange={(e) => setSelectedIpdId(e.target.value)}
                    required
                    className="w-full text-sm px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500"
                  >
                    <option value="">
                      -- Choose Admitted ICU Patient ({filteredIcuPatients.length}) --
                    </option>
                    {filteredIcuPatients.map((p) => (
                      <option key={p.ipdId} value={p.ipdId}>
                        {p.patientName} (IPD #{p.ipdNumber}) — Current: {p.icuWardName} / Bed{' '}
                        {p.bedNumber}
                      </option>
                    ))}
                  </select>

                  {selectedPatientObj && (
                    <div className="mt-2 p-2.5 bg-amber-50/60 border border-amber-200 rounded-lg text-xs text-amber-900">
                      <strong>Current ICU:</strong> {selectedPatientObj.icuWardName} • Bed{' '}
                      {selectedPatientObj.bedNumber}
                      {selectedPatientObj.doctorName && (
                        <span className="ml-3">
                          <strong>Doctor:</strong> {selectedPatientObj.doctorName}
                        </span>
                      )}
                      {selectedPatientObj.intensivistName && (
                        <span className="ml-3">
                          <strong>Intensivist:</strong> {selectedPatientObj.intensivistName}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              )}

              {/* Target General IPD Ward and Bed */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2 border-t border-gray-100">
                <div>
                  <label
                    htmlFor="icu-discharge-ward-select"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Target General Ward <span className="text-rose-600">*</span>
                  </label>
                  <select
                    id="icu-discharge-ward-select"
                    value={selectedWardId}
                    onChange={(e) => setSelectedWardId(e.target.value)}
                    required
                    className="w-full text-sm px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500"
                  >
                    <option value="">-- Select General Ward --</option>
                    {generalWards.map((w) => (
                      <option key={w.wardId} value={w.wardId}>
                        {w.wardName} {w.wardType ? `(${w.wardType})` : ''}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label
                    htmlFor="icu-discharge-bed-select"
                    className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1"
                  >
                    Available Bed in Ward <span className="text-rose-600">*</span>
                  </label>
                  <select
                    id="icu-discharge-bed-select"
                    value={selectedBedId}
                    onChange={(e) => setSelectedBedId(e.target.value)}
                    required
                    disabled={!selectedWardId || availableBeds.length === 0}
                    className="w-full text-sm px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 disabled:bg-gray-100 disabled:text-gray-400"
                  >
                    <option value="">
                      {!selectedWardId
                        ? '-- Choose General Ward First --'
                        : availableBeds.length === 0
                          ? '-- No Available Beds in this Ward --'
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

              {/* Info notice */}
              <div className="p-3 bg-blue-50/60 border border-blue-100 rounded-xl text-xs text-blue-800 flex items-start gap-2">
                <svg
                  className="w-4 h-4 text-blue-600 shrink-0 mt-0.5"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                <span>
                  Confirming this transfer moves the patient out of critical care into the general
                  ward, automatically concluding their active ICU episode (marked as Step-down to
                  Ward).
                </span>
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
                  disabled={submitting || !selectedIpdId || !selectedWardId || !selectedBedId}
                  className="px-5 py-2 text-xs font-bold text-white bg-amber-600 hover:bg-amber-700 rounded-xl transition-colors shadow-md disabled:bg-gray-300 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  {submitting ? (
                    <>
                      <div className="animate-spin rounded-full h-3.5 w-3.5 border-b-2 border-white"></div>
                      Transferring...
                    </>
                  ) : (
                    'Discharge to IPD Ward'
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

export default IcuDischargeToIpdModal;
