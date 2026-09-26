import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import ConfirmationModal from '../../components/ConfirmationModal';
import PageHeader from '../../components/PageHeader';
import { useToast } from '../../context/ToastContext';
import authService from '../../services/authService';
import {
  canImportPatients,
  commitPatientImport,
  errorBatchId,
  getPatientImportStatus,
  importErrorMessage,
  previewPatientImport,
} from '../../services/patientImportService';
import {
  buildImportChoices,
  EXCLUDE,
  fileError,
  IMPORT_FIELDS,
  suggestMapping,
  validateHeaders,
} from './patient-import/importFields';
import ImportSummary from './patient-import/ImportSummary';
import { readImportHeaders } from './patient-import/readImportHeaders';

const button =
  'px-4 py-2 rounded-lg border border-gray-300 bg-white text-sm font-medium hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed';
const primary = `${button} !bg-sky-600 !border-sky-600 text-white hover:!bg-sky-700`;
const card = 'bg-white rounded-xl border border-gray-200 p-5 md:p-6 space-y-5';

export default function PatientImportPage() {
  const user = authService.getCurrentUser();
  if (!user || !authService.isAuthenticated())
    return <Navigate to={authService.getLoginUrl()} replace />;
  if (!canImportPatients(user)) return <Navigate to="/" replace />;
  return <PatientImportWorkflow />;
}

function PatientImportWorkflow() {
  const [params, setParams] = useSearchParams();
  const batchId = params.get('batch');
  const [file, setFile] = useState(null);
  const [sheets, setSheets] = useState([]);
  const [sheetIndex, setSheetIndex] = useState(0);
  const [headers, setHeaders] = useState([]);
  const [choices, setChoices] = useState([]);
  const [preview, setPreview] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [attempted, setAttempted] = useState(false);
  const [recoverId, setRecoverId] = useState(null);
  const lock = useRef(false);
  const mounted = useRef(false);
  const reader = useRef(null);
  const generation = useRef(0);
  const input = useRef(null);
  const toast = useToast();

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      generation.current += 1;
      reader.current?.abort();
    };
  }, []);
  useEffect(() => {
    if (busy !== 'commit') return;
    const warn = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [busy]);

  const loadStatus = useCallback(async (id, signal) => {
    setBusy('status');
    setError('');
    try {
      const data = await getPatientImportStatus(id);
      if (!signal?.aborted) setResult(data);
    } catch (err) {
      if (!signal?.aborted) setError(importErrorMessage(err));
    } finally {
      if (!signal?.aborted) setBusy('');
    }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    if (batchId) loadStatus(batchId, controller.signal);
    return () => controller.abort();
  }, [batchId, loadStatus]);

  const selectSheet = (allSheets, index) => {
    setSheetIndex(index);
    setPreview(null);
    setAttempted(false);
    setRecoverId(null);
    setError('');
    try {
      const next = validateHeaders(allSheets[index]?.headers || []);
      setHeaders(next);
      setChoices(suggestMapping(next));
    } catch (err) {
      setHeaders([]);
      setChoices([]);
      setError(err.message);
    }
  };
  const selectFile = async (next) => {
    if (lock.current) return;
    reader.current?.abort();
    const current = ++generation.current;
    setPreview(null);
    setResult(null);
    setHeaders([]);
    setChoices([]);
    setSheets([]);
    setAttempted(false);
    setRecoverId(null);
    setFile(null);
    setBusy('');
    setError('');
    const problem = fileError(next);
    if (problem) {
      setError(problem);
      return;
    }
    setFile(next);
    setBusy('read');
    const controller = new AbortController();
    reader.current = controller;
    try {
      const nextSheets = await readImportHeaders(next, controller.signal);
      if (generation.current !== current) return;
      setSheets(nextSheets);
      selectSheet(nextSheets, 0);
    } catch (err) {
      if (generation.current === current && err.name !== 'AbortError') setError(err.message);
    } finally {
      if (generation.current === current) setBusy('');
    }
  };
  const reset = () => {
    if (lock.current) return;
    reader.current?.abort();
    generation.current += 1;
    setFile(null);
    setSheets([]);
    setHeaders([]);
    setChoices([]);
    setPreview(null);
    setResult(null);
    setError('');
    setAttempted(false);
    setRecoverId(null);
    setBusy('');
    setParams({}, { replace: true });
    if (input.current) input.current.value = '';
  };
  const request = () => ({
    file,
    ...buildImportChoices(headers, choices),
    sheetName: sheets[sheetIndex]?.name || undefined,
  });
  const runPreview = async () => {
    if (lock.current || busy || !file) return;
    let payload;
    try {
      payload = request();
    } catch (err) {
      setError(err.message);
      return;
    }
    lock.current = true;
    setBusy('preview');
    setError('');
    setPreview(null);
    setRecoverId(null);
    try {
      const data = await previewPatientImport(payload);
      setPreview(data);
      setAttempted(false);
    } catch (err) {
      setError(importErrorMessage(err));
      setRecoverId(errorBatchId(err));
    } finally {
      lock.current = false;
      setBusy('');
    }
  };
  const commit = async () => {
    if (lock.current || attempted || !preview || preview.previousImport) return;
    lock.current = true;
    setAttempted(true);
    setBusy('commit');
    setError('');
    try {
      const data = await commitPatientImport(request());
      // Leaving the page must not cancel the mutation or navigate on its completion.
      if (!mounted.current) return;
      setResult(data);
      setFile(null);
      setSheets([]);
      setHeaders([]);
      setChoices([]);
      setPreview(null);
      toast?.info('Import finished. Review the final counts below.');
      setParams({ batch: data.batchPublicId }, { replace: true });
    } catch (err) {
      if (!mounted.current) return;
      setError(importErrorMessage(err, true));
      setRecoverId(errorBatchId(err));
    } finally {
      lock.current = false;
      if (mounted.current) {
        setBusy('');
        setConfirm(false);
      }
    }
  };
  const isResult = Boolean(batchId || result);
  const selectedSheet = sheets[sheetIndex]?.name;
  const previousId = preview?.previousImport?.batchPublicId;

  return (
    <main className="min-h-screen bg-gray-50 px-4 py-6 md:px-8">
      <div className="mx-auto max-w-6xl space-y-6">
        {busy === 'commit' ? (
          <p className="text-sm text-slate-600">Import in progress — keep this page open.</p>
        ) : (
          <Link
            className="text-sm font-medium text-sky-700 hover:underline"
            to="/hospital/admin?tab=patients"
          >
            ← Back to Patients
          </Link>
        )}
        <PageHeader
          title="Import Patients"
          subtitle="Upload patient data exported from your previous hospital software."
        />
        <ol className="flex flex-wrap gap-3 text-sm text-slate-600" aria-label="Import steps">
          {['Upload file', 'Map columns', 'Preview & review', 'Confirm import', 'Results'].map(
            (step, i) => (
              <li key={step} className="rounded-full border border-gray-200 bg-white px-3 py-1">
                {i + 1}. {step}
              </li>
            )
          )}
        </ol>
        {error && (
          <div
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
          >
            {error}
          </div>
        )}
        {recoverId && (
          <button
            className={button}
            disabled={!!busy}
            onClick={() => setParams({ batch: recoverId })}
          >
            Check import status
          </button>
        )}
        {busy && (
          <p role="status" className="text-sm text-slate-600">
            {
              {
                read: 'Reading file headers…',
                preview: 'Validating every row. No patients are being changed…',
                commit: 'Importing patients. This may take several minutes. Do not submit again.',
                status: 'Checking import status…',
              }[busy]
            }
          </p>
        )}
        {isResult ? (
          <section className={card} aria-label="Import results">
            <h2 className="text-lg font-semibold text-slate-800">
              Import results{result ? ` — ${result.status}` : ''}
            </h2>
            {result && (
              <>
                <ImportSummary counts={result.counts} />
                {result.status === 'RUNNING' && (
                  <p className="text-sm text-slate-600">
                    This import is still running. Refresh the status to check progress.
                  </p>
                )}
                {result.status === 'FAILED' && (
                  <p className="text-sm text-red-700">
                    The import stopped. Rows already imported were kept. Review the counts before
                    considering another attempt.
                  </p>
                )}
                {result.status === 'PARTIAL' && (
                  <p className="text-sm text-amber-800">
                    Some rows were not imported. Successful changes were kept.
                  </p>
                )}
                {result.status === 'UNDONE' && (
                  <p className="text-sm text-slate-600">
                    This batch was undone. These are the recorded counts from the original import,
                    not the current patient state.
                  </p>
                )}
                <p className="text-sm text-slate-600">
                  Detailed row results and review resolution are not currently available here.
                  Review any remaining issues with your hospital administrator.
                </p>
              </>
            )}
            <div className="flex flex-wrap gap-3">
              {batchId && (
                <button className={button} disabled={!!busy} onClick={() => loadStatus(batchId)}>
                  Refresh status
                </button>
              )}
              <button
                className={button}
                disabled={!!busy || result?.status === 'RUNNING'}
                onClick={reset}
              >
                Import Another File
              </button>
            </div>
          </section>
        ) : (
          <>
            <section className={card} aria-label="Upload file">
              <h2 className="text-lg font-semibold text-slate-800">1. Upload file</h2>
              <div
                className="rounded-xl border-2 border-dashed border-gray-300 p-6 text-center"
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => {
                  event.preventDefault();
                  if (busy && busy !== 'read') return;
                  if (event.dataTransfer.files.length !== 1) setError('Choose one file at a time.');
                  else selectFile(event.dataTransfer.files[0]);
                }}
              >
                <label
                  className="block text-sm font-medium text-slate-700"
                  htmlFor="patient-import-file"
                >
                  Browse File or drop one CSV / XLSX file here
                </label>
                <input
                  ref={input}
                  id="patient-import-file"
                  type="file"
                  accept=".csv,.xlsx"
                  disabled={!!busy && busy !== 'read'}
                  className="mt-3 max-w-full text-sm"
                  onChange={(event) => {
                    const selected = event.target.files[0];
                    if (selected) selectFile(selected);
                    event.target.value = '';
                  }}
                />
                <p className="mt-3 text-sm text-slate-500">
                  Maximum 50 MiB. Use a single header row with unique column names.
                </p>
              </div>
              {file && (
                <div className="flex flex-wrap items-center gap-3 text-sm text-slate-700">
                  <span className="break-all">{file.name}</span>
                  <span>
                    {file.name.split('.').pop().toUpperCase()} ·{' '}
                    {(file.size / 1024 / 1024).toFixed(2)} MiB
                  </span>
                  <button className={button} disabled={!!busy && busy !== 'read'} onClick={reset}>
                    Remove file
                  </button>
                </div>
              )}
              <p className="text-sm text-slate-500">
                Files and patient rows are not saved in browser storage. Leaving or refreshing this
                page clears an unsubmitted file.
              </p>
            </section>
            {sheets.length > 0 && (
              <section className={card} aria-label="Map columns">
                <h2 className="text-lg font-semibold text-slate-800">2. Map columns</h2>
                {selectedSheet !== '' && (
                  <label className="block text-sm font-medium text-slate-700">
                    Worksheet
                    <select
                      className="ml-3 rounded-lg border border-gray-300 p-2"
                      value={sheetIndex}
                      disabled={!!busy}
                      onChange={(e) => selectSheet(sheets, Number(e.target.value))}
                    >
                      {sheets.map((s, i) => (
                        <option value={i} key={s.name}>
                          {s.name}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <p className="text-sm text-slate-600">
                  Check suggested fields. Choose <strong>Do not import</strong> to exclude a source
                  column from this upload. Exclusion will not erase previously imported information.
                </p>
                <p className="text-sm text-slate-600">
                  Full name mapping is required. New patients also need a usable phone number and
                  date of birth; the preview will identify missing or invalid values.
                </p>
                {headers.length > 0 && (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead className="bg-gray-50 text-slate-600">
                        <tr>
                          <th className="p-3">Source column</th>
                          <th className="p-3">AxonX field or exclusion</th>
                        </tr>
                      </thead>
                      <tbody>
                        {headers.map((header, index) => (
                          <tr className="border-b border-gray-100" key={header}>
                            <td className="p-3 break-all">{header}</td>
                            <td className="p-3">
                              <select
                                aria-label={`Map ${header}`}
                                className="w-full rounded-lg border border-gray-300 p-2"
                                value={choices[index]}
                                disabled={!!busy}
                                onChange={(e) => {
                                  setChoices((old) =>
                                    old.map((v, i) => (i === index ? e.target.value : v))
                                  );
                                  setPreview(null);
                                  setAttempted(false);
                                  setRecoverId(null);
                                  setError('');
                                }}
                              >
                                <option value="">Choose a field or exclusion</option>
                                <option value={EXCLUDE}>Do not import</option>
                                {IMPORT_FIELDS.map((field) => (
                                  <option
                                    key={field.key}
                                    value={field.key}
                                    disabled={
                                      choices.includes(field.key) && choices[index] !== field.key
                                    }
                                  >
                                    {field.label}
                                  </option>
                                ))}
                              </select>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <button
                  className={primary}
                  disabled={!!busy || !headers.length}
                  onClick={runPreview}
                >
                  Preview & validate
                </button>
              </section>
            )}
            {preview && (
              <section className={card} aria-label="Preview results">
                <h2 className="text-lg font-semibold text-slate-800">3. Preview & review</h2>
                <p className="text-sm text-slate-600">
                  Preview has not changed any patients. These are proposed outcomes; final results
                  can change if records change before import.
                </p>
                <ImportSummary counts={preview.counts} preview />
                {preview.previousImport && (
                  <div className="rounded-lg bg-amber-50 p-4 text-sm text-amber-900">
                    This original file already has an import ({preview.previousImport.status}).
                    Changing column choices does not allow importing it again.
                    {previousId && (
                      <button
                        className={`${button} ml-3`}
                        onClick={() => setParams({ batch: previousId })}
                      >
                        View existing import
                      </button>
                    )}
                  </div>
                )}
                <p className="text-sm text-slate-600">
                  Rows needing review or failing validation will not be applied. A shared phone does
                  not establish that two records are the same person. Correct the source file or
                  consult your hospital administrator; this screen cannot resolve these issues.
                </p>
                {!!preview.samples?.length && (
                  <div className="max-h-96 overflow-auto">
                    <table className="w-full text-left text-sm">
                      <caption className="p-2 text-left text-slate-600">
                        Row issues{' '}
                        {preview.samplesTruncated
                          ? '(sample only; additional issues are not shown)'
                          : ''}
                      </caption>
                      <thead className="bg-gray-50">
                        <tr>
                          {['Row', 'Outcome', 'Reason', 'Details', 'Phone (masked)'].map((h) => (
                            <th className="p-3" key={h}>
                              {h}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {preview.samples.map((sample, i) => (
                          <tr key={`${sample.rowNum}-${i}`} className="border-b border-gray-100">
                            <td className="p-3">{sample.rowNum}</td>
                            <td className="p-3">{sample.state.replaceAll('_', ' ')}</td>
                            <td className="p-3">{sample.reasonCode?.replaceAll('_', ' ')}</td>
                            <td className="p-3">{sample.message}</td>
                            <td className="p-3 whitespace-nowrap">{sample.phoneMasked || '—'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <button
                  className={primary}
                  disabled={
                    !!busy ||
                    attempted ||
                    !!preview.previousImport ||
                    !(preview.counts?.created + preview.counts?.updated)
                  }
                  onClick={() => setConfirm(true)}
                >
                  Confirm import…
                </button>
                {!(preview.counts?.created + preview.counts?.updated) && (
                  <p className="text-sm text-slate-600">
                    No patient changes are ready to import. Review the issues, correct the source
                    file if needed, and preview again.
                  </p>
                )}
                {attempted && (
                  <p className="text-sm text-amber-800">
                    This submission was attempted. Check its status or run a fresh preview before
                    another confirmation.
                  </p>
                )}
              </section>
            )}
          </>
        )}
        <ConfirmationModal
          isOpen={confirm}
          title="Import patient records?"
          message={`You are about to change patient records in AxonX HMS. Preview made no patient changes. Confirming imports the file: ${preview?.counts?.total ?? 0} total; ${preview?.counts?.created ?? 0} proposed new, ${preview?.counts?.updated ?? 0} proposed updates, ${preview?.counts?.skipped ?? 0} skipped, ${preview?.counts?.needsReview ?? 0} needing review, ${preview?.counts?.failed ?? 0} failed. Successful rows are kept even if other rows fail.`}
          onConfirm={commit}
          onCancel={() => {
            if (!lock.current) setConfirm(false);
          }}
        />
      </div>
    </main>
  );
}
