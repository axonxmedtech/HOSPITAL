import React, { useState, useEffect, useCallback } from 'react';
import bloodBankService from '../../services/bloodBankService';
import { useToast } from '../../context/ToastContext';

const TABS = ['Donors', 'Units', 'Requests', 'Cross-Match & Issue'];
const COMPONENTS = ['WHOLE_BLOOD', 'PRBC', 'FFP', 'PLATELETS'];
const GROUPS = ['A', 'B', 'AB', 'O'];

const UNIT_BADGE = {
  AVAILABLE: 'bg-green-100 text-green-700',
  RESERVED: 'bg-amber-100 text-amber-700',
  ISSUED: 'bg-blue-100 text-blue-700',
  EXPIRED: 'bg-gray-200 text-gray-600',
  QUARANTINED: 'bg-red-100 text-red-700',
};

export default function BloodBankView() {
  const { success, error: toastError } = useToast();
  const [tab, setTab] = useState('Donors');

  const [donors, setDonors] = useState([]);
  const [units, setUnits] = useState([]);
  const [requests, setRequests] = useState([]);

  const [donorForm, setDonorForm] = useState({ name: '', phone: '', bloodGroup: 'O', rhType: 'POSITIVE' });
  const [unitForm, setUnitForm] = useState({ donorId: '', componentType: 'PRBC', bloodGroup: 'O', rhType: 'POSITIVE', hivResult: 'NON_REACTIVE', hbsagResult: 'NON_REACTIVE', malariaResult: 'NON_REACTIVE', expiryDate: '' });
  const [requestForm, setRequestForm] = useState({ patientId: '', department: '', component: 'PRBC', unitsRequested: 1, priority: 'ROUTINE' });
  const [xmForm, setXmForm] = useState({ requestId: '', bloodUnitId: '', result: 'COMPATIBLE' });
  const [issuePatientId, setIssuePatientId] = useState('');

  const load = useCallback(async () => {
    try {
      const [d, u, r] = await Promise.all([
        bloodBankService.getDonors(),
        bloodBankService.getAvailableUnits(),
        bloodBankService.getRequests(),
      ]);
      setDonors(Array.isArray(d?.data) ? d.data : (Array.isArray(d) ? d : []));
      setUnits(Array.isArray(u?.data) ? u.data : (Array.isArray(u) ? u : []));
      setRequests(Array.isArray(r?.data) ? r.data : (Array.isArray(r) ? r : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const submitDonor = async (e) => {
    e.preventDefault();
    try {
      await bloodBankService.registerDonor(donorForm);
      success('Donor registered');
      setDonorForm({ name: '', phone: '', bloodGroup: 'O', rhType: 'POSITIVE' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to register donor')); }
  };

  const submitUnit = async (e) => {
    e.preventDefault();
    try {
      const res = await bloodBankService.addUnit({ ...unitForm, donorId: Number(unitForm.donorId) });
      const saved = res?.data || res;
      success(`Unit added (${saved?.status || 'saved'})`);
      load();
    } catch (ex) { toastError(err(ex, 'Failed to add unit')); }
  };

  const submitRequest = async (e) => {
    e.preventDefault();
    try {
      await bloodBankService.requestBlood({ ...requestForm, patientId: Number(requestForm.patientId), unitsRequested: Number(requestForm.unitsRequested) });
      success('Blood request created');
      setRequestForm({ patientId: '', department: '', component: 'PRBC', unitsRequested: 1, priority: 'ROUTINE' });
      load();
    } catch (ex) { toastError(err(ex, 'Failed to create request')); }
  };

  const submitCrossMatch = async (e) => {
    e.preventDefault();
    try {
      await bloodBankService.performCrossMatch({ ...xmForm, requestId: Number(xmForm.requestId), bloodUnitId: Number(xmForm.bloodUnitId) });
      success('Cross-match recorded');
      load();
    } catch (ex) { toastError(err(ex, 'Cross-match failed')); }
  };

  const issue = async () => {
    if (!xmForm.bloodUnitId || !issuePatientId) return toastError('Select a unit and enter the patient ID');
    try {
      await bloodBankService.issueUnit(Number(xmForm.bloodUnitId), Number(issuePatientId));
      success('Unit issued');
      load();
    } catch (ex) { toastError(err(ex, 'Issue blocked')); }
  };

  const InputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-400';

  return (
    <div className="p-6 space-y-4 bg-gray-50/50 min-h-screen">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">🩸 Blood Bank (BBTMS)</h2>
        <p className="text-sm text-gray-600 mt-1">Donor registry, inventory, requests and the cross-match → issue → transfusion safety chain.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === t ? 'border-red-600 text-red-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'Donors' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitDonor} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Register Donor</h3>
            <input required placeholder="Name" className={InputCls} value={donorForm.name} onChange={e => setDonorForm(p => ({ ...p, name: e.target.value }))} />
            <input placeholder="Phone" className={InputCls} value={donorForm.phone} onChange={e => setDonorForm(p => ({ ...p, phone: e.target.value }))} />
            <div className="grid grid-cols-2 gap-2">
              <select className={InputCls} value={donorForm.bloodGroup} onChange={e => setDonorForm(p => ({ ...p, bloodGroup: e.target.value }))}>
                {GROUPS.map(g => <option key={g} value={g}>{g}</option>)}
              </select>
              <select className={InputCls} value={donorForm.rhType} onChange={e => setDonorForm(p => ({ ...p, rhType: e.target.value }))}>
                <option value="POSITIVE">POSITIVE</option>
                <option value="NEGATIVE">NEGATIVE</option>
              </select>
            </div>
            <button className="w-full bg-red-600 hover:bg-red-700 text-white text-sm font-semibold py-2 rounded-lg">Register</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Donors ({donors.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {donors.map(d => (
                <div key={d.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>{d.donorNumber} — {d.name}</span>
                  <span className="font-semibold">{d.bloodGroup} {d.rhType === 'POSITIVE' ? '+' : '-'}</span>
                </div>
              ))}
              {donors.length === 0 && <p className="text-xs text-gray-400 italic">No donors registered.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Units' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitUnit} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">Add Unit (Bag)</h3>
            <select required className={InputCls} value={unitForm.donorId} onChange={e => setUnitForm(p => ({ ...p, donorId: e.target.value }))}>
              <option value="">Select donor…</option>
              {donors.map(d => <option key={d.id} value={d.id}>{d.donorNumber} — {d.name}</option>)}
            </select>
            <select className={InputCls} value={unitForm.componentType} onChange={e => setUnitForm(p => ({ ...p, componentType: e.target.value }))}>
              {COMPONENTS.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <div className="grid grid-cols-2 gap-2">
              <select className={InputCls} value={unitForm.bloodGroup} onChange={e => setUnitForm(p => ({ ...p, bloodGroup: e.target.value }))}>
                {GROUPS.map(g => <option key={g} value={g}>{g}</option>)}
              </select>
              <select className={InputCls} value={unitForm.rhType} onChange={e => setUnitForm(p => ({ ...p, rhType: e.target.value }))}>
                <option value="POSITIVE">POSITIVE</option>
                <option value="NEGATIVE">NEGATIVE</option>
              </select>
            </div>
            <div className="grid grid-cols-3 gap-2">
              {['hivResult', 'hbsagResult', 'malariaResult'].map(f => (
                <select key={f} className={InputCls} value={unitForm[f]} onChange={e => setUnitForm(p => ({ ...p, [f]: e.target.value }))}>
                  <option value="NON_REACTIVE">Neg</option>
                  <option value="REACTIVE">Pos</option>
                </select>
              ))}
            </div>
            <input required type="date" className={InputCls} value={unitForm.expiryDate} onChange={e => setUnitForm(p => ({ ...p, expiryDate: e.target.value }))} />
            <button className="w-full bg-red-600 hover:bg-red-700 text-white text-sm font-semibold py-2 rounded-lg">Add Unit</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Available Inventory ({units.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {units.map(u => (
                <div key={u.id} className="flex justify-between items-center text-xs border-b border-gray-100 py-1.5">
                  <span>{u.unitNumber} — {u.componentType} {u.bloodGroup}{u.rhType === 'POSITIVE' ? '+' : '-'}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400">exp {u.expiryDate}</span>
                    <span className={`px-2 py-0.5 rounded-full font-semibold ${UNIT_BADGE[u.status] || 'bg-gray-100 text-gray-600'}`}>{u.status}</span>
                  </div>
                </div>
              ))}
              {units.length === 0 && <p className="text-xs text-gray-400 italic">No available units.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Requests' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <form onSubmit={submitRequest} className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
            <h3 className="font-semibold text-gray-800 text-sm">New Blood Request</h3>
            <input required type="number" placeholder="Patient ID" className={InputCls} value={requestForm.patientId} onChange={e => setRequestForm(p => ({ ...p, patientId: e.target.value }))} />
            <input required placeholder="Department (e.g. OT, ICU)" className={InputCls} value={requestForm.department} onChange={e => setRequestForm(p => ({ ...p, department: e.target.value }))} />
            <select className={InputCls} value={requestForm.component} onChange={e => setRequestForm(p => ({ ...p, component: e.target.value }))}>
              {COMPONENTS.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <div className="grid grid-cols-2 gap-2">
              <input required type="number" min="1" placeholder="Units" className={InputCls} value={requestForm.unitsRequested} onChange={e => setRequestForm(p => ({ ...p, unitsRequested: e.target.value }))} />
              <select className={InputCls} value={requestForm.priority} onChange={e => setRequestForm(p => ({ ...p, priority: e.target.value }))}>
                <option value="ROUTINE">ROUTINE</option>
                <option value="URGENT">URGENT</option>
                <option value="STAT">STAT</option>
              </select>
            </div>
            <button className="w-full bg-red-600 hover:bg-red-700 text-white text-sm font-semibold py-2 rounded-lg">Submit Request</button>
          </form>
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="font-semibold text-gray-800 text-sm mb-2">Requests ({requests.length})</h3>
            <div className="space-y-1 max-h-96 overflow-y-auto">
              {requests.map(r => (
                <div key={r.id} className="flex justify-between text-xs border-b border-gray-100 py-1.5">
                  <span>#{r.id} — Patient {r.patientId} — {r.component} × {r.unitsRequested} ({r.department})</span>
                  <span className="font-semibold">{r.status}</span>
                </div>
              ))}
              {requests.length === 0 && <p className="text-xs text-gray-400 italic">No requests yet.</p>}
            </div>
          </div>
        </div>
      )}

      {tab === 'Cross-Match & Issue' && (
        <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-4">
          <h3 className="font-semibold text-gray-800 text-sm">Cross-Match (BR-4 gate: issue requires COMPATIBLE)</h3>
          <form onSubmit={submitCrossMatch} className="grid grid-cols-1 sm:grid-cols-4 gap-2">
            <select required className={InputCls} value={xmForm.requestId} onChange={e => setXmForm(p => ({ ...p, requestId: e.target.value }))}>
              <option value="">Request…</option>
              {requests.filter(r => r.status !== 'FILLED' && r.status !== 'CANCELLED').map(r => (
                <option key={r.id} value={r.id}>#{r.id} pt {r.patientId} ({r.component})</option>
              ))}
            </select>
            <select required className={InputCls} value={xmForm.bloodUnitId} onChange={e => setXmForm(p => ({ ...p, bloodUnitId: e.target.value }))}>
              <option value="">Unit…</option>
              {units.filter(u => u.status === 'AVAILABLE').map(u => (
                <option key={u.id} value={u.id}>{u.unitNumber} ({u.bloodGroup}{u.rhType === 'POSITIVE' ? '+' : '-'})</option>
              ))}
            </select>
            <select className={InputCls} value={xmForm.result} onChange={e => setXmForm(p => ({ ...p, result: e.target.value }))}>
              <option value="COMPATIBLE">COMPATIBLE</option>
              <option value="INCOMPATIBLE">INCOMPATIBLE</option>
            </select>
            <button className="bg-red-600 hover:bg-red-700 text-white text-sm font-semibold py-2 rounded-lg">Record Cross-Match</button>
          </form>

          <hr className="border-gray-100" />

          <h3 className="font-semibold text-gray-800 text-sm">Issue Unit</h3>
          <p className="text-xs text-gray-500">Uses the unit and patient selected above — a COMPATIBLE cross-match must exist for that exact pairing.</p>
          <div className="flex gap-2">
            <input type="number" placeholder="Patient ID to issue to" className={InputCls} value={issuePatientId} onChange={e => setIssuePatientId(e.target.value)} />
            <button onClick={issue} className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold px-4 py-2 rounded-lg whitespace-nowrap">Issue Unit</button>
          </div>
        </div>
      )}
    </div>
  );
}
