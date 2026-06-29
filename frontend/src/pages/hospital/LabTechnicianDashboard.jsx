import { useState, useEffect, useCallback } from 'react';
import { getLabOrders, collectSample } from '../../services/labService';
import LabResultForm from '../../components/lab/LabResultForm';
import authService from '../../services/authService';

// ─── Status styling config ────────────────────────────────────────────────────
const STATUS_CONFIG = {
  ORDERED: {
    badge: 'bg-amber-100 text-amber-800 border border-amber-200',
    dot: 'bg-amber-500',
    label: 'Ordered',
  },
  SAMPLE_COLLECTED: {
    badge: 'bg-blue-100 text-blue-800 border border-blue-200',
    dot: 'bg-blue-500',
    label: 'Sample Collected',
  },
  COMPLETED: {
    badge: 'bg-emerald-100 text-emerald-800 border border-emerald-200',
    dot: 'bg-emerald-500',
    label: 'Completed',
  },
  CANCELLED: {
    badge: 'bg-gray-100 text-gray-500 border border-gray-200',
    dot: 'bg-gray-400',
    label: 'Cancelled',
  },
};

const PRIORITY_CONFIG = {
  URGENT: 'bg-red-100 text-red-700 border border-red-200',
  ROUTINE: 'bg-gray-100 text-gray-600 border border-gray-200',
};

// ─── Sub-components ───────────────────────────────────────────────────────────
function StatCard({ icon, label, value, color }) {
  return (
    <div className={`bg-white rounded-2xl border ${color} p-5 flex items-center gap-4 shadow-sm`}>
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center text-2xl ${color.replace('border-', 'bg-').replace('-200', '-100')}`}>
        {icon}
      </div>
      <div>
        <p className="text-sm text-gray-500 font-medium">{label}</p>
        <p className="text-2xl font-bold text-gray-900">{value}</p>
      </div>
    </div>
  );
}

function OrderCard({ item, onCollect, onEnterResult }) {
  const order = item.order || item;
  const result = item.result;
  const statusCfg = STATUS_CONFIG[order.status] || STATUS_CONFIG.ORDERED;
  const priorityCfg = PRIORITY_CONFIG[order.priority] || PRIORITY_CONFIG.ROUTINE;

  const formatTime = (dt) => {
    if (!dt) return null;
    const d = new Date(dt);
    return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm hover:shadow-md transition-shadow duration-200 overflow-hidden">
      {/* Top bar */}
      <div className={`h-1 w-full ${statusCfg.dot}`} />

      <div className="p-5">
        {/* Header */}
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex-1 min-w-0">
            <h3 className="font-semibold text-gray-900 text-base leading-tight truncate">
              {order.testName}
            </h3>
            <p className="text-xs text-gray-400 mt-0.5">
              Ordered {formatTime(order.createdAt)}
            </p>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            {order.priority === 'URGENT' && (
              <span className={`text-xs px-2.5 py-1 rounded-full font-semibold ${priorityCfg}`}>
                🔴 URGENT
              </span>
            )}
            <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${statusCfg.badge}`}>
              {statusCfg.label}
            </span>
          </div>
        </div>

        {/* Meta */}
        <div className="space-y-1 mb-4">
          {order.orderedByName && (
            <p className="text-xs text-gray-500">
              <span className="font-medium">Ordered by:</span> {order.orderedByName}
            </p>
          )}
          {order.notes && (
            <p className="text-xs text-gray-500 italic">📝 {order.notes}</p>
          )}
          {order.sampleCollectedAt && (
            <p className="text-xs text-gray-500">
              <span className="font-medium">Sample collected:</span>{' '}
              {formatTime(order.sampleCollectedAt)}
              {order.sampleCollectedByName && ` by ${order.sampleCollectedByName}`}
            </p>
          )}
        </div>

        {/* Result preview */}
        {result && (
          <div className={`rounded-xl p-3 mb-4 text-xs ${result.isAbnormal ? 'bg-red-50 border border-red-100' : 'bg-emerald-50 border border-emerald-100'}`}>
            <div className="flex items-center gap-2 mb-1">
              <span className={`font-semibold ${result.isAbnormal ? 'text-red-700' : 'text-emerald-700'}`}>
                {result.isAbnormal ? '⚠ Abnormal Result' : '✓ Normal Result'}
              </span>
            </div>
            {result.resultSummary && (
              <p className="text-gray-600 leading-relaxed">{result.resultSummary}</p>
            )}
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-2">
          {order.status === 'ORDERED' && (
            <button
              onClick={() => onCollect(order.publicId)}
              className="flex-1 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-xl hover:bg-blue-700 active:scale-95 transition-all duration-150"
            >
              🧪 Collect Sample
            </button>
          )}
          {order.status === 'SAMPLE_COLLECTED' && (
            <button
              onClick={() => onEnterResult(order.publicId)}
              className="flex-1 px-4 py-2 text-sm font-medium bg-emerald-600 text-white rounded-xl hover:bg-emerald-700 active:scale-95 transition-all duration-150"
            >
              📋 Enter Result
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Main Dashboard ───────────────────────────────────────────────────────────
export default function LabTechnicianDashboard() {
  const [activeTab, setActiveTab] = useState('pending');
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [resultTarget, setResultTarget] = useState(null);
  const user = authService.getCurrentUser();

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      if (activeTab === 'pending') {
        const [ordered, collected] = await Promise.all([
          getLabOrders({ status: 'ORDERED', size: 100 }),
          getLabOrders({ status: 'SAMPLE_COLLECTED', size: 100 }),
        ]);
        const a = ordered.data?.content || [];
        const b = collected.data?.content || [];
        // Sort: SAMPLE_COLLECTED first (ready to result), then ORDERED
        setOrders([...b, ...a]);
      } else {
        const [completed, cancelled] = await Promise.all([
          getLabOrders({ status: 'COMPLETED', size: 100 }),
          getLabOrders({ status: 'CANCELLED', size: 100 }),
        ]);
        const a = completed.data?.content || [];
        const b = cancelled.data?.content || [];
        setOrders([...a, ...b]);
      }
    } catch (err) {
      console.error('Failed to fetch lab orders', err);
    } finally {
      setLoading(false);
    }
  }, [activeTab]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const handleCollect = async (publicId) => {
    try {
      await collectSample(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Failed to collect sample');
    }
  };

  // Count stats
  const orderedCount = orders.filter(i => (i.order || i).status === 'ORDERED').length;
  const collectedCount = orders.filter(i => (i.order || i).status === 'SAMPLE_COLLECTED').length;
  const completedToday = activeTab === 'completed'
    ? orders.filter(i => {
        const o = i.order || i;
        return o.status === 'COMPLETED' && new Date(o.updatedAt || o.createdAt).toDateString() === new Date().toDateString();
      }).length
    : null;

  const now = new Date();
  const greeting = now.getHours() < 12 ? 'Good Morning' : now.getHours() < 17 ? 'Good Afternoon' : 'Good Evening';

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 shadow-sm sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-gray-900">🔬 Lab Dashboard</h1>
            <p className="text-sm text-gray-500">
              {greeting}, <span className="font-medium text-blue-700">{user?.name || 'Lab Technician'}</span>
            </p>
          </div>
          <button
            onClick={fetchOrders}
            className="px-4 py-2 text-sm font-medium text-blue-700 bg-blue-50 rounded-xl hover:bg-blue-100 transition-colors border border-blue-100"
          >
            ↻ Refresh
          </button>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-6">
        {/* Stats (only for pending tab) */}
        {activeTab === 'pending' && (
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            <StatCard icon="📋" label="Awaiting Collection" value={orderedCount} color="border-amber-200" />
            <StatCard icon="🧪" label="Ready for Result" value={collectedCount} color="border-blue-200" />
          </div>
        )}

        {/* Tabs */}
        <div className="flex gap-1 p-1 bg-white rounded-2xl border border-gray-100 shadow-sm w-fit">
          {[
            { key: 'pending', label: '⏳ Pending', desc: 'To collect & process' },
            { key: 'completed', label: '✅ History', desc: 'Completed & cancelled' },
          ].map(tab => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-5 py-2.5 text-sm font-medium rounded-xl transition-all duration-200 ${
                activeTab === tab.key
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-gray-500 hover:text-gray-700 hover:bg-gray-50'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Orders grid */}
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="bg-white rounded-2xl border border-gray-100 h-48 animate-pulse" />
            ))}
          </div>
        ) : orders.length === 0 ? (
          <div className="text-center py-20 bg-white rounded-2xl border border-gray-100">
            <div className="text-5xl mb-4">
              {activeTab === 'pending' ? '🎉' : '📂'}
            </div>
            <h3 className="text-lg font-semibold text-gray-700 mb-2">
              {activeTab === 'pending' ? 'No pending orders!' : 'No history yet'}
            </h3>
            <p className="text-sm text-gray-400">
              {activeTab === 'pending'
                ? 'All lab orders have been processed.'
                : 'Completed and cancelled orders will appear here.'}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {orders.map((item, idx) => {
              const order = item.order || item;
              return (
                <OrderCard
                  key={order.publicId || idx}
                  item={item}
                  onCollect={handleCollect}
                  onEnterResult={setResultTarget}
                />
              );
            })}
          </div>
        )}
      </div>

      {/* Result entry modal */}
      {resultTarget && (
        <LabResultForm
          publicId={resultTarget}
          onClose={() => setResultTarget(null)}
          onSuccess={() => {
            setResultTarget(null);
            fetchOrders();
          }}
        />
      )}
    </div>
  );
}
