/** The small segmented switch dashboards use above a list ("Current Patients | History"). */
export default function SubTabs({ options, value, onChange }) {
  return (
    <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200 h-[38px] items-center">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          aria-pressed={value === o.value}
          onClick={() => onChange(o.value)}
          className={`px-4 py-1 text-sm font-medium rounded-md transition-all whitespace-nowrap ${
            value === o.value
              ? 'bg-white text-sky-600 shadow-sm border border-gray-100 font-semibold'
              : 'text-gray-600 hover:text-gray-800 hover:bg-gray-200'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
