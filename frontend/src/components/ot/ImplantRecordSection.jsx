import React, { useCallback, useEffect, useState } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const emptyForm = {
    implantName: "", manufacturer: "", modelNumber: "", catalogNumber: "",
    batchNumber: "", lotNumber: "", serialNumber: "", udi: "", expiryDate: "",
    quantityOpened: "", quantityImplanted: "", quantityReturned: "", quantityWasted: "",
    implantLocation: "", warrantyCardNumber: "",
    patientCardIssued: false, nurseSig: "",
};

const STATUS_BADGE = {
    DRAFT:  "bg-yellow-100 text-yellow-800",
    SIGNED: "bg-green-100  text-green-700",
};

const ImplantRecordSection = ({ admissionId, bookingId, bookingStatus, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();

    // Add implants: Nurse / Admin only (§2.5 mirrors @PreAuthorize NURSE | ADMIN)
    const canAdd    = !isLocked && ["NURSE", "HOSPITAL_ADMIN"].includes(user?.role);
    // Sign: Doctor / Admin only
    const canSign   = !isLocked && ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role);
    // Read: all clinical roles
    const canRead   = ["DOCTOR", "NURSE", "HOSPITAL_ADMIN"].includes(user?.role);

    const [implants, setImplants]   = useState([]);
    const [loading,  setLoading]    = useState(true);
    const [adding,   setAdding]     = useState(false);
    const [saving,   setSaving]     = useState(false);
    const [sigModal, setSigModal]   = useState({ open: false, implant: null, sig: "" });
    const [form, setForm]           = useState(emptyForm);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await otService.getImplants(admissionId, bookingId);
            setImplants(res?.data || []);
        } catch { setImplants([]); } finally { setLoading(false); }
    }, [admissionId, bookingId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, v) => setForm((p) => ({ ...p, [k]: v }));

    const save = async () => {
        if (!form.implantName?.trim()) { toastError("Implant name is required"); return; }
        if (!form.serialNumber?.trim()) { toastError("Serial number is required"); return; }
        setSaving(true);
        try {
            await otService.addImplant(admissionId, bookingId, {
                ...form,
                quantityOpened:    form.quantityOpened    ? parseInt(form.quantityOpened)    : null,
                quantityImplanted: form.quantityImplanted ? parseInt(form.quantityImplanted) : null,
                quantityReturned:  form.quantityReturned  ? parseInt(form.quantityReturned)  : null,
                quantityWasted:    form.quantityWasted    ? parseInt(form.quantityWasted)    : null,
                expiryDate: form.expiryDate || null,
            });
            setForm(emptyForm);
            setAdding(false);
            await load();
            success("Implant record added");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const doSign = async () => {
        if (!sigModal.sig?.trim()) { toastError("Surgeon signature is required"); return; }
        setSaving(true);
        try {
            await otService.signImplant(admissionId, bookingId, sigModal.implant.id, { surgeonSig: sigModal.sig });
            setSigModal({ open: false, implant: null, sig: "" });
            await load();
            success("Implant signed by surgeon");
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    // ── §2.4: define form sub-component inside body, render via function call ──
    const Fields = () => (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Implant / Device Name *</label>
                <input type="text" className={InputCls} placeholder="e.g. Hip Prosthesis, Cardiac Valve" value={form.implantName} onChange={(e) => setField("implantName", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Manufacturer *</label>
                <input type="text" className={InputCls} value={form.manufacturer} onChange={(e) => setField("manufacturer", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Model Number</label>
                <input type="text" className={InputCls} value={form.modelNumber} onChange={(e) => setField("modelNumber", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Serial Number * (must be unique)</label>
                <input type="text" className={InputCls} value={form.serialNumber} onChange={(e) => setField("serialNumber", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Batch / Lot Number</label>
                <input type="text" className={InputCls} value={form.batchNumber} onChange={(e) => setField("batchNumber", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Catalog Number</label>
                <input type="text" className={InputCls} value={form.catalogNumber} onChange={(e) => setField("catalogNumber", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">UDI (barcode / GS1)</label>
                <input type="text" className={InputCls} placeholder="Scan or enter UDI" value={form.udi} onChange={(e) => setField("udi", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Expiry Date</label>
                <input type="date" className={InputCls} value={form.expiryDate} onChange={(e) => setField("expiryDate", e.target.value)} />
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Implant Location / Site</label>
                <input type="text" className={InputCls} placeholder="e.g. Right Hip" value={form.implantLocation} onChange={(e) => setField("implantLocation", e.target.value)} />
            </div>
            <div className="sm:col-span-2 grid grid-cols-4 gap-2">
                {[["Qty Opened", "quantityOpened"], ["Qty Implanted", "quantityImplanted"], ["Qty Returned", "quantityReturned"], ["Qty Wasted", "quantityWasted"]].map(([label, key]) => (
                    <div key={key}>
                        <label className="text-[11px] font-medium text-gray-600">{label}</label>
                        <input type="number" min="0" className={InputCls} value={form[key]} onChange={(e) => setField(key, e.target.value)} />
                    </div>
                ))}
            </div>
            <div>
                <label className="text-[11px] font-medium text-gray-600">Warranty Card No.</label>
                <input type="text" className={InputCls} value={form.warrantyCardNumber} onChange={(e) => setField("warrantyCardNumber", e.target.value)} />
            </div>
            <div className="flex items-end gap-2">
                <label className="flex items-center gap-2 text-xs text-gray-700 cursor-pointer">
                    <input type="checkbox" checked={form.patientCardIssued} onChange={(e) => setField("patientCardIssued", e.target.checked)} />
                    Patient Implant Card Issued
                </label>
            </div>
            <div className="sm:col-span-2">
                <label className="text-[11px] font-medium text-gray-600">Nurse Signature (text / initial)</label>
                <input type="text" className={InputCls} placeholder="Nurse initials or sign confirmation" value={form.nurseSig} onChange={(e) => setField("nurseSig", e.target.value)} />
            </div>
        </div>
    );

    if (loading) return (
        <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-xs">Loading implant records…</div>
    );

    return (
        <div className="mt-4 bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-2">
                <h5 className="font-bold text-gray-900 text-sm uppercase tracking-wide">🔩 Implant / Prosthesis Record</h5>
                <span className="text-[10px] text-gray-400">Form 24 · {implants.length} device(s)</span>
            </div>
            <p className="text-[10px] text-gray-400 mb-3">⚠️ Gate: implants can only be added while the operation record is open (not FINALIZED).</p>

            {/* Existing implant rows */}
            {implants.length > 0 && (
                <div className="space-y-2 mb-3">
                    {implants.map((imp) => (
                        <div key={imp.id} className="border border-gray-100 rounded-lg p-3 text-xs text-gray-800">
                            <div className="flex items-start justify-between">
                                <div className="font-semibold text-gray-900">{imp.implantName || "—"}</div>
                                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${STATUS_BADGE[imp.status] || "bg-gray-100 text-gray-600"}`}>
                                    {imp.status || "DRAFT"}
                                </span>
                            </div>
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-1 mt-1 text-gray-500">
                                <div><span className="text-[10px] uppercase text-gray-400 block">Manufacturer</span>{imp.manufacturer || "—"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Serial</span>{imp.serialNumber || "—"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Batch</span>{imp.batchNumber || "—"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Location</span>{imp.implantLocation || "—"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Qty Implanted</span>{imp.quantityImplanted ?? "—"}</div>
                                <div><span className="text-[10px] uppercase text-gray-400 block">Expiry</span>{imp.expiryDate || "—"}</div>
                                {imp.udi && <div className="sm:col-span-2"><span className="text-[10px] uppercase text-gray-400 block">UDI</span>{imp.udi}</div>}
                            </div>
                            {imp.patientCardIssued && (
                                <div className="mt-1 text-[10px] text-blue-600">✓ Patient implant card issued</div>
                            )}
                            {canSign && imp.status !== "SIGNED" && (
                                <div className="flex justify-end mt-2">
                                    <button
                                        onClick={() => setSigModal({ open: true, implant: imp, sig: "" })}
                                        className="bg-green-600 hover:bg-green-700 text-white text-[10px] font-bold px-3 py-1 rounded-lg"
                                    >
                                        ✍️ Surgeon Sign-off
                                    </button>
                                </div>
                            )}
                            {imp.status === "SIGNED" && imp.signedAt && (
                                <div className="mt-1 text-[10px] text-green-600">
                                    ✓ Signed by surgeon on {new Date(imp.signedAt).toLocaleString()}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {/* Add form */}
            {canAdd && !adding && (bookingStatus === "IN_PROGRESS" || bookingStatus === "COMPLETED") && (
                <button
                    onClick={() => setAdding(true)}
                    className="border border-dashed border-blue-300 hover:border-blue-500 text-blue-600 text-xs font-semibold w-full py-2 rounded-lg mt-1"
                >
                    + Add Implant
                </button>
            )}

            {canAdd && !adding && bookingStatus !== "IN_PROGRESS" && bookingStatus !== "COMPLETED" && (
                <p className="text-[11px] text-gray-400 italic">Available once surgery is under way.</p>
            )}

            {adding && (
                <>
                    <div className="border-t border-gray-100 pt-3 mt-2">
                        {Fields()}
                    </div>
                    <div className="flex justify-end gap-2 mt-3">
                        <button onClick={() => { setAdding(false); setForm(emptyForm); }} className="text-xs text-gray-500 hover:text-gray-700 px-3 py-2">Cancel</button>
                        <button onClick={save} disabled={saving} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                            {saving ? "Saving…" : "Save Implant"}
                        </button>
                    </div>
                </>
            )}

            {/* Surgeon sign-off modal */}
            {sigModal.open && (
                <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-xl p-6 w-full max-w-sm shadow-xl">
                        <h4 className="font-bold text-gray-900 text-sm mb-3">✍️ Surgeon Sign-off</h4>
                        <p className="text-xs text-gray-600 mb-3">
                            Signing <strong>{sigModal.implant?.implantName}</strong> (SN: {sigModal.implant?.serialNumber}).
                        </p>
                        <label className="text-[11px] font-medium text-gray-600">Signature (text or code)</label>
                        <input
                            type="text"
                            className={`${InputCls} mt-1`}
                            placeholder="Surgeon initials / confirmation"
                            value={sigModal.sig}
                            onChange={(e) => setSigModal((p) => ({ ...p, sig: e.target.value }))}
                        />
                        <div className="flex justify-end gap-2 mt-4">
                            <button onClick={() => setSigModal({ open: false, implant: null, sig: "" })} className="text-xs text-gray-500 px-3 py-2">Cancel</button>
                            <button onClick={doSign} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-xs font-semibold px-4 py-2 rounded-lg">
                                {saving ? "…" : "Confirm Sign-off"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ImplantRecordSection;
