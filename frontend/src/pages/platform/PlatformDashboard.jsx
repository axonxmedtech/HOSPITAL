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
        { id: 'dashboard', label: 'Dashboard', icon: '📊' },
        { id: 'hospitals', label: 'Hospitals', icon: '🏥' },
        { id: 'audit_logs', label: 'Audit Logs', icon: '📋' },
    ];

    return (
        <div className="flex h-screen bg-neutral-50">
            {/* Sidebar */}
            <Sidebar
                title="HMS Platform"
                tabs={tabs}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                footerTitle="Platform"
                footerData="Super Admin"
                variant="purple"
            />

            {/* Main Content Wrapper */}
            <div className="flex-1 flex flex-col overflow-hidden">
                {/* Navbar */}
                <Navbar
                    title={tabs.find(t => t.id === activeTab)?.label}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => console.log('Profile clicked')}
                />

                {/* Main Content Area */}
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-neutral-50 p-8">
                    {/* Standardized Header */}
                    {activeTab !== 'dashboard' && (
                        <PageHeader
                            title={tabs.find(t => t.id === activeTab)?.label}
                            subtitle={activeTab === 'hospitals' ? 'Manage registered hospitals here.' : 'View system audit logs and activities.'}
                            onAdd={activeTab === 'hospitals' ? () => setShowCreateModal(true) : null}
                            addLabel="Create Hospital"
                        />
                    )}

                    {/* Dashboard Tab */}
                    {activeTab === 'dashboard' && (
                        <>
                            {/* Stats Cards */}
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                                <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-600">Total Hospitals</p>
                                            <p className="text-3xl font-bold text-gray-900 mt-2">{hospitalStats.total || 0}</p>
                                        </div>
                                        <div className="bg-blue-100 p-3 rounded-lg">
                                            <span className="text-2xl">🏥</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-600">Active Hospitals</p>
                                            <p className="text-3xl font-bold text-green-600 mt-2">{hospitalStats.active || 0}</p>
                                        </div>
                                        <div className="bg-green-100 p-3 rounded-lg">
                                            <span className="text-2xl">✅</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-600">Inactive Hospitals</p>
                                            <p className="text-3xl font-bold text-red-600 mt-2">{hospitalStats.inactive || 0}</p>
                                        </div>
                                        <div className="bg-red-100 p-3 rounded-lg">
                                            <span className="text-2xl">❌</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Hospital List on Dashboard */}
                            <div className="bg-white rounded-xl shadow-sm border border-gray-100">
                                <div className="p-6 border-b border-gray-100">
                                    <h3 className="text-lg font-semibold text-gray-900">All Hospitals</h3>
                                    <p className="text-sm text-gray-500 mt-1">Quick overview of all registered hospitals</p>
                                </div>
                                {loading ? (
                                    <div className="flex justify-center items-center h-64">
                                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-500"></div>
                                    </div>
                                ) : hospitals.length > 0 ? (
                                    <div className="overflow-x-auto">
                                        <table className="min-w-full divide-y divide-gray-200">
                                            <thead className="bg-gray-50">
                                                <tr>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Hospital Name</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Plan</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                                                </tr>
                                            </thead>
                                            <tbody className="bg-white divide-y divide-gray-200">
                                                {hospitals.map((hospital) => (
                                                    <tr key={hospital.id} className="hover:bg-gray-50 transition">
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                                                            {hospital.name}
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <span className={`px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${hospital.isActive ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                                                                }`}>
                                                                {hospital.isActive ? 'Active' : 'Inactive'}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <span className="px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-purple-100 text-purple-800">
                                                                {hospital.plan || 'FREE'}
                                                            </span>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium space-x-2">
                                                            <button
                                                                onClick={() => handleToggleStatus(hospital.publicId || hospital.id, hospital.isActive)}
                                                                className={`px-3 py-1 rounded-lg text-xs font-medium ${hospital.isActive
                                                                    ? 'bg-red-100 text-red-700 hover:bg-red-200'
                                                                    : 'bg-green-100 text-green-700 hover:bg-green-200'
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
                                        <span className="text-4xl mb-4 block">🏥</span>
                                        <p className="text-gray-500">No hospitals registered yet</p>
                                    </div>
                                )}
                            </div>
                        </>
                    )}



                    {error && (
                        <div className="bg-red-50 border-l-4 border-red-500 text-red-700 p-4 mb-6 rounded">
                            {error}
                        </div>
                    )}

                    {loading ? (
                        <div className="flex justify-center items-center h-64">
                            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-500"></div>
                        </div>
                    ) : activeTab === 'hospitals' ? (
                        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
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
                        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                            {auditLogs.length > 0 ? (
                                <AuditLogsTable auditLogs={auditLogs} />
                            ) : (
                                <EmptyState
                                    icon="📜"
                                    title="No Audit Logs"
                                    message="No activity has been logged yet."
                                />
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
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                        <div className="p-6">
                            <h2 className="text-2xl font-bold text-gray-800 mb-6">Create New Hospital</h2>
                            <form onSubmit={handleCreateHospital} className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Hospital Name</label>
                                    <input
                                        type="text"
                                        value={formData.hospitalName}
                                        onChange={(e) => {
                                            setFormData({ ...formData, hospitalName: e.target.value });
                                            if (errors.hospitalName) setErrors({ ...errors, hospitalName: null });
                                        }}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent ${errors.hospitalName ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.hospitalName && <p className="text-red-500 text-xs mt-1">{errors.hospitalName}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Admin Name</label>
                                    <input
                                        type="text"
                                        value={formData.adminName}
                                        onChange={(e) => {
                                            setFormData({ ...formData, adminName: e.target.value });
                                            if (errors.adminName) setErrors({ ...errors, adminName: null });
                                        }}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent ${errors.adminName ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.adminName && <p className="text-red-500 text-xs mt-1">{errors.adminName}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Admin Email</label>
                                    <input
                                        type="email"
                                        value={formData.adminEmail}
                                        onChange={(e) => {
                                            setFormData({ ...formData, adminEmail: e.target.value });
                                            if (errors.adminEmail) setErrors({ ...errors, adminEmail: null });
                                        }}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent ${errors.adminEmail ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.adminEmail && <p className="text-red-500 text-xs mt-1">{errors.adminEmail}</p>}
                                    {/* Admin Password */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Admin Password</label>
                                        <input
                                            type="password"
                                            value={formData.adminPassword}
                                            onChange={(e) => setFormData({ ...formData, adminPassword: e.target.value })}
                                            className={`mt-1 block w-full rounded-md border ${errors.adminPassword ? 'border-red-500' : 'border-gray-300'} px-3 py-2 shadow-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500`}
                                        />
                                        {errors.adminPassword && <p className="mt-1 text-xs text-red-500">{errors.adminPassword}</p>}
                                    </div>

                                    {/* Modules Selection */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">Enabled Modules</label>
                                        <div className="grid grid-cols-2 gap-2">
                                            {AVAILABLE_MODULES.map(module => (
                                                <label key={module} className="flex items-center space-x-2 p-2 border rounded-md hover:bg-gray-50 cursor-pointer">
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
                                                        className="rounded text-purple-600 focus:ring-purple-500"
                                                    />
                                                    <span className="text-sm text-gray-700">{module}</span>
                                                </label>
                                            ))}
                                        </div>
                                        {formData.modules.length === 0 && <p className="mt-1 text-xs text-amber-500">At least one module should be enabled.</p>}
                                    </div>
                                </div>

                                <div className="mt-6 flex justify-end space-x-3">
                                    <button
                                        type="button"
                                        onClick={() => setShowCreateModal(false)}
                                        className="flex-1 bg-gray-200 text-gray-700 px-4 py-2 rounded-lg font-medium hover:bg-gray-300 transition"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className="flex-1 bg-gradient-to-r from-purple-600 to-indigo-600 text-white px-4 py-2 rounded-lg font-medium hover:shadow-lg transition"
                                    >
                                        Create
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
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-md" onClick={(e) => e.stopPropagation()}>
                        <div className="p-6">
                            <h3 className="text-xl font-bold text-gray-800 mb-4">Edit Hospital Details</h3>
                            <p className="text-sm text-gray-600 mb-6">
                                Update settings for <span className="font-semibold">{editHospitalModal.hospital?.name}</span>
                            </p>

                            <div className="space-y-6">
                                {/* Hospital Name */}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Hospital Name</label>
                                    <input
                                        type="text"
                                        value={editHospitalModal.name}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, name: e.target.value })}
                                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                                    />
                                </div>

                                {/* Admin Name */}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Admin Name</label>
                                    <input
                                        type="text"
                                        value={editHospitalModal.adminName}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, adminName: e.target.value })}
                                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                                    />
                                </div>

                                {/* Admin Email */}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Admin Email</label>
                                    <input
                                        type="email"
                                        value={editHospitalModal.adminEmail}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, adminEmail: e.target.value })}
                                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                                    />
                                </div>

                                {/* Plan Selection */}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Subscription Plan</label>
                                    <select
                                        value={editHospitalModal.plan}
                                        onChange={(e) => setEditHospitalModal({ ...editHospitalModal, plan: e.target.value })}
                                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                                    >
                                        <option value="FREE">FREE</option>
                                        <option value="BASIC">BASIC</option>
                                        <option value="PREMIUM">PREMIUM</option>
                                        <option value="ENTERPRISE">ENTERPRISE</option>
                                    </select>
                                </div>

                                {/* Modules Selection */}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Enabled Modules</label>
                                    <div className="grid grid-cols-2 gap-2 max-h-48 overflow-y-auto p-1">
                                        {AVAILABLE_MODULES.map(module => (
                                            <label key={module} className="flex items-center space-x-2 p-2 border rounded-md hover:bg-gray-50 cursor-pointer">
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
                                                    className="rounded text-purple-600 focus:ring-purple-500"
                                                />
                                                <span className="text-sm text-gray-700">{module}</span>
                                            </label>
                                        ))}
                                    </div>
                                    {editHospitalModal.modules.length === 0 && <p className="mt-1 text-xs text-amber-500">Warning: Disabling all modules may restrict access.</p>}
                                </div>

                                <div className="flex gap-3 pt-4 border-t border-gray-100">
                                    <button
                                        onClick={() => setEditHospitalModal({ ...editHospitalModal, isOpen: false })}
                                        className="flex-1 bg-gray-100 text-gray-700 px-4 py-2 rounded-lg font-medium hover:bg-gray-200 transition"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={handleHospitalUpdate}
                                        className="flex-1 bg-purple-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-purple-700 transition"
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
            {
                passwordModal.isOpen && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" onClick={() => setPasswordModal({ ...passwordModal, isOpen: false })}>
                        <div className="bg-white rounded-xl shadow-2xl w-full max-w-sm text-center" onClick={(e) => e.stopPropagation()}>
                            <div className="p-8">
                                <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                                    <span className="text-3xl">🔑</span>
                                </div>
                                <h3 className="text-xl font-bold text-gray-800 mb-2">Password Reset Successful</h3>
                                <p className="text-gray-600 text-sm mb-6">
                                    Please copy and share these credentials with the hospital admin immediately.
                                </p>

                                <div className="bg-gray-50 rounded-lg p-4 mb-6 text-left border border-gray-200">
                                    <div className="mb-3">
                                        <span className="text-xs font-semibold text-gray-500 uppercase">Admin Email</span>
                                        <p className="text-gray-900 font-medium">{passwordModal.email}</p>
                                    </div>
                                    <div>
                                        <span className="text-xs font-semibold text-gray-500 uppercase">New Password</span>
                                        <div className="flex items-center justify-between mt-1">
                                            <code className="bg-white px-2 py-1 rounded border border-gray-300 font-mono text-lg text-purple-700 font-bold">
                                                {passwordModal.password}
                                            </code>
                                            <button
                                                onClick={() => {
                                                    navigator.clipboard.writeText(passwordModal.password);
                                                    success('Password copied to clipboard');
                                                }}
                                                className="text-gray-400 hover:text-purple-600 text-sm font-medium"
                                            >
                                                Copy
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                <button
                                    onClick={() => setPasswordModal({ ...passwordModal, isOpen: false })}
                                    className="w-full bg-purple-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-purple-700 transition"
                                >
                                    Done
                                </button>
                            </div>
                        </div>
                    </div>
                )
            }
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
                    <span className="px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs font-semibold mr-2">{info.getValue()}</span>
                    <button
                        onClick={() => openEditHospitalModal(info.row.original)}
                        className="text-gray-400 hover:text-blue-600"
                    >
                        ✎
                    </button>
                </div>
            )
        }),
        columnHelper.accessor('isActive', {
            header: 'Status',
            cell: info => (
                <span className={`px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${info.getValue()
                    ? 'bg-green-100 text-green-800'
                    : 'bg-red-100 text-red-800'
                    }`}>
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
                            onClick: () => openConfirmation(
                                'Reset Admin Password',
                                'Are you sure you want to reset the admin password for this hospital?',
                                (reason) => onResetPassword(info.row.original.publicId || info.row.original.id, reason),
                                true,
                                "Reason for password reset"
                            ),
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v3H2v-3a6 6 0 016-6 6 6 0 0110 0zm-2-3a1 1 0 10-2 0 1 1 0 002 0z" clipRule="evenodd" /></svg>,
                            danger: true
                        },
                        {
                            label: info.row.original.isActive ? 'Deactivate' : 'Activate',
                            onClick: () => openConfirmation(
                                info.row.original.isActive ? 'Deactivate Hospital' : 'Activate Hospital',
                                `Are you sure you want to ${info.row.original.isActive ? 'deactivate' : 'activate'} this hospital?`,
                                (reason) => handleToggleStatus(info.row.original.publicId || info.row.original.id, info.row.original.isActive, reason),
                                true,
                                "Reason for status change"
                            ),
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
                    <span className="text-4xl mb-4 block">🏥</span>
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
        if (action.includes('CREATED') || action.includes('ADDED')) {
            return 'bg-green-100 text-green-800';
        } else if (action.includes('DELETED') || action.includes('REMOVED')) {
            return 'bg-red-100 text-red-800';
        } else if (action.includes('UPDATED') || action.includes('MODIFIED')) {
            return 'bg-blue-100 text-blue-800';
        } else if (action.includes('CANCELLED')) {
            return 'bg-yellow-100 text-yellow-800';
        }
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
                <span className={`px-2 py-1 rounded text-xs font-semibold ${info.getValue() === 'HOSPITAL_ADMIN' ? 'bg-purple-100 text-purple-800' :
                    info.getValue() === 'DOCTOR' ? 'bg-blue-100 text-blue-800' :
                        'bg-green-100 text-green-800'
                    }`}>
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
                <span className={`px-2 py-1 rounded-full text-xs font-semibold ${info.getValue() ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                    }`}>
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
                    <span className="text-4xl mb-4 block">👥</span>
                    <p className="text-gray-500">No users found</p>
                </div>
            }
        />
    );
};
