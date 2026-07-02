import { useState, useEffect, useCallback } from 'react';
import { getLabOrders, placeLabOrder, cancelLabOrder, verifyLabResult, releaseLabResult } from '../../services/labService';
import authService from '../../services/authService';
import masterDataService from '../../services/masterDataService';
import SearchableSelect from '../SearchableSelect';

// Status display config
const STATUS_CONFIG = {
  ORDERED: { badge: 'bg-amber-100 text-amber-700 border border-amber-200', label: 'Ordered', icon: '⏳' },
  SAMPLE_COLLECTED: { badge: 'bg-blue-100 text-blue-700 border border-blue-200', label: 'Sample Collected', icon: '🧪' },
  COMPLETED: { badge: 'bg-emerald-100 text-emerald-700 border border-emerald-200', label: 'Pending Verification', icon: '✓' },
  VERIFIED: { badge: 'bg-teal-100 text-teal-700 border border-teal-200', label: 'Verified', icon: '🩺' },
  RELEASED: { badge: 'bg-purple-100 text-purple-700 border border-purple-200', label: 'Released', icon: '📤' },
  CANCELLED: { badge: 'bg-gray-100 text-gray-500 border border-gray-200', label: 'Cancelled', icon: '✕' },
};

/**
 * LabResultsPanel — Reusable panel for showing lab orders + results in IPD/OPD details.
 *
 * Props:
 *   ipdAdmissionId  — filter by IPD admission
 *   patientId       — filter by patient (for history view)
 *   canOrder        — show "New Lab Order" form (DOCTOR/HOSPITAL_ADMIN only)
 */
export default function LabResultsPanel({ ipdAdmissionId, patientId, canOrder = false, openTrigger = 0 }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showOrderForm, setShowOrderForm] = useState(false);
  const [expanded, setExpanded] = useState({});
  const [newOrder, setNewOrder] = useState({ testName: '', labTestMasterId: null, priority: 'ROUTINE', notes: '' });
  const [ordering, setOrdering] = useState(false);
  const [orderError, setOrderError] = useState('');

  const user = authService.getCurrentUser();

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params = ipdAdmissionId ? { ipdAdmissionId } : { patientId };
      const res = await getLabOrders(params);
      const content = res.data?.content ?? res.data ?? [];
      setOrders(Array.isArray(content) ? content : []);
    } catch (err) {
      console.error('Failed to load lab orders', err);
    } finally {
      setLoading(false);
    }
  }, [ipdAdmissionId, patientId]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  useEffect(() => {
    if (openTrigger > 0 && canOrder) setShowOrderForm(true);
  }, [openTrigger, canOrder]);

  const handleOrder = async (e) => {
    e.preventDefault();
    setOrdering(true);
    setOrderError('');
    try {
      await placeLabOrder({ ...newOrder, patientId, ipdAdmissionId });
      setNewOrder({ testName: '', labTestMasterId: null, priority: 'ROUTINE', notes: '' });
      setShowOrderForm(false);
      fetchOrders();
    } catch (err) {
      const rawError = err.response?.data;
      const errorMsg = typeof rawError === 'object'
        ? (rawError.error || rawError.message || JSON.stringify(rawError))
        : (rawError || 'Failed to place order');
      setOrderError(errorMsg);
    } finally {
      setOrdering(false);
    }
  };

  const handleCancel = async (publicId) => {
    if (!window.confirm('Cancel this lab order? This cannot be undone.')) return;
    try {
      await cancelLabOrder(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Cannot cancel this order');
    }
  };

  const handleVerify = async (publicId) => {
    try {
      await verifyLabResult(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Only a pathologist may verify this result');
    }
  };

  const handleRelease = async (publicId) => {
    try {
      await releaseLabResult(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Cannot release this result yet');
    }
  };

  const toggleExpand = (publicId) => {
    setExpanded(prev => ({ ...prev, [publicId]: !prev[publicId] }));
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-lg">🔬</div>
          <h3 className="text-base font-bold text-gray-900">Lab Orders & Results</h3>
          {orders.length > 0 && (
            <span className="bg-blue-100 text-blue-700 text-xs font-semibold px-2.5 py-1 rounded-full">
              {orders.length}
            </span>
          )}
        </div>
        {canOrder && (
          <button
            onClick={() => setShowOrderForm(!showOrderForm)}
            className={`px-4 py-2 text-sm font-semibold rounded-xl transition-all border ${
              showOrderForm
                ? 'bg-gray-100 text-gray-600 border-gray-200'
                : 'bg-blue-600 text-white border-blue-600 hover:bg-blue-700 shadow-sm'
            }`}
          >
            {showOrderForm ? '✕ Cancel' : '+ New Lab Order'}
          </button>
        )}
      </div>

      {/* New Order Form */}
      {showOrderForm && (
        <form
          onSubmit={handleOrder}
          className="bg-blue-50 border border-blue-200 rounded-2xl p-4 space-y-3"
        >
          <h4 className="text-sm font-semibold text-blue-900">Place Lab Order</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Test Name *</label>
              <SearchableSelect
                onSearch={masterDataService.searchLabTests}
                onSelect={item => setNewOrder(p => ({
                  ...p,
                  testName: item.testName,
                  labTestMasterId: item.id
                }))}
                getLabel={item => `${item.testName}${item.testCode ? ' (' + item.testCode + ')' : ''}`}
                placeholder="Search lab test (e.g. CBC, LFT)"
                value={newOrder.testName || ''}
                hint={newOrder.labTestMasterId ? `Sample: ${newOrder.sampleType || ''}` : ''}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Priority</label>
              <select
                value={newOrder.priority}
                onChange={e => setNewOrder(p => ({ ...p, priority: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 bg-white"
              >
                <option value="ROUTINE">Routine</option>
                <option value="URGENT">🔴 Urgent</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Clinical Notes</label>
            <input
              value={newOrder.notes}
              onChange={e => setNewOrder(p => ({ ...p, notes: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 bg-white"
              placeholder="Reason for test, clinical context..."
            />
          </div>
          {orderError && (
            <div className="text-xs text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
              ⚠ {orderError}
            </div>
          )}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={ordering}
              className="px-4 py-2 text-sm font-semibold bg-blue-600 text-white rounded-xl hover:bg-blue-700 disabled:opacity-60 transition-all shadow-sm"
            >
              {ordering ? '⏳ Placing…' : '✓ Place Order'}
            </button>
            <button
              type="button"
              onClick={() => { setShowOrderForm(false); setOrderError(''); }}
              className="px-4 py-2 text-sm font-medium border border-gray-200 rounded-xl text-gray-600 hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Order list */}
      {loading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-14 bg-gray-100 rounded-xl animate-pulse" />
          ))}
        </div>
      ) : orders.length === 0 ? (
        <div className="text-center py-8 text-gray-400 bg-gray-50 rounded-xl border border-dashed border-gray-200">
          <p className="text-sm">No lab orders yet.</p>
          {canOrder && (
            <p className="text-xs mt-1">Click "+ New Lab Order" to place the first order.</p>
          )}
        </div>
      ) : (
        <div className="space-y-2">
          {orders.map((item) => {
            const order = item.order || item;
            const result = item.result;
            const isExpanded = expanded[order.publicId];
            const cfg = STATUS_CONFIG[order.status] || STATUS_CONFIG.ORDERED;

            let parsedParams = [];
            if (result?.parameters) {
              try { parsedParams = JSON.parse(result.parameters); } catch (_) {}
            }

            return (
              <div key={order.publicId} className="border border-gray-200 rounded-xl overflow-hidden bg-white shadow-sm hover:shadow-md transition-shadow">
                {/* Row header (always visible) */}
                <div
                  className="flex items-center justify-between px-4 py-3 cursor-pointer"
                  onClick={() => toggleExpand(order.publicId)}
                >
                  <div className="flex items-center gap-3 flex-1 min-w-0">
                    <span className="text-base">{cfg.icon}</span>
                    <span className="font-semibold text-sm text-gray-900 truncate">{order.testName}</span>
                    <span className={`text-xs px-2.5 py-0.5 rounded-full font-medium ${cfg.badge}`}>
                      {cfg.label}
                    </span>
                    {order.priority === 'URGENT' && (
                      <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-red-100 text-red-600 border border-red-200">
                        URGENT
                      </span>
                    )}
                    {result?.isAbnormal && (
                      <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-orange-100 text-orange-700 border border-orange-200">
                        ⚠ Abnormal
                      </span>
                    )}
                    {result?.isCritical && (
                      <span className="text-xs px-2 py-0.5 rounded-full font-bold bg-red-700 text-white border border-red-800">
                        🚨 CRITICAL
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <span className="text-xs text-gray-400 hidden sm:block">
                      {new Date(order.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' })}
                    </span>
                    {canOrder && order.status === 'ORDERED' && (
                      <button
                        onClick={e => { e.stopPropagation(); handleCancel(order.publicId); }}
                        className="text-xs text-red-500 hover:text-red-700 px-2 py-0.5 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                      >
                        Cancel
                      </button>
                    )}
                    <span className="text-gray-400 text-sm">{isExpanded ? '▲' : '▼'}</span>
                  </div>
                </div>

                {/* Expanded detail */}
                {isExpanded && (
                  <div className="border-t border-gray-100 px-4 py-3 bg-gray-50/50 text-sm space-y-2">
                    {order.notes && (
                      <p className="text-gray-600">
                        <span className="font-medium">Clinical Note:</span> {order.notes}
                      </p>
                    )}
                    <p className="text-xs text-gray-400">
                      Ordered by: {order.orderedByName}
                      {' · '}
                      {new Date(order.createdAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                    </p>
                    {order.sampleCollectedAt && (
                      <p className="text-xs text-gray-400">
                        Sample collected: {new Date(order.sampleCollectedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                        {order.sampleCollectedByName && ` by ${order.sampleCollectedByName}`}
                      </p>
                    )}

                    {/* Result block */}
                    {result ? (
                      <div className={`rounded-xl p-3 mt-2 ${result.isAbnormal ? 'bg-orange-50 border border-orange-100' : 'bg-emerald-50 border border-emerald-100'}`}>
                        <p className={`text-xs font-semibold mb-2 ${result.isAbnormal ? 'text-orange-700' : 'text-emerald-700'}`}>
                          Results — {new Date(result.resultedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                          {result.verifiedByName && ` · Verified by ${result.verifiedByName}`}
                        </p>

                        {parsedParams.length > 0 && (
                          <table className="w-full text-xs border border-gray-200 rounded-lg overflow-hidden mb-2">
                            <thead className="bg-white/70">
                              <tr>
                                {['Parameter', 'Value', 'Unit', 'Ref Range', 'Flag'].map(h => (
                                  <th key={h} className="px-2 py-1.5 text-left font-semibold text-gray-500">{h}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {parsedParams.map((p, i) => (
                                <tr key={i} className={`border-t border-gray-100 ${p.flag && p.flag !== 'Normal' ? 'bg-amber-50/60' : 'bg-white/50'}`}>
                                  <td className="px-2 py-1.5 font-medium text-gray-700">{p.name}</td>
                                  <td className="px-2 py-1.5 font-bold text-gray-900">{p.value}</td>
                                  <td className="px-2 py-1.5 text-gray-500">{p.unit}</td>
                                  <td className="px-2 py-1.5 text-gray-500">{p.referenceRange}</td>
                                  <td className={`px-2 py-1.5 font-semibold ${
                                    p.flag === 'Critical' ? 'text-red-700' :
                                    p.flag === 'High' ? 'text-orange-700' :
                                    p.flag === 'Low' ? 'text-amber-700' :
                                    'text-emerald-700'
                                  }`}>
                                    {p.flag}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        )}

                        {result.resultSummary && (
                          <p className="text-gray-600 text-xs">
                            <span className="font-medium">Summary:</span> {result.resultSummary}
                          </p>
                        )}

                        {result.releasedAt && (
                          <p className="text-xs text-purple-700 mt-2">
                            📤 Released by {result.releasedByName} on {new Date(result.releasedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                          </p>
                        )}

                        {/* Pathologist sign-off actions (BR-4/BR-6) */}
                        {(user?.role === 'DOCTOR' || user?.role === 'HOSPITAL_ADMIN') && order.status === 'COMPLETED' && (
                          <button
                            onClick={e => { e.stopPropagation(); handleVerify(order.publicId); }}
                            className="mt-2 text-xs font-semibold px-3 py-1.5 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
                          >
                            🩺 Verify (Pathologist Sign-off)
                          </button>
                        )}
                        {(user?.role === 'DOCTOR' || user?.role === 'HOSPITAL_ADMIN') && order.status === 'VERIFIED' && (
                          <button
                            onClick={e => { e.stopPropagation(); handleRelease(order.publicId); }}
                            className="mt-2 text-xs font-semibold px-3 py-1.5 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors"
                          >
                            📤 Release Report
                          </button>
                        )}
                      </div>
                    ) : (
                      <p className="text-xs text-gray-400 italic mt-1">⏳ Awaiting lab results…</p>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
