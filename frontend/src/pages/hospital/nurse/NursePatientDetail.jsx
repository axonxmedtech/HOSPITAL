import React, { useState, useEffect } from 'react';
import nurseService from '../../../services/nurseService';
import { useToast } from '../../../context/ToastContext';
import LoadingSpinner from '../../../components/LoadingSpinner';
import VitalsPanel from './VitalsPanel';
import NotesPanel from './NotesPanel';
import MedicationPanel from './MedicationPanel';
import InitialAssessmentPanel from './InitialAssessmentPanel';
import VulnerabilityAssessmentPanel from './VulnerabilityAssessmentPanel';
import SugarChartPanel from './SugarChartPanel';
import ConsentFormsPanel from './ConsentFormsPanel';
import OtNotesSection from '../ot/OtNotesSection';
import formAccessService from '../../../services/formAccessService';

/**
 * NursePatientDetail - read-only bedside view for a nurse (Phase 1).
 * All clinical/billing data is view-only. Nurse-writable tabs (Vitals,
 * Medication, Notes) are added in milestones M4-M6.
 */
const Section = ({ title, children }) => (
    <div className="bg-white border border-gray-200 rounded-xl">
        <div className="px-5 py-3 border-b border-gray-100">
            <h3 className="font-bold text-gray-800 text-sm">{title}</h3>
        </div>
        <div className="p-5">{children}</div>
    </div>
);

const Field = ({ label, value }) => (
    <div>
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">{label}</p>
        <p className="text-sm text-gray-900 mt-0.5">{value || '—'}</p>
    </div>
);

const money = (v) => `₹${Number(v ?? 0).toLocaleString('en-IN')}`;

const NursePatientDetail = ({ admissionId, onBack, refreshKey }) => {
    const { error: toastError } = useToast();
    const [loading, setLoading] = useState(true);
    const [d, setD] = useState(null);
    const [tab, setTab] = useState('overview');
    const [hasSurgery, setHasSurgery] = useState(false);
    const [formVerdicts, setFormVerdicts] = useState({});

    useEffect(() => {
        let active = true;
        formAccessService.effective()
            .then((v) => { if (active) setFormVerdicts(v || {}); })
            .catch(() => { if (active) setFormVerdicts({}); });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        let active = true;
        setLoading(true);
        nurseService.getPatientDetail(admissionId)
            .then((res) => { if (active) setD(res); })
            .catch((err) => {
                if (!active) return;
                if (err.response?.status === 403) toastError('You are not assigned to this patient');
                else toastError('Failed to load patient');
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [admissionId, refreshKey, toastError]);

    // Surgery patients get an extra "Consent Forms" sub-tab — only once the
    // surgeon is assigned (surgery scheduled or in progress).
    useEffect(() => {
        let active = true;
        nurseService.getActiveSurgery(admissionId)
            .then((s) => {
                const assigned = !!s && (s.status === 'SCHEDULED' || s.status === 'IN_PROGRESS');
                if (active) setHasSurgery(assigned);
            })
            .catch(() => { if (active) setHasSurgery(false); });
        return () => { active = false; };
    }, [admissionId, refreshKey]);

    // Nursing tabs collapse to HIDDEN based on the Files & Access verdicts.
    // Computed unconditionally (not after the early returns below) because a
    // guard effect keyed on `tabs` must be a hook, and hooks can't follow
    // conditional returns.
    const NURSING_FORM_KEY = {
        vitals: 'VITALS',
        notes: 'NOTES',
        assessment: 'INITIAL_ASSESSMENT',
        vulnerability: 'VULNERABILITY_ASSESSMENT',
        sugar: 'SUGAR_CHART',
    };
    const verdictFor = (tabId) => formVerdicts[NURSING_FORM_KEY[tabId]] || 'EDITABLE';

    const tabs = [
        { id: 'overview', label: 'Overview' },
        ...(verdictFor('vitals') !== 'HIDDEN' ? [{ id: 'vitals', label: 'Vitals' }] : []),
        { id: 'medication', label: 'Medication' },
        ...(verdictFor('notes') !== 'HIDDEN' ? [{ id: 'notes', label: 'Notes' }] : []),
        ...(verdictFor('assessment') !== 'HIDDEN' ? [{ id: 'assessment', label: 'Initial Assessment' }] : []),
        ...(verdictFor('vulnerability') !== 'HIDDEN' ? [{ id: 'vulnerability', label: 'Vulnerability Assessment' }] : []),
        ...(verdictFor('sugar') !== 'HIDDEN' ? [{ id: 'sugar', label: 'Sugar Chart' }] : []),
        ...(hasSurgery ? [{ id: 'consent', label: 'Consent Forms' }] : []),
    ];

    // Guard against a stale active tab (e.g. it was just hidden).
    useEffect(() => { if (!tabs.some((t) => t.id === tab)) setTab('overview'); }, [tabs, tab]);

    if (loading) return <LoadingSpinner />;
    if (!d) return (
        <button onClick={onBack} className="text-sm font-semibold text-gray-600 hover:text-gray-900">← Back</button>
    );

    return (
        <div className="space-y-5">
            <div className="flex items-center justify-between">
                <button onClick={onBack} className="text-sm font-semibold text-gray-600 hover:text-gray-900">← My Patients</button>
                <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-gray-100 text-gray-700">{d.status}</span>
            </div>

            {/* Patient header */}
            <div className="bg-white border border-gray-200 rounded-xl px-5 py-4 flex items-center justify-between">
                <div>
                    <h2 className="text-lg font-bold text-gray-900">{d.patientName}</h2>
                    <p className="text-xs text-gray-500">
                        {d.ipdNumber}{d.bedCode ? ` · Bed ${d.bedCode}` : ''}{d.wardName ? ` · ${d.wardName}` : ''}
                    </p>
                </div>
            </div>

            <OtNotesSection admissionId={admissionId} />

            {/* Tabs */}
            <div className="flex gap-1 border-b border-gray-200">
                {tabs.map((t) => (
                    <button
                        key={t.id}
                        onClick={() => setTab(t.id)}
                        className={`px-4 py-2 text-sm font-semibold border-b-2 -mb-px ${tab === t.id ? 'border-gray-900 text-gray-900' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
                    >
                        {t.label}
                    </button>
                ))}
            </div>

            {tab === 'vitals' && <VitalsPanel admissionId={admissionId} readOnly={verdictFor('vitals') === 'READ_ONLY'} />}

            {tab === 'medication' && <MedicationPanel admissionId={admissionId} />}

            {tab === 'notes' && <NotesPanel admissionId={admissionId} readOnly={verdictFor('notes') === 'READ_ONLY'} />}

            {tab === 'assessment' && <InitialAssessmentPanel admissionId={admissionId} readOnly={verdictFor('assessment') === 'READ_ONLY'} />}

            {tab === 'vulnerability' && <VulnerabilityAssessmentPanel admissionId={admissionId} readOnly={verdictFor('vulnerability') === 'READ_ONLY'} />}

            {tab === 'sugar' && <SugarChartPanel admissionId={admissionId} readOnly={verdictFor('sugar') === 'READ_ONLY'} />}

            {tab === 'consent' && <ConsentFormsPanel admissionId={admissionId} formVerdicts={formVerdicts} />}

            {tab === 'overview' && (
            <>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                <Section title="Demographics">
                    <div className="grid grid-cols-2 gap-4">
                        <Field label="Name" value={d.patientName} />
                        <Field label="Age / Sex" value={`${d.age ?? '—'}${d.gender ? ` / ${d.gender}` : ''}`} />
                        <Field label="Phone" value={d.phone} />
                        <Field label="Patient ID" value={d.patientPublicId} />
                    </div>
                </Section>

                <Section title="Admission">
                    <div className="grid grid-cols-2 gap-4">
                        <Field label="IPD No." value={d.ipdNumber} />
                        <Field label="Type" value={d.admissionType} />
                        <Field label="Ward" value={d.wardName} />
                        <Field label="Bed" value={d.bedCode} />
                        <Field label="Doctor" value={d.doctorName} />
                        <Field label="Admitted" value={d.admissionDateTime ? new Date(d.admissionDateTime).toLocaleString('en-IN') : '—'} />
                    </div>
                </Section>
            </div>

            <Section title="Diagnosis & Doctor Notes">
                <div className="grid grid-cols-1 gap-4">
                    <Field label="Primary Diagnosis" value={d.primaryDiagnosis || d.diagnosis} />
                    <Field label="Treatment Notes" value={d.treatmentNotes} />
                    <Field label="Follow-up Date" value={d.followUpDate} />
                </div>
            </Section>

            <Section title="Current Medicines (Prescribed)">
                {(!d.prescriptions || d.prescriptions.length === 0) ? (
                    <p className="text-sm text-gray-500">No active prescriptions.</p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="min-w-full text-sm">
                            <thead>
                                <tr className="text-left text-xs font-semibold text-gray-500 border-b border-gray-200">
                                    <th className="px-3 py-2">MEDICINE</th>
                                    <th className="px-3 py-2">DOSAGE</th>
                                    <th className="px-3 py-2">FREQUENCY</th>
                                    <th className="px-3 py-2">ROUTE</th>
                                    <th className="px-3 py-2">DURATION</th>
                                </tr>
                            </thead>
                            <tbody>
                                {d.prescriptions.map((m, i) => (
                                    <tr key={i} className="border-b border-gray-100">
                                        <td className="px-3 py-2 font-medium text-gray-900">{m.medicineName}</td>
                                        <td className="px-3 py-2">{m.dosage || '—'}</td>
                                        <td className="px-3 py-2">{m.frequency || '—'}</td>
                                        <td className="px-3 py-2">{m.route || '—'}</td>
                                        <td className="px-3 py-2">{m.duration || '—'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
                <p className="text-xs text-gray-400 mt-3">
                    Recording medication administration against these orders arrives in a later update.
                </p>
            </Section>

            <Section title="Billing Summary (read-only)">
                <div className="grid grid-cols-3 gap-4">
                    <Field label="Total" value={money(d.billing?.total)} />
                    <Field label="Paid" value={money(d.billing?.paid)} />
                    <Field label="Balance" value={money(d.billing?.balance)} />
                </div>
            </Section>
            </>
            )}
        </div>
    );
};

export default NursePatientDetail;
