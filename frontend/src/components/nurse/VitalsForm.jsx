import { useState } from 'react';
import nurseService from '../../services/nurseService';

export default function VitalsForm({ admissionId, onSaved }) {
  const [form, setForm] = useState({ bloodPressure: '', pulse: '', temperature: '', spo2: '' });
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await nurseService.recordVitals(admissionId, form);
      setForm({ bloodPressure: '', pulse: '', temperature: '', spo2: '' });
      onSaved();
    } catch (err) {
      alert(err.response?.data || 'Failed to record vitals');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap gap-3 items-end">
      {[
        ['bloodPressure', 'BP', 'text'],
        ['pulse', 'Pulse', 'number'],
        ['temperature', 'Temp °F', 'number'],
        ['spo2', 'SpO2 %', 'number'],
      ].map(([key, label, type]) => (
        <div key={key}>
          <label className="block text-xs text-gray-600 mb-1">{label}</label>
          <input
            type={type}
            value={form[key]}
            onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
            className="border border-gray-300 rounded px-2 py-1.5 text-sm w-24"
          />
        </div>
      ))}
      <button
        type="submit"
        disabled={saving}
        className="px-3 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
      >
        {saving ? '...' : 'Record'}
      </button>
    </form>
  );
}
