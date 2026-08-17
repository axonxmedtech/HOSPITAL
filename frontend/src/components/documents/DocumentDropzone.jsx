import React, { useRef, useState } from 'react';

/**
 * Drag-and-drop plus a file picker for attaching a patient record.
 *
 * `dragover` and `drop` both need `preventDefault()` — without it the browser's default handling
 * takes over and navigates the tab to the dropped file, losing the upload dialog entirely.
 */
const DocumentDropzone = ({ onFile, accept = '.pdf,.jpg,.jpeg,.png,.webp,.heic,image/*' }) => {
  const inputRef = useRef(null);
  const [isDragging, setIsDragging] = useState(false);

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) onFile(file);
  };

  const handleInputChange = (e) => {
    const file = e.target.files?.[0];
    if (file) onFile(file);
    // Reset so choosing the same file again still fires a change event.
    e.target.value = '';
  };

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={`flex flex-col items-center justify-center gap-2 border-2 border-dashed rounded-lg p-6 text-center transition-colors ${
        isDragging ? 'border-gray-900 bg-gray-50' : 'border-gray-300 bg-white'
      }`}
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
          d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
        />
      </svg>
      <p className="text-sm text-gray-600">
        Drag a file here, or{' '}
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="font-semibold text-gray-900 underline hover:no-underline"
        >
          browse
        </button>
      </p>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        onChange={handleInputChange}
        className="sr-only"
        aria-label="Choose a file to attach"
      />
    </div>
  );
};

export default DocumentDropzone;
