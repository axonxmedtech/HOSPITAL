import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import authService from '../../services/authService';
import hospitalService from '../../services/hospitalService';
import { useToast } from '../../context/ToastContext';
import EmptyState from '../../components/EmptyState';
import ConfirmationModal from '../../components/ConfirmationModal';
import ActionMenu from '../../components/ActionMenu';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import HistoryDrawer from '../../components/HistoryDrawer';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';

import PageHeader from '../../components/PageHeader';
import { createColumnHelper } from '@tanstack/react-table';
import ConsultationModal from '../../components/ConsultationModal';
import PrescriptionViewModal from '../../components/PrescriptionViewModal';

/**
 * DoctorDashboard - Doctor dashboard
 * 
 * This page allows Doctor to:
 * - View their own appointments
 * - Manage appointment status
 * - View patients
 * 
 * @author HMS Team
 * @version Phase-1
 */
const DoctorDashboard = () => {
    const user = authService.getCurrentUser();
    console.log(user)
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'appointments';
    const viewFilter = searchParams.get('appointmentFilter') || 'today';
    const patientViewFilter = searchParams.get('patientFilter') || 'history';

    // Helper to switch tabs
    const setActiveTab = (tab) => {
        const newParams = { tab };
        if (tab === 'appointments' && viewFilter) newParams.appointmentFilter = viewFilter;
        if (tab === 'patients' && patientViewFilter) newParams.patientFilter = patientViewFilter;
        setSearchParams(newParams);
    };

    // Helper to set appointment filter
    const setViewFilter = (filter) => {
        setSearchParams({ tab: activeTab, appointmentFilter: filter });
    };

    // Helper to set patient filter
    const setPatientViewFilter = (filter) => {
        setSearchParams({ tab: activeTab, patientFilter: filter });
    };

    const [appointments, setAppointments] = useState([]);
    const [patients, setPatients] = useState([]);
    const [loading, setLoading] = useState(false);

    // View Prescription Modal State
    const [viewPrescriptionModal, setViewPrescriptionModal] = useState({
        isOpen: false,
        patient: null
    });

    // Stats State
    const [stats, setStats] = useState({ today: 0, pending: 0, total: 0 });
    // Queue / OPD state (doctor-specific)
    const [queueEntries, setQueueEntries] = useState([]);
    const [opds, setOpds] = useState([]);
    const [currentToken, setCurrentToken] = useState(null);
    const [nextToken, setNextToken] = useState(null);

    // Pagination & Search
    const [page, setPage] = useState(1);
    const ITEMS_PER_PAGE = 10;
    const [paginatedData, setPaginatedData] = useState([]);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');

    const { success, error: toastError, info } = useToast();
    const navigate = useNavigate();

    // Patient History Modal State (Removed in favor of HistoryDrawer)
    // const [historyModal, setHistoryModal] = useState({ isOpen: false, patient: null, history: [] });

    // Edit Appointment Modal
    const [editModal, setEditModal] = useState({
        isOpen: false,
        appointment: null,
        status: '',
        notes: ''
    });

    // Confirmation Modal
    const [confirmModal, setConfirmModal] = useState({
        isOpen: false,
        title: '',
        message: '',
        onConfirm: null,
        showReasonInput: false,
        inputPlaceholder: ''
    });

    // Audit History Drawer (for Appointments)
    const [auditHistory, setAuditHistory] = useState({
        isOpen: false,
        entityType: '',
        entityId: null,
        entityName: ''
    });

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

    const handleViewHistory = (patient) => {
        setAuditHistory({
            isOpen: true,
            entityType: 'PATIENT',
            entityId: patient.publicId || patient.id,
            entityName: patient.name
        });
    };

    const handleEditClick = (appointment) => {
        setEditModal({
            isOpen: true,
            appointment: appointment,
            status: appointment.status,
            notes: appointment.notes || ''
        });
    };

    const handleUpdateAppointment = async () => {
        try {
            await hospitalService.updateAppointment(
                editModal.appointment.id,
                editModal.status,
                editModal.notes
            );
            success('Appointment updated successfully');
            setEditModal({ isOpen: false, appointment: null, status: '', notes: '' });
            loadData();
        } catch (err) {
            toastError('Failed to update appointment');
        }
    };

    useEffect(() => {
        const timer = setTimeout(() => {
            setPage(1);
            loadData();
        }, 500);
        return () => clearTimeout(timer);
    }, [activeTab, searchTerm, viewFilter, patientViewFilter]);

    useEffect(() => {
        // Use local date instead of UTC
        const today = new Date();
        const todayStr = today.getFullYear() + '-' + 
                        String(today.getMonth() + 1).padStart(2, '0') + '-' + 
                        String(today.getDate()).padStart(2, '0');
        
        const todayCount = appointments.filter(a => a.appointmentDate === todayStr).length;
        const pendingCount = appointments.filter(a => a.status === 'SCHEDULED').length;
        
        setStats({
            today: todayCount,
            pending: pendingCount,
            total: totalElements // Use totalElements from server instead of local array length
        });
    }, [appointments, totalElements]);

    
    const loadData = async () => {
        setLoading(true);
        try {
            if (activeTab === 'appointments') {
                const localPage = page - 1;
                const data = await hospitalService.getMyAppointments(viewFilter, searchTerm, localPage, ITEMS_PER_PAGE,);
                // Handle both array and paginated response
                const appointmentsArray = Array.isArray(data) ? data : (data.content || []);
                setAppointments(appointmentsArray);
                setTotalElements(data.totalElements || 0);
                setTotalPages(data.totalPages || 1);
            } else if (activeTab === 'patients') {
                const data = await hospitalService.getPatients(
                    searchTerm, 
                    page - 1, 
                    ITEMS_PER_PAGE
                );
                const patientsArray = Array.isArray(data) ? data : (data.content || []);
                setPatients(patientsArray);
                setTotalElements(data.totalElements || 0);
                setTotalPages(data.totalPages || 1);
            }

            // Doctor-specific queue
            if (activeTab === 'queue') {
                try {
                    // For doctor dashboard use authenticated doctor's queue (backend maps user -> doctor)
                    const q = await hospitalService.getDoctorQueue();
                    setQueueEntries(q || []);
                    if (q && q.length > 0) {
                        const sorted = [...q].sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
                        setCurrentToken(sorted[0].tokenNumber ?? null);
                        setNextToken(sorted[1] ? (sorted[1].tokenNumber ?? null) : null);
                    } else {
                        setCurrentToken(null);
                        setNextToken(null);
                    }
                } catch (err) {
                    console.error('Failed to load doctor queue', err);
                    setQueueEntries([]);
                }
            }

            // OPD list for doctor (read-only)
            if (activeTab === 'opd') {
                try {
                    const opdsData = await hospitalService.getOpds(searchTerm, page - 1, ITEMS_PER_PAGE);
                    const opdsArray = Array.isArray(opdsData) ? opdsData : (opdsData.content || []);
                    setOpds(opdsArray);
                    setTotalElements(opdsData.totalElements || opdsArray.length);
                    setTotalPages(opdsData.totalPages || 1);
                } catch (err) {
                    console.error('Failed to load OPDs', err);
                    setOpds([]);
                }
            }
        } catch (err) {
            toastError('Failed to load data');
        } finally {
            setLoading(false);
        }
    };

    const handleStatusUpdate = (id, newStatus) => {
        const action = newStatus === 'COMPLETED' ? 'Complete' : 'Cancel';

        if (newStatus === 'CANCELLED') {
            openConfirmation(
                'Cancel Appointment',
                'Are you sure you want to cancel this appointment? This action cannot be undone.',
                async (reason) => {
                    try {
                        console.log(`Cancelling appointment ${id} with reason: ${reason}`);
                        await hospitalService.updateAppointmentStatus(id, newStatus);
                        success('Appointment cancelled successfully');
                        loadData();
                    } catch (err) {
                        toastError('Failed to cancel appointment');
                    }
                },
                true, // Show reason input
                "Reason for cancellation (required)"
            );
        } else {
            openConfirmation(
                `${action} Appointment`,
                `Are you sure you want to mark this appointment as ${newStatus.toLowerCase()}?`,
                async () => {
                    try {
                        await hospitalService.updateAppointmentStatus(id, newStatus);
                        success(`Appointment ${newStatus.toLowerCase()} successfully`);
                        loadData();
                    } catch (err) {
                        toastError(`Failed to ${action.toLowerCase()} appointment`);
                    }
                }
            );
        }
    };

    const handleAuditHistory = (type, id, name) => {
        setAuditHistory({
            isOpen: true,
            entityType: type,
            entityId: id,
            entityName: name
        });
    };

    const handleLogout = () => {
        authService.logout();
        navigate('/login');
    };

    const tabs = [
        { id: 'appointments', label: 'My Appointments', icon: null },
        { id: 'queue', label: 'Queue', icon: null },
        { id: 'opd', label: 'OPD', icon: null },
        { id: 'patients', label: 'Patients', icon: null },
    ];

    const pagination = {
        pageIndex: page - 1, // DataTable expects 0-indexed
        pageSize: ITEMS_PER_PAGE,
        totalItems: totalElements,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage + 1) // DataTable gives 0-indexed
    };

    // Consultation Modal
    const [consultationModal, setConsultationModal] = useState({ isOpen: false, appointment: null, patient: null, opd: null });

    const handleConsultClick = (appointment) => {
        setConsultationModal({ isOpen: true, appointment });
    };

    const handlePrintPrescription = async (appointmentId) => {
        try {
            const blob = await hospitalService.downloadPrescription(appointmentId);
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `prescription_${appointmentId}.pdf`);
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch (err) {
            // Check if error response is a blob
            if (err.response?.data instanceof Blob) {
                // Read blob as text to extract error message
                err.response.data.text().then(text => {
                    if (text.toLowerCase().includes('consultation not found')) {
                        toastError("Prescription not found");
                    } else {
                        toastError("Failed to download prescription");
                    }
                });
            } else {
                // Handle regular JSON error response
                const errorMessage = err.response?.data?.message || err.message || '';
                if (errorMessage.toLowerCase().includes('consultation not found')) {
                    toastError("Prescription not found");
                } else {
                    toastError("Failed to download prescription");
                }
            }
            console.error(err);
        }
    };

    const handlePrintOpd = async (opd) => {
        try {
            const blob = await hospitalService.downloadCasePaper(opd.id);
            const url = window.URL.createObjectURL(blob);
            window.open(url, '_blank');
        } catch (err) {
            console.error('Failed to download case paper', err);
            toastError('Failed to download case paper');
        }
    };

    const handlePrintPrescriptionOpd = async (opd) => {
        try {
            const blob = await hospitalService.downloadPrescriptionByOpd(opd.id);
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `prescription_opd_${opd.id}.pdf`);
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch (err) {
            console.error('Failed to download prescription', err);
            toastError('Failed to download prescription');
        }
    };

    const handleViewPrescriptionOpd = async (opd) => {
        try {
            const blob = await hospitalService.downloadPrescriptionByOpd(opd.id);
            const url = window.URL.createObjectURL(blob);
            window.open(url, '_blank');
        } catch (err) {
            console.error('Failed to open prescription', err);
            toastError('Failed to open prescription');
        }
    };

    // Patient consultation handlers
    const handleStartConsultation = async (patient) => {
        console.log("handleStartConsultation called for:", patient);
        try {
            // Update status to CONSULTING
            await hospitalService.startConsultation(patient.publicId);
            success("Consultation started");

            // Reload data to reflect status change
            await loadData();

            // Open consultation modal
            setConsultationModal({ isOpen: true, patient: { ...patient, status: 'CONSULTING' } });
        } catch (err) {
            console.error("Failed to start consultation", err);
            toastError("Failed to start consultation status update");
            // Still open modal to allow doctor to proceed even if status update fails?
            // Maybe safer to still open it
            setConsultationModal({ isOpen: true, patient });
        }
    };

    const handleStartOpdConsultation = async (opd) => {
        console.log('handleStartOpdConsultation called for OPD:', opd);
        try {
            // Open consultation modal with OPD context
            setConsultationModal({ isOpen: true, appointment: null, patient: opd.patient, opd });
        } catch (err) {
            console.error('Failed to open OPD consultation modal', err);
            toastError('Failed to start OPD consultation');
            setConsultationModal({ isOpen: true, appointment: null, patient: opd.patient, opd });
        }
    };

    const handleCompleteConsultation = (patient) => {
        console.log("handleCompleteConsultation called for:", patient);
        // Open consultation modal for this patient
        setConsultationModal({ isOpen: true, patient });
        console.log("setConsultationModal called with isOpen: true");
    };

    const handleViewPrescription = (patient) => {
        // Direct open View Prescription Modal
        setViewPrescriptionModal({ isOpen: true, patient });
    };

    const handlePatientStatusUpdate = async (patientId, newStatus) => {
        try {
            await hospitalService.updatePatientStatus(patientId, newStatus);
            success(`Patient status updated to ${newStatus}`);
            loadData(); // Reload to show updated status
        } catch (err) {
            toastError("Failed to update patient status");
            console.error(err);
        }
    };

    return (
        <div className="flex h-screen bg-white">
            {/* Sidebar */}
            <Sidebar
                title="HMS Portal"
                tabs={tabs}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                footerTitle="My Hospital"
                footerData={user?.hospitalName}
                variant="plain"
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
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-white p-8">

                    {/* Stats Cards - Only show on appointments tab */}
                    {activeTab === 'appointments' && (
                        <div className="grid grid-cols-1 md:grid-cols-5 gap-6 mb-8">
                            <div className="bg-white rounded-lg border border-gray-200 p-6">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <p className="text-gray-600 text-sm font-medium">Current Token</p>
                                        <h3 className="text-3xl font-bold text-gray-900 mt-1">{currentToken ?? '-'}</h3>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white rounded-lg border border-gray-200 p-6">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <p className="text-gray-600 text-sm font-medium">Next Token</p>
                                        <h3 className="text-3xl font-bold text-gray-900 mt-1">{nextToken ?? '-'}</h3>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white rounded-lg border border-gray-200 p-6">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <p className="text-gray-600 text-sm font-medium">Today's Appointments</p>
                                        <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.today}</h3>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white rounded-lg border border-gray-200 p-6">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <p className="text-gray-600 text-sm font-medium">Pending Action</p>
                                        <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.pending}</h3>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white rounded-lg border border-gray-200 p-6">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <p className="text-gray-600 text-sm font-medium">Total Appointments</p>
                                        <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.total}</h3>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Standardized Header */}
                    <PageHeader
                        title={activeTab === 'appointments' ? 'My Appointments' : 'My Patients'}
                        subtitle={`Manage your ${activeTab === 'appointments' ? 'schedule' : 'patients'} here.`}
                        onSearch={(e) => setSearchTerm(e.target.value)}
                        searchValue={searchTerm}
                        searchPlaceholder={`Search ${activeTab}...`}
                        filter={activeTab === 'appointments' ? (
                            <div className="flex items-center gap-4">
                                {/* View Filter Buttons */}
                                <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200">
                                    {['today', 'upcoming', 'history'].map(view => (
                                        <button
                                            key={view}
                                            onClick={() => setViewFilter(view)}
                                            className={`px-4 py-1.5 text-sm font-medium rounded-md transition-all ${viewFilter === view
                                                ? 'bg-white text-primary-600 shadow-sm border border-gray-100'
                                                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                                }`}
                                        >
                                            {view.charAt(0).toUpperCase() + view.slice(1)}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        ) : activeTab === 'patients' ? (
                            <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200">
                                {['today', 'history'].map(view => (
                                    <button
                                        key={view}
                                        onClick={() => setPatientViewFilter(view)}
                                        className={`px-4 py-1.5 text-sm font-medium rounded-md transition-all ${patientViewFilter === view
                                            ? 'bg-white text-primary-600 shadow-sm border border-gray-100'
                                            : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                            }`}
                                    >
                                        {view.charAt(0).toUpperCase() + view.slice(1)}
                                    </button>
                                ))}
                            </div>
                        ) : null}
                    />

                    {loading ? (
                        <div className="flex justify-center items-center h-64">
                            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500"></div>
                        </div>
                    ) : (
                        <>
                            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden mb-4">
                                {activeTab === 'appointments' && (
                                    appointments.length > 0 ? (
                                        <DoctorAppointmentsTable
                                            appointments={appointments}
                                            onStatusUpdate={handleStatusUpdate}
                                            onEdit={handleEditClick}
                                            onConsult={handleConsultClick}
                                            onPrint={handlePrintPrescription}
                                            onAuditHistory={(item) => handleAuditHistory('APPOINTMENT', item.publicId || item.id, "Appointment")}
                                            startIndex={(page - 1) * ITEMS_PER_PAGE}
                                            pagination={pagination}
                                        />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No Appointments"
                                            message="No appointments found for the selected filter."
                                        />
                                    )
                                )}

                                {activeTab === 'opd' && (
                                    opds.length > 0 ? (
                                        <div className="p-4 overflow-x-auto">
                                            <table className="w-full text-sm text-left">
                                                <thead>
                                                    <tr>
                                                        <th className="px-4 py-2">S.No.</th>
                                                        <th className="px-4 py-2">Case ID</th>
                                                        <th className="px-4 py-2">Patient</th>
                                                        <th className="px-4 py-2">Doctor</th>
                                                        <th className="px-4 py-2">Token</th>
                                                        <th className="px-4 py-2">Visit</th>
                                                        <th className="px-4 py-2">Created</th>
                                                        <th className="px-4 py-2">Actions</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {opds.map((o, idx) => (
                                                        <tr key={o.id} className="border-t">
                                                            <td className="px-4 py-3">{(page - 1) * ITEMS_PER_PAGE + idx + 1}</td>
                                                            <td className="px-4 py-3">{o.caseId}</td>
                                                            <td className="px-4 py-3">{o.patient?.name}</td>
                                                            <td className="px-4 py-3">{o.doctor?.name || '-'}</td>
                                                            <td className="px-4 py-3">{o.tokenNumber || '-'}</td>
                                                            <td className="px-4 py-3">{o.visitType}</td>
                                                            <td className="px-4 py-3">{new Date(o.createdAt).toLocaleString()}</td>
                                                            <td className="px-4 py-3 flex items-center space-x-2">
                                                                <button onClick={() => handlePrintOpd(o)} className="px-3 py-1 bg-gray-900 text-white rounded">Print</button>
                                                                {o.status === 'QUEUED' && (
                                                                    <button
                                                                        onClick={() => handleStartOpdConsultation(o)}
                                                                        disabled={o.tokenNumber && currentToken && o.tokenNumber !== currentToken}
                                                                        className={`px-3 py-1 rounded ${o.tokenNumber && currentToken && o.tokenNumber !== currentToken ? 'bg-gray-300 text-gray-600 cursor-not-allowed' : 'bg-green-600 text-white'}`}>
                                                                        Start Consultation
                                                                    </button>
                                                                )}
                                                                {(o.status === 'CONSULTED' || o.status === 'COMPLETED') && (
                                                                    <>
                                                                        <button onClick={() => handlePrintPrescriptionOpd(o)} className="px-3 py-1 bg-indigo-600 text-white rounded">Print Prescription</button>
                                                                        <button onClick={() => handleViewPrescriptionOpd(o)} className="px-3 py-1 bg-blue-600 text-white rounded">View Prescription</button>
                                                                    </>
                                                                )}
                                                            </td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No OPD Cases"
                                            message="No OPD cases for today."
                                        />
                                    )
                                )}

                                {activeTab === 'queue' && (
                                    queueEntries.length > 0 ? (
                                        <div className="p-4 overflow-x-auto">
                                            <table className="w-full text-sm text-left">
                                                <thead>
                                                    <tr>
                                                        <th className="px-4 py-2">S.No.</th>
                                                        <th className="px-4 py-2">Token</th>
                                                        <th className="px-4 py-2">Patient</th>
                                                        <th className="px-4 py-2">Doctor</th>
                                                        <th className="px-4 py-2">Created</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {queueEntries.map((q, idx) => (
                                                        <tr key={q.id} className="border-t">
                                                            <td className="px-4 py-3">{idx + 1}</td>
                                                            <td className="px-4 py-3">{q.tokenNumber}</td>
                                                            <td className="px-4 py-3">{q.opd?.patient?.name || q.opd?.patientName || '-'}</td>
                                                            <td className="px-4 py-3">{q.opd?.doctor?.name || q.opd?.doctorName || '-'}</td>
                                                            <td className="px-4 py-3">{new Date(q.createdAt).toLocaleString()}</td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="Queue Empty"
                                            message="No queued OPD cases for today."
                                        />
                                    )
                                )}

                                {activeTab === 'patients' && (
                                    patients.length > 0 ? (
                                        <DoctorPatientsTable
                                            patients={patients}
                                            onViewHistory={handleViewHistory}
                                            onStartConsultation={handleStartConsultation}
                                            onCompleteConsultation={handleCompleteConsultation}
                                            onViewPrescription={handleViewPrescription}
                                            onStatusUpdate={handlePatientStatusUpdate}
                                            startIndex={(page - 1) * ITEMS_PER_PAGE}
                                            pagination={pagination}
                                        />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No Patients"
                                            message="No patients found."
                                        />
                                    )
                                )}
                            </div>
                        </>
                    )}
                </main>
            </div>



            {/* Edit Appointment Modal */}
            {editModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg border border-gray-200 w-full max-w-lg p-6 m-4">
                        <h3 className="text-xl font-bold text-gray-900 mb-4">Edit Appointment Details</h3>

                        <div className="mb-6">
                            <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                            <textarea
                                value={editModal.notes}
                                onChange={(e) => setEditModal({ ...editModal, notes: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-primary-500 h-32"
                                placeholder="Enter clinical notes, diagnosis, or prescriptions..."
                            />
                        </div>

                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setEditModal({ isOpen: false, appointment: null, status: '', notes: '' })}
                                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleUpdateAppointment}
                                className="px-4 py-2 bg-gray-900 text-white rounded-lg hover:bg-gray-800 transition"
                            >
                                Save Changes
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* History Modal Removed - Uses HistoryDrawer */}

            <>
                <ConfirmationModal
                    isOpen={confirmModal.isOpen}
                    title={confirmModal.title}
                    message={confirmModal.message}
                    onConfirm={confirmModal.onConfirm}
                    onCancel={() => setConfirmModal(prev => ({ ...prev, isOpen: false }))}
                    showReasonInput={confirmModal.showReasonInput}
                    inputPlaceholder={confirmModal.inputPlaceholder}
                />

                <ConsultationModal
                    isOpen={consultationModal.isOpen}
                    appointment={consultationModal.appointment}
                    patient={consultationModal.patient}
                    opd={consultationModal.opd}
                    onClose={() => setConsultationModal({ isOpen: false, appointment: null, patient: null })}
                    onSuccess={() => {
                        setConsultationModal({ isOpen: false, appointment: null, patient: null });
                        success("Consultation completed successfully!");
                        loadData();
                    }}
                />

                <PrescriptionViewModal
                    isOpen={viewPrescriptionModal.isOpen}
                    patient={viewPrescriptionModal.patient}
                    onClose={() => setViewPrescriptionModal({ isOpen: false, patient: null })}
                />

                <HistoryDrawer
                    isOpen={auditHistory.isOpen}
                    onClose={() => setAuditHistory(prev => ({ ...prev, isOpen: false }))}
                    entityType={auditHistory.entityType}
                    entityId={auditHistory.entityId}
                    entityName={auditHistory.entityName}
                />
            </>
        </div>
    );
};

export default DoctorDashboard;

// Doctor Appointments Table
const DoctorAppointmentsTable = ({ appointments, onStatusUpdate, onEdit, onConsult, onPrint, onAuditHistory, startIndex = 0, pagination }) => {
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.NO.',
            cell: info => startIndex + info.row.index + 1,
        }),
        columnHelper.accessor(row => row.customId || row.id, {
            id: 'id',
            header: 'ID',
            cell: info => <span title="Serial Number">{info.getValue()}</span>,
        }),
        columnHelper.accessor(row => row.patientName || row.patientId, {
            id: 'patient',
            header: 'PATIENT',
        }),
        columnHelper.accessor('appointmentDate', {
            header: 'DATE',
        }),
        columnHelper.accessor('appointmentTime', {
            header: 'TIME',
            cell: info => info.getValue() ? <span className="text-sm font-medium bg-gray-100 px-2 py-1 rounded">{info.getValue().substring(0, 5)}</span> : '-',
        }),
        columnHelper.accessor('status', {
            header: 'STATUS',
            cell: info => {
                const status = info.getValue();
                const isFinal = ['CANCELLED', 'COMPLETED'].includes(status);
                return (
                    <StatusBadge
                        status={status}
                        options={isFinal ? [] : ['SCHEDULED', 'COMPLETED', 'CANCELLED']}
                        onUpdate={isFinal ? null : (newStatus) => onStatusUpdate(info.row.original.id, newStatus)}
                        type="dropdown"
                    />
                );
            },
        }),
        columnHelper.accessor('notes', {
            header: 'NOTES',
            cell: info => <span className="text-gray-600 max-w-xs truncate block">{info.getValue()}</span>
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">ACTIONS</div>,
            cell: info => (
                <div className="text-right">
                    <ActionMenu actions={[
                        {
                            label: 'View History',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" /></svg>,
                            onClick: () => onAuditHistory(info.row.original)
                        },
                        // Only show status actions if not final
                        ...(info.row.original.status === 'SCHEDULED' ? [{
                            label: 'Start Consultation',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 2a4 4 0 00-4 4v1H5a1 1 0 00-.994.89l-1 9A1 1 0 004 18h12a1 1 0 00.994-1.11l-1-9A1 1 0 0015 7h-1V6a4 4 0 00-4-4zM8 6a2 2 0 114 0v1H8V6zm.707 7.293a1 1 0 00-1.414 1.414L9.586 17a1 1 0 001.414 0l2.293-2.293a1 1 0 00-1.414-1.414L10 15.172l-1.293-1.879z" clipRule="evenodd" /></svg>,
                            onClick: () => onConsult(info.row.original)
                        },{
                            label: 'Cancel Appointment',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>,
                            onClick: () => onStatusUpdate(info.row.original.id, 'CANCELLED'),
                            variant: 'danger'
                        },{
                            label: 'Edit Details',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" /></svg>,
                            onClick: () => onEdit(info.row.original)
                        }] : []),
                        ...(info.row.original.status === 'COMPLETED' ? [{
                            label: 'Print Prescription',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M5 4v3H4a2 2 0 00-2 2v3a2 2 0 002 2h1v2a2 2 0 002 2h6a2 2 0 002-2v-2h1a2 2 0 002-2V9a2 2 0 00-2-2h-1V4a2 2 0 00-2-2H7a2 2 0 00-2 2zm8 0H7v3h6V4zm0 8H7v4h6v-4z" clipRule="evenodd" /></svg>,
                            onClick: () => onPrint(info.row.original.id)
                        }] : []),
                        
                    ]} />
                </div>
            ),
        }),
    ];

    return <DataTable data={appointments} columns={columns} pagination={pagination} />;
};

// Doctor Patients Table (Moved to end to keep context clear)
const DoctorPatientsTable = ({ patients, onViewHistory, onStartConsultation, onCompleteConsultation, onViewPrescription, onStatusUpdate, startIndex = 0, pagination }) => {
    // ... existing table code ...
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.NO.',
            cell: info => startIndex + info.row.index + 1,
        }),
        columnHelper.accessor(row => row.customId || row.id, {
            id: 'id',
            header: 'ID',
            cell: info => <span title="Serial Number">{info.getValue()}</span>,
        }),
        columnHelper.accessor('name', {
            header: 'NAME',
            cell: info => <span className="font-medium text-gray-900">{info.getValue()}</span>,
        }),
        columnHelper.accessor('age', {
            header: 'AGE/GENDER',
            cell: info => <span>{info.row.original.age} / {info.row.original.gender}</span>
        }),
        columnHelper.accessor('phone', {
            header: 'PHONE',
        }),
        columnHelper.accessor('address', {
            header: 'ADDRESS',
            cell: info => <span className="truncate max-w-xs block">{info.getValue()}</span>
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">ACTIONS</div>,
            cell: info => {
                const patient = info.row.original;
                const status = patient.status || 'REGISTERED';

                // Secondary actions for three-dot menu
                const secondaryActions = [
                    {
                        label: 'View History',
                        icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" /></svg>,
                        onClick: () => onViewHistory(patient)
                    }
                ];

                return (
                    <div className="flex items-center justify-end gap-2">
                        <ActionMenu actions={secondaryActions} />
                    </div>
                );
            },
        }),
    ];

    return <DataTable data={patients} columns={columns} pagination={pagination} />;
};

