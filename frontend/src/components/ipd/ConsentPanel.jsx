import React, { useEffect, useState, useCallback } from "react";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const CONSENT_TYPES = ["GENERAL", "SURGERY", "BLOOD", "ANESTHESIA", "PROCEDURE"];
const SIGNATURE_TYPES = ["WET", "DIGITAL", "THUMBPRINT"];
const LANGUAGES = ["ENGLISH", "HINDI", "MARATHI", "GUJARATI", "BENGALI", "TAMIL", "TELUGU", "OTHER"];

const STATUS_STYLES = {
    DRAFT:     "bg-amber-50 text-amber-700 border border-amber-200",
    SUBMITTED: "bg-blue-50 text-blue-700 border border-blue-200",
    LOCKED:    "bg-green-50 text-green-700 border border-green-200",
    AMENDED:   "bg-purple-50 text-purple-700 border border-purple-200",
};

const TYPE_ICONS = {
    GENERAL: "📋", SURGERY: "🔪", SURGICAL: "🔪", BLOOD: "🩸", ANESTHESIA: "💉", PROCEDURE: "🏥",
};

const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.message || "An error occurred";

const ConsentPanel = ({ admissionId, patientId, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const isDoctor = authService.isDoctor();
    const isAdmin = user?.role === "HOSPITAL_ADMIN";
    const canEdit = (isDoctor || isAdmin) && !isLocked;

    const [consents, setConsents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [expandedId, setExpandedId] = useState(null);

    const [createModal, setCreateModal] = useState({ open: false, saving: false });
    const [form, setForm] = useState({ consentType: "GENERAL", encounterType: "IPD", language: "ENGLISH", signatureType: "WET", patientSigned: false, guardianSigned: false, relationship: "", remarks: "", procedureName: "", surgeonName: "" });

    const [signModal, setSignModal] = useState({ open: false, consentId: null, saving: false });
    const [signForm, setSignForm] = useState({ signatureType: "WET", patientSigned: false, guardianSigned: false, relationship: "" });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await hospitalService.getConsentsByAdmission(admissionId);
            setConsents(Array.isArray(data) ? data : []);
        } catch { setConsents([]); } finally { setLoading(false); }
    }, [admissionId]);

    useEffect(() => { load(); }, [load]);

    const handleCreate = async () => {
        setCreateModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.createConsentDraft({ admissionId: Number(admissionId), patientId: Number(patientId), ...form });
            success("Consent draft created");
            setCreateModal({ open: false, saving: false });
            setForm({ consentType: "GENERAL", encounterType: "IPD", language: "ENGLISH", signatureType: "WET", patientSigned: false, guardianSigned: false, relationship: "", remarks: "", procedureName: "", surgeonName: "" });
            load();
        } catch (e) { toastError(parseErr(e)); setCreateModal(p => ({ ...p, saving: false })); }
    };

    const handleSubmit = async (consentId) => {
        try { await hospitalService.submitConsent(consentId); success("Consent submitted and locked"); load(); }
        catch (e) { toastError(parseErr(e)); }
    };

    const handleSign = async () => {
        setSignModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.signConsent(signModal.consentId, signForm);
            success("Consent signed");
            setSignModal({ open: false, consentId: null, saving: false });
            load();
        } catch (e) { toastError(parseErr(e)); setSignModal(p => ({ ...p, saving: false })); }
    };

    const InputCls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent";
    const BtnPrimary = "px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors";
    const BtnSecondary = "px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors";

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-base font-bold text-gray-900">Consent Management</h2>
                    <p className="text-xs text-gray-500 mt-0.5">Forms 05 & 01 — Patient & Procedure Consents</p>
                </div>
                {canEdit && (
                    <button onClick={() => setCreateModal({ open: true, saving: false })}
                        className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">
                        + New Consent
                    </button>
                )}
            </div>

            {loading ? (
                <div className="space-y-3">{[1, 2].map(i => <div key={i} className="h-16 bg-gray-100 rounded-xl animate-pulse" />)}</div>
            ) : consents.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-14 text-center">
                    <span className="text-4xl mb-3">📋</span>
                    <p className="text-sm font-medium text-gray-700">No consents recorded</p>
                    <p className="text-xs text-gray-400 mt-1">Create a consent form for this admission</p>
                </div>
            ) : (
                <div className="space-y-3">
                    {consents.map(c => (
                        <div key={c.id} className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                            <div className="flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-gray-50 transition-colors"
                                onClick={() => setExpandedId(expandedId === c.id ? null : c.id)}>
                                <div className="flex items-center gap-3">
                                    <span className="text-2xl">{TYPE_ICONS[c.consentType] || "📄"}</span>
                                    <div>
                                        <div className="flex items-center gap-2">
                                            <span className="text-sm font-semibold text-gray-900">{c.consentType} Consent</span>
                                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${STATUS_STYLES[c.status] || "bg-gray-100 text-gray-600"}`}>{c.status}</span>
                                            {c.version > 1 && <span className="text-[10px] px-1.5 py-0.5 bg-purple-100 text-purple-700 rounded-full font-semibold">v{c.version}</span>}
                                        </div>
                                        <p className="text-xs text-gray-400 mt-0.5">{c.language} • {c.signatureType} signature{c.createdAt && ` • ${new Date(c.createdAt).toLocaleDateString()}`}</p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2">
                                    {canEdit && c.status === "DRAFT" && (
                                        <>
                                            <button onClick={(e) => { e.stopPropagation(); setSignModal({ open: true, consentId: c.id, saving: false }); setSignForm({ signatureType: c.signatureType || "WET", patientSigned: false, guardianSigned: false, relationship: "" }); }}
                                                className="px-2.5 py-1 text-xs font-semibold bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg transition-colors">Sign</button>
                                            <button onClick={(e) => { e.stopPropagation(); handleSubmit(c.id); }}
                                                className="px-2.5 py-1 text-xs font-semibold bg-green-50 hover:bg-green-100 text-green-700 border border-green-200 rounded-lg transition-colors">Submit & Lock</button>
                                        </>
                                    )}
                                    <svg className={`h-4 w-4 text-gray-400 transition-transform ${expandedId === c.id ? "rotate-180" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" /></svg>
                                </div>
                            </div>
                            {expandedId === c.id && (
                                <div className="px-4 pb-4 border-t border-gray-100 bg-gray-50">
                                    <div className="grid grid-cols-2 gap-x-6 gap-y-2 mt-3 text-sm">
                                        <div><span className="text-xs text-gray-500 font-medium">Encounter</span><p className="font-medium text-gray-800">{c.encounterType}</p></div>
                                        <div><span className="text-xs text-gray-500 font-medium">Language</span><p className="font-medium text-gray-800">{c.language}</p></div>
                                        <div><span className="text-xs text-gray-500 font-medium">Patient Signed</span><p className={`font-semibold ${c.patientSigned ? "text-green-600" : "text-red-500"}`}>{c.patientSigned ? "Yes ✓" : "No"}</p></div>
                                        <div><span className="text-xs text-gray-500 font-medium">Guardian Signed</span><p className={`font-semibold ${c.guardianSigned ? "text-green-600" : "text-gray-400"}`}>{c.guardianSigned ? `Yes ✓ (${c.relationship || "N/A"})` : "No"}</p></div>
                                        {c.remarks && <div className="col-span-2"><span className="text-xs text-gray-500 font-medium">Remarks</span><p className="text-gray-700 mt-0.5">{c.remarks}</p></div>}
                                        {c.signedAt && <div className="col-span-2"><span className="text-xs text-gray-500 font-medium">Signed At</span><p className="text-gray-700">{new Date(c.signedAt).toLocaleString()}</p></div>}
                                    </div>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {createModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
                            <h3 className="text-base font-bold text-gray-900">New Consent Form</h3>
                            <button onClick={() => setCreateModal({ open: false, saving: false })} className="text-gray-400 hover:text-gray-600"><svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg></button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Consent Type *</label>
                                    <select value={form.consentType} onChange={e => setForm(p => ({ ...p, consentType: e.target.value }))} className={InputCls}>{CONSENT_TYPES.map(t => <option key={t}>{t}</option>)}</select></div>
                                {form.consentType === "SURGERY" && (
                                    <>
                                        <div><label className="text-[11px] font-medium text-gray-600">Planned Procedure *</label>
                                            <input type="text" value={form.procedureName} onChange={e => setForm(p => ({ ...p, procedureName: e.target.value }))} className={InputCls} placeholder="e.g. Laparoscopic Cholecystectomy" /></div>
                                        <div><label className="text-[11px] font-medium text-gray-600">Surgeon</label>
                                            <input type="text" value={form.surgeonName} onChange={e => setForm(p => ({ ...p, surgeonName: e.target.value }))} className={InputCls} /></div>
                                    </>
                                )}
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Language *</label>
                                    <select value={form.language} onChange={e => setForm(p => ({ ...p, language: e.target.value }))} className={InputCls}>{LANGUAGES.map(l => <option key={l}>{l}</option>)}</select></div>
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Signature Type</label>
                                    <select value={form.signatureType} onChange={e => setForm(p => ({ ...p, signatureType: e.target.value }))} className={InputCls}>{SIGNATURE_TYPES.map(s => <option key={s}>{s}</option>)}</select></div>
                                <div className="flex flex-col justify-end gap-2">
                                    <label className="flex items-center gap-2 cursor-pointer"><input type="checkbox" checked={form.patientSigned} onChange={e => setForm(p => ({ ...p, patientSigned: e.target.checked }))} className="w-4 h-4 text-blue-600 rounded border-gray-300" /><span className="text-sm text-gray-700">Patient Signed</span></label>
                                    <label className="flex items-center gap-2 cursor-pointer"><input type="checkbox" checked={form.guardianSigned} onChange={e => setForm(p => ({ ...p, guardianSigned: e.target.checked }))} className="w-4 h-4 text-blue-600 rounded border-gray-300" /><span className="text-sm text-gray-700">Guardian Signed</span></label>
                                </div>
                            </div>
                            {form.guardianSigned && (
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Guardian Relationship</label>
                                    <input value={form.relationship} onChange={e => setForm(p => ({ ...p, relationship: e.target.value }))} placeholder="e.g. Spouse, Parent" className={InputCls} /></div>
                            )}
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Remarks</label>
                                <textarea value={form.remarks} onChange={e => setForm(p => ({ ...p, remarks: e.target.value }))} rows={2} placeholder="Optional notes..." className={`${InputCls} resize-none`} /></div>
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl">
                            <button onClick={() => setCreateModal({ open: false, saving: false })} className={BtnSecondary}>Cancel</button>
                            <button onClick={handleCreate} disabled={createModal.saving} className={BtnPrimary}>{createModal.saving ? "Saving..." : "Create Draft"}</button>
                        </div>
                    </div>
                </div>
            )}

            {signModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
                            <h3 className="text-base font-bold text-gray-900">Sign Consent</h3>
                            <button onClick={() => setSignModal({ open: false, consentId: null, saving: false })} className="text-gray-400 hover:text-gray-600"><svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg></button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Signature Type</label>
                                <select value={signForm.signatureType} onChange={e => setSignForm(p => ({ ...p, signatureType: e.target.value }))} className={InputCls}>{SIGNATURE_TYPES.map(s => <option key={s}>{s}</option>)}</select></div>
                            <div className="space-y-2">
                                <label className="flex items-center gap-2 cursor-pointer"><input type="checkbox" checked={signForm.patientSigned} onChange={e => setSignForm(p => ({ ...p, patientSigned: e.target.checked }))} className="w-4 h-4 text-blue-600 rounded border-gray-300" /><span className="text-sm text-gray-700">Patient has signed</span></label>
                                <label className="flex items-center gap-2 cursor-pointer"><input type="checkbox" checked={signForm.guardianSigned} onChange={e => setSignForm(p => ({ ...p, guardianSigned: e.target.checked }))} className="w-4 h-4 text-blue-600 rounded border-gray-300" /><span className="text-sm text-gray-700">Guardian has signed</span></label>
                            </div>
                            {signForm.guardianSigned && (
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Guardian Relationship</label>
                                    <input value={signForm.relationship} onChange={e => setSignForm(p => ({ ...p, relationship: e.target.value }))} placeholder="e.g. Spouse" className={InputCls} /></div>
                            )}
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl">
                            <button onClick={() => setSignModal({ open: false, consentId: null, saving: false })} className={BtnSecondary}>Cancel</button>
                            <button onClick={handleSign} disabled={signModal.saving} className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-lg transition-colors">{signModal.saving ? "Saving..." : "Confirm Signature"}</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ConsentPanel;
