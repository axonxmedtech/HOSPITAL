import React, { useState, useEffect, useCallback } from 'react';
import LoadingSpinner from '../../../components/LoadingSpinner';
import GiveExtraItem from '../../../components/stock/GiveExtraItem';
import { StockBadge } from '../../../components/stock/StockPicker';
import { useToast } from '../../../context/ToastContext';
import authService from '../../../services/authService';
import nurseService from '../../../services/nurseService';
import { extractApiError } from '../../../utils/apiError';
import { describeFoodTiming } from '../../../utils/foodTiming';

/**
 * MedicationPanel - the nurse's medication chart + administration recording.
 * Shows every doctor order (ACTIVE / STOPPED / COMPLETED — never deleted) with
 * its course progress and a "pending today" reminder while the course runs.
 * Recording is only allowed against ACTIVE orders.
 */
/**
 * readOnly forces view-only. Otherwise recording is shown to whoever the hospital allows
 * (Settings → who may record medicines; all clinical roles by default).
 */
const MedicationPanel = ({ admissionId, readOnly = false }) => {
  const { success, error: toastError } = useToast();
  const currentUser = authService.getCurrentUser();
  const currentUserId = currentUser?.userId;
  const isNurse = currentUser?.role === 'NURSE' || currentUser?.role === 'NURSE_INCHARGE';

  const [loading, setLoading] = useState(true);
  const [chart, setChart] = useState([]);
  const [history, setHistory] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  const [selectedPrescriptionId, setSelectedPrescriptionId] = useState('');
  const [status, setStatus] = useState('GIVEN');
  const [administeredTime, setAdministeredTime] = useState('');
  const [remarks, setRemarks] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [ownSupply, setOwnSupply] = useState(false);
  const [access, setAccess] = useState(null);
  const [showExtra, setShowExtra] = useState(false);
  const [undoing, setUndoing] = useState(null); // { publicId, reason }
  const canRecord = !readOnly && !!access?.canRecord;

  // Separate Nurse Login OFF ("Shared Login") -> a required "Performed By
  // Nurse" dropdown is shown and its selection is sent with the payload.
  const [separateLogin, setSeparateLogin] = useState(true);
  const [wardId, setWardId] = useState(null);
  const [nurses, setNurses] = useState([]);
  const [performedByNurseId, setPerformedByNurseId] = useState('');

  const activeMeds = chart.filter((c) => (c.status || '').toUpperCase() === 'ACTIVE');
  const selectedOrder = chart.find(
    (c) => String(c.prescriptionId) === String(selectedPrescriptionId)
  );
  // Orders made before stock sources existed carry only a medicine id: that is medicine stock.
  const stocked = !!(selectedOrder && (selectedOrder.stockSource || selectedOrder.medicineId));

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [chartData, histData] = await Promise.all([
        nurseService.getMedicationChart(admissionId),
        nurseService.getMedicationHistory(admissionId),
      ]);
      const c = Array.isArray(chartData) ? chartData : [];
      setChart(c);
      setHistory(Array.isArray(histData) ? histData : []);
      const firstActive = c.find((x) => (x.status || '').toUpperCase() === 'ACTIVE');
      setSelectedPrescriptionId(firstActive ? String(firstActive.prescriptionId) : '');
    } catch (err) {
      toastError('Failed to load medication details');
    } finally {
      setLoading(false);
    }
  }, [admissionId, toastError]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (readOnly) return undefined;
    let live = true;
    nurseService
      .getMedicationAccess()
      .then((a) => live && setAccess(a))
      .catch(() => live && setAccess({ canRecord: false }));
    return () => {
      live = false;
    };
  }, [readOnly]);

  // A new order selected: start from its quantity per dose, from stock.
  useEffect(() => {
    setQuantity(selectedOrder?.quantityPerDose || 1);
    setOwnSupply(false);
  }, [selectedPrescriptionId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Both only feed the nurse's "performed by" choice, and hit nurse-only endpoints.
  useEffect(() => {
    if (readOnly || !isNurse) return;
    let active = true;
    nurseService
      .getAdmissionForm(admissionId)
      .then((d) => {
        if (active) setWardId(d?.wardId ?? null);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [admissionId, readOnly, isNurse]);

  useEffect(() => {
    if (readOnly || !isNurse) return;
    let active = true;
    nurseService
      .getSeparateNurseLogin()
      .then((v) => {
        if (active) setSeparateLogin(v);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [readOnly, isNurse]);

  useEffect(() => {
    if (isNurse && separateLogin === false && wardId) {
      nurseService
        .getWardStaffNurses(wardId)
        .then((list) => setNurses(Array.isArray(list) ? list : []))
        .catch(() => setNurses([]));
    }
  }, [isNurse, separateLogin, wardId]);

  useEffect(() => {
    if (status === 'GIVEN' || status === 'DELAYED') {
      const now = new Date();
      setAdministeredTime(
        new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
      );
    } else {
      setAdministeredTime('');
    }
  }, [status]);

  const handleSubmit = async () => {
    if (!selectedPrescriptionId) {
      toastError('Please select a medicine');
      return;
    }
    if (isNurse && separateLogin === false && !performedByNurseId) {
      toastError('Select the nurse who performed this');
      return;
    }
    const payload = {
      ipdAdmissionId: admissionId,
      prescriptionId: Number(selectedPrescriptionId),
      status,
      remarks: remarks.trim() || null,
    };
    if (administeredTime) payload.administeredTime = new Date(administeredTime).toISOString();
    if (isNurse && separateLogin === false) payload.performedByNurseId = Number(performedByNurseId);
    if (status === 'GIVEN') {
      payload.quantity = Number(quantity) || 1;
      payload.ownSupply = ownSupply || !stocked;
    }

    setSubmitting(true);
    try {
      const saved = await nurseService.recordMedication(payload);
      let note = 'Medication recorded';
      if (status === 'GIVEN' && saved?.ownSupply)
        note = "Recorded as given from the patient's own supply";
      else if (status === 'GIVEN') note = 'Given — taken from stock and added to the bill';
      success(note);
      setRemarks('');
      setPerformedByNurseId('');
      loadData();
    } catch (err) {
      const msg = extractApiError(err, 'Failed to record medication');
      toastError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const confirmUndo = async () => {
    if (!undoing?.reason?.trim()) return toastError('Give a reason for undoing this entry');
    try {
      await nurseService.undoMedication(undoing.publicId, undoing.reason.trim());
      success('Entry undone — stock and bill corrected');
      setUndoing(null);
      loadData();
    } catch (err) {
      toastError(extractApiError(err, 'Could not undo this entry'));
    }
  };

  /** Where a GIVEN dose came from, for the history. */
  const givenFrom = (mar) => {
    if (mar.status !== 'GIVEN') return null;
    if (mar.ownSupply) return "Patient's own supply";
    if (!mar.stockSource) return null;
    const qty = mar.quantity ? `${mar.quantity} from stock` : 'From stock';
    const charge =
      mar.chargeAmount > 0 ? ` · ₹${Number(mar.chargeAmount).toLocaleString('en-IN')} billed` : '';
    return qty + charge;
  };

  const medName = (presId) => {
    const found = chart.find((p) => p.prescriptionId === presId);
    return found ? found.medicineName : `Prescription #${presId}`;
  };

  const fmt = (dt) =>
    dt
      ? new Date(dt).toLocaleString('en-IN', {
          day: '2-digit',
          month: 'short',
          hour: '2-digit',
          minute: '2-digit',
        })
      : '—';

  const marStatusColor = (s) => {
    switch (s) {
      case 'GIVEN':
        return 'bg-green-100 text-green-800';
      case 'SKIPPED':
        return 'bg-yellow-100 text-yellow-800';
      case 'DELAYED':
        return 'bg-orange-100 text-orange-800';
      case 'REFUSED':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const orderBadge = (c) => {
    const s = (c.status || '').toUpperCase();
    if (s === 'STOPPED')
      return (
        <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-red-100 text-red-700">
          STOPPED BY DOCTOR
        </span>
      );
    if (s === 'COMPLETED')
      return (
        <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-gray-200 text-gray-600">
          COMPLETED
        </span>
      );
    return (
      <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-green-100 text-green-700">
        ACTIVE
      </span>
    );
  };

  /**
   * What inventory knows about this order — advisory, never a reason to hide the medication.
   *
   * <p>"Not linked" and "no stock" are deliberately different answers. An order written as free
   * text has no inventory row attached to it, so the facility's stock level for it is unknown,
   * not zero. Showing those two states the same way told nurses a drug was unavailable when in
   * fact nobody had reconciled the order — a stock-keeping gap dressed up as a clinical one.
   */
  const stockBadge = (c) => {
    const status = c.inventoryStatus;
    if (status === 'LINKED_AVAILABLE' && c.lowStock) {
      return (
        <span
          className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-amber-100 text-amber-800"
          title="At or below the reorder level — ask for it to be reordered"
        >
          LOW STOCK · {c.availableQuantity}
        </span>
      );
    }
    if (status === 'LINKED_AVAILABLE') {
      return (
        <span
          className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-green-100 text-green-700"
          title={c.earliestExpiry ? `Earliest expiry ${c.earliestExpiry}` : undefined}
        >
          IN STOCK{typeof c.availableQuantity === 'number' ? ` · ${c.availableQuantity}` : ''}
        </span>
      );
    }
    if (status === 'LINKED_NO_STOCK') {
      return (
        <span
          className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-red-100 text-red-700"
          title="Every batch is empty, expired or withdrawn"
        >
          NO USABLE STOCK
        </span>
      );
    }
    return (
      <span
        className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-gray-100 text-gray-600"
        title="Not in the hospital's stock — the patient brings it from a medical store"
      >
        PATIENT BRINGS
      </span>
    );
  };

  const courseText = (c) => {
    if (c.dayOfCourse == null) return c.duration || '—';
    if (c.durationDays) {
      const day = Math.min(c.dayOfCourse, c.durationDays);
      return `Day ${day} of ${c.durationDays}`;
    }
    return c.duration || `Day ${c.dayOfCourse}`;
  };

  if (loading) return <LoadingSpinner />;

  return (
    <div className="space-y-5">
      {/* Record Section (ACTIVE orders only) — hidden for read-only viewers (e.g. doctors) */}
      {canRecord && showExtra && (
        <GiveExtraItem
          admissionId={admissionId}
          needsNurse={isNurse && separateLogin === false}
          performedByNurseId={performedByNurseId}
          onCancel={() => setShowExtra(false)}
          onDone={() => {
            setShowExtra(false);
            loadData();
          }}
        />
      )}
      {canRecord && (
        <div className="bg-white border border-gray-200 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-bold text-gray-800 text-sm">Record Medication Administration</h3>
            {!showExtra && (
              <button
                type="button"
                onClick={() => setShowExtra(true)}
                className="px-3 py-1 text-xs font-semibold rounded-lg bg-sky-50 text-sky-700 hover:bg-sky-100"
              >
                + Give extra item
              </button>
            )}
          </div>
          {activeMeds.length === 0 ? (
            <p className="text-sm text-gray-500">
              No active prescriptions available to administer.
            </p>
          ) : (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="fld-169" className="block text-xs font-medium text-gray-600 mb-1">
                    Select Medicine
                  </label>
                  <select
                    id="fld-169"
                    value={selectedPrescriptionId}
                    onChange={(e) => setSelectedPrescriptionId(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    {activeMeds.map((p) => (
                      <option key={p.prescriptionId} value={p.prescriptionId}>
                        {p.medicineName} ({p.dosage || 'No dose'} · {p.route || 'Oral'})
                        {p.pending ? ' — pending today' : ''}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="fld-168" className="block text-xs font-medium text-gray-600 mb-1">
                    Status
                  </label>
                  <select
                    id="fld-168"
                    value={status}
                    onChange={(e) => setStatus(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    <option value="GIVEN">GIVEN</option>
                    <option value="SKIPPED">SKIPPED</option>
                    <option value="DELAYED">DELAYED</option>
                    <option value="REFUSED">REFUSED</option>
                    <option value="NOT_AVAILABLE">NOT AVAILABLE</option>
                  </select>
                </div>
                <div>
                  <label htmlFor="fld-167" className="block text-xs font-medium text-gray-600 mb-1">
                    Administered Time
                  </label>
                  <input
                    id="fld-167"
                    type="datetime-local"
                    value={administeredTime}
                    onChange={(e) => setAdministeredTime(e.target.value)}
                    disabled={status !== 'GIVEN' && status !== 'DELAYED'}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:bg-gray-100 disabled:text-gray-400"
                  />
                </div>
              </div>
              <div>
                <label htmlFor="fld-166" className="block text-xs font-medium text-gray-600 mb-1">
                  Remarks
                </label>
                <input
                  id="fld-166"
                  type="text"
                  value={remarks}
                  onChange={(e) => setRemarks(e.target.value)}
                  placeholder="e.g., Tolerated well, refused due to nausea, etc."
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                />
              </div>
              {status === 'GIVEN' && selectedOrder && (
                <div className="flex flex-wrap items-end gap-4 bg-gray-50 border border-gray-100 rounded-lg p-3">
                  <label className="text-xs font-medium text-gray-600">
                    Quantity
                    <input
                      type="number"
                      min="1"
                      value={quantity}
                      onChange={(e) => setQuantity(e.target.value)}
                      className="mt-1 block w-24 px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    />
                  </label>
                  {stocked ? (
                    <>
                      <label className="flex items-center gap-2 text-sm text-gray-700 pb-2">
                        <input
                          type="checkbox"
                          checked={ownSupply}
                          onChange={(e) => setOwnSupply(e.target.checked)}
                        />
                        Patient&apos;s own supply
                      </label>
                      <p className="text-xs text-gray-600 pb-2 flex items-center gap-2">
                        {ownSupply ? (
                          'Recorded only — nothing taken from stock or billed.'
                        ) : (
                          <>
                            From{' '}
                            {selectedOrder.stockSource === 'HOSPITAL_ITEM'
                              ? 'hospital'
                              : 'medicine'}{' '}
                            inventory
                            <StockBadge
                              option={{
                                available: selectedOrder.availableQuantity ?? 0,
                                low: selectedOrder.lowStock,
                                outOfStock: (selectedOrder.availableQuantity ?? 0) <= 0,
                              }}
                            />
                            {selectedOrder.unitPrice != null &&
                              `· ₹${Number(selectedOrder.unitPrice).toLocaleString('en-IN')} each, added to the bill`}
                          </>
                        )}
                      </p>
                    </>
                  ) : (
                    <p className="text-xs text-gray-600 pb-2">
                      Not in the hospital&apos;s stock — recorded as the patient&apos;s own supply.
                    </p>
                  )}
                </div>
              )}
              {isNurse && separateLogin === false && (
                <div>
                  <label htmlFor="fld-165" className="block text-xs font-medium text-gray-600 mb-1">
                    Performed By Nurse <span className="text-red-600">*</span>
                  </label>
                  <select
                    id="fld-165"
                    value={performedByNurseId}
                    onChange={(e) => setPerformedByNurseId(e.target.value)}
                    required
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    <option value="">Select nurse…</option>
                    {nurses.map((n) => (
                      <option key={n.id} value={n.id}>
                        {n.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}
              <div className="flex justify-end pt-2">
                <button
                  onClick={handleSubmit}
                  disabled={submitting}
                  className={`px-4 py-2 text-sm font-semibold text-white rounded-lg ${submitting ? 'bg-gray-400 cursor-not-allowed' : 'bg-gray-900 hover:bg-gray-800'}`}
                >
                  {submitting ? 'Saving…' : 'Record Administration'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Medication Chart — all orders with status + course reminders */}
      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
        <div className="px-5 py-3 border-b border-gray-100">
          <h3 className="font-bold text-gray-800 text-sm">Medication Chart</h3>
        </div>
        {chart.length === 0 ? (
          <p className="px-5 py-6 text-sm text-gray-500">No prescriptions yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr className="text-left text-xs font-semibold text-gray-500 border-b border-gray-200">
                  <th className="px-5 py-3">MEDICINE</th>
                  <th className="px-5 py-3">DOSE</th>
                  <th className="px-5 py-3">FREQ</th>
                  <th className="px-5 py-3">ROUTE</th>
                  <th className="px-5 py-3">COURSE</th>
                  <th className="px-5 py-3">STATUS</th>
                  <th className="px-5 py-3">STOCK</th>
                  <th className="px-5 py-3">TODAY</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {chart.map((c) => {
                  const stopped = (c.status || '').toUpperCase() === 'STOPPED';
                  return (
                    <tr key={c.prescriptionId} className={stopped ? 'bg-red-50/40' : ''}>
                      <td className="px-5 py-3">
                        <div
                          className={`font-medium ${stopped ? 'text-gray-500 line-through' : 'text-gray-900'}`}
                        >
                          {c.medicineName}
                        </div>
                        {describeFoodTiming(c.foodTiming) && (
                          <div className="text-[11px] text-gray-500">
                            {describeFoodTiming(c.foodTiming)}
                          </div>
                        )}
                        {c.instructions && (
                          <div className="text-[11px] text-gray-400 italic">{c.instructions}</div>
                        )}
                      </td>
                      <td className="px-5 py-3 text-gray-600">{c.dosage || '—'}</td>
                      <td className="px-5 py-3 text-gray-600">{c.frequency || '—'}</td>
                      <td className="px-5 py-3 text-gray-600">
                        <span className="px-2 py-0.5 text-xs font-medium bg-gray-100 text-gray-800 rounded">
                          {c.route || 'ORAL'}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-gray-600">{courseText(c)}</td>
                      <td className="px-5 py-3">{orderBadge(c)}</td>
                      <td className="px-5 py-3">{stockBadge(c)}</td>
                      <td className="px-5 py-3">
                        {c.pending ? (
                          <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-amber-100 text-amber-700">
                            PENDING TODAY
                          </span>
                        ) : c.courseActive && c.administeredToday ? (
                          <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-green-100 text-green-700">
                            GIVEN TODAY
                          </span>
                        ) : (
                          <span className="text-gray-300">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="px-5 py-2 text-[11px] text-gray-400 border-t border-gray-100">
          Stopped medicines are kept for history — the doctor asked to stop them; they are not
          deleted.
        </p>
      </div>

      {/* MAR Timeline */}
      <div className="bg-white border border-gray-200 rounded-xl">
        <div className="px-5 py-3 border-b border-gray-100">
          <h3 className="font-bold text-gray-800 text-sm">
            Medication Administration History (MAR)
          </h3>
        </div>
        {history.length === 0 ? (
          <p className="px-5 py-6 text-sm text-gray-500">No administration records yet.</p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {history.map((mar) => (
              <li key={mar.publicId || mar.id} className="px-5 py-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-gray-900">
                      {medName(mar.prescriptionId)}
                    </span>
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-semibold ${marStatusColor(mar.status)}`}
                    >
                      {mar.status}
                    </span>
                  </div>
                  <div className="text-xs text-gray-500">
                    Administered: <span className="font-medium">{fmt(mar.administeredTime)}</span>
                    {mar.nurseUserId && (
                      <span className="ml-2 font-normal text-gray-400">
                        (By Nurse{' '}
                        {mar.nurseUserId === currentUserId ? 'Me' : `ID: ${mar.nurseUserId}`})
                      </span>
                    )}
                  </div>
                  {givenFrom(mar) && <div className="text-xs text-gray-600">{givenFrom(mar)}</div>}
                  {mar.remarks && (
                    <p className="text-xs text-gray-600 bg-gray-50 p-2 rounded mt-2 border border-gray-100 italic">
                      Remarks: {mar.remarks}
                    </p>
                  )}
                  {canRecord && mar.publicId && undoing?.publicId !== mar.publicId && (
                    <button
                      type="button"
                      onClick={() => setUndoing({ publicId: mar.publicId, reason: '' })}
                      className="text-xs font-semibold text-red-700 hover:underline"
                    >
                      Undo
                    </button>
                  )}
                  {undoing?.publicId === mar.publicId && (
                    <div className="flex flex-wrap items-center gap-2 mt-2">
                      <input
                        aria-label="Reason for undoing"
                        value={undoing.reason}
                        onChange={(e) => setUndoing({ ...undoing, reason: e.target.value })}
                        placeholder="Why? e.g. entered on the wrong patient"
                        className="flex-1 min-w-[200px] px-2 py-1 border border-gray-300 rounded text-xs"
                      />
                      <button
                        type="button"
                        onClick={confirmUndo}
                        className="px-2 py-1 text-xs font-semibold rounded bg-red-600 text-white"
                      >
                        Undo entry
                      </button>
                      <button
                        type="button"
                        onClick={() => setUndoing(null)}
                        className="px-2 py-1 text-xs rounded border border-gray-300"
                      >
                        Keep
                      </button>
                    </div>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default MedicationPanel;
