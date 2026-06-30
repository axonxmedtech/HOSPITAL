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
import ProfileModal from '../../components/ProfileModal';
import ActionMenu from '../../components/ActionMenu';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import HistoryDrawer from '../../components/HistoryDrawer';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import PageHeader from '../../components/PageHeader';
import useWebSocket from '../../hooks/useWebSocket';
import BillingTable from './BillingTable';
import { createColumnHelper } from '@tanstack/react-table';
import PrescriptionModal from '../../components/PrescriptionModal';
import PrescriptionViewModal from '../../components/PrescriptionViewModal';
import IpdAdmitModal from '../../components/IpdAdmitModal';
import { SkeletonDashboard, SkeletonStatsGrid, SkeletonOverviewDual, SkeletonTable } from '../../components/Skeleton';
import MedicineInventoryTab from '../../components/MedicineInventoryTab';

const ReceptionistDashboard = () => {
    const navigate = useNavigate();
    const [user, setUser] = useState(() => authService.getCurrentUser());
    const modules = user?.modules || [];
    const hasOPD = modules.includes('OPD');
    const hasIPD = modules.includes('IPD');
    const hasBilling = modules.includes('BILLING');
    const hasAppointments = modules.includes('APPOINTMENTS');
    const hasMedicalInventory = modules.includes('MEDICAL_INVENTORY');
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'overview';
    const viewFilter = searchParams.get('appointmentFilter') || 'today';

    // Helper to switch tabs
    const setActiveTab = (tab) => {
        if (tab === 'mrd') {
            navigate('/hospital/receptionist/mrd');
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
    const [doctors, setDoctors] = useState([]);
    const [opds, setOpds] = useState([]);
    const [queueEntries, setQueueEntries] = useState([]);
    const [currentPatientName, setCurrentPatientName] = useState(null);
    const [nextPatientName, setNextPatientName] = useState(null);
    const [selectedDoctorForQueue, setSelectedDoctorForQueue] = useState('');
    const [billing, setBilling] = useState([]);
    const [billingStatus, setBillingStatus] = useState('PENDING');
    const [recPrintingId, setRecPrintingId] = useState(null);
    const [loading, setLoading] = useState(false);
    
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

    const [lowStockItems, setLowStockItems] = useState([]);
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [availabilityDoctorId, setAvailabilityDoctorId] = useState('');
    const [isAddPatientModalOpen, setIsAddPatientModalOpen] = useState(false);
    const [isOpdModalOpen, setIsOpdModalOpen] = useState(false);
    const [opdSubmitting, setOpdSubmitting] = useState(false);
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
    const [patientSearchText, setPatientSearchText] = useState('');
    const [showPatientDropdown, setShowPatientDropdown] = useState(false);

    // Reset OPD patient search state when modal is opened or closed
    useEffect(() => {
        if (!isOpdModalOpen) {
            setPatientSearchText('');
            setShowPatientDropdown(false);
            setOpdForm(prev => ({
                ...prev,
                patientId: null,
                bp: '',
                temperature: '',
                pulse: '',
                weight: '',
                spo2: '',
                problem: '',
                visitType: 'NEW'
            }));
        }
    }, [isOpdModalOpen]);

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
    const todayStr = (() => { const t = new Date(); return t.getFullYear() + '-' + String(t.getMonth() + 1).padStart(2, '0') + '-' + String(t.getDate()).padStart(2, '0'); })();
    const [opdDateFilter, setOpdDateFilter] = useState(todayStr);
    const [opdTabView, setOpdTabView] = useState('Live');

    // Patient tab: All / Date toggle
    const [patientTabView, setPatientTabView] = useState('All');
    const [patientDateFilter, setPatientDateFilter] = useState(todayStr);

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
    const [profileOpen, setProfileOpen] = useState(false);

    // Patient Details Modal
    const [patientDetailsModal, setPatientDetailsModal] = useState({ isOpen: false, patient: null });

    const { success, error: toastError, info } = useToast();

    // Stats
    const [stats, setStats] = useState({ today: 0, pending: 0, total: 0 });
    const [availableBeds, setAvailableBeds] = useState([]);

    useEffect(() => {
        loadData();
    }, [activeTab, page, searchTerm, viewFilter, pageSize, selectedDoctorForQueue, billingStatus, opdDateFilter, opdTabView, patientTabView, patientDateFilter]); // Add pageSize & doctor filter to dependencies

    // WebSocket connection will be initialized below loadData definition to avoid ReferenceError

    // Auto-select doctor in OPD form if only one is available
    useEffect(() => {
        if (isOpdModalOpen && doctors && doctors.length === 1) {
            setOpdForm(prev => ({ ...prev, doctorId: doctors[0].id }));
        }
    }, [isOpdModalOpen, doctors]);

    const loadData = async (showSpinner = true) => {
        if (showSpinner) setLoading(true);
        try {
            // Stats
            const statsData = await hospitalService.getAppointmentStats();
            setStats(statsData);

            if (activeTab === 'overview' || activeTab === 'appointments' || activeTab === 'opd' || activeTab === 'queue' || activeTab === 'ipd') {
                // Fetch appointments (Server-side) + Doctors + Patients for lookup
                const promises = [
                    (activeTab === 'appointments' || activeTab === 'overview') ? hospitalService.getAppointments(searchTerm, page, pageSize, viewFilter) : Promise.resolve({ content: [] }),
                    hospitalService.getDoctors('', 0, 100), // Fetch doctors for lookup
                    hospitalService.getPatients('', 0, 1000), // Fetch ALL patients (up to 1000) for lookup dropdown
                    activeTab === 'overview' ? hospitalService.getTodaysFollowUps() : Promise.resolve([])
                ];
                const [apptData, docData, patData, followUpsData] = await Promise.all(promises);

                if (activeTab === 'appointments' || activeTab === 'overview') {
                    if (apptData.content) {
                        setAppointments(apptData.content);
                        setTotalPages(apptData.totalPages);
                        setTotalElements(apptData.totalElements);
                    } else {
                        setAppointments(apptData);
                        setTotalPages(1);
                        setTotalElements(apptData.length);
                    }
                }

                if (activeTab === 'overview') {
                    setTodaysFollowUps(followUpsData || []);
                    try {
                        const beds = await hospitalService.getAvailableBeds();
                        setAvailableBeds(beds || []);
                    } catch {
                        setAvailableBeds([]);
                    }
                }

                if (activeTab === 'opd') {
                    // OPD tab: fetch opds separately
                    let dateParam = '';
                    let statusParam = '';
                    if (opdTabView === 'Live') {
                        dateParam = todayStr;
                        statusParam = 'QUEUED';
                    } else {
                        dateParam = opdDateFilter;
                        statusParam = ''; // all statuses on that date
                    }
                    const opdsData = await hospitalService.getOpds(searchTerm, page, pageSize, dateParam, statusParam);
                    let opdsArray = opdsData.content ? opdsData.content : opdsData;
                    if (opdTabView === 'Live') {
                        opdsArray = (opdsArray || []).filter(o => o.status === 'QUEUED');
                    }
                    setOpds(opdsArray || []);
                    setTotalPages(opdsData.totalPages || 1);
                    setTotalElements(opdsData.totalElements || (opdsArray ? opdsArray.length : 0));
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
                }

                if (activeTab === 'queue' || activeTab === 'overview' || activeTab === 'opd') {
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
                        setCurrentPatientName(sorted[0]?.opd?.patient?.name || sorted[0]?.opd?.patientName || 'No Name');
                        setNextPatientName(sorted[1] ? (sorted[1]?.opd?.patient?.name || sorted[1]?.opd?.patientName || 'No Name') : null);
                    } else {
                        setCurrentPatientName(null);
                        setNextPatientName(null);
                    }
                }

                // Set Doctors for lookup/dropdown
                if (docData?.content) setDoctors(docData.content);
                else setDoctors(docData || []);

                // Set Patients for lookup/dropdown in specific modal
                if (patData?.content) setPatients(patData.content);
                else setPatients(patData || []);

            } else if (activeTab === 'patients') {
                const dateParam = patientTabView === 'Date' ? patientDateFilter : '';
                const data = await hospitalService.getPatients(searchTerm, page, pageSize, dateParam);
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

            // Check for low-stock items if in Clinic mode
            if (user?.inClinic !== false) {
                try {
                    const inv = await hospitalService.getInventoryMedicines();
                    const lowStock = (inv || []).filter(item => item.isActive !== false && item.stockQuantity <= item.minStockLevel);
                    setLowStockItems(lowStock);
                } catch (err) {
                    console.error("Failed to load inventory for low stock alerts", err);
                }
            } else {
                setLowStockItems([]);
            }
        } catch (err) {
            console.error(`[ReceptionistDashboard] Failed to load ${activeTab}:`, err);
            // Only show toast error if it was a user-initiated action (spinner shown)
            if (showSpinner) toastError('Failed to load data');
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

    const handleBillStatus = async (id, status, billObj = null) => {
        // Open payment modal when marking PAID from receptionist
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

    const openPdfInNewTab = (endpointPath) => {
        const token = sessionStorage.getItem('token');
        const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
        const separator = endpointPath.includes('?') ? '&' : '?';
        const url = `${baseUrl}${endpointPath}${separator}token=${encodeURIComponent(token)}`;
        window.open(url, '_blank');
    };

    const handlePrintReceipt = (id) => {
        openPdfInNewTab(`/hospital/billing/${id}/pdf`);
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

    const handlePrintPrescription = () => {
        if (!prescriptionModal.appointmentId) return;
        openPdfInNewTab(`/hospital/doctors/prescription/${prescriptionModal.appointmentId}/pdf`);
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
        const loginUrl = authService.getLoginUrl();
        authService.logout();
        navigate(loginUrl);
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

    const [paymentProcessing, setPaymentProcessing] = useState(false);

    const handleProcessPayment = async (method) => {
        if (paymentProcessing) return;
        setPaymentProcessing(method);
        try {
            const pm = method === 'Online' ? 'UPI' : 'CASH';
            let reference = null;
            if (pm === 'UPI') {
                reference = window.prompt('Enter UTR / transaction reference (required for UPI):');
                if (!reference || !reference.trim()) {
                    toastError('UTR / reference is required for UPI payments');
                    setPaymentProcessing(false);
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
            setPaymentProcessing(false);
        }
    };


    const handleViewPatient = (patient) => {
        setPatientDetailsModal({ isOpen: true, patient });
    };

    const handlePrintOpd = (opd) => {
        openPdfInNewTab(`/hospital/opd/${opd.id}/pdf`);
    };

    const tabs = [
        { id: 'overview', label: 'Overview', icon: null },
        ...(hasOPD ? [{ id: 'patients', label: 'Patients', icon: null }] : []),
        ...(hasAppointments ? [{ id: 'appointments', label: 'Appointments', icon: null }] : []),
        ...(hasOPD ? [{ id: 'opd', label: 'OPD', icon: null }] : []),
        ...(hasIPD ? [{ id: 'ipd', label: 'IPD', icon: null }] : []),
        ...(hasIPD ? [{ id: 'mrd', label: 'MRD Archive', icon: null }] : []),
        ...(hasBilling ? [{ id: 'billing', label: 'Billing', icon: null }] : []),
        ...(hasMedicalInventory && user?.inClinic !== false ? [{ id: 'inventory', label: 'Medicine Inventory', icon: null }] : []),
    ].filter(tab => tab.id !== 'billing' || user?.billingHandler !== 'DOCTOR');

    // Fallback if the URL parameter tab is not currently valid/visible
    useEffect(() => {
        const isValidTab = tabs.some(t => t.id === activeTab);
        if (!isValidTab) {
            setActiveTab('overview');
        }
    }, [user, activeTab, tabs]);

    const pagination = {
        pageIndex: page,
        pageSize: pageSize,
        totalItems: totalElements,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage)
    };

    const getQueuePosition = (opdId) => {
        if (!queueEntries || queueEntries.length === 0) return null;
        const sorted = [...queueEntries].sort((a,b) => new Date(a.createdAt) - new Date(b.createdAt));
        const index = sorted.findIndex(q => q.opd?.id === opdId);
        return index !== -1 ? index + 1 : null;
    };

    const filteredPatientsForOpd = patientSearchText.trim().length >= 3
        ? patients.filter(p => {
            const query = patientSearchText.toLowerCase();
            return (p.name?.toLowerCase().includes(query)) ||
                   (p.phone?.includes(query)) ||
                   ((p.customId || p.id?.toString())?.toLowerCase().includes(query));
          })
        : [];

    const showPatientSuggestions = showPatientDropdown && patientSearchText.trim().length >= 3;

    const getGreeting = () => {
        const h = new Date().getHours();
        if (h < 12) return 'Good morning';
        if (h < 17) return 'Good afternoon';
        return 'Good evening';
    };
    const firstName = user?.name?.split(' ')[0] || user?.username || 'there';
    const todayLabel = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

    const availToday = new Date().toISOString().slice(0, 10);

    const availDoctorAppts = availabilityDoctorId
      ? appointments
          .filter(a => {
            const dId = String(a.doctorId ?? a.doctor?.id ?? '');
            const aDate = (a.appointmentDate ?? a.date ?? '').slice(0, 10);
            return dId === String(availabilityDoctorId) && aDate === availToday;
          })
          .sort((a, b) =>
            (a.appointmentTime ?? a.scheduledTime ?? '').localeCompare(
              b.appointmentTime ?? b.scheduledTime ?? ''
            )
          )
      : [];

    const availApptCount = availDoctorAppts.length;
    const availLastTime = availDoctorAppts.length > 0
      ? (availDoctorAppts[availDoctorAppts.length - 1].appointmentTime
          ?? availDoctorAppts[availDoctorAppts.length - 1].scheduledTime
          ?? null)
      : null;

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
                    title={`${tabs.find(t => t.id === activeTab)?.label || (activeTab.charAt(0).toUpperCase() + activeTab.slice(1))} Dashboard`}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => setProfileOpen(true)}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-white p-8">
                    {/* Overview Tab */}
                    {activeTab === 'overview' && !loading && (
                        <div className="space-y-6">
                            {/* Greeting Header */}
                            <div className="flex items-start justify-between">
                                <div>
                                    <h2 className="text-2xl font-bold text-gray-900">{getGreeting()}, {firstName} 👋</h2>
                                    <p className="text-sm text-gray-500 mt-1">Receptionist &middot; {todayLabel}</p>
                                </div>
                            </div>
                            {user?.inClinic !== false && lowStockItems.length > 0 && (
                                <div className="bg-amber-50 border border-amber-200/80 rounded-2xl p-4 flex items-start gap-3 shadow-sm hover:shadow transition-all duration-300 animate-fade-in">
                                    <div className="p-2 bg-amber-100 text-amber-800 rounded-xl">
                                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                        </svg>
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <h3 className="text-sm font-bold text-amber-900">Low Stock Alert: {lowStockItems.length} clinical items require restocking</h3>
                                        <p className="text-xs text-amber-700/90 mt-1 leading-relaxed">
                                            The physical stock levels for these administered items are below reorder thresholds:
                                        </p>
                                        <div className="flex flex-wrap gap-2 mt-2">
                                            {lowStockItems.map(item => (
                                                <span key={item.id} className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200/40">
                                                    {item.name} <span className="font-bold">({item.stockQuantity} left)</span>
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                    <button 
                                        onClick={() => setActiveTab('inventory')}
                                        className="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold rounded-lg transition-all"
                                    >
                                        Restock Inventory
                                    </button>
                                </div>
                            )}
                            {/* Stats Cards */}
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="bg-white rounded-xl border border-gray-200 p-5 text-left hover:border-blue-300 hover:shadow-md transition-all group"
                                >
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Today&apos;s Appointments</p>
                                    <p className="text-3xl font-bold text-gray-900 mt-2">{stats.today}</p>
                                    <p className="text-xs text-blue-600 mt-1 group-hover:underline">View all →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('queue')}
                                    className="bg-white rounded-xl border border-gray-200 p-5 text-left hover:border-orange-300 hover:shadow-md transition-all group"
                                >
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Patients in Queue</p>
                                    <p className={`text-3xl font-bold mt-2 ${queueEntries.length > 10 ? 'text-orange-600' : 'text-gray-900'}`}>{queueEntries.length}</p>
                                    <p className="text-xs text-orange-600 mt-1 group-hover:underline">Manage queue →</p>
                                </button>
                                <button
                                    onClick={() => setActiveTab('appointments')}
                                    className="bg-white rounded-xl border border-gray-200 p-5 text-left hover:border-red-300 hover:shadow-md transition-all group"
                                >
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Pending</p>
                                    <p className={`text-3xl font-bold mt-2 ${stats.pending > 5 ? 'text-red-600' : 'text-gray-900'}`}>{stats.pending}</p>
                                    <p className="text-xs text-red-500 mt-1 group-hover:underline">Review →</p>
                                </button>
                                <div className="bg-white rounded-xl border border-gray-200 p-5">
                                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Available Beds</p>
                                    <p className={`text-3xl font-bold mt-2 ${availableBeds.length === 0 ? 'text-red-600' : 'text-green-600'}`}>{availableBeds.length}</p>
                                    <p className="text-xs text-gray-400 mt-1">across all wards</p>
                                </div>
                            </div>

                            {/* Quick Actions */}
                            <div className="flex flex-wrap gap-3">
                                <button
                                    onClick={() => setIsAddPatientModalOpen(true)}
                                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-gray-900 hover:bg-gray-800 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
                                    </svg>
                                    Register Patient
                                </button>
                                <button
                                    onClick={() => setIsAddModalOpen(true)}
                                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                    </svg>
                                    Book Appointment
                                </button>
                                {hasIPD && (
                                    <button
                                        onClick={() => setIsIpdAdmitOpen(true)}
                                        className="inline-flex items-center gap-2 px-4 py-2.5 bg-green-600 hover:bg-green-700 text-white text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                    >
                                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-2 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                                        </svg>
                                        Admit Patient
                                    </button>
                                )}
                                <button
                                    onClick={() => setActiveTab('patients')}
                                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-white border border-gray-300 hover:bg-gray-50 text-gray-700 text-sm font-semibold rounded-xl transition-all shadow-sm active:scale-95"
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                    </svg>
                                    Search Patients
                                </button>
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
                                        <button
                                            onClick={() => setIsAddModalOpen(true)}
                                            className="inline-flex items-center justify-center gap-1.5 px-3 py-1.5 bg-gray-900 hover:bg-gray-800 text-white text-xs font-semibold rounded-xl transition-all shadow-sm active:scale-95 cursor-pointer animate-fade-in"
                                        >
                                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                                            </svg>
                                            Add Appointment
                                        </button>
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
                                                onChange={(e) => { setSearchTerm(e.target.value); setPage(0); }}
                                                className="w-full pl-9 pr-4 py-1.5 text-xs bg-white border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-gray-900/10 focus:border-gray-900 transition-all"
                                            />
                                        </div>
                                        <div className="flex bg-gray-100 rounded-xl p-1 border border-gray-200">
                                            {['today', 'upcoming'].map(view => (
                                                <button
                                                    key={view}
                                                    onClick={() => { setViewFilter(view); setPage(0); }}
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

                                     {/* Appointment List Content */}
                                     <div className="flex-1 overflow-auto p-6 space-y-6">
                                         <div>
                                             <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                                                 <span className="w-1.5 h-3 bg-gray-900 rounded-full"></span>
                                                 Active Appointments
                                             </h4>
                                             {appointments.length > 0 ? (
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
                                             )}
                                         </div>

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
                                                                 <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Assigned Doctor</th>
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
                                                                     <td className="px-4 py-3.5 text-sm text-gray-600">
                                                                         {record.doctorName || '-'}
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
                                        {doctors && doctors.length > 1 && (
                                            <div className="relative">
                                                <select
                                                    value={selectedDoctorForQueue}
                                                    onChange={(e) => { setSelectedDoctorForQueue(e.target.value); setPage(0); }}
                                                    className="pl-3 pr-8 py-1.5 text-xs bg-white border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-gray-900/10 focus:border-gray-900 appearance-none font-semibold text-gray-700 cursor-pointer"
                                                >
                                                    <option value="">All Doctors</option>
                                                    {doctors.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                                </select>
                                            </div>
                                        )}
                                    </div>

                                    {/* Now Serving Banner */}
                                    {currentPatientName && (
                                        <div className="mx-4 mt-3 mb-1 flex items-center justify-between bg-green-50 border border-green-200 rounded-xl px-4 py-2.5">
                                            <div>
                                                <p className="text-xs font-medium text-green-600 uppercase tracking-wide">Now Serving</p>
                                                <p className="text-sm font-bold text-green-900 mt-0.5">{currentPatientName}</p>
                                            </div>
                                            {queueEntries.length > 1 && (
                                                <div className="text-right">
                                                    <p className="text-xs text-green-600">Est. wait for next</p>
                                                    <p className="text-sm font-bold text-green-800">~{(queueEntries.length - 1) * 10} min</p>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    {/* Queue List Content */}
                                    <div className="flex-1 overflow-auto p-6">
                                        {queueEntries.length > 0 ? (
                                            <div className="overflow-x-auto border border-gray-100 rounded-xl">
                                                <table className="w-full text-sm text-left">
                                                    <thead>
                                                        <tr className="bg-gray-50 border-b border-gray-100">
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">S.No.</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Position</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Patient</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Doctor</th>
                                                            <th className="px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">Time</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody className="divide-y divide-gray-50">
                                                        {[...queueEntries]
                                                            .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
                                                            .slice(0, 10)
                                                            .map((q, idx) => (
                                                                <tr key={q.id} className="hover:bg-gray-50/40 transition-colors">
                                                                    <td className="px-4 py-3.5 text-sm text-gray-955 font-medium">{idx + 1}</td>
                                                                    <td className="px-4 py-3.5">
                                                                        <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700 border border-indigo-100/30">
                                                                            Position #{idx + 1}
                                                                        </span>
                                                                    </td>
                                                                    <td className="px-4 py-3.5 text-sm text-gray-955 font-semibold">{q.opd?.patient?.name || q.opd?.patientName || '-'}</td>
                                                                    <td className="px-4 py-3.5 text-sm text-gray-600">{q.opd?.doctor?.name || q.opd?.doctorName || '-'}</td>
                                                                    <td className="px-4 py-3.5 text-xs text-gray-400">
                                                                        {new Date(q.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
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

                    {/* Standardized Header */}
                    {activeTab !== 'overview' && (
                    <PageHeader
                        title={tabs.find(t => t.id === activeTab)?.label || (activeTab.charAt(0).toUpperCase() + activeTab.slice(1))}
                        subtitle={`Manage ${activeTab} records`}
                        onSearch={activeTab === 'queue' ? null : (e) => setSearchTerm(e.target.value)}
                        searchValue={searchTerm}
                        searchPlaceholder={activeTab === 'queue' ? '' : `Search ${activeTab}...`}
                        onAdd={activeTab === 'queue' || activeTab === 'billing' || activeTab === 'ipd' || activeTab === 'inventory' ? null : () => {
                            if (activeTab === 'opd') setIsOpdModalOpen(true);
                            else setIsAddModalOpen(true);
                        }}
                        addLabel={activeTab === 'opd' ? 'New OPD / Case' : `Add ${activeTab.slice(0, -1)}`}
                        filter={activeTab === 'appointments' ? (
                            <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200">
                                {['today', 'upcoming'].map(view => (
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
                                {['PENDING', 'PAID', 'PARTIAL'].map(status => (
                                    <button
                                        key={status}
                                        onClick={() => { setBillingStatus(status); setPage(0); }}
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
                            doctors && doctors.length > 1 ? (
                                <div className="flex items-center gap-3">
                                    <select value={selectedDoctorForQueue} onChange={(e) => { setSelectedDoctorForQueue(e.target.value); setPage(0); }} className="input-field">
                                        <option value="">All Doctors</option>
                                        {doctors.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                    </select>
                                </div>
                            ) : null
                        ) : activeTab === 'patients' ? (
                            <div className="flex items-center gap-2">
                                <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200 h-[38px] items-center">
                                    {['All', 'Date'].map(view => (
                                        <button
                                            key={view}
                                            type="button"
                                            onClick={() => {
                                                setPatientTabView(view);
                                                setPage(0);
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
                                        onChange={(e) => { setPatientDateFilter(e.target.value); setPage(0); }}
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
                                                setPage(0);
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
                                        onChange={(e) => { setOpdDateFilter(e.target.value); setPage(0); }}
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
                        ) : null}
                      
                    />
                    )}

                    {loading && (
                        <div className="space-y-8 animate-fade-in-up">
                            {activeTab === 'overview' ? (
                                <>
                                    <SkeletonStatsGrid count={4} gridCols="md:grid-cols-4" />
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
                                                        <td className="px-4 py-3">{o.visitType}</td>
                                                        <td className="px-4 py-3">{new Date(o.createdAt).toLocaleString()}</td>
                                                        <td className="px-4 py-3">
                                                            <div className="flex items-center gap-2">
                                                                <button onClick={() => handlePrintOpd(o)} className="px-3 py-1 bg-gray-900 text-white rounded">Print</button>
                                                                {user?.role === 'RECEPTIONIST' && hasIPD && o.status !== 'IN_IPD' && (
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
                                        title={opdTabView === 'Live' ? "No Active OPD Cases" : "No OPD Records Found"}
                                        message={opdTabView === 'Live' ? "No patients are currently in the queue or being consulted." : `No OPD registrations found on ${opdDateFilter}.`}
                                        actionLabel={opdTabView === 'Live' ? "New OPD / Case" : null}
                                        onAction={opdTabView === 'Live' ? () => setIsOpdModalOpen(true) : null}
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
                                                    <th className="px-4 py-2">Position</th>
                                                    <th className="px-4 py-2">Patient</th>
                                                    <th className="px-4 py-2">Doctor</th>
                                                    <th className="px-4 py-2">Created</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {[...queueEntries]
                                                    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
                                                    .slice(0, 10)
                                                    .map((q, idx) => (
                                                        <tr key={q.id} className="border-t">
                                                            <td className="px-4 py-3">{idx + 1}</td>
                                                            <td className="px-4 py-3">Position #{idx + 1}</td>
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
                                        onPrintReceipt={handlePrintReceipt}
                                        startIndex={page * pageSize}
                                        pagination={pagination}
                                    />
                                ) : (
                                    <EmptyState
                                        icon={null}
                                        title={patientTabView === 'Date' ? "No Registered Patients" : "No Patients Found"}
                                        message={patientTabView === 'Date' ? `No patients registered on ${patientDateFilter}.` : "There are no patients registered in the system yet."}
                                        actionLabel={patientTabView === 'Date' ? null : "Add Patient"}
                                        onAction={patientTabView === 'Date' ? null : () => setIsAddModalOpen(true)}
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
                                    onPrint={handlePrintReceipt}
                                    printingBillId={recPrintingId}
                                    onEditItems={handleOpenEditBillItems}
                                />
                            )}
                            {activeTab === 'inventory' && (
                                <MedicineInventoryTab />
                            )}
                        </div>
                    )}

                </main>
            </div>

            {/* Appointment Modal - Using Shared Component */}
            {(activeTab === 'appointments' || activeTab === 'overview') && (
                <AppointmentModal
                    isOpen={isAddModalOpen}
                    onClose={() => setIsAddModalOpen(false)}
                    onSuccess={() => loadData(false)}
                    doctors={doctors}
                    patients={patients}
                />
            )}

            {/* Patient Modal - Using Shared Component */}
            {((activeTab === 'patients' && isAddModalOpen) || (activeTab === 'overview' && isAddPatientModalOpen)) && (
                <PatientModal
                    isOpen={activeTab === 'patients' ? isAddModalOpen : isAddPatientModalOpen}
                    onClose={() => {
                        if (activeTab === 'patients') setIsAddModalOpen(false);
                        else setIsAddPatientModalOpen(false);
                        setSelectedPatient(null);
                    }}
                    initialData={selectedPatient}
                    onSuccess={() => {
                        if (activeTab === 'patients') setIsAddModalOpen(false);
                        else setIsAddPatientModalOpen(false);
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
                            if (opdSubmitting) return;
                            if (!opdForm.patientId) {
                                toastError('Please select a valid patient from the suggestions');
                                return;
                            }
                            setOpdSubmitting(true);
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
                                success('OPD Case created successfully — ID: ' + res.caseId);
                                loadData(); // Refresh the data after OPD creation
                            } catch (err) {
                                console.error('Failed to create OPD', err);
                                toastError('Failed to create OPD');
                            } finally {
                                setOpdSubmitting(false);
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
                                            className="input-field pl-10"
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
                                            placeholder="Type at least 3 letters to search patient..."
                                            autoComplete="off"
                                        />
                                    </div>
                                    
                                    {showPatientSuggestions && (
                                        <div className="absolute left-0 right-0 mt-1 bg-white border border-neutral-200 rounded-xl shadow-xl z-50 max-h-60 overflow-y-auto divide-y divide-neutral-100 animate-scale-in">
                                            {filteredPatientsForOpd.length > 0 ? (
                                                filteredPatientsForOpd.map(p => (
                                                    <button
                                                        type="button"
                                                        key={p.id}
                                                        onClick={() => {
                                                            setOpdForm(prev => ({ ...prev, patientId: p.id }));
                                                            setPatientSearchText(`${p.name}${p.phone ? ` (${p.phone})` : ''}${p.customId ? ` [${p.customId}]` : ''}`);
                                                            setShowPatientDropdown(false);
                                                        }}
                                                        className="w-full px-4 py-3 hover:bg-neutral-50 cursor-pointer transition-colors duration-150 flex flex-col gap-0.5 text-left"
                                                    >
                                                        <div className="flex justify-between items-center">
                                                            <span className="font-semibold text-neutral-800 text-sm">{p.name}</span>
                                                            {p.customId && (
                                                                <span className="text-[10px] font-bold px-2 py-0.5 bg-neutral-100 text-neutral-600 rounded-full tracking-wide">
                                                                    {p.customId}
                                                                </span>
                                                            )}
                                                        </div>
                                                        <div className="flex gap-4 text-xs text-neutral-500">
                                                            {p.phone && <span>📞 {p.phone}</span>}
                                                            {p.gender && <span>👤 {p.gender}</span>}
                                                            {p.age && <span>🎂 {p.age} Yrs</span>}
                                                        </div>
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
                                    {doctors && doctors.length === 1 ? (
                                        <div className="w-full px-4 py-2.5 bg-neutral-50 border border-neutral-200 text-neutral-800 rounded-xl text-sm font-semibold flex items-center justify-between">
                                            <span>{doctors[0].name}</span>
                                            <span className="text-xs px-2 py-0.5 bg-green-50 text-green-700 border border-green-200 rounded-full font-medium">Assigned</span>
                                        </div>
                                    ) : (
                                        <select className="input-field" value={opdForm.doctorId || ''} onChange={(e) => setOpdForm(prev => ({ ...prev, doctorId: e.target.value }))}>
                                            <option value="">Unassigned</option>
                                            {doctors.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                        </select>
                                    )}
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
                                <button type="button" onClick={() => setIsOpdModalOpen(false)} disabled={opdSubmitting} className={`flex-1 py-2 rounded-lg border ${opdSubmitting ? 'opacity-50 cursor-not-allowed' : ''}`}>Cancel</button>
                                <button type="submit" disabled={opdSubmitting} className={`flex-1 py-2 rounded-lg text-white flex items-center justify-center gap-2 ${opdSubmitting ? 'bg-gray-400 cursor-not-allowed' : 'bg-gray-900'}`}>
                                    {opdSubmitting && (
                                        <svg className="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                        </svg>
                                    )}
                                    {opdSubmitting ? 'Creating...' : 'Create OPD'}
                                </button>
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
                                                    disabled={paymentProcessing}
                                                    className={`flex items-center justify-center gap-2 p-3 border rounded-lg transition-colors group ${paymentProcessing ? 'opacity-50 cursor-not-allowed' : 'hover:bg-gray-50 hover:border-gray-300'}`}
                                                >
                                                    <span className="font-medium text-gray-700">{paymentProcessing === 'Cash' ? 'Processing...' : 'Cash'}</span>
                                                </button>
                                                <button
                                                    onClick={() => handleProcessPayment('Online')}
                                                    disabled={paymentProcessing}
                                                    className={`flex items-center justify-center gap-2 p-3 border rounded-lg transition-colors group ${paymentProcessing ? 'opacity-50 cursor-not-allowed' : 'hover:bg-gray-50 hover:border-gray-300'}`}
                                                >
                                                    <span className="font-medium text-gray-700">{paymentProcessing === 'Online' ? 'Processing...' : 'Online/UPI'}</span>
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
                                                onClick={() => handlePrintReceipt(paymentSuccessModal.billId)}
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
                                    className="px-4 py-2 bg-gray-900 text-white rounded-xl text-sm font-semibold hover:bg-gray-800 transition disabled:opacity-50"
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
const PatientsTable = ({ patients, onStatusUpdate, onViewPrescription, onViewDetails, onOpenPayment, onPrintReceipt, startIndex = 0, pagination }) => {
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
