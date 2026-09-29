import React, { useEffect, useState } from 'react';
import { useToast } from '../context/ToastContext';
import icuService from '../services/icuService';
import icuWardService from '../services/icuWardService';
import Button from './Button';

const IcuWardModal = ({ open, initial, onClose, onSaved }) => {
  const { error: toastError, success } = useToast();
  const [wardName, setWardName] = useState('');
  const [bedPrice, setBedPrice] = useState('');
  const [totalBeds, setTotalBeds] = useState('');
  const [floorNumber, setFloorNumber] = useState('');
  const [unitType, setUnitType] = useState('ICU');
  const [unitTypes, setUnitTypes] = useState([]);
  const [unitTypesError, setUnitTypesError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setUnitTypesError('');
    icuService
      .getUnitTypes()
      .then((types) => {
        const list = Array.isArray(types) ? types.filter((t) => t.criticalCare) : [];
        setUnitTypes(list);
        if (list.length === 0) {
          setUnitTypesError('No ICU unit types were returned.');
        }
      })
      .catch((e) => {
        setUnitTypes([]);
        setUnitTypesError(
          e?.response?.data?.error || 'Could not load ICU unit types. Please try again.'
        );
      });
  }, [open]);

  useEffect(() => {
    if (initial) {
      setWardName(initial.wardName || '');
      setBedPrice(initial.bedPrice ?? '');
      setTotalBeds(initial.totalBeds ?? '');
      setFloorNumber(initial.floorNumber ?? '');
      setUnitType(initial.unitType || 'ICU');
    } else {
      setWardName('');
      setBedPrice('');
      setTotalBeds('');
      setFloorNumber('');
      setUnitType('ICU');
    }
  }, [initial, open]);

  if (!open) return null;

  const onSubmit = async (e) => {
    e.preventDefault();
    if (!wardName || wardName.trim() === '') {
      toastError('Please enter ICU ward name');
      return;
    }
    if (!bedPrice || Number.isNaN(Number(bedPrice))) {
      toastError('Enter valid bed price');
      return;
    }
    if (totalBeds === '' || Number.isNaN(Number(totalBeds)) || Number(totalBeds) < 0) {
      toastError('Total beds must be 0 or more');
      return;
    }

    setSaving(true);
    try {
      const payload = {
        wardName: wardName.trim(),
        unitType,
        bedPrice: Number(bedPrice),
        totalBeds: Number(totalBeds),
        floorNumber: floorNumber ? Number(floorNumber) : null,
      };

      if (initial && initial.publicId) {
        await icuWardService.updateIcuWard(initial.publicId, payload);
        success('ICU ward updated successfully');
      } else {
        await icuWardService.createIcuWard(payload);
        success('ICU ward created successfully');
      }
      onSaved && onSaved();
      onClose && onClose();
    } catch (err) {
      console.error(err);
      toastError(err.response?.data?.error || err.response?.data?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex">
      <button type="button" aria-label="Close" className="flex-1 bg-black/40" onClick={onClose} />
      <div className="w-96 max-w-full bg-white p-6 shadow-xl h-full overflow-y-auto">
        <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-100">
          <h3 className="text-lg font-bold text-slate-800">
            {initial ? 'Edit ICU Ward' : 'Create ICU Ward'}
          </h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            ✕
          </button>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label
              htmlFor="icu-ward-name"
              className="block text-xs font-semibold text-slate-600 mb-1"
            >
              ICU Ward Name <span className="text-rose-500">*</span>
            </label>
            <input
              id="icu-ward-name"
              type="text"
              value={wardName}
              onChange={(e) => setWardName(e.target.value)}
              placeholder="e.g. Cardiac ICU, MICU-1"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-800 focus:ring-2 focus:ring-sky-500 outline-none"
              required
            />
          </div>

          <div>
            <label
              htmlFor="icu-ward-unit-type"
              className="block text-xs font-semibold text-slate-600 mb-1"
            >
              Unit Classification <span className="text-rose-500">*</span>
            </label>
            {unitTypesError ? (
              <p className="text-xs text-rose-500">{unitTypesError}</p>
            ) : (
              <select
                id="icu-ward-unit-type"
                value={unitType}
                onChange={(e) => setUnitType(e.target.value)}
                className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-800 focus:ring-2 focus:ring-sky-500 outline-none"
              >
                {unitTypes.map((t) => (
                  <option key={t.key} value={t.key}>
                    {t.label} ({t.key})
                  </option>
                ))}
              </select>
            )}
            <p className="text-[11px] text-slate-400 mt-1">
              Selects the critical-care clinical specialty.
            </p>
          </div>

          <div>
            <label
              htmlFor="icu-ward-bed-price"
              className="block text-xs font-semibold text-slate-600 mb-1"
            >
              Daily Bed Price (₹) <span className="text-rose-500">*</span>
            </label>
            <input
              id="icu-ward-bed-price"
              type="number"
              step="0.01"
              value={bedPrice}
              onChange={(e) => setBedPrice(e.target.value)}
              placeholder="0.00"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-800 focus:ring-2 focus:ring-sky-500 outline-none"
              required
            />
            <p className="text-[11px] text-slate-400 mt-1">
              Daily rate applied automatically to inpatient billing.
            </p>
          </div>

          <div>
            <label
              htmlFor="icu-ward-total-beds"
              className="block text-xs font-semibold text-slate-600 mb-1"
            >
              Total Beds <span className="text-rose-500">*</span>
            </label>
            <input
              id="icu-ward-total-beds"
              type="number"
              value={totalBeds}
              onChange={(e) => setTotalBeds(e.target.value)}
              placeholder="Number of beds"
              min="0"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-800 focus:ring-2 focus:ring-sky-500 outline-none"
              required
            />
            <p className="text-[11px] text-slate-400 mt-1">
              Beds will be auto-generated with unique codes (e.g. ICU-B1).
            </p>
          </div>

          <div>
            <label
              htmlFor="icu-ward-floor-number"
              className="block text-xs font-semibold text-slate-600 mb-1"
            >
              Floor Number
            </label>
            <input
              id="icu-ward-floor-number"
              type="number"
              value={floorNumber}
              onChange={(e) => setFloorNumber(e.target.value)}
              placeholder="e.g. 2"
              className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-800 focus:ring-2 focus:ring-sky-500 outline-none"
            />
          </div>

          <div className="pt-4 flex justify-end gap-2 border-t border-slate-100">
            <Button variant="outline" type="button" onClick={onClose} disabled={saving}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" disabled={saving}>
              {saving ? 'Saving...' : initial ? 'Update ICU Ward' : 'Create ICU Ward'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default IcuWardModal;
