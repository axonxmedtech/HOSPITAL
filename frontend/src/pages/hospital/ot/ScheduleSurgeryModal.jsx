import React, { useEffect, useState } from 'react';
import otService from '../../../services/otService';
import wardService from '../../../services/wardService';
import { useToast } from '../../../context/ToastContext';

/**
 * ScheduleSurgeryModal - reception assigns a surgeon + date/time + OT ward to a
 * requested surgery. OT wards are those whose name contains "OT".
 */
const ScheduleSurgeryModal = ({ surgery, onClose, onScheduled }) => {
    const { success, error: toastError } = useToast();
    const [surgeons, setSurgeons] = useState([]);
    const [wards, setWards] = useState([]);
    const [form, setForm] = useState({ surgeonDoctorId: '', surgeonName: '', anaesthetistName: '', scheduledAt: '', otWardId: '' });
    const [saving, setSaving] = useState(false);
    const isOther = form.surgeonDoctorId === '__OTHER__';

    useEffect(() => {
        otService.getSurgeons().then((s) => setSurgeons(Array.isArray(s) ? s : [])).catch(() => setSurgeons([]));
        wardService.getWards().then((w) => {
            const arr = Array.isArray(w) ? w : (w?.content || w?.data || []);
            setWards(arr.filter((x) => (x.wardName || '').toUpperCase().includes('OT')));
        }).catch(() => setWards([]));
    }, []);

    const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

    const submit = async () => {
        if (!form.surgeonDoctorId || !form.scheduledAt || !form.otWardId) {
            toastError('Surgeon, date/time and OT ward are required'); return;
        }
        if (isOther && !form.surgeonName.trim()) {
            toastError("Enter the operator's name for 'Other'"); return;
        }
        setSaving(true);
        try {
            await otService.schedule(surgery.publicId, {
                surgeonDoctorId: isOther ? null : Number(form.surgeonDoctorId),
                surgeonName: isOther ? form.surgeonName.trim() : null,
                anaesthetistName: form.anaesthetistName.trim() || null,
                scheduledAt: form.scheduledAt,
                otWardId: Number(form.otWardId),
            });
            success('Surgery scheduled');
            onScheduled && onScheduled();
            onClose();
        } catch (e) {
            toastError(e?.response?.data?.error || 'Failed to schedule surgery');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" onClick={onClose}>
            <div className="bg-white rounded-2xl w-full max-w-lg p-6" onClick={(e) => e.stopPropagation()}>
                <h2 className="text-xl font-bold text-gray-900 mb-1">Schedule Surgery</h2>
                <p className="text-sm text-gray-500 mb-4">{surgery.patientName} · {surgery.procedureName}</p>
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-1">Assign Surgeon (Doctor) *</label>
                        <select value={form.surgeonDoctorId} onChange={(e) => set('surgeonDoctorId', e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg">
                            <option value="">Select doctor…</option>
                            {surgeons.map((s) => <option key={s.doctorId} value={s.doctorId}>{s.name} ({s.specialization})</option>)}
                            <option value="__OTHER__">Other (external / not listed)…</option>
                        </select>
                        {surgeons.length === 0 && <p className="text-xs text-amber-600 mt-1">No listed doctors — use "Other" to enter a name.</p>}
                        {isOther && (
                            <input value={form.surgeonName} onChange={(e) => set('surgeonName', e.target.value)}
                                placeholder="Operator's name (e.g. Dr. anaesthetist / visiting surgeon)"
                                className="w-full mt-2 px-3 py-2 border border-gray-300 rounded-lg" />
                        )}
                    </div>
                    <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-1">Anaesthetist <span className="font-normal text-gray-400">(optional)</span></label>
                        <input value={form.anaesthetistName} onChange={(e) => set('anaesthetistName', e.target.value)}
                            placeholder="Anaesthetist's name, if present"
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg" />
                    </div>
                    <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-1">Date &amp; Time *</label>
                        <input type="datetime-local" value={form.scheduledAt} onChange={(e) => set('scheduledAt', e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg" />
                    </div>
                    <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-1">OT Ward *</label>
                        <select value={form.otWardId} onChange={(e) => set('otWardId', e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg">
                            <option value="">Select OT ward…</option>
                            {wards.map((w) => <option key={w.wardId} value={w.wardId}>{w.wardName}</option>)}
                        </select>
                        {wards.length === 0 && <p className="text-xs text-amber-600 mt-1">No OT ward found — create a ward named "OT" first.</p>}
                    </div>
                </div>
                <div className="flex justify-end gap-3 mt-6">
                    <button onClick={onClose} className="px-4 py-2 rounded-lg font-semibold text-gray-600 hover:bg-gray-100">Cancel</button>
                    <button onClick={submit} disabled={saving}
                        className={`px-4 py-2 rounded-lg font-semibold text-white ${saving ? 'bg-gray-400' : 'bg-gray-900 hover:bg-gray-800'}`}>
                        {saving ? 'Scheduling…' : 'Schedule'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ScheduleSurgeryModal;
