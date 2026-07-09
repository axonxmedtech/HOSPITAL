import React, { useState, useEffect, useCallback } from 'react';
import nurseService from '../../../services/nurseService';
import authService from '../../../services/authService';
import { useToast } from '../../../context/ToastContext';
import LoadingSpinner from '../../../components/LoadingSpinner';
import { titleCase } from '../../../utils/text';

const esch = (v) => (v == null ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'));

// Prints vitals as the NABH "INPUT & OUTPUT CHART". Vital columns are filled
// from our data; input/output columns are blank for offline entry. Flows to
// extra pages with repeating headers.
export const buildIoChartHtml = (rows, f, hospital) => {
    const hname = esch(titleCase(hospital.name)) || 'Hospital';
    const patientName = [f.patientSurname, f.patientFirstName, f.husbandFatherName].filter(Boolean).join(' ');
    const sex = (f.sex || '').toUpperCase();
    const isM = sex.startsWith('M'), isF = sex.startsWith('F');
    const logo = hospital.logo ? `<img src="${esch(hospital.logo)}" onerror="this.style.display='none'" style="height:56px;width:auto;object-fit:contain"/>` : '';
    const tm = (dt) => dt ? new Date(dt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) : '';
    const ordered = [...(rows || [])].sort((a, b) => new Date(a.recordedAt) - new Date(b.recordedAt));
    const bp = (v) => (v.bpSystolic != null || v.bpDiastolic != null) ? `${v.bpSystolic ?? ''}/${v.bpDiastolic ?? ''}` : '';
    const dataRows = ordered.map((v) =>
        `<tr><td>${esch(tm(v.recordedAt))}</td><td>${esch(v.temperature)}</td><td>${esch(v.pulse)}</td><td>${esch(v.respiratoryRate)}</td><td>${esch(bp(v))}</td><td></td><td></td><td></td><td></td><td></td></tr>`).join('');
    const blanks = Math.max(4, 26 - ordered.length);
    const blankRows = Array.from({ length: blanks }).map(() => '<tr>' + '<td>&nbsp;</td>' + '<td></td>'.repeat(9) + '</tr>').join('');

    return `<!doctype html><html><head><meta charset="utf-8"><title>Input & Output Chart</title>
    <style>
      @page { size: A4; margin: 8mm; }
      * { box-sizing: border-box; }
      body { font-family: Arial, "Noto Sans", sans-serif; color:#111; font-size:11px; margin:0; }
      .head { text-align:center; }
      .hname { font-size:22px; font-weight:800; color:#1d4ed8; margin:6px 0 2px; }
      .haddr { font-size:12px; font-weight:600; color:#7c3aed; }
      .title { font-size:16px; font-weight:800; margin:12px 0 8px; }
      .idbox { border:1px solid #111; border-bottom:0; padding:8px 12px; font-size:12px; }
      .idrow { display:flex; gap:24px; margin:6px 0; }
      .idrow > span { flex:1; display:flex; align-items:flex-end; gap:6px; min-width:0; }
      .flexval { border-bottom:1px solid #666; flex:1; padding:0 4px; min-height:16px; }
      .sex b { border:1px solid #111; padding:0 6px; margin:0 2px; } .sex .on { background:#111; color:#fff; }
      table { width:100%; border-collapse:collapse; }
      th, td { border:1px solid #111; padding:3px 4px; text-align:center; }
      thead { display: table-header-group; }
      thead th { background:#fff; font-size:10.5px; font-weight:700; }
      tr { page-break-inside: avoid; }
      td { height:22px; }
    </style></head><body>
      <div class="head">${logo}<div class="hname">${hname}</div><div class="haddr">${esch(hospital.address)}</div><div class="title">INPUT &amp; OUTPUT CHART</div></div>
      <div class="idbox">
        <div class="idrow">
          <span><b>UHID No. :</b> <span class="flexval">${esch(hospital.customId)}</span></span>
          <span><b>IPD No. :</b> <span class="flexval">${esch(f.ipdRegistrationNo)}</span></span>
          <span><b>MLC No. :</b> <span class="flexval"></span></span>
          <span><b>Bed No. :</b> <span class="flexval">${esch(f.bedNo)}</span></span>
        </div>
        <div class="idrow"><span style="flex:2"><b>Patient Name :</b> <span class="flexval">${esch(patientName)}</span></span></div>
        <div class="idrow">
          <span><b>Age :</b> <span class="flexval">${esch(f.age)}</span></span>
          <span style="flex:0.7"><b>Sex :</b> <span class="sex"><b class="${isM ? 'on' : ''}">M</b><b class="${isF ? 'on' : ''}">F</b></span></span>
          <span><b>Date :</b> <span class="flexval">${esch(f.admittedDate)}</span></span>
          <span><b>Time :</b> <span class="flexval">${esch(f.admittedTime)}</span></span>
        </div>
      </div>
      <table>
        <thead>
          <tr><th colspan="5"></th><th colspan="2">INPUT</th><th colspan="3">OUTPUT</th></tr>
          <tr><th>TIME</th><th>TEMP</th><th>PULSE</th><th>RESP.</th><th>B. P.</th><th>I.V. FLUIDS</th><th>ORAL</th><th>RYLES TUBE ASPIRATION</th><th>URINE O/P</th><th>VOMITING MONITION</th></tr>
        </thead>
        <tbody>${dataRows}${blankRows}</tbody>
      </table>
      <div style="margin-top:14px;display:flex;gap:60px;font-size:12px">
        <div>Ref. Doctor : <b>${esch(f.refDr || f.underCareOfDr)}</b></div>
        <div>Nurse : <b>${esch(hospital.nurse)}</b></div>
      </div>
    <script>(function(){function go(){setTimeout(function(){window.print();},300);}if(document.readyState==='complete'){go();}else{window.addEventListener('load',go);}})();</script>
    </body></html>`;
};

/**
 * VitalsPanel - record + review IPD vitals for one admission (Phase 1 Nurse
 * module, M4). Abnormal values are flagged client-side against display-only
 * normal ranges; no alerting.
 */

// Display-only normal ranges (not stored). [min, max]. Temperature is in °F.
const NORMAL = {
    temperature: [97, 99],
    pulse: [60, 100],
    bpSystolic: [90, 120],
    bpDiastolic: [60, 80],
    respiratoryRate: [12, 20],
    spo2: [95, 100],
    painScore: [0, 0],
};

const isAbnormal = (key, value) => {
    if (value == null || value === '' || !NORMAL[key]) return false;
    const n = Number(value);
    if (Number.isNaN(n)) return false;
    return n < NORMAL[key][0] || n > NORMAL[key][1];
};

const Metric = ({ label, value, unit, abnormal }) => {
    if (value == null || value === '') return null;
    return (
        <span className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium ${abnormal ? 'bg-red-50 text-red-700' : 'bg-gray-100 text-gray-700'}`}>
            {label}: {value}{unit}
            {abnormal && <span className="font-bold">!</span>}
        </span>
    );
};

const emptyForm = {
    temperature: '', pulse: '', bpSystolic: '', bpDiastolic: '',
    respiratoryRate: '', spo2: '', weight: '', painScore: '', remarks: '',
};

const VitalsPanel = ({ admissionId, readOnly = false }) => {
    const { success, error: toastError } = useToast();
    const [loading, setLoading] = useState(true);
    const [rows, setRows] = useState([]);
    const [f, setF] = useState({}); // patient header data for the I/O chart print
    const [form, setForm] = useState(emptyForm);
    const [submitting, setSubmitting] = useState(false);
    const user = authService.getCurrentUser();

    // Separate Nurse Login OFF ("Shared Login") -> a required "Performed By
    // Nurse" dropdown is shown and its selection is sent with the payload.
    const [separateLogin, setSeparateLogin] = useState(true);
    const [nurses, setNurses] = useState([]);
    const [performedByNurseId, setPerformedByNurseId] = useState('');

    const load = useCallback(() => {
        setLoading(true);
        nurseService.getVitals(admissionId)
            .then((d) => setRows(Array.isArray(d) ? d : []))
            .catch(() => toastError('Failed to load vitals'))
            .finally(() => setLoading(false));
    }, [admissionId, toastError]);

    useEffect(() => { load(); }, [load]);
    useEffect(() => {
        let active = true;
        nurseService.getAdmissionForm(admissionId).then((d) => { if (active) setF(d || {}); }).catch(() => {});
        return () => { active = false; };
    }, [admissionId]);

    useEffect(() => {
        let active = true;
        nurseService.getSeparateNurseLogin().then((v) => { if (active) setSeparateLogin(v); }).catch(() => {});
        return () => { active = false; };
    }, []);

    useEffect(() => {
        if (separateLogin === false && f.wardId) {
            nurseService.getWardStaffNurses(f.wardId)
                .then((list) => setNurses(Array.isArray(list) ? list : []))
                .catch(() => setNurses([]));
        }
    }, [separateLogin, f.wardId]);

    const setField = (k, v) => setForm((f) => ({ ...f, [k]: v }));

    const num = (v) => (v === '' || v == null ? null : Number(v));

    const handleSubmit = async () => {
        const payload = {
            ipdAdmissionId: admissionId,
            temperature: num(form.temperature),
            pulse: num(form.pulse),
            bpSystolic: num(form.bpSystolic),
            bpDiastolic: num(form.bpDiastolic),
            respiratoryRate: num(form.respiratoryRate),
            spo2: num(form.spo2),
            weight: num(form.weight),
            painScore: num(form.painScore),
            remarks: form.remarks || null,
        };
        const hasAny = ['temperature', 'pulse', 'bpSystolic', 'bpDiastolic', 'respiratoryRate', 'spo2', 'weight', 'painScore']
            .some((k) => payload[k] != null);
        if (!hasAny) { toastError('Enter at least one measurement'); return; }
        if (separateLogin === false) {
            if (!performedByNurseId) { toastError('Select the nurse who performed this'); return; }
            payload.performedByNurseId = Number(performedByNurseId);
        }

        setSubmitting(true);
        try {
            await nurseService.createVitals(payload);
            success('Vitals recorded');
            setForm(emptyForm);
            setPerformedByNurseId('');
            load();
        } catch (err) {
            const data = err.response?.data;
            const msg = data?.error || data?.message || (typeof data === 'string' ? data : null) || 'Failed to record vitals';
            toastError(msg);
        } finally {
            setSubmitting(false);
        }
    };

    const fmt = (dt) => dt ? new Date(dt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';

    const inputs = [
        ['temperature', 'Temp (°F)', 0.1],
        ['pulse', 'Pulse (bpm)', 1],
        ['bpSystolic', 'BP Sys', 1],
        ['bpDiastolic', 'BP Dia', 1],
        ['respiratoryRate', 'Resp (rpm)', 1],
        ['spo2', 'SpO₂ (%)', 1],
        ['weight', 'Weight (kg)', 0.1],
        ['painScore', 'Pain (0-10)', 1],
    ];

    return (
        <fieldset disabled={readOnly} style={{ display: 'contents' }}>
            {readOnly && (
                <div className="mb-3 text-xs font-semibold text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                    Read-only — editing this form is disabled for your role (Files &amp; Access).
                </div>
            )}
            <div className="space-y-5">
            <div className="bg-white border border-gray-200 rounded-xl p-5">
                <h3 className="font-bold text-gray-800 text-sm mb-4">Record Vitals</h3>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    {inputs.map(([key, label, step]) => (
                        <div key={key}>
                            <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
                            <input
                                type="number"
                                step={step}
                                value={form[key]}
                                onChange={(e) => setField(key, e.target.value)}
                                className={`w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent ${isAbnormal(key, form[key]) ? 'border-red-400 bg-red-50' : 'border-gray-300'}`}
                            />
                        </div>
                    ))}
                </div>
                {separateLogin === false && (
                    <div className="mt-3">
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
                <div className="mt-3">
                    <label className="block text-xs font-medium text-gray-600 mb-1">Remarks</label>
                    <input
                        type="text"
                        value={form.remarks}
                        onChange={(e) => setField('remarks', e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    />
                </div>
                <div className="mt-4 flex justify-end">
                    <button
                        onClick={handleSubmit}
                        disabled={submitting}
                        className={`px-4 py-2 text-sm font-semibold text-white rounded-lg ${submitting ? 'bg-gray-400 cursor-not-allowed' : 'bg-gray-900 hover:bg-gray-800'}`}
                    >
                        {submitting ? 'Saving…' : 'Record Vitals'}
                    </button>
                </div>
            </div>

            <div className="bg-white border border-gray-200 rounded-xl">
                <div className="px-5 py-3 border-b border-gray-100">
                    <h3 className="font-bold text-gray-800 text-sm">Vitals Timeline</h3>
                </div>
                {loading ? (
                    <LoadingSpinner />
                ) : rows.length === 0 ? (
                    <p className="px-5 py-6 text-sm text-gray-500">No vitals recorded yet.</p>
                ) : (
                    <ul className="divide-y divide-gray-100">
                        {rows.map((v) => (
                            <li key={v.publicId || v.id} className="px-5 py-3">
                                <div className="flex items-center justify-between mb-2">
                                    <span className="text-xs font-semibold text-gray-500">{fmt(v.recordedAt)}</span>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                    <Metric label="Temp" value={v.temperature} unit="°F" abnormal={isAbnormal('temperature', v.temperature)} />
                                    <Metric label="Pulse" value={v.pulse} unit="" abnormal={isAbnormal('pulse', v.pulse)} />
                                    {(v.bpSystolic != null || v.bpDiastolic != null) && (
                                        <span className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium ${(isAbnormal('bpSystolic', v.bpSystolic) || isAbnormal('bpDiastolic', v.bpDiastolic)) ? 'bg-red-50 text-red-700' : 'bg-gray-100 text-gray-700'}`}>
                                            BP: {v.bpSystolic ?? '—'}/{v.bpDiastolic ?? '—'}
                                        </span>
                                    )}
                                    <Metric label="Resp" value={v.respiratoryRate} unit="" abnormal={isAbnormal('respiratoryRate', v.respiratoryRate)} />
                                    <Metric label="SpO₂" value={v.spo2} unit="%" abnormal={isAbnormal('spo2', v.spo2)} />
                                    <Metric label="Weight" value={v.weight} unit="kg" abnormal={false} />
                                    <Metric label="Pain" value={v.painScore} unit="/10" abnormal={isAbnormal('painScore', v.painScore)} />
                                </div>
                                {v.remarks && <p className="text-xs text-gray-500 mt-2">{v.remarks}</p>}
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
        </fieldset>
    );
};

export default VitalsPanel;
