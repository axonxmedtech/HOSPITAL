import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import NotificationBell from '../../components/NotificationBell';
import ProfileModal from '../../components/ProfileModal';
import authService from '../../services/authService';
import useWebSocket from '../../hooks/useWebSocket';

/**
 * NurseInchargeDashboard - Phase A1 shell for the Nurse Incharge role.
 * Mirrors NurseDashboard's Sidebar + Navbar layout. Real content ("My Nurses"
 * roster management, "My Ward Patients" bedside views) lands in a later phase
 * — these tabs are placeholders for now.
 */
const NurseInchargeDashboard = () => {
    const [user] = useState(authService.getCurrentUser());
    const navigate = useNavigate();
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);
    const [activeTab, setActiveTab] = useState('my-nurses');
    const [refreshKey, setRefreshKey] = useState(0);

    const handleRefresh = useCallback(() => setRefreshKey((k) => k + 1), []);
    useWebSocket(user, null, handleRefresh);

    const doLogout = () => {
        const loginUrl = authService.getLoginUrl();
        authService.logout();
        navigate(loginUrl);
    };

    const sidebarTabs = [
        { id: 'my-nurses', label: 'My Nurses' },
        { id: 'my-ward-patients', label: 'My Ward Patients' },
    ];

    const titleFor = () => {
        if (activeTab === 'my-ward-patients') return 'My Ward Patients';
        return 'My Nurses';
    };

    const renderContent = () => {
        switch (activeTab) {
            case 'my-ward-patients':
                return (
                    <div className="bg-white rounded-2xl border border-gray-200/80 p-8 text-center text-gray-500">
                        Ward patient views are coming soon.
                    </div>
                );
            case 'my-nurses':
            default:
                return (
                    <div className="bg-white rounded-2xl border border-gray-200/80 p-8 text-center text-gray-500">
                        Nurse roster management is coming soon.
                    </div>
                );
        }
    };

    return (
        <div className="flex h-screen bg-white overflow-hidden">
            <Sidebar
                title="Nurse Incharge"
                tabs={sidebarTabs}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                footerTitle="Hospital"
                footerData={user?.hospitalName || 'HMS Medical Center'}
                variant="plain"
                isCollapsed={sidebarCollapsed}
            />

            <div className="flex-1 flex flex-col h-full relative overflow-hidden">
                <Navbar
                    title={titleFor()}
                    user={user}
                    onLogout={doLogout}
                    onProfile={() => setProfileOpen(true)}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                    actions={<NotificationBell refreshKey={refreshKey} />}
                />

                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-[#fafafa] p-4 md:p-6">
                    <div className="flex items-center justify-between mb-6">
                        <div className="flex items-center gap-3">
                            <div className="w-1 h-6 bg-gray-900 rounded-full"></div>
                            <h2 className="text-lg font-bold text-gray-800">{titleFor()}</h2>
                        </div>
                    </div>
                    {renderContent()}
                </main>
            </div>

            <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />
        </div>
    );
};

export default NurseInchargeDashboard;
