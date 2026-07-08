import React from 'react';

/**
 * OtBoard - presentational table of surgeries. Used by reception (Requests +
 * Scheduled/Live, with actions) and by the surgeon board (read-only).
 * `mode` = 'requests' | 'board' | 'doctor'. Action handlers are optional.
 */
const statusPill = (status) => {
    const map = {
        REQUESTED: 'bg-amber-50 text-amber-700',
        SCHEDULED: 'bg-blue-50 text-blue-700',
        IN_PROGRESS: 'bg-green-50 text-green-700',
        COMPLETED: 'bg-gray-100 text-gray-600',
        CANCELLED: 'bg-red-50 text-red-600',
    };
    return <span className={`px-2 py-0.5 rounded text-xs font-semibold ${map[status] || 'bg-gray-100 text-gray-600'}`}>{(status || '').replace('_', ' ')}</span>;
};

const fmt = (dt) => dt ? new Date(dt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—';

const OtBoard = ({ rows, mode, onSchedule, onCancel, onStart, onComplete }) => {
    if (!rows || rows.length === 0) {
        return <div className="text-center text-gray-400 py-16">No surgeries to show.</div>;
    }
    return (
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white">
            <table className="w-full text-sm">
                <thead className="bg-gray-50 text-gray-600">
                    <tr>
                        <th className="text-left px-4 py-3 font-semibold">Patient</th>
                        <th className="text-left px-4 py-3 font-semibold">Procedure</th>
                        <th className="text-left px-4 py-3 font-semibold">Priority</th>
                        <th className="text-left px-4 py-3 font-semibold">{mode === 'requests' ? 'Preferred' : 'Scheduled'}</th>
                        {mode !== 'requests' && <th className="text-left px-4 py-3 font-semibold">Surgeon</th>}
                        {mode !== 'requests' && <th className="text-left px-4 py-3 font-semibold">OT Ward</th>}
                        <th className="text-left px-4 py-3 font-semibold">Status</th>
                        {mode !== 'doctor' && <th className="text-right px-4 py-3 font-semibold">Actions</th>}
                    </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                    {rows.map((r) => (
                        <tr key={r.publicId}>
                            <td className="px-4 py-3">
                                <div className="font-semibold text-gray-900">{r.patientName || '—'}</div>
                                <div className="text-xs text-gray-500">{r.ipdNumber}</div>
                            </td>
                            <td className="px-4 py-3">{r.procedureName || '—'}</td>
                            <td className="px-4 py-3">{r.priority}</td>
                            <td className="px-4 py-3">{mode === 'requests' ? (r.preferredDate || '—') : fmt(r.scheduledAt)}</td>
                            {mode !== 'requests' && (
                                <td className="px-4 py-3">
                                    <div>{r.surgeonName || '—'}</div>
                                    {r.anaesthetistName && <div className="text-xs text-gray-500">Anaes: {r.anaesthetistName}</div>}
                                </td>
                            )}
                            {mode !== 'requests' && <td className="px-4 py-3">{r.otWardName || '—'}</td>}
                            <td className="px-4 py-3">{statusPill(r.status)}</td>
                            {mode !== 'doctor' && (
                                <td className="px-4 py-3 text-right whitespace-nowrap">
                                    {mode === 'requests' && (
                                        <>
                                            <button onClick={() => onSchedule(r)} className="px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-gray-900 hover:bg-gray-800 mr-2">Schedule</button>
                                            <button onClick={() => onCancel(r)} className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50">Cancel</button>
                                        </>
                                    )}
                                    {mode === 'board' && r.status === 'SCHEDULED' && (
                                        <button onClick={() => onStart(r)} className="px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-green-600 hover:bg-green-700">Start</button>
                                    )}
                                    {mode === 'board' && r.status === 'IN_PROGRESS' && (
                                        <button onClick={() => onComplete(r)} className="px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-gray-900 hover:bg-gray-800">Complete</button>
                                    )}
                                </td>
                            )}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
};

export default OtBoard;
