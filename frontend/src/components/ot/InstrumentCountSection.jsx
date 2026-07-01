import React, { useEffect, useState, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const emptyForm = {
    scrubNurse: "", circulatingNurse: "", countSummary: "",
    initialCountStatus: "PENDING", finalCountStatus: "PENDING",
};

const emptyResolution = { searchConducted: false, xrayPerformed: false, resolutionRemarks: "" };

const STATUS_BADGE = {
    VERIFIED: "bg-green-100 text-green-700",
    MISMATCH: "bg-red-100 text-red-700",
    PENDING: "bg-yellow-100 text-yellow-800",
};

const InstrumentCountSection = ({ admissionId, bookingId, bookingStatus, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWrite = !isLocked && ["DOCTOR", "NURSE", "HOSPITAL_ADMIN"].includes(user?.role);

    const [record, setRecord] = useState(null);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState(emptyForm);
    const [resolution, setResolution] = useState(emptyResolution);

    const hydrate = (d) => setForm({
        scrubNurse: d.scrubNurse || "", circulatingNurse: d.circulatingNurse || "",
        countSummary: d.countSummary || "",
        initialCountStatus: d.initialCountStatus || "PENDING",
        finalCountStatus: d.finalCountStatus || "PENDING",
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getInstrumentCount(admissionId, bookingId);
            const data = res?.data || null;
            setRecord(data);
            if (data) hydrate(data);
            setEditing(false);
        } catch { setRecord(null); } finally { setLoading(false); }
    }, [admissionId, bookingId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, v) => setForm((p) => ({ ...p, [k]: v }));
    const needsResolution = record?.finalCountStatus === "MISMATCH" && !record?.resolved;

    const save = async () => {
        setSaving(true);
        try {
            const res = await otService.saveInstrumentCount(admissionId, bookingId, { ...form });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setEditing(false); success("Count saved");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const resolve = async () => {
        setSaving(true);
        try {
            const res = await otService.resolveInstrumentCount(admissionId, bookingId, { ...resolution });
            setRecord(res?.data || null); if (res?.data) hydrate(res.data);
            setResolution(emptyResolution);
            success("Discrepancy resolution documented");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const Header = () => (
        <div className="flex items-center justify-between mb-3">
            <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">🔢 Instrument / Swab / Needle Count</h5>
            {record && (
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${STATUS_BADGE[record.finalCountStatus] || STATUS_BADGE.PENDING}`}>
                    {record.finalCountStatus || "PENDING"}{record.resolved ? " · RESOLVED" : ""}
                </span>
            )}
        </div>
    );

    const Fields = () => (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
                <label className="text-[11px] font-medium text-gray-600">Scrub Nurse</label>
                <input type="text" className={InputCls} value={form.scrubNurse} onChange={(e) => setField("scrubNurse", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Circulating Nurse</label>
                <input type="text" className={InputCls} value={form.circulatingNurse} onChange={(e) => setField("circulatingNurse", e.target.value)} />
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Count Lines (item — counted/expected)</label>
                <textarea rows={3} placeholder={"Artery forceps 6/6\nAbdominal sponges 10/10\nNeedles 4/4"} className={InputCls} value={form.countSummary} onChange={(e) => setField("countSummary", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Initial Count (before incision)</label>
                <select className={InputCls} value={form.initialCountStatus} onChange={(e) => setField("initialCountStatus", e.target.value)}>
                    <option value="PENDING">PENDING</option>
                    <option value="VERIFIED">VERIFIED</option>
                </select>
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Final Count (before closure)</label>
                <select className={InputCls} value={form.finalCountStatus} onChange={(e) => setField("finalCountStatus", e.target.value)}>
                    <option value="PENDING">PENDING</option>
                    <option value="VERIFIED">VERIFIED — all counts correct</option>
                    <option value="MISMATCH">MISMATCH — discrepancy found</option>
                </select>
            </div>
        </div>
    );

    if (loading) {
        return <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-xs">Loading instrument count…</div>;
    }

    return (
        <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4">
            <Header />
            <p className="text-[10px] text-gray-400 mb-3">⚠️ WHO sign-out is blocked while the count is pending or a discrepancy is unresolved.</p>

            {!record && (
                canWrite ? (
                    bookingStatus === "IN_PROGRESS" || bookingStatus === "COMPLETED" ? (
                        <>
                            {Fields()}
                            <div className="flex justify-end mt-3">
                                <button onClick={save} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "Saving…" : "Start Count Record"}
                                </button>
                            </div>
                        </>
                    ) : <p className="text-[11px] text-gray-400 italic">Available once the surgery is under way.</p>
                ) : <p className="text-[11px] text-gray-400 italic">No count record yet.</p>
            )}

            {record && editing && (
                <>
                    {Fields()}
                    <div className="flex justify-end gap-2 mt-3">
                        <button onClick={() => { hydrate(record); setEditing(false); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                        <button onClick={save} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "Saving…" : "Save Count"}
                        </button>
                    </div>
                </>
            )}

            {record && !editing && (
                <>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs text-gray-700">
                        <div><span className="text-[10px] uppercase text-gray-400 block">Scrub</span>{record.scrubNurse || "-"}</div>
                        <div><span className="text-[10px] uppercase text-gray-400 block">Circulating</span>{record.circulatingNurse || "-"}</div>
                        <div><span className="text-[10px] uppercase text-gray-400 block">Initial</span>{record.initialCountStatus || "-"}</div>
                        <div><span className="text-[10px] uppercase text-gray-400 block">Final</span>{record.finalCountStatus || "-"}</div>
                    </div>
                    {record.countSummary && (
                        <div className="mt-2 text-xs text-gray-800 whitespace-pre-wrap border-t border-gray-100 pt-2">{record.countSummary}</div>
                    )}
                    {record.resolved && (
                        <div className="mt-2 text-[10px] text-green-700">✓ Discrepancy resolved{record.xrayPerformed ? " (X-ray performed)" : ""} — {record.resolutionRemarks}</div>
                    )}

                    {canWrite && (
                        <div className="flex justify-end mt-3">
                            <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-gray-700 text-xs font-semibold px-4 py-2 rounded-lg">✏️ Update Count</button>
                        </div>
                    )}

                    {/* BR-4 discrepancy resolution */}
                    {canWrite && needsResolution && (
                        <div className="mt-3 border border-red-200 bg-red-50/40 rounded-lg p-3">
                            <div className="text-[11px] font-semibold text-red-700 mb-2">⚠️ Count discrepancy — document the resolution to unblock sign-out</div>
                            <div className="flex flex-wrap gap-4 mb-2">
                                <label className="flex items-center gap-1.5 text-xs text-gray-700">
                                    <input type="checkbox" checked={resolution.searchConducted} onChange={(e) => setResolution((p) => ({ ...p, searchConducted: e.target.checked }))} />
                                    Search conducted *
                                </label>
                                <label className="flex items-center gap-1.5 text-xs text-gray-700">
                                    <input type="checkbox" checked={resolution.xrayPerformed} onChange={(e) => setResolution((p) => ({ ...p, xrayPerformed: e.target.checked }))} />
                                    Intra-op X-ray performed
                                </label>
                            </div>
                            <textarea rows={2} placeholder="Resolution remarks (required) — e.g. X-ray negative; sponge located in kick bucket" className={InputCls} value={resolution.resolutionRemarks} onChange={(e) => setResolution((p) => ({ ...p, resolutionRemarks: e.target.value }))} />
                            <div className="flex justify-end mt-2">
                                <button onClick={resolve} disabled={saving} className="bg-red-600 hover:bg-red-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                    {saving ? "…" : "Document Resolution"}
                                </button>
                            </div>
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default InstrumentCountSection;
