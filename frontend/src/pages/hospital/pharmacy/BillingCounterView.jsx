import React, { useState, useEffect, useCallback } from 'react';
import inventoryApi from '../../../services/pharmacy/inventoryApi';
import hospitalService from '../../../services/hospitalService';
import salesApi from '../../../services/pharmacy/salesApi';
import { useToast } from '../../../context/ToastContext';
import LoadingSpinner from '../../../components/LoadingSpinner';

const BillingCounterView = ({ initialData }) => {
    const toast = useToast();
    const [barcodeMode, setBarcodeMode] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Patient State
    const [patientSearch, setPatientSearch] = useState('');
    const [patientResults, setPatientResults] = useState([]);
    const [selectedPatient, setSelectedPatient] = useState(null);

    // Item Search State
    const [itemSearch, setItemSearch] = useState('');
    const [itemResults, setItemResults] = useState([]);
    const [selectedBatch, setSelectedBatch] = useState(null);
    const [sellQty, setSellQty] = useState(1);

    // Bill State
    const [billItems, setBillItems] = useState([]);
    const [remarks, setRemarks] = useState('');
    const [paymentMethod, setPaymentMethod] = useState('CASH');

    // Prescription State (from initialData)
    const [activePrescription, setActivePrescription] = useState(null);

    useEffect(() => {
        if (initialData) {
            if (initialData.patient) {
                setSelectedPatient(initialData.patient);
                setPatientSearch(initialData.patient.patientName);
            }
            if (initialData.prescription) {
                setActivePrescription(initialData.prescription);
            }
        }
    }, [initialData]);

    // Totals
    const [totals, setTotals] = useState({
        subtotal: 0,
        tax: 0,
        discount: 0,
        net: 0
    });

    // Patient Search
    useEffect(() => {
        const timer = setTimeout(async () => {
            if (patientSearch.length > 2) {
                try {
                    const data = await hospitalService.getPatients(patientSearch, 0, 5);
                    setPatientResults(data.content || []);
                } catch (err) {}
            } else {
                setPatientResults([]);
            }
        }, 300);
        return () => clearTimeout(timer);
    }, [patientSearch]);

    // Item Search
    useEffect(() => {
        const timer = setTimeout(async () => {
            if (itemSearch.length > 1) {
                try {
                    const data = await inventoryApi.getInventory(itemSearch, 0, 10);
                    // Filter out items with 0 quantity
                    setItemResults(data.content?.filter(i => i.currentQuantity > 0) || []);
                } catch (err) {}
            } else {
                setItemResults([]);
            }
        }, 300);
        return () => clearTimeout(timer);
    }, [itemSearch]);

    const calculateTotals = useCallback((items) => {
        let sub = 0;
        let tax = 0;
        let disc = 0;

        items.forEach(item => {
            const lineSub = item.qty * item.price;
            const lineTax = (lineSub * (item.taxPercent || 0)) / 100;
            sub += lineSub;
            tax += lineTax;
            disc += (item.discount || 0);
        });

        setTotals({
            subtotal: sub,
            tax: tax,
            discount: disc,
            net: sub + tax - disc
        });
    }, []);

    const addToCart = () => {
        if (!selectedBatch) {
            toast.error("Please select a medicine batch first");
            return;
        }

        if (sellQty <= 0 || sellQty > selectedBatch.currentQuantity) {
            toast.error("Invalid quantity or insufficient stock");
            return;
        }

        // Check if item already in cart
        const existingIdx = billItems.findIndex(i => i.batchId === selectedBatch.id);
        let newItems;
        if (existingIdx > -1) {
            newItems = [...billItems];
            newItems[existingIdx].qty += parseFloat(sellQty);
            newItems[existingIdx].total = newItems[existingIdx].qty * newItems[existingIdx].price;
        } else {
            const newItem = {
                batchId: selectedBatch.id,
                medicineId: selectedBatch.medicineId,
                name: selectedBatch.medicine?.medicineName,
                batch: selectedBatch.batchNumber,
                expiry: selectedBatch.expiryDate,
                qty: parseFloat(sellQty),
                price: selectedBatch.sellingPrice,
                taxPercent: selectedBatch.medicine?.gstPercentage || 0,
                discount: 0,
                total: parseFloat(sellQty) * selectedBatch.sellingPrice
            };
            newItems = [...billItems, newItem];
        }

        setBillItems(newItems);
        calculateTotals(newItems);
        
        // Reset Item Pickers
        setItemSearch('');
        setSelectedBatch(null);
        setSellQty(1);
    };

    const removeItem = (idx) => {
        const newItems = billItems.filter((_, i) => i !== idx);
        setBillItems(newItems);
        calculateTotals(newItems);
    };

    const handleCompleteSale = async () => {
        if (billItems.length === 0) {
            toast.error("Bill is empty!");
            return;
        }

        setIsSubmitting(true);
        try {
            const payload = {
                patientId: selectedPatient?.id,
                paymentMethod: paymentMethod,
                isIpdBill: paymentMethod === 'IPD_BILL',
                ipdAdmissionId: selectedPatient?.activeIpdAdmissionId, // Assuming this field exists
                subtotal: totals.subtotal,
                taxAmount: totals.tax,
                discountAmount: totals.discount,
                netAmount: totals.net,
                items: billItems.map(i => ({
                    medicineId: i.medicineId,
                    medicineBatchId: i.batchId,
                    quantity: i.qty,
                    unitPrice: i.price,
                    taxPercentage: i.taxPercent,
                    taxAmount: (i.qty * i.price * i.taxPercent) / 100,
                    discountAmount: i.discount,
                    totalAmount: i.total
                }))
            };

            await salesApi.create(payload);
            toast.success("Bill completed successfully!");
            
            // Reset everything
            setBillItems([]);
            setSelectedPatient(null);
            setPatientSearch('');
            setRemarks('');
            setTotals({ subtotal: 0, tax: 0, discount: 0, net: 0 });
        } catch (err) {
            toast.error(err.response?.data?.message || "Failed to complete sale");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="h-full flex flex-col gap-4 -mt-2">
            {/* Top Patient Bar */}
            <div className="bg-white border border-gray-200 rounded-lg p-3 flex flex-wrap items-center justify-between gap-4 shadow-sm">
                <div className="flex items-center gap-4 flex-1">
                    <div className="relative w-80">
                         <input 
                            type="text" 
                            placeholder="Search Patient Name / Phone / PID..." 
                            value={patientSearch}
                            onChange={(e) => setPatientSearch(e.target.value)}
                            className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg w-full text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                         />
                         <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 transform -translate-y-1/2" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
                         
                         {patientResults.length > 0 && !selectedPatient && (
                             <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-xl z-30 max-h-48 overflow-auto">
                                 {patientResults.map(p => (
                                     <div 
                                        key={p.id} 
                                        onClick={() => { setSelectedPatient(p); setPatientSearch(p.patientName); setPatientResults([]); }}
                                        className="p-3 hover:bg-blue-50 cursor-pointer border-b last:border-0"
                                     >
                                         <div className="font-bold text-gray-900 text-sm">{p.patientName}</div>
                                         <div className="text-[10px] text-gray-500">{p.phone} • PID: {p.pid}</div>
                                     </div>
                                 ))}
                             </div>
                         )}
                    </div>

                    {selectedPatient && (
                        <>
                            <div className="h-6 w-px bg-gray-200"></div>
                            <div className="text-sm bg-blue-50 px-3 py-1 rounded-full border border-blue-100 flex items-center gap-2">
                                <span className="text-blue-600 font-medium">Selected:</span>
                                <span className="font-bold text-blue-900">{selectedPatient.patientName}</span>
                                <button onClick={() => setSelectedPatient(null)} className="text-blue-400 hover:text-blue-600 ml-1">×</button>
                            </div>
                        </>
                    )}
                </div>
                <div className="flex gap-2">
                    {selectedPatient?.isIpd && <span className="bg-purple-50 text-purple-700 text-xs font-bold px-2 py-1 rounded border border-purple-100">IPD Case</span>}
                    <span className="bg-gray-100 text-gray-700 text-xs font-bold px-2 py-1 rounded">Walk-in</span>
                </div>
            </div>

            <div className="flex flex-1 flex-col lg:flex-row gap-4 overflow-hidden" style={{ minHeight: 'calc(100vh - 240px)' }}>
                
                {/* LEFT SECTION: Item Discovery & Input */}
                <div className="lg:w-1/3 flex flex-col gap-4">
                    <div className="bg-white border border-gray-200 rounded-lg p-4 flex flex-col h-full shadow-sm">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="font-bold text-gray-800">Add Medicine</h3>
                            <label className="flex items-center gap-2 cursor-pointer">
                                <div className={`w-8 h-4 rounded-full transition-colors relative ${barcodeMode ? 'bg-green-500' : 'bg-gray-300'}`} onClick={() => setBarcodeMode(!barcodeMode)}>
                                    <div className={`absolute top-0.5 left-0.5 bg-white w-3 h-3 rounded-full transition-transform ${barcodeMode ? 'translate-x-4' : ''}`}></div>
                                </div>
                                <span className="text-xs font-medium text-gray-600">Barcode</span>
                            </label>
                        </div>
                        
                        <div className="space-y-4">
                            <div className="relative">
                                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Search Medicine</label>
                                <input 
                                    type="text" 
                                    placeholder="Type name / batch..." 
                                    value={itemSearch}
                                    onChange={(e) => setItemSearch(e.target.value)}
                                    className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none bg-gray-50/50 transition-all" 
                                />
                                {itemResults.length > 0 && (
                                    <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-xl z-20 max-h-60 overflow-auto py-1">
                                        {itemResults.map(item => (
                                            <div 
                                                key={item.id} 
                                                onClick={() => { setSelectedBatch(item); setItemSearch(item.medicine?.medicineName); setItemResults([]); }}
                                                className="px-4 py-2 hover:bg-blue-50 cursor-pointer border-b border-gray-50 last:border-0"
                                            >
                                                <div className="flex justify-between font-bold text-sm text-gray-900">
                                                    <span>{item.medicine?.medicineName}</span>
                                                    <span>₹{item.sellingPrice}</span>
                                                </div>
                                                <div className="flex justify-between text-[11px] text-gray-500 mt-0.5">
                                                    <span>Batch: {item.batchNumber} • Exp: {item.expiryDate}</span>
                                                    <span className={`${item.currentQuantity < 50 ? 'text-amber-600' : 'text-green-600'} font-bold`}>Stock: {item.currentQuantity}</span>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                            {selectedBatch && (
                                <div className="p-3 bg-blue-50 rounded-lg border border-blue-100 animate-in fade-in zoom-in-95 duration-200">
                                    <div className="flex justify-between items-center mb-3">
                                        <div className="text-xs font-bold text-blue-900">Selected Batch</div>
                                        <button onClick={() => setSelectedBatch(null)} className="text-blue-400 hover:text-blue-600">×</button>
                                    </div>
                                    <div className="grid grid-cols-2 gap-3">
                                        <div>
                                            <label className="block text-[10px] text-blue-600 font-bold uppercase mb-1">Selling Qty</label>
                                            <input 
                                                type="number" 
                                                value={sellQty}
                                                onChange={(e) => setSellQty(e.target.value)}
                                                className="w-full px-3 py-2 border border-blue-200 rounded-lg text-sm font-bold text-blue-900 outline-none" 
                                                autoFocus
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-[10px] text-blue-600 font-bold uppercase mb-1">Max Avail.</label>
                                            <div className="px-3 py-2 bg-white rounded-lg text-sm font-bold text-gray-600 border border-blue-100">{selectedBatch.currentQuantity}</div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            <button 
                                onClick={addToCart}
                                disabled={!selectedBatch}
                                className="w-full py-3 bg-gray-900 text-white font-bold text-sm rounded-xl hover:bg-gray-800 disabled:bg-gray-200 disabled:text-gray-400 mt-2 transition-all active:scale-95 shadow-lg shadow-gray-200"
                            >
                                Add to Bill (Enter)
                            </button>
                        </div>

                        <hr className="my-6 border-dashed border-gray-200" />

                        {/* Prescription Hint Panel */}
                        <div className="flex-1 overflow-hidden flex flex-col">
                            <h4 className="text-[10px] font-black text-gray-400 uppercase tracking-widest mb-3 flex items-center gap-2">
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                                Doctor Prescriptions
                            </h4>
                            <div className="bg-gray-50 rounded-xl border border-gray-100 flex-1 overflow-y-auto p-2 space-y-2 text-xs">
                                {!activePrescription ? (
                                    <div className="h-full flex items-center justify-center text-center p-4 text-gray-400 italic">
                                        Select a patient or process a prescription to see items here.
                                    </div>
                                ) : (
                                    activePrescription.medicines.map((m, idx) => (
                                        <div key={idx} className="bg-white p-3 rounded-lg border-l-4 border-l-blue-500 shadow-sm">
                                            <div className="flex justify-between font-bold text-gray-900 mb-1">
                                                <span>{m.name}</span>
                                                <span className="text-[10px] text-blue-600 uppercase">{m.frequency}</span>
                                            </div>
                                            <div className="text-[10px] text-gray-500">{m.dosage} • {m.duration}</div>
                                            {m.instructions && <div className="text-[9px] text-gray-400 italic mt-1 font-medium">{m.instructions}</div>}
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </div>
                </div>

                {/* CENTER SECTION: Bill Table */}
                <div className="lg:flex-1 bg-white border border-gray-200 rounded-lg flex flex-col overflow-hidden shadow-sm">
                    <div className="px-4 py-4 border-b border-gray-100 bg-gray-50/50 flex justify-between items-center">
                         <h3 className="font-bold text-gray-800 flex items-center gap-2">
                            <svg className="w-5 h-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" /></svg>
                            Current Sale Items
                         </h3>
                         <span className="bg-blue-100 text-blue-700 px-2 py-0.5 rounded text-[10px] font-bold">Total Items: {billItems.length}</span>
                    </div>
                    <div className="flex-1 overflow-auto">
                         <table className="min-w-full text-sm text-left">
                             <thead className="bg-white text-[10px] text-gray-400 font-bold uppercase tracking-widest sticky top-0 z-10 border-b">
                                 <tr>
                                     <th className="px-6 py-4 font-medium">#</th>
                                     <th className="px-6 py-4 font-medium">Medicine Name / Batch</th>
                                     <th className="px-6 py-4 font-medium text-center">Qty</th>
                                     <th className="px-6 py-4 font-medium text-right">MRP</th>
                                     <th className="px-6 py-4 font-medium text-right">Total</th>
                                     <th className="px-6 py-4 text-center"></th>
                                 </tr>
                             </thead>
                             <tbody className="divide-y divide-gray-50">
                                 {billItems.length > 0 ? billItems.map((item, idx) => (
                                     <tr key={idx} className="hover:bg-gray-50 transition-colors">
                                         <td className="px-6 py-4 text-gray-400">{idx+1}</td>
                                         <td className="px-6 py-4">
                                             <div className="font-bold text-gray-900">{item.name}</div>
                                             <div className="text-[10px] text-gray-500 mt-0.5 flex gap-2">
                                                <span className="bg-gray-100 px-1 rounded">Batch: {item.batch}</span>
                                                <span className="bg-gray-100 px-1 rounded text-red-500">Exp: {item.expiry}</span>
                                             </div>
                                         </td>
                                         <td className="px-6 py-4 text-center">
                                             <div className="font-black text-gray-900">{item.qty}</div>
                                         </td>
                                         <td className="px-6 py-4 text-right text-gray-700 font-medium">₹{item.price.toFixed(2)}</td>
                                         <td className="px-6 py-4 text-right font-black text-gray-900">₹{item.total.toFixed(2)}</td>
                                         <td className="px-6 py-4 text-center">
                                             <button onClick={() => removeItem(idx)} className="text-gray-300 hover:text-red-500 p-1 rounded-full hover:bg-red-50 transition-all">
                                                 <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                                             </button>
                                         </td>
                                     </tr>
                                 )) : (
                                     <tr>
                                         <td colSpan="6" className="px-6 py-20 text-center text-gray-400 italic">
                                             <div className="flex flex-col items-center gap-2">
                                                <svg className="w-12 h-12 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" /></svg>
                                                <span>Your billing cart is empty. Start by adding items from the left panel.</span>
                                             </div>
                                         </td>
                                     </tr>
                                 )}
                             </tbody>
                         </table>
                    </div>
                </div>

                {/* RIGHT SECTION: Checkout / Payment */}
                <div className="lg:w-80 space-y-4 flex flex-col">
                    
                    {/* Summary Card */}
                    <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden flex-shrink-0 shadow-sm">
                        <div className="bg-gray-900 text-white p-5 text-center">
                            <div className="text-[10px] uppercase font-black tracking-widest opacity-50 mb-1">Net Amount Payable</div>
                            <div className="text-4xl font-black">₹{totals.net.toFixed(2)}</div>
                        </div>
                        <div className="p-5 space-y-3 bg-white border-b border-gray-200 text-sm">
                            <div className="flex justify-between text-gray-500 font-medium">
                                <span>Sub Total</span>
                                <span>₹{totals.subtotal.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between text-gray-500 font-medium">
                                <span>Discount</span>
                                <span className="text-green-600">-₹{totals.discount.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between text-gray-500 font-medium">
                                <span>Tax (GST)</span>
                                <span>+₹{totals.tax.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between font-black text-gray-900 pt-3 border-t border-dashed border-gray-200 text-lg">
                                <span>Final Bill</span>
                                <span>₹{totals.net.toFixed(2)}</span>
                            </div>
                        </div>
                        <div className="p-5 bg-gray-50/50">
                            <label className="block text-[10px] font-black text-gray-400 mb-3 uppercase tracking-wider">Select Payment Method</label>
                            <div className="grid grid-cols-2 gap-2">
                                <button 
                                    onClick={() => setPaymentMethod('CASH')}
                                    className={`p-3 text-[11px] font-black rounded-xl border transition-all flex flex-col items-center gap-1 ${paymentMethod === 'CASH' ? 'bg-gray-900 text-white border-gray-900 shadow-md' : 'bg-white text-gray-600 border-gray-200 hover:border-gray-900'}`}
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm-5-9a2 2 0 11-4 0 2 2 0 014 0z" /></svg>
                                    Cash
                                </button>
                                <button 
                                    onClick={() => setPaymentMethod('UPI')}
                                    className={`p-3 text-[11px] font-black rounded-xl border transition-all flex flex-col items-center gap-1 ${paymentMethod === 'UPI' ? 'bg-gray-900 text-white border-gray-900 shadow-md' : 'bg-white text-gray-600 border-gray-200 hover:border-gray-900'}`}
                                >
                                     <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" /></svg>
                                     UPI / QR
                                </button>
                                <button 
                                    onClick={() => setPaymentMethod('IPD_BILL')}
                                    className={`p-3 text-[11px] font-black rounded-xl border transition-all col-span-2 flex items-center justify-center gap-2 ${paymentMethod === 'IPD_BILL' ? 'bg-gray-900 text-white border-gray-900 shadow-md' : 'bg-white text-gray-600 border-gray-200 hover:border-gray-900'}`}
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" /></svg>
                                    Post to IPD Admission Bill
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Remarks */}
                    <div className="bg-white border border-gray-200 rounded-2xl p-4 flex-1 shadow-sm">
                        <label className="block text-[10px] font-black text-gray-400 uppercase mb-2 tracking-wider">Internal Remarks</label>
                        <textarea 
                            value={remarks}
                            onChange={(e) => setRemarks(e.target.value)}
                            className="w-full h-full min-h-[100px] p-3 border border-gray-100 rounded-xl text-xs outline-none focus:ring-2 focus:ring-blue-500 bg-gray-50/50 resize-none transition-all" 
                            placeholder="Add internal notes about this sale..."
                        ></textarea>
                    </div>

                </div>
            </div>

            {/* BOTTOM ACTION BAR */}
            <div className="bg-white border border-gray-200 p-4 rounded-xl shadow-xl flex items-center justify-between">
                <div className="flex gap-3">
                    <button className="px-6 py-2.5 border border-gray-200 text-gray-600 text-sm font-bold rounded-xl hover:bg-gray-50 transition-all active:scale-95">
                        Hold Bill (F4)
                    </button>
                    <button 
                        onClick={() => { setBillItems([]); setTotals({ subtotal: 0, tax: 0, discount: 0, net: 0 }); }}
                        className="px-6 py-2.5 bg-red-50 text-red-600 text-sm font-bold rounded-xl hover:bg-red-100 transition-all active:scale-95"
                    >
                        Clear Cart
                    </button>
                </div>
                
                <div className="flex gap-4 items-center">
                    <div className="text-right hidden sm:block">
                        <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest">Total to Pay</p>
                        <p className="text-2xl font-black text-gray-900">₹{totals.net.toFixed(2)}</p>
                    </div>
                     <button className="px-6 py-3 border border-gray-200 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-50 flex items-center gap-2 transition-all active:scale-95 shadow-sm">
                         <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" /></svg>
                         Print Invoice
                     </button>
                     <button 
                        onClick={handleCompleteSale}
                        disabled={isSubmitting || billItems.length === 0}
                        className={`px-8 py-3 text-white text-sm font-bold rounded-xl flex items-center gap-3 transition-all active:scale-95 shadow-xl shadow-green-200 ${isSubmitting ? 'bg-gray-400' : 'bg-green-600 hover:bg-green-700'}`}
                     >
                         {isSubmitting ? <LoadingSpinner size="xs" color="white" /> : <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" /></svg>}
                         Complete & Save Sale (F12)
                     </button>
                </div>
            </div>
        </div>
    );
};

export default BillingCounterView;
