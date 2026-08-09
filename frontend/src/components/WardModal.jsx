import React, { useEffect, useState } from 'react';
import { useToast } from '../context/ToastContext';
import WardService from '../services/wardService';
import Button from './Button';

/**
 * @param {('IPD'|'ICU'|'OT')} wardType fixed by the screen the modal opened from, not chosen here.
 *   A ward does not change purpose by accident, and the admin already said which kind they are
 *   managing by being on that screen.
 */
const WardModal = ({ open, wardType = 'IPD', initial, onClose, onSaved }) => {
  const { error: toastError } = useToast();
  const [wardName, setWardName] = useState('');
  const [bedPrice, setBedPrice] = useState('');
  const [totalBeds, setTotalBeds] = useState('');
  const [floorNumber, setFloorNumber] = useState('');
  const [saving, setSaving] = useState(false);
  // The ward's own type. Creation takes it from the screen, but an existing ward keeps whichever
  // type it already has - and must be able to change it. Without this a ward created under the
  // wrong tab was invisible on the screen it belonged to and impossible to correct anywhere.
  const [effectiveType, setEffectiveType] = useState(wardType);

  useEffect(() => {
    if (initial) {
      setWardName(initial.wardName || '');
      setBedPrice(initial.bedPrice ?? '');
      setTotalBeds(initial.totalBeds ?? '');
      setFloorNumber(initial.floorNumber ?? '');
      setEffectiveType(initial.wardType || wardType);
    } else {
      setWardName('');
      setBedPrice('');
      setTotalBeds('');
      setFloorNumber('');
      setEffectiveType(wardType);
    }
  }, [initial, open]);

  if (!open) return null;

  const onSubmit = async (e) => {
    e.preventDefault();
    // basic client-side validation
    if (!wardName || wardName.trim() === '') {
      toastError('Please enter ward name');
      return;
    }
    if (!bedPrice || Number.isNaN(Number(bedPrice))) {
      toastError('Enter valid bed price');
      return;
    }
    if (totalBeds !== '' && (Number.isNaN(Number(totalBeds)) || Number(totalBeds) < 0)) {
      toastError('Total beds must be 0 or more');
      return;
    }
    // Mirrors the server rule so the admin is told before the round trip. The server still
    // enforces it - this is a courtesy, not the guard.
    if (effectiveType === 'OT' && totalBeds !== '' && Number(totalBeds) > 1) {
      toastError('An OT ward has at most one bed — it hosts one case at a time');
      return;
    }

    setSaving(true);
    try {
      if (initial && initial.wardId) {
        const payload = {
          wardName,
          // Sent on edit so a ward filed under the wrong type can be corrected. It was previously
          // omitted, which left the backend's "only set when non-null" guard with nothing to
          // apply - the type could be set at creation and never changed again.
          wardType: effectiveType,
          bedPrice: Number(bedPrice),
          floorNumber: floorNumber ? Number(floorNumber) : null,
          // Bed count is editable on edit too — the backend adds/removes beds to match.
          // A theatre always resolves to exactly one bed. The field is hidden for OT, and beds
          // cannot be added any other way, so without this an OT ward that ended up with none
          // could never get one. Resizing to 1 is a no-op when it already has 1, so this is
          // self-healing rather than destructive.
          totalBeds: effectiveType === 'OT' ? 1 : totalBeds === '' ? null : Number(totalBeds),
        };
        await WardService.updateWard(initial.wardId, payload);
      } else {
        const payload = {
          wardName,
          wardType: effectiveType,
          // A theatre is one bed by definition, so the field is hidden and the value implied.
          totalBeds: effectiveType === 'OT' ? 1 : totalBeds ? Number(totalBeds) : 0,
          bedPrice: Number(bedPrice),
          floorNumber: floorNumber ? Number(floorNumber) : null,
        };
        await WardService.createWard(payload);
      }
      onSaved && onSaved();
      onClose && onClose();
    } catch (err) {
      console.error(err);
      toastError(err.response?.data?.error || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex">
      <button type="button" aria-label="Close" className="flex-1 bg-black/40" onClick={onClose} />
      <div className="w-96 max-w-full bg-white p-4 shadow-xl h-full overflow-y-auto">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold">{initial ? 'Edit Ward' : 'Create Ward'}</h3>
          <button onClick={onClose} className="text-slate-500">
            Close
          </button>
        </div>

        <form onSubmit={onSubmit} className="space-y-3">
          <div>
            <label htmlFor="fld-38" className="block text-sm text-slate-600">
              Ward Name
            </label>
            <input
              id="fld-38"
              value={wardName}
              onChange={(e) => setWardName(e.target.value)}
              className="mt-1 w-full p-2 border rounded"
            />
          </div>

          {/* Always visible. The type decides which screen a ward appears on and where patients
              can be admitted or moved, so creating one without seeing it — or discovering later
              that it is wrong and having no way to change it — is how a general ward ends up
              filed as intensive care. */}
          <div>
            <label htmlFor="fld-wardtype" className="block text-sm text-slate-600">
              Ward Type
            </label>
            <select
              id="fld-wardtype"
              value={effectiveType}
              onChange={(e) => setEffectiveType(e.target.value)}
              className="mt-1 w-full p-2 border rounded bg-white"
            >
              <option value="IPD">General ward (IPD)</option>
              <option value="ICU">ICU ward</option>
              {/* OT is offered only for a ward that already has it, so a legacy OT ward can be
                  retyped to something valid. Theatres themselves live under OT Theatres. */}
              {effectiveType === 'OT' && <option value="OT">OT ward (legacy)</option>}
            </select>
            <p className="mt-1 text-xs text-slate-500">
              {effectiveType === 'ICU'
                ? 'Patients are moved here from a general ward; they are not admitted directly.'
                : effectiveType === 'OT'
                  ? 'Legacy OT ward. Theatres are managed under OT Theatres — retype this to a general or ICU ward.'
                  : 'A normal inpatient ward. Patients are admitted here.'}
            </p>
          </div>

          <div>
            <label htmlFor="fld-37" className="block text-sm text-slate-600">
              {effectiveType === 'OT' ? 'Theatre charge (per surgery)' : 'Bed Price (per day)'}
            </label>
            <input
              id="fld-37"
              value={bedPrice}
              onChange={(e) => setBedPrice(e.target.value)}
              type="number"
              step="0.01"
              className="mt-1 w-full p-2 border rounded"
            />
          </div>

          {/* A theatre is one bed by definition, so there is nothing to ask. Showing a field
              whose only valid answer is 1 invites someone to type 2 and get an error. */}
          {effectiveType === 'OT' ? (
            <p className="text-xs text-slate-500 self-end pb-2">
              A theatre holds one case at a time, so it has exactly one bed. Add a separate theatre
              for each one you operate in.
            </p>
          ) : (
            <div>
              <label htmlFor="fld-36" className="block text-sm text-slate-600">
                Total Beds
              </label>
              <input
                id="fld-36"
                value={totalBeds}
                onChange={(e) => setTotalBeds(e.target.value)}
                type="number"
                min="0"
                className="mt-1 w-full p-2 border rounded"
              />
              {initial && (
                <p className="mt-1 text-xs text-slate-500">
                  Increasing adds new beds. Decreasing removes free beds only — occupied beds are
                  never deleted.
                </p>
              )}
            </div>
          )}

          <div>
            <label htmlFor="fld-35" className="block text-sm text-slate-600">
              Floor Number
            </label>
            <input
              id="fld-35"
              value={floorNumber}
              onChange={(e) => setFloorNumber(e.target.value)}
              type="number"
              className="mt-1 w-full p-2 border rounded"
            />
          </div>

          <div className="flex justify-end gap-2 mt-4">
            <Button variant="outline" onClick={onClose} type="button">
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default WardModal;
