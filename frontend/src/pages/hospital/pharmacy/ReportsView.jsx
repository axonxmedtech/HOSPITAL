import React from 'react';

const ReportsView = () => {
    return (
        <div className="space-y-6">
             <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white border border-gray-200 p-4 rounded-lg">
                <div>
                    <h1 className="text-xl font-bold text-gray-900">Reports & Analytics</h1>
                    <p className="text-sm text-gray-500 mt-0.5">Analyze financial performance, tax breakdowns, and stock turnover.</p>
                </div>
                <div className="flex gap-2">
                    <select className="px-3 py-2 border border-gray-300 rounded text-sm bg-white font-medium text-gray-700">
                        <option>This Month</option>
                        <option>Last Quarter</option>
                        <option>This FY</option>
                    </select>
                    <button className="px-4 py-2 bg-gray-900 text-white text-sm font-bold rounded hover:bg-gray-800 flex items-center gap-2">
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" /></svg>
                        Export Reports
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                 
                 {/* Visual Placeholder 1: Sales Trend */}
                 <div className="bg-white border border-gray-200 rounded-lg p-5 flex flex-col h-80">
                     <h3 className="font-bold text-gray-800 mb-4">Gross Monthly Sales Trend</h3>
                     <div className="flex-1 bg-gray-50 border border-dashed border-gray-200 rounded flex items-center justify-center flex-col text-gray-400">
                         <svg className="w-12 h-12 mb-2 opacity-30" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 12l3-3 3 3 4-4M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" /></svg>
                         <p className="text-sm font-medium">Charts library placeholder</p>
                     </div>
                 </div>

                 {/* Visual Placeholder 2: Stock Value Distribution */}
                 <div className="bg-white border border-gray-200 rounded-lg p-5 flex flex-col h-80">
                     <h3 className="font-bold text-gray-800 mb-4">Category Stock Valuation</h3>
                     <div className="flex-1 bg-gray-50 border border-dashed border-gray-200 rounded flex items-center justify-center flex-col text-gray-400">
                          <svg className="w-12 h-12 mb-2 opacity-30" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M11 3.055A9.001 9.001 0 1020.945 13H11V3.055z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20.488 9H15V3.512A9.025 9.025 0 0120.488 9z" /></svg>
                         <p className="text-sm font-medium">Valuation pie chart placeholder</p>
                     </div>
                 </div>
            </div>

            {/* Report Grids */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                
                {/* Box 1 */}
                <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                    <div className="px-4 py-3 border-b border-gray-100 font-bold text-gray-800 bg-gray-50">Expiry Loss Projection</div>
                    <div className="p-4 space-y-3">
                        <div className="flex justify-between text-sm border-b border-gray-50 pb-2">
                            <span className="text-gray-600">Next 30 Days</span>
                            <span className="font-bold text-red-600">₹4,200</span>
                        </div>
                        <div className="flex justify-between text-sm border-b border-gray-50 pb-2">
                            <span className="text-gray-600">Next 60 Days</span>
                            <span className="font-bold text-amber-600">₹18,500</span>
                        </div>
                        <div className="flex justify-between text-sm">
                            <span className="text-gray-600">Total Risk</span>
                            <span className="font-black text-gray-900">₹22,700</span>
                        </div>
                    </div>
                </div>

                 {/* Box 2 */}
                 <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                    <div className="px-4 py-3 border-b border-gray-100 font-bold text-gray-800 bg-gray-50">Top Fast Moving Medicines</div>
                    <div className="p-4 space-y-3">
                        <div className="flex justify-between items-center text-xs">
                            <span className="font-medium text-gray-900">Paracetamol 650mg</span>
                            <span className="bg-blue-50 text-blue-700 font-bold px-2 py-0.5 rounded border border-blue-100">Qty: 1,400</span>
                        </div>
                        <div className="flex justify-between items-center text-xs">
                            <span className="font-medium text-gray-900">Pantocid DSR</span>
                            <span className="bg-blue-50 text-blue-700 font-bold px-2 py-0.5 rounded border border-blue-100">Qty: 850</span>
                        </div>
                         <div className="flex justify-between items-center text-xs">
                            <span className="font-medium text-gray-900">Levocet 5mg</span>
                            <span className="bg-blue-50 text-blue-700 font-bold px-2 py-0.5 rounded border border-blue-100">Qty: 780</span>
                        </div>
                    </div>
                </div>

                {/* Box 3 */}
                <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                    <div className="px-4 py-3 border-b border-gray-100 font-bold text-gray-800 bg-gray-50">Tax Summary (GST)</div>
                     <div className="p-4 space-y-3">
                        <div className="flex justify-between text-sm border-b border-gray-50 pb-2">
                            <span className="text-gray-600">Input GST (Pur)</span>
                            <span className="font-bold text-gray-900">₹84,200</span>
                        </div>
                        <div className="flex justify-between text-sm border-b border-gray-50 pb-2">
                            <span className="text-gray-600">Output GST (Sales)</span>
                            <span className="font-bold text-gray-900">₹1,02,500</span>
                        </div>
                        <div className="flex justify-between text-sm">
                            <span className="text-gray-600">Payable (Net)</span>
                            <span className="font-bold text-green-700">₹18,300</span>
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
};

export default ReportsView;
