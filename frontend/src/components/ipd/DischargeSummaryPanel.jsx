import React, { useEffect, useState, useCallback } from "react";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const DISCHARGE_TYPES = ["REGULAR", "LAMA", "ABSCONDED", "DEATH", "TRANSFER"];
const CONDITIONS = ["RECOVERED", "IMPROVED", "NOT_IMPROVED", "CRITICAL", "EXPIRED"];

const InputCls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.message || "An error occurred";

const emptyForm = {
    dischargeType: "REGULAR", dischargeCondition: "IMPROVED", icdCode: "",
    finalDiagnosis: "", treatmentGiven: "",
    homeMedications: "", dietAdvice: "", activityRestrictions: "",
    followUpDate: "", followUpAdvice: "", referredTo: "",
    dischargeNotes: "",
};

const Section = ({ title, children }) => (
    <div className="border border-gray-200 rounded-xl p-4 bg-white">
        <h4 className="text-sm font-semibold text-gray-700 mb-3">{title}</h4>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">{children}</div>
    </div>
);

const Field = ({ label, children, full }) => (
    <div className={full ? "sm:col-span-2" : ""}>
        <label className="text-xs font-medium text-gray-600">{label}</label>
        {children}
    </div>
);

const ReadRow = ({ label, value }) => (
    <div className="py-2 border-b border-gray-100 last:border-0">
        <div className="text-[11px] uppercase tracking-wide text-gray-400">{label}</div>
        <div className="text-sm text-gray-800 whitespace-pre-wrap">{value || "-"}</div>
    </div>
);

const DischargeSummaryPanel = ({ admissionId, isLocked, ipdStatus }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canEdit = !isLocked && ["DOCTOR", "HOSPITAL_ADMIN"].includes(user?.role) && ipdStatus !== "DISCHARGED";

    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [downloading, setDownloading] = useState(false);
    const [form, setForm] = useState(emptyForm);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await hospitalService.getDischargeSummary(admissionId);
            setSummary(data || null);
            if (data) {
                setForm({
                    dischargeType: data.dischargeType || "REGULAR",
                    dischargeCondition: data.dischargeCondition || "IMPROVED",
                    icdCode: data.icdCode || "",
                    finalDiagnosis: data.finalDiagnosis || "",
                    treatmentGiven: data.treatmentGiven || "",
                    homeMedications: data.homeMedications || "",
                    dietAdvice: data.dietAdvice || "",
                    activityRestrictions: data.activityRestrictions || "",
                    followUpDate: data.followUpDate || "",
                    followUpAdvice: data.followUpAdvice || "",
                    referredTo: data.referredTo || "",
                    dischargeNotes: data.dischargeNotes || "",
                });
            }
            // Auto-open the form when nothing exists yet and the user can edit.
            setEditing(!data);
        } catch {
            setSummary(null);
        } finally {
            setLoading(false);
        }
    }, [admissionId]);

    useEffect(() => { load(); }, [load]);

    const setField = (k, val) => setForm((p) => ({ ...p, [k]: val }));

    const save = async (finalize) => {
        if (!form.finalDiagnosis?.trim()) return toastError("Final diagnosis is required");
        setSaving(true);
        try {
            const payload = { ...form, followUpDate: form.followUpDate || null, status: finalize ? "FINALIZED" : "DRAFT" };
            await hospitalService.planDischarge(admissionId, payload);
            success(finalize ? "Discharge summary finalized" : "Draft saved");
            setEditing(false);
            load();
        } catch (e) {
            toastError(parseErr(e));
        } finally {
            setSaving(false);
        }
    };

    const downloadPdf = async () => {
        setDownloading(true);
        try {
            const blob = await hospitalService.downloadDischargeSummaryPdf(admissionId);
            const url = window.URL.createObjectURL(new Blob([blob], { type: "application/pdf" }));
            const link = document.createElement("a");
            link.href = url;
            link.setAttribute("download", `discharge_summary_${admissionId}.pdf`);
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
            success("Discharge summary PDF downloaded");
        } catch {
            toastError("Failed to download discharge summary PDF");
        } finally {
            setDownloading(false);
        }
    };

    if (loading) return <div className="text-center text-gray-400 py-10 text-sm">Loading discharge summary…</div>;

    // ---- READ-ONLY VIEW ----
    if (summary && !editing) {
        return (
            <div className="space-y-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-800">Discharge Summary</h3>
                        <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${summary.status === "FINALIZED" ? "bg-green-100 text-green-700" : "bg-yellow-100 text-yellow-800"}`}>
                            {summary.status || "DRAFT"}
                        </span>
                    </div>
                    <div className="flex items-center gap-2">
                        {canEdit && (
                            <button onClick={() => setEditing(true)} className="border border-gray-300 hover:bg-gray-50 text-sm font-medium px-4 py-2 rounded-lg text-gray-700">
                                ✏️ Edit Draft
                            </button>
                        )}
                        <button onClick={downloadPdf} disabled={downloading} className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-medium px-4 py-2 rounded-lg">
                            {downloading ? "Preparing…" : "📥 Download PDF"}
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <div className="border border-gray-200 rounded-xl p-4 bg-white">
                        <h4 className="text-sm font-semibold text-gray-700 mb-2">Discharge Details</h4>
                        <ReadRow label="Discharge Type" value={summary.dischargeType} />
                        <ReadRow label="Condition at Discharge" value={summary.dischargeCondition} />
                        <ReadRow label="ICD-10 Code" value={summary.icdCode} />
                    </div>
                    <div className="border border-gray-200 rounded-xl p-4 bg-white">
                        <h4 className="text-sm font-semibold text-gray-700 mb-2">Clinical Summary</h4>
                        <ReadRow label="Final Diagnosis" value={summary.finalDiagnosis} />
                        <ReadRow label="Treatment Given" value={summary.treatmentGiven} />
                    </div>
                    <div className="border border-gray-200 rounded-xl p-4 bg-white">
                        <h4 className="text-sm font-semibold text-gray-700 mb-2">Discharge Instructions</h4>
                        <ReadRow label="Home Medications" value={summary.homeMedications} />
                        <ReadRow label="Diet Advice" value={summary.dietAdvice} />
                        <ReadRow label="Activity Restrictions" value={summary.activityRestrictions} />
                    </div>
                    <div className="border border-gray-200 rounded-xl p-4 bg-white">
                        <h4 className="text-sm font-semibold text-gray-700 mb-2">Follow-up Plan</h4>
                        <ReadRow label="Follow-up Date" value={summary.followUpDate} />
                        <ReadRow label="Follow-up Advice" value={summary.followUpAdvice} />
                        <ReadRow label="Referred To" value={summary.referredTo} />
                    </div>
                    <div className="border border-gray-200 rounded-xl p-4 bg-white lg:col-span-2">
                        <h4 className="text-sm font-semibold text-gray-700 mb-2">Clinical Notes</h4>
                        <ReadRow label="Discharge Notes" value={summary.dischargeNotes} />
                    </div>
                </div>
            </div>
        );
    }

    // ---- EDIT / DRAFT FORM ----
    if (!canEdit) {
        return (
            <div className="text-center text-gray-400 py-10 text-sm border border-dashed border-gray-200 rounded-lg">
                No discharge summary yet.
            </div>
        );
    }

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-gray-800">{summary ? "Edit Discharge Summary" : "Draft Discharge Summary"}</h3>
                {summary && (
                    <button onClick={() => { setEditing(false); load(); }} className="text-sm text-gray-500 hover:text-gray-700">Cancel</button>
                )}
            </div>

            <Section title="1. Discharge Details">
                <Field label="Discharge Type">
                    <select className={InputCls} value={form.dischargeType} onChange={(e) => setField("dischargeType", e.target.value)}>
                        {DISCHARGE_TYPES.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </Field>
                <Field label="Condition at Discharge">
                    <select className={InputCls} value={form.dischargeCondition} onChange={(e) => setField("dischargeCondition", e.target.value)}>
                        {CONDITIONS.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                </Field>
                <Field label="ICD-10 Code">
                    <input type="text" className={InputCls} value={form.icdCode} onChange={(e) => setField("icdCode", e.target.value)} />
                </Field>
            </Section>

            <Section title="2. Clinical Summary">
                <Field label="Final Diagnosis *" full>
                    <textarea rows={2} className={InputCls} value={form.finalDiagnosis} onChange={(e) => setField("finalDiagnosis", e.target.value)} />
                </Field>
                <Field label="Treatment Given" full>
                    <textarea rows={2} className={InputCls} value={form.treatmentGiven} onChange={(e) => setField("treatmentGiven", e.target.value)} />
                </Field>
            </Section>

            <Section title="3. Discharge Instructions">
                <Field label="Home Medications" full>
                    <textarea rows={2} className={InputCls} value={form.homeMedications} onChange={(e) => setField("homeMedications", e.target.value)} />
                </Field>
                <Field label="Diet Advice">
                    <textarea rows={2} className={InputCls} value={form.dietAdvice} onChange={(e) => setField("dietAdvice", e.target.value)} />
                </Field>
                <Field label="Activity Restrictions">
                    <textarea rows={2} className={InputCls} value={form.activityRestrictions} onChange={(e) => setField("activityRestrictions", e.target.value)} />
                </Field>
            </Section>

            <Section title="4. Follow-up Plan">
                <Field label="Follow-up Date">
                    <input type="date" className={InputCls} value={form.followUpDate || ""} onChange={(e) => setField("followUpDate", e.target.value)} />
                </Field>
                <Field label="Referred To">
                    <input type="text" className={InputCls} value={form.referredTo} onChange={(e) => setField("referredTo", e.target.value)} />
                </Field>
                <Field label="Follow-up Advice" full>
                    <textarea rows={2} className={InputCls} value={form.followUpAdvice} onChange={(e) => setField("followUpAdvice", e.target.value)} />
                </Field>
            </Section>

            <Section title="5. Clinical Notes">
                <Field label="Discharge Notes" full>
                    <textarea rows={3} className={InputCls} value={form.dischargeNotes} onChange={(e) => setField("dischargeNotes", e.target.value)} />
                </Field>
            </Section>

            <div className="flex justify-end gap-3">
                <button onClick={() => save(false)} disabled={saving} className="border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-gray-700 text-sm font-medium px-5 py-2 rounded-lg">
                    {saving ? "Saving…" : "Save as Draft"}
                </button>
                <button onClick={() => save(true)} disabled={saving} className="bg-green-600 hover:bg-green-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
                    {saving ? "Saving…" : "Finalize Summary"}
                </button>
            </div>
        </div>
    );
};

export default DischargeSummaryPanel;
