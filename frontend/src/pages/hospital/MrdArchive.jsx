import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import hospitalService from '../../services/hospitalService';
import authService from '../../services/authService';
import { useToast } from '../../context/ToastContext';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import ProfileModal from '../../components/ProfileModal';
import { SkeletonTable } from '../../components/Skeleton';
import EmptyState from '../../components/EmptyState';
import { ArchiveBoxIcon, CheckBadgeIcon } from '@heroicons/react/24/outline';

const MrdArchive = () => {
    const navigate = useNavigate();
    const { success, error: toastError } = useToast();
    const [user] = useState(() => authService.getCurrentUser() || {});
    
    const getEffectiveRole = () => {
        const activeDashboard = sessionStorage.getItem('activeDashboard');
        return user?.role === 'HOSPITAL_ADMIN' && user?.isSingleDoctor && activeDashboard !== 'admin' 
            ? 'DOCTOR' 
            : user?.role;
    };

    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);
    const [activeTab, setActiveTab] = useState(() => {
        const currentUser = authService.getCurrentUser() || {};
        const activeDashboard = sessionStorage.getItem('activeDashboard');
        const effectiveRole = currentUser.role === 'HOSPITAL_ADMIN' && currentUser.isSingleDoctor && activeDashboard !== 'admin'
            ? 'DOCTOR'
            : currentUser.role;
        return effectiveRole === 'DOCTOR' ? 'archived' : 'pending';
    });
    
    const [pendingRecords, setPendingRecords] = useState([]);
    const [archivedRecords, setArchivedRecords] = useState([]);
    const [loading, setLoading] = useState(true);

    // Modal state for archiving
    const [archiveModal, setArchiveModal] = useState({ isOpen: false, ipdAdmissionId: null, rackLocation: '', saving: false });

    useEffect(() => {
        fetchData();
    }, [activeTab]);

    const fetchData = async () => {
        setLoading(true);
        try {
            if (activeTab === 'pending') {
                const res = await hospitalService.getPendingMrdArchives();
                setPendingRecords(res || []);
            } else {
                const res = await hospitalService.getArchivedMrdRecords();
                setArchivedRecords(res || []);
            }
        } catch (err) {
            console.error(err);
            toastError('Failed to load MRD records');
        } finally {
            setLoading(false);
        }
    };

    const handleLogout = () => {
        const loginUrl = authService.getLoginUrl();
        authService.logout();
        navigate(loginUrl);
    };

    const getSidebarTabs = () => {
        const role = getEffectiveRole();
        const modules = user?.modules || [];
        const hasOPD = modules.includes('OPD');
        const hasIPD = modules.includes('IPD');
        const hasBilling = modules.includes('BILLING');
        const hasAppointments = modules.includes('APPOINTMENTS');
        const hasMedicalInventory = modules.includes('MEDICAL_INVENTORY');
        const hasHospitalInventory = modules.includes('HOSPITAL_INVENTORY');

        if (role === 'RECEPTIONIST') {
            return [
                { id: 'overview', label: 'Overview', path: '/hospital/receptionist' },
                ...(hasOPD ? [{ id: 'patients', label: 'Patients', path: '/hospital/receptionist?tab=patients' }] : []),
                ...(hasAppointments ? [{ id: 'appointments', label: 'Appointments', path: '/hospital/receptionist?tab=appointments' }] : []),
                ...(hasOPD ? [{ id: 'opd', label: 'OPD', path: '/hospital/receptionist?tab=opd' }] : []),
                ...(hasIPD ? [{ id: 'ipd', label: 'IPD', path: '/hospital/receptionist?tab=ipd' }] : []),
                ...(hasIPD ? [{ id: 'mrd', label: 'MRD Archive', path: '/hospital/receptionist/mrd' }] : []),
                ...(hasBilling && user?.billingHandler !== 'DOCTOR' ? [{ id: 'billing', label: 'Billing', path: '/hospital/receptionist?tab=billing' }] : []),
                ...(hasMedicalInventory && user?.inClinic !== false ? [{ id: 'inventory', label: 'Medicine Inventory', path: '/hospital/receptionist?tab=inventory' }] : []),
            ];
        }
        
        if (role === 'HOSPITAL_ADMIN') {
            const allTabs = [
                { id: 'overview', label: 'Overview', path: '/hospital/admin' },
                { id: 'patients', label: 'Patients', path: '/hospital/admin?tab=patients', requiredModule: 'OPD' },
                { id: 'appointments', label: 'Appointments', path: '/hospital/admin?tab=appointments', requiredModule: 'APPOINTMENTS' },
                { id: 'opd', label: 'OPD', path: '/hospital/admin?tab=opd', requiredModule: 'OPD' },
                { id: 'wards', label: 'Wards & Beds', path: '/hospital/admin?tab=wards', requiredModule: 'IPD' },
                { id: 'doctors', label: 'Doctors', path: '/hospital/admin?tab=doctors', requiredModule: 'OPD' },
                { id: 'receptionists', label: 'Receptionists', path: '/hospital/admin?tab=receptionists', requiredModule: 'OPD' },
                { id: 'nurses', label: 'Nurses', path: '/hospital/admin?tab=nurses', requiredModule: 'OPD' },
                { id: 'lab-technicians', label: 'Lab Technicians', path: '/hospital/admin?tab=lab-technicians', requiredModule: 'OPD' },
                { id: 'radiology-technicians', label: 'Radiology Technicians', path: '/hospital/admin?tab=radiology-technicians', requiredModule: 'OPD' },
                { id: 'billing', label: 'Billing', path: '/hospital/admin?tab=billing', requiredModule: 'BILLING' },
                { id: 'pharmacy', label: 'Pharmacy', path: '/hospital/admin?tab=pharmacy', requiredModule: 'PHARMACY' },
                { id: 'pharmacists', label: 'Pharmacists', path: '/hospital/admin?tab=pharmacists', requiredModule: 'PHARMACY' },
                { id: 'inventory', label: 'Medicine Inventory', path: '/hospital/admin?tab=inventory', requiredModule: 'MEDICAL_INVENTORY' },
                { id: 'hospital-inventory', label: 'Hospital Inventory', path: '/hospital/admin?tab=hospital-inventory', requiredModule: 'HOSPITAL_INVENTORY' },
                { id: 'pathology', label: 'Pathology', path: '/hospital/admin?tab=pathology', requiredModule: 'PATHOLOGY' },
                { id: 'ipd', label: 'IPD', path: '/hospital/admin?tab=ipd', requiredModule: 'IPD' },
                { id: 'mrd', label: 'MRD Archive', path: '/hospital/admin/mrd', requiredModule: 'IPD' },
                { id: 'ot', label: 'Operation Theatre', path: '/hospital/admin?tab=ot', requiredModule: 'OT' },
                { id: 'fees', label: 'Fees', path: '/hospital/admin?tab=fees', requiredModule: 'BILLING' },
                { id: 'audit-logs', label: 'Audit Logs', path: '/hospital/admin?tab=audit-logs', requiredModule: null },
                { id: 'analytics', label: 'Reports & Analytics', path: '/hospital/admin?tab=analytics', requiredModule: 'REPORTS' },
                { id: 'masters', label: 'Master Data', path: '/hospital/admin?tab=masters', requiredModule: null },
                { id: 'messages', label: 'Messages', path: '/hospital/admin?tab=messages', requiredModule: null },
                { id: 'settings', label: 'Settings', path: '/hospital/admin?tab=settings', requiredModule: null },
                { id: 'support', label: 'Support', path: '/hospital/admin?tab=support', requiredModule: null },
            ];

            return allTabs.filter(tab => !tab.requiredModule || modules.includes(tab.requiredModule));
        }
        
        if (role === 'DOCTOR') {
            const isSolo = user?.receptionMode === 'SOLO';
            const hasBilling = user?.billingHandler === 'DOCTOR' || user?.billingHandler === 'BOTH';
            const hasInClinic = user?.inClinic !== false;

            return [
                { id: 'overview', label: 'Overview', path: '/hospital/doctor' },
                ...(hasIPD ? [{ id: 'ipd', label: 'IPD', path: '/hospital/doctor?tab=ipd' }] : []),
                ...(hasIPD ? [{ id: 'mrd', label: 'MRD Archive', path: '/hospital/doctor/mrd' }] : []),
                { id: 'opd', label: 'OPD', path: '/hospital/doctor?tab=opd' },
                { id: 'patients', label: 'Patients', path: '/hospital/doctor?tab=patients' },
                ...((isSolo || hasBilling) ? [{ id: 'billing', label: 'Billing', path: '/hospital/doctor?tab=billing' }] : []),
                ...((isSolo && hasInClinic && hasMedicalInventory) ? [{ id: 'inventory', label: 'Medicine Inventory', path: '/hospital/doctor?tab=inventory' }] : []),
                ...(isSolo && hasHospitalInventory ? [{ id: 'hospital-inventory', label: 'Hospital Inventory', path: '/hospital/doctor?tab=hospital-inventory' }] : []),
            ];
        }
        
        return [];
    };

    const handleTabChange = (tabId) => {
        const tabs = getSidebarTabs();
        const tab = tabs.find(t => t.id === tabId);
        if (tab) navigate(tab.path);
    };

    const openArchiveModal = (ipdId) => {
        setArchiveModal({ isOpen: true, ipdAdmissionId: ipdId, rackLocation: '', saving: false });
    };

    const submitArchive = async () => {
        if (!archiveModal.rackLocation.trim()) {
            return toastError('Shelf/Rack location is required');
        }
        setArchiveModal(prev => ({ ...prev, saving: true }));
        try {
            await hospitalService.archiveMrdRecord({
                ipdAdmissionId: archiveModal.ipdAdmissionId,
                rackLocation: archiveModal.rackLocation,
                overrideReason: archiveModal.overrideReason?.trim() || null
            });
            success('Admission archived successfully in MRD');
            setArchiveModal({ isOpen: false, ipdAdmissionId: null, rackLocation: '', saving: false });
            fetchData();
        } catch (err) {
            console.error(err);
            const msg = err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to archive record';
            // Form 02 completeness gate: reveal the audited-override field
            if (typeof msg === 'string' && msg.toLowerCase().includes('incomplete')) {
                setArchiveModal(prev => ({ ...prev, saving: false, incompleteMsg: msg }));
            } else {
                toastError(msg);
                setArchiveModal(prev => ({ ...prev, saving: false }));
            }
        }
    };

    return (
        <div className="flex h-screen bg-gray-50 overflow-hidden">
            <Sidebar 
                title="HMS Portal" 
                tabs={getSidebarTabs()} 
                activeTab="mrd" 
                onTabChange={handleTabChange} 
                footerTitle="Hospital" 
                footerData={user?.hospitalName} 
                isCollapsed={sidebarCollapsed}
            />

            <div className="flex-1 flex flex-col h-full relative overflow-hidden">
                <Navbar 
                    title="Medical Records Department (MRD)" 
                    user={user} 
                    onLogout={handleLogout} 
                    onProfile={() => setProfileOpen(true)} 
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)} 
                />

                <main className="flex-1 overflow-y-auto p-6">
                    <PageHeader 
                        title="MRD Archive" 
                        subtitle="Manage discharged patient records and physical archives." 
                    />

                    {getEffectiveRole() !== 'DOCTOR' && (
                        <div className="flex border-b mb-6 mt-4">
                            <button
                                className={`px-4 py-2 font-medium text-sm transition-colors ${activeTab === 'pending' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
                                onClick={() => setActiveTab('pending')}
                            >
                                Pending Archive
                            </button>
                            <button
                                className={`px-4 py-2 font-medium text-sm transition-colors ${activeTab === 'archived' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
                                onClick={() => setActiveTab('archived')}
                            >
                                Archived Records
                            </button>
                        </div>
                    )}

                    {loading ? (
                        <SkeletonTable rows={5} cols={5} />
                    ) : activeTab === 'pending' ? (
                        pendingRecords.length > 0 ? (
                            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                                <table className="w-full text-left text-sm text-gray-600">
                                    <thead className="bg-gray-50 text-gray-700 font-semibold border-b">
                                        <tr>
                                            <th className="p-4">IPD Number</th>
                                            <th className="p-4">Patient Name</th>
                                            <th className="p-4">Discharge Date</th>
                                            <th className="p-4">Doctor</th>
                                            <th className="p-4 text-right">Action</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-100">
                                        {pendingRecords.map((r, i) => (
                                            <tr key={i} className="hover:bg-gray-50 transition-colors">
                                                <td className="p-4 font-medium text-gray-900">{r.ipdNumber}</td>
                                                <td className="p-4">{r.patientName}</td>
                                                <td className="p-4">{r.dischargeDateTime ? new Date(r.dischargeDateTime).toLocaleDateString() : '-'}</td>
                                                <td className="p-4">{r.doctorName || '-'}</td>
                                                <td className="p-4 text-right">
                                                    <button 
                                                        onClick={() => openArchiveModal(r.ipdAdmissionId)}
                                                        className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-semibold shadow-sm hover:bg-blue-700 transition-colors inline-flex items-center gap-1"
                                                    >
                                                        <ArchiveBoxIcon className="w-4 h-4" /> Move to MRD
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <EmptyState icon="ArchiveBoxIcon" title="No Pending Records" message="There are currently no discharged patients pending MRD archival." />
                        )
                    ) : (
                        archivedRecords.length > 0 ? (
                            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                                <table className="w-full text-left text-sm text-gray-600">
                                    <thead className="bg-gray-50 text-gray-700 font-semibold border-b">
                                        <tr>
                                            <th className="p-4">MRD #</th>
                                            <th className="p-4">Patient / IPD</th>
                                            <th className="p-4">Shelf / Rack</th>
                                            <th className="p-4">Archived By</th>
                                            <th className="p-4">Archived On</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-100">
                                        {archivedRecords.map((r, i) => (
                                            <tr key={i} className="hover:bg-gray-50 transition-colors">
                                                <td className="p-4 font-bold text-gray-900 flex items-center gap-2">
                                                    <CheckBadgeIcon className="w-5 h-5 text-green-600" />
                                                    {r.mrdNumber}
                                                </td>
                                                <td className="p-4">
                                                    <div className="font-medium text-gray-900">{r.patientName}</div>
                                                    <div className="text-xs text-gray-500">{r.ipdNumber}</div>
                                                </td>
                                                <td className="p-4 font-mono text-gray-700 bg-gray-50">{r.rackLocation}</td>
                                                <td className="p-4">{r.archivedByName}</td>
                                                <td className="p-4">{new Date(r.archivedAt).toLocaleString()}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <EmptyState icon="ArchiveBoxIcon" title="No Archived Records" message="No records have been archived in the MRD yet." />
                        )
                    )}
                </main>
            </div>

            <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />

            {/* Archive Modal */}
            {archiveModal.isOpen && (
                <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50">
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-md overflow-hidden animate-fade-in-up">
                        <div className="px-6 py-4 border-b border-gray-100 bg-gray-50/50 flex items-center gap-3">
                            <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
                                <ArchiveBoxIcon className="w-6 h-6" />
                            </div>
                            <h3 className="text-lg font-bold text-gray-900">Archive to MRD</h3>
                        </div>
                        <div className="p-6">
                            <p className="text-sm text-gray-600 mb-4">
                                This will lock the clinical record and mark it as archived. This action cannot be undone.
                            </p>
                            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Physical Location (Shelf/Rack)</label>
                            <input 
                                type="text"
                                className="w-full border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 mb-6"
                                placeholder="e.g., RACK-A-12"
                                value={archiveModal.rackLocation}
                                onChange={e => setArchiveModal(p => ({ ...p, rackLocation: e.target.value }))}
                                autoFocus
                            />
                            {archiveModal.incompleteMsg && (
                                <div className="mb-6 bg-amber-50 border border-amber-200 rounded-lg p-3">
                                    <p className="text-xs font-semibold text-amber-800 mb-2">⚠️ {archiveModal.incompleteMsg}</p>
                                    <label className="block text-xs font-semibold text-gray-700 mb-1">Override Reason (audited) *</label>
                                    <textarea
                                        rows={2}
                                        className="w-full border-amber-300 rounded-lg shadow-sm focus:ring-amber-500 focus:border-amber-500 text-sm"
                                        placeholder="e.g. Legacy paper file — documents verified physically"
                                        value={archiveModal.overrideReason || ''}
                                        onChange={e => setArchiveModal(p => ({ ...p, overrideReason: e.target.value }))}
                                    />
                                </div>
                            )}
                            <div className="flex justify-end gap-3">
                                <button 
                                    className="px-4 py-2 text-sm font-semibold text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                                    onClick={() => setArchiveModal({ isOpen: false, ipdAdmissionId: null, rackLocation: '', saving: false })}
                                >
                                    Cancel
                                </button>
                                <button 
                                    className="px-4 py-2 text-sm font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center gap-2"
                                    onClick={submitArchive}
                                    disabled={archiveModal.saving || !archiveModal.rackLocation.trim()}
                                >
                                    {archiveModal.saving ? 'Saving...' : 'Confirm Archive'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default MrdArchive;
