import React, { useEffect, useState, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const ASA = ["I", "II", "III", "IV", "V", "VI"];
const FITNESS = ["FIT", "FIT_WITH_PRECAUTIONS", "FURTHER_EVALUATION", "DEFERRED"];
const PLANS = ["GENERAL", "SPINAL", "EPIDURAL", "REGIONAL", "LOCAL", "MAC"];

const FITNESS_BADGE = {
    FIT: "bg-green-100 text-green-700",
    FIT_WITH_PRECAUTIONS: "bg-lime-100 text-lime-700",
    FURTHER_EVALUATION: "bg-amber-100 text-amber-800",
    DEFERRED: "bg-red-100 text-red-700",
};

const emptyForm = {
    asaClass: "", airwayAssessment: "", systemicAssessment: "",
    fitnessStatus: "", plannedAnaesthesia: "", remarks: "",
};

/** Pre-Anaesthesia Assessment (Form 15) — admission-level; gates OT scheduling. */
const PacSection = ({ admissionId, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWrite = !isLocked && ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);

    const [record, setRecord] = useState(null);
    const [loading, setLoading] = useState(true);
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState(emptyForm);

    const hydrate = (d) => setForm({
        asaClass: d.asaClass || "", airwayAssessment: d.airwayAssessment || "",
        systemicAssessment: d.systemicAssessment || "", fitnessStatus: d.fitnessStatus || "",
        plannedAnaesthesia: d.plannedAnaesthesia || "", remarks: d.remarks || "",
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getPac(admissionId);
            const data = res?.data || null;
            setRecord(data);
            if (data) hydrate(data);
            setEditing(false);
        } catch { setRecord(null); } finally { setLoading(false); }
    }, [admissionId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, v) => setForm((p) => ({ ...p, [k]: v }));
    const isApproved = record?.status === "APPROVED";

    const save = async () => {
        setSaving(true);
        try {
            const res = await otService.savePac(admissionId, { ...form });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("PAC draft saved");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const approve = async () => {
        setSaving(true);
        try {
            const res = await otService.approvePac(admissionId);
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            success("PAC approved & signed");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const Fields = () => (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
                <label className="text-[11px] font-medium text-gray-600">ASA Class *</label>
                <select className={InputCls} value={form.asaClass} onChange={(e) => setField("asaClass", e.target.value)}>
                    <option value="">Select…</option>{ASA.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Fitness Decision *</label>
                <select className={InputCls} value={form.fitnessStatus} onChange={(e) => setField("fitnessStatus", e.target.value)}>
                    <option value="">Select…</option>{FITNESS.map((o) => <option key={o} value={o}>{o.replaceAll("_", " ")}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Planned Anaesthesia</label>
                <select className={InputCls} value={form.plannedAnaesthesia} onChange={(e) => setField("plannedAnaesthesia", e.target.value)}>
                    <option value="">Select…</option>{PLANS.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div className="sm:col-span-3">
                <label className="text-[11px] font-medium text-gray-600">Airway Assessment (Mallampati, mouth opening, neck, dentition)</label>
                <textarea rows={2} className={InputCls} value={form.airwayAssessment} onChange={(e) => setField("airwayAssessment", e.target.value)} />
            </div>
            <div className="sm:col-span-3">
                <label className="text-[11px] font-medium text-gray-600">Systemic Assessment (CVS / RS / CNS / renal / hepatic / endocrine)</label>
                <textarea rows={2} className={InputCls} value={form.systemicAssessment} onChange={(e) => setField("systemicAssessment", e.target.value)} />
            </div>
            <div className="sm:col-span-3">
                <label className="text-[11px] font-medium text-gray-600">Remarks</label>
                <textarea rows={2} className={InputCls} value={form.remarks} onChange={(e) => setField("remarks", e.target.value)} />
            </div>
        </div>
    );

    return (
        <div className="mb-4 bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-center justify-between cursor-pointer" onClick={() => setOpen((o) => !o)}>
                <div className="flex items-center gap-2">
                    <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">🩺 Pre-Anaesthesia Assessment (PAC)</h5>
                    {record && (
                        <>
                            <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${isApproved ? "bg-green-100 text-green-700" : "bg-yellow-100 text-yellow-800"}`}>{record.status}</span>
                            {record.fitnessStatus && (
                                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${FITNESS_BADGE[record.fitnessStatus] || "bg-gray-100 text-gray-600"}`}>{record.fitnessStatus.replaceAll("_", " ")}</span>
                            )}
                        </>
                    )}
                    {!record && !loading && <span className="text-[10px] text-gray-400 italic">not started</span>}
                </div>
                <span className="text-gray-400 text-xs">{open ? "▲" : "▼"}</span>
            </div>
            <p className="text-[10px] text-gray-400 mt-1">Once a PAC exists, surgery cannot be scheduled until it is approved with FIT / FIT WITH PRECAUTIONS.</p>

            {open && !loading && (
                <div className="mt-3">
                    {!record && (
                        canWrite ? (
                            <>
                                {Fields()}
                                <div className="flex justify-end mt-3">
                                    <button onClick={save} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                        {saving ? "Saving…" : "Save PAC Draft"}
                                    </button>
                                </div>
                            </>
                        ) : <p className="text-[11px] text-gray-400 italic">No PAC recorded.</p>
                    )}

                    {record && (editing && !isApproved ? (
                        <>
                            {Fields()}
                            <div className="flex justify-end gap-2 mt-3">
                                <button onClick={() => { hydrate(record); setEditing(false); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                                <button onClick={save} disabled={saving} className="border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "Saving…" : "Save Draft"}
                                </button>
                                <button onClick={approve} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "…" : "Approve & Sign"}
                                </button>
                            </div>
                        </>
                    ) : (
                        <>
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs text-gray-700">
                                <div><span className="text-[10px] uppercase text-gray-400 block">ASA</span>{record.asaClass || "-"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Fitness</span>{record.fitnessStatus || "-"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Plan</span>{record.plannedAnaesthesia || "-"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Airway</span><span className="line-clamp-2">{record.airwayAssessment || "-"}</span></div>
                            </div>
                            {isApproved && (
                                <div className="mt-2 text-[10px] text-green-700">✓ Approved by {record.signedBy} on {record.signedAt ? new Date(record.signedAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "-"}</div>
                            )}
                            {canWrite && !isApproved && (
                                <div className="flex justify-end mt-3">
                                    <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">✏️ Edit / Approve</button>
                                </div>
                            )}
                        </>
                    ))}
                </div>
            )}
        </div>
    );
};

export default PacSection;
