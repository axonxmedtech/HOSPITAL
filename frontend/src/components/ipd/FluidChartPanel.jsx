import React, { useEffect, useState, useCallback } from "react";
import hospitalService from "../../services/hospitalService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const INTAKE_TYPES = ["ORAL", "IV", "TUBE", "BLOOD"];
const OUTPUT_TYPES = ["URINE", "STOOL", "VOMIT", "DRAIN", "DIALYSIS"];
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.message || "An error occurred";

const TypeBadge = ({ type }) => {
    const colors = {
        ORAL: "bg-blue-50 text-blue-700",
        IV: "bg-indigo-50 text-indigo-700",
        TUBE: "bg-purple-50 text-purple-700",
        BLOOD: "bg-red-50 text-red-700",
        URINE: "bg-yellow-50 text-yellow-700",
        STOOL: "bg-amber-50 text-amber-700",
        VOMIT: "bg-orange-50 text-orange-700",
        DRAIN: "bg-teal-50 text-teal-700",
        DIALYSIS: "bg-gray-50 text-gray-700",
    };
    return <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${colors[type] || "bg-gray-100 text-gray-600"}`}>{type}</span>;
};

const FluidChartPanel = ({ admissionId, patientId, isLocked }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const isDoctor = authService.isDoctor();
    const isNurse = user?.role === "NURSE";
    const isAdmin = user?.role === "HOSPITAL_ADMIN";
    const canEdit = (isDoctor || isNurse || isAdmin) && !isLocked;

    const [balance, setBalance] = useState(null);
    const [trends, setTrends] = useState([]);
    const [loading, setLoading] = useState(true);

    const [intakeModal, setIntakeModal] = useState({ open: false, saving: false });
    const [intakeForm, setIntakeForm] = useState({ type: "ORAL", description: "", volumeMl: "", recordedTime: new Date().toISOString().slice(0, 16) });

    const [outputModal, setOutputModal] = useState({ open: false, saving: false });
    const [outputForm, setOutputForm] = useState({ type: "URINE", description: "", volumeMl: "", color: "", recordedTime: new Date().toISOString().slice(0, 16) });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [bal, trnd] = await Promise.all([
                hospitalService.getFluidBalance(admissionId).catch(() => null),
                hospitalService.getFluidTrends(admissionId).catch(() => []),
            ]);
            setBalance(bal);
            setTrends(Array.isArray(trnd) ? trnd : []);
        } catch { } finally { setLoading(false); }
    }, [admissionId]);

    useEffect(() => { load(); }, [load]);

    const handleRecordIntake = async () => {
        if (!intakeForm.volumeMl || !intakeForm.description) return toastError("Please fill all required fields");
        setIntakeModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.recordFluidIntake({
                admissionId: Number(admissionId),
                patientId: Number(patientId),
                type: intakeForm.type,
                description: intakeForm.description,
                volumeMl: Number(intakeForm.volumeMl),
                recordedTime: intakeForm.recordedTime,
            });
            success("Intake recorded");
            setIntakeModal({ open: false, saving: false });
            setIntakeForm({ type: "ORAL", description: "", volumeMl: "", recordedTime: new Date().toISOString().slice(0, 16) });
            load();
        } catch (e) { toastError(parseErr(e)); setIntakeModal(p => ({ ...p, saving: false })); }
    };

    const handleRecordOutput = async () => {
        if (!outputForm.volumeMl || !outputForm.description) return toastError("Please fill all required fields");
        setOutputModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.recordFluidOutput({
                admissionId: Number(admissionId),
                patientId: Number(patientId),
                type: outputForm.type,
                description: outputForm.description,
                volumeMl: Number(outputForm.volumeMl),
                color: outputForm.color,
                recordedTime: outputForm.recordedTime,
            });
            success("Output recorded");
            setOutputModal({ open: false, saving: false });
            setOutputForm({ type: "URINE", description: "", volumeMl: "", color: "", recordedTime: new Date().toISOString().slice(0, 16) });
            load();
        } catch (e) { toastError(parseErr(e)); setOutputModal(p => ({ ...p, saving: false })); }
    };

    const InputCls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent";
    const BtnSecondary = "px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors";

    const todayBalance = balance || { totalIntake: 0, totalOutput: 0, netBalance: 0 };
    const netPositive = todayBalance.netBalance >= 0;

    const allIntakes = trends.flatMap(t => (t.intakes || [])).sort((a, b) => new Date(b.recordedTime) - new Date(a.recordedTime));
    const allOutputs = trends.flatMap(t => (t.outputs || [])).sort((a, b) => new Date(b.recordedTime) - new Date(a.recordedTime));

    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-base font-bold text-gray-900">Fluid Intake / Output Chart</h2>
                    <p className="text-xs text-gray-500 mt-0.5">Form 10 — Daily Fluid Balance Monitoring</p>
                </div>
                {canEdit && (
                    <div className="flex gap-2">
                        <button onClick={() => setIntakeModal({ open: true, saving: false })}
                            className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">
                            + Intake
                        </button>
                        <button onClick={() => setOutputModal({ open: true, saving: false })}
                            className="flex items-center gap-1.5 px-3 py-1.5 bg-amber-500 hover:bg-amber-600 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors">
                            + Output
                        </button>
                    </div>
                )}
            </div>

            {loading ? (
                <div className="space-y-3">{[1, 2, 3].map(i => <div key={i} className="h-20 bg-gray-100 rounded-xl animate-pulse" />)}</div>
            ) : (
                <>
                    {/* Balance Summary Card */}
                    <div className={`p-5 rounded-2xl border-2 ${netPositive ? "bg-blue-50 border-blue-200" : "bg-red-50 border-red-200"}`}>
                        <p className="text-xs text-gray-500 font-semibold uppercase tracking-wide mb-3">Today&apos;s Fluid Balance</p>
                        <div className="grid grid-cols-3 gap-4">
                            <div className="text-center">
                                <p className="text-[10px] text-gray-500 font-medium uppercase">Total Intake</p>
                                <p className="text-2xl font-black text-blue-700 mt-1">{todayBalance.totalIntake}<span className="text-sm font-medium ml-1">ml</span></p>
                            </div>
                            <div className="text-center border-x border-white/60">
                                <p className="text-[10px] text-gray-500 font-medium uppercase">Total Output</p>
                                <p className="text-2xl font-black text-amber-700 mt-1">{todayBalance.totalOutput}<span className="text-sm font-medium ml-1">ml</span></p>
                            </div>
                            <div className="text-center">
                                <p className="text-[10px] text-gray-500 font-medium uppercase">Net Balance</p>
                                <p className={`text-2xl font-black mt-1 ${netPositive ? "text-green-700" : "text-red-700"}`}>
                                    {netPositive ? "+" : ""}{todayBalance.netBalance}<span className="text-sm font-medium ml-1">ml</span>
                                </p>
                            </div>
                        </div>
                        {!netPositive && Math.abs(todayBalance.netBalance) > 500 && (
                            <div className="mt-3 flex items-center gap-2 p-2.5 bg-red-100 border border-red-300 rounded-lg">
                                <span className="text-red-600 font-bold text-sm">⚠️</span>
                                <p className="text-xs text-red-700 font-medium">Negative fluid balance detected — monitor for dehydration / AKI risk</p>
                            </div>
                        )}
                    </div>

                    {/* Two-column logs */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {/* Intake log */}
                        <div className="bg-white border border-gray-200 rounded-xl shadow-sm">
                            <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
                                <h3 className="text-sm font-bold text-blue-700">💧 Intake Log</h3>
                                <span className="text-xs text-gray-400">{allIntakes.length} entries</span>
                            </div>
                            {allIntakes.length === 0 ? (
                                <div className="py-8 text-center text-xs text-gray-400">No intake records yet</div>
                            ) : (
                                <div className="divide-y divide-gray-50 max-h-72 overflow-y-auto">
                                    {allIntakes.map((item, i) => (
                                        <div key={i} className="px-4 py-2.5 flex items-center justify-between">
                                            <div className="flex items-center gap-2 flex-1 min-w-0">
                                                <TypeBadge type={item.type} />
                                                <span className="text-sm text-gray-700 truncate">{item.description}</span>
                                            </div>
                                            <div className="text-right ml-3 flex-shrink-0">
                                                <p className="text-sm font-bold text-blue-700">{item.volumeMl} ml</p>
                                                <p className="text-[10px] text-gray-400">{item.recordedTime ? new Date(item.recordedTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : ""}</p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* Output log */}
                        <div className="bg-white border border-gray-200 rounded-xl shadow-sm">
                            <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
                                <h3 className="text-sm font-bold text-amber-700">🔻 Output Log</h3>
                                <span className="text-xs text-gray-400">{allOutputs.length} entries</span>
                            </div>
                            {allOutputs.length === 0 ? (
                                <div className="py-8 text-center text-xs text-gray-400">No output records yet</div>
                            ) : (
                                <div className="divide-y divide-gray-50 max-h-72 overflow-y-auto">
                                    {allOutputs.map((item, i) => (
                                        <div key={i} className="px-4 py-2.5 flex items-center justify-between">
                                            <div className="flex items-center gap-2 flex-1 min-w-0">
                                                <TypeBadge type={item.type} />
                                                <span className="text-sm text-gray-700 truncate">{item.description}</span>
                                                {item.color && <span className="text-[10px] text-gray-400">({item.color})</span>}
                                            </div>
                                            <div className="text-right ml-3 flex-shrink-0">
                                                <p className="text-sm font-bold text-amber-700">{item.volumeMl} ml</p>
                                                <p className="text-[10px] text-gray-400">{item.recordedTime ? new Date(item.recordedTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : ""}</p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Daily trend table */}
                    {trends.length > 1 && (
                        <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                            <div className="px-4 py-3 border-b border-gray-100">
                                <h3 className="text-sm font-bold text-gray-800">📈 Daily Balance History</h3>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead className="bg-gray-50">
                                        <tr>
                                            <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500 uppercase">Date</th>
                                            <th className="px-4 py-2 text-right text-xs font-semibold text-blue-500 uppercase">Intake</th>
                                            <th className="px-4 py-2 text-right text-xs font-semibold text-amber-500 uppercase">Output</th>
                                            <th className="px-4 py-2 text-right text-xs font-semibold text-gray-500 uppercase">Net</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-50">
                                        {trends.map((t, i) => (
                                            <tr key={i} className="hover:bg-gray-50">
                                                <td className="px-4 py-2 font-medium text-gray-700">{t.balanceDate}</td>
                                                <td className="px-4 py-2 text-right text-blue-700 font-semibold">{t.totalIntake} ml</td>
                                                <td className="px-4 py-2 text-right text-amber-700 font-semibold">{t.totalOutput} ml</td>
                                                <td className={`px-4 py-2 text-right font-bold ${t.netBalance >= 0 ? "text-green-700" : "text-red-700"}`}>
                                                    {t.netBalance >= 0 ? "+" : ""}{t.netBalance} ml
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </>
            )}

            {/* Intake Modal */}
            {intakeModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
                            <h3 className="text-base font-bold text-gray-900">💧 Record Fluid Intake</h3>
                            <button onClick={() => setIntakeModal({ open: false, saving: false })} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Type *</label>
                                    <select value={intakeForm.type} onChange={e => setIntakeForm(p => ({ ...p, type: e.target.value }))} className={InputCls}>{INTAKE_TYPES.map(t => <option key={t}>{t}</option>)}</select></div>
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Volume (ml) *</label>
                                    <input type="number" value={intakeForm.volumeMl} onChange={e => setIntakeForm(p => ({ ...p, volumeMl: e.target.value }))} placeholder="e.g. 250" className={InputCls} /></div>
                            </div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Description *</label>
                                <input value={intakeForm.description} onChange={e => setIntakeForm(p => ({ ...p, description: e.target.value }))} placeholder="e.g. Normal saline 0.9%, Oral water" className={InputCls} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Time of Recording</label>
                                <input type="datetime-local" value={intakeForm.recordedTime} onChange={e => setIntakeForm(p => ({ ...p, recordedTime: e.target.value }))} className={InputCls} /></div>
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl">
                            <button onClick={() => setIntakeModal({ open: false, saving: false })} className={BtnSecondary}>Cancel</button>
                            <button onClick={handleRecordIntake} disabled={intakeModal.saving}
                                className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition-colors">
                                {intakeModal.saving ? "Saving..." : "Record Intake"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Output Modal */}
            {outputModal.open && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
                            <h3 className="text-base font-bold text-gray-900">🔻 Record Fluid Output</h3>
                            <button onClick={() => setOutputModal({ open: false, saving: false })} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="px-6 py-4 space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Type *</label>
                                    <select value={outputForm.type} onChange={e => setOutputForm(p => ({ ...p, type: e.target.value }))} className={InputCls}>{OUTPUT_TYPES.map(t => <option key={t}>{t}</option>)}</select></div>
                                <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Volume (ml) *</label>
                                    <input type="number" value={outputForm.volumeMl} onChange={e => setOutputForm(p => ({ ...p, volumeMl: e.target.value }))} placeholder="e.g. 400" className={InputCls} /></div>
                            </div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Description *</label>
                                <input value={outputForm.description} onChange={e => setOutputForm(p => ({ ...p, description: e.target.value }))} placeholder="e.g. Urine output catheter, NG tube drainage" className={InputCls} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Color / Appearance</label>
                                <input value={outputForm.color} onChange={e => setOutputForm(p => ({ ...p, color: e.target.value }))} placeholder="e.g. Clear yellow, Dark amber, Blood-tinged" className={InputCls} /></div>
                            <div><label className="block text-xs font-semibold text-gray-600 mb-1.5">Time of Recording</label>
                                <input type="datetime-local" value={outputForm.recordedTime} onChange={e => setOutputForm(p => ({ ...p, recordedTime: e.target.value }))} className={InputCls} /></div>
                        </div>
                        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50 rounded-b-2xl">
                            <button onClick={() => setOutputModal({ open: false, saving: false })} className={BtnSecondary}>Cancel</button>
                            <button onClick={handleRecordOutput} disabled={outputModal.saving}
                                className="px-4 py-2 text-sm font-semibold text-white bg-amber-500 hover:bg-amber-600 disabled:opacity-50 rounded-lg transition-colors">
                                {outputModal.saving ? "Saving..." : "Record Output"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default FluidChartPanel;
