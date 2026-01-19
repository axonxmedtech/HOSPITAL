import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import authService from '../../services/authService';
import hospitalService from '../../services/hospitalService';
import { useToast } from '../../context/ToastContext';
import EmptyState from '../../components/EmptyState';
import ConfirmationModal from '../../components/ConfirmationModal';
import AppointmentModal from '../../components/AppointmentModal';
import PatientModal from '../../components/PatientModal';
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

const ReceptionistDashboard = () => {
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
    const [doctors, setDoctors] = useState([]);
    const [billing, setBilling] = useState([]);
    const [loading, setLoading] = useState(false);
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
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

    const { success, error: toastError, info } = useToast();
    const navigate = useNavigate();
    const user = authService.getCurrentUser();

    // Stats
    const [stats, setStats] = useState({ today: 0, pending: 0, total: 0 });

    useEffect(() => {
        loadData();

        // Polling for real-time updates (every 30 seconds)
        const intervalId = setInterval(() => {
            loadData(false); // Silent refresh
        }, 30000);

        return () => clearInterval(intervalId);
    }, [activeTab, page, searchTerm, viewFilter, patientViewFilter, pageSize]); // Add pageSize to dependencies

    const loadData = async (showSpinner = true) => {
        if (showSpinner) setLoading(true);
        try {
            // Stats
            const statsData = await hospitalService.getAppointmentStats();
            setStats(statsData);

            if (activeTab === 'appointments') {
                // Fetch appointments (Server-side) + Doctors + Patients for lookup
                const [apptData, docData, patData] = await Promise.all([
                    hospitalService.getAppointments(page, pageSize, viewFilter),
                    hospitalService.getDoctors('', 0, 100), // Fetch doctors for lookup
                    hospitalService.getPatients('', 0, 1000) // Fetch ALL patients (up to 1000) for lookup dropdown
                ]);

                if (apptData.content) {
                    setAppointments(apptData.content);
                    setTotalPages(apptData.totalPages);
                    setTotalElements(apptData.totalElements);
                } else {
                    setAppointments(apptData);
                    setTotalPages(1);
                    setTotalElements(apptData.length);
                }

                // Set Doctors for lookup/dropdown
                if (docData.content) setDoctors(docData.content);
                else setDoctors(docData);

                // Set Patients for lookup/dropdown in specific modal
                if (patData.content) setPatients(patData.content);
                else setPatients(patData);

            } else if (activeTab === 'patients') {
                const data = await hospitalService.getPatients(searchTerm, page, pageSize, patientViewFilter);
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
                const data = await hospitalService.getBills(page, pageSize);
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
        try {
            await hospitalService.updateBillStatus(id, status);
            success('Bill status updated');
            loadData();
        } catch (err) {
            toastError('Failed to update bill status');
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
            await hospitalService.updateBillStatus(paymentModal.billId, 'PAID', method);
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
        setSelectedPatient(patient);
        setIsAddModalOpen(true);
    };

    const tabs = [
        { id: 'appointments', label: 'Appointments', icon: '📅' },
        { id: 'patients', label: 'Patients', icon: '👥' },
        { id: 'billing', label: 'Billing', icon: '💰' },
    ];

    const pagination = {
        pageIndex: page,
        pageSize: pageSize,
        totalItems: totalElements,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage)
    };

    return (
        <div className="flex h-screen bg-neutral-50">
            {/* Sidebar */}
            <Sidebar
                title="HMS Portal"
                tabs={tabs}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                footerTitle="Hospital"
                footerData={user?.hospitalName}
            />

            {/* Main Content */}
            <div className="flex-1 flex flex-col overflow-hidden">
                <Navbar
                    title={`${tabs.find(t => t.id === activeTab)?.label} Dashboard`}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => console.log('Profile clicked')}
                />

                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-neutral-50 p-8">
                    {/* Stats for Receptionist */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                        <div className="bg-white rounded-xl shadow-sm p-6 border-l-4 border-blue-500">
                            <div className="flex justify-between items-center">
                                <div>
                                    <p className="text-gray-500 text-sm font-medium uppercase tracking-wider">Today's Appointments</p>
                                    <h3 className="text-3xl font-bold text-gray-800 mt-1">{stats.today}</h3>
                                </div>
                                <div className="bg-blue-100 p-3 rounded-full text-blue-600 text-xl">📅</div>
                            </div>
                        </div>
                        <div className="bg-white rounded-xl shadow-sm p-6 border-l-4 border-orange-500">
                            <div className="flex justify-between items-center">
                                <div>
                                    <p className="text-gray-500 text-sm font-medium uppercase tracking-wider">Pending</p>
                                    <h3 className="text-3xl font-bold text-gray-800 mt-1">{stats.pending}</h3>
                                </div>
                                <div className="bg-orange-100 p-3 rounded-full text-orange-600 text-xl">⏳</div>
                            </div>
                        </div>
                        <div className="bg-white rounded-xl shadow-sm p-6 border-l-4 border-gray-500">
                            <div className="flex justify-between items-center">
                                <div>
                                    <p className="text-gray-500 text-sm font-medium uppercase tracking-wider">Total Appointments</p>
                                    <h3 className="text-3xl font-bold text-gray-800 mt-1">{stats.total}</h3>
                                </div>
                                <div className="bg-gray-100 p-3 rounded-full text-gray-600 text-xl">📈</div>
                            </div>
                        </div>
                    </div>

                    {/* Standardized Header */}
                    <PageHeader
                        title={tabs.find(t => t.id === activeTab)?.label}
                        subtitle={`Manage ${activeTab} records`}
                        onSearch={(e) => setSearchTerm(e.target.value)}
                        searchValue={searchTerm}
                        searchPlaceholder={`Search ${activeTab}...`}
                        onAdd={() => setIsAddModalOpen(true)}
                        addLabel={`Add ${activeTab.slice(0, -1)}`}
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
                        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden mb-4">
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
                                        icon="📅"
                                        title="No Appointments Found"
                                        message="Schedule appointments for your patients."
                                        actionLabel="Schedule Appointment"
                                        onAction={() => setIsAddModalOpen(true)}
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
                                        icon="👥"
                                        title="No Patients Found"
                                        message="There are no patients registered in the system yet."
                                        actionLabel="Add Patient"
                                        onAction={() => setIsAddModalOpen(true)}
                                    />
                                )
                            )}
                            {activeTab === 'billing' && billing.length === 0 && (
                                <EmptyState
                                    icon="💰"
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
                                    <div className="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-blue-100 sm:mx-0 sm:h-10 sm:w-10">
                                        <span className="text-blue-600 text-xl">💰</span>
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
                                                    className="flex items-center justify-center gap-2 p-3 border rounded-lg hover:bg-green-50 hover:border-green-200 transition-colors group"
                                                >
                                                    <span className="text-xl">💵</span>
                                                    <span className="font-medium text-gray-700 group-hover:text-green-700">Cash</span>
                                                </button>
                                                <button
                                                    onClick={() => handleProcessPayment('Online')}
                                                    className="flex items-center justify-center gap-2 p-3 border rounded-lg hover:bg-blue-50 hover:border-blue-200 transition-colors group"
                                                >
                                                    <span className="text-xl">💳</span>
                                                    <span className="font-medium text-gray-700 group-hover:text-blue-700">Online/UPI</span>
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
                                    <div className="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-green-100 sm:mx-0 sm:h-10 sm:w-10">
                                        <span className="text-green-600 text-xl">✅</span>
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
                                                className="w-full flex items-center justify-center gap-2 p-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm mb-2"
                                            >
                                                <span className="text-xl">🖨️</span>
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
                            icon: '💊',
                            onClick: () => onViewPrescription(info.row.original.publicId || info.row.original.id),
                            hidden: info.row.original.status !== 'COMPLETED'
                        },
                        {
                            label: 'History',
                            onClick: () => onHistory(info.row.original),
                            icon: <span role="img" aria-label="history">📜</span>
                        },
                        {
                            label: 'Cancel',
                            icon: '❌',
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
        columnHelper.accessor('phone', {
            header: 'PHONE',
        }),
        columnHelper.accessor('address', {
            header: 'ADDRESS',
            cell: info => <span className="truncate max-w-xs block">{info.getValue()}</span>
        }),
        columnHelper.accessor('status', {
            header: 'STATUS',
            cell: info => {
                const status = info.getValue() || 'REGISTERED';
                const patient = info.row.original;
                return (
                    <StatusBadge
                        status={status}
                        options={['REGISTERED', 'CONSULTING', 'COMPLETED']}
                        onUpdate={(newStatus) => onStatusUpdate(patient.publicId, newStatus)}
                        type="dropdown"
                    />
                );
            }
        }),
        columnHelper.display({
            id: 'payment',
            header: 'PAYMENT',
            cell: info => {
                const bill = info.row.original.latestBill;
                if (!bill) return <span className="text-xs text-gray-400"></span>;

                return (
                    <div className="flex flex-col">
                        <span className="font-semibold text-gray-900">₹{bill.amount}</span>
                        {bill.paymentStatus === 'PAID' ? (
                            <span className="text-xs text-green-600 font-medium flex items-center gap-1">
                                ✅ Paid ({bill.paymentMethod || 'Cash'})
                            </span>
                        ) : (
                            <span className="text-xs text-orange-600 font-medium">⏳ Pending</span>
                        )}
                    </div>
                );
            }
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">ACTIONS</div>,
            cell: info => {
                const patient = info.row.original;
                const status = patient.status || 'REGISTERED';

                const actions = [];

                // Receptionist can view prescription for completed patients
                if (status === 'COMPLETED') {
                    actions.push({
                        label: 'View Prescription',
                        icon: '💊',
                        onClick: () => onViewPrescription(patient)
                    });
                }

                actions.push({
                    label: 'View Details',
                    icon: '👁️',
                    onClick: () => onViewDetails(patient)
                });

                // Add Payment Action
                if (patient.latestBill && patient.latestBill.paymentStatus === 'PENDING') {
                    actions.unshift({
                        label: 'Collect Payment',
                        icon: '💰',
                        onClick: () => onOpenPayment(patient),
                        variant: 'primary'
                    });
                }

                // Add Print Receipt Action for Paid Bills
                if (patient.latestBill && patient.latestBill.paymentStatus === 'PAID') {
                    actions.unshift({
                        label: 'Print Receipt',
                        icon: '🖨️',
                        onClick: () => onDownloadReceipt(patient.latestBill.id),
                        variant: 'secondary'
                    });
                }

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
