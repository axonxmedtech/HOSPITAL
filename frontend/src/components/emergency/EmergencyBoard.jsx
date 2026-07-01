import React, { useEffect, useState, useCallback } from "react";
import emergencyService from "../../services/emergencyService";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const TRIAGE = ["RED", "ORANGE", "YELLOW", "GREEN", "BLACK"];
const TRIAGE_STYLE = {
    RED: "bg-red-600 text-white", ORANGE: "bg-orange-500 text-white", YELLOW: "bg-yellow-400 text-gray-900",
    GREEN: "bg-green-500 text-white", BLACK: "bg-gray-900 text-white",
};
const TRIAGE_ORDER = { RED: 0, ORANGE: 1, YELLOW: 2, GREEN: 3, BLACK: 4 };
const ARRIVALS = ["WALK_IN", "AMBULANCE", "POLICE", "REFERRAL", "OTHER"];
const DISPOSITIONS = ["ADMIT", "ICU", "OT", "DISCHARGE", "REFER", "DEATH"];

/** Emergency priority board (Form 12): register -> triage -> assess -> dispose. */
const EmergencyBoard = () => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const isClinician = ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);
    const canTriage = ["DOCTOR", "NURSE", "HOSPITAL_ADMIN"].includes(user?.role);

    const [visits, setVisits] = useState([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    // Register modal
    const [regOpen, setRegOpen] = useState(false);
    const [reg, setReg] = useState({ unknownPatient: true, unknownLabel: "", gender: "OTHER", approximateAge: "", patientId: "", arrivalMode: "AMBULANCE", isMlc: false, mlcNumber: "" });

    // Per-visit action modal: { visit, mode: 'assess'|'dispose' }
    const [action, setAction] = useState(null);
    const [assessForm, setAssessForm] = useState({ chiefComplaint: "", airwayStatus: "PATENT", breathingStatus: "NORMAL", circulationStatus: "NORMAL", gcsScore: 15, initialDiagnosis: "" });
    const [dispForm, setDispForm] = useState({ disposition: "ADMIT", doctorId: "", wardId: "", bedId: "" });
    const [doctors, setDoctors] = useState([]);
    const [beds, setBeds] = useState([]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await emergencyService.getActiveVisits();
            setVisits(Array.isArray(res?.data) ? res.data : []);
        } catch { setVisits([]); } finally { setLoading(false); }
    }, []);

    useEffect(() => { load(); }, [load]);

    const loadPickers = async () => {
        try {
            const d = await hospitalService.getDoctors("", 0, 100);
            setDoctors(d?.content || []);
        } catch { setDoctors([]); }
        try {
            const b = await hospitalService.getAvailableBeds();
            setBeds(Array.isArray(b) ? b : []);
        } catch { setBeds([]); }
    };

    const register = async () => {
        setSaving(true);
        try {
            const payload = { ...reg, approximateAge: reg.approximateAge ? Number(reg.approximateAge) : null, patientId: reg.patientId ? Number(reg.patientId) : null };
            await emergencyService.registerVisit(payload);
            success("Emergency arrival registered");
            setRegOpen(false);
            setReg({ unknownPatient: true, unknownLabel: "", gender: "OTHER", approximateAge: "", patientId: "", arrivalMode: "AMBULANCE", isMlc: false, mlcNumber: "" });
            load();
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const doTriage = async (visit, level) => {
        try {
            await emergencyService.triage(visit.id, { triageLevel: level });
            success(`Triaged ${level}`);
            load();
        } catch (e) { toastError(parseErr(e)); }
    };

    const doAssess = async () => {
        setSaving(true);
        try {
            await emergencyService.assess(action.visit.id, { ...assessForm, gcsScore: Number(assessForm.gcsScore) });
            success("Assessment recorded");
            setAction(null);
            load();
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const doDispose = async () => {
        setSaving(true);
        try {
            const needsBed = dispForm.disposition === "ADMIT" || dispForm.disposition === "ICU";
            await emergencyService.dispose(action.visit.id, {
                disposition: dispForm.disposition,
                doctorId: needsBed && dispForm.doctorId ? Number(dispForm.doctorId) : null,
                wardId: needsBed && dispForm.wardId ? Number(dispForm.wardId) : null,
                bedId: needsBed && dispForm.bedId ? Number(dispForm.bedId) : null,
            });
            success("Patient disposed: " + dispForm.disposition);
            setAction(null);
            load();
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const sorted = [...visits].sort((a, b) => (TRIAGE_ORDER[a.triageLevel] ?? 9) - (TRIAGE_ORDER[b.triageLevel] ?? 9));

    const selectedBed = beds.find((b) => String(b.id) === String(dispForm.bedId));

    return (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-5">
                <div>
                    <h3 className="text-lg font-bold text-gray-900">🚨 Emergency Priority Board</h3>
                    <p className="text-xs text-gray-500 mt-1">Treatment first — unknown arrivals get an immediate temporary identity; triage before assessment.</p>
                </div>
                <button onClick={() => setRegOpen(true)} className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white font-semibold text-sm rounded-xl shadow-sm self-start">
                    + Register Arrival
                </button>
            </div>

            {loading ? (
                <div className="text-center py-8 text-gray-400 text-sm">Loading emergency board…</div>
            ) : sorted.length === 0 ? (
                <div className="text-center py-10 text-gray-400 italic text-sm">No active emergency patients.</div>
            ) : (
                <div className="space-y-3">
                    {sorted.map((v) => (
                        <div key={v.id} className={`border rounded-xl p-4 ${v.triageLevel === "RED" ? "border-red-300 bg-red-50/40" : "border-gray-200"}`}>
                            <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="flex items-center gap-2">
                                    <span className="font-bold text-sm text-gray-900">{v.emergencyNumber}</span>
                                    {v.triageLevel ? (
                                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${TRIAGE_STYLE[v.triageLevel]}`}>{v.triageLevel}</span>
                                    ) : (
                                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 border border-dashed border-gray-300">UNTRIAGED</span>
                                    )}
                                    {v.isMlc && <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-purple-100 text-purple-700">⚖ MLC</span>}
                                    <span className="text-[10px] text-gray-400">{v.arrivalMode} · {v.arrivalTime ? new Date(v.arrivalTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "-"}</span>
                                </div>
                                <div className="flex items-center gap-1.5">
                                    {canTriage && TRIAGE.map((t) => (
                                        <button key={t} onClick={() => doTriage(v, t)} title={`Triage ${t}`}
                                            className={`w-6 h-6 rounded-full text-[9px] font-bold ${TRIAGE_STYLE[t]} ${v.triageLevel === t ? "ring-2 ring-offset-1 ring-gray-400" : "opacity-60 hover:opacity-100"}`}>
                                            {t[0]}
                                        </button>
                                    ))}
                                    {isClinician && (
                                        <>
                                            <button onClick={() => { setAssessForm({ chiefComplaint: v.chiefComplaint || "", airwayStatus: v.airwayStatus || "PATENT", breathingStatus: v.breathingStatus || "NORMAL", circulationStatus: v.circulationStatus || "NORMAL", gcsScore: v.gcsScore ?? 15, initialDiagnosis: v.initialDiagnosis || "" }); setAction({ visit: v, mode: "assess" }); }}
                                                className="ml-2 text-[10px] border border-gray-300 hover:bg-gray-50 text-gray-700 px-2 py-1 rounded-lg font-semibold">🩺 Assess</button>
                                            <button onClick={() => { setDispForm({ disposition: "ADMIT", doctorId: "", wardId: "", bedId: "" }); setAction({ visit: v, mode: "dispose" }); loadPickers(); }}
                                                className="text-[10px] border border-gray-300 hover:bg-gray-50 text-gray-700 px-2 py-1 rounded-lg font-semibold">➡ Dispose</button>
                                        </>
                                    )}
                                </div>
                            </div>
                            {(v.chiefComplaint || v.gcsScore != null) && (
                                <div className="mt-2 text-xs text-gray-600 flex flex-wrap gap-x-4">
                                    {v.chiefComplaint && <span>C/O: {v.chiefComplaint}</span>}
                                    {v.gcsScore != null && <span>GCS: {v.gcsScore}</span>}
                                    {v.initialDiagnosis && <span>Dx: {v.initialDiagnosis}</span>}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {/* Register modal */}
            {regOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6">
                        <h3 className="text-base font-bold text-gray-900 mb-4">🚨 Register Emergency Arrival</h3>
                        <label className="flex items-center gap-2 text-xs text-gray-700 mb-3">
                            <input type="checkbox" checked={reg.unknownPatient} onChange={(e) => setReg((p) => ({ ...p, unknownPatient: e.target.checked }))} />
                            Unknown / unregistered patient (treat first — temporary identity created now)
                        </label>
                        <div className="grid grid-cols-2 gap-3">
                            {reg.unknownPatient ? (
                                <>
                                    <div className="col-span-2">
                                        <label className="text-[11px] font-medium text-gray-600">Label</label>
                                        <input type="text" placeholder="e.g. Unknown Male ~40, RTA" className={InputCls} value={reg.unknownLabel} onChange={(e) => setReg((p) => ({ ...p, unknownLabel: e.target.value }))} />
                                    </div>
                                    <div>
                                        <label className="text-[11px] font-medium text-gray-600">Gender</label>
                                        <select className={InputCls} value={reg.gender} onChange={(e) => setReg((p) => ({ ...p, gender: e.target.value }))}>
                                            <option>MALE</option><option>FEMALE</option><option>OTHER</option>
                                        </select>
                                    </div>
                                    <div>
                                        <label className="text-[11px] font-medium text-gray-600">Approx. Age</label>
                                        <input type="number" className={InputCls} value={reg.approximateAge} onChange={(e) => setReg((p) => ({ ...p, approximateAge: e.target.value }))} />
                                    </div>
                                </>
                            ) : (
                                <div className="col-span-2">
                                    <label className="text-[11px] font-medium text-gray-600">Patient ID</label>
                                    <input type="number" className={InputCls} value={reg.patientId} onChange={(e) => setReg((p) => ({ ...p, patientId: e.target.value }))} />
                                </div>
                            )}
                            <div>
                                <label className="text-[11px] font-medium text-gray-600">Arrival Mode</label>
                                <select className={InputCls} value={reg.arrivalMode} onChange={(e) => setReg((p) => ({ ...p, arrivalMode: e.target.value }))}>
                                    {ARRIVALS.map((a) => <option key={a}>{a}</option>)}
                                </select>
                            </div>
                            <div className="flex items-end gap-2">
                                <label className="flex items-center gap-1.5 text-xs text-gray-700">
                                    <input type="checkbox" checked={reg.isMlc} onChange={(e) => setReg((p) => ({ ...p, isMlc: e.target.checked }))} />
                                    Medico-Legal Case
                                </label>
                            </div>
                            {reg.isMlc && (
                                <div className="col-span-2">
                                    <label className="text-[11px] font-medium text-gray-600">MLC / FIR Number</label>
                                    <input type="text" className={InputCls} value={reg.mlcNumber} onChange={(e) => setReg((p) => ({ ...p, mlcNumber: e.target.value }))} />
                                </div>
                            )}
                        </div>
                        <div className="flex justify-end gap-2 mt-4">
                            <button onClick={() => setRegOpen(false)} className="text-xs text-gray-500 px-3 py-2">Cancel</button>
                            <button onClick={register} disabled={saving} className="bg-red-600 hover:bg-red-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                {saving ? "…" : "Register Arrival"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Assess modal */}
            {action?.mode === "assess" && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6">
                        <h3 className="text-base font-bold text-gray-900 mb-4">🩺 Primary Assessment — {action.visit.emergencyNumber}</h3>
                        <div className="grid grid-cols-2 gap-3">
                            <div className="col-span-2">
                                <label className="text-[11px] font-medium text-gray-600">Chief Complaint</label>
                                <input type="text" className={InputCls} value={assessForm.chiefComplaint} onChange={(e) => setAssessForm((p) => ({ ...p, chiefComplaint: e.target.value }))} />
                            </div>
                            <div>
                                <label className="text-[11px] font-medium text-gray-600">Airway</label>
                                <select className={InputCls} value={assessForm.airwayStatus} onChange={(e) => setAssessForm((p) => ({ ...p, airwayStatus: e.target.value }))}>
                                    <option>PATENT</option><option>THREATENED</option><option>OBSTRUCTED</option>
                                </select>
                            </div>
                            <div>
                                <label className="text-[11px] font-medium text-gray-600">Breathing</label>
                                <select className={InputCls} value={assessForm.breathingStatus} onChange={(e) => setAssessForm((p) => ({ ...p, breathingStatus: e.target.value }))}>
                                    <option>NORMAL</option><option>LABOURED</option><option>SHALLOW</option><option>ABSENT</option>
                                </select>
                            </div>
                            <div>
                                <label className="text-[11px] font-medium text-gray-600">Circulation</label>
                                <select className={InputCls} value={assessForm.circulationStatus} onChange={(e) => setAssessForm((p) => ({ ...p, circulationStatus: e.target.value }))}>
                                    <option>NORMAL</option><option>HYPOTENSIVE</option><option>SHOCK</option>
                                </select>
                            </div>
                            <div>
                                <label className="text-[11px] font-medium text-gray-600">GCS (3–15): {assessForm.gcsScore}</label>
                                <input type="range" min="3" max="15" className="w-full" value={assessForm.gcsScore} onChange={(e) => setAssessForm((p) => ({ ...p, gcsScore: Number(e.target.value) }))} />
                            </div>
                            <div className="col-span-2">
                                <label className="text-[11px] font-medium text-gray-600">Initial Diagnosis</label>
                                <textarea rows={2} className={InputCls} value={assessForm.initialDiagnosis} onChange={(e) => setAssessForm((p) => ({ ...p, initialDiagnosis: e.target.value }))} />
                            </div>
                        </div>
                        <div className="flex justify-end gap-2 mt-4">
                            <button onClick={() => setAction(null)} className="text-xs text-gray-500 px-3 py-2">Cancel</button>
                            <button onClick={doAssess} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                {saving ? "…" : "Save Assessment"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Dispose modal */}
            {action?.mode === "dispose" && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6">
                        <h3 className="text-base font-bold text-gray-900 mb-4">➡ Disposition — {action.visit.emergencyNumber}</h3>
                        <div className="grid grid-cols-1 gap-3">
                            <div>
                                <label className="text-[11px] font-medium text-gray-600">Disposition</label>
                                <select className={InputCls} value={dispForm.disposition} onChange={(e) => setDispForm((p) => ({ ...p, disposition: e.target.value }))}>
                                    {DISPOSITIONS.map((d) => <option key={d}>{d}</option>)}
                                </select>
                            </div>
                            {(dispForm.disposition === "ADMIT" || dispForm.disposition === "ICU") && (
                                <>
                                    <div>
                                        <label className="text-[11px] font-medium text-gray-600">Admitting Doctor *</label>
                                        <select className={InputCls} value={dispForm.doctorId} onChange={(e) => setDispForm((p) => ({ ...p, doctorId: e.target.value }))}>
                                            <option value="">Select…</option>
                                            {doctors.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                                        </select>
                                    </div>
                                    <div>
                                        <label className="text-[11px] font-medium text-gray-600">Bed *</label>
                                        <select className={InputCls} value={dispForm.bedId} onChange={(e) => {
                                            const bed = beds.find((b) => String(b.id) === e.target.value);
                                            setDispForm((p) => ({ ...p, bedId: e.target.value, wardId: bed?.wardId ?? bed?.ward?.id ?? p.wardId }));
                                        }}>
                                            <option value="">Select…</option>
                                            {beds.map((b) => <option key={b.id} value={b.id}>{b.wardName || b.ward?.name || "Ward"} — {b.bedNumber || b.bedCode || ("Bed " + b.id)}</option>)}
                                        </select>
                                        {selectedBed && !(selectedBed.wardId || selectedBed.ward?.id) && (
                                            <input type="number" placeholder="Ward ID" className={`${InputCls} mt-2`} value={dispForm.wardId} onChange={(e) => setDispForm((p) => ({ ...p, wardId: e.target.value }))} />
                                        )}
                                    </div>
                                </>
                            )}
                        </div>
                        <div className="flex justify-end gap-2 mt-4">
                            <button onClick={() => setAction(null)} className="text-xs text-gray-500 px-3 py-2">Cancel</button>
                            <button onClick={doDispose} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                {saving ? "…" : "Confirm Disposition"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default EmergencyBoard;
