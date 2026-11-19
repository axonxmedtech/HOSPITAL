import { useState } from 'react';
import { useToast } from '../../context/ToastContext';
import nurseService from '../../services/nurseService';
import { extractApiError } from '../../utils/apiError';
import StockPicker from './StockPicker';

/**
 * Give something that was not ordered — an extra saline bottle, a dressing, a stat dose. From
 * stock it is taken from the inventory and billed; from the patient's own supply it is only
 * recorded.
 */
export default function GiveExtraItem({
  admissionId,
  performedByNurseId,
  needsNurse,
  onDone,
  onCancel,
}) {
  const { success, error: toastError } = useToast();
  const [picked, setPicked] = useState(null);
  const [typedName, setTypedName] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [ownSupply, setOwnSupply] = useState(false);
  const [remarks, setRemarks] = useState('');
  const [saving, setSaving] = useState(false);

  const name = picked ? picked.name : typedName.trim();
  const fromStock = !!picked && !ownSupply;
  const tooMany = fromStock && Number(quantity) > picked.available;

  const submit = async () => {
    if (!name) return toastError('Choose or type what was given');
    if (needsNurse && !performedByNurseId) return toastError('Select the nurse who performed this');
    setSaving(true);
    try {
      await nurseService.giveExtraItem({
        ipdAdmissionId: admissionId,
        source: fromStock ? picked.source : null,
        medicineId: fromStock ? picked.medicineId : null,
        name,
        quantity: Number(quantity),
        ownSupply: !fromStock,
        remarks: remarks.trim() || null,
        performedByNurseId: performedByNurseId ? Number(performedByNurseId) : null,
      });
      success(
        fromStock
          ? `${name} given and added to the bill`
          : `${name} recorded (patient's own supply)`
      );
      onDone?.();
    } catch (err) {
      toastError(extractApiError(err, 'Could not record the item'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="border border-sky-200 bg-sky-50/40 rounded-xl p-4 space-y-3">
      <h4 className="text-sm font-semibold text-gray-900">Give an item that was not ordered</h4>
      <StockPicker label="Item from stock" selected={picked} onSelect={setPicked} />
      {!picked && (
        <label className="block text-xs font-medium text-gray-600">
          Or type a name (patient&apos;s own supply)
          <input
            value={typedName}
            onChange={(e) => setTypedName(e.target.value)}
            maxLength={150}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </label>
      )}
      <div className="flex flex-wrap items-end gap-4">
        <label className="text-xs font-medium text-gray-600">
          Quantity
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="mt-1 block w-24 px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </label>
        {picked && (
          <label className="flex items-center gap-2 text-sm text-gray-700 pb-2">
            <input
              type="checkbox"
              checked={ownSupply}
              onChange={(e) => setOwnSupply(e.target.checked)}
            />
            Patient&apos;s own supply
          </label>
        )}
      </div>
      {tooMany && (
        <p className="text-xs text-red-700">
          Only {picked.available} in stock. Give fewer, or tick &quot;Patient&apos;s own
          supply&quot;.
        </p>
      )}
      <label className="block text-xs font-medium text-gray-600">
        Remarks
        <input
          value={remarks}
          onChange={(e) => setRemarks(e.target.value)}
          maxLength={500}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
        />
      </label>
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-3 py-1.5 text-sm rounded-lg border border-gray-300"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={submit}
          disabled={saving || !name || tooMany || Number(quantity) < 1}
          className="px-3 py-1.5 text-sm font-semibold rounded-lg bg-gray-900 text-white disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Give'}
        </button>
      </div>
    </div>
  );
}
