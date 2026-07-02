import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import feedbackService from '../../services/feedbackService';

const RATING_FIELDS = [
  { key: 'receptionRating', label: 'Reception' },
  { key: 'doctorRating', label: 'Doctor' },
  { key: 'nurseRating', label: 'Nurse' },
  { key: 'housekeepingRating', label: 'Housekeeping' },
  { key: 'billingRating', label: 'Billing' },
  { key: 'facilityRating', label: 'Facility' },
];

function StarRating({ value, onChange }) {
  return (
    <div className="flex gap-1">
      {[1, 2, 3, 4, 5].map(n => (
        <button
          type="button"
          key={n}
          onClick={() => onChange(n)}
          className={`text-2xl leading-none ${value >= n ? 'text-amber-400' : 'text-gray-300'}`}
          aria-label={`${n} star`}
        >
          ★
        </button>
      ))}
    </div>
  );
}

export default function PublicFeedbackPage() {
  const { token } = useParams();
  const [form, setForm] = useState({
    overallRating: 0,
    receptionRating: 0,
    doctorRating: 0,
    nurseRating: 0,
    housekeepingRating: 0,
    billingRating: 0,
    facilityRating: 0,
    recommendScore: '',
    complaints: '',
    suggestions: '',
  });
  const [status, setStatus] = useState('idle'); // idle | submitting | done | error
  const [errorMsg, setErrorMsg] = useState('');

  const setRating = (key, val) => setForm(p => ({ ...p, [key]: val }));

  const submit = async (e) => {
    e.preventDefault();
    if (!form.overallRating) {
      setErrorMsg('Please give an overall rating.');
      return;
    }
    setStatus('submitting');
    setErrorMsg('');
    try {
      const payload = {
        ...form,
        recommendScore: form.recommendScore === '' ? null : Number(form.recommendScore),
      };
      Object.keys(payload).forEach(k => { if (payload[k] === 0) payload[k] = null; });
      payload.overallRating = form.overallRating;
      await feedbackService.submitPublicFeedback(token, payload);
      setStatus('done');
    } catch (ex) {
      setStatus('error');
      const data = ex?.response?.data;
      setErrorMsg((typeof data === 'string' ? data : data?.message) || 'This feedback link is invalid, expired, or already used.');
    }
  };

  if (status === 'done') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 max-w-md text-center space-y-2">
          <h1 className="text-xl font-bold text-gray-900">Thank you for your feedback</h1>
          <p className="text-sm text-gray-600">Your response has been recorded and helps us improve care quality.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-10 flex justify-center">
      <form onSubmit={submit} className="bg-white rounded-2xl shadow-sm border border-gray-200 p-6 max-w-lg w-full space-y-5">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Share Your Experience</h1>
          <p className="text-sm text-gray-600 mt-1">Your feedback is confidential and helps us serve you better.</p>
        </div>

        {status === 'error' && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2">{errorMsg}</div>
        )}

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Overall Experience *</label>
          <StarRating value={form.overallRating} onChange={v => setRating('overallRating', v)} />
        </div>

        {RATING_FIELDS.map(f => (
          <div key={f.key}>
            <label className="block text-sm font-medium text-gray-700 mb-1">{f.label}</label>
            <StarRating value={form[f.key]} onChange={v => setRating(f.key, v)} />
          </div>
        ))}

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">How likely are you to recommend us? (0-10)</label>
          <input
            type="number" min="0" max="10"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            value={form.recommendScore}
            onChange={e => setForm(p => ({ ...p, recommendScore: e.target.value }))}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Complaints, if any</label>
          <textarea
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            rows={3}
            value={form.complaints}
            onChange={e => setForm(p => ({ ...p, complaints: e.target.value }))}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Suggestions</label>
          <textarea
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            rows={2}
            value={form.suggestions}
            onChange={e => setForm(p => ({ ...p, suggestions: e.target.value }))}
          />
        </div>

        <button
          type="submit"
          disabled={status === 'submitting'}
          className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-semibold py-2.5 rounded-lg"
        >
          {status === 'submitting' ? 'Submitting…' : 'Submit Feedback'}
        </button>
      </form>
    </div>
  );
}
