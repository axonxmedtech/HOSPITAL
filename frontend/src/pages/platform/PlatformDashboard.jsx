import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import authService from '../../services/authService';
import platformService from '../../services/platformService';
import ConfirmationModal from '../../components/ConfirmationModal';
import { validateForm } from '../../utils/validation';
import ActionMenu from '../../components/ActionMenu';
import DataTable from '../../components/DataTable';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';
import { useToast } from '../../context/ToastContext';
import { createColumnHelper } from '@tanstack/react-table';

/**
 * PlatformDashboard - Super Admin dashboard
 * 
 * This page allows Super Admin to:
 * - View all hospitals
 * - Create new hospitals
 * - Activate/deactivate hospitals
 * 
 * @author HMS Team
 * @version Phase-1
 */
const AVAILABLE_MODULES = ['OPD', 'BILLING', 'PHARMACY', 'PATHOLOGY', 'IPD'];

const PlatformDashboard = () => {
    const navigate = useNavigate();
    const { success } = useToast();
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'dashboard';

    // Helper to switch tabs
    const setActiveTab = (tab) => {
        setSearchParams({ tab });
    };

    const [hospitals, setHospitals] = useState([]);
    const [hospitalPage, setHospitalPage] = useState({
        content: [],
        totalPages: 0,
        totalElements: 0,
        number: 0,
        size: 10
    });

    const [showCreateModal, setShowCreateModal] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Confirmation Modal State
    const [confirmModal, setConfirmModal] = useState({
        isOpen: false,
        title: '',
        message: '',
        onConfirm: null
    });

    const [formData, setFormData] = useState({
        hospitalName: '',
        adminName: '',
        adminEmail: '',
        adminPassword: '',
        modules: ['OPD', 'BILLING'] // Default modules
    });
    const [errors, setErrors] = useState({}); // Field-level errors

    // Audit Logs State
    const [auditLogs, setAuditLogs] = useState([]);

    // Sidebar collapse state
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

    // Overview Stats State
    const [hospitalStats, setHospitalStats] = useState({ total: 0, active: 0, inactive: 0 });

    // UI State - removed local activeTab state, now using URL params
    // const [activeTab, setActiveTab] = useState('dashboard');

    // Edit Hospital State
    const [editHospitalModal, setEditHospitalModal] = useState({
        isOpen: false,
        hospital: null,
        plan: '',
        modules: [],
        name: '',
        adminEmail: '',
        adminName: ''
    });

    // Password Reset Modal State
    const [passwordModal, setPasswordModal] = useState({
        isOpen: false,
        email: '',
        password: ''
    });

    // Load data based on active tab
    useEffect(() => {
        if (activeTab === 'dashboard') {
            loadHospitals();
            loadHospitalStats();
        } else if (activeTab === 'hospitals') {
            loadHospitals();
        } else if (activeTab === 'audit_logs') {
            loadAuditLogs();
        }
    }, [activeTab]);

    const loadHospitals = async (page = 0, size = 10) => {
        try {
            setLoading(true);
            const data = await platformService.getHospitals(page, size);
            // Handle both Page object and legacy list response
            if (data.content) {
                setHospitals(data.content);
                setHospitalPage(data);
            } else {
                setHospitals(data);
                setHospitalPage({ content: data, totalPages: 1, totalElements: data.length, number: 0, size: data.length });
            }
        } catch (err) {
            setError('Failed to load hospitals');
        } finally {
            setLoading(false);
        }
    };



    const loadAuditLogs = async () => {
        try {
            setLoading(true);
            const data = await platformService.getAuditLogs();
            setAuditLogs(data);
        } catch (err) {
            setError('Failed to load audit logs');
        } finally {
            setLoading(false);
        }
    };

    const loadHospitalStats = async () => {
        try {
            const stats = await platformService.getHospitalStats();
            setHospitalStats(stats);
        } catch (err) {
            setError('Failed to load hospital statistics');
        }
    };

    const handleLogout = () => {
        authService.logout();
        navigate('/platform/login');
    };

    // Generic confirmation handler
    const openConfirmation = (title, message, action, showReasonInput = false, inputPlaceholder = "Please provide a reason...") => {
        setConfirmModal({
            isOpen: true,
            title,
            message,
            onConfirm: action,
            showReasonInput,
            inputPlaceholder
        });
    };

    const AVAILABLE_MODULES = ['OPD', 'IPD', 'PHARMACY', 'PATHOLOGY', 'BILLING'];

    const handleCreateHospital = async (e) => {
        e.preventDefault();
        setError('');
        setErrors({});

        const rules = {
            hospitalName: ['required'],
            adminName: ['required', 'name'],
            adminEmail: ['required', 'email'],
            adminPassword: ['required', 'password']
        };

        const validationErrors = validateForm(formData, rules);
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }

        openConfirmation(
            'Create Hospital',
            'Are you sure you want to create this new hospital?',
            async () => {
                try {
                    await platformService.createHospital(formData);
                    setShowCreateModal(false);
                    setFormData({
                        hospitalName: '',
                        adminName: '',
                        adminEmail: '',
                        adminPassword: '',
                        modules: ['OPD', 'BILLING'] // Reset to defaults
                    });
                    loadHospitals(); // Reload hospitals list
                } catch (err) {
                    setError(err.response?.data || 'Failed to create hospital');
                }
            }
        );
    };

    const handleToggleStatus = async (id, currentStatus) => {
        const action = currentStatus ? 'Deactivate' : 'Activate';
        openConfirmation(
            `${action} Hospital`,
            `Are you sure you want to ${action.toLowerCase()} this hospital?`,
            async (reason) => {
                console.log(`${action} hospital ${id}. Reason: ${reason}`);
                try {
                    await platformService.updateHospitalStatus(id, !currentStatus);
                    loadHospitals(); // Reload hospitals list
                } catch (err) {
                    setError('Failed to update hospital status');
                }
            },
            true, // Require reason
            `Why are you ${action.toLowerCase().replace(/e$/, '')}ing this hospital?`
        );
    };

    const handleHospitalUpdate = async () => {
        try {
            const hospitalId = editHospitalModal.hospital.publicId || editHospitalModal.hospital.id;

            // Update Details (Name & Email & Admin Name)
            if (editHospitalModal.name !== editHospitalModal.hospital.name ||
                editHospitalModal.adminEmail !== editHospitalModal.hospital.adminEmail ||
                editHospitalModal.adminName !== editHospitalModal.hospital.adminName) {
                await platformService.updateHospitalDetails(
                    hospitalId,
                    editHospitalModal.name,
                    editHospitalModal.adminEmail,
                    editHospitalModal.adminName
                );
            }

            // Update Plan
            if (editHospitalModal.plan !== editHospitalModal.hospital.plan) {
                await platformService.updateHospitalPlan(hospitalId, editHospitalModal.plan);
            }

            // Update Modules
            // Simple array comparison (assuming order might differ, so sort)
            const oldModules = [...(editHospitalModal.hospital.modules || [])].sort().join(',');
            const newModules = [...editHospitalModal.modules].sort().join(',');

            if (oldModules !== newModules) {
                await platformService.updateHospitalModules(hospitalId, editHospitalModal.modules);
            }

            success('Hospital updated successfully');
            setEditHospitalModal({ isOpen: false, hospital: null, plan: '', modules: [], name: '', adminEmail: '', adminName: '' });
            loadHospitals(hospitalPage.number, hospitalPage.size);
        } catch (err) {
            console.error(err);
            setError(err.response?.data || 'Failed to update hospital details');
        }
    };

    const openEditHospitalModal = async (hospital) => {
        try {
            // Fetch fresh details for Admin Email
            const details = await platformService.getHospitalById(hospital.publicId || hospital.id);
            setEditHospitalModal({
                isOpen: true,
                hospital: details,
                plan: details.plan || 'FREE',
                modules: details.modules || [],
                name: details.name,
                adminEmail: details.adminEmail || '',
                adminName: details.adminName || ''
            });
        } catch (err) {
            setError('Failed to fetch hospital details');
        }
    };



    const handleResetPassword = (id) => {
        openConfirmation(
            'Reset Admin Password',
            'Are you sure you want to reset the admin password for this hospital? The old password will stop working immediately.',
            async () => {
                try {
                    const data = await platformService.resetTenantPassword(id);
                    setPasswordModal({
                        isOpen: true,
                        email: data.email,
                        password: data.password
                    });
                    success('Password reset successfully');
                } catch (err) {
                    setError('Failed to reset password');
                }
            }
        );
    };

    const handleUserResetPassword = (id) => {
        openConfirmation(
            'Reset User Password',
            'Are you sure you want to reset the password for this user?',
            async () => {
                try {
                    const data = await platformService.resetUserPassword(id);
                    setPasswordModal({
                        isOpen: true,
                        email: data.email,
                        password: data.password
                    });
                    success('Password reset successfully');
                } catch (err) {
                    setError('Failed to reset password');
                }
            }
        );
    };





    const user = authService.getCurrentUser();

    const tabs = [
        { id: 'dashboard', label: 'Dashboard' },
        { id: 'hospitals', label: 'Hospitals' },
        { id: 'audit_logs', label: 'Audit Logs' },
    ];

    return (
        <div className="flex h-screen bg-gray-50">
            {/* Sidebar */}
            <Sidebar
                title="HMS Portal"
                tabs={tabs}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                footerTitle="Platform"
                footerData="Super Admin"
                variant="plain"
                isCollapsed={sidebarCollapsed}
            />

            {/* Main Content Wrapper */}
            <div className="flex-1 flex flex-col overflow-hidden">
                {/* Navbar */}
                <Navbar
                    title={tabs.find(t => t.id === activeTab)?.label}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => console.log('Profile clicked')}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                {/* Main Content Area */}
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-gray-50 p-6">
                    {/* Standardized Header */}
                    {activeTab !== 'dashboard' && (
                        <div className="mb-6">
                            <PageHeader
                                title={tabs.find(t => t.id === activeTab)?.label}
                                subtitle={activeTab === 'hospitals' ? 'Manage and monitor all registered hospitals on the platform.' : 'Track system activities and administrative actions across the platform.'}
                                onAdd={activeTab === 'hospitals' ? () => setShowCreateModal(true) : null}
                                addLabel="Create Hospital"
                            />
                        </div>
                    )}

                    {/* Dashboard Tab */}
                    {activeTab === 'dashboard' && (
                        <div className="space-y-6">
                            {/* Welcome Section */}
                            <div className="bg-white border border-gray-200 p-6">
                                <h1 className="text-2xl font-bold text-gray-900 mb-2">Platform Overview</h1>
                                <p className="text-gray-600">Monitor and manage all hospitals from your central dashboard</p>
                            </div>

                            {/* Stats Cards */}
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                <div className="bg-white border border-gray-200 p-6">
                                    <p className="text-sm font-medium text-gray-600 mb-2">Total Hospitals</p>
                                    <p className="text-3xl font-bold text-gray-900">{hospitalStats.total || 0}</p>
                                    <p className="text-sm text-gray-600 mt-2">Registered on platform</p>
                                </div>

                                <div className="bg-white border border-gray-200 p-6">
                                    <p className="text-sm font-medium text-gray-600 mb-2">Active Hospitals</p>
                                    <p className="text-3xl font-bold text-gray-900">{hospitalStats.active || 0}</p>
                                    <p className="text-sm text-gray-600 mt-2">Currently operational</p>
                                </div>

                                <div className="bg-white border border-gray-200 p-6">
                                    <p className="text-sm font-medium text-gray-600 mb-2">Inactive Hospitals</p>
                                    <p className="text-3xl font-bold text-gray-900">{hospitalStats.inactive || 0}</p>
                                    <p className="text-sm text-gray-600 mt-2">Temporarily disabled</p>
                                </div>
                            </div>

                            {/* Recent Hospitals */}
                            <div className="bg-white border border-gray-200">
                                <div className="px-6 py-4 border-b border-gray-200">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <h2 className="text-lg font-bold text-gray-900">Recent Hospitals</h2>
                                            <p className="text-gray-600 mt-1">Latest registered hospitals on the platform</p>
                                        </div>
                                        <button
                                            onClick={() => setActiveTab('hospitals')}
                                            className="px-4 py-2 bg-gray-900 text-white font-medium hover:bg-gray-700 transition-colors duration-200"
                                        >
                                            View All
                                        </button>
                                    </div>
                                </div>
                                
                                {loading ? (
                                    <div className="flex justify-center items-center h-32">
                                        <div className="w-8 h-8 border-2 border-gray-200 border-t-gray-900 animate-spin"></div>
                                    </div>
                                ) : hospitals.length > 0 ? (
                                    <div className="overflow-x-auto">
                                        <table className="min-w-full">
                                            <thead className="bg-gray-50">
                                                <tr>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Hospital</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Status</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Plan</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-600 uppercase">Created</th>
                                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-600 uppercase">Actions</th>
                                                </tr>
                                            </thead>
                                            <tbody className="bg-white divide-y divide-gray-200">
                                                {hospitals.slice(0, 5).map((hospital) => (
                                                    <tr key={hospital.id} className="hover:bg-gray-50">
                                                        <td className="px-6 py-4">
                                                            <div>
                                                                <p className="text-sm font-medium text-gray-900">{hospital.name}</p>
                                                                <p className="text-xs text-gray-600">ID: {hospital.customId || hospital.id}</p>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-4">
                                                            <span className={`px-2 py-1 text-xs font-medium ${
                                                                hospital.isActive 
                                                                    ? 'bg-gray-100 text-gray-900' 
                                                                    : 'bg-gray-200 text-gray-600'
                                                            }`}>
                                                                {hospital.isActive ? 'Active' : 'Inactive'}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-4">
                                                            <span className="px-2 py-1 text-xs font-medium bg-gray-100 text-gray-900">
                                                                {hospital.plan || 'FREE'}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-4 text-sm text-gray-600">
                                                            {new Date(hospital.createdAt).toLocaleDateString('en-US', {
                                                                year: 'numeric',
                                                                month: 'short',
                                                                day: 'numeric'
                                                            })}
                                                        </td>
                                                        <td className="px-6 py-4 text-right">
                                                            <button
                                                                onClick={() => handleToggleStatus(hospital.publicId || hospital.id, hospital.isActive)}
                                                                className={`px-3 py-1 text-xs font-medium transition-colors duration-200 ${
                                                                    hospital.isActive
                                                                        ? 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                                                        : 'bg-gray-900 text-white hover:bg-gray-700'
                                                                }`}
                                                            >
                                                                {hospital.isActive ? 'Deactivate' : 'Activate'}
                                                            </button>
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                ) : (
                                    <div className="p-12 text-center">
                                        <h3 className="text-lg font-medium text-gray-900 mb-2">No hospitals yet</h3>
                                        <p className="text-gray-600 mb-6">Get started by creating your first hospital</p>
                                        <button
                                            onClick={() => setShowCreateModal(true)}
                                            className="px-6 py-2 bg-gray-900 text-white font-medium hover:bg-gray-700 transition-colors duration-200"
                                        >
                                            Create Hospital
                                        </button>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}



                    {/* Error Display */}
                    {error && (
                        <div className="mb-6 bg-red-50 border border-red-300 p-4 rounded-lg">
                            <div className="flex items-start">
                                <div className="flex-shrink-0">
                                    <span className="text-red-700 font-medium">Error:</span>
                                </div>
                                <div className="ml-3">
                                    <p className="text-sm text-red-700">{error}</p>
                                </div>
                                <div className="ml-auto pl-3">
                                    <button
                                        onClick={() => setError('')}
                                        className="text-red-600 hover:text-red-900"
                                    >
                                        ×
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Content Sections */}
                    {loading ? (
                        <div className="flex justify-center items-center h-64">
                            <div className="text-center">
                                <div className="w-8 h-8 border-2 border-gray-200 border-t-gray-900 animate-spin mx-auto mb-4"></div>
                                <p className="text-gray-600">Loading platform data...</p>
                            </div>
                        </div>
                    ) : activeTab === 'hospitals' ? (
                        <div className="bg-white border border-gray-200">
                            <HospitalsTable
                                hospitals={hospitals}
                                hospitalPage={hospitalPage}
                                handleToggleStatus={handleToggleStatus}
                                openEditHospitalModal={openEditHospitalModal}
                                onResetPassword={handleResetPassword}
                                loadHospitals={loadHospitals}
                            />
                        </div>
                    ) : activeTab === 'audit_logs' ? (
                        <div className="bg-white border border-gray-200">
                            {auditLogs.length > 0 ? (
                                <AuditLogsTable auditLogs={auditLogs} />
                            ) : (
                                <div className="p-12 text-center">
                                    <h3 className="text-lg font-medium text-gray-900 mb-2">No audit logs yet</h3>
                                    <p className="text-gray-600">System activities will appear here once actions are performed</p>
                                </div>
                            )}
                        </div>
                    ) : null}
                </main>
            </div>

            {/* Confirmation Modal */}
            <ConfirmationModal
                isOpen={confirmModal.isOpen}
                title={confirmModal.title}
                message={confirmModal.message}
                onConfirm={confirmModal.onConfirm}
                onCancel={() => setConfirmModal(prev => ({ ...prev, isOpen: false }))}
                showReasonInput={confirmModal.showReasonInput}
                inputPlaceholder={confirmModal.inputPlaceholder}
            />

            {/* Create Hospital Modal */}
            {showCreateModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" onClick={() => setShowCreateModal(false)}>
                    <div className="bg-white border border-gray-200 w-full max-w-lg max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                        <div className="p-6">
                            <div className="mb-6">
                                <h2 className="text-xl font-bold text-gray-900 mb-2">Create New Hospital</h2>
                                <p className="text-gray-600">Add a new hospital to the platform with admin credentials</p>
                            </div>
                            
                            <form onSubmit={handleCreateHospital} className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-900 mb-2">Hospital Name</label>
                                    <input
                                        type="text"
                                        value={formData.hospitalName}
                                        onChange={(e) => {
                                            setFormData({ ...formData, hospitalName: e.target.value });
                                            if (errors.hospitalName) setErrors({ ...errors, hospitalName: null });
                                        }}
                                        placeholder="Enter hospital name"
                                        className={`w-full px-3 py-2 bg-white border text-gray-900 placeholder-gray-500 focus:border-gray-900 ${errors.hospitalName ? 'border-red-400 bg-red-50' : 'border-gray-200'}`}
                                    />
                                    {errors.hospitalName && <p className="text-red-600 text-sm font-medium mt-1">{errors.hospitalName}</p>}
                                </div>
                                
                                <div>
                                    <label className="block text-sm font-medium text-gray-900 mb-2">Admin Name</label>
                                    <input
                                        type="text"
                                        value={formData.adminName}
                                        onChange={(e) => {
                                            setFormData({ ...formData, adminName: e.target.value });
                                            if (errors.adminName) setErrors({ ...errors, adminName: null });
                                        }}
                                        placeholder="Enter admin full name"
                                        className={`w-full px-3 py-2 bg-white border text-gray-900 placeholder-gray-500 focus:border-gray-900 ${errors.adminName ? 'border-red-400 bg-red-50' : 'border-gray-200'}`}
                                    />
                                    {errors.adminName && <p className="text-red-600 text-sm font-medium mt-1">{errors.adminName}</p>}
                                </div>
                                
                                <div>
                                    <label className="block text-sm font-medium text-gray-900 mb-2">Admin Email</label>
                                    <input
                                        type="email"
                                        value={formData.adminEmail}
                                        onChange={(e) => {
                                            setFormData({ ...formData, adminEmail: e.target.value });
                                            if (errors.adminEmail) setErrors({ ...errors, adminEmail: null });
                                        }}
                                        placeholder="admin@hospital.com"
                                        className={`w-full px-3 py-2 bg-white border text-gray-900 placeholder-gray-500 focus:border-gray-900 ${errors.adminEmail ? 'border-red-400 bg-red-50' : 'border-gray-200'}`}
                                    />
                                    {errors.adminEmail && <p className="text-red-600 text-sm font-medium mt-1">{errors.adminEmail}</p>}
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-900 mb-2">Admin Password</label>
                                    <input
                                        type="password"
                                        value={formData.adminPassword}
                                        onChange={(e) => {
                                            setFormData({ ...formData, adminPassword: e.target.value });
                                            if (errors.adminPassword) setErrors({ ...errors, adminPassword: null });
                                        }}
                                        placeholder="Create secure password"
                                        className={`w-full px-3 py-2 bg-white border text-gray-900 placeholder-gray-500 focus:border-gray-900 ${errors.adminPassword ? 'border-red-400 bg-red-50' : 'border-gray-200'}`}
                                    />
                                    {errors.adminPassword && <p className="text-red-600 text-sm font-medium mt-1">{errors.adminPassword}</p>}
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-900 mb-3">Enabled Modules</label>
                                    <div className="grid grid-cols-2 gap-2">
                                        {AVAILABLE_MODULES.map(module => (
                                            <label key={module} className="flex items-center space-x-2 p-2 border border-gray-200 hover:bg-gray-50 cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={formData.modules.includes(module)}
                                                    onChange={(e) => {
                                                        const checked = e.target.checked;
                                                        setFormData(prev => ({
                                                            ...prev,
                                                            modules: checked
                                                                ? [...prev.modules, module]
                                                                : prev.modules.filter(m => m !== module)
                                                        }));
                                                    }}
                                                    className="w-4 h-4"
                                                />
                                                <span className="text-sm font-medium text-gray-900">{module}</span>
                                            </label>
                                        ))}
                                    </div>
                                    {formData.modules.length === 0 && <p className="mt-2 text-xs text-gray-600 font-medium">At least one module should be enabled.</p>}
                                </div>

                                <div className="flex gap-3 pt-4 border-t border-gray-200">
                                    <button
                                        type="button"
                                        onClick={() => setShowCreateModal(false)}
                                        className="flex-1 bg-gray-200 text-gray-900 px-4 py-2 font-medium hover:bg-gray-300"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className="flex-1 bg-gray-900 text-white px-4 py-2 font-medium hover:bg-gray-700"
                                    >
                                        Create Hospital
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            )}

            {/* Edit Hospital Modal */}
            {editHospitalModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" onClick={() => setEditHospitalModal({ ...editHospitalModal, isOpen: false })}>
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                        <div className="p-8">
                            <div className="text-center mb-8">
                                <div className="w-16 h-16 bg-gray-100 rounded-2xl flex items-center justify-center mx-auto mb-4">
                                    <svg className="w-8 h-8 text-gray-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                    </svg>
                                </div>
                                <h3 className="text-2xl font-bold text-gray-900 mb-2">Edit Hospital</h3>
                                <p className="text-gray-600">
                                    Update settings for <span className="font-semibold text-gray-900">{editHospitalModal.hospital?.name}</span>
                                </p>
                            </div>

                            <div className="space-y-6">
                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-2">Hospital Name</label>
                                    <input
                                        type="text"
                                        value={editHospitalModal.name}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, name: e.target.value })}
                                        className="w-full px-4 py-3 bg-gray-50 border-2 border-gray-200 rounded-xl text-gray-900 focus:bg-white focus:border-gray-900 focus:ring-4 focus:ring-gray-100 transition-all duration-200"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-2">Admin Name</label>
                                    <input
                                        type="text"
                                        value={editHospitalModal.adminName}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, adminName: e.target.value })}
                                        className="w-full px-4 py-3 bg-gray-50 border-2 border-gray-200 rounded-xl text-gray-900 focus:bg-white focus:border-gray-900 focus:ring-4 focus:ring-gray-100 transition-all duration-200"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-2">Admin Email</label>
                                    <input
                                        type="email"
                                        value={editHospitalModal.adminEmail}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, adminEmail: e.target.value })}
                                        className="w-full px-4 py-3 bg-gray-50 border-2 border-gray-200 rounded-xl text-gray-900 focus:bg-white focus:border-gray-900 focus:ring-4 focus:ring-gray-100 transition-all duration-200"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-2">Subscription Plan</label>
                                    <select
                                        value={editHospitalModal.plan}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, plan: e.target.value })}
                                        className="w-full px-4 py-3 bg-gray-50 border-2 border-gray-200 rounded-xl text-gray-900 focus:bg-white focus:border-gray-900 focus:ring-4 focus:ring-gray-100 transition-all duration-200"
                                    >
                                        <option value="FREE">FREE</option>
                                        <option value="BASIC">BASIC</option>
                                        <option value="PREMIUM">PREMIUM</option>
                                        <option value="ENTERPRISE">ENTERPRISE</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-semibold text-gray-700 mb-3">Enabled Modules</label>
                                    <div className="grid grid-cols-2 gap-3 max-h-48 overflow-y-auto">
                                        {AVAILABLE_MODULES.map(module => (
                                            <label key={module} className="flex items-center space-x-3 p-3 border-2 border-gray-200 rounded-xl hover:bg-gray-50 cursor-pointer transition-colors duration-200">
                                                <input
                                                    type="checkbox"
                                                    checked={editHospitalModal.modules.includes(module)}
                                                    onChange={(e) => {
                                                        const checked = e.target.checked;
                                                        setEditHospitalModal(prev => ({
                                                            ...prev,
                                                            modules: checked
                                                                ? [...prev.modules, module]
                                                                : prev.modules.filter(m => m !== module)
                                                        }));
                                                    }}
                                                    className="w-4 h-4 text-gray-900 bg-gray-100 border-gray-300 rounded focus:ring-gray-900 focus:ring-2"
                                                />
                                                <span className="text-sm font-medium text-gray-700">{module}</span>
                                            </label>
                                        ))}
                                    </div>
                                    {editHospitalModal.modules.length === 0 && <p className="mt-2 text-xs text-amber-600 font-medium">Warning: Disabling all modules may restrict access.</p>}
                                </div>

                                <div className="flex gap-4 pt-6 border-t border-gray-100">
                                    <button
                                        onClick={() => setEditHospitalModal({ ...editHospitalModal, isOpen: false })}
                                        className="flex-1 bg-gray-100 text-gray-700 px-6 py-3 rounded-xl font-semibold hover:bg-gray-200 transition-colors duration-200"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={handleHospitalUpdate}
                                        className="flex-1 bg-gray-900 text-white px-6 py-3 rounded-xl font-semibold hover:bg-gray-800 transition-colors duration-200"
                                    >
                                        Save Changes
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Password Display Modal */}
            {passwordModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" onClick={() => setPasswordModal({ ...passwordModal, isOpen: false })}>
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md" onClick={(e) => e.stopPropagation()}>
                        <div className="p-8 text-center">
                            <div className="w-16 h-16 bg-gray-100 rounded-2xl flex items-center justify-center mx-auto mb-6">
                                <svg className="w-8 h-8 text-gray-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                </svg>
                            </div>
                            
                            <h3 className="text-2xl font-bold text-gray-900 mb-2">Password Reset Successful</h3>
                            <p className="text-gray-600 mb-8">
                                Please copy and share these credentials with the hospital admin immediately.
                            </p>

                            <div className="bg-gray-50 rounded-2xl p-6 mb-8 text-left border border-gray-200">
                                <div className="mb-4">
                                    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Admin Email</span>
                                    <p className="text-gray-900 font-semibold text-lg mt-1">{passwordModal.email}</p>
                                </div>
                                <div>
                                    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">New Password</span>
                                    <div className="flex items-center justify-between mt-2">
                                        <code className="bg-white px-4 py-2 rounded-xl border border-gray-300 font-mono text-lg text-gray-900 font-bold flex-1 mr-3">
                                            {passwordModal.password}
                                        </code>
                                        <button
                                            onClick={() => {
                                                navigator.clipboard.writeText(passwordModal.password);
                                                success('Password copied to clipboard');
                                            }}
                                            className="px-4 py-2 bg-gray-100 text-gray-900 rounded-xl hover:bg-gray-200 transition-colors duration-200 font-semibold text-sm"
                                        >
                                            Copy
                                        </button>
                                    </div>
                                </div>
                            </div>

                            <button
                                onClick={() => setPasswordModal({ ...passwordModal, isOpen: false })}
                                className="w-full bg-gray-900 text-white px-6 py-3 rounded-xl font-semibold hover:bg-gray-800 transition-colors duration-200"
                            >
                                Done
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div >
    );
};

export default PlatformDashboard;

// Hospitals Table Component using DataTable
const HospitalsTable = ({ hospitals, hospitalPage, handleToggleStatus, openEditHospitalModal, onResetPassword, loadHospitals }) => {
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.No.',
            cell: info => hospitalPage.number * hospitalPage.size + info.row.index + 1,
        }),
        columnHelper.accessor(row => row.customId || row.id, {
            id: 'id',
            header: 'ID',
            cell: info => <span title="Serial Number">{info.getValue()}</span>,
        }),
        columnHelper.accessor('name', {
            header: 'Name',
            cell: info => <span className="font-medium text-gray-900">{info.getValue()}</span>,
        }),
        columnHelper.accessor('plan', {
            header: 'Plan',
            cell: info => (
                <div className="flex items-center">
                    <span className="px-2 py-1 bg-gray-100 text-gray-900 rounded text-xs font-semibold mr-2">{info.getValue()}</span>
                    <button
                        onClick={() => openEditHospitalModal(info.row.original)}
                        className="text-gray-400 hover:text-gray-900"
                        aria-label="Edit"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                            <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                        </svg>
                    </button>
                </div>
            )
        }),
        columnHelper.accessor('isActive', {
            header: 'Status',
            cell: info => (
                <span className="px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-gray-100 text-gray-800">
                    {info.getValue() ? 'Active' : 'Inactive'}
                </span>
            )
        }),
        columnHelper.accessor('createdAt', {
            header: 'Created At',
            cell: info => new Date(info.getValue()).toLocaleDateString(),
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">Actions</div>,
            cell: info => (
                <div className="text-right">
                    <ActionMenu actions={[
                        {
                            label: 'Edit Details',
                            onClick: () => openEditHospitalModal(info.row.original),
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" /></svg>
                        },
                        {
                            label: 'Reset Password',
                            onClick: () => onResetPassword(info.row.original.publicId || info.row.original.id, info.row.original.adminEmail),
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v3H2v-3a6 6 0 016-6 6 6 0 0110 0zm-2-3a1 1 0 10-2 0 1 1 0 002 0z" clipRule="evenodd" /></svg>,
                            danger: true
                        },
                        {
                            label: info.row.original.isActive ? 'Deactivate' : 'Activate',
                            onClick: () => handleToggleStatus(info.row.original.publicId || info.row.original.id, info.row.original.isActive),
                            icon: info.row.original.isActive
                                ? <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" /></svg>
                                : <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" /></svg>,
                            danger: info.row.original.isActive
                        }
                    ]} />
                </div>
            ),
        }),
    ];

    const pagination = {
        pageIndex: hospitalPage.number,
        pageSize: hospitalPage.size,
        totalItems: hospitalPage.totalElements,
        pageCount: hospitalPage.totalPages,
        onPageChange: (newPage) => loadHospitals(newPage, hospitalPage.size)
    };

    return (
        <DataTable
            data={hospitals}
            columns={columns}
            pagination={pagination}
            emptyState={
                <div className="p-12 text-center">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-12 w-12 mb-4 text-slate-400 mx-auto" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10l9-6 9 6v7a2 2 0 01-2 2H5a2 2 0 01-2-2v-7z" />
                    </svg>
                    <p className="text-gray-500">No hospitals registered yet</p>
                </div>
            }
        />
    );
};

// AuditLogsTable Component (Standardized)
const AuditLogsTable = ({ auditLogs }) => {
    const columnHelper = createColumnHelper();
    const [page, setPage] = useState(0);
    const pageSize = 10;

    // Helper function to format timestamp
    const formatTimestamp = (timestamp) => {
        const date = new Date(timestamp);
        return date.toLocaleString('en-IN', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: true
        });
    };

    // Helper function to get action badge color
    const getActionBadgeColor = (action) => {
        return 'bg-gray-100 text-gray-800';
    };

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.NO.',
            cell: info => (page * pageSize) + info.row.index + 1,
        }),
        columnHelper.accessor('timestamp', {
            header: 'TIMESTAMP',
            cell: info => (
                <span className="text-sm text-gray-600">
                    {formatTimestamp(info.getValue())}
                </span>
            ),
        }),
        columnHelper.accessor('action', {
            header: 'ACTION',
            cell: info => (
                <span className={`px-2 py-1 rounded-full text-xs font-semibold ${getActionBadgeColor(info.getValue())}`}>
                    {info.getValue()}
                </span>
            ),
        }),
        columnHelper.accessor('details', {
            header: 'DETAILS',
            cell: info => (
                <span className="text-sm text-gray-900">
                    {info.getValue()}
                </span>
            ),
        }),
        columnHelper.accessor('performedBy', {
            header: 'PERFORMED BY',
            cell: info => (
                <span className="text-sm text-gray-700 font-medium">
                    {info.getValue()}
                </span>
            ),
        }),
        columnHelper.accessor('entityType', {
            header: 'ENTITY TYPE',
            cell: info => (
                <span className="text-xs text-gray-500 uppercase">
                    {info.getValue() || 'N/A'}
                </span>
            ),
        }),
        columnHelper.accessor('reason', {
            header: 'REASON',
            cell: info => (
                <span className="text-sm text-gray-600 italic">
                    {info.getValue() || '-'}
                </span>
            ),
        }),
    ];

    // Client-side pagination logic
    const totalPages = Math.ceil(auditLogs.length / pageSize);
    const paginatedLogs = auditLogs.slice(page * pageSize, (page + 1) * pageSize);

    const pagination = {
        pageIndex: page,
        pageSize: pageSize,
        totalItems: auditLogs.length,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage)
    };

    return <DataTable data={paginatedLogs} columns={columns} pagination={pagination} />;
};

// Users Table Component
const UsersTable = ({ users, userPage, onResetPassword, loadUsers }) => {
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.accessor('name', {
            header: 'Name',
            cell: info => <span className="font-medium text-gray-900">{info.getValue()}</span>,
        }),
        columnHelper.accessor('email', {
            header: 'Email',
            cell: info => info.getValue(),
        }),
                columnHelper.accessor('role', {
            header: 'Role',
            cell: info => (
                        <span className="px-2 py-1 rounded text-xs font-semibold bg-gray-100 text-gray-800">
                    {info.getValue().replace('_', ' ')}
                </span>
            ),
        }),
        columnHelper.accessor('hospitalName', {
            header: 'Hospital',
            cell: info => info.getValue(),
        }),
        columnHelper.accessor('isActive', {
            header: 'Status',
            cell: info => (
                <span className="px-2 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-800">
                    {info.getValue() ? 'Active' : 'Inactive'}
                </span>
            ),
        }),
        columnHelper.display({
            id: 'actions',
            header: 'Actions',
            cell: info => (
                <ActionMenu
                    actions={[
                        {
                            label: 'Reset Password',
                            onClick: () => {
                                const uid = info.row.original.id;
                                if (uid) {
                                    onResetPassword(uid);
                                } else {
                                    // Fallback or alert
                                    console.error("User Public ID is missing", info.row.original);
                                    // Using toast context would require hooking it up here or passing it down
                                    // For now just safe guard to prevent 404/CORS error
                                    alert("Error: User data is incomplete (Missing Public ID). Please refresh the page. If issue persists, contact support.");
                                }
                            },
                            className: 'text-amber-600 hover:bg-amber-50'
                        }
                    ]}
                />
            ),
        }),
    ];

    const pagination = {
        pageIndex: userPage.number,
        pageSize: userPage.size,
        totalItems: userPage.totalElements,
        pageCount: userPage.totalPages,
        onPageChange: (newPage) => loadUsers(newPage, userPage.size)
    };

    return (
        <DataTable
            data={users}
            columns={columns}
            pagination={pagination}
            isLoading={false}
            emptyState={
                <div className="p-12 text-center">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-12 w-12 mb-4 text-slate-400 mx-auto" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a4 4 0 00-4-4h-1M9 20H4v-2a4 4 0 014-4h1m6-4a4 4 0 11-8 0 4 4 0 018 0z" />
                    </svg>
                    <p className="text-gray-500">No users found</p>
                </div>
            }
        />
    );
};
