import React, { useState, useEffect, useCallback } from 'react';
import feedbackService from '../../services/feedbackService';
import { useToast } from '../../context/ToastContext';

const TABS = ['Issue Link', 'Feedback', 'Complaints'];

const SEVERITY_BADGE = {
  LOW: 'bg-gray-100 text-gray-600',
  MEDIUM: 'bg-amber-100 text-amber-700',
  HIGH: 'bg-red-100 text-red-700',
};

export default function FeedbackView() {
  const { success, error: toastError } = useToast();
  const [tab, setTab] = useState('Issue Link');

  const [feedback, setFeedback] = useState([]);
  const [complaints, setComplaints] = useState([]);
  const [issueForm, setIssueForm] = useState({ patientId: '', feedbackType: 'OPD', appointmentId: '', admissionId: '' });
  const [issuedLink, setIssuedLink] = useState('');
  const [resolutionDraft, setResolutionDraft] = useState({});

  const load = useCallback(async () => {
    try {
      const [f, c] = await Promise.all([
        feedbackService.getFeedback(),
        feedbackService.getComplaints(),
      ]);
      setFeedback(Array.isArray(f?.data) ? f.data : (Array.isArray(f) ? f : []));
      setComplaints(Array.isArray(c?.data) ? c.data : (Array.isArray(c) ? c : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitIssue = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        patientId: Number(issueForm.patientId),
        feedbackType: issueForm.feedbackType,
        appointmentId: issueForm.appointmentId ? Number(issueForm.appointmentId) : null,
        admissionId: issueForm.admissionId ? Number(issueForm.admissionId) : null,
      };
      const res = await feedbackService.issueToken(payload);
      const saved = res?.data || res;
      const link = `${window.location.origin}/feedback/${saved.token}`;
      setIssuedLink(link);
      success('Feedback link issued');
    } catch (ex) { toastError(err(ex, 'Failed to issue feedback link')); }
  };

  const resolve = async (id) => {
    const resolution = resolutionDraft[id];
    if (!resolution || !resolution.trim()) return toastError('Enter a resolution note');
    try {
      await feedbackService.resolveComplaint(id, resolution);
      success('Complaint resolved');
      setResolutionDraft(p => ({ ...p, [id]: '' }));
      load();
    } catch (ex) { toastError(err(ex, 'Failed to resolve complaint')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Patient Feedback & Quality Complaints</h2>
        <p className="text-sm text-gray-600 mt-1">Issue single-use feedback links for completed encounters and track quality complaints raised from low ratings.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === t ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Issue Link' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <form onSubmit={submitIssue} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Issue Feedback Link</h3>
            <input required type="number" placeholder="Patient ID" className={InputCls} value={issueForm.patientId} onChange={e => setIssueForm(p => ({ ...p, patientId: e.target.value }))} />
            <select className={InputCls} value={issueForm.feedbackType} onChange={e => setIssueForm(p => ({ ...p, feedbackType: e.target.value }))}>
              <option value="OPD">OPD (completed appointment)</option>
              <option value="IPD">IPD (discharged admission)</option>
            </select>
            {issueForm.feedbackType === 'OPD' ? (
              <input required type="number" placeholder="Appointment ID" className={InputCls} value={issueForm.appointmentId} onChange={e => setIssueForm(p => ({ ...p, appointmentId: e.target.value }))} />
            ) : (
              <input required type="number" placeholder="Admission ID" className={InputCls} value={issueForm.admissionId} onChange={e => setIssueForm(p => ({ ...p, admissionId: e.target.value }))} />
            )}
            <button className="w-full bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold py-2 rounded-lg">Issue Link</button>
          </form>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Latest Link</h3>
            {issuedLink ? (
              <div className="text-xs space-y-2">
                <p className="text-gray-500">Share this single-use link with the patient. It expires in 7 days.</p>
                <div className="break-all bg-gray-50 border border-gray-200 rounded-lg p-2 font-mono">{issuedLink}</div>
              </div>
            ) : (
              <p className="text-xs text-gray-400 italic">No link issued yet.</p>
            )}
          </div>
        </div>
      )}

      {tab === 'Feedback' && (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h3 className="font-semibold text-gray-800 text-sm mb-2">Submitted Feedback ({feedback.length})</h3>
          <div className="space-y-1 max-h-[32rem] overflow-y-auto">
            {feedback.map(f => (
              <div key={f.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                <span>#{f.id} — Patient {f.patientId} — {f.feedbackType} — overall {f.overallRating}/5</span>
                <span className="text-gray-400">{f.submittedAt}</span>
              </div>
            ))}
            {feedback.length === 0 && <p className="text-xs text-gray-400 italic">No feedback submitted yet.</p>}
          </div>
        </div>
      )}

      {tab === 'Complaints' && (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h3 className="font-semibold text-gray-800 text-sm mb-2">Quality Complaints ({complaints.length})</h3>
          <div className="space-y-2 max-h-[32rem] overflow-y-auto">
            {complaints.map(c => (
              <div key={c.id} className="border border-gray-100 rounded-lg p-3 text-xs space-y-2">
                <div className="flex justify-between items-center">
                  <span className="font-semibold">#{c.id} — {c.category}</span>
                  <span className={`px-2 py-0.5 rounded-full font-semibold ${SEVERITY_BADGE[c.severity] || 'bg-gray-100 text-gray-600'}`}>{c.severity}</span>
                </div>
                <p className="text-gray-600">{c.description}</p>
                <div className="flex justify-between items-center">
                  <span className="text-gray-400">Status: {c.status}</span>
                  {c.status !== 'CLOSED' && (
                    <div className="flex gap-2">
                      <input
                        placeholder="Resolution note"
                        className="border border-gray-300 rounded-lg px-2 py-1 text-xs"
                        value={resolutionDraft[c.id] || ''}
                        onChange={e => setResolutionDraft(p => ({ ...p, [c.id]: e.target.value }))}
                      />
                      <button onClick={() => resolve(c.id)} className="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold px-3 py-1 rounded-lg whitespace-nowrap">Resolve</button>
                    </div>
                  )}
                  {c.status === 'CLOSED' && c.resolution && <span className="text-green-600 italic">Resolved: {c.resolution}</span>}
                </div>
              </div>
            ))}
            {complaints.length === 0 && <p className="text-xs text-gray-400 italic">No complaints raised.</p>}
          </div>
        </div>
      )}
    </div>
  );
}
