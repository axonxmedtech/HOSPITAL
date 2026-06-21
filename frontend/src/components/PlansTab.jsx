import React, { useState, useEffect } from 'react';
import platformService from '../services/platformService';
import { useToast } from '../context/ToastContext';

const ENTITY_TYPES = ['HOSPITAL', 'CLINIC', 'PHARMACY'];
const AVAILABLE_MODULES = ['OPD', 'IPD', 'PHARMACY', 'BILLING', 'OT', 'PATHOLOGY'];

const emptyForm = {
    name: '',
    type: 'HOSPITAL',
    monthlyPrice: '',
    yearlyPrice: '',
    modules: [],
    features: '',
    inClinic: false,
};

export default function PlansTab() {
    const { success } = useToast();
    const [plans, setPlans] = useState([]);
    const [loading, setLoading] = useState(true);
    const [typeFilter, setTypeFilter] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editingPlan, setEditingPlan] = useState(null);
    const [form, setForm] = useState(emptyForm);
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => { loadPlans(); }, [typeFilter]);

    const loadPlans = async () => {
        setLoading(true);
        try {
            const data = await platformService.getPlans(typeFilter);
            setPlans(data);
        } catch {
            setError('Failed to load plans');
        } finally {
            setLoading(false);
        }
    };

    const openCreate = () => {
        setEditingPlan(null);
        setForm(emptyForm);
        setError('');
        setShowModal(true);
    };

    const openEdit = (plan) => {
        setEditingPlan(plan);
        setForm({
            name: plan.name,
            type: plan.type,
            monthlyPrice: plan.monthlyPrice,
            yearlyPrice: plan.yearlyPrice,
            modules: plan.modules || [],
            features: (plan.features || []).join('\n'),
            inClinic: plan.inClinic || false,
        });
        setError('');
        setShowModal(true);
    };

    const handleModuleToggle = (mod) => {
        setForm(prev => ({
            ...prev,
            modules: prev.modules.includes(mod)
                ? prev.modules.filter(m => m !== mod)
                : [...prev.modules, mod],
        }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.name.trim()) { setError('Plan name is required'); return; }
        if (!form.monthlyPrice || !form.yearlyPrice) { setError('Both prices are required'); return; }

        setSubmitting(true);
        setError('');
        const payload = {
            name: form.name.trim(),
            type: form.type,
            monthlyPrice: parseFloat(form.monthlyPrice),
            yearlyPrice: parseFloat(form.yearlyPrice),
            modules: form.modules,
            features: form.features.split('\n').map(f => f.trim()).filter(Boolean),
            inClinic: form.inClinic,
        };

        try {
            if (editingPlan) {
                await platformService.updatePlan(editingPlan.publicId, payload);
                success('Plan updated successfully');
            } else {
                await platformService.createPlan(payload);
                success('Plan created successfully');
            }
            setShowModal(false);
            loadPlans();
        } catch (err) {
            setError(err.response?.data || 'Failed to save plan');
        } finally {
            setSubmitting(false);
        }
    };

    const handleDelete = async (plan) => {
        if (!window.confirm(`Delete plan "${plan.name}"? This cannot be undone.`)) return;
        try {
            await platformService.deletePlan(plan.publicId);
            success('Plan deleted');
            loadPlans();
        } catch (err) {
            setError(err.response?.data || 'Failed to delete plan');
        }
    };

    const typeBadge = (type) => {
        const colors = {
            HOSPITAL: 'bg-blue-100 text-blue-700',
            CLINIC: 'bg-green-100 text-green-700',
            PHARMACY: 'bg-purple-100 text-purple-700',
        };
        return (
            <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${colors[type] || 'bg-gray-100 text-gray-600'}`}>
                {type}
            </span>
        );
    };

    return (
        <div>
            {error && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
                    {error}
                </div>
            )}

            <div className="flex items-center justify-between mb-4">
                <div className="flex gap-2">
                    {['', ...ENTITY_TYPES].map(t => (
                        <button
                            key={t}
                            onClick={() => setTypeFilter(t)}
                            className={`px-3 py-1.5 text-sm font-medium rounded-lg border transition-colors ${
                                typeFilter === t
                                    ? 'bg-gray-900 text-white border-gray-900'
                                    : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                            }`}
                        >
                            {t || 'All'}
                        </button>
                    ))}
                </div>
                <button
                    onClick={openCreate}
                    className="px-4 py-2 bg-gray-900 text-white text-sm font-medium hover:bg-gray-700 transition-colors"
                >
                    + Create Plan
                </button>
            </div>

            {loading ? (
                <div className="text-center py-12 text-gray-500">Loading plans...</div>
            ) : plans.length === 0 ? (
                <div className="text-center py-12 text-gray-500">No plans found. Create one to get started.</div>
            ) : (
                <div className="bg-white border border-gray-200 overflow-x-auto">
                    <table className="min-w-full">
                        <thead className="bg-gray-50">
                            <tr>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Name</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Type</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Monthly ₹</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Yearly ₹</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase">Modules</th>
                                <th className="px-4 py-3 text-right text-xs font-medium text-gray-600 uppercase">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {plans.map(plan => (
                                <tr key={plan.publicId} className="hover:bg-gray-50">
                                    <td className="px-4 py-3 text-sm font-medium text-gray-900">{plan.name}</td>
                                    <td className="px-4 py-3">{typeBadge(plan.type)}</td>
                                    <td className="px-4 py-3 text-sm text-gray-700">₹{plan.monthlyPrice}</td>
                                    <td className="px-4 py-3 text-sm text-gray-700">₹{plan.yearlyPrice}</td>
                                    <td className="px-4 py-3 text-sm text-gray-600">
                                        {(plan.modules || []).join(', ') || '—'}
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        <button
                                            onClick={() => openEdit(plan)}
                                            className="mr-2 px-3 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded hover:bg-blue-50"
                                        >
                                            Edit
                                        </button>
                                        <button
                                            onClick={() => handleDelete(plan)}
                                            className="px-3 py-1 text-xs font-medium text-red-600 border border-red-200 rounded hover:bg-red-50"
                                        >
                                            Delete
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {showModal && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white w-full max-w-lg max-h-[90vh] overflow-y-auto rounded-xl shadow-xl">
                        <div className="flex items-center justify-between px-6 py-4 border-b">
                            <h3 className="text-lg font-bold text-gray-900">
                                {editingPlan ? 'Edit Plan' : 'Create Plan'}
                            </h3>
                            <button onClick={() => setShowModal(false)} className="text-gray-400 hover:text-gray-600 text-xl">×</button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            {error && <p className="text-sm text-red-600">{error}</p>}

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Plan Name *</label>
                                <input
                                    value={form.name}
                                    onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                                    placeholder="e.g. Clinic Essential"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Type *</label>
                                <select
                                    value={form.type}
                                    onChange={e => setForm(p => ({ ...p, type: e.target.value }))}
                                    disabled={!!editingPlan}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900 disabled:bg-gray-50"
                                >
                                    {ENTITY_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                                </select>
                                {editingPlan && <p className="text-xs text-gray-500 mt-1">Type cannot be changed after creation.</p>}
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">Monthly Price (₹) *</label>
                                    <input
                                        type="number" min="0" step="0.01"
                                        value={form.monthlyPrice}
                                        onChange={e => setForm(p => ({ ...p, monthlyPrice: e.target.value }))}
                                        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">Yearly Price (₹) *</label>
                                    <input
                                        type="number" min="0" step="0.01"
                                        value={form.yearlyPrice}
                                        onChange={e => setForm(p => ({ ...p, yearlyPrice: e.target.value }))}
                                        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                                    />
                                </div>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">Enabled Modules</label>
                                <div className="flex flex-wrap gap-2">
                                    {AVAILABLE_MODULES.map(mod => (
                                        <button
                                            type="button"
                                            key={mod}
                                            onClick={() => handleModuleToggle(mod)}
                                            className={`px-3 py-1 text-xs font-medium rounded-full border transition-colors ${
                                                form.modules.includes(mod)
                                                    ? 'bg-gray-900 text-white border-gray-900'
                                                    : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'
                                            }`}
                                        >
                                            {mod}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">In-Clinic Medicine</label>
                                <label className="flex items-center gap-2 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={form.inClinic}
                                        onChange={e => setForm(p => ({ ...p, inClinic: e.target.checked }))}
                                        className="rounded"
                                    />
                                    <span className="text-sm text-gray-700">Enable in-clinic medicine option for this plan</span>
                                </label>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Feature Labels (one per line, display only)
                                </label>
                                <textarea
                                    value={form.features}
                                    onChange={e => setForm(p => ({ ...p, features: e.target.value }))}
                                    rows={5}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                                    placeholder={"OPD Management\nDigital Prescription\nGST Billing"}
                                />
                            </div>

                            <div className="flex justify-end gap-3 pt-2">
                                <button
                                    type="button"
                                    onClick={() => setShowModal(false)}
                                    className="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    disabled={submitting}
                                    className="px-4 py-2 text-sm font-medium text-white bg-gray-900 rounded-lg hover:bg-gray-700 disabled:opacity-50"
                                >
                                    {submitting ? 'Saving...' : editingPlan ? 'Update Plan' : 'Create Plan'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
