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

    const [selectedDate, setSelectedDate] = useState(''); // Empty = All, Date String = Specific Date
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

    // Pagination & Search
    const [page, setPage] = useState(1);
    const ITEMS_PER_PAGE = 10;
    const [paginatedData, setPaginatedData] = useState([]);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');

    const { success, error: toastError, info } = useToast();
    const navigate = useNavigate();
    const user = authService.getCurrentUser();

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
        const todayStr = new Date().toISOString().split('T')[0];
        const todayCount = appointments.filter(a => a.appointmentDate === todayStr).length;
        const pendingCount = appointments.filter(a => a.status === 'SCHEDULED').length;

        setStats({
            today: todayCount,
            pending: pendingCount,
            total: appointments.length
        });
    }, [appointments]);

    useEffect(() => {
        const applyPagination = () => {
            let allData = [];

            if (activeTab === 'appointments') {
                allData = appointments;
                // Filter by Date if selected
                if (selectedDate) {
                    allData = allData.filter(a => a.appointmentDate === selectedDate);
                }
            }
            else if (activeTab === 'patients') allData = patients;

            // Client-side search filtering
            if (searchTerm) {
                const term = searchTerm.toLowerCase();
                if (activeTab === 'appointments') {
                    allData = allData.filter(a =>
                        a.patientId.toString().includes(term) ||
                        (a.notes && a.notes.toLowerCase().includes(term))
                    );
                }
            }

            // Ensure allData is an array before using array methods
            if (!Array.isArray(allData)) {
                console.error('allData is not an array:', allData);
                allData = [];
            }

            setTotalPages(Math.ceil(allData.length / ITEMS_PER_PAGE));
            setTotalElements(allData.length);
            const start = (page - 1) * ITEMS_PER_PAGE;
            setPaginatedData(allData.slice(start, start + ITEMS_PER_PAGE));
        };
        applyPagination();
    }, [appointments, patients, page, activeTab, searchTerm, selectedDate]);

    const loadData = async () => {
        setLoading(true);
        try {
            if (activeTab === 'appointments') {
                const data = await hospitalService.getMyAppointments(viewFilter);
                // Handle both array and paginated response
                const appointmentsArray = Array.isArray(data) ? data : (data.content || []);
                setAppointments(appointmentsArray);
            } else if (activeTab === 'patients') {
                const data = await hospitalService.getPatients(searchTerm, 0, 1000, patientViewFilter);
                // Handle both array and paginated response
                const patientsArray = Array.isArray(data) ? data : (data.content || []);
                setPatients(patientsArray);
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
        { id: 'appointments', label: 'My Appointments', icon: '📅' },
        { id: 'patients', label: 'Patients', icon: '👥' },
    ];

    const pagination = {
        pageIndex: page - 1, // DataTable expects 0-indexed
        pageSize: ITEMS_PER_PAGE,
        totalItems: totalElements,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage + 1) // DataTable gives 0-indexed
    };

    // Consultation Modal
    const [consultationModal, setConsultationModal] = useState({ isOpen: false, appointment: null, patient: null });

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
            toastError("Failed to download prescription");
            console.error(err);
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
        <div className="flex h-screen bg-neutral-50">
            {/* Sidebar */}
            <Sidebar
                title="HMS Portal"
                tabs={tabs}
                activeTab={activeTab}
                onTabChange={setActiveTab}
                footerTitle="My Hospital"
                footerData={user?.hospitalName}
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

                    {/* Stats Cards */}
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
                                    <p className="text-gray-500 text-sm font-medium uppercase tracking-wider">Pending Action</p>
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
                                <div className="h-6 w-px bg-gray-300 mx-2"></div>
                                <div className="flex items-center gap-2">
                                    <span className="text-sm text-gray-600 hidden sm:inline">Filter Date:</span>
                                    <div className="relative">
                                        <input
                                            type="date"
                                            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none shadow-sm"
                                            value={selectedDate}
                                            onChange={(e) => setSelectedDate(e.target.value)}
                                        />
                                        {selectedDate && (
                                            <button
                                                onClick={() => setSelectedDate('')}
                                                className="absolute -right-2 -top-2 bg-red-500 text-white rounded-full w-4 h-4 flex items-center justify-center text-xs hover:bg-red-600 shadow-sm"
                                                title="Clear date"
                                            >
                                                ×
                                            </button>
                                        )}
                                    </div>
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
                            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden mb-4">
                                {activeTab === 'appointments' ? (
                                    paginatedData.length > 0 ? (
                                        <DoctorAppointmentsTable
                                            appointments={paginatedData}
                                            onStatusUpdate={handleStatusUpdate}
                                            onEdit={handleEditClick}
                                            onConsult={handleConsultClick}
                                            onPrint={handlePrintPrescription}
                                            onAuditHistory={(item) => handleAuditHistory('APPOINTMENT', item.customId || item.id, "Appointment")}
                                            startIndex={(page - 1) * ITEMS_PER_PAGE}
                                            pagination={pagination}
                                        />
                                    ) : (
                                        <EmptyState
                                            icon="📅"
                                            title="No Appointments"
                                            message={selectedDate ? `No appointments scheduled for ${selectedDate}.` : "You have no appointments scheduled."}
                                        />
                                    )
                                ) : (
                                    paginatedData.length > 0 ? (
                                        <DoctorPatientsTable
                                            patients={paginatedData}
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
                                            icon="👥"
                                            title="No Patients"
                                            message="No patients found in the hospital."
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
                    <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg p-6 m-4">
                        <h3 className="text-xl font-bold text-gray-800 mb-4">Edit Appointment Details</h3>

                        <div className="mb-4">
                            <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                            <select
                                value={editModal.status}
                                onChange={(e) => setEditModal({ ...editModal, status: e.target.value })}
                                className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-primary-500"
                            >
                                <option value="SCHEDULED">Scheduled</option>
                                <option value="COMPLETED">Completed</option>
                                <option value="CANCELLED">Cancelled</option>
                            </select>
                        </div>

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
                                className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition"
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
                            icon: '📜',
                            onClick: () => onAuditHistory(info.row.original)
                        },
                        {
                            label: 'Edit Details',
                            icon: '✏️',
                            onClick: () => onEdit(info.row.original)
                        },
                        // Only show status actions if not final
                        ...(info.row.original.status === 'SCHEDULED' ? [{
                            label: 'Start Consultation',
                            icon: '🩺',
                            onClick: () => onConsult(info.row.original)
                        }, {
                            label: 'Mark Completed',
                            icon: '✅',
                            onClick: () => onStatusUpdate(info.row.original.id, 'COMPLETED')
                        }] : []),
                        ...(info.row.original.status === 'COMPLETED' ? [{
                            label: 'Print Prescription',
                            icon: '🖨️',
                            onClick: () => onPrint(info.row.original.id)
                        }] : []),
                        {
                            label: 'Cancel Appointment',
                            icon: '❌',
                            onClick: () => onStatusUpdate(info.row.original.id, 'CANCELLED'),
                            variant: 'danger'
                        }
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
            id: 'actions',
            header: () => <div className="text-right">ACTIONS</div>,
            cell: info => {
                const patient = info.row.original;
                const status = patient.status || 'REGISTERED';

                // Primary action based on status
                let primaryButton = null;
                if (status === 'REGISTERED') {
                    primaryButton = (
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                console.log("Start button clicked for:", patient);
                                onStartConsultation(patient);
                            }}
                            className="px-3 py-1.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition font-semibold text-xs flex items-center gap-1 z-10 relative"
                            title="Start Consultation"
                        >
                            <span>🩺</span>
                            <span>Start</span>
                        </button>
                    );
                } else if (status === 'CONSULTING') {
                    primaryButton = (
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                console.log("Complete button clicked for:", patient);
                                onCompleteConsultation(patient);
                            }}
                            className="px-3 py-1.5 bg-green-600 text-white rounded-lg hover:bg-green-700 transition font-semibold text-xs flex items-center gap-1 z-10 relative"
                            title="Complete Consultation"
                        >
                            <span>✅</span>
                            <span>Finish</span>
                        </button>
                    );
                } else if (status === 'COMPLETED') {
                    primaryButton = (
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                console.log("View Rx button clicked for:", patient);
                                onViewPrescription(patient);
                            }}
                            className="px-3 py-1.5 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition font-semibold text-xs flex items-center gap-1 z-10 relative"
                            title="View Prescription"
                        >
                            <span>💊</span>
                            <span>View Rx</span>
                        </button>
                    );
                }

                // Secondary actions for three-dot menu
                const secondaryActions = [
                    {
                        label: 'View History',
                        icon: '📜',
                        onClick: () => onViewHistory(patient)
                    }
                ];

                return (
                    <div className="flex items-center justify-end gap-2">
                        {primaryButton}
                        <ActionMenu actions={secondaryActions} />
                    </div>
                );
            },
        }),
    ];

    return <DataTable data={patients} columns={columns} pagination={pagination} />;
};

