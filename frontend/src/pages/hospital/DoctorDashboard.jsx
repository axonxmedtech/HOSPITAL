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
import useWebSocket from '../../hooks/useWebSocket';

import PageHeader from '../../components/PageHeader';
import { createColumnHelper } from '@tanstack/react-table';
import ConsultationModal from '../../components/ConsultationModal';
import PrescriptionViewModal from '../../components/PrescriptionViewModal';
import BillingTable from './BillingTable';
import AppointmentModal from '../../components/AppointmentModal';
import PatientModal from '../../components/PatientModal';
import PatientDetailsModal from '../../components/PatientDetailsModal';
import ProfileModal from '../../components/ProfileModal';
import IpdAdmitModal from '../../components/IpdAdmitModal';
import { SkeletonDashboard, SkeletonStatsGrid, SkeletonOverviewDual, SkeletonTable } from '../../components/Skeleton';
import MedicineInventoryTab from '../../components/MedicineInventoryTab';
import HospitalInventoryTab from '../../components/HospitalInventoryTab';

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
    const navigate = useNavigate();
    const [user, setUser] = useState(() => authService.getCurrentUser());
    const modules = user?.modules || [];
    const hasIPD = modules.includes('IPD');
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'overview';
    const viewFilter = searchParams.get('appointmentFilter') || 'today';

    // Helper to switch tabs
    const setActiveTab = (tab) => {
        if (tab === 'mrd') {
            navigate('/hospital/doctor/mrd');
            return;
        }
        const newParams = { tab };
        if (tab === 'appointments' && viewFilter) newParams.appointmentFilter = viewFilter;
        setSearchParams(newParams);
    };

    // Helper to set appointment filter
    const setViewFilter = (filter) => {
        setSearchParams({ tab: activeTab, appointmentFilter: filter });
    };

    const [appointments, setAppointments] = useState([]);
    const [todaysFollowUps, setTodaysFollowUps] = useState([]);
    const [patients, setPatients] = useState([]);
    const [loading, setLoading] = useState(false);
    const [lowStockItems, setLowStockItems] = useState([]);

    // View Prescription Modal State
    const [viewPrescriptionModal, setViewPrescriptionModal] = useState({
        isOpen: false,
        patient: null
    });

    // Stats State
    const [stats, setStats] = useState({ today: 0, pending: 0, total: 0 });
    const [ipdCount, setIpdCount] = useState(0);
    // Queue / OPD state (doctor-specific)
    const [queueEntries, setQueueEntries] = useState([]);
    const [opds, setOpds] = useState([]);
    const [currentPatient, setCurrentPatient] = useState(null);
    const [nextPatient, setNextPatient] = useState(null);

    // Pagination & Search
    const [page, setPage] = useState(1);
    const ITEMS_PER_PAGE = 10;
    const [paginatedData, setPaginatedData] = useState([]);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [totalAppointments, setTotalAppointments] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');

    const { success, error: toastError, info } = useToast();

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

    // Sidebar collapse state
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);

    // OPD date filter — default to today
    const todayStr = (() => { const t = new Date(); return t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0'); })();
    const [opdDateFilter, setOpdDateFilter] = useState(todayStr);
    const [opdTabView, setOpdTabView] = useState('Live');

    // Patient tab: All / Date toggle
    const [patientTabView, setPatientTabView] = useState('All');
    const [patientDateFilter, setPatientDateFilter] = useState(todayStr);

    // Billing specific states
    const [billing, setBilling] = useState([]);
    const [billingStatus, setBillingStatus] = useState('PENDING');

    const [customFees, setCustomFees] = useState([]);
    const [standardFees, setStandardFees] = useState({ consultationFee: '0', casePaperFee: '0' });
    const [editBillItemsModal, setEditBillItemsModal] = useState({
        isOpen: false,
        billId: null,
        items: [],
        medicines: [],
        patientName: '',
        billNumber: ''
    });
    const [editBillItemsSubmitting, setEditBillItemsSubmitting] = useState(false);

    // SOLO Doctor specific states
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [isAddPatientModalOpen, setIsAddPatientModalOpen] = useState(false);
    const [isOpdModalOpen, setIsOpdModalOpen] = useState(false);
    const [patientDetailsModal, setPatientDetailsModal] = useState({ isOpen: false, patient: null });
    const [isIpdAdmitOpen, setIsIpdAdmitOpen] = useState(false);
    const [ipdOpdForAdmit, setIpdOpdForAdmit] = useState(null);
    // State for admit-to-IPD directly from appointment action
    const [ipdFromAppointment, setIpdFromAppointment] = useState({ isOpen: false, appointment: null });

    // Payment Modals
    const [paymentModal, setPaymentModal] = useState({
        isOpen: false,
        billId: null,
        amount: null,
        patientName: ''
    });

    const [paymentSuccessModal, setPaymentSuccessModal] = useState({
        isOpen: false,
        billId: null,
        patientName: '',
        amount: 0
    });

    // Resolved Doctor record state
    const [doctorRecord, setDoctorRecord] = useState(null);

    // Fetch doctor record corresponding to user email
    useEffect(() => {
        const fetchDoctorRecord = async () => {
            try {
                const docs = await hospitalService.getDoctors('', 0, 100);
                const docsList = Array.isArray(docs) ? docs : (docs.content || []);
                const currentDoc = docsList.find(d => d.email?.toLowerCase() === user?.email?.toLowerCase());
                if (currentDoc) {
                    setDoctorRecord(currentDoc);
                    setOpdForm(prev => ({ ...prev, doctorId: currentDoc.id }));
                }
            } catch (err) {
                console.error("Failed to load doctor record", err);
            }
        };
        if (user?.email) {
            fetchDoctorRecord();
        }
    }, [user]);

    // OPD Form
    const [opdForm, setOpdForm] = useState({
        patientId: null,
        receptionistId: null,
        doctorId: user?.id || null,
        bp: '',
        temperature: '',
        pulse: '',
        weight: '',
        spo2: '',
        problem: '',
        visitType: 'NEW'
    });
    const [patientSearchText, setPatientSearchText] = useState('');
    const [showPatientDropdown, setShowPatientDropdown] = useState(false);

    // Reset OPD form on modal open/close
    useEffect(() => {
        if (!isOpdModalOpen) {
            setPatientSearchText('');
            setShowPatientDropdown(false);
            setOpdForm(prev => ({
                ...prev,
                patientId: null,
                doctorId: doctorRecord?.id || user?.id || null,
                bp: '',
                temperature: '',
                pulse: '',
                weight: '',
                spo2: '',
                problem: '',
                visitType: 'NEW'
            }));
        }
    }, [isOpdModalOpen, doctorRecord]);

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
        setPatientDetailsModal({ isOpen: true, patient });
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
        setPage(1);
        if (activeTab === 'opd') { setOpdTabView('Live'); setOpdDateFilter(todayStr); }
        if (activeTab === 'patients') { setPatientTabView('All'); setPatientDateFilter(todayStr); }
    }, [activeTab, searchTerm, viewFilter, billingStatus]);

    useEffect(() => {
        const timer = setTimeout(() => {
            loadData();
        }, 500);
        return () => clearTimeout(timer);
    }, [activeTab, searchTerm, viewFilter, billingStatus, opdDateFilter, opdTabView, patientTabView, patientDateFilter, page]);

    // WebSocket connection will be initialized below loadData definition to avoid ReferenceError

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
            total: totalAppointments
        });
    }, [appointments, totalAppointments]);

    
    const loadData = async (showSpinner = true) => {
        if (showSpinner) setLoading(true);
        try {
            if (activeTab === 'appointments') {
                const localPage = page - 1;
                const data = await hospitalService.getMyAppointments(viewFilter, searchTerm, localPage, ITEMS_PER_PAGE,);
                // Handle both array and paginated response
                const appointmentsArray = Array.isArray(data) ? data : (data.content || []);
                setAppointments(appointmentsArray);
                setTotalElements(data.totalElements || 0);
                setTotalAppointments(data.totalElements || 0);
                setTotalPages(data.totalPages || 1);
            } else if (activeTab === 'overview') {
                try {
                    const data = await hospitalService.getMyAppointments(viewFilter, searchTerm, 0, 100);
                    const appointmentsArray = Array.isArray(data) ? data : (data.content || []);
                    setAppointments(appointmentsArray);
                    setTotalAppointments(data.totalElements || appointmentsArray.length);
                    
                    const followUpsData = await hospitalService.getTodaysFollowUps();
                    setTodaysFollowUps(followUpsData || []);

                    // If Solo Mode is active, fetch patients for Overview's patient list as well
                    if (user?.receptionMode === 'SOLO') {
                        const patData = await hospitalService.getPatients('', 0, 100);
                        const patientsArray = Array.isArray(patData) ? patData : (patData.content || []);
                        setPatients(patientsArray);
                    }
                } catch (err) {
                    console.error('Failed to load overview appointments and followups', err);
                }

                    // Fetch IPD count if module is enabled
                    if (hasIPD) {
                        try {
                            const admitted = await hospitalService.getAdmittedIpdAdmissions();
                            setIpdCount(Array.isArray(admitted) ? admitted.length : 0);
                        } catch {
                            setIpdCount(0);
                        }
                    }
            } else if (activeTab === 'patients') {
                const dateParam = patientTabView === 'Date' ? patientDateFilter : '';
                const data = await hospitalService.getPatients(
                    searchTerm, 
                    page - 1, 
                    ITEMS_PER_PAGE,
                    dateParam
                );
                const patientsArray = Array.isArray(data) ? data : (data.content || []);
                setPatients(patientsArray);
                setTotalElements(data.totalElements || 0);
                setTotalPages(data.totalPages || 1);
            } else if (activeTab === 'billing') {
                const data = await hospitalService.getBills(searchTerm, page - 1, ITEMS_PER_PAGE, billingStatus);
                const billingArray = Array.isArray(data) ? data : (data.content || []);
                setBilling(billingArray);
                setTotalElements(data.totalElements || billingArray.length);
                setTotalPages(data.totalPages || 1);
            }

            // Doctor-specific queue (loaded for overview, queue, and opd tabs to enable position mapping)
            if (activeTab === 'overview' || activeTab === 'queue' || activeTab === 'opd') {
                try {
                    // For doctor dashboard use authenticated doctor's queue (backend maps user -> doctor)
                    const q = await hospitalService.getDoctorQueue();
                    setQueueEntries(q || []);
                    if (q && q.length > 0) {
                        const sorted = [...q].sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
                        setCurrentPatient(sorted[0]?.opd?.patient?.name || sorted[0]?.opd?.patientName || 'No Name');
                        setNextPatient(sorted[1] ? (sorted[1]?.opd?.patient?.name || sorted[1]?.opd?.patientName || 'No Name') : null);
                    } else {
                        setCurrentPatient(null);
                        setNextPatient(null);
                    }
                } catch (err) {
                    console.error('Failed to load doctor queue', err);
                    setQueueEntries([]);
                }
            }

            // OPD list for doctor (read-only)
            if (activeTab === 'opd') {
                try {
                    let dateParam = '';
                    let statusParam = '';
                    if (opdTabView === 'Live') {
                        dateParam = todayStr;
                        statusParam = 'QUEUED';
                    } else {
                        dateParam = opdDateFilter;
                        statusParam = ''; // all statuses on that date
                    }
                    const opdsData = await hospitalService.getOpds(searchTerm, page - 1, ITEMS_PER_PAGE, dateParam, statusParam);
                    let opdsArray = Array.isArray(opdsData) ? opdsData : (opdsData.content || []);
                    // In regular mode for Live view, we only show QUEUED anyway, but let's keep the local safety fallback
                    if (user?.receptionMode !== 'SOLO' && opdTabView === 'Live') {
                        opdsArray = opdsArray.filter(o => o.status === 'QUEUED');
                    }
                    setOpds(opdsArray);
                    setTotalElements(opdsData.totalElements || opdsArray.length);
                    setTotalPages(opdsData.totalPages || 1);
                } catch (err) {
                    console.error('Failed to load OPDs', err);
                    setOpds([]);
                }
            }

            // IPD list for doctor (their own admissions)
            if (activeTab === 'ipd') {
                try {
                    // use role-aware admitted list (server will filter to current doctor)
                    const list = await hospitalService.getAdmittedIpdAdmissions();
                    let arr = Array.isArray(list) ? list : (list.content || []);
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
                    setTotalElements(arr.length);
                    setTotalPages(1);
                } catch (err) {
                    console.error('Failed to load IPD admissions for doctor', err);
                    setOpds([]);
                }
            }

            // Check for low-stock items if in Clinic mode or Solo mode
            try {
                let lowStock = [];
                const promises = [];

                if (user?.inClinic !== false) {
                    promises.push(
                        hospitalService.getInventoryMedicines()
                            .then(inv => (inv || [])
                                .filter(item => item.isActive !== false && item.stockQuantity <= item.minStockLevel)
                                .map(item => ({ ...item, isMedicine: true }))
                            )
                            .catch(err => {
                                console.error("Failed to load medicine inventory for low stock alerts", err);
                                return [];
                            })
                    );
                }

                if (user?.receptionMode === 'SOLO') {
                    promises.push(
                        hospitalService.getHospitalInventory()
                            .then(inv => (inv || [])
                                .filter(item => item.isActive !== false && item.stockQuantity <= item.minStockLevel)
                                .map(item => ({ ...item, isHospitalItem: true }))
                            )
                            .catch(err => {
                                console.error("Failed to load hospital inventory for low stock alerts", err);
                                return [];
                            })
                    );
                }

                if (promises.length > 0) {
                    const results = await Promise.all(promises);
                    lowStock = results.flat();
                }

                setLowStockItems(lowStock);
            } catch (err) {
                console.error("Failed to check for low-stock items", err);
                setLowStockItems([]);
            }
        } catch (err) {
            toastError('Failed to load data');
        } finally {
            if (showSpinner) setLoading(false);
        }
    };

    // WebSocket real-time live sync (defined after loadData to avoid ReferenceError)
    useWebSocket(user, setUser, loadData);

    // Fetch fresh profile on mount to sync sessionStorage settings
    useEffect(() => {
        const fetchProfileOnMount = async () => {
            try {
                const profile = await authService.getProfile();
                const updatedUser = authService.updateCurrentUser(profile);
                if (updatedUser) {
                    setUser(updatedUser);
                }
            } catch (err) {
                console.error("Failed to fetch profile on mount", err);
            }
        };
        fetchProfileOnMount();

        // 15-second background synchronization fallback
        const interval = setInterval(async () => {
            try {
                const profile = await authService.getProfile();
                const updatedUser = authService.updateCurrentUser(profile);
                if (updatedUser) {
                    setUser(updatedUser);
                }
            } catch (err) {
                console.error("Background profile sync failed", err);
            }
        }, 15000);

        return () => clearInterval(interval);
    }, []);

    const handleStatusUpdate = (id, newStatus) => {
        const action = newStatus === 'COMPLETED' ? 'Complete' : 'Cancel';

        if (newStatus === 'CANCELLED') {
            openConfirmation(
                'Cancel Appointment',
                'Are you sure you want to cancel this appointment? This action cannot be undone.',
                async (reason) => {
                    try {
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

    const handleBillStatus = async (id, status, billObj = null) => {
        // Open payment modal when marking PAID
        if (status === 'PAID') {
            setPaymentModal({ 
                isOpen: true, 
                billId: id,
                amount: billObj?.balance ?? billObj?.amount ?? null,
                patientName: billObj?.patientName || ''
            });
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

    const [docPrintingId, setDocPrintingId] = useState(null);
    const handlePrintReceipt = (id) => {
        openPdfInNewTab(`/hospital/billing/${id}/pdf`);
    };

    const handleOpenEditBillItems = async (billObj) => {
        try {
            const [stdData, customData] = await Promise.all([
                hospitalService.getHospitalFees(),
                hospitalService.getCustomFees()
            ]);
            setStandardFees({
                consultationFee: stdData.consultationFee != null ? stdData.consultationFee : '0',
                casePaperFee: stdData.casePaperFee != null ? stdData.casePaperFee : '0'
            });
            setCustomFees(customData || []);
            
            const mappedItems = (billObj.items || []).map(it => ({
                id: it.id,
                name: it.description,
                defaultAmount: it.amount
            }));

            setEditBillItemsModal({
                isOpen: true,
                billId: billObj.id,
                items: mappedItems,
                medicines: billObj.medicines || [],
                patientName: billObj.patientName || '',
                billNumber: billObj.customId || billObj.id
            });
        } catch (err) {
            toastError("Failed to open bill editor");
        }
    };

    const handleSaveBillItems = async () => {
        setEditBillItemsSubmitting(true);
        try {
            const filteredItems = editBillItemsModal.items.filter(it => it.name && it.name.trim() !== "");
            await hospitalService.updateBillItems(editBillItemsModal.billId, filteredItems);
            success("Bill items updated successfully");
            setEditBillItemsModal({ isOpen: false, billId: null, items: [], medicines: [], patientName: '', billNumber: '' });
            loadData();
        } catch (err) {
            const msg = err.response?.data || "Failed to update bill items";
            toastError(msg);
        } finally {
            setEditBillItemsSubmitting(false);
        }
    };

    const updateBillItem = (index, field, value) => {
        setEditBillItemsModal(prev => {
            const updated = [...prev.items];
            updated[index] = { ...updated[index], [field]: value };
            return { ...prev, items: updated };
        });
    };

    const removeBillItem = (index) => {
        setEditBillItemsModal(prev => {
            const updated = prev.items.filter((_, i) => i !== index);
            return { ...prev, items: updated };
        });
    };

    const addBillItem = () => {
        setEditBillItemsModal(prev => ({
            ...prev,
            items: [...prev.items, { name: '', defaultAmount: '' }]
        }));
    };

    const [docPaymentProcessing, setDocPaymentProcessing] = useState(false);

    const handleProcessPayment = async (method) => {
        if (docPaymentProcessing) return;
        setDocPaymentProcessing(method);
        try {
            const pm = method === 'Online' ? 'UPI' : 'CASH';
            let reference = null;
            if (pm === 'UPI') {
                reference = window.prompt('Enter UTR / transaction reference (required for UPI):');
                if (!reference || !reference.trim()) {
                    toastError('UTR / reference is required for UPI payments');
                    setDocPaymentProcessing(false);
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
        } finally {
            setDocPaymentProcessing(false);
        }
    };

    const handleLogout = () => {
        const loginUrl = authService.getLoginUrl();
        authService.logout();
        navigate(loginUrl);
    };

    const isSolo = user?.receptionMode === 'SOLO';
    const hasBilling = user?.billingHandler === 'DOCTOR' || user?.billingHandler === 'BOTH';
    const hasInClinic = user?.inClinic !== false;
    const hasMedicalInventory = modules.includes('MEDICAL_INVENTORY');
    const hasHospitalInventory = modules.includes('HOSPITAL_INVENTORY');

    const tabs = [
        { id: 'overview', label: 'Overview', icon: null },
        ...(hasIPD ? [{ id: 'ipd', label: 'IPD', icon: null }] : []),
        ...(hasIPD ? [{ id: 'mrd', label: 'MRD Archive', icon: null }] : []),
        { id: 'opd', label: 'OPD', icon: null },
        { id: 'patients', label: 'Patients', icon: null },
        ...((isSolo || hasBilling) ? [{ id: 'billing', label: 'Billing', icon: null }] : []),
        ...((isSolo && hasInClinic && hasMedicalInventory) ? [{ id: 'inventory', label: 'Medicine Inventory', icon: null }] : []),
        ...(isSolo && hasHospitalInventory ? [{ id: 'hospital-inventory', label: 'Hospital Inventory', icon: null }] : []),
    ];

    // Fallback if the URL parameter tab is not currently valid/visible
    useEffect(() => {
        const isValidTab = tabs.some(t => t.id === activeTab);
        if (!isValidTab) {
            setActiveTab('overview');
        }
    }, [user, activeTab, tabs]);

    const pagination = {
        pageIndex: page - 1, // DataTable expects 0-indexed
        pageSize: ITEMS_PER_PAGE,
        totalItems: totalElements,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage + 1) // DataTable gives 0-indexed
    };

    // Client-side pagination logic for queueEntries
    const sortedQueue = [...queueEntries].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    const consultingEntry = sortedQueue.find(q => q.opd?.status === 'CONSULTING');
    const nextQueuedEntry = sortedQueue.find(q => q.opd?.status === 'QUEUED');
    const nextQueuedToken = nextQueuedEntry ? sortedQueue.indexOf(nextQueuedEntry) + 1 : null;
    const queueTotalPages = Math.ceil(sortedQueue.length / ITEMS_PER_PAGE) || 1;
    const paginatedQueue = sortedQueue.slice((page - 1) * ITEMS_PER_PAGE, page * ITEMS_PER_PAGE);

    const queuePagination = {
        pageIndex: page - 1,
        pageSize: ITEMS_PER_PAGE,
        totalItems: sortedQueue.length,
        pageCount: queueTotalPages,
        onPageChange: (newPage) => setPage(newPage + 1)
    };

    // Consultation Modal
    const [consultationModal, setConsultationModal] = useState({ isOpen: false, appointment: null, patient: null, opd: null });
    const [checkoutPrintModal, setCheckoutPrintModal] = useState({
        isOpen: false,
        opdId: null,
        billId: null,
        appointmentId: null,
        patientName: '',
        patientId: ''
    });

    const handleConsultClick = (appointment) => {
        setConsultationModal({ isOpen: true, appointment });
    };

    const openPdfInNewTab = (endpointPath) => {
        const token = sessionStorage.getItem('token');
        const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
        const separator = endpointPath.includes('?') ? '&' : '?';
        const url = `${baseUrl}${endpointPath}${separator}token=${encodeURIComponent(token)}`;
        const win = window.open(url, '_blank');
        if (!win || win.closed || typeof win.closed === 'undefined') {
            return false;
        }
        return true;
    };

    const handlePrintPrescription = (appointmentId) => {
        openPdfInNewTab(`/hospital/doctors/prescription/${appointmentId}/pdf`);
    };

    const handlePrintOpd = (opd) => {
        openPdfInNewTab(`/hospital/opd/${opd.id}/pdf`);
    };

    const handleDownloadPatientsReport = () => {
        let endpoint = `/hospital/patients/report/pdf`;
        if (patientTabView === 'Date' && patientDateFilter) {
            endpoint += `?date=${patientDateFilter}`;
        }
        openPdfInNewTab(endpoint);
    };

    const handleDownloadOpdReport = () => {
        let endpoint = `/hospital/opd/report/pdf`;
        const params = [];
        if (opdTabView === 'Live') {
            params.push(`date=${todayStr}`);
            params.push(`status=QUEUED`);
            params.push(`reportType=LIVE`);
        } else {
            if (opdDateFilter) {
                params.push(`date=${opdDateFilter}`);
            }
            params.push(`reportType=DATE`);
        }
        if (params.length > 0) {
            endpoint += `?${params.join('&')}`;
        }
        openPdfInNewTab(endpoint);
    };

    const handlePrintPrescriptionOpd = (opd) => {
        openPdfInNewTab(`/hospital/doctors/prescription/opd/${opd.id}/pdf`);
    };

    const handleViewPrescriptionOpd = (opd) => {
        openPdfInNewTab(`/hospital/doctors/prescription/opd/${opd.id}/pdf`);
    };

    const [startingConsultationId, setStartingConsultationId] = useState(null);

    // Patient consultation handlers
    const handleStartConsultation = async (patient) => {
        if (startingConsultationId) return;
        setStartingConsultationId(patient.publicId || patient.id);
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
        } finally {
            setStartingConsultationId(null);
        }
    };

    const handleStartOpdConsultation = async (opd) => {
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
        setConsultationModal({ isOpen: true, patient });
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

    const getGreeting = () => {
        const h = new Date().getHours();
        if (h < 12) return 'Good morning';
        if (h < 17) return 'Good afternoon';
        return 'Good evening';
    };

    const getDoctorFirstName = () => {
        const name = user?.name || user?.username || 'Doctor';
        const parts = name.trim().split(/\s+/);
        const first = parts[0].toLowerCase().replace('.', '') === 'dr' ? (parts[1] || parts[0]) : parts[0];
        return `Dr. ${first}`;
    };

    const todayLabel = new Date().toLocaleDateString('en-IN', {
        weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });

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

            {/* Main Content Wrapper */}
            <div className="flex-1 flex flex-col overflow-hidden">
                {/* Navbar */}
                 <Navbar
                    title={tabs.find(t => t.id === activeTab)?.label}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => setProfileOpen(true)}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                {/* Main Content Area */}
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-white p-8">

                    {/* Overview Tab */}
                    {activeTab === 'overview' && !loading && (
                        <div className="space-y-6">
                            {/* Greeting Header */}
                            <div>
                                <h2 className="text-2xl font-bold text-gray-900">
                                    {getGreeting()}, {getDoctorFirstName()} 👋
                                </h2>
                                <p className="text-sm text-gray-500 mt-1">Doctor · {todayLabel}</p>
                            </div>
                            {lowStockItems.length > 0 && (
                                <div className="bg-amber-50 border border-amber-200/80 rounded-2xl p-4 flex items-start gap-3 shadow-sm hover:shadow transition-all duration-300 animate-fade-in">
                                    <div className="p-2 bg-amber-100 text-amber-800 rounded-xl">
                                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                        </svg>
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <h3 className="text-sm font-bold text-amber-900">Low Stock Alert: {lowStockItems.length} items require restocking</h3>
                                        <p className="text-xs text-amber-700/90 mt-1 leading-relaxed">
                                            The physical stock levels for these items are below reorder thresholds:
                                        </p>
                                        <div className="flex flex-wrap gap-2 mt-2">
                                            {lowStockItems.map(item => (
                                                <span key={item.id} className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200/40">
                                                    {item.name} <span className="font-bold">({item.stockQuantity} left)</span>
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                    {user?.receptionMode === 'SOLO' && (
                                        <div className="flex flex-col sm:flex-row gap-2 shrink-0">
                                            {lowStockItems.some(item => item.isMedicine) && (
                                                <button 
                                                    onClick={() => setActiveTab('inventory')}
                                                    className="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold rounded-lg transition-all whitespace-nowrap"
                                                >
                                                    Restock Medicines
                                                </button>
                                            )}
                                            {lowStockItems.some(item => item.isHospitalItem) && (
                                                <button 
                                                    onClick={() => setActiveTab('hospital-inventory')}
                                                    className="px-3 py-1.5 bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold rounded-lg transition-all whitespace-nowrap"
                                                >
                                                    Restock Hospital Inventory
                                                </button>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )}
                            {/* Stat Cards */}
                            <div className={`grid grid-cols-1 gap-4 ${hasIPD ? 'md:grid-cols-4' : 'md:grid-cols-3'}`}>
                                <button
                                    onClick={() => setActiveTab('opd')}
                                    className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md hover:border-blue-200 transition-all group"
                                >
                                    <p className="text-sm font-medium text-gray-500 group-hover:text-blue-600 transition-colors">Today's OPD</p>
                                    <p className="text-3xl font-bold text-gray-900 mt-1">{stats.today}</p>
                                    <p className="text-xs text-blue-500 mt-2 font-medium">View OPD →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('queue')}
                                    className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md transition-all group"
                                >
                                    <p className="text-sm font-medium text-gray-500 group-hover:text-orange-600 transition-colors">In Queue</p>
                                    <p className={`text-3xl font-bold mt-1 ${queueEntries.length > 10 ? 'text-orange-600' : 'text-gray-900'}`}>
                                        {queueEntries.length}
                                    </p>
                                    <p className="text-xs text-gray-400 mt-2 font-medium">View Queue →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md transition-all group"
                                >
                                    <p className="text-sm font-medium text-gray-500 group-hover:text-red-600 transition-colors">Pending</p>
                                    <p className={`text-3xl font-bold mt-1 ${stats.pending > 5 ? 'text-red-600' : stats.pending === 0 ? 'text-green-600' : 'text-gray-900'}`}>
                                        {stats.pending}
                                    </p>
                                    <p className="text-xs text-gray-400 mt-2 font-medium">View Appointments →</p>
                                </button>
                                {hasIPD && (
                                    <button
                                        onClick={() => setActiveTab('ipd')}
                                        className="bg-white rounded-2xl border border-gray-200 p-6 text-left hover:shadow-md hover:border-blue-200 transition-all group"
                                    >
                                        <p className="text-sm font-medium text-gray-500 group-hover:text-blue-600 transition-colors">IPD Patients</p>
                                        <p className="text-3xl font-bold text-gray-900 mt-1">{ipdCount}</p>
                                        <p className="text-xs text-blue-500 mt-2 font-medium">View IPD →</p>
                                    </button>
                                )}
                            </div>

                            {/* Quick Actions */}
                            <div className="flex flex-wrap gap-3">
                                <button
                                    onClick={() => setActiveTab('opd')}
                                    className="inline-flex items-center gap-2 px-5 py-2.5 bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    Start OPD →
                                </button>
                                {hasIPD && (
                                    <button
                                        onClick={() => setActiveTab('ipd')}
                                        className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                    >
                                        Continue IPD →
                                    </button>
                                )}
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="inline-flex items-center gap-2 px-5 py-2.5 bg-white hover:bg-gray-50 text-gray-700 text-sm font-semibold rounded-xl border border-gray-200 transition-all shadow-sm active:scale-95"
                                >
                                    View Appointments
                                </button>
                                {user?.receptionMode === 'SOLO' && (
                                    <button
                                        onClick={() => setIsAddPatientModalOpen(true)}
                                        className="inline-flex items-center gap-2 px-5 py-2.5 bg-white hover:bg-gray-50 text-gray-700 text-sm font-semibold rounded-xl border border-gray-200 transition-all shadow-sm active:scale-95"
                                    >
                                        + Add Patient
                                    </button>
                                )}
                            </div>

                            {/* Side-by-Side Lists: Appointments and Queue */}
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mt-8">
                                {/* Left Div: Appointments */}
                                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm flex flex-col h-[650px] overflow-hidden">
                                    <div className="p-6 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-gradient-to-r from-gray-50/50 to-white">
                                        <div>
                                            <h3 className="text-lg font-bold text-gray-955">Appointments</h3>
                                            <p className="text-xs text-gray-500 mt-0.5">Manage scheduled clinical slots</p>
                                        </div>
                                        {user?.receptionMode === 'SOLO' && (
                                            <button
                                                onClick={() => setIsAddModalOpen(true)}
                                                className="inline-flex items-center justify-center gap-1.5 px-3 py-1.5 bg-gray-900 hover:bg-gray-800 text-white text-xs font-semibold rounded-xl transition-all shadow-sm active:scale-95 cursor-pointer animate-fade-in"
                                            >
                                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                                                </svg>
                                                Add Appointment
                                            </button>
                                        )}
                                    </div>

                                    {/* Appointment Controls */}
                                    <div className="px-6 py-3 bg-gray-50/30 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                                        <div className="relative flex-1 max-w-xs">
                                            <span className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none text-gray-400">
                                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                                </svg>
                                            </span>
                                            <input
                                                type="text"
                                                placeholder="Search appointments..."
                                                value={searchTerm}
                                                onChange={(e) => setSearchTerm(e.target.value)}
                                                className="w-full pl-9 pr-4 py-1.5 text-xs bg-white border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-gray-900/10 focus:border-gray-900 transition-all"
                                            />
                                        </div>
                                        <div className="flex bg-gray-100 rounded-xl p-1 border border-gray-200">
                                            {['today', 'upcoming', 'history'].map(view => (
                                                <button
                                                    key={view}
                                                    onClick={() => setViewFilter(view)}
                                                    className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer ${viewFilter === view
                                                        ? 'bg-white text-gray-900 shadow-sm border border-gray-100'
                                                        : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                                        }`}
                                                >
                                                    {view.charAt(0).toUpperCase() + view.slice(1)}
                                                </button>
                                            ))}
                                        </div>
                                    </div>

                                    {/* Scrollable Container */}
                                    <div className="flex-1 overflow-auto p-6 space-y-6">
                                        {/* Appointments Section */}
                                        <div>
                                            <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                                                <span className="w-1.5 h-3 bg-gray-950 rounded-full"></span>
                                                Today's Appointments
                                            </h4>
                                            {appointments.length > 0 ? (
                                                <DoctorAppointmentsTable
                                                    appointments={appointments}
                                                    onStatusUpdate={handleStatusUpdate}
                                                    onEdit={handleEditClick}
                                                    onConsult={handleConsultClick}
                                                    onPrint={handlePrintPrescription}
                                                    onAuditHistory={(item) => handleAuditHistory('APPOINTMENT', item.publicId || item.id, "Appointment")}
                                                    onAdmitToIpd={(appt) => setConsultationModal({ isOpen: true, appointment: appt })}
                                                    hasIPD={hasIPD}
                                                    startIndex={0}
                                                    pagination={null}
                                                />
                                            ) : (
                                                <EmptyState
                                                    icon={null}
                                                    title="No Appointments"
                                                    message="No appointments scheduled for today."
                                                />
                                            )}
                                        </div>

                                        {/* Follow-ups Section */}
                                        <div className="border-t border-gray-100 pt-6">
                                            <h4 className="text-xs font-bold text-indigo-500 uppercase tracking-wider mb-3 flex items-center gap-2">
                                                <span className="w-1.5 h-3 bg-indigo-600 rounded-full"></span>
                                                Today's Follow-Ups
                                            </h4>
                                            {todaysFollowUps && todaysFollowUps.length > 0 ? (
                                                <div className="overflow-x-auto border border-gray-100 rounded-xl">
                                                     <table className="w-full text-sm text-left">
                                                         <thead>
                                                             <tr className="bg-gray-50 border-b border-gray-100">
                                                                 <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Patient ID</th>
                                                                 <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Patient Name</th>
                                                                 <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Diagnosis / Reason</th>
                                                             </tr>
                                                         </thead>
                                                         <tbody className="divide-y divide-gray-50">
                                                             {todaysFollowUps.map((record) => (
                                                                 <tr key={record.id} className="hover:bg-gray-50/40 transition-colors">
                                                                     <td className="px-4 py-3.5 text-xs font-bold text-indigo-600">
                                                                         {record.patientCustomId || record.patientPublicId || '-'}
                                                                     </td>
                                                                     <td className="px-4 py-3.5 text-sm font-semibold text-gray-900">
                                                                         {record.patientName || '-'}
                                                                     </td>
                                                                     <td className="px-4 py-3.5 text-sm text-gray-500 italic">
                                                                         {record.diagnosis || 'Follow-up'}
                                                                     </td>
                                                                 </tr>
                                                             ))}
                                                         </tbody>
                                                     </table>
                                                 </div>
                                            ) : (
                                                <div className="text-center py-8 border border-dashed border-gray-200 rounded-xl">
                                                    <p className="text-sm text-gray-400 font-medium">No follow-ups scheduled for today.</p>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>

                                {/* Right Div: Queue */}
                                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm flex flex-col h-[650px] overflow-hidden">
                                    <div className="p-6 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-gradient-to-r from-gray-50/50 to-white">
                                        <div>
                                            <h3 className="text-lg font-bold text-gray-955">Queue</h3>
                                            <p className="text-xs text-gray-500 mt-0.5">Real-time OPD patient workflow</p>
                                        </div>
                                        {user?.receptionMode === 'SOLO' && (
                                            <button
                                                onClick={() => setIsOpdModalOpen(true)}
                                                className="inline-flex items-center justify-center gap-1.5 px-3 py-1.5 bg-sky-600 hover:bg-sky-700 text-white text-xs font-semibold rounded-xl transition-all shadow-sm active:scale-95 cursor-pointer animate-fade-in"
                                            >
                                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                                                </svg>
                                                OPD Intake
                                            </button>
                                        )}
                                    </div>

                                    {/* Queue Content */}
                                    <div className="flex-1 overflow-auto p-6">
                                        {queueEntries.length > 0 ? (
                                            <div className="overflow-x-auto border border-gray-100 rounded-xl">
                                                <table className="w-full text-sm text-left">
                                                    <thead>
                                                        <tr className="bg-gray-50 border-b border-gray-100">
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">S.No.</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Token</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Patient Name</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Time</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Action</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody className="divide-y divide-gray-50">
                                                        {[...queueEntries]
                                                            .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
                                                            .slice(0, 10)
                                                            .map((q, idx) => (
                                                                <tr key={q.id} className="hover:bg-gray-50/40 transition-colors">
                                                                    <td className="px-4 py-3.5 text-sm text-gray-700 font-medium">{idx + 1}</td>
                                                                    <td className="px-4 py-3.5">
                                                                        <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700 border border-indigo-100/30">
                                                                            #{idx + 1}
                                                                        </span>
                                                                    </td>
                                                                    <td className="px-4 py-3.5 text-sm font-semibold text-gray-900">{q.opd?.patient?.name || q.opd?.patientName || '-'}</td>
                                                                    <td className="px-4 py-3.5 text-xs text-gray-400">
                                                                        {new Date(q.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                                    </td>
                                                                    <td className="px-4 py-3.5 text-xs">
                                                                        {q.opd?.status === 'QUEUED' ? (
                                                                            <button
                                                                                onClick={() => handleStartOpdConsultation(q.opd)}
                                                                                disabled={!!startingConsultationId}
                                                                                className={`px-3 py-1 font-semibold rounded-lg transition-colors cursor-pointer ${startingConsultationId ? 'bg-gray-300 text-gray-500 cursor-not-allowed' : 'bg-gray-900 hover:bg-gray-800 text-white'}`}
                                                                            >
                                                                                Consult
                                                                            </button>
                                                                        ) : (
                                                                            <span className="text-gray-400 italic">Consulting</span>
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
                                                title="Queue Empty"
                                                message="No queued OPD cases for today."
                                            />
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Standardized Header - Hide on overview, inventory and hospital-inventory tabs */}
                    {activeTab !== 'overview' && activeTab !== 'inventory' && activeTab !== 'hospital-inventory' && (
                    <PageHeader
                        title={
                            activeTab === 'appointments' ? 'My Appointments' : 
                            activeTab === 'opd' ? 'OPD Cases' :
                            activeTab === 'queue' ? 'Patient Queue' :
                            activeTab === 'billing' ? 'Billing Records' :
                            'My Patients'
                        }
                        subtitle={`Manage your ${
                            activeTab === 'appointments' ? 'schedule' : 
                            activeTab === 'opd' ? 'OPD cases' :
                            activeTab === 'queue' ? 'patient queue' :
                            activeTab === 'billing' ? 'bills' :
                            'patients'
                        } here.`}
                        onSearch={(e) => setSearchTerm(e.target.value)}
                        searchValue={searchTerm}
                        searchPlaceholder={`Search ${activeTab}...`}
                        onAdd={(user?.receptionMode === 'SOLO' && (activeTab === 'patients' || activeTab === 'opd' || activeTab === 'appointments')) ? () => {
                            if (activeTab === 'patients') setIsAddPatientModalOpen(true);
                            if (activeTab === 'opd') setIsOpdModalOpen(true);
                            if (activeTab === 'appointments') setIsAddModalOpen(true);
                        } : null}
                        addLabel={activeTab === 'patients' ? 'Register Patient' : activeTab === 'opd' ? 'New OPD Case' : 'Add Appointment'}
                        filter={
                            activeTab === 'appointments' ? (
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
                            ) : activeTab === 'billing' ? (
                                <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200">
                                    {['PENDING', 'PAID', 'PARTIAL'].map(status => (
                                        <button
                                            key={status}
                                            onClick={() => { setBillingStatus(status); setPage(1); }}
                                            className={`px-4 py-1.5 text-sm font-medium rounded-md transition-all ${billingStatus === status
                                                ? 'bg-white text-primary-600 shadow-sm border border-gray-100'
                                                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                                }`}
                                        >
                                            {status.charAt(0) + status.slice(1).toLowerCase()}
                                        </button>
                                    ))}
                                </div>
                            ) : activeTab === 'patients' ? (
                                <div className="flex items-center gap-2">
                                    <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200 h-[38px] items-center">
                                        {['All', 'Date'].map(view => (
                                            <button
                                                key={view}
                                                type="button"
                                                onClick={() => {
                                                    setPatientTabView(view);
                                                    setPage(1);
                                                    setSearchTerm('');
                                                }}
                                                className={`px-4 py-1 text-sm font-medium rounded-md transition-all ${patientTabView === view
                                                    ? 'bg-white text-sky-600 shadow-sm border border-gray-100 font-semibold'
                                                    : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                                    }`}
                                            >
                                                {view}
                                            </button>
                                        ))}
                                    </div>
                                    {patientTabView === 'Date' && (
                                        <input
                                            type="date"
                                            value={patientDateFilter}
                                            onChange={(e) => { setPatientDateFilter(e.target.value); setPage(1); }}
                                            className="px-4 py-1.5 border border-neutral-300 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-transparent bg-white text-slate-800 h-[38px]"
                                        />
                                    )}
                                    <button
                                        type="button"
                                        onClick={handleDownloadPatientsReport}
                                        disabled={loading}
                                        className="bg-sky-600 hover:bg-sky-700 disabled:bg-gray-300 disabled:cursor-not-allowed text-white px-4 py-1.5 rounded-lg text-sm font-semibold shadow-sm transition flex items-center gap-1.5 h-[38px]"
                                    >
                                        <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                        </svg>
                                        <span>Download PDF</span>
                                    </button>
                                </div>
                            ) : activeTab === 'opd' ? (
                                <div className="flex items-center gap-2">
                                    <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200 h-[38px] items-center">
                                        {['Live', 'Date'].map(view => (
                                            <button
                                                key={view}
                                                type="button"
                                                onClick={() => {
                                                    setOpdTabView(view);
                                                    setPage(1);
                                                    setSearchTerm('');
                                                }}
                                                className={`px-4 py-1 text-sm font-medium rounded-md transition-all ${opdTabView === view
                                                    ? 'bg-white text-sky-600 shadow-sm border border-gray-100 font-semibold'
                                                    : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                                    }`}
                                            >
                                                {view}
                                            </button>
                                        ))}
                                    </div>
                                    {opdTabView === 'Date' && (
                                        <input
                                            type="date"
                                            value={opdDateFilter}
                                            onChange={(e) => { setOpdDateFilter(e.target.value); setPage(1); }}
                                            className="px-4 py-1.5 border border-neutral-300 rounded-lg text-sm focus:ring-2 focus:ring-sky-500 focus:border-transparent bg-white text-slate-800 h-[38px]"
                                        />
                                    )}
                                    <button
                                        type="button"
                                        onClick={handleDownloadOpdReport}
                                        disabled={loading}
                                        className="bg-sky-600 hover:bg-sky-700 disabled:bg-gray-300 disabled:cursor-not-allowed text-white px-4 py-1.5 rounded-lg text-sm font-semibold shadow-sm transition flex items-center gap-1.5 h-[38px]"
                                    >
                                        <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                        </svg>
                                        <span>Download PDF</span>
                                    </button>
                                </div>
                            ) : null
                        }
                    />
                    )}

                    {loading && (
                        <div className="space-y-8 animate-fade-in-up">
                            {activeTab === 'overview' ? (
                                <>
                                    <SkeletonStatsGrid count={5} gridCols="md:grid-cols-5" />
                                    <SkeletonOverviewDual />
                                </>
                            ) : (
                                <SkeletonTable rows={6} cols={5} />
                            )}
                        </div>
                    )}

                    {!loading && activeTab !== 'overview' && (
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
                                            onAdmitToIpd={(appt) => setConsultationModal({ isOpen: true, appointment: appt })}
                                            hasIPD={hasIPD}
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
                                        <DoctorOpdTable
                                            opds={opds}
                                            queueEntries={queueEntries}
                                            onPrintOpd={handlePrintOpd}
                                            onStartConsultation={handleStartOpdConsultation}
                                            onPrintPrescription={handlePrintPrescriptionOpd}
                                            onViewPrescription={handleViewPrescriptionOpd}
                                            onAdmitIpd={(opd) => { setIpdOpdForAdmit(opd); setIsIpdAdmitOpen(true); }}
                                            user={user}
                                            hasIPD={hasIPD}
                                            startIndex={(page - 1) * ITEMS_PER_PAGE}
                                            pagination={pagination}
                                        />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title={opdTabView === 'Live' ? "No Active OPD Patients" : "No OPD Records Found"}
                                            message={opdTabView === 'Live' ? "No patients are currently in the queue or being consulted." : `No OPD registrations found on ${opdDateFilter}.`}
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
                                                            return (
                                                                <tr key={row.ipdId || row.id || ipdNumber || idx} className="border-t">
                                                                    <td className="px-4 py-3">{(page - 1) * ITEMS_PER_PAGE + idx + 1}</td>
                                                                    <td className="px-4 py-3">{ipdNumber || row.id}</td>
                                                                    <td className="px-4 py-3">{patientName}</td>
                                                                    <td className="px-4 py-3">{doctorName}</td>
                                                                    <td className="px-4 py-3">{wardName}</td>
                                                                    <td className="px-4 py-3">{bedNumber}</td>
                                                                    <td className="px-4 py-3">{admittedAt ? new Date(admittedAt).toLocaleString() : '-'}</td>
                                                                    <td className="px-4 py-3">{status}</td>
                                                                    <td className="px-4 py-3">
                                                                        {(() => {
                                                                            const theId = row.ipdId || row.id || row.ipd?.id || row.ipd?.ipdId || null;
                                                                                return (
                                                                                <button
                                                                                    className={`px-3 py-1 rounded ${theId ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-500 cursor-not-allowed'}`}
                                                                                    onClick={() => { if (theId) navigate(`/ipd/${theId}`) }}
                                                                                    disabled={!theId}
                                                                                    title={theId ? 'Open IPD case' : 'IPD id not available'}
                                                                                >
                                                                                    Open Case
                                                                                </button>
                                                                            );
                                                                        })()}
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
                                            message="You have no active IPD admissions."
                                        />
                                    )
                                )}

                                {activeTab === 'queue' && (
                                    queueEntries.length > 0 ? (
                                        <DoctorQueueTable
                                            queueEntries={paginatedQueue}
                                            startIndex={(page - 1) * ITEMS_PER_PAGE}
                                            pagination={queuePagination}
                                        />
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
                                            title={patientTabView === 'Date' ? "No Registered Patients" : "No Patients"}
                                            message={patientTabView === 'Date' ? `No patients registered on ${patientDateFilter}.` : "No patients found."}
                                        />
                                    )
                                )}

                                {activeTab === 'billing' && (
                                    billing.length === 0 ? (
                                        <EmptyState
                                            icon={null}
                                            title="No Billing Records"
                                            message="No bills found."
                                        />
                                    ) : (
                                        <BillingTable
                                            billing={billing}
                                            startIndex={(page - 1) * ITEMS_PER_PAGE}
                                            pagination={pagination}
                                            onUpdateStatus={handleBillStatus}
                                            onPrint={handlePrintReceipt}
                                            printingBillId={docPrintingId}
                                            onEditItems={handleOpenEditBillItems}
                                        />
                                    )
                                )}

                                {activeTab === 'inventory' && (
                                    <MedicineInventoryTab />
                                )}

                                {activeTab === 'hospital-inventory' && (
                                    <HospitalInventoryTab />
                                )}
                            </div>
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
                    onClose={() => setConsultationModal({ isOpen: false, appointment: null, patient: null, opd: null })}
                    onSuccess={async (res) => {
                        setConsultationModal({ isOpen: false, appointment: null, patient: null, opd: null });
                        loadData();
                        
                        if (res && typeof res === 'object') {
                            const opdId = res.opdId || res.opd?.id;
                            const billId = res.billId;

                            if (opdId) {
                                let blockedCount = 0;

                                // 1. Case Paper / Consultation Print (always)
                                const p1 = openPdfInNewTab(`/hospital/opd/${opdId}/pdf`);
                                if (!p1) blockedCount++;

                                // 2. Prescription Print (only if hasPrescription is true)
                                if (res.hasPrescription) {
                                    const p2 = openPdfInNewTab(`/hospital/doctors/prescription/opd/${opdId}/pdf`);
                                    if (!p2) blockedCount++;
                                }

                                // 3. Bill / Invoice Print (always if billId is present)
                                if (billId) {
                                    const p3 = openPdfInNewTab(`/hospital/billing/${billId}/pdf`);
                                    if (!p3) blockedCount++;
                                }

                                // 4. In-Clinic Medicines Print (only if hasAdministered is true)
                                if (res.hasAdministered) {
                                    const p4 = openPdfInNewTab(`/hospital/patients/opd/${opdId}/medicines/pdf`);
                                    if (!p4) blockedCount++;
                                }

                                if (blockedCount > 0) {
                                    toastError("Some print windows were blocked. Please select 'Always allow pop-ups' in your browser address bar to enable automatic printing of all documents.");
                                } else {
                                    success("Consultation completed successfully! Documents sent to print.");
                                }
                            } else {
                                success("Consultation completed successfully!");
                            }
                        } else {
                            success(res || "Consultation completed successfully!");
                        }
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

                {isAddModalOpen && (
                    <AppointmentModal
                        isOpen={isAddModalOpen}
                        onClose={() => setIsAddModalOpen(false)}
                        onSuccess={loadData}
                        doctors={doctorRecord ? [doctorRecord] : [{ id: user?.id, name: user?.name }]}
                        patients={patients}
                    />
                )}

                {isAddPatientModalOpen && (
                    <PatientModal
                        isOpen={isAddPatientModalOpen}
                        onClose={() => setIsAddPatientModalOpen(false)}
                        onSuccess={loadData}
                    />
                )}

                {patientDetailsModal.isOpen && (
                    <PatientDetailsModal
                        patient={patientDetailsModal.patient}
                        onClose={() => setPatientDetailsModal({ isOpen: false, patient: null })}
                    />
                )}

                {/* Admit to IPD directly from Appointment - opens consultation modal so doctor fills data first */}
                {/* (ipdFromAppointment state opens the existing consultationModal with the appointment) */}

                {isOpdModalOpen && (
                    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                        <div className="bg-white rounded-2xl shadow-xl w-full max-w-3xl animate-scale-in overflow-hidden max-h-[90vh]">
                            <div className="bg-white px-8 py-6 border-b border-gray-200">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <h3 className="text-2xl font-bold text-neutral-800">OPD Queue Intake</h3>
                                        <p className="text-sm text-neutral-600 mt-1">Capture vitals and check-in patient to queue</p>
                                    </div>
                                    <button onClick={() => setIsOpdModalOpen(false)} className="w-10 h-10 rounded-xl bg-white/80 hover:bg-white flex items-center justify-center text-neutral-400 hover:text-neutral-600 cursor-pointer border-0">
                                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                        </svg>
                                    </button>
                                </div>
                            </div>
                            <form onSubmit={async (e) => {
                                e.preventDefault();
                                if (!opdForm.patientId) {
                                    toastError('Please select a valid patient from the suggestions');
                                    return;
                                }
                                try {
                                    const payload = {
                                        patientId: opdForm.patientId,
                                        doctorId: opdForm.doctorId || doctorRecord?.id || user?.id,
                                        bp: opdForm.bp,
                                        temperature: opdForm.temperature ? parseFloat(opdForm.temperature) : null,
                                        pulse: opdForm.pulse ? parseInt(opdForm.pulse) : null,
                                        weight: opdForm.weight ? parseFloat(opdForm.weight) : null,
                                        spo2: opdForm.spo2 ? parseInt(opdForm.spo2) : null,
                                        problem: opdForm.problem,
                                        visitType: opdForm.visitType
                                    };
                                    const res = await hospitalService.createOpd(payload);
                                    setIsOpdModalOpen(false);
                                    success('OPD Case created successfully — ID: ' + res.caseId);
                                    loadData();
                                } catch (err) {
                                    console.error('Failed to create OPD', err);
                                    toastError('Failed to create OPD');
                                }
                            }} className="p-6 space-y-4 max-h-[76vh] overflow-auto">
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <div className="relative">
                                        <label className="block text-sm font-semibold text-neutral-700 mb-2">Patient <span className="text-red-600">*</span></label>
                                        <div className="relative">
                                            <span className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-neutral-400">
                                                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                                </svg>
                                            </span>
                                            <input
                                                type="text"
                                                className="w-full border border-gray-300 rounded-xl pl-10 pr-4 py-2 focus:ring-2 focus:ring-primary-500 text-sm focus:border-transparent text-slate-800"
                                                value={patientSearchText}
                                                onChange={(e) => {
                                                    const val = e.target.value;
                                                    setPatientSearchText(val);
                                                    setShowPatientDropdown(true);
                                                    setOpdForm(prev => ({ ...prev, patientId: null }));
                                                }}
                                                onFocus={() => setShowPatientDropdown(true)}
                                                onBlur={() => {
                                                    setTimeout(() => {
                                                        setShowPatientDropdown(false);
                                                    }, 250);
                                                }}
                                                placeholder="Type patient name to search..."
                                                autoComplete="off"
                                            />
                                        </div>
                                        
                                        {showPatientDropdown && patientSearchText.trim().length >= 2 && (
                                            <div className="absolute left-0 right-0 mt-1 bg-white border border-neutral-200 rounded-xl shadow-xl z-50 max-h-60 overflow-y-auto divide-y divide-neutral-100">
                                                {patients.filter(p => p.name?.toLowerCase().includes(patientSearchText.toLowerCase())).length > 0 ? (
                                                    patients.filter(p => p.name?.toLowerCase().includes(patientSearchText.toLowerCase())).map(p => (
                                                        <button
                                                            type="button"
                                                            key={p.id}
                                                            onClick={() => {
                                                                setOpdForm(prev => ({ ...prev, patientId: p.id }));
                                                                setPatientSearchText(`${p.name}${p.phone ? ` (${p.phone})` : ''}`);
                                                                setShowPatientDropdown(false);
                                                            }}
                                                            className="w-full px-4 py-3 hover:bg-neutral-50 cursor-pointer transition-colors duration-150 flex flex-col gap-0.5 text-left border-0"
                                                        >
                                                            <span className="font-semibold text-neutral-800 text-sm">{p.name}</span>
                                                            <span className="text-xs text-neutral-500">{p.phone ? `📞 ${p.phone}` : ''} | {p.age} Yrs | {p.gender}</span>
                                                        </button>
                                                    ))
                                                ) : (
                                                    <div className="px-4 py-4 text-sm text-neutral-500 text-center">
                                                        No matching patients found
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                    <div>
                                        <label className="block text-sm font-semibold text-neutral-700 mb-2">Doctor</label>
                                        <div className="w-full px-4 py-2.5 bg-neutral-50 border border-neutral-200 text-neutral-850 rounded-xl text-sm font-semibold flex items-center justify-between">
                                            <span>{user?.name || 'Self'}</span>
                                            <span className="text-xs px-2 py-0.5 bg-green-50 text-green-700 border border-green-200 rounded-full font-medium">Assigned</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-semibold text-neutral-700 mb-2">BP</label>
                                        <input className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={opdForm.bp} onChange={(e) => setOpdForm(prev => ({ ...prev, bp: e.target.value }))} placeholder="120/80" />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-semibold text-neutral-700 mb-2">Temperature (°C)</label>
                                        <input type="number" step="0.1" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={opdForm.temperature} onChange={(e) => setOpdForm(prev => ({ ...prev, temperature: e.target.value }))} />
                                    </div>
                                </div>
                                <div className="grid grid-cols-3 gap-4">
                                    <div>
                                        <label className="block text-sm font-semibold text-neutral-700 mb-2">Pulse</label>
                                        <input type="number" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={opdForm.pulse} onChange={(e) => setOpdForm(prev => ({ ...prev, pulse: e.target.value }))} />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-semibold text-neutral-700 mb-2">Weight (kg)</label>
                                        <input type="number" step="0.1" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={opdForm.weight} onChange={(e) => setOpdForm(prev => ({ ...prev, weight: e.target.value }))} />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-semibold text-neutral-700 mb-2">SpO2 (%)</label>
                                        <input type="number" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={opdForm.spo2} onChange={(e) => setOpdForm(prev => ({ ...prev, spo2: e.target.value }))} />
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Problem / Reason</label>
                                    <textarea rows={3} className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800 resize-none" value={opdForm.problem} onChange={(e) => setOpdForm(prev => ({ ...prev, problem: e.target.value }))} />
                                </div>

                                <div className="flex items-center gap-4">
                                    <label className="text-sm font-medium">Visit Type:</label>
                                    <label className="inline-flex items-center gap-2 cursor-pointer"><input type="radio" name="visitType" value="NEW" checked={opdForm.visitType === 'NEW'} onChange={() => setOpdForm(prev => ({ ...prev, visitType: 'NEW' }))} /> New</label>
                                    <label className="inline-flex items-center gap-2 cursor-pointer"><input type="radio" name="visitType" value="FOLLOWUP" checked={opdForm.visitType === 'FOLLOWUP'} onChange={() => setOpdForm(prev => ({ ...prev, visitType: 'FOLLOWUP' }))} /> Follow-up</label>
                                </div>

                                <div className="flex gap-4 pt-4">
                                    <button type="button" onClick={() => setIsOpdModalOpen(false)} className="flex-1 py-2.5 rounded-xl border border-gray-300 font-semibold text-gray-700 hover:bg-gray-50 transition">Cancel</button>
                                    <button type="submit" className="flex-1 py-2.5 rounded-xl bg-slate-900 text-white font-semibold hover:bg-slate-850 transition">Create OPD Case</button>
                                </div>
                            </form>
                        </div>
                    </div>
                )}

                {/* Payment Modal */}
                {paymentModal.isOpen && (
                    <div className="fixed inset-0 z-50 overflow-y-auto">
                        <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
                            <div className="fixed inset-0 transition-opacity" aria-hidden="true">
                                <div className="absolute inset-0 bg-gray-500 opacity-75" onClick={() => setPaymentModal({ ...paymentModal, isOpen: false })}></div>
                            </div>
                            <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>
                            <div className="inline-block align-bottom bg-white rounded-2xl text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full">
                                <div className="bg-white px-8 py-6 border-b border-gray-100">
                                    <h3 className="text-xl font-bold text-gray-900">Process Payment</h3>
                                    <p className="text-xs text-gray-500 mt-1">Settle invoice for patient {paymentModal.patientName}</p>
                                </div>
                                <div className="p-6 space-y-4">
                                    <div className="flex justify-between items-center bg-gray-50 p-4 rounded-xl">
                                        <span className="text-sm font-semibold text-gray-700">Amount Due:</span>
                                        <span className="text-xl font-bold text-gray-900">₹{paymentModal.amount?.toFixed(2)}</span>
                                    </div>
                                    <div className="grid grid-cols-2 gap-4">
                                        <button
                                            onClick={() => handleProcessPayment('Cash')}
                                            disabled={!!docPaymentProcessing}
                                            className={`px-4 py-3 rounded-xl border transition-all font-bold text-sm flex flex-col items-center gap-1.5 ${docPaymentProcessing ? 'opacity-50 cursor-not-allowed border-gray-200' : 'border-gray-300 hover:bg-gray-50 text-gray-800'}`}
                                        >
                                            {docPaymentProcessing === 'Cash' ? '⏳ Processing...' : '💵 Pay Cash'}
                                        </button>
                                        <button
                                            onClick={() => handleProcessPayment('Online')}
                                            disabled={!!docPaymentProcessing}
                                            className={`px-4 py-3 rounded-xl text-white font-bold text-sm transition-all flex flex-col items-center gap-1.5 ${docPaymentProcessing ? 'bg-gray-400 cursor-not-allowed' : 'bg-sky-600 hover:bg-sky-700'}`}
                                        >
                                            {docPaymentProcessing === 'Online' ? '⏳ Processing...' : '📱 Pay Online (UPI)'}
                                        </button>
                                    </div>
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
                            <div className="inline-block align-bottom bg-white rounded-2xl text-center overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-md sm:w-full p-8">
                                <div className="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto mb-4">
                                    <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                                    </svg>
                                </div>
                                <h3 className="text-xl font-bold text-gray-900 mb-1">Payment Successful!</h3>
                                <p className="text-sm text-gray-500 mb-4">Received ₹{paymentSuccessModal.amount?.toFixed(2)} from {paymentSuccessModal.patientName}</p>
                                <div className="flex gap-4">
                                    <button
                                        onClick={() => {
                                            setPaymentSuccessModal({ isOpen: false, billId: null, patientName: '', amount: 0 });
                                        }}
                                        className="flex-1 py-2.5 rounded-xl border border-gray-300 font-semibold text-gray-750 hover:bg-gray-50 transition border-0 bg-neutral-100"
                                    >
                                        Close
                                    </button>
                                    <button
                                        onClick={() => {
                                            handlePrintReceipt(paymentSuccessModal.billId);
                                            setPaymentSuccessModal({ isOpen: false, billId: null, patientName: '', amount: 0 });
                                        }}
                                        className="flex-1 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-semibold transition"
                                    >
                                        Print Receipt
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Profile Settings Modal */}
                <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />

                {/* Edit Bill Items Modal */}
                {editBillItemsModal.isOpen && (
                    <div className="fixed inset-0 z-50 overflow-y-auto">
                        <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
                            <div className="fixed inset-0 transition-opacity" aria-hidden="true">
                                <div className="absolute inset-0 bg-gray-500 opacity-75" onClick={() => setEditBillItemsModal({ ...editBillItemsModal, isOpen: false })}></div>
                            </div>
                            <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>
                            <div className="inline-block align-bottom bg-white rounded-2xl text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full border border-gray-200">
                                <div className="bg-white px-6 pt-6 pb-4">
                                    <h3 className="text-lg leading-6 font-bold text-gray-900 mb-1">
                                        Edit Bill Charges
                                    </h3>
                                    <p className="text-xs text-gray-500 mb-4">
                                        Patient: <span className="font-semibold text-gray-800">{editBillItemsModal.patientName}</span> | Bill No: <span className="font-semibold text-gray-800">{editBillItemsModal.billNumber}</span>
                                    </p>
                                    
                                    <div className="space-y-4">
                                        <div className="flex justify-between items-center">
                                            <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wide">Clinic Charges & Services</h4>
                                            <button
                                                type="button"
                                                onClick={addBillItem}
                                                className="text-xs font-semibold text-sky-600 hover:text-sky-800"
                                            >
                                                + Add Charge Item
                                            </button>
                                        </div>

                                        <div className="space-y-3 max-h-[250px] overflow-y-auto pr-1">
                                            {editBillItemsModal.items.map((item, index) => (
                                                <div key={index} className="flex gap-2 items-center bg-gray-50 p-3 rounded-xl border border-gray-100">
                                                    <div className="flex-1 space-y-2">
                                                        <div className="flex gap-2">
                                                            <select
                                                                onChange={(e) => {
                                                                    const val = e.target.value;
                                                                    if (val === 'consultation') {
                                                                        updateBillItem(index, 'name', 'Consultation Fee');
                                                                        updateBillItem(index, 'defaultAmount', standardFees.consultationFee || '0');
                                                                    } else if (val === 'case_paper') {
                                                                        updateBillItem(index, 'name', 'Case Paper Fee');
                                                                        updateBillItem(index, 'defaultAmount', standardFees.casePaperFee || '0');
                                                                    } else if (val.startsWith('custom_')) {
                                                                        const customId = parseInt(val.replace('custom_', ''));
                                                                        const found = customFees.find(f => f.id === customId);
                                                                        if (found) {
                                                                            updateBillItem(index, 'name', found.name);
                                                                            updateBillItem(index, 'defaultAmount', found.defaultAmount || '0');
                                                                        }
                                                                    } else if (val === 'injections') {
                                                                        updateBillItem(index, 'name', 'Injections');
                                                                        updateBillItem(index, 'defaultAmount', '0');
                                                                    } else if (val === 'medicines_by_hospital') {
                                                                        updateBillItem(index, 'name', 'Medicines by Hospital');
                                                                        updateBillItem(index, 'defaultAmount', '0');
                                                                    }
                                                                }}
                                                                className="text-xs border border-gray-200 rounded-lg p-1 bg-white focus:ring-1 focus:ring-sky-500"
                                                                defaultValue=""
                                                            >
                                                                <option value="" disabled>-- Select Charge Preset --</option>
                                                                <option value="consultation">Consultation Fee (₹{standardFees.consultationFee || 0})</option>
                                                                <option value="case_paper">Case Paper Fee (₹{standardFees.casePaperFee || 0})</option>
                                                                <option value="injections">Injections</option>
                                                                <option value="medicines_by_hospital">Medicines by Hospital</option>
                                                                {customFees.map(f => (
                                                                    <option key={f.id} value={`custom_${f.id}`}>{f.name} (₹{f.defaultAmount || 0})</option>
                                                                ))}
                                                                <option value="manual">Manual Entry</option>
                                                            </select>
                                                        </div>
                                                        <div className="grid grid-cols-3 gap-2">
                                                            <input
                                                                type="text"
                                                                placeholder="Description"
                                                                value={item.name}
                                                                onChange={(e) => updateBillItem(index, 'name', e.target.value)}
                                                                className="col-span-2 px-3 py-1.5 text-sm border border-gray-200 rounded-lg focus:ring-1 focus:ring-sky-500 focus:border-transparent bg-white font-medium text-gray-900"
                                                            />
                                                            <input
                                                                type="number"
                                                                placeholder="Amount"
                                                                value={item.defaultAmount}
                                                                onChange={(e) => updateBillItem(index, 'defaultAmount', e.target.value)}
                                                                className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg focus:ring-1 focus:ring-sky-500 focus:border-transparent bg-white text-gray-900 font-semibold"
                                                            />
                                                        </div>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        onClick={() => removeBillItem(index)}
                                                        className="text-red-500 hover:text-red-700 p-2 text-lg font-bold"
                                                    >
                                                        ✕
                                                    </button>
                                                </div>
                                            ))}
                                            {editBillItemsModal.items.length === 0 && (
                                                <div className="text-center py-6 border border-dashed border-gray-200 rounded-xl bg-gray-50 text-xs text-gray-400">
                                                    No charges added yet. Click "+ Add Charge Item" to begin.
                                                </div>
                                            )}
                                        </div>

                                        {editBillItemsModal.medicines && editBillItemsModal.medicines.length > 0 && (
                                            <div className="border-t border-gray-100 pt-3">
                                                <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-2">Administered In-Clinic Medicines (Read-Only)</h4>
                                                <div className="space-y-2 max-h-[120px] overflow-y-auto pr-1">
                                                    {editBillItemsModal.medicines.map((med, index) => (
                                                        <div key={index} className="flex justify-between items-center text-xs text-gray-600 bg-teal-50/50 border border-teal-100/50 p-2 rounded-lg">
                                                            <div>
                                                                <span className="font-semibold text-gray-800">{med.medicineName}</span>
                                                                <span className="text-gray-500 ml-2">Qty: {med.quantity}</span>
                                                            </div>
                                                            <span className="font-semibold text-teal-700">₹{med.amount}</span>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>
                                <div className="bg-gray-50 px-6 py-4 flex flex-row-reverse gap-3">
                                    <button
                                        type="button"
                                        onClick={handleSaveBillItems}
                                        disabled={editBillItemsSubmitting}
                                        className="px-4 py-2 bg-gray-900 text-white rounded-xl text-sm font-semibold hover:bg-slate-800 transition disabled:opacity-50"
                                    >
                                        {editBillItemsSubmitting ? 'Saving...' : 'Save Changes'}
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setEditBillItemsModal({ isOpen: false, billId: null, items: [], medicines: [], patientName: '', billNumber: '' })}
                                        className="px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl text-sm font-semibold hover:bg-gray-50 transition"
                                    >
                                        Cancel
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Checkout Print Action Center Modal removed in favor of automatic printing */}
            </>
        </div>
    );
};

export default DoctorDashboard;


// Doctor Appointments Table
const DoctorAppointmentsTable = ({ appointments, onStatusUpdate, onEdit, onConsult, onPrint, onAuditHistory, onAdmitToIpd, hasIPD, startIndex = 0, pagination }) => {
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
                        },
                        ...(hasIPD ? [{
                            label: 'Admit to IPD',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" /></svg>,
                            onClick: () => onAdmitToIpd(info.row.original)
                        }] : [])
                        ] : []),
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
                        label: 'View Details',
                        icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z" /><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" /></svg>,
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


// Doctor OPD Table
const DoctorOpdTable = ({ opds, queueEntries = [], onPrintOpd, onStartConsultation, onPrintPrescription, onViewPrescription, onAdmitIpd, user, hasIPD, startIndex = 0, pagination }) => {
    const columnHelper = createColumnHelper();

    const getQueuePosition = (opdId) => {
        if (!queueEntries || queueEntries.length === 0) return null;
        const sorted = [...queueEntries].sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
        const index = sorted.findIndex(q => q.opd?.id === opdId);
        return index !== -1 ? index + 1 : null;
    };

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.NO.',
            cell: info => startIndex + info.row.index + 1,
        }),
        columnHelper.accessor('caseId', {
            header: 'CASE ID',
            cell: info => <span className="font-medium text-gray-900">{info.getValue()}</span>,
        }),
        columnHelper.accessor(row => row.patient?.name, {
            id: 'patient',
            header: 'PATIENT',
        }),
        columnHelper.accessor(row => row.doctor?.name || '-', {
            id: 'doctor',
            header: 'DOCTOR',
        }),
        columnHelper.accessor('status', {
            id: 'position',
            header: 'QUEUE POSITION',
            cell: info => {
                const opd = info.row.original;
                if (opd.status === 'QUEUED') {
                    const pos = getQueuePosition(opd.id);
                    return pos ? `Position #${pos}` : 'Queued';
                }
                return '—';
            },
        }),
        columnHelper.accessor('visitType', {
            header: 'VISIT',
        }),
        columnHelper.accessor('createdAt', {
            header: 'CREATED',
            cell: info => new Date(info.getValue()).toLocaleString(),
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">ACTIONS</div>,
            cell: info => {
                const opd = info.row.original;
                const actions = [];

                // Print Case Paper - always available
                actions.push({
                    label: 'Print Case Paper',
                    icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M5 4v3H4a2 2 0 00-2 2v3a2 2 0 002 2h1v2a2 2 0 002 2h6a2 2 0 002-2v-2h1a2 2 0 002-2V9a2 2 0 00-2-2h-1V4a2 2 0 00-2-2H7a2 2 0 00-2 2zm8 0H7v3h6V4zm0 8H7v4h6v-4z" clipRule="evenodd" /></svg>,
                    onClick: () => onPrintOpd(opd)
                });

                // Start Consultation - only for QUEUED status
                if (opd.status === 'QUEUED') {
                    actions.push({
                        label: 'Start Consultation',
                        icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 2a4 4 0 00-4 4v1H5a1 1 0 00-.994.89l-1 9A1 1 0 004 18h12a1 1 0 00.994-1.11l-1-9A1 1 0 0015 7h-1V6a4 4 0 00-4-4zM8 6a2 2 0 114 0v1H8V6zm.707 7.293a1 1 0 00-1.414 1.414L9.586 17a1 1 0 001.414 0l2.293-2.293a1 1 0 00-1.414-1.414L10 15.172l-1.293-1.879z" clipRule="evenodd" /></svg>,
                        onClick: () => onStartConsultation(opd),
                        disabled: false
                    });
                }

                // Prescription actions - only for CONSULTED or COMPLETED status
                if (opd.status === 'CONSULTED' || opd.status === 'COMPLETED') {
                    actions.push({
                        label: 'Print Prescription',
                        icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M5 4v3H4a2 2 0 00-2 2v3a2 2 0 002 2h1v2a2 2 0 002 2h6a2 2 0 002-2v-2h1a2 2 0 002-2V9a2 2 0 00-2-2h-1V4a2 2 0 00-2-2H7a2 2 0 00-2 2zm8 0H7v3h6V4zm0 8H7v4h6v-4z" clipRule="evenodd" /></svg>,
                        onClick: () => onPrintPrescription(opd)
                    });
                    actions.push({
                        label: 'View Prescription',
                        icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z" /><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" /></svg>,
                        onClick: () => onViewPrescription(opd)
                    });

                    // Solo Doctor Mode + IPD module enabled: can admit patient to IPD
                    if (user?.receptionMode === 'SOLO' && hasIPD && onAdmitIpd) {
                        actions.push({
                            label: 'Admit to IPD',
                            icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" /></svg>,
                            onClick: () => onAdmitIpd(opd)
                        });
                    }
                }

                return (
                    <div className="text-right">
                        <ActionMenu actions={actions} />
                    </div>
                );
            },
        }),
    ];

    return <DataTable data={opds} columns={columns} pagination={pagination} />;
};


// Doctor Queue Table (Client-side Paginated)
const DoctorQueueTable = ({ queueEntries, startIndex = 0, pagination }) => {
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.NO.',
            cell: info => startIndex + info.row.index + 1,
        }),
        columnHelper.display({
            id: 'position',
            header: 'QUEUE POSITION',
            cell: info => (
                <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700 border border-indigo-100/30">
                    Position #{startIndex + info.row.index + 1}
                </span>
            ),
        }),
        columnHelper.accessor(row => row.opd?.patient?.name || row.opd?.patientName || '-', {
            id: 'patient',
            header: 'PATIENT',
        }),
        columnHelper.accessor(row => row.opd?.doctor?.name || row.opd?.doctorName || '-', {
            id: 'doctor',
            header: 'DOCTOR',
        }),
        columnHelper.accessor('createdAt', {
            header: 'CREATED AT',
            cell: info => new Date(info.getValue()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        }),
    ];

    return <DataTable data={queueEntries} columns={columns} pagination={pagination} />;
};
