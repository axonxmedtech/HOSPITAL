import React from 'react';
import Button from './Button';

const WardCard = ({
  ward,
  onViewBeds,
  onEdit,
  onDelete,
  deleting,
  inchargeOptions,
  onSetIncharge,
}) => {
  return (
    <div className="bg-white rounded-xl shadow p-4 flex flex-col justify-between">
      <div>
        <div className="flex items-center gap-2 flex-wrap">
          <h3 className="text-lg font-semibold text-slate-800">{ward.wardName}</h3>
          {/* Shown on every card, including general wards. A badge that only appears for ICU and
              OT would make a mistyped ward look like a normal one, which is the failure this is
              here to catch. */}
          <span
            className={`px-2 py-0.5 text-[10px] font-bold uppercase rounded-full ${
              ward.wardType === 'ICU'
                ? 'bg-rose-100 text-rose-800'
                : ward.wardType === 'OT'
                  ? 'bg-purple-100 text-purple-800'
                  : 'bg-slate-100 text-slate-600'
            }`}
          >
            {ward.wardType === 'ICU' ? 'ICU' : ward.wardType === 'OT' ? 'OT' : 'General'}
          </span>
        </div>
        <p className="text-sm text-slate-500">Beds: {ward.totalBeds}</p>
        <p className="text-sm text-slate-500">Price: ₹{ward.bedPrice}</p>
        {ward.floorNumber !== null && (
          <p className="text-sm text-slate-500">Floor: {ward.floorNumber}</p>
        )}
      </div>

      {onSetIncharge && (
        <div className="mt-3">
          <label htmlFor="fld-34" className="block text-xs font-medium text-slate-500 mb-1">
            Nurse Incharge
          </label>
          <select
            id="fld-34"
            className="w-full border border-slate-300 rounded-lg px-2 py-1.5 text-sm text-slate-700 focus:ring-2 focus:ring-sky-500 focus:border-transparent outline-none"
            value={ward.inchargeNurseId || ''}
            onChange={(e) => onSetIncharge(ward, e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">— No incharge assigned —</option>
            {(inchargeOptions || []).map((n) => (
              <option key={n.nurseProfileId} value={n.nurseProfileId}>
                {n.name}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="mt-4 flex gap-2">
        <Button variant="secondary" size="sm" onClick={() => onViewBeds(ward)}>
          View Beds
        </Button>
        <Button variant="outline" size="sm" onClick={() => onEdit(ward)}>
          Edit
        </Button>
        {onDelete && (
          <Button variant="alert" size="sm" onClick={() => onDelete(ward)} disabled={deleting}>
            {deleting ? '...' : 'Delete'}
          </Button>
        )}
      </div>
    </div>
  );
};

export default WardCard;
