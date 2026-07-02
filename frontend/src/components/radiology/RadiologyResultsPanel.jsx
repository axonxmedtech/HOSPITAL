import { useState, useEffect, useCallback } from 'react';
import { getRadiologyOrders, placeRadiologyOrder, cancelRadiologyOrder, verifyRadiologyResult, releaseRadiologyResult } from '../../services/radiologyService';
import authService from '../../services/authService';
import masterDataService from '../../services/masterDataService';
import SearchableSelect from '../SearchableSelect';

const STATUS_CONFIG = {
  ORDERED: { badge: 'bg-amber-100 text-amber-700 border border-amber-200', label: 'Ordered', icon: '⏳' },
  STUDY_CONDUCTED: { badge: 'bg-indigo-100 text-indigo-700 border border-indigo-200', label: 'Study Conducted', icon: '📷' },
  COMPLETED: { badge: 'bg-emerald-100 text-emerald-700 border border-emerald-200', label: 'Pending Verification', icon: '✓' },
  VERIFIED: { badge: 'bg-teal-100 text-teal-700 border border-teal-200', label: 'Verified', icon: '🩺' },
  RELEASED: { badge: 'bg-purple-100 text-purple-700 border border-purple-200', label: 'Released', icon: '📤' },
  CANCELLED: { badge: 'bg-gray-100 text-gray-500 border border-gray-200', label: 'Cancelled', icon: '✕' },
};

export default function RadiologyResultsPanel({ ipdAdmissionId, patientId, canOrder = false, openTrigger = 0 }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showOrderForm, setShowOrderForm] = useState(false);
  const [expanded, setExpanded] = useState({});
  const [newOrder, setNewOrder] = useState({ testName: '', radiologyTestMasterId: null, modality: '', priority: 'ROUTINE', notes: '' });
  const [ordering, setOrdering] = useState(false);
  const [orderError, setOrderError] = useState('');

  const user = authService.getCurrentUser();

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params = ipdAdmissionId ? { ipdAdmissionId } : { patientId };
      const res = await getRadiologyOrders(params);
      const content = res.data?.content ?? res.data ?? [];
      setOrders(Array.isArray(content) ? content : []);
    } catch (err) {
      console.error('Failed to load radiology orders', err);
    } finally {
      setLoading(false);
    }
  }, [ipdAdmissionId, patientId]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  useEffect(() => {
    if (openTrigger > 0 && canOrder) setShowOrderForm(true);
  }, [openTrigger, canOrder]);

  const handleOrder = async (e) => {
    e.preventDefault();
    setOrdering(true);
    setOrderError('');
    try {
      await placeRadiologyOrder({ ...newOrder, patientId, ipdAdmissionId });
      setNewOrder({ testName: '', radiologyTestMasterId: null, modality: '', priority: 'ROUTINE', notes: '' });
      setShowOrderForm(false);
      fetchOrders();
    } catch (err) {
      const rawError = err.response?.data;
      const errorMsg = typeof rawError === 'object'
        ? (rawError.error || rawError.message || JSON.stringify(rawError))
        : (rawError || 'Failed to place radiology order');
      setOrderError(errorMsg);
    } finally {
      setOrdering(false);
    }
  };

  const handleCancel = async (publicId) => {
    if (!window.confirm('Cancel this radiology order? This cannot be undone.')) return;
    try {
      await cancelRadiologyOrder(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Cannot cancel this order');
    }
  };

  const toggleExpand = (publicId) => {
    setExpanded(prev => ({ ...prev, [publicId]: !prev[publicId] }));
  };

  const handleVerify = async (publicId) => {
    try {
      await verifyRadiologyResult(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Only a radiologist may verify this result');
    }
  };

  const handleRelease = async (publicId) => {
    try {
      await releaseRadiologyResult(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Cannot release this result yet');
    }
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-indigo-100 rounded-lg flex items-center justify-center text-lg">📷</div>
          <h3 className="text-base font-bold text-gray-900">Radiology Orders & Reports</h3>
          {orders.length > 0 && (
            <span className="bg-indigo-100 text-indigo-700 text-xs font-semibold px-2.5 py-1 rounded-full">
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
                : 'bg-indigo-600 text-white border-indigo-600 hover:bg-indigo-700 shadow-sm'
            }`}
          >
            {showOrderForm ? '✕ Cancel' : '+ New Radiology Order'}
          </button>
        )}
      </div>

      {/* New Order Form */}
      {showOrderForm && (
        <form
          onSubmit={handleOrder}
          className="bg-indigo-50 border border-indigo-200 rounded-2xl p-4 space-y-3"
        >
          <h4 className="text-sm font-semibold text-indigo-900">Place Radiology Order</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Scan / Test Name *</label>
              <SearchableSelect
                onSearch={masterDataService.searchRadiologyTests}
                onSelect={item => setNewOrder(p => ({
                  ...p,
                  testName: item.testName,
                  radiologyTestMasterId: item.id,
                  modality: item.modality
                }))}
                getLabel={item => `${item.testName}${item.modality ? ' [' + item.modality.replace(/_/g, ' ') + ']' : ''}`}
                placeholder="Search radiology test (e.g. X-Ray Chest, MRI Brain)"
                value={newOrder.testName || ''}
                hint={newOrder.modality ? `Modality: ${newOrder.modality.replace(/_/g, ' ')}` : ''}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Priority</label>
              <select
                value={newOrder.priority}
                onChange={e => setNewOrder(p => ({ ...p, priority: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white"
              >
                <option value="ROUTINE">Routine</option>
                <option value="URGENT">🔴 Urgent</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Clinical Notes / History</label>
            <input
              value={newOrder.notes}
              onChange={e => setNewOrder(p => ({ ...p, notes: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white"
              placeholder="e.g. cough and fever for 3 days, r/o pneumonia"
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
              className="px-4 py-2 text-sm font-semibold bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 disabled:opacity-60 transition-all shadow-sm"
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
          <p className="text-sm">No radiology orders yet.</p>
          {canOrder && (
            <p className="text-xs mt-1">Click "+ New Radiology Order" to place the first order.</p>
          )}
        </div>
      ) : (
        <div className="space-y-2">
          {orders.map((item) => {
            const order = item.order || item;
            const result = item.result;
            const isExpanded = expanded[order.publicId];
            const cfg = STATUS_CONFIG[order.status] || STATUS_CONFIG.ORDERED;

            return (
              <div key={order.publicId} className="border border-gray-200 rounded-xl overflow-hidden bg-white shadow-sm hover:shadow-md transition-shadow">
                {/* Row header */}
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
                    {order.studyConductedAt && (
                      <p className="text-xs text-gray-400">
                        Study conducted: {new Date(order.studyConductedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                        {order.studyConductedByName && ` by ${order.studyConductedByName}`}
                      </p>
                    )}

                    {/* Result block */}
                    {result ? (
                      <div className={`rounded-xl p-4 mt-2 ${result.isAbnormal ? 'bg-orange-50 border border-orange-100' : 'bg-emerald-50 border border-emerald-100'}`}>
                        <p className={`text-xs font-semibold mb-2 ${result.isAbnormal ? 'text-orange-700' : 'text-emerald-700'}`}>
                          Report — {new Date(result.resultedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                          {result.verifiedByName && ` · Reported/Verified by ${result.verifiedByName}`}
                        </p>

                        <div className="space-y-2 text-xs">
                          <div>
                            <span className="font-semibold text-gray-700">Findings:</span>
                            <p className="text-gray-600 mt-0.5 whitespace-pre-wrap leading-relaxed">{result.findings}</p>
                          </div>
                          <div>
                            <span className="font-semibold text-gray-700">Impression:</span>
                            <p className="text-gray-800 font-medium mt-0.5 whitespace-pre-wrap">{result.impression}</p>
                          </div>
                          {result.resultFileUrl && (
                            <div className="mt-2 pt-2 border-t border-dashed border-gray-200">
                              <a
                                href={result.resultFileUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1.5 text-xs text-indigo-600 hover:text-indigo-800 font-medium hover:underline"
                              >
                                🔗 View Scan Image / PACS Link
                              </a>
                            </div>
                          )}

                          {result.releasedAt && (
                            <p className="text-purple-700 mt-2">
                              📤 Released by {result.releasedByName} on {new Date(result.releasedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                            </p>
                          )}

                          {/* Radiologist sign-off actions (BR-4/BR-6) */}
                          {(user?.role === 'DOCTOR' || user?.role === 'HOSPITAL_ADMIN') && order.status === 'COMPLETED' && (
                            <button
                              onClick={e => { e.stopPropagation(); handleVerify(order.publicId); }}
                              className="mt-2 text-xs font-semibold px-3 py-1.5 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition-colors"
                            >
                              🩺 Verify (Radiologist Sign-off)
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
                      </div>
                    ) : (
                      <p className="text-xs text-gray-400 italic mt-1">⏳ Awaiting scan and reporting…</p>
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
