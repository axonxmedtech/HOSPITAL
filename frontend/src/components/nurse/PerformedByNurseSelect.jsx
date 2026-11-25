import React from 'react';

/**
 * The required "Performed By Nurse" dropdown shown under shared nurse login. Pair it with
 * `usePerformingNurse`, which decides whether it is needed and loads the ward's staff nurses.
 */
const PerformedByNurseSelect = ({ id, performing }) => {
  if (!performing.needsPicker) return null;
  return (
    <div>
      <label htmlFor={id} className="block text-xs font-medium text-gray-600 mb-1">
        Performed By Nurse <span className="text-red-600">*</span>
      </label>
      <select
        id={id}
        value={performing.nurseId}
        onChange={(e) => performing.setNurseId(e.target.value)}
        required
        className="w-full sm:w-64 px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
      >
        <option value="">Select nurse…</option>
        {performing.nurses.map((n) => (
          <option key={n.id} value={n.id}>
            {n.name}
          </option>
        ))}
      </select>
    </div>
  );
};

export default PerformedByNurseSelect;
