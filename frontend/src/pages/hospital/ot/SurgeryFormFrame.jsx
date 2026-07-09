import React, { useEffect, useState, useCallback } from 'react';
import nurseService from '../../../services/nurseService';
import otService from '../../../services/otService';
import authService from '../../../services/authService';
import { useToast } from '../../../context/ToastContext';

/**
 * SurgeryFormFrame - shared shell for every OT/NABH surgery form.
 *
 * Handles the common plumbing so each form only declares its fields + print
 * layout:
 *  - loads the saved values (otService.getSurgeryForm) and the patient's
 *    admission record (for pre-fill + hospital branding),
 *  - renders the form body via `renderFields({ data, set, prefill })`,
 *  - Save persists the values; editing after a save marks it dirty again,
 *  - Print is enabled only once saved, and opens `buildPrintHtml(data, prefill, hospital)`.
 *
 * Props:
 *  admissionId, formType, title, code
 *  defaults        - default field values (object)
 *  renderFields    - ({ data, set, prefill }) => JSX
 *  buildPrintHtml  - (data, prefill, hospital) => htmlString
 *  onClose         - close handler
 */
const SurgeryFormFrame = ({ admissionId, formType, title, code, defaults = {}, renderFields, buildPrintHtml, onClose, readOnly = false }) => {
    const { success, error: toastError } = useToast();
    const user = authService.getCurrentUser();
    const [data, setData] = useState(defaults);
    const [prefill, setPrefill] = useState({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false); // true when current values are persisted

    // Separate Nurse Login OFF ("Shared Login") -> a required "Performed By
    // Nurse" dropdown is shown and its selection is sent with the save payload.
    const [separateLogin, setSeparateLogin] = useState(true);
    const [nurses, setNurses] = useState([]);
    const [performedByNurseId, setPerformedByNurseId] = useState('');

    useEffect(() => {
        let active = true;
        nurseService.getSeparateNurseLogin().then((v) => { if (active) setSeparateLogin(v); }).catch(() => {});
        return () => { active = false; };
    }, []);

    useEffect(() => {
        if (separateLogin === false && prefill.wardId) {
            nurseService.getWardStaffNurses(prefill.wardId)
                .then((list) => setNurses(Array.isArray(list) ? list : []))
                .catch(() => setNurses([]));
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [separateLogin, prefill.wardId]);

    useEffect(() => {
        let active = true;
        setLoading(true);
        Promise.all([
            otService.getSurgeryForm(admissionId, formType).catch(() => null),
            nurseService.getAdmissionForm(admissionId).catch(() => ({})),
        ]).then(([savedForm, adm]) => {
            if (!active) return;
            setPrefill(adm || {});
            if (savedForm && savedForm.data && Object.keys(savedForm.data).length) {
                setData({ ...defaults, ...savedForm.data });
                setSaved(true);
            } else {
                setData(defaults);
                setSaved(false);
            }
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [admissionId, formType]);

    const set = useCallback((key, value) => {
        setData((d) => ({ ...d, [key]: value }));
        setSaved(false); // editing invalidates the printable saved state
    }, []);

    const handleSave = async () => {
        if (separateLogin === false && !performedByNurseId) {
            toastError('Select the nurse who performed this');
            return;
        }
        setSaving(true);
        try {
            await otService.saveSurgeryForm(admissionId, formType, data, separateLogin === false ? Number(performedByNurseId) : undefined);
            setSaved(true);
            success('Form saved');
        } catch (e) {
            toastError(e?.response?.data?.error || 'Failed to save form');
        } finally {
            setSaving(false);
        }
    };

    const hospital = () => ({
        name: prefill.hospitalName || user?.hospitalName,
        address: prefill.hospitalAddress || user?.hospitalAddress,
        logo: prefill.hospitalLogoUrl || user?.logoUrl,
        customId: prefill.hospitalCustomId,
        nurse: user?.name,
    });

    const handlePrint = () => {
        if (!saved) { toastError('Save the form before printing'); return; }
        const w = window.open('', '_blank');
        if (!w) { toastError('Popup blocked — allow popups to print'); return; }
        w.document.write(buildPrintHtml(data, prefill, hospital()));
        w.document.close();
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-start justify-center z-50 p-4 overflow-y-auto" onClick={onClose}>
            <div className="bg-white rounded-2xl w-full max-w-3xl my-6" onClick={(e) => e.stopPropagation()}>
                <div className="flex items-start justify-between px-6 py-4 border-b border-gray-100 sticky top-0 bg-white rounded-t-2xl">
                    <div>
                        <h2 className="text-lg font-bold text-gray-900">{title}</h2>
                        {code && <p className="text-xs text-gray-400 mt-0.5">{code}</p>}
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-700 text-2xl leading-none">×</button>
                </div>

                <fieldset disabled={readOnly} style={{ display: 'contents' }}>
                {readOnly && (
                    <div className="mx-6 mt-4 text-xs font-semibold text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                        Read-only — editing this form is disabled for your role (Files &amp; Access).
                    </div>
                )}
                <div className="px-6 py-5">
                    {loading ? (
                        <div className="text-center text-gray-400 py-12">Loading…</div>
                    ) : (
                        renderFields({ data, set, prefill })
                    )}
                </div>

                {separateLogin === false && (
                    <div className="px-6 pb-4">
                        <label className="block text-xs font-medium text-gray-600 mb-1">Performed By Nurse *</label>
                        <select
                            value={performedByNurseId}
                            onChange={(e) => setPerformedByNurseId(e.target.value)}
                            required
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                        >
                            <option value="">Select nurse…</option>
                            {nurses.map((n) => (
                                <option key={n.id} value={n.id}>{n.name}</option>
                            ))}
                        </select>
                    </div>
                )}
                </fieldset>
                <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100 sticky bottom-0 bg-white rounded-b-2xl">
                    {!saved && !readOnly && <span className="mr-auto text-xs text-amber-600">Unsaved — save to enable printing</span>}
                    <button onClick={onClose} className="px-4 py-2 rounded-lg font-semibold text-gray-600 hover:bg-gray-100">Close</button>
                    {!readOnly && (
                        <button onClick={handleSave} disabled={saving || loading}
                            className={`px-4 py-2 rounded-lg font-semibold text-white ${saving ? 'bg-gray-400' : 'bg-gray-900 hover:bg-gray-800'}`}>
                            {saving ? 'Saving…' : 'Save'}
                        </button>
                    )}
                    <button onClick={handlePrint} disabled={!saved || loading}
                        className={`px-4 py-2 rounded-lg font-semibold ${saved ? 'bg-indigo-600 text-white hover:bg-indigo-700' : 'bg-gray-200 text-gray-400 cursor-not-allowed'}`}>
                        Print
                    </button>
                </div>
            </div>
        </div>
    );
};

export default SurgeryFormFrame;
