import React, { useEffect, useState, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const emptyForm = {
    procedureName: "", actualProcedure: "", operativeFindings: "",
    estimatedBloodLoss: "", complicationsSummary: "", postOpPlan: "",
    operationStart: "", operationEnd: "",
};

// Backend sends ISO LocalDateTime; <input type="datetime-local"> wants "yyyy-MM-ddTHH:mm".
const toLocalInput = (iso) => (iso ? String(iso).slice(0, 16) : "");

const OperationRecordSection = ({ admissionId, bookingId, bookingStatus, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWrite = !isLocked && ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);

    const [record, setRecord] = useState(null);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState(emptyForm);

    const hydrate = (data) => setForm({
        procedureName: data.procedureName || "",
        actualProcedure: data.actualProcedure || "",
        operativeFindings: data.operativeFindings || "",
        estimatedBloodLoss: data.estimatedBloodLoss || "",
        complicationsSummary: data.complicationsSummary || "",
        postOpPlan: data.postOpPlan || "",
        operationStart: toLocalInput(data.operationStart),
        operationEnd: toLocalInput(data.operationEnd),
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getOperationRecord(admissionId, bookingId);
            const data = res?.data || null;
            setRecord(data);
            if (data) hydrate(data);
            setEditing(false);
        } catch {
            setRecord(null);
        } finally {
            setLoading(false);
        }
    }, [admissionId, bookingId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, v) => setForm((p) => ({ ...p, [k]: v }));
    const isFinalized = record?.status === "FINALIZED";

    const payload = () => ({
        ...form,
        operationStart: form.operationStart || null,
        operationEnd: form.operationEnd || null,
    });

    const start = async () => {
        setSaving(true);
        try {
            const res = await otService.saveOperationRecord(admissionId, bookingId, payload());
            setRecord(res?.data || null);
            if (res?.data) hydrate(res.data);
            setEditing(false);
            success("Operation record started");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const update = async () => {
        setSaving(true);
        try {
            const res = await otService.updateOperationRecord(admissionId, bookingId, payload());
            setRecord(res?.data || null);
            if (res?.data) hydrate(res.data);
            setEditing(false);
            success("Operation record saved");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const finalize = async () => {
        setSaving(true);
        try {
            const res = await otService.finalizeOperationRecord(admissionId, bookingId);
            setRecord(res?.data || null);
            if (res?.data) hydrate(res.data);
            success("Operation record finalized & signed");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const Header = () => (
        <div className="flex items-center justify-between mb-3">
            <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">📝 Operation Record</h5>
            {record && (
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${isFinalized ? "bg-green-100 text-green-700" : "bg-yellow-100 text-yellow-800"}`}>
                    {record.status || "DRAFT"}
                </span>
            )}
        </div>
    );

    const Fields = () => (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Actual Procedure Performed *</label>
                <textarea rows={2} className={InputCls} value={form.actualProcedure} onChange={(e) => setField("actualProcedure", e.target.value)} />
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Operative Findings</label>
                <textarea rows={2} className={InputCls} value={form.operativeFindings} onChange={(e) => setField("operativeFindings", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Estimated Blood Loss</label>
                <input type="text" placeholder="e.g. 150 ml" className={InputCls} value={form.estimatedBloodLoss} onChange={(e) => setField("estimatedBloodLoss", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Complications</label>
                <input type="text" placeholder="None / describe" className={InputCls} value={form.complicationsSummary} onChange={(e) => setField("complicationsSummary", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Operation Start</label>
                <input type="datetime-local" className={InputCls} value={form.operationStart} onChange={(e) => setField("operationStart", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Operation End</label>
                <input type="datetime-local" className={InputCls} value={form.operationEnd} onChange={(e) => setField("operationEnd", e.target.value)} />
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Post-operative Plan *</label>
                <textarea rows={2} className={InputCls} value={form.postOpPlan} onChange={(e) => setField("postOpPlan", e.target.value)} />
            </div>
        </div>
    );

    const Row = ({ label, value }) => (
        <div className="py-1.5 border-b border-gray-100 last:border-0">
            <span className="text-[10px] uppercase tracking-wide text-gray-400">{label}</span>
            <div className="text-xs text-gray-800 whitespace-pre-wrap">{value || "-"}</div>
        </div>
    );

    if (loading) {
        return <div className="mt-6 bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-xs">Loading operation record…</div>;
    }

    return (
        <div className="mt-6 bg-white rounded-xl border border-gray-200 p-4">
            <Header />

            {/* No record yet */}
            {!record && (
                canWrite ? (
                    bookingStatus === "IN_PROGRESS" || bookingStatus === "COMPLETED" ? (
                        <>
                            <p className="text-[11px] text-gray-500 mb-3">Capture the intra-operative record. The WHO time-out must be completed first.</p>
                            {Fields()}
                            <div className="flex justify-end mt-3">
                                <button onClick={start} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "Saving…" : "Start Operation Record"}
                                </button>
                            </div>
                        </>
                    ) : (
                        <p className="text-[11px] text-gray-400 italic">Available once the WHO time-out is signed (surgery in progress).</p>
                    )
                ) : (
                    <p className="text-[11px] text-gray-400 italic">No operation record yet.</p>
                )
            )}

            {/* Editing an existing draft */}
            {record && editing && !isFinalized && (
                <>
                    {Fields()}
                    <div className="flex justify-end gap-2 mt-3">
                        <button onClick={() => { hydrate(record); setEditing(false); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                        <button onClick={update} disabled={saving} className="border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "Saving…" : "Save Draft"}
                        </button>
                        <button onClick={finalize} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "…" : "Finalize & Sign"}
                        </button>
                    </div>
                </>
            )}

            {/* Read view of an existing record */}
            {record && !editing && (
                <>
                    <Row label="Actual Procedure" value={record.actualProcedure} />
                    <Row label="Operative Findings" value={record.operativeFindings} />
                    <div className="grid grid-cols-2 gap-3">
                        <Row label="Estimated Blood Loss" value={record.estimatedBloodLoss} />
                        <Row label="Complications" value={record.complicationsSummary} />
                    </div>
                    <Row label="Post-operative Plan" value={record.postOpPlan} />
                    {isFinalized && (
                        <div className="mt-2 text-[10px] text-green-700">✓ Finalized by {record.signedBy} on {record.signedAt ? new Date(record.signedAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "-"}</div>
                    )}
                    {canWrite && !isFinalized && (
                        <div className="flex justify-end mt-3">
                            <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">✏️ Edit / Finalize</button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default OperationRecordSection;
