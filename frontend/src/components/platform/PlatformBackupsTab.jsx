import React, { useState, useEffect, useCallback } from 'react';
import { useToast } from '../../context/ToastContext';
import platformService from '../../services/platformService';
import { extractApiError } from '../../utils/apiError';

function formatBytes(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

function formatDate(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return d.toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoString;
  }
}

const SecurityNotice = () => (
  <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-6 flex items-start gap-3">
    <div className="p-2 bg-blue-100 rounded-lg text-blue-700 shrink-0">
      <svg
        className="w-5 h-5"
        aria-hidden="true"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
        />
      </svg>
    </div>
    <div>
      <h4 className="text-sm font-bold text-blue-900">End-to-End Encrypted Backups</h4>
      <p className="text-xs text-blue-800 mt-1 leading-relaxed">
        Files are locked with your Strong Master Key (AES-256). Save each downloaded backup to your
        laptop <strong className="font-semibold">and</strong> an external drive. Without your key,
        no backup can be opened.
      </p>
    </div>
  </div>
);

const BackupsTable = ({ backups, loading, downloadingFile, onDownload }) => {
  if (loading) {
    return (
      <div className="py-12 flex justify-center items-center text-gray-500 text-sm">
        <svg
          className="animate-spin h-5 w-5 mr-3 text-gray-600"
          aria-hidden="true"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
        Loading backups...
      </div>
    );
  }

  if (backups.length === 0) {
    return (
      <div className="py-12 text-center text-gray-500 text-sm border border-dashed border-gray-200 rounded-lg">
        <svg
          className="w-10 h-10 mx-auto text-gray-400 mb-2"
          aria-hidden="true"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M4 7v10c0 2 1 3 3 3h10c2 0 3-1 3-3V7M4 7c0-2 1-3 3-3h10c2 0 3 1 3 3M4 7h16"
          />
        </svg>
        No backups found on this server yet.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm border-collapse">
        <thead>
          <tr className="border-b border-gray-200 bg-gray-50 text-xs font-semibold text-gray-600 uppercase tracking-wider">
            <th className="py-3 px-4">Backup Date</th>
            <th className="py-3 px-4">Filename</th>
            <th className="py-3 px-4">Size</th>
            <th className="py-3 px-4">Integrity</th>
            <th className="py-3 px-4">Last Downloaded</th>
            <th className="py-3 px-4 text-right">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {backups.map((b) => (
            <tr key={b.fileName} className="hover:bg-gray-50/75 transition-colors">
              <td className="py-3.5 px-4 font-medium text-gray-900 whitespace-nowrap">
                {formatDate(b.createdAt)}
              </td>
              <td
                className="py-3.5 px-4 font-mono text-xs text-gray-600 max-w-xs truncate"
                title={b.fileName}
              >
                {b.fileName}
              </td>
              <td className="py-3.5 px-4 text-gray-600 whitespace-nowrap">
                {formatBytes(b.sizeBytes)}
              </td>
              <td className="py-3.5 px-4 whitespace-nowrap">
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                  Checked OK
                </span>
              </td>
              <td className="py-3.5 px-4 text-xs whitespace-nowrap">
                {b.lastDownloadedAt ? (
                  <span className="text-gray-700">
                    {formatDate(b.lastDownloadedAt)}
                    <span className="text-gray-400 block text-[11px]">{b.lastDownloadedBy}</span>
                  </span>
                ) : (
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200">
                    Never downloaded
                  </span>
                )}
              </td>
              <td className="py-3.5 px-4 text-right whitespace-nowrap">
                <button
                  type="button"
                  onClick={() => onDownload(b.fileName)}
                  disabled={downloadingFile === b.fileName}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-neutral-900 hover:bg-neutral-800 text-white rounded-lg text-xs font-semibold shadow-sm transition-colors disabled:opacity-50"
                >
                  {downloadingFile === b.fileName ? (
                    <>
                      <svg
                        className="animate-spin h-3.5 w-3.5 text-white"
                        aria-hidden="true"
                        fill="none"
                        viewBox="0 0 24 24"
                      >
                        <circle
                          className="opacity-25"
                          cx="12"
                          cy="12"
                          r="10"
                          stroke="currentColor"
                          strokeWidth="4"
                        />
                        <path
                          className="opacity-75"
                          fill="currentColor"
                          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                        />
                      </svg>
                      Downloading...
                    </>
                  ) : (
                    <>
                      <svg
                        className="w-3.5 h-3.5"
                        aria-hidden="true"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
                        />
                      </svg>
                      Download
                    </>
                  )}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const DisasterRecoveryCard = ({
  backups,
  onUploadSuccess,
  selectedFile,
  setSelectedFile,
  backupKey,
  setBackupKey,
  onValidate,
  validating,
  validationResult,
  onRestore,
  restoring,
}) => {
  const [uploading, setUploading] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const { success, error } = useToast();

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!uploadFile) return;
    setUploading(true);
    try {
      const res = await platformService.uploadBackup(uploadFile);
      success(res.message || 'Backup file uploaded successfully.');
      setUploadFile(null);
      if (res.fileName) setSelectedFile(res.fileName);
      onUploadSuccess();
    } catch (err) {
      error(extractApiError(err, 'Failed to upload backup file.'));
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="mt-8 bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
      <div className="flex items-center gap-2 mb-4">
        <div className="p-2 bg-red-100 rounded-lg text-red-700">
          <svg
            className="w-5 h-5"
            aria-hidden="true"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
        </div>
        <div>
          <h3 className="text-base font-bold text-gray-900">
            Disaster Recovery (Upload & Restore)
          </h3>
          <p className="text-xs text-gray-500">
            If the server was wiped, upload your offline backup and restore hospital records.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Upload form */}
        <form
          onSubmit={handleUpload}
          className="p-4 border border-dashed border-gray-300 rounded-lg bg-gray-50 flex flex-col justify-between"
        >
          <div>
            <label
              htmlFor="upload-backup-file"
              className="block text-xs font-semibold text-gray-700 uppercase mb-2"
            >
              Upload Backup from Device
            </label>
            <input
              id="upload-backup-file"
              type="file"
              accept=".enc,.sql.gz.enc"
              onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
              className="text-xs text-gray-600 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-neutral-900 file:text-white hover:file:bg-neutral-800 cursor-pointer w-full"
            />
            <p className="text-[11px] text-gray-500 mt-2">
              Only encrypted .sql.gz.enc archives are accepted.
            </p>
          </div>
          <button
            type="submit"
            disabled={!uploadFile || uploading}
            className="mt-4 px-4 py-2 bg-neutral-900 text-white rounded-lg text-xs font-semibold hover:bg-neutral-800 disabled:opacity-50 transition-colors"
          >
            {uploading ? 'Uploading...' : 'Upload to Server'}
          </button>
        </form>

        {/* Validation & Restore form */}
        <div className="space-y-4">
          <div>
            <label
              htmlFor="select-backup-archive"
              className="block text-xs font-semibold text-gray-700 uppercase mb-1"
            >
              Select Backup Archive
            </label>
            <select
              id="select-backup-archive"
              value={selectedFile}
              onChange={(e) => setSelectedFile(e.target.value)}
              className="w-full text-xs border border-gray-300 rounded-lg px-3 py-2 bg-white text-gray-900 focus:ring-2 focus:ring-neutral-900"
            >
              <option value="">-- Choose Backup File --</option>
              {backups.map((b) => (
                <option key={b.fileName} value={b.fileName}>
                  {b.fileName} ({formatBytes(b.sizeBytes)})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label
              htmlFor="backup-master-key"
              className="block text-xs font-semibold text-gray-700 uppercase mb-1"
            >
              Strong Master Key
            </label>
            <input
              id="backup-master-key"
              type="password"
              placeholder="Enter encryption secret key"
              value={backupKey}
              onChange={(e) => setBackupKey(e.target.value)}
              className="w-full text-xs border border-gray-300 rounded-lg px-3 py-2 bg-white text-gray-900 focus:ring-2 focus:ring-neutral-900"
            />
          </div>

          <div className="flex gap-2">
            <button
              type="button"
              onClick={onValidate}
              disabled={!selectedFile || !backupKey || validating}
              className="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold disabled:opacity-50 transition-colors"
            >
              {validating ? 'Validating in Scratch DB...' : '1. Test in Scratch DB'}
            </button>
            <button
              type="button"
              onClick={onRestore}
              disabled={!validationResult?.valid || restoring}
              className="flex-1 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold disabled:opacity-50 transition-colors"
            >
              {restoring ? 'Restoring Live...' : '2. Restore to Live DB'}
            </button>
          </div>

          {validationResult && (
            <div
              className={`p-3 rounded-lg text-xs border ${validationResult.valid ? 'bg-green-50 text-green-800 border-green-200' : 'bg-red-50 text-red-800 border-red-200'}`}
            >
              <p className="font-semibold">{validationResult.message}</p>
              {validationResult.valid && (
                <p className="mt-1 text-[11px]">
                  Table count: {validationResult.tableCount}. Core tables verified restorable.
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default function PlatformBackupsTab() {
  const [backups, setBackups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [downloadingFile, setDownloadingFile] = useState(null);

  const [selectedFile, setSelectedFile] = useState('');
  const [backupKey, setBackupKey] = useState('');
  const [validating, setValidating] = useState(false);
  const [validationResult, setValidationResult] = useState(null);
  const [restoring, setRestoring] = useState(false);

  const { success, error } = useToast();

  const loadBackups = useCallback(async () => {
    setLoading(true);
    try {
      const data = await platformService.getBackups();
      setBackups(data.backups || []);
    } catch (err) {
      error(extractApiError(err, 'Failed to load backup list.'));
    } finally {
      setLoading(false);
    }
  }, [error]);

  useEffect(() => {
    void loadBackups();
  }, [loadBackups]);

  const handleDownload = async (fileName) => {
    setDownloadingFile(fileName);
    try {
      const blob = await platformService.downloadBackup(fileName);
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', fileName);
      document.body.appendChild(link);
      link.click();
      link.parentNode?.removeChild(link);
      window.URL.revokeObjectURL(url);
      success(`Backup ${fileName} downloaded successfully.`);
      void loadBackups();
    } catch (err) {
      if (err?.response?.status === 429) {
        error('Download limit reached: maximum 5 downloads per hour.');
      } else {
        error(extractApiError(err, 'Failed to download backup archive.'));
      }
    } finally {
      setDownloadingFile(null);
    }
  };

  const handleValidate = async () => {
    if (!selectedFile || !backupKey) return;
    setValidating(true);
    setValidationResult(null);
    try {
      const res = await platformService.validateBackupRestore(selectedFile, backupKey);
      setValidationResult(res);
      if (res.valid) {
        success('Backup verified successfully in scratch database.');
      } else {
        error(res.message || 'Validation failed.');
      }
    } catch (err) {
      error(extractApiError(err, 'Failed to validate backup.'));
    } finally {
      setValidating(false);
    }
  };

  const handleRestore = async () => {
    const confirmation = window.prompt(
      'EMERGENCY ACTION: This will overwrite live data. Type RESTORE-LIVE-DATABASE to confirm:'
    );
    if (confirmation !== 'RESTORE-LIVE-DATABASE') {
      if (confirmation) error('Confirmation mismatch. Restore cancelled.');
      return;
    }

    setRestoring(true);
    try {
      const res = await platformService.executeBackupRestore(selectedFile, backupKey, confirmation);
      success(res.message || 'Live database restore completed successfully.');
      setValidationResult(null);
      void loadBackups();
    } catch (err) {
      error(extractApiError(err, 'Live restore failed.'));
    } finally {
      setRestoring(false);
    }
  };

  return (
    <div className="p-6 max-w-7xl mx-auto animate-fadeIn">
      <SecurityNotice />

      <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-bold text-gray-900">Encrypted Backups on Server</h3>
            <p className="text-xs text-gray-500">
              Nightly verified database dumps retained for 30 days.
            </p>
          </div>
          <button
            type="button"
            onClick={loadBackups}
            disabled={loading}
            className="px-3 py-1.5 border border-gray-300 rounded-lg text-xs font-semibold text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Refresh List
          </button>
        </div>

        <BackupsTable
          backups={backups}
          loading={loading}
          downloadingFile={downloadingFile}
          onDownload={handleDownload}
        />
      </div>

      <DisasterRecoveryCard
        backups={backups}
        onUploadSuccess={loadBackups}
        selectedFile={selectedFile}
        setSelectedFile={setSelectedFile}
        backupKey={backupKey}
        setBackupKey={setBackupKey}
        onValidate={handleValidate}
        validating={validating}
        validationResult={validationResult}
        onRestore={handleRestore}
        restoring={restoring}
      />
    </div>
  );
}
