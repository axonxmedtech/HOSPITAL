import React, { useState, useEffect, useCallback } from 'react';
import formAccessService from '../../services/formAccessService';
import { useToast } from '../../context/ToastContext';

const ACCESS_OPTIONS = [
    { value: 'DOCTOR', label: 'Doctor' },
    { value: 'NURSE', label: 'Nurse' },
    { value: 'BOTH', label: 'Both' },
];

/**
 * FilesAndAccessCard - admin table controlling which forms are active and who
 * may edit each (Files & Access, Phase 1). Rows come from the backend registry;
 * each change PUTs an override.
 */
const FilesAndAccessCard = () => {
    const { success, error: toastError } = useToast();
    const [forms, setForms] = useState([]);
    const [loading, setLoading] = useState(true);
    const [savingKey, setSavingKey] = useState(null);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setForms(await formAccessService.list());
        } catch (e) {
            toastError(e?.response?.data?.error || 'Failed to load forms');
        } finally {
            setLoading(false);
        }
    }, [toastError]);

    useEffect(() => { load(); }, [load]);

    const save = async (form, patch) => {
        const next = { enabled: form.enabled, accessRole: form.accessRole, ...patch };
        setSavingKey(form.key);
        setForms((prev) => prev.map((f) => (f.key === form.key ? { ...f, ...next } : f)));
        try {
            await formAccessService.update(form.key, next);
            success('Form access updated');
        } catch (e) {
            toastError(e?.response?.data?.error || 'Failed to update');
            load();
        } finally {
            setSavingKey(null);
        }
    };

    if (loading) return <div className="p-6 text-gray-500 text-sm">Loading forms…</div>;

    const groups = [
        { category: 'NURSING', title: 'Nursing Records' },
        { category: 'OT', title: 'OT / Surgery Forms' },
    ];

    return (
        <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 mt-6">
            <h3 className="text-lg font-bold text-gray-900 mb-1">Files &amp; Access</h3>
            <p className="text-sm text-gray-500 mb-5">Turn forms on or off for this hospital and choose who can edit each one. Off forms are hidden everywhere.</p>
            {groups.map((g) => {
                const rows = forms.filter((f) => f.category === g.category);
                if (rows.length === 0) return null;
                return (
                    <div key={g.category} className="mb-6 last:mb-0">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-gray-400 mb-2">{g.title}</h4>
                        <div className="border border-gray-200 rounded-xl overflow-hidden">
                            <table className="w-full text-sm">
                                <thead className="bg-gray-50 border-b border-gray-200">
                                    <tr>
                                        <th className="px-4 py-2.5 text-left font-semibold text-gray-600">Form</th>
                                        <th className="px-4 py-2.5 text-left font-semibold text-gray-600">Accessed by</th>
                                        <th className="px-4 py-2.5 text-left font-semibold text-gray-600">Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((f) => (
                                        <tr key={f.key} className={`border-b border-gray-100 last:border-0 ${!f.enabled ? 'bg-gray-50/60' : ''}`}>
                                            <td className={`px-4 py-3 font-medium ${f.enabled ? 'text-gray-900' : 'text-gray-400'}`}>{f.label}</td>
                                            <td className="px-4 py-3">
                                                <select
                                                    value={f.accessRole}
                                                    disabled={!f.enabled || savingKey === f.key}
                                                    onChange={(e) => save(f, { accessRole: e.target.value })}
                                                    className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg disabled:bg-gray-100 disabled:text-gray-400"
                                                >
                                                    {ACCESS_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                                                </select>
                                            </td>
                                            <td className="px-4 py-3">
                                                <button
                                                    type="button"
                                                    disabled={savingKey === f.key}
                                                    onClick={() => save(f, { enabled: !f.enabled })}
                                                    className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${f.enabled ? 'bg-gray-900' : 'bg-gray-300'}`}
                                                    aria-label={f.enabled ? 'On' : 'Off'}
                                                >
                                                    <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${f.enabled ? 'translate-x-6' : 'translate-x-1'}`} />
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

export default FilesAndAccessCard;
