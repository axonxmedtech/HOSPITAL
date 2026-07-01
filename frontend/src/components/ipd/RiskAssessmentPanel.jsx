import React, { useEffect, useState, useCallback } from "react";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const RISK_BADGE = {
    LOW:    "bg-green-50 text-green-700 border border-green-200",
    MEDIUM: "bg-amber-50 text-amber-700 border border-amber-200",
    HIGH:   "bg-red-50 text-red-700 border border-red-200",
};

const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.message || "An error occurred";

// Client-side scoring helpers
const scoreFallRisk = (f) => {
    let s = 0;
    if (f.ageOver65) s++;
    if (f.historyOfFalls) s++;
    if (f.mobilityAid) s++;
    if (f.sedated) s++;
    if (f.weakBalance) s++;
    return s >= 3 ? "HIGH" : s >= 1 ? "MEDIUM" : "LOW";
};

const scoreUlcerRisk = (u) => {
    let s = 0;
    if (u.sensoryImpaired) s++;
    if (u.bedridden) s++;
    if (u.incontinent) s++;
    if (u.poorNutrition) s++;
    return s >= 3 ? "HIGH" : s >= 1 ? "MEDIUM" : "LOW";
};

const scoreNutritionRisk = (n) => {
    let s = 0;
    if (n.bmiLow) s++;
    if (n.unintendedWeightLoss) s++;
    if (n.poorAppetite) s++;
    return s >= 2 ? "HIGH" : s >= 1 ? "MEDIUM" : "LOW";
};

const overallRisk = (fall, ulcer, nutrition) => {
    if ([fall, ulcer, nutrition].includes("HIGH")) return "HIGH";
    if ([fall, ulcer, nutrition].includes("MEDIUM")) return "MEDIUM";
    return "LOW";
};

const RiskBadge = ({ level }) => (
    <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${RISK_BADGE[level] || "bg-gray-100 text-gray-600"}`}>
        {level === "HIGH" ? "🔴" : level === "MEDIUM" ? "🟡" : "🟢"} {level}
    </span>
);

const CheckItem = ({ label, checked, onChange, disabled }) => (
    <label className={`flex items-center gap-2 ${disabled ? "opacity-60 cursor-not-allowed" : "cursor-pointer"}`}>
        <input type="checkbox" checked={checked} onChange={e => !disabled && onChange(e.target.checked)}
            disabled={disabled} className="w-4 h-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500" />
        <span className="text-sm text-gray-700">{label}</span>
    </label>
);

const Section = ({ title, risk, children }) => (
    <div className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm">
        <div className="flex items-center justify-between mb-3">
            <h4 className="text-sm font-bold text-gray-800">{title}</h4>
            <RiskBadge level={risk} />
        </div>
        <div className="space-y-2">{children}</div>
    </div>
);

const RiskAssessmentPanel = ({ admissionId, patientId, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const isDoctor = authService.isDoctor();
    const isNurse = user?.role === "NURSE";
    const isAdmin = user?.role === "HOSPITAL_ADMIN";
    const canAssess = (isDoctor || isNurse || isAdmin) && !isLocked;

    const [assessments, setAssessments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showHistory, setShowHistory] = useState(false);
    const [mode, setMode] = useState("view"); // "view" | "edit"

    // Review modal
    const [reviewModal, setReviewModal] = useState({ open: false, id: null, notes: "", saving: false });

    // Fall risk inputs
    const [fall, setFall] = useState({ ageOver65: false, historyOfFalls: false, mobilityAid: false, sedated: false, weakBalance: false });
    // Pressure ulcer inputs
    const [ulcer, setUlcer] = useState({ sensoryImpaired: false, bedridden: false, incontinent: false, poorNutrition: false });
    // Nutrition inputs
    const [nutrition, setNutrition] = useState({ bmiLow: false, unintendedWeightLoss: false, poorAppetite: false });
    // Other
    const [other, setOther] = useState({ mentalStatus: "ALERT", infectionRisk: false, allergyRisk: false, isolationRequired: false });
    const [saving, setSaving] = useState(false);

    const fallRisk = scoreFallRisk(fall);
    const ulcerRisk = scoreUlcerRisk(ulcer);
    const nutritionRisk = scoreNutritionRisk(nutrition);
    const overall = overallRisk(fallRisk, ulcerRisk, nutritionRisk);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await hospitalService.getRiskAssessmentByAdmission(admissionId);
            setAssessments(Array.isArray(data) ? data : data ? [data] : []);
        } catch { setAssessments([]); } finally { setLoading(false); }
    }, [admissionId]);

    useEffect(() => { load(); }, [load]);

    const handleSave = async () => {
        setSaving(true);
        try {
            await hospitalService.saveRiskAssessment({
                admissionId: Number(admissionId),
                patientId: Number(patientId),
                fallRisk,
                pressureUlcerRisk: ulcerRisk,
                nutritionRisk,
                overallRisk: overall,
                mentalStatus: other.mentalStatus,
                infectionRisk: other.infectionRisk,
                allergyRisk: other.allergyRisk,
                isolationRequired: other.isolationRequired,
                inputsJson: JSON.stringify({ fall, ulcer, nutrition, other }),
                status: "ASSESSED",
            });
            success("Risk assessment saved");
            setMode("view");
            load();
        } catch (e) { toastError(parseErr(e)); } finally { setSaving(false); }
    };

    const handleReview = async () => {
        setReviewModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.reviewRiskAssessment(reviewModal.id, { reviewNotes: reviewModal.notes });
            success("Review saved");
            setReviewModal({ open: false, id: null, notes: "", saving: false });
            load();
        } catch (e) { toastError(parseErr(e)); setReviewModal(p => ({ ...p, saving: false })); }
    };

    const latest = assessments[0];

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-base font-bold text-gray-900">Risk Assessment</h2>
                    <p className="text-xs text-gray-500 mt-0.5">Form 06 — NABH Vulnerability & Risk Screening</p>
                </div>
                <div className="flex items-center gap-2">
                    {assessments.length > 1 && (
                        <button onClick={() => setShowHistory(h => !h)} className="text-xs text-blue-600 hover:text-blue-800 font-medium underline">
                            {showHistory ? "Hide" : "Show"} history ({assessments.length})
                        </button>
                    )}
                    {canAssess && mode === "view" && (
                        <button onClick={() => setMode("edit")}
                            className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">
                            {latest ? "Re-assess" : "New Assessment"}
                        </button>
                    )}
                </div>
            </div>

            {loading ? (
                <div className="space-y-3">{[1, 2, 3].map(i => <div key={i} className="h-20 bg-gray-100 rounded-xl animate-pulse" />)}</div>
            ) : mode === "edit" ? (
                /* ——— Assessment Form ——— */
                <div className="space-y-4">
                    {/* Overall preview */}
                    <div className="flex items-center gap-3 p-4 bg-gradient-to-r from-slate-50 to-gray-50 border border-gray-200 rounded-xl">
                        <div className="flex-1">
                            <p className="text-xs text-gray-500 font-medium">Overall Risk Score</p>
                            <p className="text-lg font-bold text-gray-900 mt-0.5">Live Preview</p>
                        </div>
                        <RiskBadge level={overall} />
                    </div>

                    <Section title="🚶 Fall Risk" risk={fallRisk}>
                        <CheckItem label="Age > 65 years" checked={fall.ageOver65} onChange={v => setFall(p => ({ ...p, ageOver65: v }))} />
                        <CheckItem label="History of falls in past 3 months" checked={fall.historyOfFalls} onChange={v => setFall(p => ({ ...p, historyOfFalls: v }))} />
                        <CheckItem label="Uses mobility aid (walker/crutches)" checked={fall.mobilityAid} onChange={v => setFall(p => ({ ...p, mobilityAid: v }))} />
                        <CheckItem label="Sedated / on sedating medications" checked={fall.sedated} onChange={v => setFall(p => ({ ...p, sedated: v }))} />
                        <CheckItem label="Weak balance / dizziness" checked={fall.weakBalance} onChange={v => setFall(p => ({ ...p, weakBalance: v }))} />
                    </Section>

                    <Section title="🩹 Pressure Ulcer Risk" risk={ulcerRisk}>
                        <CheckItem label="Sensory impairment" checked={ulcer.sensoryImpaired} onChange={v => setUlcer(p => ({ ...p, sensoryImpaired: v }))} />
                        <CheckItem label="Bedridden / immobile" checked={ulcer.bedridden} onChange={v => setUlcer(p => ({ ...p, bedridden: v }))} />
                        <CheckItem label="Incontinent (bladder or bowel)" checked={ulcer.incontinent} onChange={v => setUlcer(p => ({ ...p, incontinent: v }))} />
                        <CheckItem label="Poor nutrition / low albumin" checked={ulcer.poorNutrition} onChange={v => setUlcer(p => ({ ...p, poorNutrition: v }))} />
                    </Section>

                    <Section title="🥗 Nutrition Risk" risk={nutritionRisk}>
                        <CheckItem label="BMI &lt; 18.5 (underweight)" checked={nutrition.bmiLow} onChange={v => setNutrition(p => ({ ...p, bmiLow: v }))} />
                        <CheckItem label="Unintended weight loss (&gt;5% in 3 months)" checked={nutrition.unintendedWeightLoss} onChange={v => setNutrition(p => ({ ...p, unintendedWeightLoss: v }))} />
                        <CheckItem label="Poor appetite / eating difficulty" checked={nutrition.poorAppetite} onChange={v => setNutrition(p => ({ ...p, poorAppetite: v }))} />
                    </Section>

                    <div className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm space-y-3">
                        <h4 className="text-sm font-bold text-gray-800">🧠 Other Risk Factors</h4>
                        <div>
                            <label className="block text-xs font-semibold text-gray-600 mb-1.5">Mental / Cognitive Status</label>
                            <select value={other.mentalStatus} onChange={e => setOther(p => ({ ...p, mentalStatus: e.target.value }))}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                                <option value="ALERT">Alert and Oriented</option>
                                <option value="CONFUSED">Confused / Disoriented</option>
                                <option value="LETHARGIC">Lethargic</option>
                                <option value="UNCONSCIOUS">Unconscious</option>
                            </select>
                        </div>
                        <div className="grid grid-cols-3 gap-3">
                            <CheckItem label="Infection risk" checked={other.infectionRisk} onChange={v => setOther(p => ({ ...p, infectionRisk: v }))} />
                            <CheckItem label="Allergy risk" checked={other.allergyRisk} onChange={v => setOther(p => ({ ...p, allergyRisk: v }))} />
                            <CheckItem label="Isolation required" checked={other.isolationRequired} onChange={v => setOther(p => ({ ...p, isolationRequired: v }))} />
                        </div>
                    </div>

                    <div className="flex justify-end gap-3">
                        <button onClick={() => setMode("view")} className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                        <button onClick={handleSave} disabled={saving}
                            className="px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors flex items-center gap-2">
                            {saving && <svg className="animate-spin h-3.5 w-3.5" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/></svg>}
                            {saving ? "Saving..." : "Save Assessment"}
                        </button>
                    </div>
                </div>
            ) : latest ? (
                /* ——— View mode ——— */
                <div className="space-y-4">
                    {/* Summary card */}
                    <div className={`p-5 rounded-2xl border-2 ${latest.overallRisk === "HIGH" ? "bg-red-50 border-red-200" : latest.overallRisk === "MEDIUM" ? "bg-amber-50 border-amber-200" : "bg-green-50 border-green-200"}`}>
                        <div className="flex items-center justify-between mb-4">
                            <div>
                                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Overall Risk</p>
                                <div className="flex items-center gap-3 mt-1">
                                    <span className={`text-2xl font-black ${latest.overallRisk === "HIGH" ? "text-red-700" : latest.overallRisk === "MEDIUM" ? "text-amber-700" : "text-green-700"}`}>{latest.overallRisk}</span>
                                    {latest.overallRisk === "HIGH" && <span className="text-2xl">🔴</span>}
                                    {latest.overallRisk === "MEDIUM" && <span className="text-2xl">🟡</span>}
                                    {latest.overallRisk === "LOW" && <span className="text-2xl">🟢</span>}
                                </div>
                                <p className="text-xs text-gray-500 mt-1">{latest.createdAt && new Date(latest.createdAt).toLocaleString()}</p>
                            </div>
                            {isDoctor && !isLocked && (
                                <button onClick={() => setReviewModal({ open: true, id: latest.id, notes: "", saving: false })}
                                    className="px-3 py-1.5 text-xs font-semibold bg-white hover:bg-gray-50 border border-gray-300 text-gray-700 rounded-lg shadow-sm transition-colors">
                                    Doctor Review
                                </button>
                            )}
                        </div>
                        <div className="grid grid-cols-3 gap-3">
                            {[
                                { label: "Fall Risk", value: latest.fallRisk },
                                { label: "Pressure Ulcer", value: latest.pressureUlcerRisk },
                                { label: "Nutrition", value: latest.nutritionRisk },
                            ].map(({ label, value }) => (
                                <div key={label} className="bg-white/60 rounded-xl p-3 text-center">
                                    <p className="text-[10px] text-gray-500 font-semibold uppercase">{label}</p>
                                    <RiskBadge level={value || "LOW"} />
                                </div>
                            ))}
                        </div>
                        {(latest.infectionRisk || latest.allergyRisk || latest.isolationRequired) && (
                            <div className="mt-3 flex flex-wrap gap-2">
                                {latest.infectionRisk && <span className="text-xs px-2 py-0.5 bg-orange-100 text-orange-700 rounded-full font-medium">⚠️ Infection Risk</span>}
                                {latest.allergyRisk && <span className="text-xs px-2 py-0.5 bg-red-100 text-red-700 rounded-full font-medium">🚨 Allergy Risk</span>}
                                {latest.isolationRequired && <span className="text-xs px-2 py-0.5 bg-purple-100 text-purple-700 rounded-full font-medium">🔒 Isolation Required</span>}
                            </div>
                        )}
                        {latest.reviewedBy && (
                            <div className="mt-3 pt-3 border-t border-white/60 text-xs text-gray-600">
                                <span className="font-semibold">Doctor Reviewed ✓</span>
                            </div>
                        )}
                    </div>

                    {/* History */}
                    {showHistory && assessments.slice(1).map(a => (
                        <div key={a.id} className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm opacity-75">
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <RiskBadge level={a.overallRisk} />
                                    <span className="text-xs text-gray-400">{a.createdAt && new Date(a.createdAt).toLocaleString()}</span>
                                </div>
                                <div className="flex gap-2 text-xs">
                                    <span className={`px-2 py-0.5 rounded-full ${RISK_BADGE[a.fallRisk]}`}>Fall: {a.fallRisk}</span>
                                    <span className={`px-2 py-0.5 rounded-full ${RISK_BADGE[a.pressureUlcerRisk]}`}>Ulcer: {a.pressureUlcerRisk}</span>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-14 text-center">
                    <span className="text-4xl mb-3">📊</span>
                    <p className="text-sm font-medium text-gray-700">No risk assessment recorded</p>
                    <p className="text-xs text-gray-400 mt-1">Complete a NABH vulnerability screening for this patient</p>
                </div>
            )}

            {/* Doctor Review Modal */}
            {reviewModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
                            <h3 className="text-base font-bold text-gray-900">Doctor Review Notes</h3>
                            <button onClick={() => setReviewModal({ open: false, id: null, notes: "", saving: false })} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="px-6 py-4">
                            <label className="block text-xs font-semibold text-gray-600 mb-1.5">Review Notes</label>
                            <textarea value={reviewModal.notes} onChange={e => setReviewModal(p => ({ ...p, notes: e.target.value }))} rows={4}
                                placeholder="Enter clinical review notes and any follow-up care plan..."
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl">
                            <button onClick={() => setReviewModal({ open: false, id: null, notes: "", saving: false })}
                                className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                            <button onClick={handleReview} disabled={reviewModal.saving}
                                className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors">
                                {reviewModal.saving ? "Saving..." : "Save Review"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default RiskAssessmentPanel;
