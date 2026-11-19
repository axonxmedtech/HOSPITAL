/**
 * Shown under a doctor picker that has nothing in it. A brand-new hospital has no doctors, and an
 * empty dropdown gave the receptionist no clue why nothing could be registered.
 */
export default function NoDoctorsHint({ doctors }) {
  if (!Array.isArray(doctors) || doctors.length > 0) return null;
  return (
    <p className="text-xs text-amber-800 bg-amber-50 rounded p-2 mt-1">
      No doctors have been added yet. The hospital admin adds them under Staff → Doctors.
    </p>
  );
}
