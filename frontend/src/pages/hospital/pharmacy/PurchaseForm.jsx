import React, { useState, useEffect } from 'react';
import medicinesApi from '../../../services/pharmacy/medicinesApi';
import suppliersApi from '../../../services/pharmacy/suppliersApi';
import { useToast } from '../../../context/ToastContext';

const PurchaseForm = ({ isOpen, onClose, onSave }) => {
    const toast = useToast();
    const [suppliers, setSuppliers] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [header, setHeader] = useState({
        supplierId: '',
        invoiceNumber: '',
        invoiceDate: new Date().toISOString().split('T')[0],
        subtotal: 0,
        discountAmount: 0,
        gstAmount: 0,
        totalAmount: 0,
    });

    const [items, setItems] = useState([
        { medicineId: '', medicineName: '', batchNumber: '', expiryDate: '', quantity: '', freeQuantity: 0, purchaseRate: '', mrp: '', sellingPrice: '', gstPercentage: 0, lineTotal: 0 }
    ]);

    const resetForm = () => {
        setHeader({
            supplierId: '',
            invoiceNumber: '',
            invoiceDate: new Date().toISOString().split('T')[0],
            subtotal: 0,
            discountAmount: 0,
            gstAmount: 0,
            totalAmount: 0,
        });
        setItems([
            { medicineId: '', medicineName: '', batchNumber: '', expiryDate: '', quantity: '', freeQuantity: 0, purchaseRate: '', mrp: '', sellingPrice: '', gstPercentage: 0, lineTotal: 0 }
        ]);
    };

    useEffect(() => {
        if (isOpen) {
            fetchSuppliers();
            resetForm(); // Clear old data when opening
        }
    }, [isOpen]);

    const fetchSuppliers = async () => {
        try {
            const resp = await suppliersApi.getAll('', 0, 100);
            setSuppliers(resp.content || []);
        } catch (err) {
            toast.error("Failed to load suppliers");
        }
    };

    const handleHeaderChange = (e) => {
        const { name, value } = e.target;
        setHeader(prev => ({ ...prev, [name]: value }));
    };

    const handleItemChange = (index, field, value) => {
        const newItems = [...items];
        newItems[index][field] = value;

        // Auto-calculate line total
        if (['quantity', 'purchaseRate', 'gstPercentage'].includes(field)) {
            const qty = parseFloat(newItems[index].quantity) || 0;
            const rate = parseFloat(newItems[index].purchaseRate) || 0;
            const gst = parseFloat(newItems[index].gstPercentage) || 0;
            
            const base = qty * rate;
            const gstAmt = (base * gst) / 100;
            newItems[index].lineTotal = parseFloat((base + gstAmt).toFixed(2));
        }

        setItems(newItems);
        calculateTotals(newItems, header.discountAmount);
    };

    const addItem = () => {
        setItems([...items, { medicineId: '', medicineName: '', batchNumber: '', expiryDate: '', quantity: '', freeQuantity: 0, purchaseRate: '', mrp: '', sellingPrice: '', gstPercentage: 0, lineTotal: 0 }]);
    };

    const removeItem = (index) => {
        if (items.length === 1) return;
        const newItems = items.filter((_, i) => i !== index);
        setItems(newItems);
        calculateTotals(newItems, header.discountAmount);
    };

    const calculateTotals = (currentItems, discount) => {
        let subtotal = 0;
        let gstTotal = 0;

        currentItems.forEach(item => {
            const qty = parseFloat(item.quantity) || 0;
            const rate = parseFloat(item.purchaseRate) || 0;
            const gst = parseFloat(item.gstPercentage) || 0;
            
            const lineBase = qty * rate;
            const lineGst = (lineBase * gst) / 100;
            
            subtotal += lineBase;
            gstTotal += lineGst;
        });

        const total = subtotal + gstTotal - (parseFloat(discount) || 0);

        setHeader(prev => ({
            ...prev,
            subtotal: parseFloat(subtotal.toFixed(2)),
            gstAmount: parseFloat(gstTotal.toFixed(2)),
            totalAmount: parseFloat(total.toFixed(2))
        }));
    };

    const handleSubmit = async (postingStatus) => {
        if (!header.supplierId || !header.invoiceNumber) {
            toast.error("Please fill Supplier and Invoice Number");
            return;
        }

        // Clean items: filter out invalid ones and cast strings to numbers/nulls
        const validItems = items
            .filter(it => it.medicineId && (parseFloat(it.quantity) > 0 || parseFloat(it.freeQuantity) > 0))
            .map(it => ({
                ...it,
                medicineId: parseInt(it.medicineId),
                quantity: parseFloat(it.quantity) || 0,
                freeQuantity: parseFloat(it.freeQuantity) || 0,
                purchaseRate: parseFloat(it.purchaseRate) || 0,
                mrp: parseFloat(it.mrp) || 0,
                sellingPrice: parseFloat(it.sellingPrice) || 0,
                gstPercentage: parseFloat(it.gstPercentage) || 0,
                lineTotal: parseFloat(it.lineTotal) || 0,
                expiryDate: it.expiryDate || null, // Convert empty string to null
                batchNumber: it.batchNumber || "UNBATCHED"
            }));

        if (validItems.length === 0) {
            toast.error("Please add at least one valid medicine item with quantity");
            return;
        }

        setIsSubmitting(true);
        try {
            const payload = {
                supplierId: parseInt(header.supplierId),
                invoiceNumber: header.invoiceNumber,
                invoiceDate: header.invoiceDate,
                subtotal: parseFloat(header.subtotal) || 0,
                discountAmount: parseFloat(header.discountAmount) || 0,
                gstAmount: parseFloat(header.gstAmount) || 0,
                totalAmount: parseFloat(header.totalAmount) || 0,
                postingStatus,
                items: validItems
            };
            await onSave(payload);
            onClose();
        } catch (err) {
            // Error handling is handled by the parent view or toast
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-6xl max-h-[95vh] overflow-hidden flex flex-col">
                <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50/50">
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900">New Purchase Inward</h2>
                        <p className="text-sm text-gray-500">Enter supplier invoice details and update inventory.</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                    </button>
                </div>

                <div className="flex-1 overflow-auto p-6 space-y-6">
                    {/* Header Section */}
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 bg-gray-50 p-4 rounded-xl border border-gray-100">
                        <div className="space-y-1">
                            <label className="text-xs font-bold text-gray-500 uppercase">Supplier</label>
                            <select 
                                name="supplierId" 
                                value={header.supplierId} 
                                onChange={handleHeaderChange}
                                className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-gray-900 focus:border-transparent outline-none transition-all"
                            >
                                <option value="">Select Supplier</option>
                                {suppliers.map(s => <option key={s.id} value={s.id}>{s.supplierName}</option>)}
                            </select>
                        </div>
                        <div className="space-y-1">
                            <label className="text-xs font-bold text-gray-500 uppercase">Invoice Number</label>
                            <input 
                                type="text" 
                                name="invoiceNumber" 
                                value={header.invoiceNumber} 
                                onChange={handleHeaderChange}
                                placeholder="e.g. INV-2024-001"
                                className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-gray-900 outline-none"
                            />
                        </div>
                        <div className="space-y-1">
                            <label className="text-xs font-bold text-gray-500 uppercase">Invoice Date</label>
                            <input 
                                type="date" 
                                name="invoiceDate" 
                                value={header.invoiceDate} 
                                onChange={handleHeaderChange}
                                className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-2 focus:ring-gray-900 outline-none"
                            />
                        </div>
                        <div className="space-y-1 text-right">
                            <label className="text-xs font-bold text-gray-500 uppercase">Net Payable</label>
                            <div className="text-3xl font-black text-gray-900">₹{header.totalAmount.toLocaleString()}</div>
                        </div>
                    </div>

                    {/* Items Section */}
                    <div className="border border-gray-200 rounded-xl overflow-hidden">
                        <table className="w-full text-sm text-left">
                            <thead className="bg-gray-900 text-white text-[10px] uppercase tracking-wider">
                                <tr>
                                    <th className="px-3 py-3 font-medium">Medicine</th>
                                    <th className="px-3 py-3 font-medium w-24">Batch</th>
                                    <th className="px-3 py-3 font-medium w-28">Expiry</th>
                                    <th className="px-3 py-3 font-medium w-20">Qty</th>
                                    <th className="px-3 py-3 font-medium w-20">Free</th>
                                    <th className="px-3 py-3 font-medium w-24">P. Rate</th>
                                    <th className="px-3 py-3 font-medium w-24 text-amber-400">MRP</th>
                                    <th className="px-3 py-3 font-medium w-24 text-green-400">S. Price</th>
                                    <th className="px-3 py-3 font-medium w-16">GST%</th>
                                    <th className="px-3 py-3 font-medium w-28 text-right">Total</th>
                                    <th className="px-3 py-3 font-medium w-10"></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {items.map((item, index) => (
                                    <tr key={index} className="hover:bg-gray-50/50 group transition-colors">
                                        <td className="px-3 py-3">
                                            <MedicineSearch 
                                                value={item.medicineName} 
                                                onSelect={(med) => handleItemChange(index, 'medicineId', med.id) || handleItemChange(index, 'medicineName', med.medicineName)} 
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <input 
                                                type="text" 
                                                value={item.batchNumber} 
                                                onChange={(e) => handleItemChange(index, 'batchNumber', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-sm py-1 bg-transparent outline-none"
                                                placeholder="Batch"
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <input 
                                                type="date" 
                                                value={item.expiryDate} 
                                                onChange={(e) => handleItemChange(index, 'expiryDate', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-[11px] py-1 bg-transparent outline-none"
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <input 
                                                type="number" 
                                                value={item.quantity} 
                                                onChange={(e) => handleItemChange(index, 'quantity', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-sm py-1 bg-transparent outline-none font-bold"
                                                placeholder="0"
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <input 
                                                type="number" 
                                                value={item.freeQuantity} 
                                                onChange={(e) => handleItemChange(index, 'freeQuantity', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-sm py-1 bg-transparent outline-none"
                                                placeholder="0"
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <input 
                                                type="number" 
                                                value={item.purchaseRate} 
                                                onChange={(e) => handleItemChange(index, 'purchaseRate', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-sm py-1 bg-transparent outline-none"
                                                placeholder="0.00"
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <input 
                                                type="number" 
                                                value={item.mrp} 
                                                onChange={(e) => handleItemChange(index, 'mrp', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-sm py-1 bg-amber-50/50 outline-none font-medium text-amber-700"
                                                placeholder="0.00"
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <input 
                                                type="number" 
                                                value={item.sellingPrice} 
                                                onChange={(e) => handleItemChange(index, 'sellingPrice', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-sm py-1 bg-green-50/50 outline-none font-medium text-green-700"
                                                placeholder="0.00"
                                            />
                                        </td>
                                        <td className="px-3 py-3">
                                            <select 
                                                value={item.gstPercentage} 
                                                onChange={(e) => handleItemChange(index, 'gstPercentage', e.target.value)}
                                                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-xs py-1 bg-transparent outline-none"
                                            >
                                                <option value="0">0%</option>
                                                <option value="5">5%</option>
                                                <option value="12">12%</option>
                                                <option value="18">18%</option>
                                                <option value="28">28%</option>
                                            </select>
                                        </td>
                                        <td className="px-3 py-3 text-right font-bold text-gray-900">
                                            ₹{item.lineTotal.toFixed(2)}
                                        </td>
                                        <td className="px-3 py-3 text-center">
                                            <button onClick={() => removeItem(index)} className="text-gray-300 hover:text-red-500 transition-colors">
                                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        <button 
                            onClick={addItem}
                            className="w-full py-3 bg-gray-50 text-gray-600 text-xs font-bold uppercase tracking-widest hover:bg-gray-100 transition-colors border-t border-gray-100 flex items-center justify-center gap-2"
                        >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
                            Add New Line Item
                        </button>
                    </div>

                    {/* Summary Row */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-start">
                        <div className="bg-blue-50/50 p-4 rounded-xl border border-blue-100">
                            <h4 className="text-xs font-bold text-blue-800 uppercase mb-3 flex items-center gap-2">
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                                Pricing Notes
                            </h4>
                            <p className="text-xs text-blue-700/80 leading-relaxed">
                                Updating purchase rates here will update the <b>Medicine Master</b> and <b>Batch Inventory</b> default pricing for future sales. Ensure Batch Number and Expiry dates match the physical box.
                            </p>
                        </div>
                        <div className="space-y-3 bg-gray-50 p-6 rounded-xl border border-gray-200 ml-auto w-full max-w-sm">
                            <div className="flex justify-between text-sm text-gray-500">
                                <span>Subtotal</span>
                                <span>₹{header.subtotal.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between text-sm text-gray-500">
                                <span>GST Total</span>
                                <span>₹{header.gstAmount.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between text-sm items-center">
                                <span>Discount</span>
                                <input 
                                    type="number" 
                                    value={header.discountAmount} 
                                    onChange={(e) => {
                                        const val = e.target.value;
                                        setHeader(prev => ({ ...prev, discountAmount: val }));
                                        calculateTotals(items, val);
                                    }}
                                    className="w-24 border border-gray-300 rounded px-2 py-1 text-right text-sm focus:ring-1 focus:ring-gray-900 outline-none"
                                />
                            </div>
                            <div className="border-t border-gray-300 pt-3 flex justify-between items-center">
                                <span className="font-bold text-gray-900">Total Payable</span>
                                <span className="text-xl font-black text-gray-900">₹{header.totalAmount.toFixed(2)}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="p-6 border-t border-gray-100 bg-gray-50/50 flex justify-end gap-3">
                    <button 
                        onClick={onClose}
                        className="px-6 py-2.5 border border-gray-300 rounded-lg text-sm font-bold text-gray-600 hover:bg-white transition-all shadow-sm"
                    >
                        Cancel
                    </button>
                    <button 
                        onClick={() => handleSubmit('DRAFT')}
                        disabled={isSubmitting}
                        className="px-6 py-2.5 bg-white border border-gray-900 text-gray-900 rounded-lg text-sm font-bold hover:bg-gray-50 transition-all shadow-sm disabled:opacity-50"
                    >
                        Save as Draft
                    </button>
                    <button 
                        onClick={() => handleSubmit('POSTED')}
                        disabled={isSubmitting}
                        className="px-8 py-2.5 bg-gray-900 text-white rounded-lg text-sm font-bold hover:bg-gray-800 transition-all shadow-lg hover:shadow-gray-200 disabled:opacity-50"
                    >
                        {isSubmitting ? 'Posting...' : 'Post & Update Inventory'}
                    </button>
                </div>
            </div>
        </div>
    );
};

// Helper Search Component
const MedicineSearch = ({ value, onSelect }) => {
    const [query, setQuery] = useState(value || '');
    const [results, setResults] = useState([]);
    const [showResults, setShowResults] = useState(false);

    useEffect(() => {
        setQuery(value || '');
    }, [value]);

    useEffect(() => {
        const timer = setTimeout(async () => {
            if (query.length > 1) {
                try {
                    const data = await medicinesApi.autocomplete(query);
                    setResults(data || []);
                    setShowResults(true);
                } catch (err) {}
            } else {
                setResults([]);
            }
        }, 300);
        return () => clearTimeout(timer);
    }, [query]);

    return (
        <div className="relative">
            <input 
                type="text" 
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onFocus={() => query.length > 1 && setShowResults(true)}
                className="w-full border-b border-transparent group-hover:border-gray-200 focus:border-gray-900 focus:ring-0 text-sm py-1 bg-transparent outline-none font-medium text-gray-900"
                placeholder="Search Medicine..."
            />
            {showResults && results.length > 0 && (
                <div className="absolute z-[60] left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-2xl max-h-48 overflow-auto py-1">
                    {results.map((r) => (
                        <div 
                            key={r.id} 
                            onClick={() => {
                                onSelect(r);
                                setQuery(r.medicineName);
                                setShowResults(false);
                            }}
                            className="px-4 py-2 hover:bg-gray-50 cursor-pointer text-sm"
                        >
                            <div className="font-bold text-gray-900">{r.medicineName}</div>
                            <div className="text-[10px] text-gray-500 uppercase">{r.medicineType} • {r.strength}</div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export default PurchaseForm;
