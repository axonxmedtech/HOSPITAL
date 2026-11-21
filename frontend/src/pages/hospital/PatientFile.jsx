import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useToast } from '../../context/ToastContext';
import useLiveRefresh from '../../hooks/useLiveRefresh';
import apiClient from '../../services/apiService';
import patientDocumentService from '../../services/patientDocumentService';
import { extractApiError } from '../../utils/apiError';
import { formatDay, formatDays, formatWhen } from '../../utils/date';
import { documentTypeLabel } from '../../utils/patientDocuments';
import { printBlob, printPdf } from '../../utils/printPdf';

/**
 * The Patient File: everything that ever happened to one patient here — every OPD visit, IPD
 * stay, ICU stay, surgery, bill and document, current or finished — on one page, newest first.
 * Read-only. What each role sees is decided by the server (reception gets no clinical detail,
 * nurses only patients in their care, bills follow the Billing Handler setting).
 */

const money = (v) =>
  v == null
    ? '—'
    : `₹${Number(v).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;

const ageOf = (dob) => {
  if (!dob) return null;
  const d = new Date(dob);
  if (Number.isNaN(d.getTime())) return null;
  const now = new Date();
  let a = now.getFullYear() - d.getFullYear();
  if (
    now.getMonth() < d.getMonth() ||
    (now.getMonth() === d.getMonth() && now.getDate() < d.getDate())
  )
    a -= 1;
  return a;
};

const titleCase = (s) =>
  String(s || '')
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());

function Badge({ children, tone = 'gray' }) {
  const tones = {
    gray: 'bg-gray-100 text-gray-700 border-gray-200',
    green: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    amber: 'bg-amber-50 text-amber-700 border-amber-200',
    red: 'bg-red-50 text-red-700 border-red-200',
    sky: 'bg-sky-50 text-sky-700 border-sky-200',
  };
  return (
    <span
      className={`inline-block px-2 py-0.5 rounded-full border text-xs font-semibold ${tones[tone]}`}
    >
      {children}
    </span>
  );
}

const statusTone = (s) => {
  const v = String(s || '').toUpperCase();
  if (
    ['ADMITTED', 'ACTIVE', 'QUEUED', 'SCHEDULED', 'IN_PROGRESS', 'PENDING', 'PARTIAL'].includes(v)
  )
    return 'amber';
  if (['CANCELLED', 'EXPIRED'].includes(v)) return 'red';
  if (['PAID', 'COMPLETED', 'CONSULTED'].includes(v)) return 'green';
  return 'gray';
};

function Section({ title, count, children, empty }) {
  return (
    <section className="bg-white rounded-2xl border border-gray-200 overflow-hidden break-inside-avoid">
      <header className="px-5 py-3 border-b border-gray-100 flex items-center gap-2">
        <h2 className="text-base font-bold text-gray-900">{title}</h2>
        <span className="text-xs font-semibold text-gray-500">{count}</span>
      </header>
      {count === 0 ? <p className="px-5 py-4 text-sm text-gray-500">{empty}</p> : children}
    </section>
  );
}

function Table({ head, children }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm text-left">
        <thead className="bg-gray-50 text-gray-600">
          <tr>
            {head.map((h) => (
              <th key={h} className="px-4 py-2 font-semibold whitespace-nowrap">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">{children}</tbody>
      </table>
    </div>
  );
}

const linkBtn = 'text-sm font-semibold text-sky-700 hover:underline whitespace-nowrap print:hidden';

const TABS = [
  { id: 'all', label: 'Everything' },
  { id: 'opd', label: 'OPD visits', key: 'opdVisits' },
  { id: 'ipd', label: 'IPD stays', key: 'ipdStays' },
  { id: 'icu', label: 'ICU', key: 'icuStays' },
  { id: 'ot', label: 'Surgeries', key: 'surgeries' },
  { id: 'bills', label: 'Bills', key: 'bills', needs: 'billing' },
  { id: 'docs', label: 'Documents', key: 'documents', needs: 'clinical' },
  { id: 'timeline', label: 'Clinical timeline', needs: 'clinical' },
];

export default function PatientFile() {
  const { publicId } = useParams();
  const navigate = useNavigate();
  const { error: toastError } = useToast();
  const [file, setFile] = useState(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState('all');
  const [timeline, setTimeline] = useState(null);

  const load = useCallback(async () => {
    try {
      const res = await apiClient.get(`/hospital/patients/${encodeURIComponent(publicId)}/file`);
      setFile(res.data);
      setError('');
    } catch (e) {
      setError(extractApiError(e, 'Could not open this patient file'));
    }
  }, [publicId]);

  useEffect(() => {
    void load();
  }, [load]);
  useLiveRefresh(load);

  useEffect(() => {
    if (tab !== 'timeline' || timeline) return;
    apiClient
      .get(`/hospital/patients/${encodeURIComponent(publicId)}/timeline`)
      .then((r) => setTimeline([...(r.data || [])].reverse()))
      .catch((e) => {
        setTimeline([]);
        toastError(extractApiError(e, 'Could not load the clinical timeline'));
      });
  }, [tab, timeline, publicId, toastError]);

  const access = useMemo(() => file?.access || {}, [file]);
  const tabs = useMemo(() => TABS.filter((t) => !t.needs || access[t.needs]), [access]);
  // "Everything" shows only what exists; empty sections are listed in one line instead.
  const show = (id, count) => tab === id || (tab === 'all' && count > 0);

  const printCasePaper = async (opdId) => {
    const ok = await printPdf(`/hospital/opd/${opdId}/pdf`);
    if (!ok) toastError('Could not print the case paper');
  };
  const printReceipt = async (billId) => {
    const ok = await printPdf(`/hospital/billing/${billId}/pdf`);
    if (!ok) toastError('Could not print the receipt');
  };
  const openDocument = async (doc) => {
    try {
      const blob = await patientDocumentService.getContentBlob(doc.publicId);
      if (blob.type === 'application/pdf') await printBlob(blob);
      else window.open(URL.createObjectURL(blob), '_blank', 'noopener');
    } catch (e) {
      toastError(extractApiError(e, 'Could not open the document'));
    }
  };

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <button type="button" onClick={() => navigate(-1)} className={linkBtn}>
          ← Back
        </button>
        <div
          role="alert"
          className="mt-6 max-w-xl bg-white border border-red-200 rounded-2xl p-6 text-red-700"
        >
          {error}
        </div>
      </div>
    );
  }
  if (!file) {
    return <div className="min-h-screen bg-gray-50 p-6 text-gray-500">Opening patient file…</div>;
  }

  const emptyLabels = tabs
    .filter((t) => t.key && (file[t.key] || []).length === 0)
    .map((t) => t.label);
  const p = file.patient || {};
  const c = file.counts || {};
  const age = ageOf(p.dateOfBirth);
  const totals = file.billTotals || {};

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-6xl mx-auto p-4 sm:p-6 space-y-5">
        <div className="flex items-center justify-between print:hidden">
          <button type="button" onClick={() => navigate(-1)} className={linkBtn}>
            ← Back
          </button>
          <button
            type="button"
            onClick={() => window.print()}
            className="h-[38px] px-4 rounded-lg border border-gray-300 bg-white text-sm font-semibold text-gray-700 hover:bg-gray-50"
          >
            Print file
          </button>
        </div>

        {/* Who */}
        <div className="bg-white rounded-2xl border border-gray-200 p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
                Patient file
              </p>
              <h1 className="text-2xl font-bold text-gray-900">{p.name}</h1>
              <p className="text-sm text-gray-600 mt-1">
                {[p.customId, p.gender && titleCase(p.gender), age != null && `${age} yrs`, p.phone]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
              {p.address && <p className="text-sm text-gray-500">{p.address}</p>}
              <p className="text-xs text-gray-400 mt-1">
                Registered {formatDay(p.registeredAt)}
                {p.dateOfBirth ? ` · Born ${formatDay(p.dateOfBirth)}` : ''}
              </p>
            </div>
            {!p.isActive && <Badge tone="red">Deleted patient — records kept</Badge>}
          </div>
          {access.clinical && p.medicalHistory && (
            <p className="mt-3 text-sm text-gray-700">
              <span className="font-semibold">Medical history: </span>
              {p.medicalHistory}
            </p>
          )}
          <dl className="mt-4 grid grid-cols-2 sm:grid-cols-5 gap-3">
            {[
              ['OPD visits', c.opdVisits],
              ['IPD stays', c.ipdStays],
              ['ICU stays', c.icuStays],
              ['Surgeries', c.surgeries],
              ...(access.billing ? [['Balance due', money(totals.balance)]] : []),
            ].map(([k, v]) => (
              <div key={k} className="rounded-xl bg-gray-50 border border-gray-100 px-3 py-2">
                <dt className="text-xs text-gray-500">{k}</dt>
                <dd className="text-lg font-bold text-gray-900">{v ?? 0}</dd>
              </div>
            ))}
          </dl>
          {!access.clinical && (
            <p className="mt-3 text-xs text-gray-500">
              Clinical notes, diagnoses and documents are visible to doctors and nurses only.
            </p>
          )}
        </div>

        <div className="flex flex-wrap gap-2 print:hidden" role="tablist" aria-label="Sections">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => setTab(t.id)}
              className={`px-3 py-1.5 rounded-lg text-sm font-semibold border ${
                tab === t.id
                  ? 'bg-sky-700 text-white border-sky-700'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              {t.label}
              {t.key && (
                <span className="ml-1.5 text-xs opacity-75">{(file[t.key] || []).length}</span>
              )}
            </button>
          ))}
        </div>

        {tab === 'all' && emptyLabels.length > 0 && (
          <p className="text-sm text-gray-500">
            Nothing recorded yet for: {emptyLabels.join(', ')}.
          </p>
        )}

        {show('opd', file.opdVisits.length) && (
          <Section title="OPD visits" count={file.opdVisits.length} empty="No OPD visits.">
            <Table
              head={[
                'Date',
                'Case',
                'Doctor',
                'Type',
                ...(access.clinical ? ['Complaint'] : []),
                'Status',
                '',
              ]}
            >
              {file.opdVisits.map((o) => (
                <tr key={o.id}>
                  <td className="px-4 py-2 whitespace-nowrap">{formatWhen(o.date)}</td>
                  <td className="px-4 py-2 font-medium whitespace-nowrap">
                    {o.caseId || `#${o.id}`}
                  </td>
                  <td className="px-4 py-2">{o.doctorName || '—'}</td>
                  <td className="px-4 py-2">{o.visitType === 'FOLLOWUP' ? 'Follow-up' : 'New'}</td>
                  {access.clinical && <td className="px-4 py-2 max-w-xs">{o.problem || '—'}</td>}
                  <td className="px-4 py-2">
                    <Badge tone={statusTone(o.status)}>
                      {titleCase(o.status === 'IN_IPD' ? 'Moved to IPD' : o.status)}
                    </Badge>
                  </td>
                  <td className="px-4 py-2 text-right">
                    {o.status !== 'CANCELLED' && (
                      <button
                        type="button"
                        className={linkBtn}
                        onClick={() => printCasePaper(o.id)}
                      >
                        Print case paper
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
          </Section>
        )}

        {show('ipd', file.ipdStays.length) && (
          <Section title="IPD stays" count={file.ipdStays.length} empty="Never admitted.">
            <Table
              head={[
                'IPD No.',
                'Admitted',
                'Discharged',
                'Days',
                'Ward / Bed',
                'Doctor',
                'Status',
                '',
              ]}
            >
              {file.ipdStays.map((a) => (
                <tr key={a.id}>
                  <td className="px-4 py-2 font-medium whitespace-nowrap">{a.ipdNumber}</td>
                  <td className="px-4 py-2 whitespace-nowrap">{formatWhen(a.admittedAt)}</td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {a.dischargedAt ? formatWhen(a.dischargedAt) : '—'}
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">{formatDays(a.days)}</td>
                  <td className="px-4 py-2">
                    {a.wardName || '—'}
                    {a.bedCode && <div className="text-xs text-gray-500">Bed {a.bedCode}</div>}
                  </td>
                  <td className="px-4 py-2">{a.doctorName || '—'}</td>
                  <td className="px-4 py-2">
                    <Badge tone={statusTone(a.status)}>{titleCase(a.status)}</Badge>
                    {a.hasDischargeSummary && (
                      <div className="text-xs text-gray-500 mt-0.5">Discharge summary on file</div>
                    )}
                  </td>
                  <td className="px-4 py-2 text-right">
                    <button
                      type="button"
                      className={linkBtn}
                      onClick={() => navigate(`/ipd/${a.id}`)}
                    >
                      Open case
                    </button>
                  </td>
                </tr>
              ))}
            </Table>
          </Section>
        )}

        {show('icu', file.icuStays.length) && (
          <Section title="ICU stays" count={file.icuStays.length} empty="No ICU stays.">
            <Table
              head={[
                'Unit',
                'In ICU',
                'Out of ICU',
                'Days',
                'Outcome',
                'Intensivist',
                ...(access.clinical ? ['Reason'] : []),
              ]}
            >
              {file.icuStays.map((s) => (
                <tr key={s.publicId}>
                  <td className="px-4 py-2 font-medium">{s.unitName || '—'}</td>
                  <td className="px-4 py-2 whitespace-nowrap">{formatWhen(s.admittedAt)}</td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {s.dischargedAt ? formatWhen(s.dischargedAt) : 'Still in ICU'}
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">{formatDays(s.days)}</td>
                  <td className="px-4 py-2">{s.disposition ? titleCase(s.disposition) : '—'}</td>
                  <td className="px-4 py-2">{s.intensivistName || '—'}</td>
                  {access.clinical && (
                    <td className="px-4 py-2 max-w-xs">{s.admissionReason || '—'}</td>
                  )}
                </tr>
              ))}
            </Table>
          </Section>
        )}

        {show('ot', file.surgeries.length) && (
          <Section title="Surgeries" count={file.surgeries.length} empty="No surgeries.">
            <Table head={['Procedure', 'Date', 'Surgeon', 'Anaesthetist', 'Priority', 'Status']}>
              {file.surgeries.map((s) => (
                <tr key={s.publicId}>
                  <td className="px-4 py-2 font-medium">{s.procedureName}</td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    {formatWhen(s.scheduledAt || s.requestedAt)}
                  </td>
                  <td className="px-4 py-2">{s.surgeonName || '—'}</td>
                  <td className="px-4 py-2">{s.anaesthetistName || '—'}</td>
                  <td className="px-4 py-2">{titleCase(s.priority)}</td>
                  <td className="px-4 py-2">
                    <Badge tone={statusTone(s.status)}>{titleCase(s.status)}</Badge>
                  </td>
                </tr>
              ))}
            </Table>
          </Section>
        )}

        {access.billing && show('bills', file.bills.length) && (
          <Section title="Bills" count={file.bills.length} empty="No bills.">
            <Table head={['Date', 'Bill', 'Category', 'Total', 'Paid', 'Balance', 'Status', '']}>
              {file.bills.map((b) => (
                <tr key={b.id} className={b.status === 'CANCELLED' ? 'text-gray-400' : ''}>
                  <td className="px-4 py-2 whitespace-nowrap">{formatWhen(b.date)}</td>
                  <td className="px-4 py-2 font-medium whitespace-nowrap">
                    {b.customId || `#${b.id}`}
                  </td>
                  <td className="px-4 py-2">{b.category}</td>
                  <td className="px-4 py-2">{money(b.totalAmount)}</td>
                  <td className="px-4 py-2">{money(b.paidAmount)}</td>
                  <td className="px-4 py-2">{money(b.balance)}</td>
                  <td className="px-4 py-2">
                    <Badge tone={statusTone(b.status)}>{titleCase(b.status)}</Badge>
                  </td>
                  <td className="px-4 py-2 text-right">
                    <button type="button" className={linkBtn} onClick={() => printReceipt(b.id)}>
                      Print
                    </button>
                  </td>
                </tr>
              ))}
              <tr className="bg-gray-50 font-semibold">
                <td className="px-4 py-2" colSpan={3}>
                  Total (cancelled bills not counted)
                </td>
                <td className="px-4 py-2">{money(totals.totalAmount)}</td>
                <td className="px-4 py-2">{money(totals.paidAmount)}</td>
                <td className="px-4 py-2">{money(totals.balance)}</td>
                <td colSpan={2} />
              </tr>
            </Table>
          </Section>
        )}

        {access.clinical && show('docs', file.documents.length) && (
          <Section title="Documents" count={file.documents.length} empty="No documents uploaded.">
            <Table head={['Date', 'Title', 'Type', '']}>
              {file.documents.map((d) => (
                <tr key={d.publicId}>
                  <td className="px-4 py-2 whitespace-nowrap">{formatDay(d.reportDate)}</td>
                  <td className="px-4 py-2 font-medium">{d.title}</td>
                  <td className="px-4 py-2">{documentTypeLabel(d.documentType)}</td>
                  <td className="px-4 py-2 text-right">
                    <button type="button" className={linkBtn} onClick={() => openDocument(d)}>
                      Open
                    </button>
                  </td>
                </tr>
              ))}
            </Table>
          </Section>
        )}

        {access.clinical && tab === 'timeline' && (
          <Section
            title="Clinical timeline"
            count={timeline ? timeline.length : '…'}
            empty="Nothing recorded yet."
          >
            {!timeline ? (
              <p className="px-5 py-4 text-sm text-gray-500">Loading…</p>
            ) : (
              <ol className="divide-y divide-gray-100">
                {timeline.map((e, i) => (
                  <li
                    key={`${e.eventType}-${e.sourceId}-${i}`}
                    className="px-5 py-2.5 flex gap-4 text-sm"
                  >
                    <span className="w-40 shrink-0 text-gray-500 whitespace-nowrap">
                      {formatWhen(e.timestamp)}
                    </span>
                    <span className="flex-1">
                      <span className="font-semibold text-gray-900">{titleCase(e.eventType)}</span>
                      {e.summary && <span className="text-gray-700"> — {e.summary}</span>}
                      {e.performedByName && (
                        <span className="block text-xs text-gray-500">
                          {e.performedByName}
                          {e.performedByRole ? ` (${titleCase(e.performedByRole)})` : ''}
                        </span>
                      )}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </Section>
        )}
      </div>
    </div>
  );
}
