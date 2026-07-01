import React, { useEffect, useState, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const CONDITIONS = ["STABLE", "GUARDED", "CRITICAL"];
const DIETS = ["NPO", "CLEAR_LIQUIDS", "SOFT", "NORMAL", "DIABETIC"];
const ACTIVITY = ["BED_REST", "BED_REST_WITH_TOILET", "AMBULATE_ASSISTED", "AMBULATE_FREE"];

const emptyForm = {
    postopDiagnosis: "", condition: "STABLE", dietOrder: "NPO", activityOrder: "BED_REST",
    medications: "", monitoringPlan: "", investigations: "", escalationInstructions: "",
};

const PostopOrdersSection = ({ admissionId, bookingId, bookingStatus, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWrite = !isLocked && ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);

    const [record, setRecord] = useState(null);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState(emptyForm);

    const hydrate = (d) => setForm({
        postopDiagnosis: d.postopDiagnosis || "", condition: d.condition || "STABLE",
        dietOrder: d.dietOrder || "NPO", activityOrder: d.activityOrder || "BED_REST",
        medications: d.medications || "", monitoringPlan: d.monitoringPlan || "",
        investigations: d.investigations || "", escalationInstructions: d.escalationInstructions || "",
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getPostopOrders(admissionId, bookingId);
            const data = res?.data || null;
            setRecord(data);
            if (data) hydrate(data);
            setEditing(false);
        } catch { setRecord(null); } finally { setLoading(false); }
    }, [admissionId, bookingId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, v) => setForm((p) => ({ ...p, [k]: v }));
    const isSigned = record?.status === "SIGNED";

    const save = async () => {
        setSaving(true);
        try {
            const res = await otService.savePostopOrders(admissionId, bookingId, { ...form });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("Post-op orders saved as draft");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const sign = async () => {
        setSaving(true);
        try {
            const res = await otService.signPostopOrders(admissionId, bookingId);
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("Post-op orders signed");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const Header = () => (
        <div className="flex items-center justify-between mb-3">
            <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">📋 Post-operative Orders</h5>
            {record && (
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${isSigned ? "bg-green-100 text-green-700" : "bg-yellow-100 text-yellow-800"}`}>
                    {record.status || "DRAFT"}
                </span>
            )}
        </div>
    );

    const Fields = () => (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Post-operative Diagnosis *</label>
                <input type="text" className={InputCls} value={form.postopDiagnosis} onChange={(e) => setField("postopDiagnosis", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Condition</label>
                <select className={InputCls} value={form.condition} onChange={(e) => setField("condition", e.target.value)}>
                    {CONDITIONS.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Diet</label>
                <select className={InputCls} value={form.dietOrder} onChange={(e) => setField("dietOrder", e.target.value)}>
                    {DIETS.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Activity</label>
                <select className={InputCls} value={form.activityOrder} onChange={(e) => setField("activityOrder", e.target.value)}>
                    {ACTIVITY.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Medications (drug / dose / route / frequency)</label>
                <textarea rows={2} placeholder="e.g. Inj. Ceftriaxone 1g IV BD x 3 days; Inj. Paracetamol 1g IV TDS" className={InputCls} value={form.medications} onChange={(e) => setField("medications", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Monitoring Plan</label>
                <textarea rows={2} placeholder="e.g. Vitals q30min x 2h, then q4h; drain output charting" className={InputCls} value={form.monitoringPlan} onChange={(e) => setField("monitoringPlan", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Investigations</label>
                <textarea rows={2} placeholder="e.g. CBC + electrolytes tomorrow AM; X-ray chest if SpO2 < 94%" className={InputCls} value={form.investigations} onChange={(e) => setField("investigations", e.target.value)} />
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Escalation Instructions</label>
                <textarea rows={2} placeholder="e.g. Call surgeon if SBP < 90, HR > 120, drain > 100ml/h, temp > 38.5" className={InputCls} value={form.escalationInstructions} onChange={(e) => setField("escalationInstructions", e.target.value)} />
            </div>
        </div>
    );

    const Row = ({ label, value }) => (
        <div className="py-1.5">
            <span className="text-[10px] uppercase tracking-wide text-gray-400">{label}</span>
            <div className="text-xs text-gray-800 whitespace-pre-wrap">{value || "-"}</div>
        </div>
    );

    if (loading) {
        return <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-xs">Loading post-op orders…</div>;
    }

    return (
        <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4">
            <Header />

            {!record && (
                canWrite ? (
                    bookingStatus === "IN_PROGRESS" || bookingStatus === "COMPLETED" ? (
                        <>
                            <p className="text-[11px] text-gray-500 mb-3">Draft the surgeon's post-operative instruction bundle for the ward/ICU.</p>
                            {Fields()}
                            <div className="flex justify-end mt-3">
                                <button onClick={save} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "Saving…" : "Save Draft"}
                                </button>
                            </div>
                        </>
                    ) : (
                        <p className="text-[11px] text-gray-400 italic">Available once the surgery is under way.</p>
                    )
                ) : <p className="text-[11px] text-gray-400 italic">No post-operative orders yet.</p>
            )}

            {record && editing && !isSigned && (
                <>
                    {Fields()}
                    <div className="flex justify-end gap-2 mt-3">
                        <button onClick={() => { hydrate(record); setEditing(false); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                        <button onClick={save} disabled={saving} className="border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "Saving…" : "Save Draft"}
                        </button>
                        <button onClick={sign} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "…" : "Sign Orders"}
                        </button>
                    </div>
                </>
            )}

            {record && !editing && (
                <>
                    <Row label="Post-op Diagnosis" value={record.postopDiagnosis} />
                    <div className="grid grid-cols-3 gap-3">
                        <Row label="Condition" value={record.condition} />
                        <Row label="Diet" value={record.dietOrder} />
                        <Row label="Activity" value={record.activityOrder} />
                    </div>
                    <Row label="Medications" value={record.medications} />
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Row label="Monitoring" value={record.monitoringPlan} />
                        <Row label="Investigations" value={record.investigations} />
                    </div>
                    {record.escalationInstructions && <Row label="Escalation" value={record.escalationInstructions} />}
                    {isSigned && (
                        <div className="mt-2 text-[10px] text-green-700">✓ Signed by {record.signedBy} on {record.signedAt ? new Date(record.signedAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "-"} — read-only</div>
                    )}
                    {canWrite && !isSigned && (
                        <div className="flex justify-end mt-3">
                            <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">✏️ Edit / Sign</button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default PostopOrdersSection;
