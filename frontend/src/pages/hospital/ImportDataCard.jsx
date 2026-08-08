import React, { useState, useEffect, useCallback } from 'react';
import { useToast } from '../../context/ToastContext';
import importService, { saveBlob } from '../../services/importService';

/**
 * ImportDataCard — brings a hospital's existing patient records in from their previous system.
 *
 * The flow is upload → map columns → preview → commit, and the preview step is the point of the
 * whole screen: it reports exactly what a commit would do while writing nothing at all. An admin
 * pointing this at eight thousand rows they have never seen should find out what will happen
 * before anything is saved, not after.
 *
 * Problems are shown before the counts, deliberately. "6,431 will import" is easy to skim past;
 * "43 rows will NOT import" is what actually needs a decision.
 */
const ImportDataCard = () => {
  const { success, error: toastError } = useToast();

  const [step, setStep] = useState('choose'); // choose | map | preview | done
  const [file, setFile] = useState(null);
  const [fields, setFields] = useState([]);
  const [parsed, setParsed] = useState(null); // headers, suggestedMapping, sampleRows, totalRows
  const [mapping, setMapping] = useState({});
  const [preview, setPreview] = useState(null);
  const [batch, setBatch] = useState(null);
  const [busy, setBusy] = useState(false);
  const [history, setHistory] = useState([]);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await importService.list());
    } catch {
      /* history is informational; a failure here must not block an import */
    }
  }, []);

  useEffect(() => {
    importService
      .fields()
      .then(setFields)
      .catch(() => toastError('Could not load the list of importable fields.'));
    loadHistory();
  }, [toastError, loadHistory]);

  const reset = () => {
    setStep('choose');
    setFile(null);
    setParsed(null);
    setMapping({});
    setPreview(null);
    setBatch(null);
  };

  const onFileChosen = async (e) => {
    const chosen = e.target.files?.[0];
    if (!chosen) return;
    setFile(chosen);
    setBusy(true);
    try {
      const result = await importService.upload(chosen);
      setParsed(result);
      setMapping(result.suggestedMapping || {});
      setStep('map');
    } catch (err) {
      toastError(err?.response?.data?.error || 'Could not read that file.');
      setFile(null);
    } finally {
      setBusy(false);
    }
  };

  const runPreview = async () => {
    setBusy(true);
    try {
      const result = await importService.preview(file, mapping);
      setPreview(result);
      setStep('preview');
    } catch (err) {
      toastError(err?.response?.data?.error || 'Could not preview this file.');
    } finally {
      setBusy(false);
    }
  };

  const runCommit = async () => {
    setBusy(true);
    try {
      const result = await importService.commit(file, mapping);
      setBatch(result);
      setStep('done');
      success('Import complete.');
      loadHistory();
    } catch (err) {
      toastError(err?.response?.data?.error || 'The import failed.');
    } finally {
      setBusy(false);
    }
  };

  const runUndo = async (publicId) => {
    setBusy(true);
    try {
      await importService.undo(publicId);
      success('Import reversed.');
      loadHistory();
      if (batch?.publicId === publicId) setBatch({ ...batch, status: 'UNDONE' });
    } catch (err) {
      // The server refuses when patients from the batch have clinical activity. That message
      // explains exactly which records are in the way, so surface it verbatim.
      toastError(err?.response?.data?.error || 'Could not reverse this import.');
    } finally {
      setBusy(false);
    }
  };

  const downloadErrors = async (publicId) => {
    try {
      const blob = await importService.downloadErrors(publicId, parsed?.headers || []);
      saveBlob(blob, 'import-errors.csv');
    } catch {
      toastError('Could not download the error report.');
    }
  };

  const mappedFieldFor = (header) => mapping[header] || '';
  const unmappedHeaders = (parsed?.headers || []).filter((h) => h && !mapping[h]);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 p-6">
      <div className="flex items-start justify-between mb-6">
        <div>
          <h3 className="text-lg font-bold text-gray-900">Import Data</h3>
          <p className="text-sm text-gray-600 mt-1">
            Bring patient records in from your previous system. Nothing is saved until you review
            the preview and confirm.
          </p>
        </div>
        {step !== 'choose' && (
          <button
            onClick={reset}
            className="text-sm font-medium text-gray-600 hover:text-gray-900 underline"
          >
            Start over
          </button>
        )}
      </div>

      {/* ── Step 1: choose a file ─────────────────────────────────────────── */}
      {step === 'choose' && (
        <div className="border-2 border-dashed border-gray-300 rounded-xl p-8 text-center">
          <p className="text-sm text-gray-700 mb-4">
            Choose an Excel (.xlsx) or CSV file exported from your old software.
          </p>
          <label className="inline-block px-5 py-2.5 bg-sky-600 text-white rounded-lg font-medium cursor-pointer hover:bg-sky-700 transition">
            {busy ? 'Reading file…' : 'Choose file'}
            <input
              type="file"
              accept=".xlsx,.csv"
              className="hidden"
              disabled={busy}
              onChange={onFileChosen}
            />
          </label>
          <p className="text-xs text-gray-500 mt-4">
            Up to 50 MB. Blank values stay blank — the importer never invents data.
          </p>
        </div>
      )}

      {/* ── Step 2: map columns ───────────────────────────────────────────── */}
      {step === 'map' && parsed && (
        <div>
          <div className="flex items-center justify-between mb-4">
            <p className="text-sm text-gray-700">
              <span className="font-semibold">{parsed.totalRows}</span> rows found in{' '}
              <span className="font-mono text-xs">{file?.name}</span>. Check each column is going to
              the right place.
            </p>
          </div>

          {!Object.values(mapping).includes('legacyId') && (
            <div className="mb-4 p-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-900">
              <strong>No old patient ID (MRN) column is mapped.</strong> Without one, re-uploading
              this file later cannot match the records it created and would add duplicates. Map it
              if your file has one.
            </div>
          )}

          <div className="border border-gray-200 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-700">
                <tr>
                  <th className="text-left px-4 py-2 font-semibold">Column in your file</th>
                  <th className="text-left px-4 py-2 font-semibold">Example value</th>
                  <th className="text-left px-4 py-2 font-semibold">Import as</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {(parsed.headers || [])
                  .filter((h) => h)
                  .map((header) => (
                    <tr key={header}>
                      <td className="px-4 py-2 font-medium text-gray-900">{header}</td>
                      <td className="px-4 py-2 text-gray-500 truncate max-w-[12rem]">
                        {parsed.sampleRows?.[0]?.[header] || <span className="italic">blank</span>}
                      </td>
                      <td className="px-4 py-2">
                        <select
                          value={mappedFieldFor(header)}
                          onChange={(e) =>
                            setMapping((m) => {
                              const next = { ...m };
                              if (e.target.value) next[header] = e.target.value;
                              else delete next[header];
                              return next;
                            })
                          }
                          className="w-full border border-gray-300 rounded-lg px-2 py-1.5 text-sm focus:ring-2 focus:ring-sky-500"
                        >
                          <option value="">— keep as extra information —</option>
                          {fields.map((f) => (
                            <option
                              key={f.key}
                              value={f.key}
                              disabled={
                                mappedFieldFor(header) !== f.key &&
                                Object.values(mapping).includes(f.key)
                              }
                            >
                              {f.label}
                              {f.required ? ' (required)' : ''}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>

          {unmappedHeaders.length > 0 && (
            <p className="text-xs text-gray-600 mt-3">
              {unmappedHeaders.length} column(s) will be kept on each patient as &ldquo;Imported
              information&rdquo; — nothing from your file is discarded:{' '}
              <span className="font-medium">{unmappedHeaders.join(', ')}</span>
            </p>
          )}

          <div className="flex justify-end gap-3 mt-6">
            <button
              onClick={runPreview}
              disabled={busy || !Object.values(mapping).includes('name')}
              className="px-5 py-2.5 bg-sky-600 text-white rounded-lg font-medium disabled:opacity-50 hover:bg-sky-700 transition"
            >
              {busy ? 'Checking…' : 'Preview import'}
            </button>
          </div>
          {!Object.values(mapping).includes('name') && (
            <p className="text-xs text-rose-600 text-right mt-2">
              A column must be mapped to Full name before you can continue.
            </p>
          )}
        </div>
      )}

      {/* ── Step 3: preview ───────────────────────────────────────────────── */}
      {step === 'preview' && preview && (
        <div>
          {/* Problems first. Counts are easy to skim past; refusals need a decision. */}
          {(preview.warnings || []).map((w) => (
            <div
              key={w}
              className="mb-3 p-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-900"
            >
              {w}
            </div>
          ))}

          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 my-5">
            <Stat label="Will be added" value={preview.createCount} tone="emerald" />
            <Stat label="Will be updated" value={preview.updateCount} tone="sky" />
            <Stat label="Skipped" value={preview.skipCount} tone="gray" />
            <Stat label="Problems" value={preview.errorCount} tone="rose" />
          </div>

          {preview.errorCount > 0 && (
            <div className="border border-rose-200 rounded-xl overflow-hidden mb-5">
              <div className="bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-900">
                Rows that will not be imported
              </div>
              <div className="max-h-64 overflow-y-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 text-gray-600 sticky top-0">
                    <tr>
                      <th className="text-left px-4 py-2 font-semibold w-20">Row</th>
                      <th className="text-left px-4 py-2 font-semibold w-40">Column</th>
                      <th className="text-left px-4 py-2 font-semibold">Problem</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {(preview.errors || []).map((err) => (
                      <tr key={`${err.rowNumber}-${err.columnName}-${err.message}`}>
                        <td className="px-4 py-2 font-mono text-gray-900">{err.rowNumber}</td>
                        <td className="px-4 py-2 text-gray-700">{err.columnName || '—'}</td>
                        <td className="px-4 py-2 text-gray-700">{err.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="px-4 py-2 text-xs text-gray-600 bg-gray-50">
                Row numbers match the rows in your spreadsheet. Fix them there and start over, or
                import now and correct them afterwards.
              </p>
            </div>
          )}

          {(preview.sampleNames || []).length > 0 && (
            <p className="text-xs text-gray-600 mb-5">
              First few patients that will be added:{' '}
              <span className="font-medium">{preview.sampleNames.join(', ')}</span>
            </p>
          )}

          <div className="flex justify-between items-center">
            <button
              onClick={() => setStep('map')}
              className="text-sm font-medium text-gray-600 hover:text-gray-900 underline"
            >
              Back to columns
            </button>
            <button
              onClick={runCommit}
              disabled={busy || preview.createCount + preview.updateCount === 0}
              className="px-5 py-2.5 bg-emerald-600 text-white rounded-lg font-medium disabled:opacity-50 hover:bg-emerald-700 transition"
            >
              {busy
                ? 'Importing…'
                : `Import ${preview.createCount + preview.updateCount} patient(s)`}
            </button>
          </div>
        </div>
      )}

      {/* ── Step 4: result ────────────────────────────────────────────────── */}
      {step === 'done' && batch && (
        <div>
          <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 mb-5">
            <p className="font-semibold text-emerald-900">Import finished</p>
            <p className="text-sm text-emerald-800 mt-1">
              {batch.createdCount} added · {batch.updatedCount} updated · {batch.skippedCount}{' '}
              skipped · {batch.failedCount} not imported
            </p>
          </div>

          <div className="flex flex-wrap gap-3">
            {batch.failedCount > 0 && (
              <button
                onClick={() => downloadErrors(batch.publicId)}
                className="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50"
              >
                Download the {batch.failedCount} failed row(s)
              </button>
            )}
            <button
              onClick={() => runUndo(batch.publicId)}
              disabled={busy || batch.status === 'UNDONE'}
              className="px-4 py-2 border border-rose-300 text-rose-700 rounded-lg text-sm font-medium hover:bg-rose-50 disabled:opacity-50"
            >
              {batch.status === 'UNDONE' ? 'Reversed' : 'Undo this import'}
            </button>
            <button
              onClick={reset}
              className="px-4 py-2 bg-sky-600 text-white rounded-lg text-sm font-medium hover:bg-sky-700"
            >
              Import another file
            </button>
          </div>

          <p className="text-xs text-gray-600 mt-4">
            Undo removes only the patients this import added, and is refused once any of them has a
            visit or a bill recorded against them.
          </p>
        </div>
      )}

      {/* ── Past imports ──────────────────────────────────────────────────── */}
      {history.length > 0 && step === 'choose' && (
        <div className="mt-8">
          <h4 className="text-sm font-semibold text-gray-900 mb-3">Past imports</h4>
          <div className="border border-gray-200 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-600">
                <tr>
                  <th className="text-left px-4 py-2 font-semibold">File</th>
                  <th className="text-left px-4 py-2 font-semibold">Result</th>
                  <th className="text-left px-4 py-2 font-semibold">Status</th>
                  <th className="px-4 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {history.slice(0, 5).map((b) => (
                  <tr key={b.publicId}>
                    <td className="px-4 py-2 text-gray-900 truncate max-w-[14rem]">
                      {b.sourceFilename || '—'}
                    </td>
                    <td className="px-4 py-2 text-gray-700">
                      {b.createdCount} added, {b.failedCount} failed
                    </td>
                    <td className="px-4 py-2 text-gray-700">{b.status}</td>
                    <td className="px-4 py-2 text-right">
                      {b.status !== 'UNDONE' && (
                        <button
                          onClick={() => runUndo(b.publicId)}
                          disabled={busy}
                          className="text-xs font-medium text-rose-700 hover:underline disabled:opacity-50"
                        >
                          Undo
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

const Stat = ({ label, value, tone }) => {
  const tones = {
    emerald: 'bg-emerald-50 border-emerald-200 text-emerald-900',
    sky: 'bg-sky-50 border-sky-200 text-sky-900',
    gray: 'bg-gray-50 border-gray-200 text-gray-800',
    rose: 'bg-rose-50 border-rose-200 text-rose-900',
  };
  return (
    <div className={`rounded-xl border p-3 ${tones[tone]}`}>
      <div className="text-2xl font-bold">{value ?? 0}</div>
      <div className="text-xs font-medium mt-0.5">{label}</div>
    </div>
  );
};

export default ImportDataCard;
