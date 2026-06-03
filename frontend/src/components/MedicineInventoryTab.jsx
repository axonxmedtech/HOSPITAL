import React, { useState, useEffect } from 'react';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';
import Skeleton from './Skeleton';

const MedicineInventoryTab = () => {
    const [subTab, setSubTab] = useState('inventory'); // 'inventory' or 'catalog'
    
    // Data states
    const [inventoryList, setInventoryList] = useState([]);
    const [catalogList, setCatalogList] = useState([]);
    const [loading, setLoading] = useState(false);
    
    // Modal states
    const [stockModal, setStockModal] = useState({ isOpen: false, isEdit: false, data: null });
    const [catalogModal, setCatalogModal] = useState({ isOpen: false, isEdit: false, data: null });

    const { success, error: toastError } = useToast();

    // Fetch catalog list
    const fetchCatalog = async () => {
        try {
            const res = await hospitalService.getCatalogMedicines();
            setCatalogList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    // Fetch active stock inventory
    const fetchInventory = async () => {
        try {
            const res = await hospitalService.getInventoryMedicines();
            setInventoryList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    const loadData = async () => {
        setLoading(true);
        try {
            if (subTab === 'inventory') {
                await fetchInventory();
                await fetchCatalog(); // Load catalog to populate options in stock intake modal
            } else {
                await fetchCatalog();
            }
        } catch (err) {
            toastError('Failed to load medicine inventory data.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, [subTab]);

    // Handle Active Stock Save
    const handleStockSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const medicineName = form.medicineName.value.trim();
        const type = form.type.value;
        const stockQuantity = parseInt(form.stockQuantity.value);
        const unitPrice = parseFloat(form.unitPrice.value);
        const minStockLevel = parseInt(form.minStockLevel.value);
        const expiryDate = form.expiryDate.value;

        if (!medicineName) return;

        const payload = {
            name: medicineName,
            type,
            stockQuantity,
            unitPrice,
            minStockLevel,
            expiryDate: expiryDate ? expiryDate : null
        };

        try {
            setLoading(true);
            if (stockModal.isEdit) {
                await hospitalService.updateInventoryMedicine(stockModal.data.id, payload);
                success('Stock details updated successfully.');
            } else {
                await hospitalService.addInventoryMedicine(payload);
                success('Medicine added to stock inventory.');
            }
            setStockModal({ isOpen: false, isEdit: false, data: null });
            loadData();
        } catch (err) {
            toastError(err.response?.data || 'Failed to save inventory record.');
        } finally {
            setLoading(false);
        }
    };

    // Handle Catalog Save
    const handleCatalogSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const name = form.name.value.trim();
        const type = form.type.value;
        const defaultDosage = form.defaultDosage.value.trim();
        const defaultFrequency = form.defaultFrequency.value.trim();
        const defaultDuration = form.defaultDuration.value.trim();
        const manufacturer = form.manufacturer.value.trim();

        if (!name) return;

        const payload = {
            name,
            type,
            defaultDosage,
            defaultFrequency,
            defaultDuration,
            manufacturer
        };

        try {
            setLoading(true);
            if (catalogModal.isEdit) {
                await hospitalService.updateCatalogMedicine(catalogModal.data.id, payload);
                success('Catalog record updated successfully.');
            } else {
                await hospitalService.addCatalogMedicine(payload);
                success('Medicine registered in catalog.');
            }
            setCatalogModal({ isOpen: false, isEdit: false, data: null });
            loadData();
        } catch (err) {
            toastError(err.response?.data || 'Failed to save catalog record.');
        } finally {
            setLoading(false);
        }
    };

    const handleDeactivateStock = async (id) => {
        if (!window.confirm('Are you sure you want to remove this item from active stock inventory?')) return;
        try {
            await hospitalService.deleteInventoryMedicine(id);
            success('Item removed from inventory.');
            loadData();
        } catch (err) {
            toastError('Failed to delete inventory record.');
        }
    };

    const handleDeactivateCatalog = async (id) => {
        if (!window.confirm('Are you sure you want to deactivate this item in the catalog directory?')) return;
        try {
            await hospitalService.deleteCatalogMedicine(id);
            success('Item deactivated in catalog.');
            loadData();
        } catch (err) {
            toastError('Failed to delete catalog record.');
        }
    };

    return (
        <div className="p-6 bg-white rounded-2xl border border-gray-200/80 shadow-sm space-y-6">
            
            {/* Header and Toggle Controls */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-gray-100 pb-4">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">In-Clinic Medicine Inventory</h2>
                    <p className="text-sm text-gray-500">Manage catalog lookup medicines and active in-clinic physical stock levels.</p>
                </div>
                
                {/* Segmented Top-Tab Toggle */}
                <div className="flex bg-gray-100 p-1 rounded-xl w-full sm:w-auto">
                    <button
                        onClick={() => setSubTab('inventory')}
                        className={`flex-1 sm:flex-none px-5 py-2 text-sm font-semibold rounded-lg transition-all ${subTab === 'inventory' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Inventory
                    </button>
                    <button
                        onClick={() => setSubTab('catalog')}
                        className={`flex-1 sm:flex-none px-5 py-2 text-sm font-semibold rounded-lg transition-all ${subTab === 'catalog' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Medicines
                    </button>
                </div>
            </div>

            {/* Quick Actions Panel */}
            <div className="flex justify-between items-center bg-teal-50/40 p-4 rounded-xl border border-teal-100/60">
                <div className="text-sm text-teal-800 font-medium">
                    {subTab === 'inventory' 
                        ? `Displaying ${inventoryList.filter(x => x.isActive !== false).length} active stock items in-clinic`
                        : `Displaying ${catalogList.filter(x => x.isActive !== false).length} catalog lookup dictionary names`
                    }
                </div>
                {subTab === 'inventory' ? (
                    <button
                        onClick={() => setStockModal({ isOpen: true, isEdit: false, data: null })}
                        className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition font-semibold text-sm shadow-md shadow-teal-600/10 active:scale-95"
                    >
                        + Add to Inventory
                    </button>
                ) : (
                    <button
                        onClick={() => setCatalogModal({ isOpen: true, isEdit: false, data: null })}
                        className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition font-semibold text-sm shadow-md shadow-teal-600/10 active:scale-95"
                    >
                        + Add New Medicine
                    </button>
                )}
            </div>

            {/* Main Tables */}
            {loading && inventoryList.length === 0 && catalogList.length === 0 ? (
                <div className="space-y-3">
                    <div className="h-10 bg-slate-100 rounded-lg animate-pulse" />
                    <div className="h-10 bg-slate-100 rounded-lg animate-pulse" />
                    <div className="h-10 bg-slate-100 rounded-lg animate-pulse" />
                </div>
            ) : subTab === 'inventory' ? (
                /* INVENTORY TAB LIST */
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-gray-200 text-gray-500 font-medium">
                                <th className="pb-3 text-left">Medicine Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-center">Quantity</th>
                                <th className="pb-3 text-right">Unit Price</th>
                                <th className="pb-3 text-center">Expiry Date</th>
                                <th className="pb-3 text-center">Stock Level</th>
                                <th className="pb-3 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {inventoryList.filter(x => x.isActive !== false).map((item) => {
                                const isLow = item.stockQuantity <= item.minStockLevel;
                                return (
                                    <tr key={item.id} className="hover:bg-slate-50/50 transition">
                                        <td className="py-3 font-semibold text-gray-800">{item.name}</td>
                                        <td className="py-3 text-center text-gray-600">
                                            <span className="px-2 py-0.5 text-xs bg-slate-100 rounded-full font-medium">{item.type || 'Tablet'}</span>
                                        </td>
                                        <td className="py-3 text-center font-bold text-gray-900">{item.stockQuantity}</td>
                                        <td className="py-3 text-right text-gray-900 font-medium">₹{item.unitPrice?.toFixed(2)}</td>
                                        <td className="py-3 text-center text-gray-500">{item.expiryDate ? new Date(item.expiryDate).toLocaleDateString() : '-'}</td>
                                        <td className="py-3 text-center">
                                            {isLow ? (
                                                <span className="px-2 py-1 text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200 rounded-full">
                                                    Low (Min: {item.minStockLevel})
                                                </span>
                                            ) : (
                                                <span className="px-2 py-1 text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full">
                                                    Good (Min: {item.minStockLevel})
                                                </span>
                                            )}
                                        </td>
                                        <td className="py-3 text-right space-x-2">
                                            <button
                                                onClick={() => setStockModal({ isOpen: true, isEdit: true, data: item })}
                                                className="text-teal-600 hover:text-teal-800 font-semibold"
                                            >
                                                Edit Stock
                                            </button>
                                            <button
                                                onClick={() => handleDeactivateStock(item.id)}
                                                className="text-red-500 hover:text-red-700 font-semibold"
                                            >
                                                Remove
                                            </button>
                                        </td>
                                    </tr>
                                );
                            })}
                            {inventoryList.filter(x => x.isActive !== false).length === 0 && (
                                <tr>
                                    <td colSpan={7} className="py-8 text-center text-gray-400">
                                        No stock items in inventory. Click "+ Add to Inventory" to stock items.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            ) : (
                /* MEDICINES CATALOG LIST */
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-gray-200 text-gray-500 font-medium">
                                <th className="pb-3 text-left">Medicine Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-center">Default Dosage</th>
                                <th className="pb-3 text-center">Frequency</th>
                                <th className="pb-3 text-center">Duration</th>
                                <th className="pb-3 text-left">Manufacturer</th>
                                <th className="pb-3 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {catalogList.filter(x => x.isActive !== false).map((item) => (
                                <tr key={item.id} className="hover:bg-slate-50/50 transition">
                                    <td className="py-3 font-semibold text-gray-800">{item.name}</td>
                                    <td className="py-3 text-center">
                                        <span className="px-2 py-0.5 text-xs bg-slate-100 rounded-full font-medium">{item.type}</span>
                                    </td>
                                    <td className="py-3 text-center text-gray-600">{item.defaultDosage || '-'}</td>
                                    <td className="py-3 text-center text-gray-600">{item.defaultFrequency || '-'}</td>
                                    <td className="py-3 text-center text-gray-600">{item.defaultDuration || '-'}</td>
                                    <td className="py-3 text-left text-gray-500">{item.manufacturer || '-'}</td>
                                    <td className="py-3 text-right space-x-2">
                                        <button
                                            onClick={() => setCatalogModal({ isOpen: true, isEdit: true, data: item })}
                                            className="text-teal-600 hover:text-teal-800 font-semibold"
                                        >
                                            Edit
                                        </button>
                                        <button
                                            onClick={() => handleDeactivateCatalog(item.id)}
                                            className="text-red-500 hover:text-red-700 font-semibold"
                                        >
                                            Deactivate
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            {catalogList.filter(x => x.isActive !== false).length === 0 && (
                                <tr>
                                    <td colSpan={7} className="py-8 text-center text-gray-400">
                                        No catalog dictionary medicines registered.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}

            {/* MODAL 1: ADD/EDIT ACTIVE INVENTORY STOCK */}
            {stockModal.isOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg overflow-hidden animate-fade-in-up" onClick={e => e.stopPropagation()}>
                        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-slate-50">
                            <h3 className="text-lg font-bold text-gray-800">{stockModal.isEdit ? 'Edit Stock Details' : 'Add Stock Intake'}</h3>
                            <button onClick={() => setStockModal({ isOpen: false, isEdit: false, data: null })} className="text-gray-400 hover:text-gray-600">
                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        
                        <form onSubmit={handleStockSubmit} className="p-6 space-y-4">
                            {/* Medicine Name Autocomplete Input */}
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Medicine Name *</label>
                                <input
                                    type="text"
                                    name="medicineName"
                                    list="catalog-options"
                                    placeholder="Type medicine name..."
                                    required
                                    disabled={stockModal.isEdit}
                                    defaultValue={stockModal.data?.name || ''}
                                    onChange={(e) => {
                                        const name = e.target.value.trim();
                                        // --- Suggestion 2: Auto check if new catalog ---
                                        const isKnown = catalogList.some(x => x.name.toLowerCase() === name.toLowerCase());
                                        const hint = document.getElementById('catalog-hint');
                                        if (hint) {
                                            if (name.length > 2 && !isKnown) {
                                                hint.innerText = "💡 This medicine will be registered automatically in the catalog dictionary.";
                                                hint.classList.remove('hidden');
                                            } else {
                                                hint.classList.add('hidden');
                                            }
                                        }
                                    }}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none disabled:bg-gray-100"
                                />
                                <datalist id="catalog-options">
                                    {catalogList.filter(x => x.isActive !== false).map(c => <option key={c.id} value={c.name} />)}
                                </datalist>
                                <p id="catalog-hint" className="text-xs text-amber-600 font-medium mt-1 hidden"></p>
                            </div>

                            {/* Type Select */}
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Type *</label>
                                <select
                                    name="type"
                                    required
                                    defaultValue={stockModal.data?.type || 'Tablet'}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                >
                                    <option value="Tablet">Tablet</option>
                                    <option value="Capsule">Capsule</option>
                                    <option value="Syrup">Syrup</option>
                                    <option value="Injection">Injection</option>
                                    <option value="Saline">Saline</option>
                                    <option value="Cream">Cream</option>
                                    <option value="Other">Other</option>
                                </select>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Quantity *</label>
                                    <input
                                        type="number"
                                        name="stockQuantity"
                                        min="0"
                                        required
                                        placeholder="0"
                                        defaultValue={stockModal.data?.stockQuantity !== undefined ? stockModal.data.stockQuantity : ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Unit Price (₹) *</label>
                                    <input
                                        type="number"
                                        name="unitPrice"
                                        step="0.01"
                                        min="0"
                                        required
                                        placeholder="0.00"
                                        defaultValue={stockModal.data?.unitPrice !== undefined ? stockModal.data.unitPrice : ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Low Stock Warning Limit *</label>
                                    <input
                                        type="number"
                                        name="minStockLevel"
                                        min="0"
                                        required
                                        placeholder="10"
                                        defaultValue={stockModal.data?.minStockLevel !== undefined ? stockModal.data.minStockLevel : '10'}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Expiry Date</label>
                                    <input
                                        type="date"
                                        name="expiryDate"
                                        defaultValue={stockModal.data?.expiryDate || ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                            </div>

                            <div className="pt-4 flex gap-3">
                                <button
                                    type="button"
                                    onClick={() => setStockModal({ isOpen: false, isEdit: false, data: null })}
                                    className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg font-semibold hover:bg-slate-50 transition"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="flex-1 px-4 py-2.5 bg-teal-600 text-white rounded-lg font-semibold hover:bg-teal-700 transition shadow-md shadow-teal-600/10"
                                >
                                    {stockModal.isEdit ? 'Save Changes' : 'Restock / Intake'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* MODAL 2: ADD/EDIT CATALOG DICTIONARY MEDICINE */}
            {catalogModal.isOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg overflow-hidden animate-fade-in-up" onClick={e => e.stopPropagation()}>
                        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-slate-50">
                            <h3 className="text-lg font-bold text-gray-800">{catalogModal.isEdit ? 'Edit Catalog Specifications' : 'Register Catalog Medicine'}</h3>
                            <button onClick={() => setCatalogModal({ isOpen: false, isEdit: false, data: null })} className="text-gray-400 hover:text-gray-600">
                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        
                        <form onSubmit={handleCatalogSubmit} className="p-6 space-y-4">
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Medicine Name *</label>
                                <input
                                    type="text"
                                    name="name"
                                    required
                                    placeholder="e.g. Paracetamol"
                                    defaultValue={catalogModal.data?.name || ''}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Type *</label>
                                <select
                                    name="type"
                                    required
                                    defaultValue={catalogModal.data?.type || 'Tablet'}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                >
                                    <option value="Tablet">Tablet</option>
                                    <option value="Capsule">Capsule</option>
                                    <option value="Syrup">Syrup</option>
                                    <option value="Injection">Injection</option>
                                    <option value="Saline">Saline</option>
                                    <option value="Cream">Cream</option>
                                    <option value="Other">Other</option>
                                </select>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Default Dosage</label>
                                    <input
                                        type="text"
                                        name="defaultDosage"
                                        placeholder="e.g. 500mg"
                                        defaultValue={catalogModal.data?.defaultDosage || ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Default Frequency</label>
                                    <input
                                        type="text"
                                        name="defaultFrequency"
                                        placeholder="e.g. 1-0-1"
                                        defaultValue={catalogModal.data?.defaultFrequency || ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Default Duration</label>
                                    <input
                                        type="text"
                                        name="defaultDuration"
                                        placeholder="e.g. 3 Days"
                                        defaultValue={catalogModal.data?.defaultDuration || ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Manufacturer</label>
                                    <input
                                        type="text"
                                        name="manufacturer"
                                        placeholder="e.g. Generic"
                                        defaultValue={catalogModal.data?.manufacturer || ''}
                                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                    />
                                </div>
                            </div>

                            <div className="pt-4 flex gap-3">
                                <button
                                    type="button"
                                    onClick={() => setCatalogModal({ isOpen: false, isEdit: false, data: null })}
                                    className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg font-semibold hover:bg-slate-50 transition"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="flex-1 px-4 py-2.5 bg-teal-600 text-white rounded-lg font-semibold hover:bg-teal-700 transition shadow-md shadow-teal-600/10"
                                >
                                    {catalogModal.isEdit ? 'Save Changes' : 'Register Medicine'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default MedicineInventoryTab;
