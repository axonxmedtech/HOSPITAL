import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import ProfileModal from '../../components/ProfileModal';
import Sidebar from '../../components/Sidebar';
import { useToast } from '../../context/ToastContext';
import useWebSocket from '../../hooks/useWebSocket';
import authService from '../../services/authService';

// Module Views
import BillingCounterView from './pharmacy/BillingCounterView';
import BillingHistoryView from './pharmacy/BillingHistoryView';
import BranchAuditLogsView from './pharmacy/BranchAuditLogsView';
import BranchSettingsView from './pharmacy/BranchSettingsView';
import DashboardView from './pharmacy/DashboardView';
import ExpiryView from './pharmacy/ExpiryView';
import InventoryView from './pharmacy/InventoryView';
import ManufacturerMasterView from './pharmacy/ManufacturerMasterView';
import PrescriptionsView from './pharmacy/PrescriptionsView';
import PurchaseView from './pharmacy/PurchaseView';
import ReportsView from './pharmacy/ReportsView';
import ReturnsView from './pharmacy/ReturnsView';
import SuppliersView from './pharmacy/SuppliersView';

const PharmacyDashboard = () => {
  const [user, setUser] = useState(authService.getCurrentUser());
  const navigate = useNavigate();
  const { success, error: toastError } = useToast();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  // Navigation State for sub-modules
  const [activeTab, setActiveTab] = useState('dashboard');
  const [navData, setNavData] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const handleRefresh = useCallback(() => setRefreshKey((k) => k + 1), []);
  useWebSocket(user, setUser, handleRefresh);

  const handleLogout = () => {
    const loginUrl = authService.getLoginUrl();
    authService.logout();
    navigate(loginUrl);
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

  const isStandalonePharmacy =
    user?.modules?.includes('PHARMACY') && !user?.modules?.includes('OPD');
  const selectedBranchId = sessionStorage.getItem('selectedBranchId');
  const selectedBranchName = sessionStorage.getItem('selectedBranchName');

  // Flat Sidebar definition matching Receptionist/Doctor layouts to support clean minimalist icons
  const sidebarTabs = [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'billing', label: 'Billing Counter' },
    { id: 'billing-history', label: 'Billing' },
    ...(!isStandalonePharmacy ? [{ id: 'prescriptions', label: 'Prescriptions' }] : []),
    { id: 'inventory', label: 'Inventory' },
    { id: 'purchase', label: 'Purchase Management' },
    { id: 'suppliers', label: 'Suppliers' },
    { id: 'manufacturers', label: 'Manufacturers' },
    { id: 'returns', label: 'Returns & Refunds' },
    { id: 'expiry', label: 'Expiry Management' },
    { id: 'reports', label: 'Reports & Analytics' },
    ...(user?.role === 'HOSPITAL_ADMIN'
      ? [
          { id: 'audit-logs', label: 'Audit Logs' },
          { id: 'settings', label: 'Settings' },
        ]
      : []),
  ];

  // Router-like rendering component based on selected state
  const renderContent = () => {
    switch (activeTab) {
      case 'dashboard':
        return <DashboardView onNavigate={handleNavigate} refreshKey={refreshKey} />;

      case 'billing':
        return <BillingCounterView initialData={navData} refreshKey={refreshKey} />;

      case 'billing-history':
        return <BillingHistoryView refreshKey={refreshKey} />;

      case 'prescriptions':
        return <PrescriptionsView onNavigate={handleNavigate} refreshKey={refreshKey} />;

      case 'inventory':
        return <InventoryView onNavigate={handleNavigate} refreshKey={refreshKey} />;

      case 'purchase':
        return <PurchaseView refreshKey={refreshKey} />;

      case 'suppliers':
        return <SuppliersView refreshKey={refreshKey} />;
      case 'manufacturers':
        return <ManufacturerMasterView refreshKey={refreshKey} />;

      case 'reports':
        return <ReportsView refreshKey={refreshKey} />;

      case 'expiry':
        return <ExpiryView refreshKey={refreshKey} />;

      case 'returns':
        return <ReturnsView refreshKey={refreshKey} />;

      case 'audit-logs':
        return <BranchAuditLogsView refreshKey={refreshKey} />;

      case 'settings':
        return <BranchSettingsView refreshKey={refreshKey} />;

      default:
        // An unknown tab id (a stale link to a removed screen) lands on the dashboard. This
        // used to render a "Layout Component Pending … roadmap placeholder" card to customers.
        return <DashboardView onNavigate={handleNavigate} refreshKey={refreshKey} />;
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
        footerTitle={
          selectedBranchId
            ? 'Branch Outlet'
            : user?.hospitalType === 'PHARMACY'
              ? 'Pharmacy'
              : user?.hospitalType === 'CLINIC'
                ? 'Clinic'
                : 'Hospital'
        }
        footerData={
          selectedBranchId ? selectedBranchName : user?.hospitalName || 'HMS Medical Center'
        }
        variant="plain"
        isCollapsed={sidebarCollapsed}
      />

      <div className="flex-1 flex flex-col h-full relative overflow-hidden">
        <Navbar
          title={
            selectedBranchId ? `Branch: ${selectedBranchName}` : `Pharmacy & Inventory Operations`
          }
          user={user}
          onLogout={handleLogout}
          onProfile={() => setProfileOpen(true)}
          onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
        />

        {/* Main Dashboard Content Area */}
        <main className="flex-1 overflow-x-hidden overflow-y-auto bg-[#fafafa] p-4 md:p-6">
          {/* Impersonation Banner */}
          {selectedBranchId && (
            <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 mb-6 flex items-center justify-between shadow-sm">
              <div className="flex items-center gap-2.5">
                <span className="flex h-2.5 w-2.5 relative">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-amber-500"></span>
                </span>
                <span className="text-xs font-semibold text-amber-800">
                  Viewing branch:{' '}
                  <strong className="font-bold text-amber-900">{selectedBranchName}</strong> (Admin
                  Impersonation Mode)
                </span>
              </div>
              <button
                onClick={() => {
                  sessionStorage.removeItem('selectedBranchId');
                  sessionStorage.removeItem('selectedBranchName');
                  navigate('/pharmacy/admin?tab=pharmacies');
                }}
                className="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-black text-[10px] uppercase tracking-wider rounded-lg transition-all shadow cursor-pointer"
              >
                Exit Branch
              </button>
            </div>
          )}

          {/* Page Title Bar */}
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <div className="w-1 h-6 bg-gray-900 rounded-full"></div>
              <h2 className="text-lg font-bold text-gray-800 capitalize flex items-center gap-2">
                {activeTab.replace('-', ' ')}
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
