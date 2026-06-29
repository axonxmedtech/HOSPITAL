import { useState, useEffect } from 'react';
import otService from '../../services/otService';
import hospitalService from '../../services/hospitalService';
import authService from '../../services/authService';

export default function OtWorkflowPanel({ admissionId }) {
  const [bookings, setBookings] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeBooking, setActiveBooking] = useState(null); // booking to view/edit checklists
  const [checklist, setChecklist] = useState(null);
  const [loadingChecklist, setLoadingChecklist] = useState(false);
  const [checklistNotes, setChecklistNotes] = useState('');
  const [signing, setSigning] = useState(false);

  // Scheduling Form State
  const [procedureName, setProcedureName] = useState('');
  const [scheduledDateTime, setScheduledDateTime] = useState('');
  const [surgeonId, setSurgeonId] = useState('');
  const [anesthetistName, setAnesthetistName] = useState('');
  const [otRoomNumber, setOtRoomNumber] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');

  const isDoctor = authService.isDoctor();
  const user = authService.getCurrentUser() || {};
  const isAdmin = user.role === 'HOSPITAL_ADMIN';
  const canSchedule = isDoctor || isAdmin;

  useEffect(() => {
    loadBookings();
    loadDoctors();
  }, [admissionId]);

  useEffect(() => {
    if (activeBooking) {
      loadChecklist(activeBooking.id);
    } else {
      setChecklist(null);
    }
  }, [activeBooking]);

  async function loadBookings() {
    setLoading(true);
    try {
      const res = await otService.getBookings(admissionId);
      setBookings(res.data || []);
    } catch (e) {
      console.error('Failed to load OT bookings', e);
    } finally {
      setLoading(false);
    }
  }

  async function loadDoctors() {
    try {
      const res = await hospitalService.getDoctors('', 0, 100);
      setDoctors(res.content || []);
    } catch (e) {
      console.error('Failed to load doctors list', e);
    }
  }

  async function loadChecklist(bookingId) {
    setLoadingChecklist(true);
    try {
      const res = await otService.getChecklist(admissionId, bookingId);
      setChecklist(res.data);
    } catch (e) {
      console.error('Failed to load checklist', e);
    } finally {
      setLoadingChecklist(false);
    }
  }

  const handleSchedule = async (e) => {
    e.preventDefault();
    if (!procedureName.trim() || !scheduledDateTime || !surgeonId || !otRoomNumber.trim()) {
      setError('Please fill in all required fields.');
      return;
    }

    setSaving(true);
    setError('');

    const payload = {
      procedureName: procedureName.trim(),
      scheduledDateTime: new Date(scheduledDateTime).toISOString(),
      surgeonId: Number(surgeonId),
      anesthetistName: anesthetistName.trim() || null,
      otRoomNumber: otRoomNumber.trim(),
      notes: notes.trim() || null,
    };

    try {
      await otService.scheduleBooking(admissionId, payload);
      setIsOpen(false);
      // Reset Form
      setProcedureName('');
      setScheduledDateTime('');
      setSurgeonId('');
      setAnesthetistName('');
      setOtRoomNumber('');
      setNotes('');
      loadBookings();
    } catch (err) {
      setError(err.response?.data || 'Failed to schedule booking. Check for room schedule conflicts.');
    } finally {
      setSaving(false);
    }
  };

  const handleSignChecklist = async (phase) => {
    setSigning(true);
    try {
      await otService.signChecklist(admissionId, activeBooking.id, {
        phase,
        notes: checklistNotes.trim() || null,
      });
      setChecklistNotes('');
      // Reload checklists and bookings
      await loadChecklist(activeBooking.id);
      await loadBookings();
      // Update activeBooking reference
      const updatedBooking = bookings.find(b => b.id === activeBooking.id);
      if (updatedBooking) {
        setActiveBooking(updatedBooking);
      } else {
        // Fallback reload
        const freshBookings = await otService.getBookings(admissionId);
        const fb = (freshBookings.data || []).find(b => b.id === activeBooking.id);
        if (fb) setActiveBooking(fb);
      }
    } catch (e) {
      alert(e.response?.data || 'Failed to sign checklist phase');
    } finally {
      setSigning(false);
    }
  };

  const handleStatusChange = async (bookingId, newStatus) => {
    try {
      await otService.updateStatus(admissionId, bookingId, newStatus);
      loadBookings();
      if (activeBooking && activeBooking.id === bookingId) {
        setActiveBooking(prev => ({ ...prev, status: newStatus }));
      }
    } catch (e) {
      alert(e.response?.data || 'Failed to update status');
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden shadow-sm p-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h3 className="text-lg font-bold text-gray-900">🏥 Operation Theatre (OT) & Surgeries</h3>
          <p className="text-xs text-gray-500 mt-1">Schedule surgical procedures and complete WHO Surgical Safety Checklists</p>
        </div>
        {canSchedule && (
          <button
            onClick={() => setIsOpen(true)}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl shadow-sm transition-all active:scale-95 self-start sm:self-center"
          >
            + Schedule Surgery
          </button>
        )}
      </div>

      {/* Bookings List */}
      {loading ? (
        <div className="text-center py-6 text-gray-400">Loading OT bookings...</div>
      ) : bookings.length === 0 ? (
        <div className="text-center py-8 text-gray-400 italic">No surgeries scheduled yet.</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* List Column */}
          <div className="lg:col-span-1 space-y-3 max-h-[500px] overflow-y-auto pr-1">
            {bookings.map(b => {
              const surgeon = doctors.find(d => d.id === b.surgeonId);
              const dateStr = new Date(b.scheduledDateTime).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' });
              const isSelected = activeBooking?.id === b.id;

              return (
                <div
                  key={b.id}
                  onClick={() => setActiveBooking(b)}
                  className={`p-4 border rounded-xl cursor-pointer transition-all ${
                    isSelected
                      ? 'border-blue-600 bg-blue-50/20 shadow-xs'
                      : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50/50'
                  }`}
                >
                  <div className="flex justify-between items-start gap-2">
                    <h4 className="font-bold text-gray-900 text-sm truncate">{b.procedureName}</h4>
                    <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase ${
                      b.status === 'SCHEDULED' ? 'bg-blue-100 text-blue-700' :
                      b.status === 'IN_PROGRESS' ? 'bg-amber-100 text-amber-700' :
                      b.status === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                      'bg-gray-100 text-gray-600'
                    }`}>
                      {b.status}
                    </span>
                  </div>
                  <div className="mt-2 space-y-0.5 text-xs text-gray-500">
                    <p>🕒 {dateStr}</p>
                    <p>👨‍⚕️ Surgeon: {surgeon?.name || `Dr. (ID: ${b.surgeonId})`}</p>
                    <p>🚪 Room: {b.otRoomNumber}</p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Checklist Column */}
          <div className="lg:col-span-2 border border-gray-200 rounded-xl p-5 bg-slate-50/20">
            {activeBooking ? (
              <div>
                {/* Active Surgery Header */}
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 border-b border-gray-200 pb-3 mb-4">
                  <div>
                    <h4 className="font-bold text-gray-950 text-base">{activeBooking.procedureName}</h4>
                    <p className="text-xs text-gray-500">Scheduled for room {activeBooking.otRoomNumber}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <select
                      value={activeBooking.status}
                      onChange={e => handleStatusChange(activeBooking.id, e.target.value)}
                      className="border border-gray-300 rounded-lg text-xs font-semibold px-2 py-1.5 bg-white"
                    >
                      <option value="SCHEDULED">Scheduled</option>
                      <option value="IN_PROGRESS">In Progress</option>
                      <option value="COMPLETED">Completed</option>
                      <option value="CANCELLED">Cancelled</option>
                    </select>
                  </div>
                </div>

                {loadingChecklist ? (
                  <div className="text-center py-10 text-gray-400">Loading surgical checklist...</div>
                ) : checklist ? (
                  <div className="space-y-6">
                    {/* WHO Checklist grid */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      {/* Sign In */}
                      <div className={`p-4 rounded-xl border ${
                        checklist.signInCompleted ? 'bg-green-50/30 border-green-200' : 'bg-white border-gray-200'
                      }`}>
                        <div className="flex items-center justify-between mb-2">
                          <h5 className="font-bold text-gray-900 text-xs uppercase tracking-wide">1. Sign In</h5>
                          {checklist.signInCompleted && <span className="text-green-600 font-bold">✓</span>}
                        </div>
                        <p className="text-[10px] text-gray-500 mb-3">Before induction of anesthesia. Nurse & Anesthetist audit check.</p>
                        
                        {checklist.signInCompleted ? (
                          <div className="text-[10px] text-gray-600 bg-green-50 border border-green-100 rounded p-2">
                            <p className="font-semibold text-green-800">Completed</p>
                            <p className="truncate">By: {checklist.signInBy}</p>
                            <p>At: {new Date(checklist.signInAt).toLocaleString([], { timeStyle: 'short', dateStyle: 'short' })}</p>
                            {checklist.signInNotes && <p className="italic mt-1 border-t border-green-150 pt-1">Notes: "{checklist.signInNotes}"</p>}
                          </div>
                        ) : (
                          <div>
                            <ul className="text-[10px] space-y-1 text-gray-500 mb-3 font-medium">
                              <li>🔲 Patient identity & consent</li>
                              <li>🔲 Surgical site marked</li>
                              <li>🔲 Anesthesia safety check</li>
                              <li>🔲 Pulse oximeter active</li>
                            </ul>
                            <button
                              onClick={() => handleSignChecklist('SIGN_IN')}
                              disabled={signing}
                              className="w-full py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-[10px] rounded-lg shadow-sm transition-all"
                            >
                              Sign Off Phase
                            </button>
                          </div>
                        )}
                      </div>

                      {/* Time Out */}
                      <div className={`p-4 rounded-xl border ${
                        checklist.timeOutCompleted ? 'bg-green-50/30 border-green-200' : 'bg-white border-gray-200'
                      }`}>
                        <div className="flex items-center justify-between mb-2">
                          <h5 className="font-bold text-gray-900 text-xs uppercase tracking-wide">2. Time Out</h5>
                          {checklist.timeOutCompleted && <span className="text-green-600 font-bold">✓</span>}
                        </div>
                        <p className="text-[10px] text-gray-500 mb-3">Before skin incision. Whole team verification pause.</p>
                        
                        {checklist.timeOutCompleted ? (
                          <div className="text-[10px] text-gray-600 bg-green-50 border border-green-100 rounded p-2">
                            <p className="font-semibold text-green-800">Completed</p>
                            <p className="truncate">By: {checklist.timeOutBy}</p>
                            <p>At: {new Date(checklist.timeOutAt).toLocaleString([], { timeStyle: 'short', dateStyle: 'short' })}</p>
                            {checklist.timeOutNotes && <p className="italic mt-1 border-t border-green-150 pt-1">Notes: "{checklist.timeOutNotes}"</p>}
                          </div>
                        ) : (
                          <div>
                            <ul className="text-[10px] space-y-1 text-gray-500 mb-3 font-medium">
                              <li>🔲 Introduce team members</li>
                              <li>🔲 Confirm patient, site, procedure</li>
                              <li>🔲 Anticipated critical events</li>
                              <li>🔲 Antibiotic prophylaxis check</li>
                            </ul>
                            <button
                              onClick={() => handleSignChecklist('TIME_OUT')}
                              disabled={signing || !checklist.signInCompleted}
                              className="w-full py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-[10px] rounded-lg shadow-sm disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                              title={!checklist.signInCompleted ? 'Complete Sign In first' : ''}
                            >
                              Sign Off Phase
                            </button>
                          </div>
                        )}
                      </div>

                      {/* Sign Out */}
                      <div className={`p-4 rounded-xl border ${
                        checklist.signOutCompleted ? 'bg-green-50/30 border-green-200' : 'bg-white border-gray-200'
                      }`}>
                        <div className="flex items-center justify-between mb-2">
                          <h5 className="font-bold text-gray-900 text-xs uppercase tracking-wide">3. Sign Out</h5>
                          {checklist.signOutCompleted && <span className="text-green-600 font-bold">✓</span>}
                        </div>
                        <p className="text-[10px] text-gray-500 mb-3">Before patient leaves OT. Instruments & counts audit.</p>
                        
                        {checklist.signOutCompleted ? (
                          <div className="text-[10px] text-gray-600 bg-green-50 border border-green-100 rounded p-2">
                            <p className="font-semibold text-green-800">Completed</p>
                            <p className="truncate">By: {checklist.signOutBy}</p>
                            <p>At: {new Date(checklist.signOutAt).toLocaleString([], { timeStyle: 'short', dateStyle: 'short' })}</p>
                            {checklist.signOutNotes && <p className="italic mt-1 border-t border-green-150 pt-1">Notes: "{checklist.signOutNotes}"</p>}
                          </div>
                        ) : (
                          <div>
                            <ul className="text-[10px] space-y-1 text-gray-500 mb-3 font-medium">
                              <li>🔲 Verbal name of procedure</li>
                              <li>🔲 Swab & needle counts correct</li>
                              <li>🔲 Specimen labeled verified</li>
                              <li>🔲 Post-op concerns noted</li>
                            </ul>
                            <button
                              onClick={() => handleSignChecklist('SIGN_OUT')}
                              disabled={signing || !checklist.timeOutCompleted}
                              className="w-full py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-[10px] rounded-lg shadow-sm disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                              title={!checklist.timeOutCompleted ? 'Complete Time Out first' : ''}
                            >
                              Sign Off Phase
                            </button>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Sign-off notes capture */}
                    {(!checklist.signInCompleted || !checklist.timeOutCompleted || !checklist.signOutCompleted) && (
                      <div className="bg-white rounded-xl border border-gray-150 p-4">
                        <label className="block text-xs font-semibold text-gray-700 mb-1.5">Checklist remarks / notes (Optional)</label>
                        <input
                          type="text"
                          value={checklistNotes}
                          onChange={e => setChecklistNotes(e.target.value)}
                          className="w-full border border-gray-200 rounded-lg px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-blue-300"
                          placeholder="e.g. Swab count confirmed, consent form matched..."
                        />
                      </div>
                    )}
                  </div>
                ) : null}
              </div>
            ) : (
              <div className="text-center py-20 text-gray-400 italic">Select a scheduled surgery from the list to view and complete the WHO Surgical Safety Checklist.</div>
            )}
          </div>
        </div>
      )}

      {/* Schedule Modal */}
      {isOpen && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[95vh] flex flex-col overflow-hidden border border-gray-100">
            {/* Header */}
            <div className="px-6 py-4 border-b border-gray-100 bg-gray-50/50 flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-gray-900">🏥 Schedule Surgical Procedure</h3>
                <p className="text-xs text-gray-500 mt-0.5">Book an OT room and assign the clinical surgery team</p>
              </div>
              <button
                onClick={() => setIsOpen(false)}
                className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors"
              >
                ✕
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleSchedule} className="flex-1 overflow-y-auto p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1.5">Procedure Name *</label>
                <input
                  type="text"
                  value={procedureName}
                  onChange={e => setProcedureName(e.target.value)}
                  className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                  placeholder="e.g. Laparoscopic Appendectomy"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1.5">Scheduled Date & Time *</label>
                  <input
                    type="datetime-local"
                    value={scheduledDateTime}
                    onChange={e => setScheduledDateTime(e.target.value)}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1.5">OT Room Name / Number *</label>
                  <input
                    type="text"
                    value={otRoomNumber}
                    onChange={e => setOtRoomNumber(e.target.value)}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                    placeholder="e.g. OT-1, OT-2"
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1.5">Lead Surgeon *</label>
                  <select
                    value={surgeonId}
                    onChange={e => setSurgeonId(e.target.value)}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-300"
                    required
                  >
                    <option value="">Select Surgeon</option>
                    {doctors.map(d => (
                      <option key={d.id} value={d.id}>{d.name} ({d.specialization})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1.5">Anesthetist Name</label>
                  <input
                    type="text"
                    value={anesthetistName}
                    onChange={e => setAnesthetistName(e.target.value)}
                    className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                    placeholder="e.g. Dr. Ana Estesia"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1.5">Scheduling Notes</label>
                <textarea
                  value={notes}
                  onChange={e => setNotes(e.target.value)}
                  rows={3}
                  className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
                  placeholder="e.g. High risk patient, require extra cardiac monitor..."
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
                onClick={handleSchedule}
                disabled={saving}
                className="px-6 py-2 bg-blue-600 text-white text-sm font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-60 transition-all shadow-sm active:scale-95"
              >
                {saving ? 'Scheduling…' : 'Schedule Surgery'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
