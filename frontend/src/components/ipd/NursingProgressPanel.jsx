import React, { useEffect, useState, useCallback } from "react";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const SHIFTS = ["MORNING", "EVENING", "NIGHT"];
const CONDITIONS = ["STABLE", "IMPROVING", "DETERIORATING", "CRITICAL", "COMFORTABLE"];
const RESPONSES = ["COOPERATIVE", "UNCOOPERATIVE", "SEDATED", "UNCONSCIOUS"];
const PAIN_LABELS = ["0 - None", "1", "2", "3 - Mild", "4", "5", "6 - Moderate", "7", "8", "9", "10 - Severe"];
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.message || "An error occurred";

const SHIFT_COLORS = { MORNING: "bg-yellow-50 border-yellow-200 text-yellow-800", EVENING: "bg-orange-50 border-orange-200 text-orange-800", NIGHT: "bg-indigo-50 border-indigo-200 text-indigo-800" };
const SHIFT_ICONS = { MORNING: "🌅", EVENING: "🌇", NIGHT: "🌙" };

const InputCls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent";

const NursingProgressPanel = ({ admissionId, patientId, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const isNurse = user?.role === "NURSE";
    const isDoctor = authService.isDoctor();
    const isAdmin = user?.role === "HOSPITAL_ADMIN";
    const canCreate = isNurse && !isLocked;

    const [notes, setNotes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [expandedId, setExpandedId] = useState(null);

    // Note form
    const [noteModal, setNoteModal] = useState({ open: false, saving: false });
    const [noteForm, setNoteForm] = useState({ shift: "MORNING", generalCondition: "STABLE", painScore: 0, patientResponse: "COOPERATIVE", remarks: "", doctorNotified: false, doctorName: "", doctorAdvice: "" });

    // Procedure form
    const [procModal, setProcModal] = useState({ open: false, noteId: null, saving: false });
    const [procForm, setProcForm] = useState({ procedureName: "", performedTime: new Date().toISOString().slice(0, 16), remarks: "" });

    // Handover form
    const [handoverModal, setHandoverModal] = useState({ open: false, saving: false });
    const [handoverForm, setHandoverForm] = useState({ shift: "MORNING", incomingNurseId: "", pendingTasks: "", criticalAlerts: "", medsDue: "", investigationsPending: "", doctorReviewPending: false });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await hospitalService.getNursingProgressByAdmission(admissionId);
            setNotes(Array.isArray(data) ? data : []);
        } catch { setNotes([]); } finally { setLoading(false); }
    }, [admissionId]);

    useEffect(() => { load(); }, [load]);

    const handleSaveNote = async () => {
        setNoteModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.saveNursingProgress({ admissionId: Number(admissionId), patientId: Number(patientId), ...noteForm });
            success("Progress note saved");
            setNoteModal({ open: false, saving: false });
            setNoteForm({ shift: "MORNING", generalCondition: "STABLE", painScore: 0, patientResponse: "COOPERATIVE", remarks: "", doctorNotified: false, doctorName: "", doctorAdvice: "" });
            load();
        } catch (e) { toastError(parseErr(e)); setNoteModal(p => ({ ...p, saving: false })); }
    };

    const handleSubmitNote = async (noteId) => {
        try {
            await hospitalService.submitNursingProgress(noteId);
            success("Note submitted");
            load();
        } catch (e) { toastError(parseErr(e)); }
    };

    const handleAddProcedure = async () => {
        if (!procForm.procedureName) return toastError("Procedure name is required");
        setProcModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.addNursingProcedure(procModal.noteId, { ...procForm, admissionId: Number(admissionId) });
            success("Procedure added");
            setProcModal({ open: false, noteId: null, saving: false });
            setProcForm({ procedureName: "", performedTime: new Date().toISOString().slice(0, 16), remarks: "" });
            load();
        } catch (e) { toastError(parseErr(e)); setProcModal(p => ({ ...p, saving: false })); }
    };

    const handleHandover = async () => {
        setHandoverModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.saveShiftHandover({ admissionId: Number(admissionId), ...handoverForm });
            success("Handover saved");
            setHandoverModal({ open: false, saving: false });
        } catch (e) { toastError(parseErr(e)); setHandoverModal(p => ({ ...p, saving: false })); }
    };

    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-base font-bold text-gray-900">Nursing Progress</h2>
                    <p className="text-xs text-gray-500 mt-0.5">Form 08 — Daily Nursing Notes & Shift Handover</p>
                </div>
                <div className="flex gap-2">
                    {canCreate && (
                        <>
                            <button onClick={() => setHandoverModal({ open: true, saving: false })}
                                className="flex items-center gap-1.5 px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 text-xs font-semibold rounded-lg transition-colors">
                                🔄 Handover
                            </button>
                            <button onClick={() => setNoteModal({ open: true, saving: false })}
                                className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">
                                + Progress Note
                            </button>
                        </>
                    )}
                </div>
            </div>

            {loading ? (
                <div className="space-y-3">{[1, 2].map(i => <div key={i} className="h-24 bg-gray-100 rounded-xl animate-pulse" />)}</div>
            ) : notes.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-14 text-center">
                    <span className="text-4xl mb-3">📓</span>
                    <p className="text-sm font-medium text-gray-700">No nursing notes recorded</p>
                    {canCreate && <p className="text-xs text-gray-400 mt-1">Add a shift-specific progress note</p>}
                    {!canCreate && !isNurse && <p className="text-xs text-gray-400 mt-1">Nursing notes are added by the ward nurse</p>}
                </div>
            ) : (
                <div className="space-y-3">
                    {notes.map(note => (
                        <div key={note.id} className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                            <div className="flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-gray-50 transition-colors"
                                onClick={() => setExpandedId(expandedId === note.id ? null : note.id)}>
                                <div className="flex items-center gap-3">
                                    <span className={`text-xs font-bold px-2.5 py-1 rounded-full border ${SHIFT_COLORS[note.shift] || "bg-gray-100 text-gray-600"}`}>
                                        {SHIFT_ICONS[note.shift]} {note.shift}
                                    </span>
                                    <div>
                                        <div className="flex items-center gap-2">
                                            <span className="text-sm font-semibold text-gray-900">{note.generalCondition}</span>
                                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${note.status === "SUBMITTED" ? "bg-green-50 text-green-700 border border-green-200" : "bg-amber-50 text-amber-700 border border-amber-200"}`}>{note.status}</span>
                                            {note.painScore >= 7 && <span className="text-[10px] text-red-600 font-bold">🔴 High Pain ({note.painScore}/10)</span>}
                                        </div>
                                        <p className="text-xs text-gray-400 mt-0.5">{note.createdAt && new Date(note.createdAt).toLocaleString()}</p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2">
                                    {canCreate && note.status === "DRAFT" && (
                                        <>
                                            <button onClick={e => { e.stopPropagation(); setProcModal({ open: true, noteId: note.id, saving: false }); }}
                                                className="px-2.5 py-1 text-xs font-semibold bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 rounded-lg transition-colors">+ Procedure</button>
                                            <button onClick={e => { e.stopPropagation(); handleSubmitNote(note.id); }}
                                                className="px-2.5 py-1 text-xs font-semibold bg-green-50 hover:bg-green-100 text-green-700 border border-green-200 rounded-lg transition-colors">Submit</button>
                                        </>
                                    )}
                                    <svg className={`h-4 w-4 text-gray-400 transition-transform ${expandedId === note.id ? "rotate-180" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" /></svg>
                                </div>
                            </div>

                            {expandedId === note.id && (
                                <div className="px-4 pb-4 border-t border-gray-100 bg-gray-50 space-y-3">
                                    <div className="grid grid-cols-2 gap-x-6 gap-y-2 mt-3 text-sm">
                                        <div><span className="text-xs text-gray-500 font-medium">Pain Score</span><p className="font-semibold text-gray-800">{note.painScore} / 10</p></div>
                                        <div><span className="text-xs text-gray-500 font-medium">Patient Response</span><p className="font-medium text-gray-800">{note.patientResponse}</p></div>
                                        <div><span className="text-xs text-gray-500 font-medium">Doctor Notified</span><p className={`font-semibold ${note.doctorNotified ? "text-green-600" : "text-gray-400"}`}>{note.doctorNotified ? `Yes — ${note.doctorName || "N/A"}` : "No"}</p></div>
                                        {note.doctorAdvice && <div className="col-span-2"><span className="text-xs text-gray-500 font-medium">Doctor Advice</span><p className="text-gray-700 mt-0.5">{note.doctorAdvice}</p></div>}
                                        {note.remarks && <div className="col-span-2"><span className="text-xs text-gray-500 font-medium">Remarks</span><p className="text-gray-700 mt-0.5">{note.remarks}</p></div>}
                                    </div>

                                    {note.procedures && note.procedures.length > 0 && (
                                        <div className="mt-2 pt-3 border-t border-gray-200">
                                            <p className="text-xs font-bold text-gray-600 uppercase mb-2">Procedures</p>
                                            <div className="space-y-1">
                                                {note.procedures.map((p, i) => (
                                                    <div key={i} className="flex items-center justify-between text-sm">
                                                        <span className="text-gray-700">• {p.procedureName}</span>
                                                        <span className="text-xs text-gray-400">{p.performedTime && new Date(p.performedTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {/* Progress Note Modal */}
            {noteModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 sticky top-0 bg-white rounded-t-2xl">
                            <h3 className="text-base font-bold text-gray-900">📓 New Progress Note</h3>
                            <button onClick={() => setNoteModal({ open: false, saving: false })} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Shift *</label>
                                    <select value={noteForm.shift} onChange={e => setNoteForm(p => ({ ...p, shift: e.target.value }))} className={InputCls}>{SHIFTS.map(s => <option key={s}>{s}</option>)}</select></div>
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">General Condition *</label>
                                    <select value={noteForm.generalCondition} onChange={e => setNoteForm(p => ({ ...p, generalCondition: e.target.value }))} className={InputCls}>{CONDITIONS.map(c => <option key={c}>{c}</option>)}</select></div>
                                <div>
                                    <label className="block text-xs font-semibold text-gray-600 mb-1.5">Pain Score: {noteForm.painScore}/10</label>
                                    <input type="range" min="0" max="10" value={noteForm.painScore} onChange={e => setNoteForm(p => ({ ...p, painScore: Number(e.target.value) }))} className="w-full accent-blue-600" />
                                    <div className="flex justify-between text-[10px] text-gray-400 mt-1"><span>None</span><span>Moderate</span><span>Severe</span></div>
                                </div>
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Patient Response</label>
                                    <select value={noteForm.patientResponse} onChange={e => setNoteForm(p => ({ ...p, patientResponse: e.target.value }))} className={InputCls}>{RESPONSES.map(r => <option key={r}>{r}</option>)}</select></div>
                            </div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Remarks / Observations</label>
                                <textarea value={noteForm.remarks} onChange={e => setNoteForm(p => ({ ...p, remarks: e.target.value }))} rows={3} placeholder="Observed clinical events, patient complaints, interventions..." className={`${InputCls} resize-none`} /></div>
                            <div className="p-3 bg-gray-50 border border-gray-200 rounded-xl space-y-3">
                                <label className="flex items-center gap-2 cursor-pointer">
                                    <input type="checkbox" checked={noteForm.doctorNotified} onChange={e => setNoteForm(p => ({ ...p, doctorNotified: e.target.checked }))} className="w-4 h-4 text-blue-600 rounded border-gray-300" />
                                    <span className="text-sm font-medium text-gray-700">Doctor was notified</span>
                                </label>
                                {noteForm.doctorNotified && (
                                    <div className="grid grid-cols-2 gap-3">
                                        <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Doctor Name</label>
                                            <input value={noteForm.doctorName} onChange={e => setNoteForm(p => ({ ...p, doctorName: e.target.value }))} placeholder="Dr. Name" className={InputCls} /></div>
                                        <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Doctor Advice Given</label>
                                            <input value={noteForm.doctorAdvice} onChange={e => setNoteForm(p => ({ ...p, doctorAdvice: e.target.value }))} placeholder="Advice / orders..." className={InputCls} /></div>
                                    </div>
                                )}
                            </div>
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl sticky bottom-0">
                            <button onClick={() => setNoteModal({ open: false, saving: false })} className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                            <button onClick={handleSaveNote} disabled={noteModal.saving}
                                className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors">
                                {noteModal.saving ? "Saving..." : "Save Note"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Add Procedure Modal */}
            {procModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
                            <h3 className="text-base font-bold text-gray-900">🩺 Add Nursing Procedure</h3>
                            <button onClick={() => setProcModal({ open: false, noteId: null, saving: false })} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Procedure Name *</label>
                                <input value={procForm.procedureName} onChange={e => setProcForm(p => ({ ...p, procedureName: e.target.value }))} placeholder="e.g. IV cannula insertion, Wound dressing, Suctioning" className={InputCls} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Time Performed</label>
                                <input type="datetime-local" value={procForm.performedTime} onChange={e => setProcForm(p => ({ ...p, performedTime: e.target.value }))} className={InputCls} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Remarks</label>
                                <input value={procForm.remarks} onChange={e => setProcForm(p => ({ ...p, remarks: e.target.value }))} placeholder="Outcome, patient tolerance..." className={InputCls} /></div>
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl">
                            <button onClick={() => setProcModal({ open: false, noteId: null, saving: false })} className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                            <button onClick={handleAddProcedure} disabled={procModal.saving}
                                className="px-4 py-2 text-sm font-semibold text-white bg-purple-600 hover:bg-purple-700 disabled:opacity-50 rounded-lg transition-colors">
                                {procModal.saving ? "Saving..." : "Add Procedure"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Shift Handover Modal */}
            {handoverModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 sticky top-0 bg-white rounded-t-2xl">
                            <h3 className="text-base font-bold text-gray-900">🔄 Shift Handover</h3>
                            <button onClick={() => setHandoverModal({ open: false, saving: false })} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Shift Being Handed Over</label>
                                    <select value={handoverForm.shift} onChange={e => setHandoverForm(p => ({ ...p, shift: e.target.value }))} className={InputCls}>{SHIFTS.map(s => <option key={s}>{s}</option>)}</select></div>
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Incoming Nurse ID</label>
                                    <input value={handoverForm.incomingNurseId} onChange={e => setHandoverForm(p => ({ ...p, incomingNurseId: e.target.value }))} placeholder="Nurse ID or Name" className={InputCls} /></div>
                            </div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Pending Tasks</label>
                                <textarea value={handoverForm.pendingTasks} onChange={e => setHandoverForm(p => ({ ...p, pendingTasks: e.target.value }))} rows={2} placeholder="List pending tasks..." className={`${InputCls} resize-none`} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Critical Alerts</label>
                                <textarea value={handoverForm.criticalAlerts} onChange={e => setHandoverForm(p => ({ ...p, criticalAlerts: e.target.value }))} rows={2} placeholder="Any critical patient status alerts..." className={`${InputCls} resize-none`} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Medications Due</label>
                                <input value={handoverForm.medsDue} onChange={e => setHandoverForm(p => ({ ...p, medsDue: e.target.value }))} placeholder="e.g. Insulin at 6pm, IV antibiotics at 8pm" className={InputCls} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Investigations Pending</label>
                                <input value={handoverForm.investigationsPending} onChange={e => setHandoverForm(p => ({ ...p, investigationsPending: e.target.value }))} placeholder="e.g. CBC report awaited, ECG ordered" className={InputCls} /></div>
                            <label className="flex items-center gap-2 cursor-pointer">
                                <input type="checkbox" checked={handoverForm.doctorReviewPending} onChange={e => setHandoverForm(p => ({ ...p, doctorReviewPending: e.target.checked }))} className="w-4 h-4 text-blue-600 rounded border-gray-300" />
                                <span className="text-sm text-gray-700">Doctor review is pending</span>
                            </label>
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl sticky bottom-0">
                            <button onClick={() => setHandoverModal({ open: false, saving: false })} className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                            <button onClick={handleHandover} disabled={handoverModal.saving}
                                className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-lg transition-colors">
                                {handoverModal.saving ? "Saving..." : "Save Handover"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default NursingProgressPanel;
