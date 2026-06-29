import { useState, useEffect } from 'react';
import doctorRoundService from '../../services/doctorRoundService';
import authService from '../../services/authService';

export default function DoctorRoundsPanel({ admissionId }) {
  const [rounds, setRounds] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  // Form Fields
  const [subjective, setSubjective] = useState('');
  const [objective, setObjective] = useState('');
  const [assessment, setAssessment] = useState('');
  const [plan, setPlan] = useState('');
  const [nextRoundAt, setNextRoundAt] = useState('');
  const [error, setError] = useState('');

  const isDoctor = authService.isDoctor();
  const user = authService.getCurrentUser() || {};
  const isAdmin = user.role === 'HOSPITAL_ADMIN';
  const canLog = isDoctor || isAdmin;

  useEffect(() => {
    loadRounds();
  }, [admissionId]);

  async function loadRounds() {
    setLoading(true);
    try {
      const res = await doctorRoundService.getRoundsHistory(admissionId);
      setRounds(res.data || []);
    } catch (e) {
      console.error('Failed to load rounds history', e);
    } finally {
      setLoading(false);
    }
  }

  const handleSave = async (e) => {
    e.preventDefault();
    if (!subjective.trim() && !objective.trim() && !assessment.trim() && !plan.trim()) {
      setError('Please fill in at least one SOAP section.');
      return;
    }

    setSaving(true);
    setError('');

    const payload = {
      subjective: subjective.trim() || null,
      objective: objective.trim() || null,
      assessment: assessment.trim() || null,
      plan: plan.trim() || null,
      nextRoundAt: nextRoundAt ? new Date(nextRoundAt).toISOString() : null,
    };

    try {
      await doctorRoundService.logRound(admissionId, payload);
      setIsOpen(false);
      // Reset Form
      setSubjective('');
      setObjective('');
      setAssessment('');
      setPlan('');
      setNextRoundAt('');
      loadRounds();
    } catch (err) {
      setError(err.response?.data || 'Failed to log round. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  // Find next round details
  const upcomingRounds = rounds
    .filter(r => r.nextRoundAt && new Date(r.nextRoundAt) > new Date())
    .sort((a, b) => new Date(a.nextRoundAt) - new Date(b.nextRoundAt));
  
  const overdueRounds = rounds
    .filter(r => r.nextRoundAt && new Date(r.nextRoundAt) <= new Date())
    .sort((a, b) => new Date(b.nextRoundAt) - new Date(a.nextRoundAt));

  const nextScheduledRound = upcomingRounds[0];
  const isRoundOverdue = overdueRounds.length > 0;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden shadow-sm p-6">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h3 className="text-lg font-bold text-gray-900">👨‍⚕️ Doctor Ward Rounds & SOAP Progress Notes</h3>
          <p className="text-xs text-gray-500 mt-1">Clinical reviews and plans recorded during patient bedside rounds</p>
        </div>
        {canLog && (
          <button
            onClick={() => setIsOpen(true)}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl shadow-sm transition-all active:scale-95 self-start sm:self-center"
          >
            + Log Round Note
          </button>
        )}
      </div>

      {/* Overdue Alert Bar */}
      {isRoundOverdue && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-800 rounded-xl px-4 py-3 text-sm flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span>⚠️</span>
            <div>
              <span className="font-bold">Next Round Overdue:</span> Last scheduled round was due on{' '}
              {new Date(overdueRounds[0].nextRoundAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}
            </div>
          </div>
        </div>
      )}

      {/* Upcoming Round Info */}
      {nextScheduledRound && !isRoundOverdue && (
        <div className="mb-4 bg-blue-50 border border-blue-150 text-blue-800 rounded-xl px-4 py-3 text-sm">
          <span className="font-semibold">🕒 Next Scheduled Round:</span>{' '}
          {new Date(nextScheduledRound.nextRoundAt).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
        </div>
      )}

      {/* Rounds History List */}
      {loading ? (
        <div className="text-center py-6 text-gray-400">Loading rounds history...</div>
      ) : rounds.length === 0 ? (
        <div className="text-center py-8 text-gray-400 italic">No rounds logged yet for this admission.</div>
      ) : (
        <div className="space-y-4">
          {rounds.map(round => (
            <div key={round.id} className="border border-gray-150 rounded-2xl p-5 hover:shadow-md transition-all bg-slate-50/30">
              <div className="flex justify-between items-center border-b border-gray-100 pb-3 mb-4">
                <div className="flex items-center gap-2">
                  <span className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 font-bold text-xs uppercase">
                    {round.doctorName ? round.doctorName.charAt(0) : 'D'}
                  </span>
                  <div>
                    <h4 className="font-bold text-gray-900 text-sm">Dr. {round.doctorName}</h4>
                    <p className="text-[10px] text-gray-400">
                      {new Date(round.roundDateTime).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
                    </p>
                  </div>
                </div>
                {round.nextRoundAt && (
                  <span className="text-[10px] sm:text-xs bg-slate-100 border border-slate-200 text-gray-600 px-2 py-0.5 rounded-full font-medium">
                    Next Due: {new Date(round.nextRoundAt).toLocaleDateString()} {new Date(round.nextRoundAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                )}
              </div>

              {/* SOAP Details Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm text-gray-800">
                {/* Subjective */}
                <div className="bg-white rounded-xl p-3 border border-gray-100 shadow-2xs">
                  <span className="block text-[10px] font-extrabold text-blue-600 uppercase tracking-wide mb-1">Subjective (S)</span>
                  <p className="whitespace-pre-line text-xs font-medium text-gray-700">{round.subjective || '—'}</p>
                </div>

                {/* Objective */}
                <div className="bg-white rounded-xl p-3 border border-gray-100 shadow-2xs">
                  <span className="block text-[10px] font-extrabold text-green-600 uppercase tracking-wide mb-1">Objective (O)</span>
                  <p className="whitespace-pre-line text-xs font-medium text-gray-700">{round.objective || '—'}</p>
                </div>

                {/* Assessment */}
                <div className="bg-white rounded-xl p-3 border border-gray-100 shadow-2xs">
                  <span className="block text-[10px] font-extrabold text-amber-600 uppercase tracking-wide mb-1">Assessment (A)</span>
                  <p className="whitespace-pre-line text-xs font-medium text-gray-700">{round.assessment || '—'}</p>
                </div>

                {/* Plan */}
                <div className="bg-white rounded-xl p-3 border border-gray-100 shadow-2xs">
                  <span className="block text-[10px] font-extrabold text-purple-600 uppercase tracking-wide mb-1">Plan (P)</span>
                  <p className="whitespace-pre-line text-xs font-medium text-gray-700">{round.plan || '—'}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal Dialog */}
      {isOpen && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[95vh] flex flex-col overflow-hidden border border-gray-100">
            {/* Header */}
            <div className="px-6 py-4 border-b border-gray-100 bg-gray-50/50 flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-gray-900">👨‍⚕️ Log Bedside Round & SOAP Progress Note</h3>
                <p className="text-xs text-gray-500 mt-0.5">Log clinical observation status and record planned updates</p>
              </div>
              <button
                onClick={() => setIsOpen(false)}
                className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors"
              >
                ✕
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleSave} className="flex-1 overflow-y-auto p-6 space-y-4">
              {/* SOAP input grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Subjective */}
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">
                    Subjective (Complaints, Symptoms)
                  </label>
                  <textarea
                    value={subjective}
                    onChange={e => setSubjective(e.target.value)}
                    rows={4}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                    placeholder="Patient describes dry cough, fatigue, sleeping well..."
                  />
                </div>

                {/* Objective */}
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">
                    Objective (Exam, Vitals Review)
                  </label>
                  <textarea
                    value={objective}
                    onChange={e => setObjective(e.target.value)}
                    rows={4}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-300"
                    placeholder="Chest clear on auscultation, Vitals reviewed..."
                  />
                </div>

                {/* Assessment */}
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">
                    Assessment (Clinical Impression)
                  </label>
                  <textarea
                    value={assessment}
                    onChange={e => setAssessment(e.target.value)}
                    rows={4}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-300"
                    placeholder="Mild pneumonia, showing signs of clinical improvement..."
                  />
                </div>

                {/* Plan */}
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5">
                    Plan (Dosing, Discharges, Procedures)
                  </label>
                  <textarea
                    value={plan}
                    onChange={e => setPlan(e.target.value)}
                    rows={4}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-300"
                    placeholder="Continue IV Ceftriaxone, shift to oral if stable tomorrow..."
                  />
                </div>
              </div>

              {/* Next Round Time */}
              <div className="max-w-xs">
                <label className="block text-xs font-semibold text-gray-700 mb-1.5">Next Scheduled Round Time (Optional)</label>
                <input
                  type="datetime-local"
                  value={nextRoundAt}
                  onChange={e => setNextRoundAt(e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                />
              </div>

              {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 rounded-xl px-4 py-2.5 text-xs">
                  ⚠ {error}
                </div>
              )}
            </form>

            {/* Footer */}
            <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3 bg-gray-50/30">
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="px-4 py-2 text-sm font-medium border border-gray-200 rounded-xl text-gray-600 hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-6 py-2 bg-blue-600 text-white text-sm font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-60 transition-all shadow-sm active:scale-95"
              >
                {saving ? 'Saving…' : 'Save Round Note'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
