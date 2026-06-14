import React, { useState, useEffect } from 'react';
import { useToast } from '../../context/ToastContext';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import authService from '../../services/authService';
import MedicineMasterView from './pharmacy/MedicineMasterView';
import ProfileModal from '../../components/ProfileModal';

// Module Views
import DashboardView from './pharmacy/DashboardView';
import BillingCounterView from './pharmacy/BillingCounterView';
import InventoryView from './pharmacy/InventoryView';
import PurchaseView from './pharmacy/PurchaseView';
import ReportsView from './pharmacy/ReportsView';
import PrescriptionsView from './pharmacy/PrescriptionsView';
import SuppliersView from './pharmacy/SuppliersView';
import ExpiryView from './pharmacy/ExpiryView';
import ReturnsView from './pharmacy/ReturnsView';

const PharmacyDashboard = () => {
    const [user] = useState(authService.getCurrentUser());
    const navigate = useNavigate();
    const { success, error: toastError } = useToast();
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);

    // Navigation State for sub-modules
    const [activeTab, setActiveTab] = useState('dashboard');
    const [navData, setNavData] = useState(null);

    const handleLogout = () => {
        authService.logout();
        navigate('/login');
    };

    // Handle sidebar state toggle
    const handleTabChange = (tabId) => {
        setActiveTab(tabId);
        setNavData(null); // Clear data when using sidebar
    };

    const handleNavigate = (tabId, data = null) => {
        setActiveTab(tabId);
        setNavData(data);
    };

    const isStandalonePharmacy = user?.modules?.includes('PHARMACY') && !user?.modules?.includes('OPD');

    // Flat Sidebar definition matching Receptionist/Doctor layouts to support clean minimalist icons
    const sidebarTabs = [
        { id: 'dashboard', label: 'Dashboard' },
        { id: 'billing', label: 'Billing Counter' },
        ...(!isStandalonePharmacy ? [{ id: 'prescriptions', label: 'Prescriptions' }] : []),
        { id: 'inventory', label: 'Inventory' },
        { id: 'medicine_master', label: 'Medicine Master' },
        { id: 'purchase', label: 'Purchase Management' },
        { id: 'suppliers', label: 'Suppliers' },
        { id: 'returns', label: 'Returns & Refunds' },
        { id: 'expiry', label: 'Expiry Management' },
        { id: 'reports', label: 'Reports & Analytics' }
    ];

    // Router-like rendering component based on selected state
    const renderContent = () => {
        switch (activeTab) {
            case 'dashboard':
                return <DashboardView onNavigate={handleNavigate} />;

            case 'billing':
                return <BillingCounterView initialData={navData} />;

            case 'prescriptions':
                return <PrescriptionsView onNavigate={handleNavigate} />;

            case 'inventory':
                return <InventoryView onNavigate={handleNavigate} />;

            case 'purchase':
                return <PurchaseView />;

            case 'suppliers':
                return <SuppliersView />;

            case 'medicine_master':
                return <MedicineMasterView />;

            case 'reports':
                return <ReportsView />;
                
            case 'expiry':
                return <ExpiryView />;
                
            case 'returns':
                return <ReturnsView />;

            default:
                return (
                    <div className="flex flex-col items-center justify-center h-64 border-2 border-dashed border-gray-200 rounded-lg bg-gray-50 text-gray-400">
                        <svg
                            className="w-16 h-16 mb-2 opacity-40"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                        >
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={1}
                                d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
                            />
                        </svg>

                        <h3 className="font-bold text-gray-600 mb-1">
                            Layout Component Pending
                        </h3>

                        <p className="text-sm">
                            Feature implementation roadmap placeholder for:{' '}
                            {activeTab.replace('_', ' ').toUpperCase()}
                        </p>
                    </div>
                );
        }
    };

    return (
        <div className="flex h-screen bg-white overflow-hidden">
            {/* Modified Sidebar supporting groups */}
            <Sidebar
                title="HMS Portal"
                tabs={sidebarTabs}
                activeTab={activeTab}
                onTabChange={handleTabChange}
                footerTitle="Hospital"
                footerData={user?.hospitalName || 'HMS Medical Center'}
                variant="plain"
                isCollapsed={sidebarCollapsed}
            />

            <div className="flex-1 flex flex-col h-full relative overflow-hidden">
                <Navbar
                    title={`Pharmacy & Inventory Operations`}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => setProfileOpen(true)}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                {/* Main Dashboard Content Area */}
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-[#fafafa] p-4 md:p-6">
                    {/* Page Title Bar */}
                    <div className="flex items-center justify-between mb-6">
                        <div className="flex items-center gap-3">
                            <div className="w-1 h-6 bg-gray-900 rounded-full"></div>
                            <h2 className="text-lg font-bold text-gray-800 capitalize flex items-center gap-2">
                                {activeTab.replace('_', ' ')}
                            </h2>
                        </div>
                        <div className="flex items-center gap-2 text-xs font-medium text-gray-500 bg-white px-3 py-1.5 rounded border border-gray-200">
                            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
                            Live Status
                        </div>
                    </div>

                    {/* Dynamic Content Injector */}
                    {renderContent()}
                </main>
            </div>

            {/* Profile Settings Modal */}
            <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />
        </div>
    );
};

export default PharmacyDashboard;
