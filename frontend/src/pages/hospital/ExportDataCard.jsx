import React, { useState } from 'react';
import { useToast } from '../../context/ToastContext';
import importService, { saveBlob } from '../../services/importService';

/**
 * ExportDataCard — downloads this hospital's patient records as an Excel workbook.
 *
 * The download is deliberately blunt about two things. It is not a complete backup: visit,
 * admission, prescription and billing history are not in the file, and an admin who assumes
 * otherwise would be relying on it for something it cannot do. And the file is unencrypted patient
 * data — once it is on someone's laptop it sits outside every protection the server provides.
 */
const ExportDataCard = () => {
  const { success, error: toastError } = useToast();
  const [busy, setBusy] = useState(false);

  const runExport = async () => {
    setBusy(true);
    try {
      const res = await importService.exportPatients();

      // Prefer the filename the server chose so the timestamp matches when it was generated.
      const disposition = res.headers?.['content-disposition'] || '';
      const match = disposition.match(/filename=([^;]+)/i);
      const filename = match ? match[1].trim().replace(/"/g, '') : 'hospital-export.xlsx';

      saveBlob(res.data, filename);
      success('Export downloaded.');
    } catch (err) {
      toastError(err?.response?.data?.error || 'Could not build the export file.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-200 p-6">
      <h3 className="text-lg font-bold text-gray-900">Export Data</h3>
      <p className="text-sm text-gray-600 mt-1 mb-5">
        Download this hospital&rsquo;s patient records as an Excel file — useful for reporting, for
        an offsite copy, or for taking your data with you.
      </p>

      <div className="border border-gray-200 rounded-xl p-4 mb-5 bg-slate-50/60">
        <p className="text-sm font-semibold text-gray-900 mb-2">What the file contains</p>
        <ul className="text-sm text-gray-700 space-y-1 list-disc list-inside">
          <li>
            Every active patient: name, contact, date of birth, address, medical history and status
          </li>
          <li>The old patient ID (MRN) for anyone brought in through an import</li>
          <li>Any extra columns an import preserved, so nothing captured for you is left behind</li>
        </ul>
        <p className="text-sm font-semibold text-gray-900 mt-3 mb-1">What it does not contain</p>
        <p className="text-sm text-gray-700">
          Visits, admissions, prescriptions and bills are <strong>not</strong> included. This is a
          patient list, not a full backup — do not rely on it as one.
        </p>
      </div>

      <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-900 mb-5">
        <strong>This file holds patient information and is not encrypted.</strong> Once downloaded
        it is outside the protections this system provides. Keep it somewhere secure and delete
        local copies when you are finished. Every export is recorded in the audit log.
      </div>

      <button
        onClick={runExport}
        disabled={busy}
        className="px-5 py-2.5 bg-sky-600 text-white rounded-lg font-medium disabled:opacity-50 hover:bg-sky-700 transition"
      >
        {busy ? 'Preparing file…' : 'Download Excel file'}
      </button>
      {busy && (
        <p className="text-xs text-gray-500 mt-2">
          Large hospitals can take a minute — leave this page open.
        </p>
      )}
    </div>
  );
};

export default ExportDataCard;
