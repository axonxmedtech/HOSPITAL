import React, { useEffect, useState, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const TYPES = ["GENERAL", "SPINAL", "EPIDURAL", "REGIONAL", "LOCAL", "MAC"];
const ASA = ["I", "II", "III", "IV", "V", "VI"];
const AIRWAYS = ["ETT", "LMA", "FACE_MASK", "TRACHEOSTOMY", "NONE"];
const VENTILATION = ["SPONTANEOUS", "CONTROLLED", "ASSISTED"];

const emptyForm = {
    anaesthesiaType: "", asaGrade: "", airwayType: "", ventilationMode: "",
    inductionTime: "", completionTime: "", notes: "",
};

const toLocalInput = (iso) => (iso ? String(iso).slice(0, 16) : "");

const AnaesthesiaRecordSection = ({ admissionId, bookingId, checklistSignedIn, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWrite = !isLocked && ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);

    const [record, setRecord] = useState(null);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState(emptyForm);

    const hydrate = (data) => setForm({
        anaesthesiaType: data.anaesthesiaType || "",
        asaGrade: data.asaGrade || "",
        airwayType: data.airwayType || "",
        ventilationMode: data.ventilationMode || "",
        inductionTime: toLocalInput(data.inductionTime),
        completionTime: toLocalInput(data.completionTime),
        notes: data.notes || "",
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getAnaesthesiaRecord(admissionId, bookingId);
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
    const isCompleted = record?.status === "COMPLETED";

    const payload = () => ({
        ...form,
        inductionTime: form.inductionTime || null,
        completionTime: form.completionTime || null,
    });

    const start = async () => {
        setSaving(true);
        try {
            const res = await otService.startAnaesthesiaRecord(admissionId, bookingId, payload());
            setRecord(res?.data || null);
            if (res?.data) hydrate(res.data);
            setEditing(false);
            success("Anaesthesia record started");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const update = async () => {
        setSaving(true);
        try {
            const res = await otService.updateAnaesthesiaRecord(admissionId, bookingId, payload());
            setRecord(res?.data || null);
            if (res?.data) hydrate(res.data);
            setEditing(false);
            success("Anaesthesia record saved");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const complete = async () => {
        setSaving(true);
        try {
            const res = await otService.completeAnaesthesiaRecord(admissionId, bookingId);
            setRecord(res?.data || null);
            if (res?.data) hydrate(res.data);
            success("Anaesthesia record completed & signed");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const Header = () => (
        <div className="flex items-center justify-between mb-3">
            <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">💉 Anaesthesia Record</h5>
            {record && (
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${isCompleted ? "bg-green-100 text-green-700" : "bg-blue-100 text-blue-700"}`}>
                    {record.status || "ACTIVE"}
                </span>
            )}
        </div>
    );

    const Fields = () => (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
                <label className="text-[11px] font-medium text-gray-600">Anaesthesia Type *</label>
                <select className={InputCls} value={form.anaesthesiaType} onChange={(e) => setField("anaesthesiaType", e.target.value)}>
                    <option value="">Select…</option>
                    {TYPES.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">ASA Grade</label>
                <select className={InputCls} value={form.asaGrade} onChange={(e) => setField("asaGrade", e.target.value)}>
                    <option value="">Select…</option>
                    {ASA.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Airway</label>
                <select className={InputCls} value={form.airwayType} onChange={(e) => setField("airwayType", e.target.value)}>
                    <option value="">Select…</option>
                    {AIRWAYS.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Ventilation</label>
                <select className={InputCls} value={form.ventilationMode} onChange={(e) => setField("ventilationMode", e.target.value)}>
                    <option value="">Select…</option>
                    {VENTILATION.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Induction Time</label>
                <input type="datetime-local" className={InputCls} value={form.inductionTime} onChange={(e) => setField("inductionTime", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Completion Time</label>
                <input type="datetime-local" className={InputCls} value={form.completionTime} onChange={(e) => setField("completionTime", e.target.value)} />
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Notes</label>
                <textarea rows={2} className={InputCls} value={form.notes} onChange={(e) => setField("notes", e.target.value)} />
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
        return <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-xs">Loading anaesthesia record…</div>;
    }

    return (
        <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4">
            <Header />

            {!record && (
                canWrite ? (
                    checklistSignedIn ? (
                        <>
                            <p className="text-[11px] text-gray-500 mb-3">Start the anaesthesia record. Requires the WHO sign-in (before induction).</p>
                            {Fields()}
                            <div className="flex justify-end mt-3">
                                <button onClick={start} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "Saving…" : "Start Anaesthesia Record"}
                                </button>
                            </div>
                        </>
                    ) : (
                        <p className="text-[11px] text-gray-400 italic">Available once the WHO sign-in is completed (before induction of anaesthesia).</p>
                    )
                ) : (
                    <p className="text-[11px] text-gray-400 italic">No anaesthesia record yet.</p>
                )
            )}

            {record && editing && !isCompleted && (
                <>
                    {Fields()}
                    <div className="flex justify-end gap-2 mt-3">
                        <button onClick={() => { hydrate(record); setEditing(false); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                        <button onClick={update} disabled={saving} className="border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "Saving…" : "Save"}
                        </button>
                        <button onClick={complete} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "…" : "Complete & Sign"}
                        </button>
                    </div>
                </>
            )}

            {record && !editing && (
                <>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                        <Row label="Type" value={record.anaesthesiaType} />
                        <Row label="ASA" value={record.asaGrade} />
                        <Row label="Airway" value={record.airwayType} />
                        <Row label="Ventilation" value={record.ventilationMode} />
                    </div>
                    {record.notes && <Row label="Notes" value={record.notes} />}
                    {isCompleted && (
                        <div className="mt-2 text-[10px] text-green-700">✓ Completed by {record.signedBy} on {record.signedAt ? new Date(record.signedAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "-"}</div>
                    )}
                    {canWrite && !isCompleted && (
                        <div className="flex justify-end mt-3">
                            <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">✏️ Edit / Complete</button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default AnaesthesiaRecordSection;
