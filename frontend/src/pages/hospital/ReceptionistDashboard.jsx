import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import authService from '../../services/authService';
import hospitalService from '../../services/hospitalService';
import { useToast } from '../../context/ToastContext';
import EmptyState from '../../components/EmptyState';
import ConfirmationModal from '../../components/ConfirmationModal';
import AppointmentModal from '../../components/AppointmentModal';
import PatientModal from '../../components/PatientModal';
import PatientDetailsModal from '../../components/PatientDetailsModal';
import ActionMenu from '../../components/ActionMenu';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import HistoryDrawer from '../../components/HistoryDrawer';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import PageHeader from '../../components/PageHeader';
import BillingTable from './BillingTable';
import { createColumnHelper } from '@tanstack/react-table';
import PrescriptionModal from '../../components/PrescriptionModal';
import PrescriptionViewModal from '../../components/PrescriptionViewModal';
import IpdAdmitModal from '../../components/IpdAdmitModal';

const ReceptionistDashboard = () => {
    const user = authService.getCurrentUser();
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'overview'; // Changed default to 'overview'
    const viewFilter = searchParams.get('appointmentFilter') || 'today';

    // Helper to switch tabs
    const setActiveTab = (tab) => {
        const newParams = { tab };
        if (tab === 'appointments' && viewFilter) newParams.appointmentFilter = viewFilter;
        setSearchParams(newParams);
    };

    // Helper to set appointment filter
    const setViewFilter = (filter) => {
        setSearchParams({ tab: activeTab, appointmentFilter: filter });
    };

    const [appointments, setAppointments] = useState([]);
    const [patients, setPatients] = useState([]);
    const [doctors, setDoctors] = useState([]);
    const [opds, setOpds] = useState([]);
    const [queueEntries, setQueueEntries] = useState([]);
    const [currentToken, setCurrentToken] = useState(null);
    const [nextToken, setNextToken] = useState(null);
    const [selectedDoctorForQueue, setSelectedDoctorForQueue] = useState('');
    const [billing, setBilling] = useState([]);
    const [billingStatus, setBillingStatus] = useState('PENDING');
    const [loading, setLoading] = useState(false);
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [isOpdModalOpen, setIsOpdModalOpen] = useState(false);
    const [isIpdAdmitOpen, setIsIpdAdmitOpen] = useState(false);
    const [ipdOpdForAdmit, setIpdOpdForAdmit] = useState(null);
    const [opdForm, setOpdForm] = useState({
        patientId: null,
        receptionistId: user?.id || null,
        doctorId: null,
        bp: '',
        temperature: '',
        pulse: '',
        weight: '',
        spo2: '',
        problem: '',
        visitType: 'NEW'
    });
    const [createdOpd, setCreatedOpd] = useState(null);
    const [selectedPatient, setSelectedPatient] = useState(null);

    // Prescription Modal State
    const [prescriptionModal, setPrescriptionModal] = useState({
        isOpen: false,
        data: null,
        appointmentId: null
    });

    // View Prescription Modal State
    const [viewPrescriptionModal, setViewPrescriptionModal] = useState({
        isOpen: false,
        patient: null
    });

    // Payment Modal State
    const [paymentModal, setPaymentModal] = useState({
        isOpen: false,
        billId: null,
        amount: null,
        patientName: ''
    });

    // Payment Success Modal State
    const [paymentSuccessModal, setPaymentSuccessModal] = useState({
        isOpen: false,
        billId: null,
        patientName: '',
        amount: 0
    });

    // Pagination
    const [page, setPage] = useState(0); // 0-indexed for Spring Boot
    const [pageSize, setPageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');

    const [confirmModal, setConfirmModal] = useState({
        isOpen: false,
        title: '',
        message: '',
        onConfirm: null,
        showReasonInput: false,
        inputPlaceholder: ''
    });

    const [historyDrawer, setHistoryDrawer] = useState({
        isOpen: false,
        entityType: '',
        entityId: null,
        entityName: ''
    });

    // Sidebar collapse state
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

    // Patient Details Modal
    const [patientDetailsModal, setPatientDetailsModal] = useState({ isOpen: false, patient: null });

    const { success, error: toastError, info } = useToast();
    const navigate = useNavigate();

    // Stats
    const [stats, setStats] = useState({ today: 0, pending: 0, total: 0 });

    useEffect(() => {
        loadData();

        // Polling for real-time updates (every 30 seconds)
        const intervalId = setInterval(() => {
            loadData(false); // Silent refresh
        }, 30000);

        return () => clearInterval(intervalId);
    }, [activeTab, page, searchTerm, viewFilter, pageSize, selectedDoctorForQueue, billingStatus]); // Add pageSize & doctor filter to dependencies

    const loadData = async (showSpinner = true) => {
        if (showSpinner) setLoading(true);
        try {
            // Stats
            const statsData = await hospitalService.getAppointmentStats();
            setStats(statsData);

            if (activeTab === 'appointments' || activeTab === 'opd' || activeTab === 'queue' || activeTab === 'ipd') {
                // Fetch appointments (Server-side) + Doctors + Patients for lookup
                const promises = [
                    activeTab === 'appointments' ? hospitalService.getAppointments(searchTerm, page, pageSize, viewFilter) : Promise.resolve({ content: [] }),
                    hospitalService.getDoctors('', 0, 100), // Fetch doctors for lookup
                    hospitalService.getPatients('', 0, 1000) // Fetch ALL patients (up to 1000) for lookup dropdown
                ];
                const [apptData, docData, patData] = await Promise.all(promises);

                if (activeTab === 'appointments') {
                    if (apptData.content) {
                        setAppointments(apptData.content);
                        setTotalPages(apptData.totalPages);
                        setTotalElements(apptData.totalElements);
                    } else {
                        setAppointments(apptData);
                        setTotalPages(1);
                        setTotalElements(apptData.length);
                    }
                } else if (activeTab === 'opd') {
                    // OPD tab: fetch opds separately
                    const opdsData = await hospitalService.getOpds(searchTerm, page, pageSize);
                    if (opdsData.content) {
                        setOpds(opdsData.content);
                        setTotalPages(opdsData.totalPages);
                        setTotalElements(opdsData.totalElements);
                    } else {
                        setOpds(opdsData);
                        setTotalPages(1);
                        setTotalElements(opdsData.length);
                    }
                } else if (activeTab === 'ipd') {
                    // IPD tab: fetch role-based admitted IPD admissions
                    const ipdList = await hospitalService.getAdmittedIpdAdmissions();
                    let arr = ipdList || [];
                    if (searchTerm && searchTerm.trim()) {
                        const q = searchTerm.trim().toLowerCase();
                        arr = arr.filter(item => {
                            const row = item.ipd || item;
                            const ipdNumber = (row.ipdNumber || row.ipd?.ipdNumber || '').toString().toLowerCase();
                            const patient = (item.patient?.name || row.patient?.name || item.patientName || '').toString().toLowerCase();
                            const doctor = (item.doctor?.name || row.doctor?.name || item.doctorName || '').toString().toLowerCase();
                            const ward = (item.ward?.wardName || row.wardName || row.ward || '').toString().toLowerCase();
                            const bed = (item.bed?.bedCode || row.bedNumber || row.bed?.bedCode || '').toString().toLowerCase();
                            const status = (row.status || '').toString().toLowerCase();
                            return ipdNumber.includes(q) || patient.includes(q) || doctor.includes(q) || ward.includes(q) || bed.includes(q) || status.includes(q);
                        });
                    }
                    setOpds(arr);
                    setTotalPages(1);
                    setTotalElements((arr && arr.length) || 0);
                } else if (activeTab === 'queue') {
                    // Fetch hospital queue or doctor's queue based on filter and compute current/next tokens
                    let q = [];
                    if (selectedDoctorForQueue) {
                        q = await hospitalService.getDoctorQueue(selectedDoctorForQueue);
                    } else {
                        q = await hospitalService.getHospitalQueue();
                    }
                    setQueueEntries(q || []);
                    if (q && q.length > 0) {
                        const sorted = [...q].sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
                        setCurrentToken(sorted[0].tokenNumber ?? null);
                        setNextToken(sorted[1] ? (sorted[1].tokenNumber ?? null) : null);
                    } else {
                        setCurrentToken(null);
                        setNextToken(null);
                    }
                }

                // Set Doctors for lookup/dropdown
                if (docData?.content) setDoctors(docData.content);
                else setDoctors(docData || []);

                // Set Patients for lookup/dropdown in specific modal
                if (patData?.content) setPatients(patData.content);
                else setPatients(patData || []);

            } else if (activeTab === 'patients') {
                const data = await hospitalService.getPatients(searchTerm, page, pageSize);
                if (data.content) {
                    setPatients(data.content);
                    setTotalPages(data.totalPages);
                    setTotalElements(data.totalElements);
                } else {
                    setPatients(data);
                    setTotalPages(1);
                    setTotalElements(data.length);
                }
            } else if (activeTab === 'billing') {
                const data = await hospitalService.getBills(searchTerm, page, pageSize, billingStatus);
                if (data.content) {
                    setBilling(data.content);
                    setTotalPages(data.totalPages);
                    setTotalElements(data.totalElements);
                } else {
                    setBilling(data);
                    setTotalPages(1);
                    setTotalElements(data.length);
                }
            }
        } catch (err) {
            console.error(`[ReceptionistDashboard] Failed to load ${activeTab}:`, err);
            // Only show toast error if it was a user-initiated action (spinner shown)
            if (showSpinner) toastError('Failed to load data');
        } finally {
            if (showSpinner) setLoading(false);
        }
    };

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

    const handleStatusUpdate = async (id, newStatus) => {
        if (!id) {
            toastError("Cannot update status: Invalid Appointment ID.");
            return;
        }

        if (newStatus === 'CANCELLED') {
            openConfirmation(
                'Cancel Appointment',
                'Are you sure you want to cancel this appointment? This action cannot be undone.',
                async (reason) => {
                    try {
                        // Assuming the API might support reason in the future, or we just log it for now
                        console.log(`Cancelling appointment ${id} with reason: ${reason}`);
                        await hospitalService.updateAppointmentStatus(id, newStatus);
                        success('Appointment cancelled successfully');
                        loadData();
                    } catch (err) {
                        console.error('Failed to cancel appointment:', err);
                        toastError('Failed to cancel appointment');
                    }
                },
                true, // Show reason input
                "Reason for cancellation (required)"
            );
        } else {
            // Check if irreversible transition if needed, but backend handles most. 
            // Admin dashboard allows non-cancelled updates directly? 
            // Admin dash: "Normal update" -> handleAppointmentStatusUpdate
            try {
                await hospitalService.updateAppointmentStatus(id, newStatus);
                success(`Appointment status updated to ${newStatus}`);
                loadData();
            } catch (err) {
                console.error('Failed to update status:', err);
                toastError('Failed to update status');
            }
        }
    };

    const handleBillStatus = async (id, status) => {
        // Open payment modal when marking PAID from receptionist
        if (status === 'PAID') {
            setPaymentModal({ isOpen: true, billId: id });
            return;
        }
        try {
            await hospitalService.updateBillStatus(id, status);
            success('Bill status updated');
            loadData();
        } catch (err) {
            toastError('Failed to update bill status');
        }
    };

    const confirmPayment = async (id, method, reference) => {
        try {
            await hospitalService.updateBillStatus(id, 'PAID', method, reference);
            success('Bill marked as PAID');
            loadData();
        } catch (err) {
            console.error(err);
            toastError('Failed to mark bill as paid');
        }
    };

    const handleDownloadReceipt = async (id) => {
        try {
            const blob = await hospitalService.downloadReceipt(id);
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `receipt_${id}.pdf`);
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch (err) {
            toastError('Failed to download receipt');
        }
    };

    const handleViewPrescription = async (appointmentId) => {
        try {
            setLoading(true);
            const data = await hospitalService.getConsultationDetails(appointmentId);
            setPrescriptionModal({
                isOpen: true,
                data: data,
                appointmentId: appointmentId
            });
        } catch (err) {
            console.error(err);
            toastError("Prescription not available yet.");
        } finally {
            setLoading(false);
        }
    };

    const handleViewPatientPrescription = (patient) => {
        setViewPrescriptionModal({ isOpen: true, patient });
    };

    const handlePrintPrescription = async () => {
        if (!prescriptionModal.appointmentId) return;
        try {
            const blob = await hospitalService.downloadPrescription(prescriptionModal.appointmentId);
            const url = window.URL.createObjectURL(blob);
            window.open(url, '_blank'); // Open in new tab for printing
        } catch (err) {
            toastError("Failed to print prescription.");
        }
    };

    const handleHistory = (type, id, name) => {
        setHistoryDrawer({
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

    const handleOpenPayment = (patient) => {
        if (!patient.latestBill) {
            toastError("No bill found for this patient.");
            return;
        }
        setPaymentModal({
            isOpen: true,
            billId: patient.latestBill.id,
            amount: patient.latestBill.amount,
            patientName: patient.name
        });
    };

    const handleProcessPayment = async (method) => {
        try {
            const pm = method === 'Online' ? 'UPI' : 'CASH';
            let reference = null;
            if (pm === 'UPI') {
                reference = window.prompt('Enter UTR / transaction reference (required for UPI):');
                if (!reference || !reference.trim()) {
                    toastError('UTR / reference is required for UPI payments');
                    return;
                }
            }
            await hospitalService.updateBillStatus(paymentModal.billId, 'PAID', pm, reference);
            // Close Payment Modal
            setPaymentModal({ isOpen: false, billId: null, amount: null, patientName: '' });

            // Open Success Modal immediately
            setPaymentSuccessModal({
                isOpen: true,
                billId: paymentModal.billId,
                patientName: paymentModal.patientName,
                amount: paymentModal.amount
            });

            loadData();
        } catch (err) {
            toastError("Failed to process payment");
        }
    };


    const handleViewPatient = (patient) => {
        setPatientDetailsModal({ isOpen: true, patient });
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

    const tabs = [
        { id: 'overview', label: 'Overview', icon: null },
        { id: 'patients', label: 'Patients', icon: null },
        { id: 'appointments', label: 'Appointments', icon: null },
        { id: 'opd', label: 'OPD', icon: null },
        { id: 'ipd', label: 'IPD', icon: null },
        { id: 'queue', label: 'Queue', icon: null },
        { id: 'billing', label: 'Billing', icon: null },
    ];

    const pagination = {
        pageIndex: page,
        pageSize: pageSize,
        totalItems: totalElements,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage)
    };

    return (
        <div className="flex h-screen bg-white">
            {/* Sidebar */}
            <Sidebar
                title="HMS Portal"
                tabs={tabs}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                footerTitle="Hospital"
                footerData={user?.hospitalName}
                variant="plain"
                isCollapsed={sidebarCollapsed}
                showOnMobile={true}
            />

            {/* Main Content */}
            <div className="flex-1 flex flex-col overflow-hidden">
                <Navbar
                    title={`${tabs.find(t => t.id === activeTab)?.label} Dashboard`}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => console.log('Profile clicked')}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-white p-8">
                    {/* Overview Tab - Stats Only */}
                    {activeTab === 'overview' && (
                        <div className="space-y-6">
                            <h2 className="text-2xl font-bold text-gray-900">Overview</h2>
                            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
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
                                            <p className="text-gray-600 text-sm font-medium">Pending</p>
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
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div>
                                        <p className="text-gray-600 text-sm font-medium">Current / Next Token</p>
                                        <div className="mt-2 flex items-baseline gap-3">
                                            <h3 className="text-2xl font-bold text-gray-900">{currentToken ?? '-'}</h3>
                                            <span className="text-sm text-gray-500">/ {nextToken ?? '-'}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Standardized Header */}
                    {activeTab !== 'overview' && (
                    <PageHeader
                        title={tabs.find(t => t.id === activeTab)?.label}
                        subtitle={`Manage ${activeTab} records`}
                        onSearch={activeTab === 'queue' ? null : (e) => setSearchTerm(e.target.value)}
                        searchValue={searchTerm}
                        searchPlaceholder={activeTab === 'queue' ? '' : `Search ${activeTab}...`}
                        onAdd={activeTab === 'queue' || activeTab === 'billing' ? null : () => {
                            if (activeTab === 'opd') setIsOpdModalOpen(true);
                            else setIsAddModalOpen(true);
                        }}
                        addLabel={activeTab === 'opd' ? 'New OPD / Case' : `Add ${activeTab.slice(0, -1)}`}
                        filter={activeTab === 'appointments' ? (
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
                        ) : activeTab === 'billing' ? (
                            <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200">
                                {['PENDING', 'PAID'].map(status => (
                                    <button
                                        key={status}
                                        onClick={() => setBillingStatus(status)}
                                        className={`px-4 py-1.5 text-sm font-medium rounded-md transition-all ${billingStatus === status
                                            ? 'bg-white text-primary-600 shadow-sm border border-gray-100'
                                            : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                            }`}
                                    >
                                        {status.charAt(0) + status.slice(1).toLowerCase()}
                                    </button>
                                ))}
                            </div>
                        ) : activeTab === 'queue' ? (
                            <div className="flex items-center gap-3">
                                <select value={selectedDoctorForQueue} onChange={(e) => { setSelectedDoctorForQueue(e.target.value); setPage(0); }} className="input-field">
                                    <option value="">All Doctors</option>
                                    {doctors.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                </select>
                            </div>
                        ) : null}
                      
                    />
                    )}

                    {loading ? (
                        <div className="flex justify-center items-center h-64">
                            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500"></div>
                        </div>
                            ) : (
                                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden mb-4">
                            {activeTab === 'appointments' && (
                                appointments.length > 0 ? (
                                    <AppointmentsTable
                                        appointments={appointments}
                                        doctors={doctors}
                                        onStatusUpdate={handleStatusUpdate}
                                        onHistory={(item) => handleHistory('APPOINTMENT', item.publicId || item.id, "Appointment")}
                                        onViewPrescription={handleViewPrescription}
                                        startIndex={page * pageSize}
                                        pagination={pagination}
                                    />
                                ) : (
                                    <EmptyState
                                        icon={null}
                                        title="No Appointments Found"
                                        message="Schedule appointments for your patients."
                                        actionLabel="Schedule Appointment"
                                        onAction={() => setIsAddModalOpen(true)}
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
                                                        <td className="px-4 py-3">{page * pageSize + idx + 1}</td>
                                                        <td className="px-4 py-3">{o.caseId}</td>
                                                        <td className="px-4 py-3">{o.patient?.name}</td>
                                                        <td className="px-4 py-3">{o.doctor?.name || '-'}</td>
                                                        <td className="px-4 py-3">{o.tokenNumber || '-'}</td>
                                                        <td className="px-4 py-3">{o.visitType}</td>
                                                        <td className="px-4 py-3">{new Date(o.createdAt).toLocaleString()}</td>
                                                        <td className="px-4 py-3">
                                                            <div className="flex items-center gap-2">
                                                                <button onClick={() => handlePrintOpd(o)} className="px-3 py-1 bg-gray-900 text-white rounded">Print</button>
                                                                                                {user?.role === 'RECEPTIONIST' && (o.status === 'CONSULTED' || o.status === 'COMPLETED') && !(o.status === 'IN_IPD' || o.ipd || o.patient?.currentIpdId || o.patient?.isAdmitted) && (
                                                                                                    <button onClick={() => { setIpdOpdForAdmit(o); setIsIpdAdmitOpen(true); }} className="px-3 py-1 bg-green-600 text-white rounded">Admit to IPD</button>
                                                                                                )}
                                                            </div>
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
                                        message="Create OPD cases when patients arrive."
                                        actionLabel="New OPD / Case"
                                        onAction={() => setIsOpdModalOpen(true)}
                                    />
                                )
                            )}
                            {activeTab === 'ipd' && (
                                opds.length > 0 ? (
                                    <div className="p-4 overflow-x-auto">
                                        <table className="w-full text-sm text-left">
                                            <thead>
                                                <tr>
                                                    <th className="px-4 py-2">S.No.</th>
                                                    <th className="px-4 py-2">IPD No.</th>
                                                    <th className="px-4 py-2">Patient</th>
                                                    <th className="px-4 py-2">Doctor</th>
                                                    <th className="px-4 py-2">Ward</th>
                                                    <th className="px-4 py-2">Bed</th>
                                                    <th className="px-4 py-2">Admitted</th>
                                                        <th className="px-4 py-2">Status</th>
                                                        <th className="px-4 py-2">Actions</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {opds.map((o, idx) => {
                                                    const row = o.ipd || o;
                                                    const ipdNumber = row.ipdNumber || row.ipd?.ipdNumber || row.ipdNumber;
                                                    const patientName = row.patientName || row.patient?.name || '-';
                                                    const doctorName = row.doctorName || row.doctor?.name || '-';
                                                    const wardName = row.wardName || row.ward?.name || '-';
                                                    const bedNumber = row.bedNumber || row.bed?.bedNumber || row.bed?.bedCode || row.bed?.name || '-';
                                                    const admittedAt = row.admissionDateTime || row.admissionDatetime || row.ipd?.admissionDatetime;
                                                    const status = row.status || row.ipd?.status || 'ADMITTED';
                                                    const ipdId = row.ipdId || row.id || row.ipd?.id || row.ipd?.ipdId || ipdNumber;
                                                    return (
                                                        <tr key={idx} className="border-t">
                                                            <td className="px-4 py-3">{page * pageSize + idx + 1}</td>
                                                            <td className="px-4 py-3">{ipdNumber || row.id}</td>
                                                            <td className="px-4 py-3">{patientName}</td>
                                                            <td className="px-4 py-3">{doctorName}</td>
                                                            <td className="px-4 py-3">{wardName}</td>
                                                            <td className="px-4 py-3">{bedNumber}</td>
                                                            <td className="px-4 py-3">{admittedAt ? new Date(admittedAt).toLocaleString() : '-'}</td>
                                                            <td className="px-4 py-3">{status}</td>
                                                            <td className="px-4 py-3">
                                                                <button
                                                                    className={`px-3 py-1 rounded ${ipdId ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-500 cursor-not-allowed'}`}
                                                                    onClick={() => { if (ipdId) window.location.href = `/ipd/${ipdId}` }}
                                                                    disabled={!ipdId}
                                                                    title={ipdId ? 'View IPD details' : 'IPD id not available'}
                                                                >
                                                                    View
                                                                </button>
                                                            </td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                ) : (
                                    <EmptyState
                                        icon={null}
                                        title="No IPD Admissions"
                                        message="No IPD admissions found."
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
                                    <PatientsTable
                                        patients={patients}
                                        onStatusUpdate={handlePatientStatusUpdate}
                                        onViewPrescription={handleViewPatientPrescription}
                                        onViewDetails={handleViewPatient}
                                        onOpenPayment={handleOpenPayment}
                                        onDownloadReceipt={handleDownloadReceipt}
                                        startIndex={page * pageSize}
                                        pagination={pagination}
                                    />
                                ) : (
                                    <EmptyState
                                        icon={null}
                                        title="No Patients Found"
                                        message="There are no patients registered in the system yet."
                                        actionLabel="Add Patient"
                                        onAction={() => setIsAddModalOpen(true)}
                                    />
                                )
                            )}
                            {activeTab === 'billing' && billing.length === 0 && (
                                <EmptyState
                                    icon={null}
                                    title="No Billing Records"
                                    message="No bills found."
                                />
                            )}
                            {activeTab === 'billing' && billing.length > 0 && (
                                <BillingTable
                                    billing={billing}
                                    startIndex={page * pageSize}
                                    pagination={pagination}
                                    onUpdateStatus={handleBillStatus}
                                    onDownload={handleDownloadReceipt}
                                />
                            )}
                        </div>
                    )}

                </main>
            </div>

            {/* Appointment Modal - Using Shared Component */}
            {activeTab === 'appointments' && (
                <AppointmentModal
                    isOpen={isAddModalOpen}
                    onClose={() => setIsAddModalOpen(false)}
                    onSuccess={loadData}
                    doctors={doctors}
                    patients={patients}
                />
            )}

            {/* Patient Modal - Using Shared Component */}
            {activeTab === 'patients' && isAddModalOpen && (
                <PatientModal
                    isOpen={isAddModalOpen}
                    onClose={() => {
                        setIsAddModalOpen(false);
                        setSelectedPatient(null);
                    }}
                    initialData={selectedPatient}
                    onSuccess={() => {
                        setIsAddModalOpen(false);
                        setSelectedPatient(null);
                        loadData(); // Reload patients list
                    }}
                />
            )}

            {/* IPD Admit Modal */}
            {isIpdAdmitOpen && (
                <IpdAdmitModal
                    isOpen={isIpdAdmitOpen}
                    onClose={() => { setIsIpdAdmitOpen(false); setIpdOpdForAdmit(null); }}
                    opd={ipdOpdForAdmit}
                    onSuccess={() => { loadData(); }}
                />
            )}

            {/* OPD Modal / Form for Receptionist */}
            {activeTab === 'opd' && isOpdModalOpen && (
                <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-organic w-full max-w-3xl animate-scale-in overflow-hidden max-h-[90vh]">
                        <div className="bg-white px-8 py-6 border-b border-gray-200">
                            <div className="flex justify-between items-center">
                                <div>
                                    <h3 className="text-2xl font-bold text-neutral-800">New OPD / Case</h3>
                                    <p className="text-sm text-neutral-600 mt-1">Capture vitals and assign doctor</p>
                                </div>
                                <button onClick={() => setIsOpdModalOpen(false)} className="w-10 h-10 rounded-xl bg-white/80 hover:bg-white flex items-center justify-center text-neutral-400 hover:text-neutral-600">
                                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                </button>
                            </div>
                        </div>
                        <form onSubmit={async (e) => {
                            e.preventDefault();
                            try {
                                const payload = {
                                    patientId: opdForm.patientId,
                                    doctorId: opdForm.doctorId,
                                    bp: opdForm.bp,
                                    temperature: opdForm.temperature ? parseFloat(opdForm.temperature) : null,
                                    pulse: opdForm.pulse ? parseInt(opdForm.pulse) : null,
                                    weight: opdForm.weight ? parseFloat(opdForm.weight) : null,
                                    spo2: opdForm.spo2 ? parseInt(opdForm.spo2) : null,
                                    problem: opdForm.problem,
                                    visitType: opdForm.visitType
                                };
                                const res = await hospitalService.createOpd(payload);
                                setCreatedOpd(res);
                                setIsOpdModalOpen(false);
                                success('OPD created — token: ' + (res.tokenNumber || '-'));
                                loadData(); // Refresh the data after OPD creation
                            } catch (err) {
                                console.error('Failed to create OPD', err);
                                toastError('Failed to create OPD');
                            }
                        }} className="p-6 space-y-4 max-h-[76vh] overflow-auto">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Patient <span className="text-red-600">*</span></label>
                                    <select className="input-field" value={opdForm.patientId || ''} onChange={(e) => setOpdForm(prev => ({ ...prev, patientId: e.target.value }))} required>
                                        <option value="">Select patient</option>
                                        {patients.map(p => <option key={p.id} value={p.id}>{p.name} {p.phone ? `(${p.phone})` : ''}</option>)}
                                    </select>
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Doctor</label>
                                    <select className="input-field" value={opdForm.doctorId || ''} onChange={(e) => setOpdForm(prev => ({ ...prev, doctorId: e.target.value }))}>
                                        <option value="">Unassigned</option>
                                        {doctors.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                    </select>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">BP</label>
                                    <input className="input-field" value={opdForm.bp} onChange={(e) => setOpdForm(prev => ({ ...prev, bp: e.target.value }))} placeholder="120/80" />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Temperature (°C)</label>
                                    <input type="number" step="0.1" className="input-field" value={opdForm.temperature} onChange={(e) => setOpdForm(prev => ({ ...prev, temperature: e.target.value }))} />
                                </div>
                            </div>
                            <div className="grid grid-cols-3 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Pulse</label>
                                    <input type="number" className="input-field" value={opdForm.pulse} onChange={(e) => setOpdForm(prev => ({ ...prev, pulse: e.target.value }))} />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Weight (kg)</label>
                                    <input type="number" step="0.1" className="input-field" value={opdForm.weight} onChange={(e) => setOpdForm(prev => ({ ...prev, weight: e.target.value }))} />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">SpO2 (%)</label>
                                    <input type="number" className="input-field" value={opdForm.spo2} onChange={(e) => setOpdForm(prev => ({ ...prev, spo2: e.target.value }))} />
                                </div>
                            </div>

                            <div>
                                <label className="block text-sm font-semibold text-neutral-700 mb-2">Problem / Reason</label>
                                <textarea rows={3} className="input-field resize-none" value={opdForm.problem} onChange={(e) => setOpdForm(prev => ({ ...prev, problem: e.target.value }))} />
                            </div>

                            <div className="flex items-center gap-4">
                                <label className="text-sm font-medium">Visit Type:</label>
                                <label className="inline-flex items-center gap-2"><input type="radio" name="visitType" value="NEW" checked={opdForm.visitType === 'NEW'} onChange={() => setOpdForm(prev => ({ ...prev, visitType: 'NEW' }))} /> New</label>
                                <label className="inline-flex items-center gap-2"><input type="radio" name="visitType" value="FOLLOWUP" checked={opdForm.visitType === 'FOLLOWUP'} onChange={() => setOpdForm(prev => ({ ...prev, visitType: 'FOLLOWUP' }))} /> Follow-up</label>
                            </div>

                            <div className="flex gap-4 pt-4">
                                <button type="button" onClick={() => setIsOpdModalOpen(false)} className="flex-1 py-2 rounded-lg border">Cancel</button>
                                <button type="submit" className="flex-1 py-2 rounded-lg bg-gray-900 text-white">Create OPD</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Confirmation Modal */}
            <ConfirmationModal
                isOpen={confirmModal.isOpen}
                onCancel={() => setConfirmModal({ ...confirmModal, isOpen: false })}
                title={confirmModal.title}
                message={confirmModal.message}
                onConfirm={confirmModal.onConfirm}
                showReasonInput={confirmModal.showReasonInput}
                inputPlaceholder={confirmModal.inputPlaceholder}
            />

            {/* History Drawer */}
            <HistoryDrawer
                isOpen={historyDrawer.isOpen}
                onClose={() => setHistoryDrawer(prev => ({ ...prev, isOpen: false }))}
                entityType={historyDrawer.entityType}
                entityId={historyDrawer.entityId}
                entityName={historyDrawer.entityName}
            />

            {/* Patient Details Modal */}
            {patientDetailsModal.isOpen && (
                <PatientDetailsModal
                    patient={patientDetailsModal.patient}
                    onClose={() => setPatientDetailsModal({ isOpen: false, patient: null })}
                />
            )}

            {/* Prescription Modal */}
            <PrescriptionModal
                isOpen={prescriptionModal.isOpen}
                onClose={() => setPrescriptionModal({ ...prescriptionModal, isOpen: false })}
                data={prescriptionModal.data}
                onPrint={handlePrintPrescription}
            />

            {/* View Patient Prescription Modal */}
            <PrescriptionViewModal
                isOpen={viewPrescriptionModal.isOpen}
                patient={viewPrescriptionModal.patient}
                onClose={() => setViewPrescriptionModal({ isOpen: false, patient: null })}
            />

            {/* Payment Modal */}
            {paymentModal.isOpen && (
                <div className="fixed inset-0 z-50 overflow-y-auto">
                    <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
                        <div className="fixed inset-0 transition-opacity" aria-hidden="true">
                            <div className="absolute inset-0 bg-gray-500 opacity-75" onClick={() => setPaymentModal({ ...paymentModal, isOpen: false })}></div>
                        </div>
                        <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>
                        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full">
                            <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
                                <div className="sm:flex sm:items-start">
                                    <div className="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-gray-100 sm:mx-0 sm:h-10 sm:w-10">
                                    </div>
                                    <div className="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
                                        <h3 className="text-lg leading-6 font-medium text-gray-900">
                                            Collect Payment
                                        </h3>
                                        <div className="mt-2">
                                            <p className="text-sm text-gray-500 mb-4">
                                                Collect payment for <strong>{paymentModal.patientName}</strong>.
                                            </p>
                                            <div className="bg-gray-50 p-4 rounded-lg mb-4 text-center">
                                                <span className="block text-xs text-gray-500 uppercase">Total Amount</span>
                                                <span className="text-2xl font-bold text-gray-900">₹{paymentModal.amount}</span>
                                            </div>
                                            <p className="text-sm font-medium text-gray-700 mb-3">Select Payment Method:</p>
                                            <div className="grid grid-cols-2 gap-3">
                                                <button
                                                    onClick={() => handleProcessPayment('Cash')}
                                                    className="flex items-center justify-center gap-2 p-3 border rounded-lg hover:bg-gray-50 hover:border-gray-300 transition-colors group"
                                                >
                                                    <span className="font-medium text-gray-700">Cash</span>
                                                </button>
                                                <button
                                                    onClick={() => handleProcessPayment('Online')}
                                                    className="flex items-center justify-center gap-2 p-3 border rounded-lg hover:bg-gray-50 hover:border-gray-300 transition-colors group"
                                                >
                                                    <span className="font-medium text-gray-700">Online/UPI</span>
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse">
                                <button
                                    type="button"
                                    onClick={() => setPaymentModal({ ...paymentModal, isOpen: false })}
                                    className="mt-3 w-full inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none sm:mt-0 sm:ml-3 sm:w-auto sm:text-sm"
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Payment Success Modal */}
            {paymentSuccessModal.isOpen && (
                <div className="fixed inset-0 z-50 overflow-y-auto">
                    <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
                        <div className="fixed inset-0 transition-opacity" aria-hidden="true">
                            <div className="absolute inset-0 bg-gray-500 opacity-75" onClick={() => setPaymentSuccessModal({ ...paymentSuccessModal, isOpen: false })}></div>
                        </div>
                        <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>
                        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-sm sm:w-full">
                            <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
                                <div className="sm:flex sm:items-start">
                                    <div className="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-gray-100 sm:mx-0 sm:h-10 sm:w-10">
                                    </div>
                                    <div className="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
                                        <h3 className="text-lg leading-6 font-medium text-gray-900">
                                            Payment Successful
                                        </h3>
                                        <div className="mt-2">
                                            <p className="text-sm text-gray-500 mb-4">
                                                Payment recorded for <strong>{paymentSuccessModal.patientName}</strong>.
                                            </p>
                                            <div className="bg-gray-50 p-3 rounded-lg mb-4 text-center">
                                                <span className="block text-xs text-gray-500 uppercase">Total Received</span>
                                                <span className="text-2xl font-bold text-gray-900">₹{paymentSuccessModal.amount}</span>
                                            </div>
                                            <button
                                                onClick={() => handleDownloadReceipt(paymentSuccessModal.billId)}
                                                className="w-full flex items-center justify-center gap-2 p-3 bg-gray-900 text-white rounded-lg hover:bg-gray-800 transition-colors mb-2"
                                            >
                                                <span className="font-medium">Print Receipt</span>
                                            </button>
                                            <button
                                                onClick={() => setPaymentSuccessModal({ ...paymentSuccessModal, isOpen: false })}
                                                className="w-full p-2 text-gray-500 hover:text-gray-700 text-sm font-medium"
                                            >
                                                Close
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ReceptionistDashboard;

// Appointments Table Component (Standardized with Admin)
const AppointmentsTable = ({ appointments, doctors, onStatusUpdate, onHistory, onViewPrescription, startIndex = 0, pagination }) => {
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.No.',
            cell: info => startIndex + info.row.index + 1,
        }),
        columnHelper.accessor(row => row.customId || row.id, {
            id: 'id',
            header: 'ID',
            cell: info => <span title="Serial Number">{info.getValue()}</span>,
        }),
        columnHelper.accessor(row => row.patientName || row.patientId, {
            id: 'patient',
            header: 'Patient',
        }),
        columnHelper.accessor(row => row.doctorName || doctors?.find(d => d.id === row.doctorId)?.name || row.doctorId, {
            id: 'doctor',
            header: 'Doctor',
        }),
        columnHelper.accessor('appointmentDate', {
            header: 'Date',
        }),
        columnHelper.accessor('appointmentTime', {
            header: 'Time',
            cell: info => info.getValue() ? <span className="text-sm font-medium bg-gray-100 px-2 py-1 rounded">{info.getValue().substring(0, 5)}</span> : '-',
        }),
        columnHelper.accessor('status', {
            header: 'Status',
            cell: info => {
                const status = info.getValue();
                const isFinal = ['CANCELLED', 'COMPLETED'].includes(status);
                return (
                    <StatusBadge
                        status={status}
                        options={isFinal ? [] : ['SCHEDULED', 'COMPLETED', 'CANCELLED']}
                        onUpdate={isFinal ? null : (newStatus) => onStatusUpdate(info.row.original.publicId || info.row.original.id, newStatus)}
                        type="dropdown"
                    />
                );
            },
        }),
        columnHelper.accessor('notes', {
            header: 'Notes',
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">Actions</div>,
            cell: info => (
                <div className="text-right">
                    <ActionMenu actions={[
                        {
                            label: 'View Prescription',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zm0 4a1 1 0 011-1h6a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h6a1 1 0 110 2H4a1 1 0 01-1-1z" clipRule="evenodd" /></svg>,
                            onClick: () => onViewPrescription(info.row.original.publicId || info.row.original.id),
                            hidden: info.row.original.status !== 'COMPLETED'
                        },
                        {
                            label: 'History',
                            onClick: () => onHistory(info.row.original),
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" /></svg>
                        },
                        {
                            label: 'Cancel',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>,
                            onClick: () => onStatusUpdate(info.row.original.publicId || info.row.original.id, 'CANCELLED'),
                            variant: 'danger',
                            // Only show cancel if not already cancelled/completed
                            disabled: ['CANCELLED', 'COMPLETED'].includes(info.row.original.status),
                            hidden: ['CANCELLED', 'COMPLETED'].includes(info.row.original.status)
                        }
                    ]} />
                </div>
            ),
        }),
    ];

    // DEBUG: Log first appointment structure to verify publicId
    if (appointments.length > 0) {
        // console.log("DEBUG: First Appointment Record:", appointments[0]);
        const corruptAppointments = appointments.filter(a => !a.publicId);
        if (corruptAppointments.length > 0) {
            console.error("CRITICAL: The following appointments are missing publicId:", JSON.stringify(corruptAppointments, null, 2));
            // Optional: fallback display or warning
        }
    }

    return <DataTable data={appointments} columns={columns} pagination={pagination} />;
};

// Patients Table Component (Standardized with Admin)
const PatientsTable = ({ patients, onStatusUpdate, onViewPrescription, onViewDetails, onOpenPayment, onDownloadReceipt, startIndex = 0, pagination }) => {
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
            header: 'AGE',
        }),
        columnHelper.accessor('gender', {
            header: 'GENDER',
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

                const actions = [
                    {
                        label: 'View Details',
                        icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z" /><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" /></svg>,
                        onClick: () => onViewDetails(patient)
                    }
                ];
                
                return (
                    <div className="text-right">
                        <ActionMenu actions={actions} />
                    </div>
                );
            },
        }),
    ];

    return <DataTable data={patients} columns={columns} pagination={pagination} />;
};
