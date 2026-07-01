import React, { useEffect, useState, useCallback } from "react";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.message || "An error occurred";

const STATUS_STYLES = {
    DRAFT:     "bg-amber-50 text-amber-700 border border-amber-200",
    FINALIZED: "bg-green-50 text-green-700 border border-green-200",
    AMENDED:   "bg-purple-50 text-purple-700 border border-purple-200",
};

const InputCls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent";
const TextAreaCls = `${InputCls} resize-none`;

const SectionCard = ({ title, icon, children, isOpen, onToggle }) => (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
        <button onClick={onToggle} className="w-full flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors text-left">
            <span className="text-sm font-bold text-gray-800">{icon} {title}</span>
            <svg className={`h-4 w-4 text-gray-400 transition-transform ${isOpen ? "rotate-180" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" /></svg>
        </button>
        {isOpen && <div className="px-4 pb-4 border-t border-gray-100">{children}</div>}
    </div>
);

const VitalsCard = ({ vitals }) => {
    if (!vitals) return null;
    const items = [
        { label: "Temperature", value: vitals.temperature ? `${vitals.temperature} °C` : null },
        { label: "Pulse", value: vitals.pulseRate ? `${vitals.pulseRate} bpm` : null },
        { label: "BP", value: (vitals.bpSystolic && vitals.bpDiastolic) ? `${vitals.bpSystolic}/${vitals.bpDiastolic} mmHg` : null },
        { label: "SpO2", value: vitals.oxygenSaturation ? `${vitals.oxygenSaturation}%` : null },
        { label: "RR", value: vitals.respiratoryRate ? `${vitals.respiratoryRate} /min` : null },
        { label: "Weight", value: vitals.weight ? `${vitals.weight} kg` : null },
    ].filter(i => i.value);
    if (items.length === 0) return null;
    return (
        <div className="p-4 bg-blue-50 border border-blue-100 rounded-xl">
            <p className="text-xs font-bold text-blue-700 uppercase mb-2">📊 Latest Vitals (Auto-imported)</p>
            <div className="grid grid-cols-3 gap-2">
                {items.map(({ label, value }) => (
                    <div key={label} className="bg-white/70 rounded-lg px-3 py-2 text-center">
                        <p className="text-[10px] text-gray-500 font-medium">{label}</p>
                        <p className="text-sm font-bold text-gray-800 mt-0.5">{value}</p>
                    </div>
                ))}
            </div>
        </div>
    );
};

const ClinicalAssessmentPanel = ({ admissionId, patientId, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const isDoctor = authService.isDoctor();
    const isAdmin = user?.role === "HOSPITAL_ADMIN";
    const canEdit = (isDoctor || isAdmin) && !isLocked;

    const [assessment, setAssessment] = useState(null);
    const [history, setHistory] = useState([]);
    const [latestVitals, setLatestVitals] = useState(null);
    const [loading, setLoading] = useState(true);
    const [mode, setMode] = useState("view");   // "view" | "edit"
    const [saving, setSaving] = useState(false);
    const [finalizing, setFinalizing] = useState(false);
    const [openSections, setOpenSections] = useState({ chiefComplaint: true, hpi: true, exam: false, emr: false, diagnosis: false, plan: false });

    const toggleSection = (key) => setOpenSections(p => ({ ...p, [key]: !p[key] }));

    // Form state
    const [form, setForm] = useState({
        chiefComplaint: "",
        historyPresentIllness: "",
        physicalExam: "",
        provisionalDiagnosis: "",
        treatmentPlan: "",
        // EMR longitudinal (pre-populated)
        pastMedicalHistory: "",
        pastSurgicalHistory: "",
        familyHistory: "",
        socialHistory: "",
        currentMedications: "",
        knownAllergies: "",
    });

    // Finalize extras
    const [finalizeModal, setFinalizeModal] = useState({ open: false, saving: false });
    const [finalizeForm, setFinalizeForm] = useState({ finalDiagnosis: "", icdCode: "", additionalOrders: "" });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [asmData, histData] = await Promise.all([
                hospitalService.getClinicalAssessmentByAdmission(admissionId).catch(() => null),
                hospitalService.getPatientClinicalHistory(patientId).catch(() => null),
            ]);
            setAssessment(asmData);
            if (histData) {
                setLatestVitals(histData.latestVitals || null);
                // Pre-populate form from longitudinal history
                setForm(prev => ({
                    ...prev,
                    pastMedicalHistory: (histData.medicalHistory || []).map(h => h.condition).join(", "),
                    pastSurgicalHistory: (histData.surgicalHistory || []).map(h => `${h.procedure} (${h.year || "?"})` ).join("; "),
                    familyHistory: (histData.familyHistory || []).map(h => h.condition).join(", "),
                    socialHistory: histData.socialHistory?.smokingStatus ? `Smoking: ${histData.socialHistory.smokingStatus}` : "",
                    currentMedications: (histData.medicationHistory || []).map(m => m.medicineName).join(", "),
                    knownAllergies: (histData.allergies || []).map(a => a.allergyName).join(", "),
                }));
            }
            if (asmData) {
                setForm(prev => ({
                    ...prev,
                    chiefComplaint: asmData.chiefComplaint || "",
                    historyPresentIllness: asmData.historyPresentIllness || "",
                    physicalExam: asmData.physicalExam || "",
                    provisionalDiagnosis: asmData.provisionalDiagnosis || "",
                    treatmentPlan: asmData.treatmentPlan || "",
                }));
            }
        } catch { } finally { setLoading(false); }
    }, [admissionId, patientId]);

    useEffect(() => { load(); }, [load]);

    const handleSaveDraft = async () => {
        setSaving(true);
        try {
            if (assessment && assessment.status === "DRAFT") {
                await hospitalService.updateClinicalAssessment(assessment.id, form);
                success("Assessment draft updated");
            } else {
                await hospitalService.createClinicalAssessmentDraft({ admissionId: Number(admissionId), patientId: Number(patientId), ...form });
                success("Assessment draft created");
            }
            setMode("view");
            load();
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const handleFinalize = async () => {
        setFinalizeModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.finalizeClinicalAssessment(assessment.id, {
                finalDiagnosis: finalizeForm.finalDiagnosis || form.provisionalDiagnosis,
                icdCode: finalizeForm.icdCode,
                additionalOrders: finalizeForm.additionalOrders ? finalizeForm.additionalOrders.split("\n") : [],
            });
            success("Assessment finalized");
            setFinalizeModal({ open: false, saving: false });
            setMode("view");
            load();
        } catch (e) { toastError(parseErr(e)); setFinalizeModal(p => ({ ...p, saving: false })); }
    };

    const handleAmend = async () => {
        try {
            await hospitalService.amendClinicalAssessment(assessment.id, { reason: "Amendment requested" });
            success("Amendment version created — you can now edit the draft");
            load();
        } catch (e) { toastError(parseErr(e)); }
    };

    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-base font-bold text-gray-900">Initial Clinical Assessment</h2>
                    <p className="text-xs text-gray-500 mt-0.5">Form 07 — Admission History & EMR Spine</p>
                </div>
                <div className="flex items-center gap-2">
                    {assessment && (
                        <span className={`text-xs font-bold px-2.5 py-1 rounded-full border ${STATUS_STYLES[assessment.status] || "bg-gray-100 text-gray-600"}`}>
                            {assessment.status} {assessment.version > 1 ? `v${assessment.version}` : ""}
                        </span>
                    )}
                    {canEdit && mode === "view" && (
                        <>
                            {!assessment && (
                                <button onClick={() => setMode("edit")}
                                    className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">
                                    Start Assessment
                                </button>
                            )}
                            {assessment?.status === "DRAFT" && (
                                <>
                                    <button onClick={() => setMode("edit")} className="px-3 py-1.5 bg-amber-500 hover:bg-amber-600 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">Edit Draft</button>
                                    <button onClick={() => setFinalizeModal({ open: true, saving: false })} className="px-3 py-1.5 bg-green-600 hover:bg-green-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">Finalize</button>
                                </>
                            )}
                            {assessment?.status === "FINALIZED" && (
                                <button onClick={handleAmend} className="px-3 py-1.5 bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 text-xs font-semibold rounded-lg transition-colors">Amend</button>
                            )}
                        </>
                    )}
                </div>
            </div>

            {loading ? (
                <div className="space-y-3">{[1, 2, 3].map(i => <div key={i} className="h-20 bg-gray-100 rounded-xl animate-pulse" />)}</div>
            ) : mode === "edit" ? (
                /* ——— Edit Mode ——— */
                <div className="space-y-3">
                    {latestVitals && <VitalsCard vitals={latestVitals} />}

                    <SectionCard title="Chief Complaint" icon="🗣️" isOpen={openSections.chiefComplaint} onToggle={() => toggleSection("chiefComplaint")}>
                        <textarea value={form.chiefComplaint} onChange={e => setForm(p => ({ ...p, chiefComplaint: e.target.value }))} rows={3}
                            placeholder="Patient's presenting complaint in their own words..." className={`${TextAreaCls} mt-3`} />
                    </SectionCard>

                    <SectionCard title="History of Present Illness" icon="📋" isOpen={openSections.hpi} onToggle={() => toggleSection("hpi")}>
                        <textarea value={form.historyPresentIllness} onChange={e => setForm(p => ({ ...p, historyPresentIllness: e.target.value }))} rows={5}
                            placeholder="Onset, duration, character, radiation, aggravating/relieving factors, associated symptoms..." className={`${TextAreaCls} mt-3`} />
                    </SectionCard>

                    <SectionCard title="Physical Examination Findings" icon="🩺" isOpen={openSections.exam} onToggle={() => toggleSection("exam")}>
                        <textarea value={form.physicalExam} onChange={e => setForm(p => ({ ...p, physicalExam: e.target.value }))} rows={6}
                            placeholder="General appearance, systems review (CVS, Resp, Abdomen, Neuro, MSK)..." className={`${TextAreaCls} mt-3`} />
                    </SectionCard>

                    <SectionCard title="Longitudinal EMR History (Pre-populated)" icon="📚" isOpen={openSections.emr} onToggle={() => toggleSection("emr")}>
                        <div className="grid grid-cols-1 gap-3 mt-3">
                            {[
                                { key: "pastMedicalHistory", label: "Past Medical History" },
                                { key: "pastSurgicalHistory", label: "Past Surgical History" },
                                { key: "familyHistory", label: "Family History" },
                                { key: "socialHistory", label: "Social History" },
                                { key: "currentMedications", label: "Current Medications" },
                                { key: "knownAllergies", label: "Known Allergies" },
                            ].map(({ key, label }) => (
                                <div key={key}>
                                    <label className="block text-xs font-semibold text-gray-600 mb-1">{label}</label>
                                    <input value={form[key]} onChange={e => setForm(p => ({ ...p, [key]: e.target.value }))} className={InputCls} placeholder={`Enter ${label.toLowerCase()}...`} />
                                </div>
                            ))}
                        </div>
                    </SectionCard>

                    <SectionCard title="Provisional Diagnosis" icon="🔬" isOpen={openSections.diagnosis} onToggle={() => toggleSection("diagnosis")}>
                        <textarea value={form.provisionalDiagnosis} onChange={e => setForm(p => ({ ...p, provisionalDiagnosis: e.target.value }))} rows={3}
                            placeholder="Primary diagnosis and differentials..." className={`${TextAreaCls} mt-3`} />
                    </SectionCard>

                    <SectionCard title="Management Plan" icon="💊" isOpen={openSections.plan} onToggle={() => toggleSection("plan")}>
                        <textarea value={form.treatmentPlan} onChange={e => setForm(p => ({ ...p, treatmentPlan: e.target.value }))} rows={4}
                            placeholder="Investigations ordered, medications, procedures, dietary advice, referrals..." className={`${TextAreaCls} mt-3`} />
                    </SectionCard>

                    <div className="flex justify-end gap-3 pt-2">
                        <button onClick={() => setMode("view")} className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                        <button onClick={handleSaveDraft} disabled={saving}
                            className="px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors flex items-center gap-2">
                            {saving && <svg className="animate-spin h-3.5 w-3.5" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/></svg>}
                            {saving ? "Saving..." : "Save Draft"}
                        </button>
                    </div>
                </div>
            ) : assessment ? (
                /* ——— View mode ——— */
                <div className="space-y-3">
                    {latestVitals && <VitalsCard vitals={latestVitals} />}

                    {[
                        { label: "Chief Complaint", value: assessment.chiefComplaint, icon: "🗣️" },
                        { label: "History of Present Illness", value: assessment.historyPresentIllness, icon: "📋" },
                        { label: "Physical Examination", value: assessment.physicalExam, icon: "🩺" },
                        { label: "Provisional Diagnosis", value: assessment.provisionalDiagnosis, icon: "🔬" },
                        { label: "Management Plan", value: assessment.treatmentPlan, icon: "💊" },
                    ].filter(s => s.value).map(({ label, value, icon }) => (
                        <div key={label} className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm">
                            <p className="text-xs font-bold text-gray-500 uppercase tracking-wide mb-2">{icon} {label}</p>
                            <p className="text-sm text-gray-800 whitespace-pre-wrap leading-relaxed">{value}</p>
                        </div>
                    ))}

                    {assessment.status === "FINALIZED" && assessment.finalizedAt && (
                        <div className="flex items-center gap-2 px-4 py-2.5 bg-green-50 border border-green-200 rounded-xl text-xs text-green-700">
                            <svg className="h-4 w-4 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                            Finalized on {new Date(assessment.finalizedAt).toLocaleString()}
                        </div>
                    )}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-14 text-center">
                    <span className="text-4xl mb-3">🩺</span>
                    <p className="text-sm font-medium text-gray-700">No clinical assessment recorded</p>
                    {canEdit && <p className="text-xs text-gray-400 mt-1">Start the initial clinical assessment for this admission</p>}
                </div>
            )}

            {/* Finalize Modal */}
            {finalizeModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
                            <h3 className="text-base font-bold text-gray-900">✅ Finalize Assessment</h3>
                            <button onClick={() => setFinalizeModal({ open: false, saving: false })} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-700">
                                ⚠️ Finalizing locks the assessment. Use &ldquo;Amend&rdquo; to make corrections after finalization.
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-600 mb-1.5">Final Diagnosis (leave blank to use provisional)</label>
                                <textarea value={finalizeForm.finalDiagnosis} onChange={e => setFinalizeForm(p => ({ ...p, finalDiagnosis: e.target.value }))} rows={2}
                                    placeholder={form.provisionalDiagnosis || "Final confirmed diagnosis..."} className={`${TextAreaCls}`} />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-600 mb-1.5">ICD-10 Code (optional)</label>
                                <input value={finalizeForm.icdCode} onChange={e => setFinalizeForm(p => ({ ...p, icdCode: e.target.value }))} placeholder="e.g. J18.9" className={InputCls} />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-600 mb-1.5">Additional Orders to Spawn (one per line)</label>
                                <textarea value={finalizeForm.additionalOrders} onChange={e => setFinalizeForm(p => ({ ...p, additionalOrders: e.target.value }))} rows={3}
                                    placeholder="e.g. CBC with differential&#10;Chest X-ray PA view&#10;ECG 12-lead" className={`${TextAreaCls}`} />
                            </div>
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl">
                            <button onClick={() => setFinalizeModal({ open: false, saving: false })} className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                            <button onClick={handleFinalize} disabled={finalizeModal.saving}
                                className="px-4 py-2 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 disabled:opacity-50 rounded-lg transition-colors">
                                {finalizeModal.saving ? "Finalizing..." : "Confirm Finalize"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ClinicalAssessmentPanel;
