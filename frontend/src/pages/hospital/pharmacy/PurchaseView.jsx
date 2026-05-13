import React from 'react';

const PurchaseView = () => {
    const invoices = [
        { id: '#PUR-1002', supplier: 'Max Healthcare Suppliers', date: '12 May 2026', amount: '₹1,20,500', status: 'Posted', items: 12 },
        { id: '#PUR-1001', supplier: 'Cipla Distribution Ltd.', date: '10 May 2026', amount: '₹45,000', status: 'Draft', items: 4 },
        { id: '#PUR-999', supplier: 'Apex Medical Agencies', date: '05 May 2026', amount: '₹12,800', status: 'Posted', items: 8 },
    ];

    return (
        <div className="space-y-6">
            {/* Mini Stats Row Specific to Purchase */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-white p-4 border border-gray-200 rounded-lg flex items-center justify-between">
                    <div>
                        <p className="text-xs font-medium text-gray-500 uppercase mb-1">Month Purchases</p>
                        <p className="text-2xl font-bold text-gray-900">₹8.54 L</p>
                    </div>
                    <div className="p-2 bg-blue-50 text-blue-600 rounded-lg"><svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" /></svg></div>
                </div>
                <div className="bg-white p-4 border border-gray-200 rounded-lg flex items-center justify-between">
                    <div>
                        <p className="text-xs font-medium text-gray-500 uppercase mb-1">Outstanding Payables</p>
                        <p className="text-2xl font-bold text-gray-900">₹2.10 L</p>
                    </div>
                    <div className="p-2 bg-amber-50 text-amber-600 rounded-lg"><svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg></div>
                </div>
                 <div className="bg-white p-4 border border-gray-200 rounded-lg flex items-center justify-between">
                    <div>
                        <p className="text-xs font-medium text-gray-500 uppercase mb-1">Suppliers Count</p>
                        <p className="text-2xl font-bold text-gray-900">24</p>
                    </div>
                    <div className="p-2 bg-gray-50 text-gray-600 rounded-lg"><svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" /></svg></div>
                </div>
            </div>

            {/* Quick Action Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                     <h2 className="text-xl font-bold text-gray-900">Purchase Management</h2>
                     <p className="text-sm text-gray-500">Record new inward stock and manage supplier bills.</p>
                </div>
                <button className="px-6 py-2.5 bg-gray-900 text-white rounded text-sm font-bold shadow-sm hover:bg-gray-800 flex items-center gap-2 transition-colors">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
                    Create New Purchase Entry
                </button>
            </div>

            {/* Main Split Section for Feature Preview */}
            <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
                 
                 {/* Invoice List (Col span 8) */}
                 <div className="xl:col-span-8 bg-white border border-gray-200 rounded-lg flex flex-col">
                     <div className="px-4 py-3 border-b border-gray-100 flex justify-between items-center">
                         <h3 className="font-bold text-gray-800">Recent Purchase Invoices</h3>
                         <div className="flex gap-2">
                             <input type="text" placeholder="Search Invoices..." className="px-3 py-1.5 border border-gray-300 rounded text-xs focus:border-gray-900 outline-none" />
                         </div>
                     </div>
                     <div className="overflow-x-auto">
                         <table className="min-w-full text-sm text-left">
                             <thead className="bg-gray-50 text-xs text-gray-500 uppercase border-b">
                                 <tr>
                                     <th className="px-4 py-3 font-medium">Invoice ID</th>
                                     <th className="px-4 py-3 font-medium">Supplier</th>
                                     <th className="px-4 py-3 font-medium">Date</th>
                                     <th className="px-4 py-3 font-medium text-center">Items</th>
                                     <th className="px-4 py-3 font-medium text-right">Net Amt</th>
                                     <th className="px-4 py-3 font-medium text-center">Status</th>
                                 </tr>
                             </thead>
                             <tbody className="divide-y divide-gray-100">
                                 {invoices.map(inv => (
                                     <tr key={inv.id} className="hover:bg-gray-50 cursor-pointer">
                                         <td className="px-4 py-3 font-mono font-medium text-gray-900">{inv.id}</td>
                                         <td className="px-4 py-3 text-gray-700">{inv.supplier}</td>
                                         <td className="px-4 py-3 text-gray-500">{inv.date}</td>
                                         <td className="px-4 py-3 text-center font-medium">{inv.items}</td>
                                         <td className="px-4 py-3 text-right font-bold text-gray-900">{inv.amount}</td>
                                         <td className="px-4 py-3 text-center">
                                             <span className={`inline-flex px-2 py-0.5 rounded text-[10px] font-bold border ${inv.status === 'Posted' ? 'bg-green-50 text-green-700 border-green-100' : 'bg-gray-50 text-gray-600 border-gray-200'}`}>
                                                 {inv.status}
                                             </span>
                                         </td>
                                     </tr>
                                 ))}
                             </tbody>
                         </table>
                     </div>
                     <div className="p-3 bg-gray-50 text-center border-t text-xs text-gray-500">View full history &rarr;</div>
                 </div>

                 {/* Dummy Right Panel for Quick "Add Form Layout Mockup" (Col span 4) */}
                 <div className="xl:col-span-4 space-y-4">
                      <div className="bg-white border border-gray-200 rounded-lg p-5">
                          <h3 className="font-bold text-gray-800 mb-4 pb-2 border-b border-gray-100">New Purchase Skeleton</h3>
                          <div className="space-y-3">
                              <div>
                                  <label className="block text-xs font-bold text-gray-500 mb-1 uppercase">Supplier</label>
                                  <select className="w-full border border-gray-300 rounded p-2 text-sm bg-gray-50"><option>Select Supplier...</option></select>
                              </div>
                              <div className="grid grid-cols-2 gap-3">
                                   <div>
                                        <label className="block text-xs font-bold text-gray-500 mb-1 uppercase">Bill No.</label>
                                        <input type="text" className="w-full border border-gray-300 rounded p-2 text-sm" placeholder="Eg: A102" />
                                   </div>
                                   <div>
                                        <label className="block text-xs font-bold text-gray-500 mb-1 uppercase">Bill Date</label>
                                        <input type="date" className="w-full border border-gray-300 rounded p-2 text-sm" />
                                   </div>
                              </div>
                              <hr className="border-dashed border-gray-200" />
                              <div className="border-2 border-dashed border-gray-200 rounded p-4 text-center bg-gray-50">
                                  <svg className="w-6 h-6 mx-auto text-gray-400 mb-1" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" /></svg>
                                  <span className="text-xs font-medium text-gray-500">Upload Invoice PDF/Scan</span>
                              </div>
                              <button disabled className="w-full py-2 bg-gray-100 text-gray-400 rounded font-bold text-sm border border-gray-200 cursor-not-allowed mt-2">
                                  Proceed to Entry &rarr;
                              </button>
                          </div>
                      </div>
                 </div>
            </div>
        </div>
    );
};

export default PurchaseView;
