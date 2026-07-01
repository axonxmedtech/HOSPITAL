import React, { useEffect, useState, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const TRANSPORT = ["STRETCHER", "WHEELCHAIR", "ICU_BED", "AMBULATORY"];

const emptyForm = {
    fromDepartment: "PACU", toDepartment: "", transportMode: "STRETCHER", transportStaff: "",
    devices: "", monitoringPlan: "", pendingTasks: "", remarks: "",
};

const ClinicalHandoverSection = ({ admissionId, bookingId, bookingStatus, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWrite = !isLocked && ["DOCTOR", "NURSE", "HOSPITAL_ADMIN"].includes(user?.role);

    const [record, setRecord] = useState(null);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState(emptyForm);

    const hydrate = (d) => setForm({
        fromDepartment: d.fromDepartment || "PACU", toDepartment: d.toDepartment || "",
        transportMode: d.transportMode || "STRETCHER", transportStaff: d.transportStaff || "",
        devices: d.devices || "", monitoringPlan: d.monitoringPlan || "",
        pendingTasks: d.pendingTasks || "", remarks: d.remarks || "",
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getClinicalHandover(admissionId, bookingId);
            const data = res?.data || null;
            setRecord(data);
            if (data) hydrate(data);
            setEditing(false);
        } catch { setRecord(null); } finally { setLoading(false); }
    }, [admissionId, bookingId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, v) => setForm((p) => ({ ...p, [k]: v }));
    const isAccepted = record?.status === "ACCEPTED";

    const initiate = async () => {
        setSaving(true);
        try {
            const res = await otService.initiateHandover(admissionId, bookingId, { ...form });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("Handover initiated");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const update = async () => {
        setSaving(true);
        try {
            const res = await otService.updateHandover(admissionId, bookingId, { ...form });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("Handover updated");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const accept = async () => {
        setSaving(true);
        try {
            const res = await otService.acceptHandover(admissionId, bookingId);
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            success("Handover accepted — patient received");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const Header = () => (
        <div className="flex items-center justify-between mb-3">
            <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">🤝 Clinical Handover</h5>
            {record && (
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                    isAccepted ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
                }`}>{record.status || "PENDING"}</span>
            )}
        </div>
    );

    const Fields = () => (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
                <label className="text-[11px] font-medium text-gray-600">From</label>
                <input type="text" className={InputCls} value={form.fromDepartment} onChange={(e) => setField("fromDepartment", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Destination Ward</label>
                <input type="text" className={InputCls} value={form.toDepartment} onChange={(e) => setField("toDepartment", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Transport Mode</label>
                <select className={InputCls} value={form.transportMode} onChange={(e) => setField("transportMode", e.target.value)}>
                    {TRANSPORT.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Transport Staff *</label>
                <input type="text" className={InputCls} value={form.transportStaff} onChange={(e) => setField("transportStaff", e.target.value)} />
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Devices — tubes / drains / lines (required) *</label>
                <textarea rows={2} placeholder="e.g. Foley catheter (patent); Abdominal drain (30ml serous); IV cannula R hand" className={InputCls} value={form.devices} onChange={(e) => setField("devices", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Next Due Meds / Pending Tasks</label>
                <textarea rows={2} className={InputCls} value={form.pendingTasks} onChange={(e) => setField("pendingTasks", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Monitoring Plan</label>
                <textarea rows={2} className={InputCls} value={form.monitoringPlan} onChange={(e) => setField("monitoringPlan", e.target.value)} />
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Remarks</label>
                <textarea rows={2} className={InputCls} value={form.remarks} onChange={(e) => setField("remarks", e.target.value)} />
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
        return <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-xs">Loading handover…</div>;
    }

    return (
        <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4">
            <Header />

            {!record && (
                canWrite ? (
                    bookingStatus === "COMPLETED" ? (
                        <>
                            <p className="text-[11px] text-gray-500 mb-3">Initiate the ward handover. Requires the patient to be recovery-ready (PACU Aldrete ≥ 9) and all devices documented.</p>
                            {Fields()}
                            <div className="flex justify-end mt-3">
                                <button onClick={initiate} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "Saving…" : "Initiate Handover"}
                                </button>
                            </div>
                        </>
                    ) : <p className="text-[11px] text-gray-400 italic">Available after surgery is completed and the patient is recovery-ready.</p>
                ) : <p className="text-[11px] text-gray-400 italic">No handover yet.</p>
            )}

            {record && editing && !isAccepted && (
                <>
                    {Fields()}
                    <div className="flex justify-end gap-2 mt-3">
                        <button onClick={() => { hydrate(record); setEditing(false); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                        <button onClick={update} disabled={saving} className="border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "Saving…" : "Save"}
                        </button>
                    </div>
                </>
            )}

            {record && !editing && (
                <>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                        <Row label="From → To" value={`${record.fromDepartment || "-"} → ${record.toDepartment || "-"}`} />
                        <Row label="Transport" value={record.transportMode} />
                        <Row label="Staff" value={record.transportStaff} />
                        <Row label="Initiated by" value={record.handoverBy} />
                    </div>
                    <Row label="Devices" value={record.devices} />
                    {record.pendingTasks && <Row label="Pending Tasks" value={record.pendingTasks} />}
                    {isAccepted ? (
                        <div className="mt-2 text-[10px] text-green-700">✓ Accepted by {record.acceptedBy} on {record.acceptedTime ? new Date(record.acceptedTime).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "-"} — record locked</div>
                    ) : (
                        canWrite && (
                            <div className="flex justify-end gap-2 mt-3">
                                <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">✏️ Edit</button>
                                <button onClick={accept} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "…" : "Accept Handover"}
                                </button>
                            </div>
                        )
                    )}
                </>
            )}
        </div>
    );
};

export default ClinicalHandoverSection;
