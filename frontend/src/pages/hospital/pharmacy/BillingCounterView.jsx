import React, { useState } from 'react';

const BillingCounterView = () => {
    const [barcodeMode, setBarcodeMode] = useState(false);
    
    const mockBillItems = [
        { id: 1, name: 'Augmentin 625 Duo Tablet', batch: 'BT9092', expiry: '12/2026', qty: 10, mrp: 20.5, gst: 12, disc: 0, total: 205.00 },
        { id: 2, name: 'Pantop D SR Capsule', batch: 'BN1001', expiry: '09/2025', qty: 15, mrp: 12.0, gst: 12, disc: 5, total: 171.00 },
    ];

    const subTotal = 376.00;
    const taxTotal = 45.12;
    const discTotal = 9.00;
    const netTotal = 412.12;

    return (
        <div className="h-full flex flex-col gap-4 -mt-2">
            {/* Top Patient Bar */}
            <div className="bg-white border border-gray-200 rounded-lg p-3 flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center gap-4">
                    <div className="relative">
                         <input 
                            type="text" 
                            placeholder="Scan RX Barcode / Search Patient..." 
                            className="pl-10 pr-4 py-2 border border-gray-300 rounded w-80 text-sm focus:ring-1 focus:ring-gray-900 focus:border-gray-900 outline-none transition-all"
                         />
                         <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 transform -translate-y-1/2" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
                    </div>
                    <div className="h-6 w-px bg-gray-200"></div>
                    <div className="text-sm">
                        <span className="text-gray-500">Active Patient: </span>
                        <span className="font-bold text-gray-900">Rahul Sharma</span>
                        <span className="ml-2 text-xs text-gray-400 font-mono">PID-44901</span>
                    </div>
                </div>
                <div className="flex gap-2">
                    <span className="bg-purple-50 text-purple-700 text-xs font-bold px-2 py-1 rounded border border-purple-100">IPD Case</span>
                    <span className="bg-gray-100 text-gray-700 text-xs font-bold px-2 py-1 rounded">Walk-in</span>
                </div>
            </div>

            <div className="flex flex-1 flex-col lg:flex-row gap-4 overflow-hidden" style={{ minHeight: 'calc(100vh - 240px)' }}>
                
                {/* LEFT SECTION: Item Discovery & Input */}
                <div className="lg:w-1/3 flex flex-col gap-4">
                    <div className="bg-white border border-gray-200 rounded-lg p-4 flex flex-col h-full">
                        <div className="flex justify-between items-center mb-3">
                            <h3 className="font-bold text-gray-800">Add Item</h3>
                            <label className="flex items-center gap-2 cursor-pointer">
                                <div className={`w-8 h-4 rounded-full transition-colors relative ${barcodeMode ? 'bg-green-500' : 'bg-gray-300'}`} onClick={() => setBarcodeMode(!barcodeMode)}>
                                    <div className={`absolute top-0.5 left-0.5 bg-white w-3 h-3 rounded-full transition-transform ${barcodeMode ? 'translate-x-4' : ''}`}></div>
                                </div>
                                <span className="text-xs font-medium text-gray-600">Barcode Mode</span>
                            </label>
                        </div>
                        
                        <div className="space-y-3">
                            <div>
                                <label className="block text-xs font-medium text-gray-500 mb-1">Search Medicine Name / Formula</label>
                                <input type="text" placeholder="Type min 3 chars..." className="w-full px-3 py-2 border border-gray-300 rounded text-sm focus:border-gray-900 outline-none bg-gray-50" />
                            </div>

                            {/* Results Placeholder */}
                            <div className="border border-gray-100 rounded bg-gray-50 p-1 max-h-48 overflow-y-auto">
                                <div className="bg-white p-2 border border-gray-200 rounded shadow-sm mb-1 cursor-pointer hover:bg-gray-50">
                                    <div className="flex justify-between font-medium text-sm text-gray-900">
                                        <span>Paracetamol 650mg</span>
                                        <span>₹1.20</span>
                                    </div>
                                    <div className="flex justify-between text-[11px] text-gray-500 mt-0.5">
                                        <span>Generic Corp</span>
                                        <span className="text-green-600 font-bold">Stock: 1420</span>
                                    </div>
                                </div>
                                <div className="p-2 cursor-pointer text-sm text-gray-600 hover:bg-gray-100 rounded transition">
                                    Paracetamol 500mg <span className="text-[10px] ml-2 bg-gray-100 px-1 rounded">Brand B</span>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3 pt-2">
                                <div>
                                    <label className="block text-xs font-medium text-gray-500 mb-1">Select Batch</label>
                                    <select className="w-full px-3 py-2 border border-gray-300 rounded text-sm focus:border-gray-900 outline-none bg-white">
                                        <option>BN-002 (Exp: 10/25) - 40</option>
                                        <option>BN-001 (Exp: 06/25) - 12</option>
                                    </select>
                                </div>
                                <div>
                                    <label className="block text-xs font-medium text-gray-500 mb-1">Qty</label>
                                    <input type="number" defaultValue="1" className="w-full px-3 py-2 border border-gray-300 rounded text-sm focus:border-gray-900 outline-none" />
                                </div>
                            </div>

                            <button className="w-full py-2.5 bg-gray-900 text-white font-bold text-sm rounded hover:bg-gray-800 mt-2 transition-all active:scale-95 shadow-sm">
                                Add to Cart (Enter)
                            </button>
                        </div>

                        <hr className="my-4 border-dashed border-gray-200" />

                        {/* Prescription Medicines Panel */}
                        <div className="flex-1 overflow-hidden flex flex-col">
                            <h4 className="text-xs font-bold text-gray-600 uppercase tracking-wider mb-2 flex items-center gap-1">
                                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                                Prescribed By Doctor
                            </h4>
                            <div className="bg-gray-50 rounded border border-gray-100 flex-1 overflow-y-auto p-2 space-y-2 text-xs">
                                <div className="bg-white border border-l-4 border-l-blue-500 p-2 flex justify-between items-center rounded shadow-sm">
                                    <div>
                                        <p className="font-bold text-gray-800">Augmentin 625</p>
                                        <p className="text-gray-500 mt-0.5">1-0-1 | 5 Days</p>
                                    </div>
                                    <span className="bg-green-100 text-green-800 px-1.5 py-0.5 rounded font-bold text-[10px]">ADDED</span>
                                </div>
                                <div className="bg-white border border-l-4 border-l-gray-300 p-2 flex justify-between items-center rounded hover:border-l-blue-500 cursor-pointer group">
                                    <div>
                                        <p className="font-bold text-gray-800 group-hover:text-blue-600">Dolo 650</p>
                                        <p className="text-gray-500 mt-0.5">SOS | 2 Days</p>
                                    </div>
                                    <button className="opacity-0 group-hover:opacity-100 bg-gray-900 text-white px-2 py-1 rounded font-bold">Add</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* CENTER SECTION: Bill Table */}
                <div className="lg:flex-1 bg-white border border-gray-200 rounded-lg flex flex-col overflow-hidden">
                    <div className="px-4 py-3 border-b border-gray-100 bg-gray-50 flex justify-between items-center">
                         <h3 className="font-bold text-gray-800">Current Sale Items</h3>
                         <span className="text-xs text-gray-500 font-medium">Total Items: {mockBillItems.length}</span>
                    </div>
                    <div className="flex-1 overflow-auto">
                         <table className="min-w-full text-sm text-left">
                             <thead className="bg-white text-xs text-gray-500 uppercase sticky top-0 z-10 border-b">
                                 <tr>
                                     <th className="px-4 py-3 font-medium">#</th>
                                     <th className="px-4 py-3 font-medium">Medicine Name / Batch</th>
                                     <th className="px-4 py-3 font-medium text-center">Qty</th>
                                     <th className="px-4 py-3 font-medium text-right">MRP</th>
                                     <th className="px-4 py-3 font-medium text-right">Disc %</th>
                                     <th className="px-4 py-3 font-medium text-right">GST %</th>
                                     <th className="px-4 py-3 font-medium text-right">Total</th>
                                     <th className="px-4 py-3 text-center"></th>
                                 </tr>
                             </thead>
                             <tbody className="divide-y divide-gray-100">
                                 {mockBillItems.map((item, idx) => (
                                     <tr key={item.id} className="hover:bg-gray-50">
                                         <td className="px-4 py-3 text-gray-400">{idx+1}</td>
                                         <td className="px-4 py-3">
                                             <div className="font-semibold text-gray-900">{item.name}</div>
                                             <div className="text-[11px] text-gray-500 mt-0.5">Batch: {item.batch} • Exp: {item.expiry}</div>
                                         </td>
                                         <td className="px-4 py-3 text-center">
                                             <input type="number" defaultValue={item.qty} className="w-12 text-center border border-gray-200 rounded py-0.5 text-sm" />
                                         </td>
                                         <td className="px-4 py-3 text-right text-gray-700">{item.mrp.toFixed(2)}</td>
                                         <td className="px-4 py-3 text-right text-gray-700">
                                             <input type="number" defaultValue={item.disc} className="w-10 text-right border-b border-gray-200 text-xs focus:border-gray-900 outline-none" />
                                         </td>
                                         <td className="px-4 py-3 text-right text-gray-500 text-xs">{item.gst}%</td>
                                         <td className="px-4 py-3 text-right font-bold text-gray-900">₹{item.total.toFixed(2)}</td>
                                         <td className="px-4 py-3 text-center">
                                             <button className="text-red-300 hover:text-red-600">
                                                 <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                                             </button>
                                         </td>
                                     </tr>
                                 ))}
                                 {/* Add more placeholder empty row space if necessary */}
                             </tbody>
                         </table>
                    </div>
                </div>

                {/* RIGHT SECTION: Checkout / Payment */}
                <div className="lg:w-80 space-y-4 flex flex-col">
                    
                    {/* Summary Card */}
                    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden flex-shrink-0">
                        <div className="bg-gray-900 text-white p-4 text-center">
                            <div className="text-xs uppercase tracking-widest opacity-70 mb-1">Amount Payable</div>
                            <div className="text-3xl font-black">₹{netTotal.toFixed(2)}</div>
                        </div>
                        <div className="p-4 space-y-3 bg-white border-b border-gray-200 text-sm">
                            <div className="flex justify-between text-gray-600">
                                <span>Sub Total</span>
                                <span>₹{subTotal.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between text-gray-600">
                                <span>Discount</span>
                                <span className="text-green-600">-₹{discTotal.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between text-gray-600">
                                <span>Tax (GST)</span>
                                <span>+₹{taxTotal.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between font-bold text-gray-900 pt-2 border-t border-dashed border-gray-200">
                                <span>Net Total</span>
                                <span>₹{netTotal.toFixed(2)}</span>
                            </div>
                        </div>
                        <div className="p-4 bg-gray-50">
                            <label className="block text-xs font-bold text-gray-700 mb-2 uppercase">Payment Method</label>
                            <div className="grid grid-cols-2 gap-2">
                                <button className="p-2 text-xs font-bold border border-gray-900 bg-gray-900 text-white rounded flex items-center justify-center gap-1">
                                    <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path d="M4 4a2 2 0 00-2 2v1h16V6a2 2 0 00-2-2H4z" /><path fillRule="evenodd" d="M18 9H2v5a2 2 0 002 2h12a2 2 0 002-2V9zM4 13a1 1 0 011-1h1a1 1 0 110 2H5a1 1 0 01-1-1zm5-1a1 1 0 100 2h1a1 1 0 100-2H9z" clipRule="evenodd" /></svg>
                                    Cash
                                </button>
                                <button className="p-2 text-xs font-bold border border-gray-300 bg-white text-gray-700 rounded hover:bg-white hover:border-gray-900 flex items-center justify-center gap-1 transition-colors">
                                     UPI / QR
                                </button>
                                <button className="p-2 text-xs font-bold border border-gray-300 bg-white text-gray-700 rounded hover:border-gray-900 transition-colors col-span-2">
                                    Post to IPD Bill
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Quick Short Notes */}
                    <div className="bg-white border border-gray-200 rounded-lg p-3 flex-1">
                        <label className="block text-xs font-bold text-gray-600 uppercase mb-1">Internal Remarks</label>
                        <textarea className="w-full h-20 p-2 border border-gray-200 rounded text-xs outline-none focus:border-gray-400 bg-gray-50 resize-none" placeholder="Add billing notes here..."></textarea>
                    </div>

                </div>
            </div>

            {/* BOTTOM ACTION BAR (Sticky feel at base of main content context) */}
            <div className="bg-white border border-gray-200 p-3 rounded-lg shadow-sm flex items-center justify-between">
                <div className="flex gap-3">
                    <button className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded hover:bg-gray-50">
                        Hold Bill (F4)
                    </button>
                    <button className="px-4 py-2 border border-red-200 bg-red-50 text-red-600 text-sm font-bold rounded hover:bg-red-100 transition-colors">
                        Cancel
                    </button>
                </div>
                
                <div className="flex gap-3">
                     <button className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded hover:bg-gray-50 inline-flex items-center gap-2">
                         <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" /></svg>
                         Print Invoice
                     </button>
                     <button className="px-6 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-bold rounded shadow-sm shadow-green-200 flex items-center gap-2 transition-colors">
                         <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" /></svg>
                         Complete & Save Sale (F12)
                     </button>
                </div>
            </div>
        </div>
    );
};

export default BillingCounterView;
