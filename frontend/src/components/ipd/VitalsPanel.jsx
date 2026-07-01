import React, { useEffect, useState, useCallback } from "react";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const TEMP_METHODS = ["ORAL", "AXILLARY", "RECTAL", "TYMPANIC"];
const PULSE_RHYTHMS = ["REGULAR", "IRREGULAR", "BOUNDING", "WEAK"];
const BP_POSITIONS = ["SITTING", "STANDING", "LYING"];
const RESP_PATTERNS = ["NORMAL", "SHALLOW", "DEEP", "LABOURED", "CHEYNE_STOKES"];

const InputCls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.message || "An error occurred";

const emptyForm = {
    temperature: "", tempMethod: "ORAL",
    pulse: "", pulseRhythm: "REGULAR",
    bpSystolic: "", bpDiastolic: "", bpPosition: "SITTING",
    spo2: "", respiratoryRate: "", respPattern: "NORMAL",
    oxygenSupport: "", painScore: 0, weight: "", remarks: "",
};

// Returns 'critical' | 'warn' | null for a single reading's headline severity.
const readingAlert = (v) => {
    const sys = v.bpSystolic, dia = v.bpDiastolic, hr = v.pulse, spo2 = v.spo2;
    const temp = v.temperature != null ? Number(v.temperature) : null;
    if ((sys != null && sys > 140) || (dia != null && dia > 90) || (hr != null && hr > 100)
        || (temp != null && temp > 38.5) || (spo2 != null && spo2 < 94)) return "critical";
    if ((sys != null && sys >= 130) || (hr != null && hr >= 90)
        || (temp != null && temp >= 37.5) || (spo2 != null && spo2 < 96)) return "warn";
    return null;
};

const fmtTime = (ts) => {
    if (!ts) return "-";
    try { return new Date(ts).toLocaleString([], { dateStyle: "medium", timeStyle: "short" }); }
    catch { return ts; }
};

const Metric = ({ label, value, unit, flag }) => (
    <div className={`px-3 py-2 rounded-lg border text-center ${flag === "critical" ? "bg-red-50 border-red-300" : flag === "warn" ? "bg-yellow-50 border-yellow-300" : "bg-gray-50 border-gray-200"}`}>
        <div className="text-[10px] uppercase tracking-wide text-gray-500">{label}</div>
        <div className={`text-sm font-semibold ${flag === "critical" ? "text-red-700" : flag === "warn" ? "text-yellow-800" : "text-gray-800"}`}>
            {value != null && value !== "" ? value : "-"}{value != null && value !== "" && unit ? ` ${unit}` : ""}
        </div>
    </div>
);

const VitalsPanel = ({ admissionId, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canRecord = !isLocked && ["NURSE", "DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);

    const [vitals, setVitals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [modal, setModal] = useState({ open: false, saving: false });
    const [form, setForm] = useState(emptyForm);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await hospitalService.getVitalsByAdmission(admissionId);
            setVitals(Array.isArray(data) ? data : []);
        } catch { setVitals([]); } finally { setLoading(false); }
    }, [admissionId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, val) => setForm((p) => ({ ...p, [k]: val }));

    const handleSave = async () => {
        setModal((p) => ({ ...p, saving: true }));
        try {
            await hospitalService.recordVitals(admissionId, { ...form });
            success("Vitals recorded");
            setModal({ open: false, saving: false });
            setForm(emptyForm);
            load();
        } catch (e) {
            toastError(parseErr(e));
            setModal((p) => ({ ...p, saving: false }));
        }
    };

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <div>
                    <h3 className="text-lg font-semibold text-gray-800">Vital Signs</h3>
                    <p className="text-xs text-gray-500">Reverse-chronological timeline of all recorded readings</p>
                </div>
                {canRecord && (
                    <button
                        onClick={() => { setForm(emptyForm); setModal({ open: true, saving: false }); }}
                        className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-4 py-2 rounded-lg"
                    >
                        + Record Vitals
                    </button>
                )}
            </div>

            {loading ? (
                <div className="text-center text-gray-400 py-10 text-sm">Loading vitals…</div>
            ) : vitals.length === 0 ? (
                <div className="text-center text-gray-400 py-10 text-sm border border-dashed border-gray-200 rounded-lg">
                    No vitals recorded yet.
                </div>
            ) : (
                <div className="space-y-3">
                    {vitals.map((v) => {
                        const flag = readingAlert(v);
                        const bp = (v.bpSystolic != null || v.bpDiastolic != null)
                            ? `${v.bpSystolic ?? "-"}/${v.bpDiastolic ?? "-"}`
                            : (v.bloodPressure || null);
                        const bpFlag = (v.bpSystolic != null && v.bpSystolic > 140) || (v.bpDiastolic != null && v.bpDiastolic > 90)
                            ? "critical" : (v.bpSystolic != null && v.bpSystolic >= 130) ? "warn" : null;
                        return (
                            <div key={v.id} className={`rounded-xl border p-4 ${flag === "critical" ? "border-red-200 bg-red-50/40" : "border-gray-200 bg-white"}`}>
                                <div className="flex items-center justify-between mb-3">
                                    <span className="text-xs font-medium text-gray-600">🕒 {fmtTime(v.recordedAt)}</span>
                                    <div className="flex items-center gap-2">
                                        {flag === "critical" && <span className="text-[10px] font-semibold text-red-700 bg-red-100 px-2 py-0.5 rounded-full">⚠ ABNORMAL</span>}
                                        {v.recordedByName && <span className="text-[10px] text-gray-400">{v.recordedByName}</span>}
                                    </div>
                                </div>
                                <div className="grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-7 gap-2">
                                    <Metric label="BP" value={bp} unit="mmHg" flag={bpFlag} />
                                    <Metric label="Pulse" value={v.pulse} unit="bpm" flag={v.pulse != null && v.pulse > 100 ? "critical" : v.pulse != null && v.pulse >= 90 ? "warn" : null} />
                                    <Metric label="Temp" value={v.temperature} unit="°C" flag={v.temperature != null && Number(v.temperature) > 38.5 ? "critical" : v.temperature != null && Number(v.temperature) >= 37.5 ? "warn" : null} />
                                    <Metric label="SpO₂" value={v.spo2} unit="%" flag={v.spo2 != null && v.spo2 < 94 ? "critical" : v.spo2 != null && v.spo2 < 96 ? "warn" : null} />
                                    <Metric label="Resp" value={v.respiratoryRate} unit="/min" />
                                    <Metric label="Pain" value={v.painScore} unit="/10" flag={v.painScore != null && v.painScore >= 7 ? "critical" : v.painScore != null && v.painScore >= 4 ? "warn" : null} />
                                    <Metric label="Weight" value={v.weight} unit="kg" />
                                </div>
                                {(v.tempMethod || v.pulseRhythm || v.respPattern || v.bpPosition || v.oxygenSupport || v.remarks) && (
                                    <div className="mt-2 text-[11px] text-gray-500 flex flex-wrap gap-x-4 gap-y-1">
                                        {v.bpPosition && <span>Position: {v.bpPosition}</span>}
                                        {v.pulseRhythm && <span>Rhythm: {v.pulseRhythm}</span>}
                                        {v.tempMethod && <span>Temp method: {v.tempMethod}</span>}
                                        {v.respPattern && <span>Resp pattern: {v.respPattern}</span>}
                                        {v.oxygenSupport && <span>O₂: {v.oxygenSupport}</span>}
                                        {v.remarks && <span className="italic">“{v.remarks}”</span>}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            {modal.open && (
                <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                        <div className="px-6 py-4 border-b flex items-center justify-between sticky top-0 bg-white rounded-t-2xl">
                            <h3 className="text-lg font-semibold text-gray-800">Record Vitals</h3>
                            <button onClick={() => setModal({ open: false, saving: false })} className="text-gray-400 hover:text-gray-600 text-xl">×</button>
                        </div>
                        <div className="p-6 grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <div>
                                <label className="text-xs font-medium text-gray-600">Temperature (°C)</label>
                                <input type="number" step="0.1" className={InputCls} value={form.temperature} onChange={(e) => setField("temperature", e.target.value)} />
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">Temp Method</label>
                                <select className={InputCls} value={form.tempMethod} onChange={(e) => setField("tempMethod", e.target.value)}>
                                    {TEMP_METHODS.map((o) => <option key={o} value={o}>{o}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">Pulse Rate (bpm)</label>
                                <input type="number" className={InputCls} value={form.pulse} onChange={(e) => setField("pulse", e.target.value)} />
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">Pulse Rhythm</label>
                                <select className={InputCls} value={form.pulseRhythm} onChange={(e) => setField("pulseRhythm", e.target.value)}>
                                    {PULSE_RHYTHMS.map((o) => <option key={o} value={o}>{o}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">BP Systolic</label>
                                <input type="number" className={InputCls} value={form.bpSystolic} onChange={(e) => setField("bpSystolic", e.target.value)} />
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">BP Diastolic</label>
                                <input type="number" className={InputCls} value={form.bpDiastolic} onChange={(e) => setField("bpDiastolic", e.target.value)} />
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">BP Position</label>
                                <select className={InputCls} value={form.bpPosition} onChange={(e) => setField("bpPosition", e.target.value)}>
                                    {BP_POSITIONS.map((o) => <option key={o} value={o}>{o}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">SpO₂ (%)</label>
                                <input type="number" className={InputCls} value={form.spo2} onChange={(e) => setField("spo2", e.target.value)} />
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">Respiratory Rate (/min)</label>
                                <input type="number" className={InputCls} value={form.respiratoryRate} onChange={(e) => setField("respiratoryRate", e.target.value)} />
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">Resp Pattern</label>
                                <select className={InputCls} value={form.respPattern} onChange={(e) => setField("respPattern", e.target.value)}>
                                    {RESP_PATTERNS.map((o) => <option key={o} value={o}>{o}</option>)}
                                </select>
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">Oxygen Support</label>
                                <input type="text" placeholder="e.g. Room air, 2L NC" className={InputCls} value={form.oxygenSupport} onChange={(e) => setField("oxygenSupport", e.target.value)} />
                            </div>
                            <div>
                                <label className="text-xs font-medium text-gray-600">Weight (kg)</label>
                                <input type="number" step="0.1" className={InputCls} value={form.weight} onChange={(e) => setField("weight", e.target.value)} />
                            </div>
                            <div className="sm:col-span-2">
                                <label className="text-xs font-medium text-gray-600">Pain Score: <span className="font-semibold">{form.painScore}</span>/10</label>
                                <input type="range" min="0" max="10" className="w-full" value={form.painScore} onChange={(e) => setField("painScore", Number(e.target.value))} />
                            </div>
                            <div className="sm:col-span-2">
                                <label className="text-xs font-medium text-gray-600">Remarks</label>
                                <textarea rows={2} className={InputCls} value={form.remarks} onChange={(e) => setField("remarks", e.target.value)} />
                            </div>
                        </div>
                        <div className="px-6 py-4 border-t flex justify-end gap-3 sticky bottom-0 bg-white rounded-b-2xl">
                            <button onClick={() => setModal({ open: false, saving: false })} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancel</button>
                            <button onClick={handleSave} disabled={modal.saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
                                {modal.saving ? "Saving…" : "Save Vitals"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default VitalsPanel;
