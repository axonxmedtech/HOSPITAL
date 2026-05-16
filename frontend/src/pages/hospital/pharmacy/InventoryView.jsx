import React, { useState, useCallback, useEffect } from 'react';
import { useViewManager } from '../../../hooks/pharmacy/useViewManager';
import { ViewLayout, ViewToolbar, SearchInput } from '../../../components/pharmacy/shared/ViewComponents';
import inventoryApi from '../../../services/pharmacy/inventoryApi';
import categoriesApi from '../../../services/pharmacy/categoriesApi';
import StockAdjustmentModal from '../../../components/StockAdjustmentModal';
import LoadingSpinner from '../../../components/LoadingSpinner';

const InventoryView = ({ onNavigate }) => {
    const [categories, setCategories] = useState([]);
    const [selectedItem, setSelectedItem] = useState(null);
    const [transactions, setTransactions] = useState([]);
    const [txLoading, setTxLoading] = useState(false);
    const [isAdjustmentModalOpen, setIsAdjustmentModalOpen] = useState(false);
    
    const [selectedStatus, setSelectedStatus] = useState('all');
    const [selectedCategory, setSelectedCategory] = useState('all');

    // Restoration: Multi-criteria filtering logic
    const fetchFn = useCallback((search, page, size) => {
        if (selectedStatus === 'low-stock') return inventoryApi.getLowStock(page, size);
        if (selectedStatus === 'expiring') return inventoryApi.getExpiring(30, page, size);
        return inventoryApi.getInventory(search, page, size, selectedCategory === 'all' ? null : selectedCategory);
    }, [selectedStatus, selectedCategory]);

    const {
        data: inventory,
        loading,
        error,
        search,
        page,
        pageSize,
        totalPages,
        totalElements,
        handleSearch,
        handlePageChange,
        refresh
    } = useViewManager(fetchFn, { dependencies: [selectedStatus, selectedCategory] });

    useEffect(() => {
        categoriesApi.getAll('', 0, 100).then(res => setCategories(res.content || []));
    }, []);

    const fetchTransactions = useCallback(async (batchId) => {
        setTxLoading(true);
        try {
            const data = await inventoryApi.getTransactions(batchId, 0, 10);
            setTransactions(data.content || []);
        } finally {
            setTxLoading(false);
        }
    }, []);

    useEffect(() => {
        if (selectedItem) fetchTransactions(selectedItem.id);
    }, [selectedItem, fetchTransactions]);

    const handleExport = () => {
        const headers = ["Medicine", "Batch", "Category", "Quantity", "Expiry", "MRP", "Cost"];
        const rows = inventory.map(item => [
            `"${item.medicine?.medicineName}"`,
            `"${item.batchNumber}"`,
            `"${item.medicine?.category?.categoryName || 'N/A'}"`,
            item.currentQuantity,
            item.expiryDate,
            item.sellingPrice,
            item.purchaseRate
        ]);
        const csvContent = [headers, ...rows].map(e => e.join(",")).join("\n");
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `Inventory_Export_${new Date().toLocaleDateString()}.csv`;
        link.click();
    };

    const getStatusInfo = (item) => {
        const qty = item.currentQuantity || 0;
        const reorder = item.medicine?.reorderLevel || 0;
        const expiry = new Date(item.expiryDate);
        const today = new Date();
        const nearExpiry = new Date();
        nearExpiry.setDate(today.getDate() + 30);

        if (qty <= 0) return { label: 'Out of Stock', color: 'bg-red-50 text-red-700 border-red-100' };
        if (expiry < today) return { label: 'Expired', color: 'bg-gray-100 text-gray-700 border-gray-200' };
        if (expiry < nearExpiry) return { label: 'Near Expiry', color: 'bg-orange-50 text-orange-700 border-orange-100' };
        if (qty <= reorder) return { label: 'Low Stock', color: 'bg-amber-50 text-amber-700 border-amber-100' };
        return { label: 'In Stock', color: 'bg-green-50 text-green-700 border-green-100' };
    };

    return (
        <ViewLayout
            error={error}
            toolbar={
                <ViewToolbar 
                    left={
                        <>
                            <SearchInput 
                                placeholder="Search inventory / batch..."
                                value={search}
                                onChange={(e) => handleSearch(e.target.value)}
                            />
                            <select 
                                value={selectedCategory}
                                onChange={(e) => setSelectedCategory(e.target.value)}
                                className="px-3 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium focus:ring-2 focus:ring-gray-900/5 focus:border-gray-900 outline-none transition-all"
                            >
                                <option value="all">All Categories</option>
                                {categories.map(cat => <option key={cat.id} value={cat.id}>{cat.categoryName}</option>)}
                            </select>
                            <select 
                                value={selectedStatus}
                                onChange={(e) => setSelectedStatus(e.target.value)}
                                className="px-3 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium focus:ring-2 focus:ring-gray-900/5 focus:border-gray-900 outline-none transition-all"
                            >
                                <option value="all">All Status</option>
                                <option value="low-stock">Low Stock</option>
                                <option value="expiring">Near Expiry</option>
                                <option value="out-of-stock">Out of Stock</option>
                            </select>
                        </>
                    }
                    right={
                        <div className="flex gap-2">
                            <button onClick={handleExport} className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded-lg hover:bg-gray-50 transition-colors">
                                Export
                            </button>

                            <button onClick={() => onNavigate('medicine_master')} className="px-4 py-2 bg-gray-900 text-white text-sm font-bold rounded-lg hover:bg-gray-800 transition-all shadow-md">
                                + Add Medicine
                            </button>
                        </div>
                    }
                />
            }
            sidePanel={selectedItem ? (
                <div className="flex flex-col h-full">
                    <div className="p-6 border-b border-gray-100 bg-gray-50/50">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h3 className="text-lg font-bold text-gray-900">{selectedItem.medicine?.medicineName}</h3>
                                <p className="text-xs text-gray-500 font-medium mt-0.5">{selectedItem.medicine?.genericName} • Batch: {selectedItem.batchNumber}</p>
                            </div>
                            <button onClick={() => setSelectedItem(null)} className="text-gray-400 hover:text-gray-600">
                                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                        </div>
                        <div className="grid grid-cols-2 gap-3">
                            <div className="p-3 bg-white rounded-lg border border-gray-100 shadow-sm">
                                <span className="text-[10px] uppercase font-bold text-gray-400 block mb-1">Purchase Rate</span>
                                <span className="text-sm font-bold text-gray-900">₹{selectedItem.purchaseRate}</span>
                            </div>
                            <div className="p-3 bg-white rounded-lg border border-gray-100 shadow-sm">
                                <span className="text-[10px] uppercase font-bold text-gray-400 block mb-1">Selling MRP</span>
                                <span className="text-sm font-bold text-gray-900">₹{selectedItem.sellingPrice}</span>
                            </div>
                            <div className="p-3 bg-blue-50 rounded-lg border border-blue-100 shadow-sm">
                                <span className="text-[10px] uppercase font-bold text-blue-600 block mb-1">Total Stock</span>
                                <span className="text-lg font-black text-blue-900">{selectedItem.currentQuantity}</span>
                            </div>
                            <div className="p-3 bg-amber-50 rounded-lg border border-amber-100 shadow-sm">
                                <span className="text-[10px] uppercase font-bold text-amber-600 block mb-1">Reserved</span>
                                <span className="text-lg font-black text-amber-900">{selectedItem.reservedQuantity || 0}</span>
                            </div>
                        </div>
                    </div>

                    <div className="flex-1 overflow-hidden flex flex-col p-6">
                        <h4 className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-4">Inventory Movement</h4>
                        <div className="flex-1 overflow-y-auto space-y-4 pr-2 custom-scrollbar">
                            {txLoading ? <LoadingSpinner size="sm" /> : transactions.map(tx => (
                                <div key={tx.id} className="flex gap-3 text-xs">
                                    <div className={`mt-1.5 w-2 h-2 rounded-full shrink-0 ${
                                        tx.transactionType === 'SALE' ? 'bg-red-500' : 
                                        tx.transactionType === 'ADJUSTMENT' ? 'bg-blue-500' : 'bg-green-500'
                                    }`}></div>
                                    <div className="flex-1 border-b border-gray-50 pb-2">
                                        <div className="flex justify-between items-start">
                                            <p className="font-bold text-gray-900">{tx.transactionType} ({tx.quantity > 0 ? '+' : ''}{tx.quantity})</p>
                                            <span className="text-[10px] text-gray-400">{new Date(tx.createdAt).toLocaleDateString()}</span>
                                        </div>
                                        <p className="text-gray-500 text-[10px] mt-0.5">{tx.remarks || 'Standard transaction'}</p>
                                        <p className="text-[9px] text-gray-400 mt-1 font-medium">Stock: {tx.quantityBefore} → {tx.quantityAfter}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                        
                        <div className="mt-6 pt-6 border-t border-gray-100 flex flex-col gap-2">
                            <button className="w-full py-2.5 border border-gray-300 rounded-lg text-xs font-bold text-gray-700 bg-white hover:bg-gray-50 transition-colors">
                                Print Barcode Labels
                            </button>
                            <button onClick={() => setIsAdjustmentModalOpen(true)} className="w-full py-2.5 bg-blue-600 text-white rounded-lg text-xs font-bold hover:bg-blue-700 transition-all shadow-md">
                                Adjust Stock (Audit)
                            </button>
                            <button onClick={() => onNavigate('medicine_master')} className="w-full py-2.5 bg-gray-900 text-white rounded-lg text-xs font-bold hover:bg-gray-800 transition-all shadow-md">
                                Edit Medicine Master
                            </button>
                        </div>
                    </div>
                </div>
            ) : (
                <div className="h-full flex flex-col items-center justify-center text-center p-8 text-gray-400">
                    <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mb-4 border border-gray-100 shadow-inner">
                        <svg className="w-8 h-8 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                    </div>
                    <h4 className="text-sm font-bold text-gray-500">Inventory Insights</h4>
                    <p className="text-xs mt-2 leading-relaxed max-w-[200px]">Select a batch from the list to view granular metrics and transaction logs.</p>
                </div>
            )}
        >
            <div className="h-full flex flex-col overflow-hidden">
                <div className="overflow-auto flex-1">
                    <table className="w-full text-left border-collapse">
                        <thead className="bg-gray-50/50 sticky top-0 z-10 border-b border-gray-100">
                            <tr>
                                <th className="px-6 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest">Medicine / Batch</th>
                                <th className="px-6 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest">Category</th>
                                <th className="px-6 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest text-right">Quantity</th>
                                <th className="px-6 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest">Expiry</th>
                                <th className="px-6 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest">MRP / Cost</th>
                                <th className="px-6 py-4 text-[10px] font-black text-gray-400 uppercase tracking-widest">Status</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50">
                            {loading ? (
                                <tr><td colSpan="6" className="py-20 text-center"><LoadingSpinner /></td></tr>
                            ) : inventory.map(item => (
                                <tr 
                                    key={item.id} 
                                    onClick={() => setSelectedItem(item)}
                                    className={`group hover:bg-blue-50/30 cursor-pointer transition-all ${selectedItem?.id === item.id ? 'bg-blue-50/50' : ''}`}
                                >
                                    <td className="px-6 py-4">
                                        <div className="flex flex-col">
                                            <span className="font-bold text-gray-900 text-sm group-hover:text-blue-700 transition-colors">{item.medicine?.medicineName}</span>
                                            <span className="text-[10px] text-gray-400 font-bold uppercase">{item.batchNumber}</span>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4 text-sm text-gray-600">{item.medicine?.category?.categoryName || 'General'}</td>
                                    <td className="px-6 py-4 text-right">
                                        <div className="font-bold text-gray-900">{item.currentQuantity}</div>
                                        {item.reservedQuantity > 0 && <span className="text-[10px] text-amber-600 font-bold block">Res: {item.reservedQuantity}</span>}
                                    </td>
                                    <td className="px-6 py-4 text-sm font-medium text-gray-500">{item.expiryDate}</td>
                                    <td className="px-6 py-4">
                                        <div className="text-sm font-bold text-gray-900">₹{item.sellingPrice}</div>
                                        <div className="text-[10px] text-gray-400 font-medium">Cost: ₹{item.purchaseRate}</div>
                                    </td>
                                    <td className="px-6 py-4">
                                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase border ${getStatusInfo(item).color}`}>
                                            {getStatusInfo(item).label}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
                
                <div className="px-6 py-4 border-t border-gray-100 bg-gray-50/50 flex justify-between items-center">
                    <span className="text-xs text-gray-500 font-medium">Showing {inventory.length} of {totalElements} items</span>
                    <div className="flex gap-2">
                        <button disabled={page === 0} onClick={() => handlePageChange(page - 1)} className="px-3 py-1 border border-gray-200 rounded text-xs font-bold bg-white disabled:opacity-50">Prev</button>
                        <button disabled={page >= totalPages - 1} onClick={() => handlePageChange(page + 1)} className="px-3 py-1 border border-gray-200 rounded text-xs font-bold bg-white disabled:opacity-50">Next</button>
                    </div>
                </div>
            </div>

            <StockAdjustmentModal 
                isOpen={isAdjustmentModalOpen}
                onClose={() => setIsAdjustmentModalOpen(false)}
                item={selectedItem}
                onAdjustmentComplete={refresh}
            />


        </ViewLayout>
    );
};

export default InventoryView;
