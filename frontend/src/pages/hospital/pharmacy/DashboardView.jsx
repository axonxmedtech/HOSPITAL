import React, { useState, useEffect } from 'react';
import hospitalService from '../../../services/hospitalService';
import inventoryApi from '../../../services/pharmacy/inventoryApi';
import salesApi from '../../../services/pharmacy/salesApi';

const DashboardView = ({ onNavigate }) => {
    const [loading, setLoading] = useState(true);
    const [activeCount, setActiveCount] = useState(0);
    const [lowStockCount, setLowStockCount] = useState(0);
    const [lowStockItems, setLowStockItems] = useState([]);
    const [recentPrescriptions, setRecentPrescriptions] = useState([]);
    const [salesStats, setSalesStats] = useState({ todaySalesTotal: 0, todaySalesCount: 0 });

    useEffect(() => {
        const fetchLiveStats = async () => {
            setLoading(true);
            try {
                // 1. Fetch Pending Prescriptions for queue & stats
                const rxData = await hospitalService.getPendingPrescriptions();
                const validRx = rxData || [];
                
                // Group to match Consultations visual representation
                const grouped = new Map();
                validRx.forEach(item => {
                    const key = item.medicalRecordId || item.id;
                    if (!grouped.has(key)) {
                        grouped.set(key, {
                            id: item.medicalRecordId ? `RX-${item.medicalRecordId}` : `RX-RAW-${item.id}`,
                            patient: item.patientName || 'Unknown Patient',
                            doctor: item.doctorName || 'Consulting Specialist',
                            age: item.patientAge || 'N/A',
                            type: 'LIVE',
                            status: 'Pending'
                        });
                    }
                });
                
                const finalRx = Array.from(grouped.values());
                setActiveCount(finalRx.length);
                setRecentPrescriptions(finalRx.slice(0, 4)); // Grab top 4 for overview

                // 2. Fetch Low Stock from ERP Batch Layer
                const stockResponse = await inventoryApi.getLowStock(0, 5);
                if (stockResponse && stockResponse.content) {
                    setLowStockCount(stockResponse.totalElements || stockResponse.content.length);
                    setLowStockItems(stockResponse.content.map(b => ({
                        item: b.medicine?.medicineName || 'Unknown Med',
                        available: b.currentQuantity,
                        unit: b.medicine?.dosageForm || 'Units',
                        threshold: b.medicine?.reorderLevel || 0
                    })));
                }

                // 3. Fetch Today Sales
                const salesData = await salesApi.getStats();
                setSalesStats(salesData);

            } catch (err) {
                console.error("Failed loading pharmacy stats", err);
            } finally {
                setLoading(false);
            }
        };

        fetchLiveStats();
    }, []);

    // Dynamic stats mapping
    const stats = [
        { title: 'Today Sales', value: `₹${salesStats.todaySalesTotal.toLocaleString()}`, subtitle: `${salesStats.todaySalesCount} Bills Generated`, icon: (
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
        )},
        { title: 'Active Prescriptions', value: loading ? '...' : String(activeCount), subtitle: 'Live count from OPD', icon: (
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
        )},
        { title: 'Low Stock Medicines', value: loading ? '...' : String(lowStockCount), isAlert: lowStockCount > 0, icon: (
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
        )},
        { title: 'Near Expiry', value: '0', subtitle: 'Standard View', icon: (
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
        )},
        { title: 'Pending Vendor Bills', value: '₹0', subtitle: 'Locked for V1', icon: (
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
            </svg>
        )},
        { title: 'Inventory Volume', value: 'Active', subtitle: 'Flat tracking mode', icon: (
            <svg className="w-5 h-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
        )},
    ];

    return (
        <div className="space-y-6">
            {/* Header Section */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-4 border border-gray-200 rounded-lg">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Pharmacy Operations</h1>
                    <p className="text-sm text-gray-500 mt-1">Real-time inventory & dispense tracking overview.</p>
                </div>
                <div className="flex flex-wrap gap-3">
                    <button onClick={() => onNavigate('prescriptions')} className="px-4 py-2 bg-gray-900 text-white text-sm font-medium rounded hover:bg-gray-800 transition-colors inline-flex items-center gap-2">
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" /></svg>
                        View Live Prescriptions
                    </button>
                    <button onClick={() => onNavigate('inventory')} className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-medium rounded hover:bg-gray-50 transition-colors bg-white inline-flex items-center gap-2">
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" /></svg>
                        Manage Inventory
                    </button>
                </div>
            </div>

            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
                {stats.map((stat, idx) => (
                    <div key={idx} className={`bg-white p-4 border border-gray-200 rounded-lg transition-all hover:shadow-sm ${stat.isAlert ? 'border-l-4 border-l-red-500' : ''}`}>
                        <div className="flex items-center justify-between mb-2">
                            <div className="p-2 bg-gray-50 rounded">
                                {stat.icon}
                            </div>
                        </div>
                        <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">{stat.title}</p>
                        <p className="text-2xl font-bold text-gray-900 mt-1">{stat.value}</p>
                        {(stat.subtitle) && (
                            <p className="text-xs text-gray-400 mt-1 truncate">{stat.subtitle}</p>
                        )}
                    </div>
                ))}
            </div>

            {/* Main Workspace Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                
                {/* Left/Center Content (Span 2) */}
                <div className="lg:col-span-2 space-y-6">
                    
                    {/* Prescription Queue */}
                    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                        <div className="px-4 py-3 border-b border-gray-100 flex justify-between items-center">
                            <h3 className="font-bold text-gray-800">Active Prescription Queue</h3>
                            <span className="text-xs font-bold bg-gray-100 text-gray-600 px-2 py-1 rounded-full">{recentPrescriptions.length} In Queue</span>
                        </div>
                        <div className="divide-y divide-gray-100">
                            {loading ? (
                                <div className="p-6 text-center text-gray-500 text-sm">Synchronizing queue from consultations...</div>
                            ) : recentPrescriptions.length > 0 ? recentPrescriptions.map(rx => (
                                <div key={rx.id} className="p-4 hover:bg-gray-50 flex flex-col sm:flex-row sm:items-center justify-between gap-4 transition">
                                    <div className="flex items-start gap-3">
                                        <div className="mt-1 w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <h4 className="font-semibold text-gray-900">{rx.patient}</h4>
                                                <span className="text-xs text-gray-500">({rx.age} yrs)</span>
                                            </div>
                                            <div className="text-sm text-gray-500 mt-0.5">{rx.doctor} • <span className="font-mono text-xs font-medium bg-gray-100 px-1 rounded">{rx.id}</span></div>
                                        </div>
                                    </div>
                                    <div className="flex items-center justify-end w-full sm:w-auto gap-4">
                                        <button onClick={() => onNavigate('prescriptions')} className="px-3 py-1.5 bg-white border border-gray-300 hover:border-gray-900 text-gray-700 text-xs font-semibold rounded transition-colors shadow-sm">
                                            View Details
                                        </button>
                                    </div>
                                </div>
                            )) : (
                                <div className="p-8 text-center text-gray-400 text-sm flex flex-col items-center gap-1">
                                    <svg className="w-8 h-8 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" /></svg>
                                    No pending consultation prescriptions found.
                                </div>
                            )}
                        </div>
                        <div className="bg-gray-50 px-4 py-2 text-center border-t border-gray-100">
                            <button onClick={() => onNavigate('prescriptions')} className="text-xs font-semibold text-gray-600 hover:text-gray-900">Open Prescriptions Center &rarr;</button>
                        </div>
                    </div>

                </div>

                {/* Right Column Sidebars */}
                <div className="space-y-6">
                    
                    {/* Low Stock Alerts - LIVE */}
                    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                        <div className="px-4 py-3 bg-red-50 border-b border-red-100 flex justify-between items-center">
                            <h3 className="font-bold text-red-800 text-sm inline-flex items-center gap-2">
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg>
                                Real-time Low Stock
                            </h3>
                            <span className="text-xs font-bold bg-red-200 text-red-800 px-1.5 py-0.5 rounded-full">{lowStockCount}</span>
                        </div>
                        <div className="p-3 space-y-3">
                            {loading ? (
                                <div className="p-4 text-center text-xs text-gray-400">Checking inventory levels...</div>
                            ) : lowStockItems.length > 0 ? lowStockItems.map((stock, i) => (
                                <div key={i} className="text-xs border-b border-gray-100 pb-2 last:border-0 last:pb-0">
                                    <div className="flex justify-between font-medium">
                                        <span className="text-gray-900 truncate mr-2 uppercase">{stock.item}</span>
                                        <span className="text-red-600 font-bold whitespace-nowrap">{stock.available} {stock.unit}</span>
                                    </div>
                                    <div className="text-[10px] text-gray-400 mt-0.5 flex justify-between">
                                        <span>Min Threshold: {stock.threshold}</span>
                                        <span className="font-bold text-red-500">Needs Refill</span>
                                    </div>
                                </div>
                            )) : (
                                <div className="p-4 text-center text-xs text-green-600 font-medium flex flex-col items-center gap-1">
                                    <svg className="w-6 h-6 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                                    All inventory stocks are normal.
                                </div>
                            )}
                            <button onClick={() => onNavigate('inventory')} className="w-full text-xs py-2 border border-dashed border-gray-300 text-gray-500 hover:text-gray-900 hover:border-gray-400 rounded mt-1 transition-colors font-semibold bg-gray-50/50">Go To Inventory View</button>
                        </div>
                    </div>

                    {/* Quick System Alert */}
                    <div className="bg-white border border-gray-200 rounded-lg p-4 text-xs text-gray-500">
                        <h4 className="font-bold text-gray-700 uppercase mb-1 tracking-wide text-[10px]">System Status</h4>
                        <p>Phase 1 Pharmacy Core is ACTIVE. All data streams are synced directly with consultation systems under multi-tenant isolation rules.</p>
                    </div>

                </div>
            </div>
        </div>
    );
};

export default DashboardView;
