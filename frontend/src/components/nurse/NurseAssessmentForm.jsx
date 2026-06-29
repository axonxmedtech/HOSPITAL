import { useState } from 'react';
import nurseService from '../../services/nurseService';

export default function NurseAssessmentForm({ admissionId, onSaved }) {
  const [form, setForm] = useState({
    bloodPressure: '', pulse: '', temperature: '', spo2: '',
    height: '', weight: '', painScore: '', allergies: '',
    fallRisk: 'LOW', generalCondition: '', chiefComplaintOnAdmission: '',
  });
  const [saving, setSaving] = useState(false);

  const field = (key, label, type = 'text', extra = {}) => (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      <input
        type={type}
        value={form[key]}
        onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
        className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
        {...extra}
      />
    </div>
  );

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await nurseService.createAssessment(admissionId, form);
      onSaved();
    } catch (err) {
      alert(err.response?.data || 'Failed to save assessment');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <h3 className="font-semibold text-gray-800">Initial Assessment</h3>
      <div className="grid grid-cols-2 gap-4">
        {field('bloodPressure', 'Blood Pressure (e.g. 120/80)')}
        {field('pulse', 'Pulse (bpm)', 'number')}
        {field('temperature', 'Temperature (°F)', 'number', { step: '0.1' })}
        {field('spo2', 'SpO2 (%)', 'number')}
        {field('height', 'Height (cm)', 'number', { step: '0.1' })}
        {field('weight', 'Weight (kg)', 'number', { step: '0.1' })}
        {field('painScore', 'Pain Score (0–10)', 'number', { min: 0, max: 10 })}
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Fall Risk</label>
        <select
          value={form.fallRisk}
          onChange={e => setForm(f => ({ ...f, fallRisk: e.target.value }))}
          className="border border-gray-300 rounded px-3 py-2 text-sm"
        >
          {['LOW', 'MEDIUM', 'HIGH'].map(r => <option key={r}>{r}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Allergies</label>
        <textarea
          value={form.allergies}
          onChange={e => setForm(f => ({ ...f, allergies: e.target.value }))}
          className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          rows={2}
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Chief Complaint on Admission</label>
        <textarea
          value={form.chiefComplaintOnAdmission}
          onChange={e => setForm(f => ({ ...f, chiefComplaintOnAdmission: e.target.value }))}
          className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          rows={2}
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">General Condition</label>
        <textarea
          value={form.generalCondition}
          onChange={e => setForm(f => ({ ...f, generalCondition: e.target.value }))}
          className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          rows={2}
        />
      </div>
      <button
        type="submit"
        disabled={saving}
        className="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
      >
        {saving ? 'Saving...' : 'Save Assessment'}
      </button>
    </form>
  );
}
