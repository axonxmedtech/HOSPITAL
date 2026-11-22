import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../context/ToastContext';
import hospitalService from '../../services/hospitalService';
import { extractApiError } from '../../utils/apiError';

const blank = { id: null, name: '', price: '', includedDays: '', description: '' };

/**
 * Fixed-price IPD packages. A package covers bed, nursing and doctor visits for its days;
 * medicines, consumables and extra days are charged as usual. Packages are retired (made
 * inactive), never deleted, because admissions refer to them.
 */
export default function IpdPackagesCard() {
  const { success, error: toastError } = useToast();
  const [packages, setPackages] = useState(null);
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setPackages(await hospitalService.getIpdPackages(true));
    } catch (err) {
      toastError(extractApiError(err, 'Failed to load packages'));
      setPackages([]);
    }
  }, [toastError]);

  useEffect(() => {
    load();
  }, [load]);

  const save = async () => {
    if (!form.name.trim()) return toastError('Enter a package name');
    if (!(Number(form.price) > 0)) return toastError('Enter the package price');
    const days = Number(form.includedDays);
    if (!Number.isInteger(days) || days < 1 || days > 60)
      return toastError('Included days must be 1 to 60');
    setSaving(true);
    try {
      const payload = {
        name: form.name.trim(),
        price: Number(form.price),
        includedDays: days,
        description: form.description.trim() || null,
      };
      if (form.id) await hospitalService.updateIpdPackage(form.id, payload);
      else await hospitalService.createIpdPackage(payload);
      success(form.id ? 'Package updated' : 'Package added');
      setForm(null);
      await load();
    } catch (err) {
      toastError(extractApiError(err, 'Failed to save the package'));
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (p) => {
    try {
      await hospitalService.updateIpdPackage(p.id, {
        name: p.name,
        price: p.price,
        includedDays: p.includedDays,
        description: p.description,
        isActive: !p.isActive,
      });
      await load();
    } catch (err) {
      toastError(extractApiError(err, 'Failed to update the package'));
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 mt-6">
      <div className="flex items-start justify-between gap-4 mb-1">
        <h3 className="text-lg font-bold text-gray-900">IPD Packages</h3>
        {!form && (
          <button
            type="button"
            onClick={() => setForm({ ...blank })}
            className="px-3 py-1.5 bg-sky-700 hover:bg-sky-800 text-white rounded-lg text-sm font-semibold"
          >
            + Add package
          </button>
        )}
      </div>
      <p className="text-sm text-gray-500 mb-5">
        A package covers bed, nursing and doctor visits for its days at one price. Medicines,
        consumables and extra days are charged as usual. Reception can apply a package at admission
        or later.
      </p>

      {form && (
        <div className="border rounded-xl p-4 mb-5 bg-gray-50">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="md:col-span-3">
              <label htmlFor="pkg-name" className="block text-sm font-medium mb-1">
                Name *
              </label>
              <input
                id="pkg-name"
                value={form.name}
                maxLength={120}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Normal delivery"
                className="w-full border p-2 rounded bg-white"
              />
            </div>
            <div>
              <label htmlFor="pkg-price" className="block text-sm font-medium mb-1">
                Price (₹) *
              </label>
              <input
                id="pkg-price"
                type="number"
                min="1"
                value={form.price}
                onChange={(e) => setForm({ ...form, price: e.target.value })}
                className="w-full border p-2 rounded bg-white"
              />
            </div>
            <div>
              <label htmlFor="pkg-days" className="block text-sm font-medium mb-1">
                Days included *
              </label>
              <input
                id="pkg-days"
                type="number"
                min="1"
                max="60"
                value={form.includedDays}
                onChange={(e) => setForm({ ...form, includedDays: e.target.value })}
                className="w-full border p-2 rounded bg-white"
              />
            </div>
            <div className="md:col-span-3">
              <label htmlFor="pkg-desc" className="block text-sm font-medium mb-1">
                Description
              </label>
              <input
                id="pkg-desc"
                value={form.description}
                maxLength={500}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="w-full border p-2 rounded bg-white"
              />
            </div>
          </div>
          {form.id && (
            <p className="text-xs text-gray-500 mt-2">
              A new price or number of days applies when the package is next applied; stays that
              already have it keep what was charged.
            </p>
          )}
          <div className="flex justify-end gap-3 mt-4">
            <button
              type="button"
              onClick={() => setForm(null)}
              className="px-3 py-1.5 bg-gray-100 rounded"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={save}
              disabled={saving}
              className="px-3 py-1.5 bg-sky-700 hover:bg-sky-800 text-white rounded disabled:opacity-60"
            >
              {saving ? 'Saving…' : 'Save package'}
            </button>
          </div>
        </div>
      )}

      {packages === null ? (
        <p className="text-sm text-gray-500">Loading…</p>
      ) : packages.length === 0 ? (
        <p className="text-sm text-gray-500">No packages yet.</p>
      ) : (
        <ul className="divide-y">
          {packages.map((p) => (
            <li key={p.id} className="py-3 flex flex-wrap items-center justify-between gap-2">
              <div className={p.isActive ? '' : 'opacity-60'}>
                <div className="font-medium text-gray-900">
                  {p.name}
                  {!p.isActive && <span className="ml-2 text-xs text-gray-500">(inactive)</span>}
                </div>
                <div className="text-sm text-gray-600">
                  ₹{Number(p.price).toLocaleString('en-IN')} · {p.includedDays} day
                  {p.includedDays === 1 ? '' : 's'}
                  {p.description ? ` · ${p.description}` : ''}
                </div>
              </div>
              <div className="flex gap-3 text-sm">
                <button
                  type="button"
                  onClick={() =>
                    setForm({
                      id: p.id,
                      name: p.name,
                      price: p.price,
                      includedDays: p.includedDays,
                      description: p.description || '',
                    })
                  }
                  className="text-sky-700 hover:underline"
                >
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => toggleActive(p)}
                  className={
                    p.isActive ? 'text-red-700 hover:underline' : 'text-emerald-700 hover:underline'
                  }
                >
                  {p.isActive ? 'Retire' : 'Reactivate'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
