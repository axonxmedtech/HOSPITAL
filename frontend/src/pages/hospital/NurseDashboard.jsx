import React, { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import NotificationBell from '../../components/NotificationBell';
import ProfileModal from '../../components/ProfileModal';
import authService from '../../services/authService';
import nurseService from '../../services/nurseService';
import { useToast } from '../../context/ToastContext';
import useWebSocket from '../../hooks/useWebSocket';

import NurseOverviewView from './nurse/NurseOverviewView';
import MyPatientsView from './nurse/MyPatientsView';
import NursePatientDetail from './nurse/NursePatientDetail';
import MyTasksView from './nurse/MyTasksView';
import NurseFormsView from './nurse/NurseFormsView';

/**
 * NurseDashboard - Phase 1 Nurse portal shell.
 * Sidebar + Navbar layout (mirrors PharmacyDashboard) with view switching.
 * Gated by a "Start Shift" screen: a nurse must start their shift (become
 * available) before reaching the dashboard. Logout offers Only Logout (stay on
 * shift) or End Shift & Logout (become unavailable).
 */
const NurseDashboard = () => {
    const [user] = useState(authService.getCurrentUser());
    const navigate = useNavigate();
    const { error: toastError } = useToast();
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);

    const [activeTab, setActiveTab] = useState('dashboard');
    const [selectedAdmissionId, setSelectedAdmissionId] = useState(null);
    const [refreshKey, setRefreshKey] = useState(0);

    // Shift gate
    const [shiftLoading, setShiftLoading] = useState(true);
    const [onShift, setOnShift] = useState(false);
    const [shiftBusy, setShiftBusy] = useState(false);
    // Logout options modal
    const [logoutOpen, setLogoutOpen] = useState(false);

    const handleRefresh = useCallback(() => setRefreshKey((k) => k + 1), []);
    useWebSocket(user, null, handleRefresh);

    useEffect(() => {
        let active = true;
        nurseService.getShiftStatus()
            .then((d) => { if (active) setOnShift(!!d?.onShift); })
            .catch(() => { if (active) setOnShift(false); })
            .finally(() => { if (active) setShiftLoading(false); });
        return () => { active = false; };
    }, []);

    const handleStartShift = async () => {
        setShiftBusy(true);
        try {
            await nurseService.startShift();
            setOnShift(true);
        } catch (err) {
            toastError('Failed to start shift');
        } finally {
            setShiftBusy(false);
        }
    };

    const doLogout = () => {
        const loginUrl = authService.getLoginUrl();
        authService.logout();
        navigate(loginUrl);
    };

    const handleOnlyLogout = () => {
        setLogoutOpen(false);
        doLogout();
    };

    const handleEndShiftAndLogout = async () => {
        setShiftBusy(true);
        try {
            await nurseService.endShift();
        } catch (err) {
            // Even if ending the shift fails, proceed to log out; surface a hint.
            toastError('Could not end shift cleanly, logging out anyway');
        } finally {
            setShiftBusy(false);
            setLogoutOpen(false);
            doLogout();
        }
    };

    const handleTabChange = (tabId) => {
        setActiveTab(tabId);
        setSelectedAdmissionId(null);
    };

    const openPatient = (admissionId) => {
        setSelectedAdmissionId(admissionId);
        setActiveTab('patient-detail');
    };

    const sidebarTabs = [
        { id: 'dashboard', label: 'Dashboard' },
        { id: 'my-patients', label: 'My Patients' },
        { id: 'my-tasks', label: 'My Tasks' },
        { id: 'forms', label: 'Forms' },
    ];

    const renderContent = () => {
        if (activeTab === 'patient-detail' && selectedAdmissionId) {
            return (
                <NursePatientDetail
                    admissionId={selectedAdmissionId}
                    onBack={() => handleTabChange('my-patients')}
                    refreshKey={refreshKey}
                />
            );
        }
        switch (activeTab) {
            case 'my-patients':
                return <MyPatientsView onOpenPatient={openPatient} refreshKey={refreshKey} />;
            case 'my-tasks':
                return <MyTasksView refreshKey={refreshKey} />;
            case 'forms':
                return <NurseFormsView />;
            case 'dashboard':
            default:
                return <NurseOverviewView onOpenPatient={openPatient} refreshKey={refreshKey} />;
        }
    };

    const titleFor = () => {
        if (activeTab === 'patient-detail') return 'Patient Details';
        if (activeTab === 'my-patients') return 'My Patients';
        if (activeTab === 'my-tasks') return 'My Tasks';
        if (activeTab === 'forms') return 'Forms';
        return 'Nurse Dashboard';
    };

    // --- Shift gate: block the dashboard until the nurse starts their shift ---
    if (shiftLoading) {
        return (
            <div className="flex items-center justify-center h-screen bg-[#fafafa]">
                <div className="w-10 h-10 border-4 border-gray-200 border-t-gray-800 rounded-full animate-spin" />
            </div>
        );
    }

    if (!onShift) {
        return (
            <div className="flex flex-col items-center justify-center h-screen bg-[#fafafa] px-6 text-center">
                <div className="w-16 h-16 rounded-2xl bg-gray-900 text-white flex items-center justify-center mb-6">
                    <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                </div>
                <h1 className="text-2xl font-bold text-gray-900 mb-2">Welcome{user?.name ? `, ${user.name}` : ''}</h1>
                <p className="text-gray-500 mb-8 max-w-sm">
                    Start your shift to become available and access your dashboard, patients, and tasks.
                </p>
                <button
                    onClick={handleStartShift}
                    disabled={shiftBusy}
                    className={`px-10 py-5 rounded-2xl text-white text-lg font-bold shadow-lg transition ${shiftBusy ? 'bg-gray-400 cursor-not-allowed' : 'bg-green-600 hover:bg-green-700'}`}
                >
                    {shiftBusy ? 'Starting…' : 'Start Shift'}
                </button>
                <button
                    onClick={doLogout}
                    className="mt-6 text-sm font-semibold text-gray-500 hover:text-gray-800"
                >
                    Log out
                </button>
            </div>
        );
    }

    return (
        <div className="flex h-screen bg-white overflow-hidden">
            <Sidebar
                title="Nurse Portal"
                tabs={sidebarTabs}
                activeTab={activeTab === 'patient-detail' ? 'my-patients' : activeTab}
                onTabChange={handleTabChange}
                footerTitle="Hospital"
                footerData={user?.hospitalName || 'HMS Medical Center'}
                variant="plain"
                isCollapsed={sidebarCollapsed}
            />

            <div className="flex-1 flex flex-col h-full relative overflow-hidden">
                <Navbar
                    title={titleFor()}
                    user={user}
                    onLogout={() => setLogoutOpen(true)}
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
                        <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-green-700 bg-green-50 px-3 py-1 rounded-full">
                            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
                            On Shift
                        </span>
                    </div>
                    {renderContent()}
                </main>
            </div>

            <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />

            {/* Logout options: Cancel / Only Logout / End Shift & Logout */}
            {logoutOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" onClick={() => setLogoutOpen(false)}>
                    <div className="bg-white rounded-2xl w-full max-w-md p-6" onClick={(e) => e.stopPropagation()}>
                        <h2 className="text-xl font-bold text-gray-900 mb-1">Log out</h2>
                        <p className="text-sm text-gray-500 mb-6">
                            End your shift to become unavailable for new patient assignments, or log
                            out and stay on shift.
                        </p>
                        <div className="flex flex-col gap-3">
                            <button
                                onClick={handleEndShiftAndLogout}
                                disabled={shiftBusy}
                                className={`w-full px-4 py-3 rounded-lg text-white font-semibold ${shiftBusy ? 'bg-gray-400 cursor-not-allowed' : 'bg-red-600 hover:bg-red-700'}`}
                            >
                                {shiftBusy ? 'Please wait…' : 'End Shift & Log out'}
                            </button>
                            <button
                                onClick={handleOnlyLogout}
                                disabled={shiftBusy}
                                className="w-full px-4 py-3 rounded-lg font-semibold border border-gray-300 text-gray-800 hover:bg-gray-100"
                            >
                                Only Log out (stay on shift)
                            </button>
                            <button
                                onClick={() => setLogoutOpen(false)}
                                disabled={shiftBusy}
                                className="w-full px-4 py-3 rounded-lg font-semibold text-gray-500 hover:bg-gray-50"
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default NurseDashboard;
