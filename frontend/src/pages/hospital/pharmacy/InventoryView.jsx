import React, { useState } from 'react';

const InventoryView = () => {
    const [selectedItem, setSelectedItem] = useState(null);

    const mockInventory = [
        { id: 1, name: 'Augmentin 625 Duo', category: 'Antibiotic', batch: 'ABX-100', shelf: 'A-04', qty: 450, reserved: 20, exp: '12/2026', cost: 15.5, price: 20.5, status: 'In Stock' },
        { id: 2, name: 'Crocin 650mg', category: 'Analgesic', batch: 'CR-992', shelf: 'B-01', qty: 1200, reserved: 50, exp: '04/2027', cost: 0.8, price: 1.2, status: 'In Stock' },
        { id: 3, name: 'Telma 40 H', category: 'Cardiac', batch: 'TEL-55', shelf: 'A-10', qty: 15, reserved: 0, exp: '08/2025', cost: 8.5, price: 12.0, status: 'Low Stock' },
        { id: 4, name: 'Pantocid 40', category: 'Gastro', batch: 'PAN-002', shelf: 'C-02', qty: 85, reserved: 5, exp: '05/2024', cost: 6.2, price: 9.5, status: 'Near Expiry' },
        { id: 5, name: 'Coughril Syrup', category: 'Cough Syrup', batch: 'CS-202', shelf: 'D-09', qty: 0, reserved: 0, exp: '02/2026', cost: 45.0, price: 70.0, status: 'Out of Stock' },
    ];

    const getStatusBadge = (status) => {
        switch (status) {
            case 'In Stock': return 'bg-green-50 text-green-700 border-green-100';
            case 'Low Stock': return 'bg-amber-50 text-amber-700 border-amber-100';
            case 'Near Expiry': return 'bg-orange-50 text-orange-700 border-orange-100';
            case 'Out of Stock': return 'bg-red-50 text-red-700 border-red-100';
            default: return 'bg-gray-50 text-gray-700 border-gray-100';
        }
    };

    return (
        <div className="h-full flex flex-col gap-4 -mt-2">
            {/* Toolbar Section */}
            <div className="bg-white border border-gray-200 rounded-lg p-4 flex flex-wrap items-center justify-between gap-4">
                <div className="flex flex-wrap gap-3 items-center">
                     <div className="relative w-72">
                         <input 
                            type="text" 
                            placeholder="Search item / barcode / batch..." 
                            className="pl-10 pr-4 py-2 border border-gray-300 rounded w-full text-sm outline-none focus:border-gray-900"
                         />
                         <svg className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 transform -translate-y-1/2" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
                    </div>
                    <select className="px-3 py-2 border border-gray-300 rounded text-sm text-gray-600 bg-white">
                        <option>All Categories</option>
                        <option>Antibiotics</option>
                        <option>Cardiac</option>
                    </select>
                    <select className="px-3 py-2 border border-gray-300 rounded text-sm text-gray-600 bg-white">
                        <option>All Status</option>
                        <option>Low Stock</option>
                        <option>Out of Stock</option>
                    </select>
                </div>
                <div className="flex gap-2">
                     <button className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-bold rounded bg-white hover:bg-gray-50">Export</button>
                     <button className="px-4 py-2 bg-gray-900 text-white text-sm font-bold rounded hover:bg-gray-800">+ Add Medicine</button>
                </div>
            </div>

            {/* Main Body with Right Drawer Layout */}
            <div className="flex flex-1 gap-4 overflow-hidden" style={{ height: 'calc(100vh - 260px)' }}>
                
                {/* Table Container */}
                <div className={`bg-white border border-gray-200 rounded-lg flex flex-col overflow-hidden transition-all duration-300 ${selectedItem ? 'flex-[2]' : 'flex-1'}`}>
                    <div className="overflow-auto flex-1">
                        <table className="min-w-full text-sm text-left border-collapse">
                             <thead className="bg-gray-50 text-xs text-gray-500 uppercase sticky top-0 border-b border-gray-200 z-10">
                                 <tr>
                                     <th className="px-4 py-3 font-medium">Medicine / Batch</th>
                                     <th className="px-4 py-3 font-medium">Category</th>
                                     <th className="px-4 py-3 font-medium text-right">Avail. Qty</th>
                                     <th className="px-4 py-3 font-medium">Expiry</th>
                                     <th className="px-4 py-3 font-medium">Location</th>
                                     <th className="px-4 py-3 font-medium">Status</th>
                                 </tr>
                             </thead>
                             <tbody className="divide-y divide-gray-100">
                                {mockInventory.map((item) => (
                                    <tr 
                                        key={item.id} 
                                        onClick={() => setSelectedItem(item)}
                                        className={`hover:bg-gray-50 cursor-pointer transition-colors ${selectedItem?.id === item.id ? 'bg-blue-50/50 border-l-2 border-l-blue-600' : ''}`}
                                    >
                                        <td className="px-4 py-3">
                                            <div className="font-semibold text-gray-900">{item.name}</div>
                                            <div className="text-[11px] text-gray-500 mt-0.5 font-mono">{item.batch}</div>
                                        </td>
                                        <td className="px-4 py-3 text-gray-600">{item.category}</td>
                                        <td className="px-4 py-3 text-right">
                                            <span className="font-bold text-gray-900">{item.qty}</span>
                                            {item.reserved > 0 && <span className="text-[10px] text-amber-600 block font-medium">Res: {item.reserved}</span>}
                                        </td>
                                        <td className="px-4 py-3 text-gray-600">{item.exp}</td>
                                        <td className="px-4 py-3 font-medium text-gray-500">{item.shelf}</td>
                                        <td className="px-4 py-3">
                                            <span className={`px-2 py-0.5 border rounded text-[10px] font-bold whitespace-nowrap ${getStatusBadge(item.status)}`}>
                                                {item.status}
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                             </tbody>
                        </table>
                    </div>
                    {/* Simple Footer Paginate */}
                    <div className="px-4 py-3 border-t border-gray-100 bg-gray-50 flex justify-between items-center text-xs text-gray-500">
                         <span>Showing 1 to 5 of 482 items</span>
                         <div className="flex gap-1">
                             <button className="px-2 py-1 border border-gray-200 rounded bg-white">Prev</button>
                             <button className="px-2 py-1 border border-gray-200 rounded bg-white">Next</button>
                         </div>
                    </div>
                </div>

                {/* Right Detail Drawer (Conditional or Persistent placeholder) */}
                {selectedItem ? (
                    <div className="w-80 bg-white border border-gray-200 rounded-lg flex flex-col overflow-hidden animate-in slide-in-from-right duration-200">
                         <div className="p-4 border-b border-gray-100 bg-gray-50 flex justify-between items-start">
                             <div>
                                 <h3 className="font-bold text-gray-900">{selectedItem.name}</h3>
                                 <p className="text-xs text-gray-500 mt-0.5">Batch: {selectedItem.batch}</p>
                             </div>
                             <button onClick={() => setSelectedItem(null)} className="text-gray-400 hover:text-gray-700">
                                 <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                             </button>
                         </div>
                         
                         <div className="flex-1 overflow-y-auto p-4 space-y-5">
                             {/* Quick Details Grid */}
                             <div>
                                 <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">Financials</h4>
                                 <div className="grid grid-cols-2 gap-3">
                                     <div className="bg-gray-50 p-2 rounded border border-gray-100">
                                         <span className="text-[10px] text-gray-500">Purchase Cost</span>
                                         <p className="font-bold text-sm text-gray-900">₹{selectedItem.cost}</p>
                                     </div>
                                     <div className="bg-gray-50 p-2 rounded border border-gray-100">
                                         <span className="text-[10px] text-gray-500">Sales MRP</span>
                                         <p className="font-bold text-sm text-gray-900">₹{selectedItem.price}</p>
                                     </div>
                                 </div>
                             </div>

                             {/* Stock Logs */}
                             <div>
                                 <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">Recent Activity</h4>
                                 <div className="space-y-3">
                                     <div className="flex gap-2 text-xs border-l-2 border-green-500 pl-2 py-0.5">
                                         <div>
                                             <p className="font-medium text-gray-900">Dispensed 10 Units</p>
                                             <p className="text-gray-500 text-[10px]">Today, 10:42 AM • Billing</p>
                                         </div>
                                     </div>
                                     <div className="flex gap-2 text-xs border-l-2 border-blue-500 pl-2 py-0.5">
                                         <div>
                                             <p className="font-medium text-gray-900">Stock Received (+500)</p>
                                             <p className="text-gray-500 text-[10px]">12 May • PO#9902</p>
                                         </div>
                                     </div>
                                 </div>
                             </div>

                             {/* Action Buttons for this item */}
                             <div className="pt-4 space-y-2">
                                 <button className="w-full py-2 border border-gray-300 rounded text-xs font-bold text-gray-700 bg-white hover:bg-gray-50">
                                     Print Barcodes
                                 </button>
                                 <button className="w-full py-2 border border-gray-300 rounded text-xs font-bold text-gray-700 bg-white hover:bg-gray-50">
                                     Adjust Stock (Audit)
                                 </button>
                                 <button className="w-full py-2 bg-gray-900 text-white rounded text-xs font-bold hover:bg-gray-800">
                                     Edit Medicine Master
                                 </button>
                             </div>
                         </div>
                    </div>
                ) : (
                    <div className="w-80 bg-gray-50 border border-gray-200 border-dashed rounded-lg flex flex-col items-center justify-center text-center p-6 text-gray-400">
                        <svg className="w-12 h-12 mb-2 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                        <p className="text-sm font-medium">Select an item to view detail metrics, history and stock logs.</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default InventoryView;
