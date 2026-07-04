import React, { useState, useEffect } from 'react';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';
import ConfirmationModal from './ConfirmationModal';

const HospitalInventoryTab = () => {
    const [subTab, setSubTab] = useState('inventory'); // 'inventory', 'purchase', or 'catalog'
    
    // Data states
    const [inventoryList, setInventoryList] = useState([]);
    const [purchaseList, setPurchaseList] = useState([]);
    const [catalogList, setCatalogList] = useState([]);
    const [loading, setLoading] = useState(false);
    
    // Fee options for catalog linking (fetched from admin fees)
    const [availableFees, setAvailableFees] = useState([]);
    
    // Modal states
    const [stockModal, setStockModal] = useState({ isOpen: false, isEdit: false, data: null });
    const [catalogModal, setCatalogModal] = useState({ isOpen: false, isEdit: false, data: null });

    const { success, error: toastError } = useToast();

    const [confirmState, setConfirmState] = useState({ open: false, title: '', message: '', onConfirm: null });

    const [stockItemQuery, setStockItemQuery] = useState('');
    const [showStockSuggestions, setShowStockSuggestions] = useState(false);
    const [stockFormState, setStockFormState] = useState({
        type: 'Consumable',
        manufacturer: '',
        minStockLevel: '10'
    });

    // Relative items states for catalog item
    const [selectedRelativeItems, setSelectedRelativeItems] = useState([]);
    const [hasOwnStock, setHasOwnStock] = useState(true);
    const [templates, setTemplates] = useState([]);
    const [templatePickerOpen, setTemplatePickerOpen] = useState(false);
    const [relativeItemSearch, setRelativeItemSearch] = useState('');
    const [showRelativeSuggestions, setShowRelativeSuggestions] = useState(false);

    useEffect(() => {
        if (stockModal.isOpen) {
            setStockItemQuery(stockModal.data?.name || '');
            setStockFormState({
                type: stockModal.data?.type || 'Consumable',
                manufacturer: stockModal.data?.manufacturer || '',
                minStockLevel: stockModal.data?.minStockLevel?.toString() || '10'
            });
        } else {
            setStockItemQuery('');
        }
        setShowStockSuggestions(false);
    }, [stockModal.isOpen, stockModal.data]);

    useEffect(() => {
        if (catalogModal.isOpen) {
            if (catalogModal.isEdit && catalogModal.data) {
                try {
                    const ids = JSON.parse(catalogModal.data.relativeItemIds || '[]');
                    const matched = catalogList.filter(x => ids.includes(x.id)).map(x => ({ id: x.id, name: x.name }));
                    setSelectedRelativeItems(matched);
                } catch (e) {
                    setSelectedRelativeItems([]);
                }
                setHasOwnStock(catalogModal.data.hasOwnStock !== false);
            } else if (!catalogModal.data) {
                // Only reset for a truly blank "+ Add Catalog Item" open;
                // a template/duplicate prefill passes a non-null data object
                // and sets selectedRelativeItems/hasOwnStock itself beforehand.
                setSelectedRelativeItems([]);
                setHasOwnStock(true);
            }
            setRelativeItemSearch('');
            setShowRelativeSuggestions(false);
        }
    }, [catalogModal.isOpen, catalogModal.isEdit, catalogModal.data, catalogList]);

    // Fetch catalog list
    const fetchCatalog = async () => {
        try {
            const res = await hospitalService.getHospitalInventoryCatalog();
            setCatalogList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    const fetchTemplates = async () => {
        try {
            const res = await hospitalService.getCatalogTemplates();
            setTemplates(res || []);
        } catch (err) {
            console.error('Failed to load inventory templates', err);
        }
    };

    // Fetch available fees for linking (from admin Fees tab)
    // Only custom fees are shown here — standard fees (consultation/casepaper) apply automatically
    const fetchFees = async () => {
        try {
            const customFees = await hospitalService.getCustomFees();
            // Use raw numeric ID from HospitalFee — stored directly as linkedFeeId (Long) in DB
            const custom = (customFees || []).map(f => ({
                id: f.id,          // numeric Long ID
                name: f.name,
                displayName: `${f.name} (₹${f.defaultAmount})`,
                amount: f.defaultAmount
            }));
            setAvailableFees(custom);
        } catch (err) {
            console.error('Failed to load fees', err);
        }
    };

    // Fetch active stock inventory
    const fetchInventory = async () => {
        try {
            const res = await hospitalService.getHospitalInventory();
            setInventoryList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    // Fetch purchases
    const fetchPurchases = async () => {
        try {
            const res = await hospitalService.getHospitalInventoryPurchases();
            setPurchaseList(res || []);
        } catch (err) {
            console.error(err);
        }
    };

    const loadData = async () => {
        setLoading(true);
        try {
            if (subTab === 'inventory') {
                await fetchInventory();
                await fetchCatalog(); // Load catalog to populate options
            } else if (subTab === 'purchase') {
                await fetchPurchases();
                await fetchCatalog(); // For autocomplete in add stock
            } else {
                await Promise.all([fetchCatalog(), fetchFees(), fetchTemplates()]);
            }
        } catch (err) {
            toastError('Failed to load hospital inventory data.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, [subTab]);

    // Handle Stock Intake / Purchase Save
    const handleStockSubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const itemName = form.itemName.value.trim();
        const type = form.type.value;
        const stockQuantity = parseInt(form.stockQuantity.value);
        const unitPrice = parseFloat(form.unitPrice.value);
        const minStockLevel = parseInt(form.minStockLevel.value);
        const expiryDate = form.expiryDate.value;
        const manufacturer = form.manufacturer.value.trim();

        if (!itemName) return;

        const payload = {
            name: itemName,
            type,
            quantity: stockQuantity, // mapped to quantity in purchase schema
            unitPrice,
            minStockLevel,
            expiryDate: expiryDate ? expiryDate : null,
            manufacturer: manufacturer ? manufacturer : null
        };

        try {
            setLoading(true);
            await hospitalService.addHospitalInventoryPurchase(payload);
            success('Purchase recorded and stock inventory updated.');
            setStockModal({ isOpen: false, isEdit: false, data: null });
            loadData();
        } catch (err) {
            toastError(err.response?.data || 'Failed to record purchase.');
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
        const manufacturer = form.manufacturer.value.trim();
        const linkedFeeId = form.linkedFeeId?.value || null;

        if (!name) return;

        const payload = {
            name,
            type,
            manufacturer: manufacturer ? manufacturer : null,
            // Parse as number (custom fee ID is a Long in DB); null if empty/invalid
            linkedFeeId: linkedFeeId && !isNaN(linkedFeeId) ? Number(linkedFeeId) : null,
            relativeItemIds: JSON.stringify(selectedRelativeItems.map(x => x.id)),
            hasOwnStock
        };

        try {
            setLoading(true);
            if (catalogModal.isEdit) {
                await hospitalService.updateHospitalInventoryCatalog(catalogModal.data.id, payload);
                success('Catalog record updated successfully.');
            } else {
                await hospitalService.addHospitalInventoryCatalog(payload);
                success('Item registered in catalog.');
            }
            setCatalogModal({ isOpen: false, isEdit: false, data: null });
            loadData();
        } catch (err) {
            toastError(err.response?.data || 'Failed to save catalog record.');
        } finally {
            setLoading(false);
        }
    };

    const handleDuplicateCatalog = async (id) => {
        try {
            const source = await hospitalService.duplicateCatalogItem(id);
            const matchedRelativeItems = (() => {
                try {
                    const ids = JSON.parse(source.relativeItemIds || '[]');
                    return catalogList.filter(x => ids.includes(x.id)).map(x => ({ id: x.id, name: x.name }));
                } catch (e) {
                    return [];
                }
            })();
            setSelectedRelativeItems(matchedRelativeItems);
            setHasOwnStock(source.hasOwnStock !== false);
            setCatalogModal({
                isOpen: true,
                isEdit: false,
                data: { name: '', type: source.type, manufacturer: source.manufacturer, linkedFeeId: source.linkedFeeId }
            });
        } catch (err) {
            toastError('Failed to load item for duplication.');
        }
    };

    const handleDeactivateStock = (id) => {
        setConfirmState({
            open: true,
            title: 'Remove Stock Item',
            message: 'Are you sure you want to remove this item from active stock inventory?',
            onConfirm: async () => {
                await hospitalService.deleteHospitalInventory(id);
                success('Item removed from inventory.');
                loadData();
            }
        });
    };

    const handleDeactivateCatalog = (id) => {
        setConfirmState({
            open: true,
            title: 'Deactivate Catalog Item',
            message: 'Are you sure you want to deactivate this item in the catalog directory?',
            onConfirm: async () => {
                await hospitalService.deleteHospitalInventoryCatalog(id);
                success('Item deactivated in catalog.');
                loadData();
            }
        });
    };

    return (
        <div className="p-6 bg-white rounded-2xl border border-gray-200/80 shadow-sm space-y-6">
            
            {/* Header and Toggle Controls */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-gray-100 pb-4">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Hospital Equipment & Consumable Inventory</h2>
                    <p className="text-sm text-gray-500">Manage catalog lookup items and active physical stock levels for non-medicine equipment (e.g. saline, syringes, gloves).</p>
                </div>
                
                {/* Segmented Top-Tab Toggle */}
                <div className="flex bg-gray-100 p-1 rounded-xl w-full sm:w-auto overflow-x-auto whitespace-nowrap scrollbar-none">
                    <button
                        onClick={() => setSubTab('inventory')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'inventory' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Active Stock
                    </button>
                    <button
                        onClick={() => setSubTab('purchase')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'purchase' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Purchase History
                    </button>
                    <button
                        onClick={() => setSubTab('catalog')}
                        className={`flex-1 sm:flex-none px-3 sm:px-5 py-2 text-sm font-semibold rounded-lg transition-all whitespace-nowrap ${subTab === 'catalog' ? 'bg-white text-teal-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'}`}
                    >
                        Catalog Lookup
                    </button>
                </div>
            </div>

            {/* Quick Actions Panel */}
            <div className="flex justify-between items-center bg-teal-50/40 p-4 rounded-xl border border-teal-100/60">
                <div className="text-sm text-teal-800 font-medium">
                    {subTab === 'inventory' && `Displaying ${inventoryList.filter(x => x.isActive !== false).length} active stock items in-clinic`}
                    {subTab === 'purchase' && `Displaying ${purchaseList.length} purchase ledger entries`}
                    {subTab === 'catalog' && `Displaying ${catalogList.filter(x => x.isActive !== false).length} catalog lookup dictionary names`}
                </div>
                {subTab === 'purchase' && (
                    <button
                        onClick={() => setStockModal({ isOpen: true, isEdit: false, data: null })}
                        className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition font-semibold text-sm shadow-md shadow-teal-600/10 active:scale-95"
                    >
                        + Add Stock (Purchase Intake)
                    </button>
                )}
                {subTab === 'catalog' && (
                    <div className="flex gap-2">
                        <button
                            onClick={() => setTemplatePickerOpen(true)}
                            className="px-4 py-2 border border-teal-600 text-teal-700 rounded-lg hover:bg-teal-50 transition font-semibold text-sm active:scale-95"
                        >
                            Add from Template
                        </button>
                        <button
                            onClick={() => setCatalogModal({ isOpen: true, isEdit: false, data: null })}
                            className="px-4 py-2 bg-teal-600 text-white rounded-lg hover:bg-teal-700 transition font-semibold text-sm shadow-md shadow-teal-600/10 active:scale-95"
                        >
                            + Add Catalog Item
                        </button>
                    </div>
                )}
            </div>

            {/* Main Tables */}
            {loading && inventoryList.length === 0 && purchaseList.length === 0 && catalogList.length === 0 ? (
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
                                <th className="pb-3 text-left">Item Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-center">Quantity</th>
                                <th className="pb-3 text-right">Unit Cost</th>
                                <th className="pb-3 text-center">Expiry Date</th>
                                <th className="pb-3 text-center">Stock Level</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {inventoryList.filter(x => x.isActive !== false).map((item) => {
                                const isLow = item.stockQuantity <= item.minStockLevel;
                                return (
                                    <tr key={item.id} className="hover:bg-slate-50/50 transition">
                                        <td className="py-3 font-semibold text-gray-800">{item.name}</td>
                                        <td className="py-3 text-center text-gray-600">
                                            <span className="px-2 py-0.5 text-xs bg-slate-100 rounded-full font-medium">{item.type || 'Consumable'}</span>
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
                                    </tr>
                                );
                            })}
                            {inventoryList.filter(x => x.isActive !== false).length === 0 && (
                                <tr>
                                    <td colSpan={6} className="py-8 text-center text-gray-400">
                                        No stock items in inventory. Record purchases in the "Purchase History" tab to add stock.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            ) : subTab === 'purchase' ? (
                /* PURCHASE HISTORY TAB LIST */
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-gray-200 text-gray-500 font-medium">
                                <th className="pb-3 text-left">Item Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-center">Quantity Purchased</th>
                                <th className="pb-3 text-right">Unit Price</th>
                                <th className="pb-3 text-right">Total Cost</th>
                                <th className="pb-3 text-center">Expiry Date</th>
                                <th className="pb-3 text-left">Manufacturer</th>
                                <th className="pb-3 text-center">Purchase Date</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {purchaseList.map((item) => {
                                const totalCost = item.quantity * item.unitPrice;
                                return (
                                    <tr key={item.id} className="hover:bg-slate-50/50 transition">
                                        <td className="py-3 font-semibold text-gray-800">{item.name}</td>
                                        <td className="py-3 text-center text-gray-600">
                                            <span className="px-2 py-0.5 text-xs bg-slate-100 rounded-full font-medium">{item.type || 'Consumable'}</span>
                                        </td>
                                        <td className="py-3 text-center font-bold text-gray-900">{item.quantity}</td>
                                        <td className="py-3 text-right text-gray-900 font-medium">₹{item.unitPrice?.toFixed(2)}</td>
                                        <td className="py-3 text-right text-teal-700 font-semibold">₹{totalCost.toFixed(2)}</td>
                                        <td className="py-3 text-center text-gray-500">{item.expiryDate ? new Date(item.expiryDate).toLocaleDateString() : '-'}</td>
                                        <td className="py-3 text-left text-gray-500">{item.manufacturer || '-'}</td>
                                        <td className="py-3 text-center text-gray-500">
                                            {item.purchaseDate ? new Date(item.purchaseDate).toLocaleString() : '-'}
                                        </td>
                                    </tr>
                                );
                            })}
                            {purchaseList.length === 0 && (
                                <tr>
                                    <td colSpan={8} className="py-8 text-center text-gray-400">
                                        No purchase history found. Click "+ Add Stock (Purchase Intake)" to record a purchase.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            ) : (
                /* CATALOG LIST */
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm text-left">
                        <thead>
                            <tr className="border-b border-gray-200 text-gray-500 font-medium">
                                <th className="pb-3 text-left">Item Name</th>
                                <th className="pb-3 text-center">Type</th>
                                <th className="pb-3 text-left">Manufacturer</th>
                                <th className="pb-3 text-left">Linked Charge</th>
                                <th className="pb-3 text-center">Stock Type</th>
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
                                    <td className="py-3 text-left text-gray-500">{item.manufacturer || '-'}</td>
                                    <td className="py-3 text-left">
                                        {item.linkedFeeId ? (
                                            <span className="px-2 py-0.5 text-xs bg-teal-50 text-teal-700 border border-teal-100 rounded-full font-medium">
                                                {availableFees.find(f => String(f.id) === String(item.linkedFeeId))?.name || `Fee ID: ${item.linkedFeeId}`}
                                            </span>
                                        ) : (
                                            <span className="text-xs text-gray-400">No charge linked</span>
                                        )}
                                    </td>
                                    <td className="py-3 text-center">
                                        {item.hasOwnStock === false ? (
                                            <span className="px-2 py-0.5 text-xs bg-indigo-50 text-indigo-700 border border-indigo-100 rounded-full font-medium">Service</span>
                                        ) : (
                                            <span className="px-2 py-0.5 text-xs bg-slate-100 text-gray-600 rounded-full font-medium">Stocked</span>
                                        )}
                                    </td>
                                    <td className="py-3 text-right space-x-2">
                                        <button
                                            onClick={() => setCatalogModal({ isOpen: true, isEdit: true, data: item })}
                                            className="text-teal-600 hover:text-teal-800 font-semibold"
                                        >
                                            Edit
                                        </button>
                                        <button
                                            onClick={() => handleDuplicateCatalog(item.id)}
                                            className="text-indigo-600 hover:text-indigo-800 font-semibold"
                                        >
                                            Duplicate
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
                                    <td colSpan={6} className="py-8 text-center text-gray-400">
                                        No catalog items registered.
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
                            {/* Item Name Autocomplete Input */}
                            <div className="relative">
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Item Name *</label>
                                <input
                                    type="text"
                                    name="itemName"
                                    placeholder="Type general item name (e.g. Gloves, Syringe)..."
                                    required
                                    disabled={stockModal.isEdit}
                                    value={stockItemQuery}
                                    onChange={(e) => {
                                        const name = e.target.value;
                                        setStockItemQuery(name);
                                        setShowStockSuggestions(true);
                                        
                                        const isKnown = catalogList.some(x => x.name.toLowerCase() === name.trim().toLowerCase());
                                        const hint = document.getElementById('catalog-hint');
                                        if (hint) {
                                            if (name.trim().length >= 3 && !isKnown) {
                                                hint.innerText = "💡 This item will be registered automatically in the catalog dictionary.";
                                                hint.classList.remove('hidden');
                                            } else {
                                                hint.classList.add('hidden');
                                            }
                                        }
                                    }}
                                    onFocus={() => setShowStockSuggestions(true)}
                                    onBlur={() => {
                                        setTimeout(() => setShowStockSuggestions(false), 200);
                                    }}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none disabled:bg-gray-100 text-gray-800"
                                />
                                {showStockSuggestions && stockItemQuery.trim().length >= 3 && (
                                    <div className="absolute left-0 right-0 mt-1 max-h-40 overflow-y-auto bg-white rounded-lg border border-gray-200 shadow-lg z-50 divide-y divide-gray-100">
                                        {catalogList
                                            .filter(x => x.isActive !== false && x.name.toLowerCase().includes(stockItemQuery.toLowerCase().trim()))
                                            .map(c => (
                                                <button
                                                    key={c.id}
                                                    type="button"
                                                    onMouseDown={() => {
                                                        setStockItemQuery(c.name);
                                                        setShowStockSuggestions(false);
                                                        setStockFormState({
                                                            type: c.type || 'Consumable',
                                                            manufacturer: c.manufacturer || '',
                                                            minStockLevel: '10'
                                                        });
                                                        const hint = document.getElementById('catalog-hint');
                                                        if (hint) hint.classList.add('hidden');
                                                    }}
                                                    className="w-full text-left px-4 py-2 hover:bg-slate-50 text-sm font-medium text-gray-800"
                                                >
                                                    {c.name} <span className="text-xs text-gray-400 font-normal">({c.type})</span>
                                                </button>
                                            ))}
                                        {catalogList.filter(x => x.isActive !== false && x.name.toLowerCase().includes(stockItemQuery.toLowerCase().trim())).length === 0 && (
                                            <div className="p-2.5 text-center text-xs text-gray-400">No matching catalog item.</div>
                                        )}
                                    </div>
                                )}
                                <p id="catalog-hint" className="text-xs text-amber-600 font-medium mt-1 hidden"></p>
                            </div>

                            {/* Type Select */}
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Type *</label>
                                <select
                                    name="type"
                                    required
                                    value={stockFormState.type}
                                    onChange={(e) => setStockFormState(prev => ({ ...prev, type: e.target.value }))}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none bg-white text-gray-800"
                                >
                                    <option value="Consumable">Consumable (Gloves, Swabs)</option>
                                    <option value="Surgical">Surgical Instruments (Syringes, Needles)</option>
                                    <option value="Fluid">Saline/Fluid</option>
                                    <option value="Equipment">Diagnostic Equipment</option>
                                    <option value="Other">Other</option>
                                </select>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Quantity *</label>
                                    <input
                                        type="number"
                                        name="stockQuantity"
                                        min="1"
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
                                    <label className="block text-sm font-semibold text-gray-700 mb-1">Min Stock Warning Level *</label>
                                    <input
                                        type="number"
                                        name="minStockLevel"
                                        min="0"
                                        required
                                        placeholder="10"
                                        value={stockFormState.minStockLevel}
                                        onChange={(e) => setStockFormState(prev => ({ ...prev, minStockLevel: e.target.value }))}
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

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Manufacturer</label>
                                <input
                                    type="text"
                                    name="manufacturer"
                                    placeholder="e.g. Generic Co."
                                    value={stockFormState.manufacturer}
                                    onChange={(e) => setStockFormState(prev => ({ ...prev, manufacturer: e.target.value }))}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                />
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

            <ConfirmationModal
                isOpen={confirmState.open}
                title={confirmState.title}
                message={confirmState.message}
                onConfirm={confirmState.onConfirm}
                onCancel={() => setConfirmState({ open: false })}
            />

            {/* TEMPLATE PICKER */}
            {templatePickerOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => setTemplatePickerOpen(false)}>
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-md overflow-hidden animate-fade-in-up" onClick={e => e.stopPropagation()}>
                        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-slate-50">
                            <h3 className="text-lg font-bold text-gray-800">Add from Template</h3>
                            <button onClick={() => setTemplatePickerOpen(false)} className="text-gray-400 hover:text-gray-600">
                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="p-4 divide-y divide-gray-100 max-h-96 overflow-y-auto">
                            {templates.map((t, i) => (
                                <button
                                    key={i}
                                    type="button"
                                    onClick={() => {
                                        const matchedRelativeItems = t.suggestedRelativeItemNames
                                            .map(suggestedName => catalogList.find(c => c.isActive !== false && c.name.toLowerCase() === suggestedName.toLowerCase()))
                                            .filter(Boolean)
                                            .map(c => ({ id: c.id, name: c.name }));
                                        setSelectedRelativeItems(matchedRelativeItems);
                                        setHasOwnStock(t.hasOwnStock !== false);
                                        setCatalogModal({ isOpen: true, isEdit: false, data: { name: t.name, type: t.type } });
                                        setTemplatePickerOpen(false);
                                    }}
                                    className="w-full text-left px-2 py-3 hover:bg-slate-50 transition"
                                >
                                    <div className="font-semibold text-gray-800 text-sm">{t.name}</div>
                                    <div className="text-xs text-gray-400 mt-0.5">Suggests: {t.suggestedRelativeItemNames.join(', ')}</div>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* MODAL 2: ADD/EDIT CATALOG DICTIONARY ITEM */}
            {catalogModal.isOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg overflow-hidden animate-fade-in-up" onClick={e => e.stopPropagation()}>
                        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-slate-50">
                            <h3 className="text-lg font-bold text-gray-800">{catalogModal.isEdit ? 'Edit Catalog Specifications' : 'Register Catalog Item'}</h3>
                            <button onClick={() => setCatalogModal({ isOpen: false, isEdit: false, data: null })} className="text-gray-400 hover:text-gray-600">
                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        
                        <form onSubmit={handleCatalogSubmit} className="p-6 space-y-4">
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Item Name *</label>
                                <input
                                    type="text"
                                    name="name"
                                    required
                                    placeholder="e.g. Syringe 5ml"
                                    defaultValue={catalogModal.data?.name || ''}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Type *</label>
                                <select
                                    name="type"
                                    required
                                    defaultValue={catalogModal.data?.type || 'Consumable'}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none bg-white text-gray-800"
                                >
                                    <option value="Consumable">Consumable (Gloves, Swabs)</option>
                                    <option value="Surgical">Surgical Instruments (Syringes, Needles)</option>
                                    <option value="Fluid">Saline/Fluid</option>
                                    <option value="Equipment">Diagnostic Equipment</option>
                                    <option value="Other">Other</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Manufacturer</label>
                                <input
                                    type="text"
                                    name="manufacturer"
                                    placeholder="e.g. Generic Co."
                                    defaultValue={catalogModal.data?.manufacturer || ''}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Linked Charge / Fee</label>
                                <select
                                    name="linkedFeeId"
                                    defaultValue={catalogModal.data?.linkedFeeId || ''}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none bg-white text-gray-800"
                                >
                                     <option value="">-- No charge linked --</option>
                                     {availableFees.map(fee => (
                                         <option key={fee.id} value={fee.id}>{fee.displayName || fee.name}</option>
                                     ))}
                                 </select>
                                 <p className="text-xs text-gray-400 mt-1">Link a custom fee from the Fees tab. When this item is used in a consultation/IPD, the linked fee will be auto-applied to the bill.</p>
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Stock Type</label>
                                <div className="flex gap-4">
                                    <label className="flex items-center gap-2 text-sm text-gray-700">
                                        <input
                                            type="radio"
                                            name="hasOwnStock"
                                            checked={hasOwnStock === true}
                                            onChange={() => setHasOwnStock(true)}
                                        />
                                        Stocked — has its own physical quantity
                                    </label>
                                    <label className="flex items-center gap-2 text-sm text-gray-700">
                                        <input
                                            type="radio"
                                            name="hasOwnStock"
                                            checked={hasOwnStock === false}
                                            onChange={() => setHasOwnStock(false)}
                                        />
                                        Service — stock comes from related items
                                    </label>
                                </div>
                                <p className="text-xs text-gray-400 mt-1">Choose "Service" for procedures like Dressing that aren't purchased in units themselves — their availability is determined entirely by the related items below.</p>
                            </div>

                            {/* Relative Items search and select */}
                            <div className="relative">
                                <label className="block text-sm font-semibold text-gray-700 mb-1">Relative Items (Dependencies)</label>
                                <input
                                    type="text"
                                    placeholder="Search child/relative items (needle, tube)..."
                                    value={relativeItemSearch}
                                    onChange={(e) => {
                                        setRelativeItemSearch(e.target.value);
                                        setShowRelativeSuggestions(true);
                                    }}
                                    onFocus={() => setShowRelativeSuggestions(true)}
                                    onBlur={() => setTimeout(() => setShowRelativeSuggestions(false), 200)}
                                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-teal-500 focus:border-transparent outline-none text-gray-800"
                                />

                                {showRelativeSuggestions && relativeItemSearch.trim().length >= 1 && (
                                    <div className="absolute left-0 right-0 mt-1 max-h-40 overflow-y-auto bg-white rounded-lg border border-gray-200 shadow-lg z-50 divide-y divide-gray-100">
                                        {catalogList
                                            .filter(x => x.isActive !== false 
                                                && x.name.toLowerCase().includes(relativeItemSearch.toLowerCase().trim())
                                                // Prevent self-reference
                                                && x.id !== catalogModal.data?.id
                                                // Prevent duplicate selection
                                                && !selectedRelativeItems.some(item => item.id === x.id)
                                            )
                                            .map(c => (
                                                <button
                                                    key={c.id}
                                                    type="button"
                                                    onMouseDown={() => {
                                                        setSelectedRelativeItems(prev => [...prev, { id: c.id, name: c.name }]);
                                                        setRelativeItemSearch('');
                                                        setShowRelativeSuggestions(false);
                                                    }}
                                                    className="w-full text-left px-4 py-2 hover:bg-slate-50 text-sm font-medium text-gray-800"
                                                >
                                                    {c.name} <span className="text-xs text-gray-400 font-normal">({c.type})</span>
                                                </button>
                                            ))}
                                        {catalogList.filter(x => x.isActive !== false 
                                            && x.name.toLowerCase().includes(relativeItemSearch.toLowerCase().trim())
                                            && x.id !== catalogModal.data?.id
                                            && !selectedRelativeItems.some(item => item.id === x.id)
                                        ).length === 0 && (
                                            <div className="p-2.5 text-center text-xs text-gray-400">No matching catalog items.</div>
                                        )}
                                    </div>
                                )}

                                {/* Selected Items Tags */}
                                {selectedRelativeItems.length > 0 && (
                                    <div className="flex flex-wrap gap-2 mt-2">
                                        {selectedRelativeItems.map(item => (
                                            <span 
                                                key={item.id} 
                                                className="inline-flex items-center gap-1 px-3 py-1 bg-teal-50 text-teal-700 border border-teal-200 rounded-full text-xs font-semibold"
                                            >
                                                {item.name}
                                                <button
                                                    type="button"
                                                    onClick={() => setSelectedRelativeItems(prev => prev.filter(x => x.id !== item.id))}
                                                    className="hover:text-teal-900 focus:outline-none text-teal-500 font-bold"
                                                >
                                                    &times;
                                                </button>
                                            </span>
                                        ))}
                                    </div>
                                )}
                                <p className="text-xs text-gray-400 mt-1">These relative items will be automatically degraded from active stock when this catalog item is administered to a patient.</p>
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
                                    {catalogModal.isEdit ? 'Save Changes' : 'Register Item'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default HospitalInventoryTab;
