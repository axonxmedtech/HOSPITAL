import { useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import apiClient from '../../services/apiService';
import { extractApiError } from '../../utils/apiError';

const GSTIN = /^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/;

/**
 * GSTIN and drug licence number printed on every pharmacy tax invoice. Without them a bill is
 * not a valid pharmacy invoice, so the card says plainly when either is missing.
 */
export default function PharmacyInvoiceDetailsCard() {
  const { success, error: toastError } = useToast();
  const [form, setForm] = useState({ gstin: '', drugLicenseNumber: '' });
  const [saved, setSaved] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    apiClient
      .get('/hospital/settings/invoice-details')
      .then((r) => {
        setSaved(r.data);
        setForm({ gstin: r.data.gstin || '', drugLicenseNumber: r.data.drugLicenseNumber || '' });
      })
      .catch((e) => toastError(extractApiError(e, 'Could not load invoice details')));
  }, [toastError]);

  const gstin = form.gstin.trim().toUpperCase();
  const gstinError =
    gstin && !GSTIN.test(gstin) ? 'GSTIN must be 15 characters, e.g. 27ABCDE1234F1Z5' : '';

  const save = async (e) => {
    e.preventDefault();
    if (gstinError) return;
    setSaving(true);
    try {
      const r = await apiClient.put('/hospital/settings/invoice-details', {
        gstin,
        drugLicenseNumber: form.drugLicenseNumber.trim(),
      });
      setSaved(r.data);
      success('Invoice details saved. They now print on every pharmacy bill.');
    } catch (err) {
      toastError(extractApiError(err, 'Could not save invoice details'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form
      onSubmit={save}
      className="p-6 bg-white rounded-2xl border border-gray-200/80 shadow-sm max-w-4xl mx-auto my-4"
    >
      <h2 className="text-xl font-bold mb-1 text-gray-900">Invoice details</h2>
      <p className="text-sm text-gray-500 mb-4">
        Printed on every pharmacy bill. A tax invoice must show your GSTIN and drug licence number.
      </p>
      {saved && !saved.complete && (
        <p
          className="mb-4 rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-sm text-amber-800"
          role="status"
        >
          Not complete yet: bills currently print “not entered” in place of the missing details.
        </p>
      )}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <label className="block text-sm">
          <span className="font-semibold text-gray-700">GSTIN</span>
          <input
            value={form.gstin}
            onChange={(e) => setForm((f) => ({ ...f, gstin: e.target.value }))}
            maxLength={15}
            placeholder="27ABCDE1234F1Z5"
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg uppercase"
            aria-invalid={Boolean(gstinError)}
          />
          {gstinError && <span className="mt-1 block text-xs text-red-600">{gstinError}</span>}
        </label>
        <label className="block text-sm">
          <span className="font-semibold text-gray-700">Drug licence number(s)</span>
          <input
            value={form.drugLicenseNumber}
            onChange={(e) => setForm((f) => ({ ...f, drugLicenseNumber: e.target.value }))}
            maxLength={120}
            placeholder="20B/MH/123, 21B/MH/124"
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </label>
      </div>
      <div className="mt-5 flex justify-end">
        <button
          type="submit"
          disabled={saving || Boolean(gstinError)}
          className="px-5 py-2 rounded-lg bg-gray-900 text-white text-sm font-semibold disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save invoice details'}
        </button>
      </div>
    </form>
  );
}
