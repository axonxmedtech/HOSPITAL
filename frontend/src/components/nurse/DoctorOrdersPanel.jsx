import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';

export default function DoctorOrdersPanel({ admissionId }) {
  const [orders, setOrders] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    orderType: 'MEDICATION', description: '', frequency: 'BD', notes: '',
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => { load(); }, [admissionId]);

  async function load() {
    try {
      const r = await nurseService.getOrders(admissionId);
      setOrders(r.data);
    } catch (e) { console.error(e); }
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!form.description.trim()) return alert('Description is required');
    setSaving(true);
    try {
      await nurseService.createOrder(admissionId, form);
      setForm({ orderType: 'MEDICATION', description: '', frequency: 'BD', notes: '' });
      setShowForm(false);
      load();
    } catch (err) {
      alert(err.response?.data || 'Failed to create order');
    } finally {
      setSaving(false);
    }
  }

  async function handleCancel(publicId) {
    if (!confirm('Cancel this order?')) return;
    try {
      await nurseService.cancelOrder(admissionId, publicId);
      load();
    } catch (err) {
      alert(err.response?.data || 'Failed to cancel');
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="font-semibold text-gray-800">Doctor Orders</h3>
        <button
          onClick={() => setShowForm(v => !v)}
          className="px-3 py-1.5 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
        >
          {showForm ? 'Cancel' : '+ New Order'}
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="border border-gray-200 rounded-lg p-4 space-y-3 bg-gray-50">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-600 mb-1">Type</label>
              <select
                value={form.orderType}
                onChange={e => setForm(f => ({ ...f, orderType: e.target.value }))}
                className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
              >
                {['MEDICATION', 'INVESTIGATION', 'PROCEDURE', 'DIET'].map(t => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-600 mb-1">Frequency</label>
              <select
                value={form.frequency}
                onChange={e => setForm(f => ({ ...f, frequency: e.target.value }))}
                className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
              >
                {['OD', 'BD', 'TDS', 'QID', 'SOS', 'Once'].map(f => (
                  <option key={f}>{f}</option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Description *</label>
            <input
              type="text"
              value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
              placeholder="e.g. Ceftriaxone 1g IV"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Notes</label>
            <input
              type="text"
              value={form.notes}
              onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
              className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={saving}
            className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Create Order'}
          </button>
        </form>
      )}

      {orders.length === 0 && !showForm && (
        <div className="text-center py-6 text-gray-400 text-sm">No orders yet</div>
      )}

      {orders.map(order => (
        <div key={order.id} className="border border-gray-200 rounded-lg p-3 flex justify-between items-start">
          <div>
            <span className={`text-xs px-2 py-0.5 rounded font-medium mr-2 ${
              order.orderType === 'MEDICATION' ? 'bg-blue-100 text-blue-700' :
              order.orderType === 'INVESTIGATION' ? 'bg-purple-100 text-purple-700' :
              'bg-gray-100 text-gray-700'
            }`}>{order.orderType}</span>
            <span className="font-medium text-gray-800 text-sm">{order.description}</span>
            <p className="text-xs text-gray-400 mt-0.5">
              {order.frequency} &nbsp;·&nbsp; {order.createdByName} &nbsp;·&nbsp;{' '}
              {new Date(order.createdAt).toLocaleDateString()}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className={`text-xs px-2 py-0.5 rounded ${
              order.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
              order.status === 'CANCELLED' ? 'bg-red-100 text-red-700' :
              'bg-gray-100 text-gray-600'
            }`}>{order.status}</span>
            {order.status === 'ACTIVE' && (
              <button
                onClick={() => handleCancel(order.publicId)}
                className="text-xs text-red-500 hover:underline"
              >
                Cancel
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
