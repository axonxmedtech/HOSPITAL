import React, { useState, useEffect } from 'react';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';

const ChargeMasterTab = () => {
    const { success, error } = useToast();
    const [chargeMasterList, setChargeMasterList] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedEntry, setSelectedEntry] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    // Form states
    const [serviceCode, setServiceCode] = useState('');
    const [name, setName] = useState('');
    const [category, setCategory] = useState('');
    const [activePrice, setActivePrice] = useState('');
    const [effectiveFrom, setEffectiveFrom] = useState('');
    const [isActive, setIsActive] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    const fetchChargeMaster = async () => {
        setLoading(true);
        try {
            const data = await hospitalService.getChargeMaster();
            setChargeMasterList(data || []);
        } catch (err) {
            error(err.message || 'Failed to fetch charge master entries');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchChargeMaster();
    }, []);

    const openCreateModal = () => {
        setSelectedEntry(null);
        setServiceCode('');
        setName('');
        setCategory('');
        setActivePrice('');
        setEffectiveFrom(new Date().toISOString().split('T')[0]);
        setIsActive(true);
        setIsModalOpen(true);
    };

    const openEditModal = (entry) => {
        setSelectedEntry(entry);
        setServiceCode(entry.serviceCode || '');
        setName(entry.name || '');
        setCategory(entry.category || '');
        setActivePrice(entry.activePrice || '');
        setEffectiveFrom(entry.effectiveFrom ? entry.effectiveFrom.split('T')[0] : '');
        setIsActive(entry.isActive !== false);
        setIsModalOpen(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        if (!serviceCode || !name || !category || !activePrice || !effectiveFrom) {
            error('All fields are required');
            return;
        }

        setIsSaving(true);
        const payload = {
            serviceCode: serviceCode.trim(),
            name: name.trim(),
            category: category.trim(),
            activePrice: parseFloat(activePrice),
            effectiveFrom: new Date(effectiveFrom).toISOString(),
            isActive: !!isActive
        };

        try {
            if (selectedEntry) {
                await hospitalService.updateChargeMaster(selectedEntry.id, payload);
                success('Charge master entry updated successfully');
            } else {
                await hospitalService.createChargeMaster(payload);
                success('Charge master entry created successfully');
            }
            setIsModalOpen(false);
            fetchChargeMaster();
        } catch (err) {
            error(err.message || 'Failed to save charge master entry');
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async (id) => {
        if (!window.confirm('Are you sure you want to delete this entry?')) return;
        try {
            await hospitalService.deleteChargeMaster(id);
            success('Charge master entry deleted successfully');
            fetchChargeMaster();
        } catch (err) {
            error(err.message || 'Failed to delete charge master entry');
        }
    };

    const filteredList = chargeMasterList.filter(item => {
        const query = searchQuery.toLowerCase();
        return (
            (item.serviceCode?.toLowerCase() || '').includes(query) ||
            (item.name?.toLowerCase() || '').includes(query) ||
            (item.category?.toLowerCase() || '').includes(query)
        );
    });

    return (
        <div className="space-y-6">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Charge Master</h2>
                    <p className="text-sm text-gray-600">Manage billing rates and catalogs for services across the hospital.</p>
                </div>
                <button
                    onClick={openCreateModal}
                    className="px-4 py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-800 transition-colors shadow-sm self-start md:self-auto"
                >
                    Add Service Rate
                </button>
            </div>

            <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
                <div className="p-4 border-b border-gray-200 bg-gray-50 flex items-center">
                    <div className="relative flex-1 max-w-md">
                        <svg className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                        <input
                            type="text"
                            placeholder="Search by code, name or category..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="pl-10 pr-4 py-2 w-full border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                        />
                    </div>
                </div>

                {loading ? (
                    <div className="p-12 text-center text-gray-500">Loading catalog...</div>
                ) : filteredList.length === 0 ? (
                    <div className="p-12 text-center text-gray-500">No active catalog entries found.</div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Service Code</th>
                                    <th className="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Name</th>
                                    <th className="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Category</th>
                                    <th className="px-6 py-3 text-right text-xs font-bold text-gray-500 uppercase tracking-wider">Active Price</th>
                                    <th className="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Effective From</th>
                                    <th className="px-6 py-3 text-center text-xs font-bold text-gray-500 uppercase tracking-wider">Status</th>
                                    <th className="px-6 py-3 text-right text-xs font-bold text-gray-500 uppercase tracking-wider">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {filteredList.map((entry) => (
                                    <tr key={entry.id} className="hover:bg-gray-50/50 transition-colors">
                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-gray-900">{entry.serviceCode}</td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{entry.name}</td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{entry.category}</td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-right font-medium text-gray-900">₹{entry.activePrice?.toFixed(2)}</td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                                            {entry.effectiveFrom ? new Date(entry.effectiveFrom).toLocaleDateString() : '-'}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-center text-sm">
                                            <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${entry.isActive ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>
                                                {entry.isActive ? 'Active' : 'Inactive'}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium space-x-2">
                                            <button
                                                onClick={() => openEditModal(entry)}
                                                className="text-gray-950 hover:underline"
                                            >
                                                Edit
                                            </button>
                                            <button
                                                onClick={() => handleDelete(entry.id)}
                                                className="text-red-600 hover:underline"
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
            </div>

            {/* Modal */}
            {isModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
                    <div className="bg-white rounded-xl max-w-lg w-full p-6 shadow-xl border border-gray-100 flex flex-col gap-4 animate-in fade-in zoom-in-95 duration-200">
                        <div className="flex justify-between items-center border-b border-gray-100 pb-3">
                            <h3 className="text-lg font-bold text-gray-900">{selectedEntry ? 'Edit Service Rate' : 'Add Service Rate'}</h3>
                            <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </button>
                        </div>

                        <form onSubmit={handleSave} className="space-y-4">
                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Service Code</label>
                                <input
                                    type="text"
                                    value={serviceCode}
                                    onChange={(e) => setServiceCode(e.target.value)}
                                    placeholder="e.g. SRV-001"
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Service Name</label>
                                <input
                                    type="text"
                                    value={name}
                                    onChange={(e) => setName(e.target.value)}
                                    placeholder="e.g. Consultation Fee"
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Category</label>
                                <input
                                    type="text"
                                    value={category}
                                    onChange={(e) => setCategory(e.target.value)}
                                    placeholder="e.g. General, Surgery, Laboratory"
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Active Price (₹)</label>
                                <input
                                    type="number"
                                    value={activePrice}
                                    onChange={(e) => setActivePrice(e.target.value)}
                                    placeholder="0.00"
                                    min="0"
                                    step="0.01"
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">Effective From Date</label>
                                <input
                                    type="date"
                                    value={effectiveFrom}
                                    onChange={(e) => setEffectiveFrom(e.target.value)}
                                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900"
                                    required
                                />
                            </div>

                            <div className="flex items-center gap-2 py-2">
                                <input
                                    type="checkbox"
                                    id="isActive"
                                    checked={isActive}
                                    onChange={(e) => setIsActive(e.target.checked)}
                                    className="h-4 w-4 rounded border-gray-300 text-gray-900 focus:ring-gray-900"
                                />
                                <label htmlFor="isActive" className="text-sm font-semibold text-gray-700">Set as Active Rate</label>
                            </div>

                            <div className="flex justify-end gap-2 border-t border-gray-100 pt-4 mt-6">
                                <button
                                    type="button"
                                    onClick={() => setIsModalOpen(false)}
                                    className="px-4 py-2 border border-gray-300 rounded-lg text-sm font-semibold hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    disabled={isSaving}
                                    className="px-4 py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-800 disabled:bg-gray-300"
                                >
                                    {isSaving ? 'Saving...' : 'Save Catalog Entry'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ChargeMasterTab;
