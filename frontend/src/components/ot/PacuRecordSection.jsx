import React, { useEffect, useState, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const ALDRETE_MIN = 9;
const CONSCIOUSNESS = ["FULLY_AWAKE", "AROUSABLE", "UNRESPONSIVE"];
const ORIENTATION = ["ORIENTED", "CONFUSED", "DISORIENTED"];
const AIRWAY = ["PATENT", "ASSISTED", "INTUBATED"];
const BREATHING = ["NORMAL", "SHALLOW", "LABOURED", "APNEIC"];
const CIRCULATION = ["NORMAL", "HYPOTENSIVE", "HYPERTENSIVE"];
const NAUSEA = ["NONE", "MILD", "MODERATE", "SEVERE"];
const DESTINATIONS = ["WARD", "ICU", "HDU", "RE_EXPLORATION"];

const ALDRETE_KEYS = [
    ["aldreteActivity", "Activity"],
    ["aldreteRespiration", "Respiration"],
    ["aldreteCirculation", "Circulation"],
    ["aldreteConsciousness", "Consciousness"],
    ["aldreteOxygen", "SpO₂"],
];

const emptyForm = {
    recoveryBed: "", consciousness: "", orientation: "", airwayStatus: "",
    breathingStatus: "", circulationStatus: "", nauseaSeverity: "", vomitingPresent: false,
    painScore: 0, aldreteActivity: 0, aldreteRespiration: 0, aldreteCirculation: 0,
    aldreteConsciousness: 0, aldreteOxygen: 0, transferDestination: "", handoverNotes: "",
};

const PacuRecordSection = ({ admissionId, bookingId, bookingStatus, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWrite = !isLocked && ["DOCTOR", "NURSE", "HOSPITAL_ADMIN"].includes(user?.role);
    const canTransfer = !isLocked && ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);

    const [record, setRecord] = useState(null);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState(emptyForm);

    const hydrate = (d) => setForm({
        recoveryBed: d.recoveryBed || "", consciousness: d.consciousness || "", orientation: d.orientation || "",
        airwayStatus: d.airwayStatus || "", breathingStatus: d.breathingStatus || "", circulationStatus: d.circulationStatus || "",
        nauseaSeverity: d.nauseaSeverity || "", vomitingPresent: !!d.vomitingPresent, painScore: d.painScore ?? 0,
        aldreteActivity: d.aldreteActivity ?? 0, aldreteRespiration: d.aldreteRespiration ?? 0,
        aldreteCirculation: d.aldreteCirculation ?? 0, aldreteConsciousness: d.aldreteConsciousness ?? 0,
        aldreteOxygen: d.aldreteOxygen ?? 0, transferDestination: d.transferDestination || "", handoverNotes: d.handoverNotes || "",
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getPacuRecord(admissionId, bookingId);
            const data = res?.data || null;
            setRecord(data);
            if (data) hydrate(data);
            setEditing(false);
        } catch { setRecord(null); } finally { setLoading(false); }
    }, [admissionId, bookingId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, v) => setForm((p) => ({ ...p, [k]: v }));
    const isTransferred = record?.status === "TRANSFERRED";
    const liveAldrete = ALDRETE_KEYS.reduce((sum, [k]) => sum + (Number(form[k]) || 0), 0);

    const start = async () => {
        setSaving(true);
        try {
            const res = await otService.startPacuRecord(admissionId, bookingId, { ...form });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("Recovery record started");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const update = async () => {
        setSaving(true);
        try {
            const res = await otService.updatePacuRecord(admissionId, bookingId, { ...form });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("Recovery record saved");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const transfer = async () => {
        setSaving(true);
        try {
            const res = await otService.transferPacuRecord(admissionId, bookingId);
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            success("Patient transferred out of recovery");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const Header = () => (
        <div className="flex items-center justify-between mb-3">
            <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">🛏️ PACU / Recovery Record</h5>
            {record && (
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                    isTransferred ? "bg-gray-200 text-gray-700" : record.status === "READY" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
                }`}>{record.status || "ACTIVE"}</span>
            )}
        </div>
    );

    const AldreteScore = ({ score }) => (
        <div className={`text-center px-3 py-2 rounded-lg border ${score >= ALDRETE_MIN ? "bg-green-50 border-green-300" : "bg-amber-50 border-amber-300"}`}>
            <div className="text-[10px] uppercase tracking-wide text-gray-500">Modified Aldrete</div>
            <div className={`text-lg font-bold ${score >= ALDRETE_MIN ? "text-green-700" : "text-amber-700"}`}>{score}/10</div>
            <div className="text-[9px] text-gray-500">{score >= ALDRETE_MIN ? "Ready for transfer" : `Need ≥ ${ALDRETE_MIN}`}</div>
        </div>
    );

    const Fields = () => (
        <div className="space-y-3">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Recovery Bed</label>
                    <input type="text" className={InputCls} value={form.recoveryBed} onChange={(e) => setField("recoveryBed", e.target.value)} />
                </div>
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Consciousness</label>
                    <select className={InputCls} value={form.consciousness} onChange={(e) => setField("consciousness", e.target.value)}>
                        <option value="">—</option>{CONSCIOUSNESS.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </div>
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Orientation</label>
                    <select className={InputCls} value={form.orientation} onChange={(e) => setField("orientation", e.target.value)}>
                        <option value="">—</option>{ORIENTATION.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </div>
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Airway</label>
                    <select className={InputCls} value={form.airwayStatus} onChange={(e) => setField("airwayStatus", e.target.value)}>
                        <option value="">—</option>{AIRWAY.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </div>
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Breathing</label>
                    <select className={InputCls} value={form.breathingStatus} onChange={(e) => setField("breathingStatus", e.target.value)}>
                        <option value="">—</option>{BREATHING.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </div>
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Circulation</label>
                    <select className={InputCls} value={form.circulationStatus} onChange={(e) => setField("circulationStatus", e.target.value)}>
                        <option value="">—</option>{CIRCULATION.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </div>
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Nausea</label>
                    <select className={InputCls} value={form.nauseaSeverity} onChange={(e) => setField("nauseaSeverity", e.target.value)}>
                        <option value="">—</option>{NAUSEA.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </div>
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Pain (0–10): {form.painScore}</label>
                    <input type="range" min="0" max="10" className="w-full" value={form.painScore} onChange={(e) => setField("painScore", Number(e.target.value))} />
                </div>
            </div>

            {/* Aldrete components */}
            <div className="bg-gray-50 rounded-lg p-3">
                <div className="flex items-center justify-between mb-2">
                    <span className="text-[11px] font-semibold text-gray-700">Modified Aldrete Score</span>
                    <AldreteScore score={liveAldrete} />
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
                    {ALDRETE_KEYS.map(([k, label]) => (
                        <div key={k}>
                            <label className="text-[10px] font-medium text-gray-600">{label}</label>
                            <select className={InputCls} value={form[k]} onChange={(e) => setField(k, Number(e.target.value))}>
                                <option value={0}>0</option><option value={1}>1</option><option value={2}>2</option>
                            </select>
                        </div>
                    ))}
                </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                    <label className="text-[11px] font-medium text-gray-600">Transfer Destination</label>
                    <select className={InputCls} value={form.transferDestination} onChange={(e) => setField("transferDestination", e.target.value)}>
                        <option value="">—</option>{DESTINATIONS.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </div>
                <div className="flex items-end">
                    <label className="flex items-center gap-2 text-xs text-gray-700">
                        <input type="checkbox" checked={form.vomitingPresent} onChange={(e) => setField("vomitingPresent", e.target.checked)} />
                        Vomiting present
                    </label>
                </div>
                <div className="sm:col-span-2">
                    <label className="text-[11px] font-medium text-gray-600">Handover Notes (required to transfer)</label>
                    <textarea rows={2} className={InputCls} value={form.handoverNotes} onChange={(e) => setField("handoverNotes", e.target.value)} />
                </div>
            </div>
        </div>
    );

    if (loading) {
        return <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-xs">Loading recovery record…</div>;
    }

    return (
        <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4">
            <Header />

            {!record && (
                canWrite ? (
                    bookingStatus === "COMPLETED" ? (
                        <>
                            <p className="text-[11px] text-gray-500 mb-3">Begin recovery monitoring. Requires the anaesthesia record to be completed.</p>
                            {Fields()}
                            <div className="flex justify-end mt-3">
                                <button onClick={start} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "Saving…" : "Start Recovery Record"}
                                </button>
                            </div>
                        </>
                    ) : (
                        <p className="text-[11px] text-gray-400 italic">Available once surgery is completed (WHO sign-out) and the anaesthesia record is signed.</p>
                    )
                ) : <p className="text-[11px] text-gray-400 italic">No recovery record yet.</p>
            )}

            {record && editing && !isTransferred && (
                <>
                    {Fields()}
                    <div className="flex justify-end gap-2 mt-3">
                        <button onClick={() => { hydrate(record); setEditing(false); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                        <button onClick={update} disabled={saving} className="border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "Saving…" : "Save Assessment"}
                        </button>
                        {canTransfer && (
                            <button onClick={transfer} disabled={saving || liveAldrete < ALDRETE_MIN}
                                title={liveAldrete < ALDRETE_MIN ? `Aldrete must be ≥ ${ALDRETE_MIN}` : ""}
                                className="bg-green-600 hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                {saving ? "…" : "Transfer Out"}
                            </button>
                        )}
                    </div>
                </>
            )}

            {record && !editing && (
                <>
                    <div className="flex items-center gap-4 mb-2">
                        <AldreteScore score={record.aldreteScore ?? 0} />
                        <div className="text-xs text-gray-600 space-y-0.5">
                            <div>Bed: <span className="font-medium">{record.recoveryBed || "-"}</span></div>
                            <div>Destination: <span className="font-medium">{record.transferDestination || "-"}</span></div>
                            <div>Pain: <span className="font-medium">{record.painScore ?? "-"}/10</span></div>
                        </div>
                    </div>
                    {record.handoverNotes && (
                        <div className="text-xs text-gray-700 border-t border-gray-100 pt-2 mt-1"><span className="text-[10px] uppercase text-gray-400">Handover</span><div className="whitespace-pre-wrap">{record.handoverNotes}</div></div>
                    )}
                    {isTransferred && (
                        <div className="mt-2 text-[10px] text-gray-600">✓ Transferred to {record.transferDestination} by {record.signedBy} on {record.signedAt ? new Date(record.signedAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "-"}</div>
                    )}
                    {canWrite && !isTransferred && (
                        <div className="flex justify-end mt-3">
                            <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">✏️ Update / Transfer</button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default PacuRecordSection;
