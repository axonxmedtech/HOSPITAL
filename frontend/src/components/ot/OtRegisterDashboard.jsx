import React, { useState, useEffect, useCallback } from "react";
import otService from "../../services/otService";
import authService from "../../services/authService";
import { useToast } from "../../context/ToastContext";

const InputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300";
const parseErr = (e) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || e?.message || "An error occurred";

const emptyReadiness = {
    cleaningDone: false,
    sterilityDone: false,
    equipmentOk: false,
    status: "PENDING",
};

const STATUS_COLORS = {
    SCHEDULED: "bg-blue-100 text-blue-800",
    IN_PROGRESS: "bg-yellow-100 text-yellow-800",
    COMPLETED: "bg-green-100 text-green-800",
    CANCELLED: "bg-red-100 text-red-800",
    NONE: "bg-gray-100 text-gray-500",
    DRAFT: "bg-amber-100 text-amber-800",
    FINALIZED: "bg-green-100 text-green-800",
    ACTIVE: "bg-yellow-100 text-yellow-800",
    READY: "bg-green-100 text-green-800",
    TRANSFERRED: "bg-purple-100 text-purple-800",
    SIGN_IN: "bg-blue-100 text-blue-800",
    TIME_OUT: "bg-orange-100 text-orange-800",
    SIGN_OUT: "bg-green-100 text-green-800",
};

export default function OtRegisterDashboard() {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const canWriteReadiness = ["NURSE", "HOSPITAL_ADMIN"].includes(user?.role);

    // Filters
    const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split("T")[0]);
    const [selectedRoom, setSelectedRoom] = useState("OT 1");

    // Data states
    const [registerEntries, setRegisterEntries] = useState([]);
    const [readiness, setReadiness] = useState(null);
    const [loading, setLoading] = useState(true);
    const [savingReadiness, setSavingReadiness] = useState(false);

    // Readiness Form State
    const [readinessForm, setReadinessForm] = useState(emptyReadiness);

    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            // Load register entries
            const regRes = await otService.getRegisterEntries(selectedDate, selectedRoom);
            setRegisterEntries(regRes?.data || []);

            // Load readiness checklist
            const readRes = await otService.getReadiness(selectedDate, selectedRoom);
            if (readRes?.data) {
                setReadiness(readRes.data);
                setReadinessForm({
                    cleaningDone: readRes.data.cleaningDone || false,
                    sterilityDone: readRes.data.sterilityDone || false,
                    equipmentOk: readRes.data.equipmentOk || false,
                    status: readRes.data.status || "PENDING",
                });
            } else {
                setReadiness(null);
                setReadinessForm(emptyReadiness);
            }
        } catch (e) {
            toastError(parseErr(e));
        } finally {
            setLoading(false);
        }
    }, [selectedDate, selectedRoom]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleSaveReadiness = async (newStatus) => {
        setSavingReadiness(true);
        try {
            const payload = {
                otRoom: selectedRoom,
                readinessDate: selectedDate,
                cleaningDone: readinessForm.cleaningDone,
                sterilityDone: readinessForm.sterilityDone,
                equipmentOk: readinessForm.equipmentOk,
                status: newStatus || readinessForm.status,
            };
            const res = await otService.saveReadiness(payload);
            setReadiness(res?.data);
            setReadinessForm({
                cleaningDone: res.data.cleaningDone || false,
                sterilityDone: res.data.sterilityDone || false,
                equipmentOk: res.data.equipmentOk || false,
                status: res.data.status || "PENDING",
            });
            success(`Readiness checklist saved as ${newStatus || res.data.status}`);
            loadData();
        } catch (e) {
            toastError(parseErr(e));
        } finally {
            setSavingReadiness(false);
        }
    };

    const StatusBadge = ({ val }) => {
        const uppercaseVal = val ? val.toUpperCase() : "NONE";
        const colorClass = STATUS_COLORS[uppercaseVal] || STATUS_COLORS.NONE;
        return (
            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${colorClass}`}>
                {uppercaseVal}
            </span>
        );
    };

    const ReadinessFields = () => (
        <div className="space-y-4">
            <label className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg cursor-pointer border border-gray-100 hover:bg-gray-100 transition">
                <input
                    type="checkbox"
                    className="w-4 h-4 text-blue-600 rounded"
                    checked={readinessForm.cleaningDone}
                    disabled={!canWriteReadiness || readiness?.status === "READY"}
                    onChange={(e) => setReadinessForm(p => ({ ...p, cleaningDone: e.target.checked }))}
                />
                <div>
                    <p className="text-xs font-semibold text-gray-800">Turnaround Cleaning Complete</p>
                    <p className="text-[10px] text-gray-400">Floors washed and surgical surfaces disinfected</p>
                </div>
            </label>

            <label className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg cursor-pointer border border-gray-100 hover:bg-gray-100 transition">
                <input
                    type="checkbox"
                    className="w-4 h-4 text-blue-600 rounded"
                    checked={readinessForm.sterilityDone}
                    disabled={!canWriteReadiness || readiness?.status === "READY"}
                    onChange={(e) => setReadinessForm(p => ({ ...p, sterilityDone: e.target.checked }))}
                />
                <div>
                    <p className="text-xs font-semibold text-gray-800">CSSD Sterile Supplies Verified</p>
                    <p className="text-[10px] text-gray-400">Instruments and autoclave indicators confirmed sterile</p>
                </div>
            </label>

            <label className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg cursor-pointer border border-gray-100 hover:bg-gray-100 transition">
                <input
                    type="checkbox"
                    className="w-4 h-4 text-blue-600 rounded"
                    checked={readinessForm.equipmentOk}
                    disabled={!canWriteReadiness || readiness?.status === "READY"}
                    onChange={(e) => setReadinessForm(p => ({ ...p, equipmentOk: e.target.checked }))}
                />
                <div>
                    <p className="text-xs font-semibold text-gray-800">Biomedical Equipment Calibrated & OK</p>
                    <p className="text-[10px] text-gray-400">Anaesthesia machine, cautery, backup power status validated</p>
                </div>
            </label>
        </div>
    );

    return (
        <div className="p-6 max-w-7xl mx-auto space-y-6">
            {/* Header section */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center bg-white p-4 rounded-xl border border-gray-200 gap-4">
                <div>
                    <h2 className="text-lg font-bold text-gray-900">📋 OT Operations Dashboard</h2>
                    <p className="text-xs text-gray-500">Monitor daily surgeries & room sterile readiness logs</p>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                    <div>
                        <label className="block text-[10px] font-bold text-gray-400 uppercase mb-1">Date</label>
                        <input
                            type="date"
                            className="border border-gray-200 rounded-lg px-2 py-1 text-xs focus:ring-2 focus:ring-blue-300 outline-none"
                            value={selectedDate}
                            onChange={(e) => setSelectedDate(e.target.value)}
                        />
                    </div>
                    <div>
                        <label className="block text-[10px] font-bold text-gray-400 uppercase mb-1">OT Room</label>
                        <select
                            className="border border-gray-200 rounded-lg px-2 py-1 text-xs bg-white focus:ring-2 focus:ring-blue-300 outline-none"
                            value={selectedRoom}
                            onChange={(e) => setSelectedRoom(e.target.value)}
                        >
                            <option value="OT 1">OT 1</option>
                            <option value="OT 2">OT 2</option>
                            <option value="OT 3">OT 3</option>
                        </select>
                    </div>
                </div>
            </div>

            {loading ? (
                <div className="bg-white rounded-xl border border-gray-200 p-10 text-center text-gray-400 text-sm">
                    Loading dashboard details...
                </div>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Form 25: Master OT Register */}
                    <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="font-bold text-gray-900 text-sm uppercase tracking-wide">
                                📅 Surgery Register ({selectedRoom} - {selectedDate})
                            </h3>
                            <span className="text-[10px] text-gray-400">{registerEntries.length} Case(s)</span>
                        </div>

                        {registerEntries.length === 0 ? (
                            <div className="text-center py-20 text-xs text-gray-400 italic bg-gray-50/50 rounded-lg border border-dashed border-gray-200">
                                No scheduled or completed surgeries in {selectedRoom} on this date.
                            </div>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-left border-collapse text-xs">
                                    <thead>
                                        <tr className="border-b border-gray-100 text-gray-400 font-semibold uppercase text-[10px]">
                                            <th className="py-2.5 px-2">Patient Details</th>
                                            <th className="py-2.5 px-2">Procedure</th>
                                            <th className="py-2.5 px-2">Surgical Team</th>
                                            <th className="py-2.5 px-2">WHO Safety</th>
                                            <th className="py-2.5 px-2">Intra-Op Status</th>
                                            <th className="py-2.5 px-2">Overall Status</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-50">
                                        {registerEntries.map((entry) => (
                                            <tr key={entry.bookingId} className="hover:bg-gray-50/30">
                                                <td className="py-3 px-2">
                                                    <p className="font-semibold text-gray-800">{entry.patientName || "—"}</p>
                                                    <p className="text-[10px] text-gray-400">
                                                        {entry.patientCustomId || "—"} · {entry.patientAge || "?"}y · {entry.patientGender || "?"}
                                                    </p>
                                                </td>
                                                <td className="py-3 px-2">
                                                    <p className="font-medium text-gray-700">{entry.procedureName}</p>
                                                    <p className="text-[10px] text-gray-400">
                                                        Scheduled: {new Date(entry.scheduledDateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                    </p>
                                                </td>
                                                <td className="py-3 px-2 text-gray-600">
                                                    <p className="text-[11px]"><span className="text-[9px] uppercase text-gray-400 mr-1 font-semibold">Surg</span>{entry.surgeonName || "—"}</p>
                                                    <p className="text-[11px]"><span className="text-[9px] uppercase text-gray-400 mr-1 font-semibold">Anes</span>{entry.anesthetistName || "—"}</p>
                                                </td>
                                                <td className="py-3 px-2">
                                                    <StatusBadge val={entry.checklistStatus} />
                                                </td>
                                                <td className="py-3 px-2 space-y-1">
                                                    <div>
                                                        <span className="text-[9px] font-bold text-gray-400 mr-1">ANES:</span>
                                                        <StatusBadge val={entry.anaesthesiaStatus} />
                                                    </div>
                                                    <div>
                                                        <span className="text-[9px] font-bold text-gray-400 mr-1">OPER:</span>
                                                        <StatusBadge val={entry.operationStatus} />
                                                    </div>
                                                    <div>
                                                        <span className="text-[9px] font-bold text-gray-400 mr-1">PACU:</span>
                                                        <StatusBadge val={entry.pacuStatus} />
                                                    </div>
                                                </td>
                                                <td className="py-3 px-2">
                                                    <StatusBadge val={entry.status} />
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>

                    {/* Form 26: Room Sterility & Readiness Checklist */}
                    <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-4">
                        <div className="flex justify-between items-center">
                            <h3 className="font-bold text-gray-900 text-sm uppercase tracking-wide">
                                🛡️ Room Readiness Checklist
                            </h3>
                            {readiness ? (
                                <StatusBadge val={readiness.status} />
                            ) : (
                                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">
                                    UNINITIALIZED
                                </span>
                            )}
                        </div>

                        <p className="text-[10px] text-gray-400">
                            ⚠️ A room's readiness checklist must be signed off as <strong>READY</strong> before any surgery can be scheduled in it for that date.
                        </p>

                        {/* Rendering checks */}
                        {ReadinessFields()}

                        {/* Summary logs / metadata */}
                        {readiness?.status === "READY" && (
                            <div className="bg-green-50 border border-green-200 text-green-800 p-3 rounded-lg text-xs">
                                <p className="font-bold">✓ Sterility & Calibration Confirmed</p>
                                <p className="text-[10px] text-green-600 mt-1">Verified By: {readiness.verifiedBy}</p>
                                <p className="text-[10px] text-green-600">Verified At: {new Date(readiness.verifiedAt).toLocaleString()}</p>
                            </div>
                        )}

                        {canWriteReadiness && (
                            <div className="pt-2 border-t border-gray-100 flex gap-2 justify-end">
                                {readiness?.status !== "READY" ? (
                                    <>
                                        <button
                                            onClick={() => handleSaveReadiness("PENDING")}
                                            disabled={savingReadiness}
                                            className="px-3 py-1.5 border border-gray-200 hover:bg-gray-50 text-gray-600 text-xs font-semibold rounded-lg"
                                        >
                                            Save Draft
                                        </button>
                                        <button
                                            onClick={() => handleSaveReadiness("READY")}
                                            disabled={savingReadiness || !readinessForm.cleaningDone || !readinessForm.sterilityDone || !readinessForm.equipmentOk}
                                            className="px-4 py-1.5 bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white text-xs font-semibold rounded-lg"
                                        >
                                            Sign off as READY
                                        </button>
                                    </>
                                ) : (
                                    <button
                                        onClick={() => handleSaveReadiness("PENDING")}
                                        disabled={savingReadiness}
                                        className="px-3 py-1.5 border border-red-200 hover:bg-red-50 text-red-600 text-xs font-semibold rounded-lg"
                                    >
                                        Revert to PENDING
                                    </button>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
