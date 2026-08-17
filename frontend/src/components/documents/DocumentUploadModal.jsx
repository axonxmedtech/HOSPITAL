import React, { useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import patientDocumentService, {
  DOCUMENT_TYPES,
  MAX_FILE_SIZE_BYTES,
  MAX_FILE_SIZE_MB,
  extractErrorMessage,
  formatFileSize,
} from '../../services/patientDocumentService';
import CameraCapture from './CameraCapture';
import DocumentDropzone from './DocumentDropzone';

/**
 * The Camera / Browse chooser and the metadata form for attaching a patient record.
 *
 * The size limit is stated before a file is even picked, and re-checked the moment one is chosen —
 * discovering it after waiting through an upload on a clinic connection is the worst possible time
 * to learn it.
 */
const DocumentUploadModal = ({ patientPublicId, onClose, onUploaded }) => {
  // 'select' — choose Camera or Browse; 'camera' — live capture; 'details' — metadata form.
  const [mode, setMode] = useState('select');
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState(null);
  const [title, setTitle] = useState('');
  const [documentType, setDocumentType] = useState(DOCUMENT_TYPES[0].value);
  const [documentDate, setDocumentDate] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const toast = useToast();

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && !isUploading) onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose, isUploading]);

  const acceptFile = (chosen) => {
    if (chosen.size > MAX_FILE_SIZE_BYTES) {
      // Refused locally rather than uploaded just to be rejected — on a clinic connection that
      // wastes a minute and looks like a fault, not a size problem.
      setFileError(
        `This file is ${formatFileSize(chosen.size)}. The limit is ${MAX_FILE_SIZE_MB} MB.`
      );
      return;
    }
    setFileError(null);
    setFile(chosen);
    setTitle((prev) => prev || chosen.name.replace(/\.[^.]+$/, ''));
    setMode('details');
  };

  const changeFile = () => {
    setFile(null);
    setFileError(null);
    setMode('select');
  };

  const handleSave = async () => {
    if (!file || !title.trim() || isUploading) return;
    setIsUploading(true);
    try {
      const saved = await patientDocumentService.upload(patientPublicId, {
        file,
        title: title.trim(),
        documentType,
        documentDate: documentDate || undefined,
      });
      toast.success('Record attached.');
      onUploaded(saved);
    } catch (err) {
      toast.error(await extractErrorMessage(err, 'Failed to attach this record.'));
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4"
      onClick={(e) => {
        if (!isUploading && e.target === e.currentTarget) onClose();
      }}
      role="presentation"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="document-upload-title"
        className="bg-white rounded-xl shadow-2xl w-full max-w-lg max-h-[90vh] flex flex-col overflow-hidden"
      >
        <div className="flex justify-between items-center p-5 border-b border-gray-200">
          <h3 id="document-upload-title" className="text-lg font-bold text-gray-900">
            Attach a Record
          </h3>
          <button
            type="button"
            onClick={onClose}
            disabled={isUploading}
            aria-label="Close"
            className="text-gray-400 hover:text-gray-600 transition-colors disabled:opacity-50"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>

        <div className="p-5 overflow-y-auto space-y-4">
          <p className="text-xs text-gray-500">
            PDF or image (JPG, PNG, WEBP, HEIC), up to {MAX_FILE_SIZE_MB} MB.
          </p>

          {mode === 'select' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <button
                type="button"
                onClick={() => setMode('camera')}
                className="flex flex-col items-center justify-center gap-2 border-2 border-dashed border-gray-300 rounded-lg p-6 text-center hover:border-gray-900 hover:bg-gray-50 transition-colors"
              >
                <svg
                  className="w-8 h-8 text-gray-400"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"
                  />
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M12 17a4 4 0 100-8 4 4 0 000 8z"
                  />
                </svg>
                <span className="text-sm font-semibold text-gray-800">Camera</span>
              </button>

              <DocumentDropzone onFile={acceptFile} />
            </div>
          )}

          {fileError && mode === 'select' && (
            <p className="text-sm text-red-600" role="alert">
              {fileError}
            </p>
          )}

          {mode === 'camera' && (
            <CameraCapture onCapture={acceptFile} onCancel={() => setMode('select')} />
          )}

          {mode === 'details' && file && (
            <div className="space-y-4">
              <div className="flex items-center justify-between bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-800 truncate">{file.name}</p>
                  <p className="text-xs text-gray-500">{formatFileSize(file.size)}</p>
                </div>
                <button
                  type="button"
                  onClick={changeFile}
                  className="text-xs font-semibold text-gray-700 underline hover:no-underline shrink-0 ml-3"
                >
                  Change file
                </button>
              </div>

              <div>
                <label htmlFor="doc-title" className="block text-sm font-medium text-gray-700 mb-1">
                  Title <span className="text-red-500">*</span>
                </label>
                <input
                  id="doc-title"
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g. CBC Report"
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-gray-900 focus:border-gray-900 text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label
                    htmlFor="doc-type"
                    className="block text-sm font-medium text-gray-700 mb-1"
                  >
                    Type
                  </label>
                  <select
                    id="doc-type"
                    value={documentType}
                    onChange={(e) => setDocumentType(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-gray-900 focus:border-gray-900 text-sm"
                  >
                    {DOCUMENT_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label
                    htmlFor="doc-date"
                    className="block text-sm font-medium text-gray-700 mb-1"
                  >
                    Report Date
                  </label>
                  <input
                    id="doc-date"
                    type="date"
                    value={documentDate}
                    onChange={(e) => setDocumentDate(e.target.value)}
                    max={new Date().toISOString().slice(0, 10)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-gray-900 focus:border-gray-900 text-sm"
                  />
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-3 p-5 border-t border-gray-200 bg-gray-50">
          <button
            type="button"
            onClick={onClose}
            disabled={isUploading}
            className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          {mode === 'details' && (
            <button
              type="button"
              onClick={handleSave}
              disabled={isUploading || !title.trim()}
              className="px-4 py-2 text-white bg-gray-900 rounded-lg hover:bg-gray-800 transition-colors disabled:opacity-50 flex items-center gap-2"
            >
              {isUploading && (
                <svg
                  className="animate-spin h-4 w-4 text-white"
                  fill="none"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
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
              )}
              {isUploading ? 'Saving...' : 'Save'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default DocumentUploadModal;
