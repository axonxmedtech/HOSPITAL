import React, { useState, useEffect } from 'react';
import medicinesApi from '../services/pharmacy/medicinesApi';
import inventoryApi from '../services/pharmacy/inventoryApi';
import { useToast } from '../context/ToastContext';

const MedicineBatchForm = ({ isOpen, onClose, onSuccess }) => {
    const toast = useToast();
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [formData, setFormData] = useState({
        medicineId: '',
        medicineName: '',
        batchNumber: '',
        expiryDate: '',
        currentQuantity: '',
        purchaseRate: '',
        mrp: '',
        sellingPrice: '',
        remarks: 'Opening Stock'
    });

    const [results, setResults] = useState([]);
    const [showResults, setShowResults] = useState(false);
    const [isLoadingResults, setIsLoadingResults] = useState(false);

    useEffect(() => {
        if (!isOpen) {
            setFormData({
                medicineId: '',
                medicineName: '',
                batchNumber: '',
                expiryDate: '',
                currentQuantity: '',
                purchaseRate: '',
                mrp: '',
                sellingPrice: '',
                remarks: 'Opening Stock'
            });
            setResults([]);
            setShowResults(false);
        }
    }, [isOpen]);

    // Search Debounce
    useEffect(() => {
        const timer = setTimeout(async () => {
            if (formData.medicineName && formData.medicineName.length > 1 && !formData.medicineId) {
                setIsLoadingResults(true);
                try {
                    console.log('Searching for:', formData.medicineName);
                    const data = await medicinesApi.autocomplete(formData.medicineName);
                    console.log('Results:', data);
                    setResults(data || []);
                    setShowResults(true);
                } catch (err) {
                    console.error('Autocomplete error:', err);
                    toast.error("Search failed");
                } finally {
                    setIsLoadingResults(false);
                }
            } else if (!formData.medicineName) {
                setResults([]);
                setShowResults(false);
            }
        }, 400);

        return () => clearTimeout(timer);
    }, [formData.medicineName, formData.medicineId, toast]);

    const handleSearchChange = (query) => {
        setFormData(prev => ({ 
            ...prev, 
            medicineName: query,
            medicineId: '' // Clear ID when typing to allow re-selection
        }));
    };

    const handleSelect = (med) => {
        setFormData(prev => ({
            ...prev,
            medicineId: med.id,
            medicineName: med.medicineName,
            mrp: med.mrp || '',
            sellingPrice: med.sellingPrice || '',
            purchaseRate: med.purchaseRate || ''
        }));
        setShowResults(false);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!formData.medicineId || !formData.batchNumber || !formData.currentQuantity) {
            toast.error("Please fill all required fields");
            return;
        }

        setIsSubmitting(true);
        try {
            const payload = {
                ...formData,
                medicineId: parseInt(formData.medicineId),
                currentQuantity: parseFloat(formData.currentQuantity),
                purchaseRate: parseFloat(formData.purchaseRate) || 0,
                mrp: parseFloat(formData.mrp) || 0,
                sellingPrice: parseFloat(formData.sellingPrice) || 0
            };
            await inventoryApi.createBatch(payload);
            toast.success("Opening stock added successfully");
            onSuccess();
            onClose();
        } catch (err) {
            toast.error(err.response?.data?.message || "Failed to add opening stock");
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col">
                <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50/50">
                    <div>
                        <h2 className="text-xl font-black text-gray-900 tracking-tight">Add Opening Stock</h2>
                        <p className="text-xs text-gray-500 font-medium mt-1">Manually seed inventory for existing medicines.</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="p-6 space-y-4 overflow-auto max-h-[70vh]">
                    {/* Medicine Selection */}
                    <div className="relative">
                        <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Select Medicine *</label>
                        <input 
                            type="text"
                            value={formData.medicineName}
                            onChange={(e) => handleSearchChange(e.target.value)}
                            className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-gray-900 outline-none transition-all"
                            placeholder="Type to search medicine..."
                            autoComplete="off"
                            required
                        />
                        {isLoadingResults && (
                            <div className="absolute right-3 top-[34px]">
                                <div className="animate-spin h-4 w-4 border-2 border-gray-900 border-t-transparent rounded-full"></div>
                            </div>
                        )}
                        {showResults && !isLoadingResults && (
                            <div className="absolute z-[60] left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-xl max-h-48 overflow-auto border-t-0 rounded-t-none">
                                {results.length > 0 ? results.map(r => (
                                    <div 
                                        key={r.id} 
                                        onClick={() => handleSelect(r)}
                                        className="px-4 py-3 hover:bg-gray-50 cursor-pointer text-sm border-b border-gray-50 last:border-0"
                                    >
                                        <div className="font-bold text-gray-900">{r.medicineName}</div>
                                        <div className="text-[10px] text-gray-500 font-medium uppercase">{r.medicineType} • {r.dosageForm} • {r.strength}</div>
                                    </div>
                                )) : (
                                    <div className="px-4 py-3 text-xs text-gray-400 font-medium italic">No medicines found matching "{formData.medicineName}"</div>
                                )}
                            </div>
                        )}
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Batch Number *</label>
                            <input 
                                type="text"
                                value={formData.batchNumber}
                                onChange={(e) => setFormData({...formData, batchNumber: e.target.value})}
                                className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-gray-900 outline-none"
                                placeholder="e.g. B1234"
                                required
                            />
                        </div>
                        <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Expiry Date</label>
                            <input 
                                type="date"
                                value={formData.expiryDate}
                                onChange={(e) => setFormData({...formData, expiryDate: e.target.value})}
                                className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-gray-900 outline-none"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Initial Quantity *</label>
                            <input 
                                type="number"
                                value={formData.currentQuantity}
                                onChange={(e) => setFormData({...formData, currentQuantity: e.target.value})}
                                className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-gray-900 outline-none font-bold text-gray-900"
                                placeholder="0"
                                required
                            />
                        </div>
                        <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1">Purchase Rate</label>
                            <input 
                                type="number"
                                value={formData.purchaseRate}
                                onChange={(e) => setFormData({...formData, purchaseRate: e.target.value})}
                                className="w-full border border-gray-300 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-gray-900 outline-none"
                                placeholder="0.00"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1 text-amber-600">MRP</label>
                            <input 
                                type="number"
                                value={formData.mrp}
                                onChange={(e) => setFormData({...formData, mrp: e.target.value})}
                                className="w-full border border-amber-200 bg-amber-50/30 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-amber-500 outline-none"
                                placeholder="0.00"
                            />
                        </div>
                        <div>
                            <label className="block text-[10px] font-black text-gray-400 uppercase tracking-widest mb-1 text-green-600">Selling Price</label>
                            <input 
                                type="number"
                                value={formData.sellingPrice}
                                onChange={(e) => setFormData({...formData, sellingPrice: e.target.value})}
                                className="w-full border border-green-200 bg-green-50/30 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-green-500 outline-none"
                                placeholder="0.00"
                            />
                        </div>
                    </div>
                </form>

                <div className="p-6 border-t border-gray-100 bg-gray-50/50 flex justify-end gap-3">
                    <button 
                        onClick={onClose}
                        className="px-6 py-2 text-sm font-bold text-gray-600 hover:text-gray-900"
                    >
                        Cancel
                    </button>
                    <button 
                        onClick={handleSubmit}
                        disabled={isSubmitting}
                        className="px-8 py-2.5 bg-gray-900 text-white rounded-xl text-sm font-bold hover:bg-gray-800 transition-all shadow-lg disabled:opacity-50"
                    >
                        {isSubmitting ? 'Adding...' : 'Add Stock'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default MedicineBatchForm;
