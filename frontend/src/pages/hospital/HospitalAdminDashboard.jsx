import BillingTable from './BillingTable';
import MessagesTab from './MessagesTab';
import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import authService from '../../services/authService';
import hospitalService from '../../services/hospitalService';
import { API_BASE_URL } from '../../services/apiService'; // BUG-028: single source-of-truth for base URL
import { useToast } from '../../context/ToastContext';
import ConfirmationModal from '../../components/ConfirmationModal';
import { validateForm } from '../../utils/validation';
import EmptyState from '../../components/EmptyState';
import OverviewDashboard from '../../components/OverviewDashboard';
import AppointmentModal from '../../components/AppointmentModal';
import PatientModal from '../../components/PatientModal';
import PatientDetailsModal from '../../components/PatientDetailsModal';
import StaffDetailsModal from '../../components/StaffDetailsModal';
import ProfileModal from '../../components/ProfileModal';

import ActionMenu from '../../components/ActionMenu';
import StatusBadge from '../../components/StatusBadge';
import DataTable from '../../components/DataTable';
import { createColumnHelper } from '@tanstack/react-table';
import ActivityFeed from '../../components/ActivityFeed';
import HistoryDrawer from '../../components/HistoryDrawer';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import PageHeader from '../../components/PageHeader';
import WardsAndBeds from './WardsAndBeds';
import WardModal from '../../components/WardModal';
import useWebSocket from '../../hooks/useWebSocket';
import useDebounce from '../../hooks/useDebounce'; // BUG-017: standardised debounce hook
import { SkeletonDashboard, SkeletonFormCard, SkeletonSettingsCard, SkeletonTable, SkeletonStatsGrid, SkeletonOverviewDual } from '../../components/Skeleton';
import reportsApi from '../../services/pharmacy/reportsApi';
import MedicineInventoryTab from '../../components/MedicineInventoryTab';
import HospitalInventoryTab from '../../components/HospitalInventoryTab';
import IpdAdmitModal from '../../components/IpdAdmitModal';
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    BarChart, Bar, Legend, PieChart, Pie, Cell
} from 'recharts';
/**
 * HospitalAdminDashboard - Hospital Admin dashboard
 * 
 * This page allows Hospital Admin to:
 * - Manage patients
 * - Manage doctors
 * - Manage appointments
 * - Manage billing
 * 
 * @author HMS Team
 * @version Phase-1
 */
const HospitalAdminDashboard = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const [user, setUser] = useState(authService.getCurrentUser());
    const modules = user?.modules || [];
    const hasOPD = modules.includes('OPD');
    const hasIPD = modules.includes('IPD');
    const defaultTab = 'overview';
    const activeTab = searchParams.get('tab') || defaultTab;

    // Helper to switch tabs
    const setActiveTab = (tab) => {
        const newParams = { tab };
        setSearchParams(newParams);
        setSearchInput(''); // Clear search input on tab switch
        setPage(0); // Reset page to 0
    };

    const [patients, setPatients] = useState([]);
    const [doctors, setDoctors] = useState([]);
    const [receptionists, setReceptionists] = useState([]);
    const [pharmacists, setPharmacists] = useState([]);
    const [appointments, setAppointments] = useState([]);
    const [billing, setBilling] = useState([]);
    const [ipds, setIpds] = useState([]);
    const [opds, setOpds] = useState([]);
    const [billingStatus, setBillingStatus] = useState('PENDING');
    const [auditLogs, setAuditLogs] = useState([]);
    const [stats, setStats] = useState({ today: 0, pending: 0, total: 0 });
    const [loading, setLoading] = useState(false);
    const [pharmacyStats, setPharmacyStats] = useState(null);
    const [pharmacyStatsLoading, setPharmacyStatsLoading] = useState(false);
    const [analyticsData, setAnalyticsData] = useState(null);
    const [analyticsTimePeriod, setAnalyticsTimePeriod] = useState('Last 6 Months');

    // Dashboard state
    const [dashboardStats, setDashboardStats] = useState({ totalPatients: 0, totalDoctors: 0, todaysAppointments: 0 });
    const [todaysAppointments, setTodaysAppointments] = useState([]);
    const [isNewPatient, setIsNewPatient] = useState(false);
    const [fees, setFees] = useState({ consultationFee: '', casePaperFee: '' });
    const [origFees, setOrigFees] = useState(null);
    const [feesLoading, setFeesLoading] = useState(false);
    const [feesEditing, setFeesEditing] = useState(false);

    const [customFees, setCustomFees] = useState([]);
    const [customFeeModal, setCustomFeeModal] = useState({
        isOpen: false,
        mode: 'add',
        feeId: null,
        name: '',
        defaultAmount: ''
    });
    const [customFeeSubmitting, setCustomFeeSubmitting] = useState(false);

    const [editBillItemsModal, setEditBillItemsModal] = useState({
        isOpen: false,
        billId: null,
        items: [],
        medicines: [],
        patientName: '',
        billNumber: ''
    });
    const [editBillItemsSubmitting, setEditBillItemsSubmitting] = useState(false);
    const [operationsSettings, setOperationsSettings] = useState({ receptionMode: 'HAS_RECEPTIONIST', billingHandler: 'RECEPTIONIST', inClinic: true });
    const [origOperationsSettings, setOrigOperationsSettings] = useState(null);
    const [settingsLoading, setSettingsLoading] = useState(false);
    const [settingsEditing, setSettingsEditing] = useState(false);
    const [auditLogRoleFilter, setAuditLogRoleFilter] = useState('ALL');
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const ITEMS_PER_PAGE = 10;
    const [paginatedData, setPaginatedData] = useState([]);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);

    const { success, error: toastError, info } = useToast();

    // Confirmation Modal State
    const [searchInput, setSearchInput] = useState('');
    const searchTerm = useDebounce(searchInput, 500); // BUG-017: debounce main search input

    // Modal tracking state instead of activeTab
    const [modalType, setModalType] = useState(null);

    // Patients state for Overview tab
    const [patientsSearchInput, setPatientsSearchInput] = useState('');
    const patientsSearchTerm = useDebounce(patientsSearchInput, 500); // BUG-017: debounce overview patients search
    const [patientsPage, setPatientsPage] = useState(0);
    const [patientsTotalPages, setPatientsTotalPages] = useState(1);
    const [patientsTotalElements, setPatientsTotalElements] = useState(0);

    // Appointments search state for Today's Appointments under Overview tab
    const [appointmentsSearchTerm, setAppointmentsSearchTerm] = useState('');

    // History Drawer State
    const [historyDrawer, setHistoryDrawer] = useState({ isOpen: false, entityType: null, entityId: null, entityName: null });

    // Confirmation Modal State
    const [confirmModal, setConfirmModal] = useState({
        isOpen: false,
        title: '',
        message: '',
        onConfirm: null
    });

    const [showModal, setShowModal] = useState(false);
    const [editData, setEditData] = useState(null);
    const [patientDetailsModal, setPatientDetailsModal] = useState({ isOpen: false, patient: null });
    const [staffDetailsModal, setStaffDetailsModal] = useState({ isOpen: false, staff: null, role: null });
    const [resetPasswordModal, setResetPasswordModal] = useState({ isOpen: false, staff: null, role: '' });
    const [resetPasswordForm, setResetPasswordForm] = useState({ newPassword: '', confirmPassword: '', showNew: false, showConfirm: false, submitting: false, error: '' });
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);


    // OPD Modal State (Admin)
    const [isAdminOpdModalOpen, setIsAdminOpdModalOpen] = useState(false);
    const [adminOpdForm, setAdminOpdForm] = useState({ patientId: null, doctorId: null, bp: '', temperature: '', pulse: '', weight: '', spo2: '', problem: '', visitType: 'NEW' });
    const [adminOpdPatientSearch, setAdminOpdPatientSearch] = useState('');
    const [adminOpdShowDropdown, setAdminOpdShowDropdown] = useState(false);

    // IPD Admit from OPD (Admin)
    const [isAdminIpdAdmitOpen, setIsAdminIpdAdmitOpen] = useState(false);
    const [adminIpdOpdForAdmit, setAdminIpdOpdForAdmit] = useState(null);
    const [profileOpen, setProfileOpen] = useState(false);

    // Patient tab: Date / All toggle
    const [patientTabView, setPatientTabView] = useState('All');
    const getISTDateString = () => {
        const now = new Date();
        const istOffset = 330; // IST = UTC+5:30 in minutes
        const istDate = new Date(now.getTime() + istOffset * 60000);
        return istDate.toISOString().split('T')[0];
    };
    const [patientDateFilter, setPatientDateFilter] = useState(getISTDateString);
    const [datePatients, setDatePatients] = useState([]);
    const [datePatientsLoading, setDatePatientsLoading] = useState(false);

    // OPD tab: Live / Date toggle
    const [opdTabView, setOpdTabView] = useState('Live');
    const [opdDateFilter, setOpdDateFilter] = useState(getISTDateString);


    // Help & Support state variables
    const [faqs, setFaqs] = useState([]);
    const [tickets, setTickets] = useState([]);
    const [supportLoading, setSupportLoading] = useState(false);
    const [supportSubmitting, setSupportSubmitting] = useState(false);
    const [expandedFaqId, setExpandedFaqId] = useState(null);
    const [faqSearch, setFaqSearch] = useState('');
    const [ticketForm, setTicketForm] = useState({ subject: '', message: '', priority: 'LOW' });

    const navigate = useNavigate();

    // Reset patientDateFilter at midnight IST
    useEffect(() => {
        const msUntilMidnightIST = () => {
            const now = new Date();
            const istOffset = 330;
            const nowIST = new Date(now.getTime() + istOffset * 60000);
            const nextMidnight = new Date(nowIST);
            nextMidnight.setUTCHours(0, 0, 0, 0);
            nextMidnight.setUTCDate(nextMidnight.getUTCDate() + 1);
            return nextMidnight.getTime() - istOffset * 60000 - now.getTime();
        };
        const timer = setTimeout(() => {
            setPatientDateFilter(getISTDateString());
        }, msUntilMidnightIST());
        return () => clearTimeout(timer);
    }, []);

    // Real-time WebSocket sync
    useWebSocket(user, setUser, (silent) => {
        if (activeTab !== 'fees' && activeTab !== 'settings' && activeTab !== 'support' && activeTab !== 'audit-logs') {
            loadData(page, pageSize, silent);
        }
    });

    // Clear searchInput when tab changes
    useEffect(() => {
        setSearchInput('');
        setPage(0);
    }, [activeTab]);

    // Effect for loading data
    useEffect(() => {
        // Prevent double fetch when searchInput was just cleared but debounced searchTerm hasn't updated yet
        if (searchInput === '' && searchTerm !== '') return;
        loadData(page, pageSize);
    }, [activeTab, searchTerm, page, billingStatus, auditLogRoleFilter, patientTabView, patientDateFilter, opdTabView, opdDateFilter]);

    // Periodic background polling replaced with WebSocket real-time sync

    // Effect for Patients list loading (Overview Tab specific)
    useEffect(() => {
        if (activeTab !== 'overview') return;
        if (patientsSearchInput === '' && patientsSearchTerm !== '') return;
        loadPatients(patientsSearchTerm, patientsPage);
    }, [activeTab, patientsSearchTerm, patientsPage, pageSize]);

    // Load hospital fees when Fees tab is active
    useEffect(() => {
        const loadFees = async () => {
            if (activeTab !== 'fees') return;
            setFeesLoading(true);
            try {
                const [data, customData] = await Promise.all([
                    hospitalService.getHospitalFees(),
                    hospitalService.getCustomFees()
                ]);
                const loaded = {
                    consultationFee: data.consultationFee != null ? data.consultationFee : '',
                    casePaperFee: data.casePaperFee != null ? data.casePaperFee : ''
                };
                setFees(loaded);
                setOrigFees(loaded);
                setFeesEditing(false);
                setCustomFees(customData || []);
            } catch (err) {
                toastError('Failed to load fees');
            } finally {
                setFeesLoading(false);
            }
        };
        loadFees();
    }, [activeTab]);

    const handleSaveFees = async () => {
        try {
            setFeesLoading(true);
            const payload = {
                consultationFee: fees.consultationFee === '' ? null : parseFloat(fees.consultationFee),
                casePaperFee: fees.casePaperFee === '' ? null : parseFloat(fees.casePaperFee)
            };
            await hospitalService.updateHospitalFees(payload);
            success('Fees updated successfully');
            setOrigFees({ ...fees });
            setFeesEditing(false);
        } catch (err) {
            const msg = err.response?.data || 'Failed to update fees';
            toastError(msg);
        } finally {
            setFeesLoading(false);
        }
    };

    const handleSaveCustomFee = async (e) => {
        e.preventDefault();
        if (!customFeeModal.name.trim()) {
            toastError("Fee name is required");
            return;
        }
        setCustomFeeSubmitting(true);
        try {
            const payload = {
                name: customFeeModal.name.trim(),
                defaultAmount: customFeeModal.defaultAmount === '' ? 0 : parseFloat(customFeeModal.defaultAmount)
            };
            if (customFeeModal.mode === 'add') {
                const newFee = await hospitalService.addCustomFee(payload);
                success('Custom fee added successfully');
                setCustomFees(prev => [...prev, newFee]);
            } else {
                const updatedFee = await hospitalService.updateCustomFee(customFeeModal.feeId, payload);
                success('Custom fee updated successfully');
                setCustomFees(prev => prev.map(f => f.id === customFeeModal.feeId ? updatedFee : f));
            }
            setCustomFeeModal({ isOpen: false, mode: 'add', feeId: null, name: '', defaultAmount: '' });
        } catch (err) {
            const msg = err.response?.data || 'Failed to save custom fee';
            toastError(msg);
        } finally {
            setCustomFeeSubmitting(false);
        }
    };

    const handleDeleteCustomFee = (id) => {
        openConfirmation(
            'Delete Custom Fee',
            'Are you sure you want to delete this custom fee? It will no longer be available for new billing items.',
            async () => {
                try {
                    await hospitalService.deleteCustomFee(id);
                    success('Custom fee deleted successfully');
                    setCustomFees(prev => prev.filter(f => f.id !== id));
                } catch (err) {
                    toastError('Failed to delete custom fee');
                }
            }
        );
    };

    const handleOpenEditBillItems = async (billObj) => {
        try {
            // Make sure custom fees are loaded
            let currentCustomFees = customFees;
            if (currentCustomFees.length === 0) {
                const customData = await hospitalService.getCustomFees();
                setCustomFees(customData || []);
                currentCustomFees = customData || [];
            }
            
            // Map the billing items to DTO format
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

    // Load hospital operations settings when Settings or Audit Logs tab is active
    useEffect(() => {
        const loadOperationsSettings = async () => {
            if (activeTab !== 'settings' && activeTab !== 'audit-logs') return;
            const showSpinner = activeTab === 'settings';
            if (showSpinner) setSettingsLoading(true);
            try {
                const data = await hospitalService.getHospitalOperationsSettings();
                const loaded = {
                    receptionMode: data.receptionMode || 'HAS_RECEPTIONIST',
                    billingHandler: data.billingHandler || 'RECEPTIONIST',
                    inClinic: data.inClinic !== false
                };
                setOperationsSettings(loaded);
                setOrigOperationsSettings(loaded);
                if (activeTab === 'settings') setSettingsEditing(false);
            } catch (err) {
                if (activeTab === 'settings') toastError('Failed to load settings');
            } finally {
                if (showSpinner) setSettingsLoading(false);
            }
        };
        loadOperationsSettings();
    }, [activeTab]);

    // Load Support data (FAQs and Tickets) when support tab is active
    useEffect(() => {
        const loadSupportData = async () => {
            if (activeTab !== 'support') return;
            setSupportLoading(true);
            try {
                const [faqData, ticketData] = await Promise.all([
                    hospitalService.getPublicFaqs(),
                    hospitalService.getTickets()
                ]);
                setFaqs(faqData || []);
                setTickets(ticketData || []);
            } catch (err) {
                console.error("Failed to load Help & Support data", err);
                toastError("Failed to load FAQs or support tickets");
            } finally {
                setSupportLoading(false);
            }
        };
        loadSupportData();
    }, [activeTab]);

    // Load Pharmacy Dashboard Analytics when Pharmacy tab is active
    useEffect(() => {
        const loadPharmacyDashboard = async () => {
            if (activeTab !== 'pharmacy') return;
            setPharmacyStatsLoading(true);
            try {
                const data = await reportsApi.getDashboardData();
                setPharmacyStats(data);
            } catch (err) {
                console.error("Failed to load pharmacy dashboard analytics", err);
                toastError('Failed to load pharmacy dashboard analytics');
            } finally {
                setPharmacyStatsLoading(false);
            }
        };
        loadPharmacyDashboard();
    }, [activeTab]);

    const handleExportLedger = async () => {
        try {
            const blob = await reportsApi.exportLedgerCsv();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `pharmacy_tax_ledger.csv`);
            document.body.appendChild(link);
            link.click();
            link.remove();
            success('Pharmacy tax ledger CSV exported successfully');
        } catch (err) {
            toastError('Failed to export ledger CSV');
        }
    };

    const handleCreateTicket = async (e) => {
        e.preventDefault();
        if (!ticketForm.subject.trim() || !ticketForm.message.trim()) {
            toastError("Subject and Message are required");
            return;
        }
        setSupportSubmitting(true);
        try {
            const newTicket = await hospitalService.createTicket(ticketForm);
            success("Support ticket submitted successfully");
            setTickets(prev => [newTicket, ...prev]);
            setTicketForm({ subject: '', message: '', priority: 'LOW' });
        } catch (err) {
            console.error("Failed to submit support ticket", err);
            const msg = err.response?.data?.message || err.response?.data || "Failed to submit ticket";
            toastError(msg);
        } finally {
            setSupportSubmitting(false);
        }
    };

    const toggleReceptionMode = () => {
        const isCurrentlySolo = operationsSettings.receptionMode === 'SOLO';
        const nextValue = isCurrentlySolo ? 'HAS_RECEPTIONIST' : 'SOLO';
        const title = isCurrentlySolo ? 'Enable Reception Mode' : 'Switch to Self Manage Mode';
        const message = isCurrentlySolo
            ? 'Are you sure you want to enable Reception Mode? Receptionists will be allowed to log in and manage the clinic workflow.'
            : 'Are you sure you want to activate Self Manage mode? Receptionist accounts will be blocked from logging in, and Billing will automatically switch to the Doctor.';

        openConfirmation(title, message, async () => {
            try {
                setSettingsLoading(true);
                const updated = {
                    receptionMode: nextValue,
                    billingHandler: nextValue === 'SOLO' ? 'DOCTOR' : operationsSettings.billingHandler,
                    inClinic: operationsSettings.inClinic
                };
                await hospitalService.updateHospitalOperationsSettings(updated);
                setOperationsSettings(updated);
                setOrigOperationsSettings(updated);
                success('Operational settings updated successfully.');
                
                // Refresh local user profile session
                const profile = await authService.getProfile();
                authService.updateCurrentUser(profile);
                setUser(profile);
            } catch (err) {
                const msg = err.response?.data || 'Failed to update operations mode';
                toastError(msg);
            } finally {
                setSettingsLoading(false);
            }
        }, false);
    };

    const handleBillingHandlerChange = async (e) => {
        const nextValue = e.target.value;
        if (operationsSettings.receptionMode === 'SOLO' && nextValue !== 'DOCTOR') {
            info('Billing must be managed by Doctor in Self Manage Mode.');
            return;
        }
        
        try {
            setSettingsLoading(true);
            const updated = {
                receptionMode: operationsSettings.receptionMode,
                billingHandler: nextValue,
                inClinic: operationsSettings.inClinic
            };
            const data = await hospitalService.updateHospitalOperationsSettings(updated);
            const loaded = {
                receptionMode: data.receptionMode || 'HAS_RECEPTIONIST',
                billingHandler: data.billingHandler || 'RECEPTIONIST',
                inClinic: data.inClinic !== false
            };
            setOperationsSettings(loaded);
            setOrigOperationsSettings(loaded);
            success('Operational settings updated successfully.');
            
            // Refresh local user profile session
            const profile = await authService.getProfile();
            authService.updateCurrentUser(profile);
            setUser(profile);
        } catch (err) {
            const errData = err.response?.data;
            const msg = typeof errData === 'string' ? errData
                : errData?.message || errData?.error || errData?.billingHandler
                ? (errData.message || errData.error || JSON.stringify(errData))
                : 'Failed to update billing responsibility';
            toastError(msg);
        } finally {
            setSettingsLoading(false);
        }
    };

    const toggleInClinic = () => {
        const isCurrentlyInClinic = operationsSettings.inClinic !== false;
        const nextValue = !isCurrentlyInClinic;
        const title = isCurrentlyInClinic ? 'Disable In-Clinic Mode' : 'Enable In-Clinic Mode';
        const message = isCurrentlyInClinic
            ? 'Are you sure you want to disable In-Clinic medicine flow? The Medicine Inventory management tabs will be hidden from the Receptionist and Doctor dashboards.'
            : 'Are you sure you want to enable In-Clinic medicine flow? This will allow Receptionists and Doctors to manage active stock inventory and administer direct stock charges.';

        openConfirmation(title, message, async () => {
            try {
                setSettingsLoading(true);
                const updated = {
                    receptionMode: operationsSettings.receptionMode,
                    billingHandler: operationsSettings.billingHandler,
                    inClinic: nextValue
                };
                const data = await hospitalService.updateHospitalOperationsSettings(updated);
                const loaded = {
                    receptionMode: data.receptionMode || 'HAS_RECEPTIONIST',
                    billingHandler: data.billingHandler || 'RECEPTIONIST',
                    inClinic: data.inClinic !== false
                };
                setOperationsSettings(loaded);
                setOrigOperationsSettings(loaded);
                success('Operational settings updated successfully.');
                
                // Refresh local user profile session
                const profile = await authService.getProfile();
                authService.updateCurrentUser(profile);
                setUser(profile);
            } catch (err) {
                const msg = err.response?.data || 'Failed to update In-Clinic mode';
                toastError(msg);
            } finally {
                setSettingsLoading(false);
            }
        }, false);
    };

    const loadPatients = async (searchTermVal = patientsSearchTerm, pageNum = patientsPage) => {
        try {
            const patData = await hospitalService.getPatients(searchTermVal, pageNum, pageSize);
            if (patData.content) {
                setPatients(patData.content);
                setPatientsTotalPages(patData.totalPages);
                setPatientsTotalElements(patData.totalElements);
            } else {
                setPatients(patData);
                setPatientsTotalPages(1);
                setPatientsTotalElements(patData.length);
            }
        } catch (err) {
            console.error("Failed to load patients", err);
        }
    };

    const loadDatePatients = async (dateVal = patientDateFilter) => {
        setDatePatientsLoading(true);
        try {
            const data = await hospitalService.getPatientActivityByDate(dateVal);
            setDatePatients(data || []);
        } catch (err) {
            console.error("Failed to load patient activity for date", err);
            toastError("Failed to load patient activities");
        } finally {
            setDatePatientsLoading(false);
        }
    };

    const loadData = async (pageNum = page, sizeNum = pageSize, showSpinner = true) => {
        if (showSpinner) setLoading(true);
        try {
            if (activeTab === 'overview') {
                const [statsData, globalStatsData, todaysAppts, docData] = await Promise.all([
                    hospitalService.getAppointmentStats(),
                    hospitalService.getGlobalStats(),
                    hospitalService.getTodaysAppointments(),
                    hospitalService.getDoctors('', 0, 100)
                ]);
                setStats({ ...statsData, ...globalStatsData });
                setTodaysAppointments(todaysAppts);
                if (docData.content) {
                    setDoctors(docData.content);
                } else {
                    setDoctors(docData);
                }
                await loadPatients(patientsSearchTerm, patientsPage);
            } else if (activeTab === 'dashboard') {
                // Load dashboard data
                const [statsData, todaysAppts] = await Promise.all([
                    hospitalService.getGlobalStats(),
                    hospitalService.getTodaysAppointments()
                ]);
                setDashboardStats(statsData);
                setTodaysAppointments(todaysAppts);
            } else {
                // Always fetch stats when loading data to keep numbers fresh
                const statsData = await hospitalService.getAppointmentStats();
                setStats(statsData);

                if (activeTab === 'patients') {
                    const dateParam = patientTabView === 'Date' ? patientDateFilter : '';
                    const data = await hospitalService.getPatients(searchTerm, page, pageSize, dateParam);
                    if (data.content) {
                        setPatients(data.content);
                        setTotalPages(data.totalPages);
                        setTotalElements(data.totalElements);
                    } else {
                        // Fallback for list
                        setPatients(data);
                        setTotalPages(1);
                        setTotalElements(data.length);
                    }
                } else if (activeTab === 'doctors') {
                    const data = await hospitalService.getDoctors(searchTerm, page, pageSize);
                    if (data.content) {
                        setDoctors(data.content);
                        setTotalPages(data.totalPages);
                        setTotalElements(data.totalElements);
                    } else {
                        setDoctors(data);
                        setTotalPages(1);
                        setTotalElements(data.length);
                    }
                } else if (activeTab === 'receptionists') {
                    const data = await hospitalService.getReceptionists(searchTerm,page, pageSize);
                    if (data.content) {
                        setReceptionists(data.content);
                        setTotalPages(data.totalPages);
                        setTotalElements(data.totalElements);
                    } else {
                        setReceptionists(data);
                        setTotalPages(1);
                        setTotalElements(data.length);
                    }
                } else if (activeTab === 'pharmacists') {
                    const data = await hospitalService.getPharmacists(searchTerm,page, pageSize);
                    if (data.content) {
                        setPharmacists(data.content);
                        setTotalPages(data.totalPages);
                        setTotalElements(data.totalElements);
                    } else {
                        setPharmacists(data); // Fallback if API returns list directly
                        setTotalPages(1);
                        setTotalElements(data.length);
                    }
                } else if (activeTab === 'appointments') {
                    // Fetch both appointments and doctors (for name lookup)
                    // Note: getAppointments now supports page/size, getDoctors might not return all if paginated
                    // For now, we fetch paginated appointments and maybe "all" doctors for lookup if possible or handle missing names
                    const [apptData, docData, patData] = await Promise.all([
                        hospitalService.getAppointments(searchTerm,page, pageSize),
                        hospitalService.getDoctors('', 0, 100), // Attempt to get more doctors for lookup
                        hospitalService.getPatients('', 0, 1000) // Fetch ALL patients for lookup
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

                    if (docData.content) {
                        setDoctors(docData.content);
                    } else {
                        setDoctors(docData);
                    }

                    if (patData.content) {
                        setPatients(patData.content);
                    } else {
                        setPatients(patData);
                    }

                } else if (activeTab === 'billing') {
                    const data = await hospitalService.getBills(searchTerm,page, pageSize, billingStatus);
                    if (data && data.content) {
                        setBilling(Array.isArray(data.content) ? data.content : []);
                        setTotalPages(data.totalPages || 1);
                        setTotalElements(data.totalElements || 0);
                    } else if (Array.isArray(data)) {
                        setBilling(data);
                        setTotalPages(1);
                        setTotalElements(data.length);
                    } else {
                        setBilling([]);
                        setTotalPages(1);
                        setTotalElements(0);
                    }
                } else if (activeTab === 'ipd') {
                    try {
                        // Use admissions endpoint to show only currently admitted patients (same as receptionist)
                        const arr = await hospitalService.getAdmittedIpdAdmissions();
                        const filtered = (arr || []).filter(o => {
                            if (!searchTerm) return true;
                            const q = searchTerm.toLowerCase();
                            const row = o.ipd || o;
                            const ipdNumber = (row.ipdNumber || '').toString().toLowerCase();
                            const patient = (row.patientName || row.patient?.name || '').toLowerCase();
                            const doctor = (row.doctorName || row.doctor?.name || '').toLowerCase();
                            return ipdNumber.includes(q) || patient.includes(q) || doctor.includes(q);
                        });
                        setIpds(filtered);
                        setTotalPages(1);
                        setTotalElements(filtered.length);
                    } catch (err) {
                        console.error('Failed to load IPD admissions', err);
                        setIpds([]);
                        setTotalPages(1);
                        setTotalElements(0);
                    }
                } else if (activeTab === 'opd') {
                    try {
                        let dateParam = '';
                        let statusParam = '';
                        if (opdTabView === 'Live') {
                            dateParam = getISTDateString();
                            statusParam = 'QUEUED';
                        } else {
                            dateParam = opdDateFilter;
                            statusParam = ''; // show all statuses
                        }
                        const [data, docData, patData] = await Promise.all([
                            hospitalService.getOpds(searchTerm, page, pageSize, dateParam, statusParam),
                            hospitalService.getDoctors('', 0, 100),
                            hospitalService.getPatients('', 0, 1000)
                        ]);
                        if (data.content) {
                            setOpds(data.content);
                            setTotalPages(data.totalPages);
                            setTotalElements(data.totalElements);
                        } else {
                            const arr = Array.isArray(data) ? data : [];
                            setOpds(arr);
                            setTotalPages(1);
                            setTotalElements(arr.length);
                        }
                        // Also set doctors and patients for the modal dropdowns
                        setDoctors(docData.content || (Array.isArray(docData) ? docData : []));
                        setPatients(patData.content || (Array.isArray(patData) ? patData : []));
                    } catch (err) {
                        console.error('Failed to load OPD cases', err);
                        setOpds([]);
                        setTotalPages(1);
                        setTotalElements(0);
                    }
                } else if (activeTab === 'audit-logs') {
                    const data = await hospitalService.getAuditLogs(searchTerm, auditLogRoleFilter);
                    setAuditLogs(data);
                    setTotalPages(1); // Audit logs don't have pagination yet
                } else if (activeTab === 'analytics') {
                    const data = await hospitalService.getAnalyticsStats();
                    setAnalyticsData(data);
                }
            }
        } catch (err) {
            toastError('Failed to load data');
        } finally {
            if (showSpinner) setLoading(false);
        }
    };

    const handleLogout = () => {
        const loginUrl = authService.getLoginUrl();
        authService.logout();
        navigate(loginUrl);
    };

    // Generic confirmation handler
    const openConfirmation = (title, message, action, showReasonInput = false, inputPlaceholder = "Please provide a reason...") => {
        setConfirmModal({
            isOpen: true,
            title,
            message,
            onConfirm: action, // Action now accepts optional reason arg
            showReasonInput,
            inputPlaceholder
        });
    };

    /**
     * Handlers for Actions
     */

    const handleDeletePatient = (id) => {
        openConfirmation(
            'Delete Patient',
            'Are you sure you want to delete this patient?',
            async (reason) => {
                try {
                    await hospitalService.deletePatient(id);
                    success('Patient deleted successfully');
                    loadData();
                } catch (err) {
                    toastError('Failed to delete patient');
                }
            },
            false // Patient deletion might not need strict reason logging yet per requirements, but good to have. User said "deleting/editing key entities". let's enable it.
            // Actually, keep it false unless strictly required to avoid friction.
            // User: "Super Admin or Hospital Admin... deleting/editing key entities". Patients are key.
            // But let's stick to Doctor/Receptionist first as they are staff.
        );
    };

    const handleDeleteDoctor = (id) => {
        openConfirmation(
            'Delete Doctor',
            'Are you sure you want to delete this doctor? This action cannot be undone.',
            async (reason) => {
                try {
                    await hospitalService.deleteDoctor(id, reason);
                    success('Doctor deleted successfully');
                    loadData();
                } catch (err) {
                    toastError('Failed to delete doctor');
                }
            },
            true, // Require reason
            "Why are you deleting this doctor?"
        );
    };

    const handleDeleteReceptionist = (id) => {
        openConfirmation(
            'Delete Receptionist',
            'Are you sure you want to delete this receptionist?',
            async (reason) => {
                try {
                    await hospitalService.deleteReceptionist(id, reason);
                    success('Receptionist deleted successfully');
                    loadData();
                } catch (err) {
                    toastError('Failed to delete receptionist');
                }
            },
            true, // Require reason
            "Why are you deleting this receptionist?"
        );
    };

    const handleDeletePharmacist = (id) => {
        openConfirmation(
            'Delete Pharmacist',
            'Are you sure you want to delete this pharmacist?',
            async (reason) => {
                try {
                    await hospitalService.deletePharmacist(id, reason);
                    success('Pharmacist deleted successfully');
                    loadData();
                } catch (err) {
                    toastError('Failed to delete pharmacist');
                }
            },
            true,
            "Reason for deletion?"
        );
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

    const handleDownloadActivityReport = async () => {
        try {
            const blob = await hospitalService.downloadPatientActivityPdf(patientDateFilter);
            const url = window.URL.createObjectURL(blob);
            window.open(url, '_blank');
        } catch (err) {
            console.error('Failed to generate report', err);
            toastError('Failed to generate PDF report');
        }
    };

    const openPdfInNewTab = (endpointPath) => {
        const token = sessionStorage.getItem('token');
        const separator = endpointPath.includes('?') ? '&' : '?';
        const url = `${API_BASE_URL}${endpointPath}${separator}token=${encodeURIComponent(token)}`;
        window.open(url, '_blank');
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
            params.push(`date=${getISTDateString()}`);
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


    const handleDeleteAppointment = (id) => {
        openConfirmation(
            'Delete Appointment',
            'Are you sure you want to delete this appointment?',
            async (reason) => {
                console.log(`Deleting appointment ${id}. Reason: ${reason}`);
                try {
                    await hospitalService.deleteAppointment(id);
                    success('Appointment deleted successfully');
                    loadData(); // Reload all or specific tab?
                } catch (err) {
                    toastError('Failed to delete appointment');
                }
            },
            true,
            "Why are you deleting this appointment?"
        );
    };

    // Original status update logic without reason input
    const handleAppointmentStatusUpdate = async (id, newStatus) => {
        try {
            await hospitalService.updateAppointmentStatus(id, newStatus);
            success(`Appointment ${newStatus.toLowerCase()} successfully`);
            if (activeTab === 'appointments' || activeTab === 'overview') loadData();
            else if (activeTab === 'dashboard') loadData();
        } catch (err) {
            toastError(`Failed to update appointment status`);
        }
    };

    // For Cancellation (Status Update to CANCELLED)
    const onAppointmentStatusUpdate = (id, newStatus) => {
        if (newStatus === 'CANCELLED') {
            openConfirmation(
                'Cancel Appointment',
                'Are you sure you want to cancel this appointment?',
                async (reason) => {
                    console.log(`Cancelling appointment ${id}. Reason: ${reason}`);
                    try {
                        // Pass reason to updateAppointment if supported, or just log
                        // For now we assume updateAppointmentStatus just takes status
                        await hospitalService.updateAppointmentStatus(id, newStatus);
                        success('Appointment cancelled successfully');
                        // refresh
                        if (activeTab === 'appointments' || activeTab === 'overview') loadData();
                        else if (activeTab === 'dashboard') loadData();
                    } catch (err) {
                        toastError('Failed to cancel appointment');
                    }
                },
                true,
                "Reason for cancellation?"
            );
        } else {
            // Normal update
            handleAppointmentStatusUpdate(id, newStatus);
        }
    };

    const handleEdit = (item, type = null) => {
        setEditData(item);
        const modalTypeStr = (type && typeof type === 'string') ? type : activeTab;
        setModalType(modalTypeStr);
        setShowModal(true);
    };

    const handleViewDetails = (patient) => {
        setPatientDetailsModal({ isOpen: true, patient });
    };

    const handleViewStaffDetails = (staff, role) => {
        setStaffDetailsModal({ isOpen: true, staff, role });
    };

    const handleResetStaffPassword = (staff, role) => {
        setResetPasswordModal({ isOpen: true, staff, role });
        setResetPasswordForm({ newPassword: '', confirmPassword: '', showNew: false, showConfirm: false, submitting: false, error: '' });
    };

    const handleResetPasswordSubmit = async () => {
        const { newPassword, confirmPassword } = resetPasswordForm;
        if (!newPassword || newPassword.length < 6) {
            setResetPasswordForm(f => ({ ...f, error: 'Password must be at least 6 characters.' }));
            return;
        }
        if (newPassword !== confirmPassword) {
            setResetPasswordForm(f => ({ ...f, error: 'Passwords do not match.' }));
            return;
        }
        setResetPasswordForm(f => ({ ...f, submitting: true, error: '' }));
        try {
            const { staff, role } = resetPasswordModal;
            const id = staff.publicId || staff.id;
            if (role === 'doctor') {
                await hospitalService.resetDoctorPassword(id, newPassword);
            } else if (role === 'receptionist') {
                await hospitalService.resetReceptionistPassword(id, newPassword);
            } else if (role === 'pharmacist') {
                await hospitalService.resetPharmacistPassword(id, newPassword);
            }
            success('Password reset successfully');
            setResetPasswordModal({ isOpen: false, staff: null, role: '' });
        } catch (err) {
            const errorMsg = err.response?.data?.message || err.response?.data?.error || err.response?.data || 'Failed to reset password';
            setResetPasswordForm(f => ({ ...f, submitting: false, error: typeof errorMsg === 'string' ? errorMsg : 'Failed to reset password' }));
        }
    };


    const handleAdd = (type = null) => {
        setEditData(null); // Clear previous edit data
        const modalTypeStr = (type && typeof type === 'string') ? type : activeTab;
        setModalType(modalTypeStr);
        setShowModal(true);
    };

    const handleHistory = (type, id, name) => {
        setHistoryDrawer({
            isOpen: true,
            entityType: type,
            entityId: id,
            entityName: name
        });
    };

    const handleExportCSV = () => {
        if (!analyticsData || !analyticsData.monthlyTrends) return;
        let csvContent = "data:text/csv;charset=utf-8,";
        csvContent += "Month,OPD Consultations,IPD Admissions,Billing Revenue (INR),Pharmacy Revenue (INR),Total Revenue (INR)\r\n";
        analyticsData.monthlyTrends.forEach(item => {
            csvContent += `"${item.month}",${item.opdCount},${item.ipdCount},${item.billingRevenue},${item.pharmacyRevenue},${item.totalRevenue}\r\n`;
        });
        const encodedUri = encodeURI(csvContent);
        const link = document.createElement("a");
        link.setAttribute("href", encodedUri);
        link.setAttribute("download", `Hospital_Analytics_Report_${new Date().toISOString().split('T')[0]}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    const handleExportPDF = () => {
        if (!analyticsData) return;
        const printWindow = window.open("", "_blank");
        printWindow.document.write(`
            <html>
            <head>
                <title>Hospital Analytics Report</title>
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; padding: 40px; color: #111; }
                    .header { display: flex; align-items: center; justify-content: space-between; border-b: 2px solid #eaeaea; padding-bottom: 20px; margin-bottom: 30px; }
                    .hospital-name { font-size: 24px; font-weight: bold; }
                    .report-title { font-size: 16px; color: #666; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; }
                    .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 40px; }
                    .card { border: 1px solid #eaeaea; padding: 20px; border-radius: 12px; background: #fafafa; }
                    .card-title { font-size: 11px; color: #666; text-transform: uppercase; margin-bottom: 5px; font-weight: 600; }
                    .card-value { font-size: 24px; font-weight: 800; color: #111; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 40px; }
                    th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #eaeaea; }
                    th { background-color: #f9fafb; font-weight: 700; color: #374151; font-size: 13px; text-transform: uppercase; }
                    td { font-size: 14px; color: #4B5563; }
                    .section-title { font-size: 16px; font-weight: bold; margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 8px; color: #111; text-transform: uppercase; letter-spacing: 0.5px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div>
                        <div class="hospital-name">${user?.hospitalName || "Hospital"} Analytics Summary</div>
                        <div style="font-size: 12px; color: #666; margin-top: 4px;">Run Date: ${new Date().toLocaleDateString()}</div>
                    </div>
                    <div class="report-title">Executive Summary</div>
                </div>
                <div class="section-title">Key Performance Indicators</div>
                <div class="grid">
                    <div class="card">
                        <div class="card-title">Total Patients</div>
                        <div class="card-value">${analyticsData.totalPatients}</div>
                    </div>
                    <div class="card">
                        <div class="card-title">OPD Consultations</div>
                        <div class="card-value">${analyticsData.totalOPDConsultations}</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Bed Occupancy Rate</div>
                        <div class="card-value">${analyticsData.bedOccupancyRate}%</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Total Revenue</div>
                        <div class="card-value">₹${analyticsData.totalRevenue?.toLocaleString()}</div>
                    </div>
                </div>
                
                <div class="section-title">Monthly Volume & Revenue Trends</div>
                <table>
                    <thead>
                        <tr>
                            <th>Month</th>
                            <th>OPD Consultations</th>
                            <th>IPD Admissions</th>
                            <th>Billing Revenue</th>
                            <th>Pharmacy Sales</th>
                            <th>Total Revenue</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${analyticsData.monthlyTrends.map(t => 
                            "<tr>" +
                            "<td>" + t.month + "</td>" +
                            "<td>" + t.opdCount + "</td>" +
                            "<td>" + t.ipdCount + "</td>" +
                            "<td>₹" + (t.billingRevenue ? t.billingRevenue.toLocaleString() : "0") + "</td>" +
                            "<td>₹" + (t.pharmacyRevenue ? t.pharmacyRevenue.toLocaleString() : "0") + "</td>" +
                            "<td><strong>₹" + (t.totalRevenue ? t.totalRevenue.toLocaleString() : "0") + "</strong></td>" +
                            "</tr>"
                        ).join('')}
                    </tbody>
                </table>

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 30px;">
                    <div>
                        <div class="section-title">Doctor Consultation Load</div>
                        <table>
                            <thead>
                                <tr>
                                    <th>Doctor</th>
                                    <th>OPD Volume</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${analyticsData.doctorWorkload.map(d => 
                                    "<tr>" +
                                    "<td>" + d.doctorName + "</td>" +
                                    "<td>" + d.consultations + " cases</td>" +
                                    "</tr>"
                                ).join('')}
                            </tbody>
                        </table>
                    </div>
                    <div>
                        <div class="section-title">Ward Occupancy Utilization</div>
                        <table>
                            <thead>
                                <tr>
                                    <th>Ward</th>
                                    <th>Capacity</th>
                                    <th>Occupied</th>
                                    <th>Available</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${analyticsData.wardOccupancy.map(w => 
                                    "<tr>" +
                                    "<td>" + w.wardName + "</td>" +
                                    "<td>" + w.totalBeds + "</td>" +
                                    "<td>" + w.occupiedBeds + "</td>" +
                                    "<td>" + w.availableBeds + "</td>" +
                                    "</tr>"
                                ).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>

                <script>
                    window.onload = function() { window.print(); }
                </script>
            </body>
            </html>
        `);
        printWindow.document.close();
    };

    const allTabs = [
        { id: 'overview', label: 'Overview', icon: null, requiredModule: null },
        { id: 'patients', label: 'Patients', icon: null, requiredModule: 'OPD' },
        { id: 'appointments', label: 'Appointments', icon: null, requiredModule: 'APPOINTMENTS' },
        { id: 'opd', label: 'OPD', icon: null, requiredModule: 'OPD' },
        { id: 'wards', label: 'Wards & Beds', icon: null, requiredModule: 'IPD' },
        { id: 'doctors', label: 'Doctors', icon: null, requiredModule: 'OPD' },
        { id: 'receptionists', label: 'Receptionists', icon: null, requiredModule: 'OPD' },
        { id: 'billing', label: 'Billing', icon: null, requiredModule: 'BILLING' },
        { id: 'pharmacy', label: 'Pharmacy', icon: null, requiredModule: 'PHARMACY' },
        { id: 'pharmacists', label: 'Pharmacists', icon: null, requiredModule: 'PHARMACY' },
        { id: 'inventory', label: 'Medicine Inventory', icon: null, requiredModule: 'MEDICAL_INVENTORY' },
        { id: 'hospital-inventory', label: 'Hospital Inventory', icon: null, requiredModule: 'HOSPITAL_INVENTORY' },
        { id: 'pathology', label: 'Pathology', icon: null, requiredModule: 'PATHOLOGY' },
        { id: 'ipd', label: 'IPD', icon: null, requiredModule: 'IPD' },
        { id: 'ot', label: 'Operation Theatre', icon: null, requiredModule: 'OT' },
        { id: 'fees', label: 'Fees', icon: null, requiredModule: 'BILLING' },
        { id: 'audit-logs', label: 'Audit Logs', icon: null, requiredModule: null },
        { id: 'analytics', label: 'Reports & Analytics', icon: null, requiredModule: 'REPORTS' },
        { id: 'messages', label: 'Messages', icon: null, requiredModule: null },
        { id: 'settings', label: 'Settings', icon: null, requiredModule: null },
        { id: 'support', label: 'Support', icon: null, requiredModule: null },
    ];

    const tabs = allTabs.filter(tab =>
        !tab.requiredModule || modules.includes(tab.requiredModule)
    );
    // Pagination Object
    const pagination = {
        pageIndex: page,
        pageSize: pageSize,
        totalItems: totalElements,
        pageCount: totalPages,
        onPageChange: (newPage) => setPage(newPage)
    };

    // Payment Modal States (Admin Dashboard consistent implementation)
    const [paymentModal, setPaymentModal] = useState({ isOpen: false, billId: null, amount: null, patientName: '' });
    const [paymentSuccessModal, setPaymentSuccessModal] = useState({ isOpen: false, billId: null, amount: null, patientName: '' });
    const [paymentProcessing, setPaymentProcessing] = useState(false);

    const [billStatusUpdating, setBillStatusUpdating] = useState(null);
    const handleBillStatus = async (id, status, billObj = null) => {
        if (status === 'PAID') {
            setPaymentModal({ 
                isOpen: true, 
                billId: id,
                amount: billObj?.balance ?? billObj?.amount ?? null,
                patientName: billObj?.patientName || ''
            });
            return;
        }
        if (billStatusUpdating) return;
        setBillStatusUpdating(id);
        try {
            await hospitalService.updateBillStatus(id, status);
            success('Bill status updated');
            loadData();
        } catch (err) {
            toastError('Failed to update bill status');
        } finally {
            setBillStatusUpdating(null);
        }
    };

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

    const [printingReceiptId, setPrintingReceiptId] = useState(null);
    const handlePrintReceipt = async (id) => {
        if (printingReceiptId) return;
        setPrintingReceiptId(id);
        
        // Pre-open the window synchronously to bypass popup blocker
        const printWindow = window.open('', '_blank');
        if (printWindow) {
            printWindow.document.write('<p style="font-family: sans-serif; text-align: center; margin-top: 20px;">Generating receipt PDF, please wait...</p>');
        }
        
        try {
            const blob = await hospitalService.downloadReceipt(id);
            const url = window.URL.createObjectURL(blob);
            if (printWindow) {
                printWindow.document.open();
                printWindow.document.write(
                    '<!DOCTYPE html><html><head><title>Receipt</title></head>' +
                    '<body style="margin:0;padding:0;">' +
                    '<embed type="application/pdf" src="' + url + '" style="position:fixed;top:0;left:0;width:100%;height:100%;border:none;">' +
                    '</body></html>'
                );
                printWindow.document.close();
            }
        } catch (err) {
            console.error(err);
            if (printWindow) {
                printWindow.close();
            }
            toastError('Failed to load receipt for printing');
        } finally {
            setPrintingReceiptId(null);
        }
    };

    const patientsPagination = {
        pageIndex: patientsPage,
        pageSize: pageSize,
        totalItems: patientsTotalElements,
        pageCount: patientsTotalPages,
        onPageChange: (newPage) => setPatientsPage(newPage)
    };

    const filteredTodaysAppointments = todaysAppointments.filter(appt => {
        if (!appointmentsSearchTerm) return true;
        const term = appointmentsSearchTerm.toLowerCase();
        return (
            (appt.patientName && appt.patientName.toLowerCase().includes(term)) ||
            (appt.doctorName && appt.doctorName.toLowerCase().includes(term)) ||
            (appt.customId && appt.customId.toLowerCase().includes(term)) ||
            (appt.id && appt.id.toString().includes(term)) ||
            (appt.notes && appt.notes.toLowerCase().includes(term))
        );
    });

    const appointmentsPagination = {
        pageIndex: 0,
        pageSize: filteredTodaysAppointments.length || 10,
        totalItems: filteredTodaysAppointments.length,
        pageCount: 1,
        onPageChange: () => {}
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
            />

            {/* Main Content Wrapper */}
            <div className="flex-1 flex flex-col overflow-hidden">
                {/* Navbar */}
                <Navbar
                    title={activeTab}
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => setProfileOpen(true)}
                    onSupport={() => setActiveTab('support')}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                {/* Main Content Area */}
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-white p-8">

                    {/* Overview Tab - Stats & Inline Tables Split Grid */}
                    {activeTab === 'overview' && !loading && (
                        <div className="space-y-6">
                            <h2 className="text-2xl font-bold text-gray-900">Overview</h2>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div>
                                            <p className="text-gray-600 text-sm font-medium">Total Registered Patients</p>
                                            <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.totalRegisteredPatients || stats.totalPatients || 0}</h3>
                                        </div>
                                    </div>
                                </div>
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div>
                                            <p className="text-gray-600 text-sm font-medium">Patients This Month</p>
                                            <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.patientsThisMonth || 0}</h3>
                                        </div>
                                    </div>
                                </div>
                                <div className="bg-white rounded-lg border border-gray-200 p-6">
                                    <div className="flex justify-between items-center">
                                        <div>
                                            <p className="text-gray-600 text-sm font-medium">Patients Today</p>
                                            <h3 className="text-3xl font-bold text-gray-900 mt-1">{stats.patientsToday || 0}</h3>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mt-8">
                                {/* Left Div: Patients */}
                                <div className="bg-white rounded-2xl border border-neutral-200 overflow-hidden shadow-sm flex flex-col">
                                    {/* Head */}
                                    <div className="px-6 py-5 border-b border-neutral-100 bg-neutral-50/50 flex flex-row justify-between items-center">
                                        <div>
                                            <h3 className="text-lg font-bold text-slate-800">Patients</h3>
                                            <p className="text-xs text-slate-500 mt-0.5">Manage registered hospital patients</p>
                                        </div>
                                        {user?.role === 'HOSPITAL_ADMIN' && (
                                            <button
                                                onClick={() => handleAdd('patients')}
                                                className="bg-sky-600 hover:bg-sky-700 text-white px-4 py-2 rounded-xl text-sm font-semibold shadow-sm transform hover:-translate-y-0.5 transition-all flex items-center gap-1.5"
                                            >
                                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                                                    <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
                                                </svg>
                                                <span>Add Patient</span>
                                            </button>
                                        )}
                                    </div>
                                    {/* Body */}
                                    <div className="p-6 flex-1">
                                        {/* Search Input for patients */}
                                        <div className="relative mb-4">
                                            <input
                                                type="text"
                                                placeholder="Search patients..."
                                                value={patientsSearchInput}
                                                onChange={(e) => setPatientsSearchInput(e.target.value)}
                                                className="pl-9 pr-4 py-2 border border-neutral-300 rounded-xl text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent w-full transition-all bg-neutral-50 focus:bg-white text-slate-800 placeholder-slate-400"
                                            />
                                            <span className="absolute left-3 top-2.5 text-slate-400">
                                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                                </svg>
                                            </span>
                                        </div>
                                        {patients.length > 0 ? (
                                            <PatientsTable 
                                                patients={patients} 
                                                onEdit={(item) => handleEdit(item, 'patients')} 
                                                onViewDetails={handleViewDetails} 
                                                onDelete={handleDeletePatient} 
                                                onHistory={(p) => setPatientDetailsModal({ isOpen: true, patient: p })} 
                                                startIndex={patientsPage * pageSize} 
                                                pagination={patientsPagination} 
                                                isAdmin={user?.role === 'HOSPITAL_ADMIN'} 
                                            />
                                        ) : (
                                            <EmptyState
                                                icon={null}
                                                title="No Patients Found"
                                                message="There are no patients registered in the system yet."
                                                actionLabel="Add Patient"
                                                onAction={user?.role === 'HOSPITAL_ADMIN' ? () => handleAdd('patients') : null}
                                            />
                                        )}
                                    </div>
                                </div>

                                {/* Right Div: Today's Appointments */}
                                <div className="bg-white rounded-2xl border border-neutral-200 overflow-hidden shadow-sm flex flex-col">
                                    {/* Head */}
                                    <div className="px-6 py-5 border-b border-neutral-100 bg-neutral-50/50 flex flex-row justify-between items-center">
                                        <div>
                                            <h3 className="text-lg font-bold text-slate-800">Today's Appointments</h3>
                                            <p className="text-xs text-slate-500 mt-0.5">Quick overview of appointments for today</p>
                                        </div>
                                        {user?.role === 'HOSPITAL_ADMIN' && (
                                            <button
                                                onClick={() => handleAdd('appointments')}
                                                className="bg-sky-600 hover:bg-sky-700 text-white px-4 py-2 rounded-xl text-sm font-semibold shadow-sm transform hover:-translate-y-0.5 transition-all flex items-center gap-1.5"
                                            >
                                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                                                    <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
                                                </svg>
                                                <span>Add Appointment</span>
                                            </button>
                                        )}
                                    </div>
                                    {/* Body */}
                                    <div className="p-6 flex-1">
                                        {/* Search Input for appointments */}
                                        <div className="relative mb-4">
                                            <input
                                                type="text"
                                                placeholder="Search today's appointments by patient / doctor name..."
                                                value={appointmentsSearchTerm}
                                                onChange={(e) => setAppointmentsSearchTerm(e.target.value)}
                                                className="pl-9 pr-4 py-2 border border-neutral-300 rounded-xl text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent w-full transition-all bg-neutral-50 focus:bg-white text-slate-800 placeholder-slate-400"
                                            />
                                            <span className="absolute left-3 top-2.5 text-slate-400">
                                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                                </svg>
                                            </span>
                                        </div>
                                        {filteredTodaysAppointments.length > 0 ? (
                                            <AppointmentsTable 
                                                appointments={filteredTodaysAppointments} 
                                                doctors={doctors} 
                                                isAdmin={user?.role === 'HOSPITAL_ADMIN'} 
                                                onDelete={handleDeleteAppointment} 
                                                onStatusUpdate={onAppointmentStatusUpdate} 
                                                onHistory={(item) => handleHistory('APPOINTMENT', item.publicId || item.id, "Appointment")} 
                                                startIndex={0} 
                                                pagination={appointmentsPagination} 
                                            />
                                        ) : (
                                            <EmptyState
                                                icon={null}
                                                title="No Appointments Found"
                                                message="There are no appointments matching your search today."
                                                actionLabel="Schedule Appointment"
                                                onAction={user?.role === 'HOSPITAL_ADMIN' ? () => handleAdd('appointments') : null}
                                            />
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Standardized Header */}
                    {activeTab !== 'overview' && activeTab !== 'pharmacy' &&  activeTab !== 'ipd' &&  activeTab !== 'pathology' && activeTab !== 'support' && activeTab !== 'inventory' && activeTab !== 'hospital-inventory' && activeTab !== 'messages' && (
                        <PageHeader
                            title={tabs.find(t => t.id === activeTab)?.label}
                            subtitle={activeTab === 'settings' ? 'Configure operational settings and permissions' : activeTab === 'opd' ? 'Active patients currently in queue or being consulted' : `Manage hospital ${activeTab} records`}
                            onSearch={(activeTab === 'fees' || activeTab === 'settings') ? null : (e) => setSearchInput(e.target.value)}
                            searchValue={(activeTab === 'fees' || activeTab === 'settings') ? '' : searchInput}
                            searchPlaceholder={(activeTab === 'fees' || activeTab === 'settings') ? '' : `Search ${activeTab}...`}
                            onAdd={activeTab === 'opd' ? () => setIsAdminOpdModalOpen(true) : (activeTab !== 'billing' && activeTab !== 'audit-logs' && activeTab !== 'fees' && activeTab !== 'settings' && user?.role === 'HOSPITAL_ADMIN' ? handleAdd : null)}
                            addLabel={activeTab === 'opd' ? 'New OPD' : (activeTab === 'fees' || activeTab === 'settings') ? '' : `Add ${activeTab === 'patients' ? 'Patient' : activeTab === 'doctors' ? 'Doctor' : activeTab === 'receptionists' ? 'Receptionist' : activeTab === 'pharmacists' ? 'Pharmacist' : activeTab === 'appointments' ? 'Appointment' : activeTab === 'wards' ? 'Ward' : ''}`}
                            filter={activeTab === 'patients' ? (
                                <div className="flex items-center gap-2">
                                    <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200 h-[38px] items-center">
                                        {['All', 'Date'].map(view => (
                                            <button
                                                key={view}
                                                type="button"
                                                onClick={() => {
                                                    setPatientTabView(view);
                                                    setPage(0);
                                                    setSearchInput('');
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
                                            onChange={(e) => setPatientDateFilter(e.target.value)}
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
                                                    setSearchInput('');
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
                                            onChange={(e) => setOpdDateFilter(e.target.value)}
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
                            ) : activeTab === 'billing' ? (
                                <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200">
                                    {['PENDING', 'PAID', 'PARTIAL'].map(status => (
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
                            ) : activeTab === 'audit-logs' ? (
                                <div className="flex bg-gray-100 rounded-lg p-1 border border-gray-200 gap-1">
                                    {[
                                        { id: 'ALL', label: 'All' },
                                        { id: 'DOCTOR', label: 'Doctor' },
                                        { id: 'HOSPITAL_ADMIN', label: 'Admin' },
                                        ...(operationsSettings.receptionMode !== 'SOLO' ? [{ id: 'RECEPTIONIST', label: 'Reception' }] : []),
                                        ...(modules.includes('PHARMACY') ? [{ id: 'PHARMACIST', label: 'Pharmacy' }] : [])
                                    ].map(item => (
                                        <button
                                            key={item.id}
                                            onClick={() => setAuditLogRoleFilter(item.id)}
                                            className={`px-4 py-1.5 text-sm font-medium rounded-md transition-all ${auditLogRoleFilter === item.id
                                                ? 'bg-white text-blue-600 shadow-sm border border-gray-100 font-semibold'
                                                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                                }`}
                                        >
                                            {item.label}
                                        </button>
                                    ))}
                                </div>
                            ) : null}
                        />
                    )}

                    {/* Error Banner Removed - Using Toasts now */}

                    {loading && activeTab !== 'fees' && activeTab !== 'settings' ? (
                        activeTab === 'overview' ? (
                            <div className="space-y-8 animate-fade-in-up">
                                <SkeletonStatsGrid count={3} />
                                <SkeletonOverviewDual />
                            </div>
                        ) : (
                            <SkeletonTable rows={6} cols={5} />
                        )
                    ) : (
                        <>
                            {/* Overview tab content already rendered above */}

                            {(activeTab === 'patients' || activeTab === 'doctors' || activeTab === 'pharmacists' || activeTab === 'receptionists' || activeTab === 'wards' || activeTab === 'billing' || activeTab === 'fees' || activeTab === 'opd') && (
                                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden mb-4">
                                    {activeTab === 'patients' && (
                                        patients.length > 0 ? (
                                            <PatientsTable 
                                                patients={patients} 
                                                onEdit={(item) => handleEdit(item, 'patients')} 
                                                onViewDetails={handleViewDetails} 
                                                onDelete={handleDeletePatient} 
                                                onHistory={(p) => setPatientDetailsModal({ isOpen: true, patient: p })} 
                                                startIndex={page * pageSize} 
                                                pagination={pagination} 
                                                isAdmin={user?.role === 'HOSPITAL_ADMIN'} 
                                            />
                                        ) : (
                                            <EmptyState
                                                icon={null}
                                                title={patientTabView === 'Date' ? "No Registered Patients" : "No Patients Found"}
                                                message={patientTabView === 'Date' ? `No patients registered on ${patientDateFilter}.` : "Add patients to start scheduling appointments."}
                                                actionLabel={patientTabView === 'Date' ? null : (user?.role === 'HOSPITAL_ADMIN' ? "Add Patient" : null)}
                                                onAction={patientTabView === 'Date' ? null : (user?.role === 'HOSPITAL_ADMIN' ? handleAdd : null)}
                                            />
                                        )
                                    )}

                                    {activeTab === 'doctors' && (
                                    doctors.length > 0 ? (
                                        <DoctorsTable doctors={doctors} isAdmin={user?.role === 'HOSPITAL_ADMIN'} onEdit={handleEdit} onDelete={handleDeleteDoctor} onViewDetails={(doc) => handleViewStaffDetails(doc, 'doctor')} onResetPassword={(doc) => handleResetStaffPassword(doc, 'doctor')} startIndex={page * pageSize} pagination={pagination} />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No Doctors Found"
                                            message="Add doctors to start scheduling appointments."
                                            actionLabel="Add Doctor"
                                            onAction={user?.role === 'HOSPITAL_ADMIN' ? handleAdd : null}
                                        />
                                    )
                                )}

                                {activeTab === 'appointments' && (
                                    appointments.length > 0 ? (
                                        <AppointmentsTable
                                            appointments={appointments}
                                            doctors={doctors}
                                            isAdmin={user?.role === 'HOSPITAL_ADMIN'}
                                            onDelete={handleDeleteAppointment}
                                            onStatusUpdate={onAppointmentStatusUpdate}
                                            onHistory={(item) => handleHistory('APPOINTMENT', item.publicId || item.id, "Appointment")}
                                            startIndex={page * pageSize}
                                            pagination={pagination}
                                        />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No Appointments Found"
                                            message="No appointment records match your search."
                                            actionLabel="Schedule Appointment"
                                            onAction={user?.role === 'HOSPITAL_ADMIN' ? () => handleAdd('appointments') : null}
                                        />
                                    )
                                )}

                                {activeTab === 'pharmacists' && (
                                    pharmacists.length > 0 ? (
                                        <PharmacistsTable pharmacists={pharmacists} isAdmin={user?.role === 'HOSPITAL_ADMIN'} onDelete={handleDeletePharmacist} onEdit={(pharm) => handleEdit(pharm, 'pharmacists')} onViewDetails={(pharm) => handleViewStaffDetails(pharm, 'pharmacist')} onResetPassword={(pharm) => handleResetStaffPassword(pharm, 'pharmacist')} startIndex={page * pageSize} pagination={pagination} />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No Pharmacists Found"
                                            message="Add pharmacists to manage inventory and dispensing."
                                            actionLabel="Add Pharmacist"
                                            onAction={user?.role === 'HOSPITAL_ADMIN' ? handleAdd : null}
                                        />
                                    )
                                )}
                                {activeTab === 'receptionists' && (
                                    receptionists.length > 0 ? (
                                        <ReceptionistsTable 
                                            receptionists={receptionists} 
                                            isAdmin={user?.role === 'HOSPITAL_ADMIN'} 
                                            onDelete={handleDeleteReceptionist} 
                                            onEdit={(rec) => handleEdit(rec, 'receptionists')}
                                            onViewDetails={(rec) => handleViewStaffDetails(rec, 'receptionist')}
                                            onResetPassword={(rec) => handleResetStaffPassword(rec, 'receptionist')}
                                            startIndex={page * pageSize} 
                                            pagination={pagination} 
                                        />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No Receptionists Found"
                                            message="Add receptionists to help manage your hospital operations."
                                            actionLabel="Add Receptionist"
                                            onAction={user?.role === 'HOSPITAL_ADMIN' ? handleAdd : null}
                                        />
                                    )
                                )}

                                {activeTab === 'wards' && (
                                    <div className="p-6">
                                        <WardsAndBeds />
                                    </div>
                                )}
                                {activeTab === 'billing' && billing.length === 0 && (
                                    <EmptyState
                                        icon={null}
                                        title="No Billing Records"
                                        message="Billing module is active. Records will appear here."
                                    />
                                )}
                                {activeTab === 'billing' && billing.length > 0 && (
                                    <BillingTable
                                        billing={billing}
                                        startIndex={page * pageSize}
                                        pagination={pagination}
                                        onUpdateStatus={handleBillStatus}
                                        onPrint={handlePrintReceipt}
                                        updatingBillId={billStatusUpdating}
                                        printingBillId={printingReceiptId}
                                        onEditItems={handleOpenEditBillItems}
                                    />
                                )}
                                {activeTab === 'fees' && (
                                    <div className="p-6 max-w-6xl mx-auto">
                                        <h2 className="text-2xl font-bold mb-6 text-gray-900">Hospital Fees & Charges</h2>
                                        {feesLoading ? (
                                            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                                                <SkeletonFormCard fields={2} />
                                                <SkeletonTable rows={5} cols={3} />
                                            </div>
                                        ) : (
                                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
                                                {/* Standard Fees Card */}
                                                <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm space-y-6">
                                                    <div>
                                                        <h3 className="text-lg font-semibold text-gray-900 mb-1">Standard Fees</h3>
                                                        <p className="text-xs text-gray-500 mb-4">Default fees automatically applied to new patients and case papers.</p>
                                                    </div>
                                                    <div className="space-y-4">
                                                        <div>
                                                            <label className="block text-sm font-medium text-gray-700 mb-2">Consultation Fee (₹)</label>
                                                            <input
                                                                type="number"
                                                                step="0.01"
                                                                value={fees.consultationFee}
                                                                onChange={(e) => setFees(prev => ({ ...prev, consultationFee: e.target.value }))}
                                                                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${!feesEditing ? 'bg-gray-50 text-gray-600' : ''}`}
                                                                placeholder="0.00"
                                                                disabled={!feesEditing}
                                                            />
                                                        </div>
                                                        <div>
                                                            <label className="block text-sm font-medium text-gray-700 mb-2">Case Paper Fee (₹)</label>
                                                            <input
                                                                type="number"
                                                                step="0.01"
                                                                value={fees.casePaperFee}
                                                                onChange={(e) => setFees(prev => ({ ...prev, casePaperFee: e.target.value }))}
                                                                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${!feesEditing ? 'bg-gray-50 text-gray-600' : ''}`}
                                                                placeholder="0.00"
                                                                disabled={!feesEditing}
                                                            />
                                                        </div>
                                                        <div className="flex gap-3 pt-4">
                                                            {!feesEditing ? (
                                                                user?.role === 'HOSPITAL_ADMIN' ? (
                                                                    <button onClick={() => setFeesEditing(true)} className="flex-1 bg-gray-950 text-white px-4 py-2 rounded-lg font-medium hover:bg-gray-800 transition">Edit Standard Fees</button>
                                                                ) : (
                                                                    <div className="text-sm text-gray-500 text-center w-full">Only Hospital Admin can edit standard fees</div>
                                                                )
                                                            ) : (
                                                                <>
                                                                    <button onClick={() => { setFees(origFees || { consultationFee: '', casePaperFee: '' }); setFeesEditing(false); }} className="flex-1 bg-gray-200 text-gray-700 px-4 py-2 rounded-lg font-medium hover:bg-gray-300 transition">Cancel</button>
                                                                    <button onClick={handleSaveFees} disabled={feesLoading} className="flex-1 bg-gray-950 text-white px-4 py-2 rounded-lg font-medium hover:bg-gray-800 transition">Save Changes</button>
                                                                </>
                                                            )}
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Custom Fees Card */}
                                                <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm space-y-6">
                                                    <div className="flex justify-between items-center">
                                                        <div>
                                                            <h3 className="text-lg font-semibold text-gray-900 mb-1">Custom Charges</h3>
                                                            <p className="text-xs text-gray-500">Custom rates available for selection when editing hospital billing items.</p>
                                                        </div>
                                                        {user?.role === 'HOSPITAL_ADMIN' && (
                                                            <button
                                                                onClick={() => setCustomFeeModal({ isOpen: true, mode: 'add', feeId: null, name: '', defaultAmount: '' })}
                                                                className="bg-gray-950 text-white text-xs font-semibold px-3 py-2 rounded-lg hover:bg-gray-800 transition"
                                                            >
                                                                + Add Fee
                                                            </button>
                                                        )}
                                                    </div>

                                                    {customFees.length === 0 ? (
                                                        <div className="text-center py-8 border border-dashed border-gray-200 rounded-xl bg-gray-50">
                                                            <p className="text-sm text-gray-500">No custom fees added yet.</p>
                                                        </div>
                                                    ) : (
                                                        <div className="overflow-x-auto">
                                                            <table className="min-w-full divide-y divide-gray-200">
                                                                <thead>
                                                                    <tr>
                                                                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Fee Name</th>
                                                                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Default Rate</th>
                                                                        {user?.role === 'HOSPITAL_ADMIN' && <th className="px-4 py-2 text-right text-xs font-bold text-gray-500 uppercase tracking-wider">Actions</th>}
                                                                    </tr>
                                                                </thead>
                                                                <tbody className="divide-y divide-gray-200">
                                                                    {customFees.map(fee => (
                                                                        <tr key={fee.id}>
                                                                            <td className="px-4 py-3 text-sm font-medium text-gray-950">{fee.name}</td>
                                                                            <td className="px-4 py-3 text-sm text-gray-600">₹{fee.defaultAmount != null ? fee.defaultAmount.toFixed(2) : '0.00'}</td>
                                                                            {user?.role === 'HOSPITAL_ADMIN' && (
                                                                                <td className="px-4 py-3 text-right text-sm space-x-2">
                                                                                    <button
                                                                                        onClick={() => setCustomFeeModal({ isOpen: true, mode: 'edit', feeId: fee.id, name: fee.name, defaultAmount: fee.defaultAmount || '' })}
                                                                                        className="text-indigo-600 hover:text-indigo-900 font-medium"
                                                                                    >
                                                                                        Edit
                                                                                    </button>
                                                                                    <button
                                                                                        onClick={() => handleDeleteCustomFee(fee.id)}
                                                                                        className="text-red-600 hover:text-red-900 font-medium"
                                                                                    >
                                                                                        Delete
                                                                                    </button>
                                                                                </td>
                                                                            )}
                                                                        </tr>
                                                                    ))}
                                                                </tbody>
                                                            </table>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}

                                {activeTab === 'opd' && (
                                    opds.length > 0 ? (
                                        <AdminOpdTable
                                            opds={opds}
                                            onPrintOpd={handlePrintOpd}
                                            onAdmitToIpd={hasIPD ? (o) => { setAdminIpdOpdForAdmit(o); setIsAdminIpdAdmitOpen(true); } : null}
                                            startIndex={page * pageSize}
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
                            </div>
                        )}

                        {activeTab === 'inventory' && (
                            <MedicineInventoryTab />
                        )}

                        {activeTab === 'hospital-inventory' && (
                            <HospitalInventoryTab />
                        )}
                                {activeTab === 'settings' && (
                                    <div className="p-6 bg-white rounded-2xl border border-gray-200/80 shadow-sm max-w-4xl mx-auto my-4">
                                        <h2 className="text-xl font-bold mb-1 text-gray-900">Operations Settings</h2>
                                        <p className="text-sm text-gray-500 mb-8">Configure operational scenarios, staff access permissions, and billing responsibilities.</p>
                                        
                                        {settingsLoading ? (
                                            <SkeletonSettingsCard />
                                        ) : (
                                            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                                                {/* Receptionist Access Card */}
                                                <div className="bg-slate-50/50 rounded-2xl border border-gray-200 p-6 flex flex-col justify-between hover:shadow-md transition-all duration-300">
                                                    <div>
                                                        <div className="flex items-center justify-between mb-4">
                                                            <div className="p-3 bg-indigo-50 rounded-xl text-indigo-600">
                                                                <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                                                                </svg>
                                                            </div>
                                                            <span className={`px-3 py-1 text-xs font-semibold rounded-full ${operationsSettings.receptionMode === 'SOLO' ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'}`}>
                                                                {operationsSettings.receptionMode === 'SOLO' ? 'Self Manage' : 'Reception Mode'}
                                                            </span>
                                                        </div>
                                                        <h3 className="text-lg font-bold text-gray-900 mb-2">Operations Mode</h3>
                                                        <p className="text-sm text-gray-600 leading-relaxed mb-6">
                                                            Configure practice operations. Under Self Manage, billing and scheduling are managed directly by the doctor. Under Reception Mode, receptionists have system access.
                                                        </p>
                                                    </div>
                                                    <div className="flex items-center justify-between border-t border-gray-100 pt-4">
                                                        <span className="text-sm font-medium text-gray-700">Enable Reception Mode</span>
                                                        <button 
                                                            onClick={toggleReceptionMode}
                                                            className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2 ${
                                                                operationsSettings.receptionMode === 'HAS_RECEPTIONIST' ? 'bg-sky-600' : 'bg-gray-200'
                                                            }`}
                                                        >
                                                            <span className="sr-only">Toggle Reception Mode</span>
                                                            <span 
                                                                className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                                                                    operationsSettings.receptionMode === 'HAS_RECEPTIONIST' ? 'translate-x-5' : 'translate-x-0'
                                                                }`} 
                                                            />
                                                        </button>
                                                    </div>
                                                </div>

                                                {/* Billing Responsibility Card */}
                                                <div className="bg-slate-50/50 rounded-2xl border border-gray-200 p-6 flex flex-col justify-between hover:shadow-md transition-all duration-300">
                                                    <div>
                                                        <div className="flex items-center justify-between mb-4">
                                                            <div className="p-3 bg-emerald-50 rounded-xl text-emerald-600">
                                                                <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                                                                </svg>
                                                            </div>
                                                            <span className={`px-3 py-1 text-xs font-semibold rounded-full ${
                                                                operationsSettings.receptionMode === 'SOLO'
                                                                    ? 'bg-amber-100 text-amber-800'
                                                                    : operationsSettings.billingHandler === 'RECEPTIONIST'
                                                                    ? 'bg-emerald-100 text-emerald-800'
                                                                    : operationsSettings.billingHandler === 'DOCTOR'
                                                                    ? 'bg-blue-100 text-blue-800'
                                                                    : 'bg-purple-100 text-purple-800'
                                                            }`}>
                                                                {operationsSettings.receptionMode === 'SOLO'
                                                                    ? 'Doctor Managed (Forced)'
                                                                    : operationsSettings.billingHandler === 'RECEPTIONIST'
                                                                    ? 'Receptionist Managed'
                                                                    : operationsSettings.billingHandler === 'DOCTOR'
                                                                    ? 'Doctor Managed'
                                                                    : 'Both Managed'}
                                                            </span>
                                                        </div>
                                                        <h3 className="text-lg font-bold text-gray-900 mb-2">Billing Responsibility</h3>
                                                        <p className="text-sm text-gray-600 leading-relaxed mb-6">
                                                            Determine who handles billing and payment collection. In Self Manage mode, billing responsibility is restricted to the Doctor.
                                                        </p>
                                                    </div>
                                                    <div className="flex flex-col gap-2 border-t border-gray-100 pt-4">
                                                        <span className="text-sm font-medium text-gray-700">Billing Managed By</span>
                                                        <select
                                                            disabled={operationsSettings.receptionMode === 'SOLO'}
                                                            value={operationsSettings.billingHandler}
                                                            onChange={handleBillingHandlerChange}
                                                            className={`w-full border border-gray-300 rounded-lg px-3 py-1.5 focus:ring-2 focus:ring-sky-500 focus:border-transparent outline-none bg-white text-sm ${
                                                                operationsSettings.receptionMode === 'SOLO' ? 'bg-gray-100 text-gray-400 cursor-not-allowed' : 'text-gray-700'
                                                            }`}
                                                        >
                                                            <option value="RECEPTIONIST">Receptionist</option>
                                                            <option value="DOCTOR">Doctor</option>
                                                            <option value="BOTH">Both (Doctor & Receptionist)</option>
                                                        </select>
                                                    </div>
                                                </div>

                                                {/* In-Clinic Mode Card — only shown when IN_CLINIC module is in plan */}
                                                {modules.includes('IN_CLINIC') && <div className="bg-slate-50/50 rounded-2xl border border-gray-200 p-6 flex flex-col justify-between hover:shadow-md transition-all duration-300">
                                                    <div>
                                                        <div className="flex items-center justify-between mb-4">
                                                            <div className="p-3 bg-teal-50 rounded-xl text-teal-600">
                                                                <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 9.172V5L8 4z" />
                                                                </svg>
                                                            </div>
                                                            <span className={`px-3 py-1 text-xs font-semibold rounded-full ${operationsSettings.inClinic !== false ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'}`}>
                                                                {operationsSettings.inClinic !== false ? 'In-Clinic Enabled' : 'In-Clinic Disabled'}
                                                            </span>
                                                        </div>
                                                        <h3 className="text-lg font-bold text-gray-900 mb-2">In-Clinic Operations</h3>
                                                        <p className="text-sm text-gray-600 leading-relaxed mb-6">
                                                            Enable or disable active in-clinic stock inventory, low-stock warnings, and direct administered stock charges during OPD/IPD visits.
                                                        </p>
                                                    </div>
                                                    <div className="flex items-center justify-between border-t border-gray-100 pt-4">
                                                        <span className="text-sm font-medium text-gray-700">
                                                            {operationsSettings.inClinic !== false ? 'In-Clinic Flow Active' : 'In-Clinic Flow Halted'}
                                                        </span>
                                                        <button 
                                                            onClick={toggleInClinic}
                                                            className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2 ${
                                                                operationsSettings.inClinic !== false ? 'bg-sky-600' : 'bg-gray-200'
                                                            }`}
                                                        >
                                                            <span className="sr-only">Toggle In-Clinic Mode</span>
                                                            <span 
                                                                className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                                                                    operationsSettings.inClinic !== false ? 'translate-x-5' : 'translate-x-0'
                                                                }`} 
                                                            />
                                                        </button>
                                                    </div>
                                                </div>}
                                            </div>
                                        )}
                                    </div>
                                )}
                                {activeTab === 'pharmacy' && (
                                    <div className="space-y-6 animate-in fade-in duration-300">
                                        
                                        {/* Header Controls Panel */}
                                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-gradient-to-r from-emerald-600 to-teal-600 p-6 rounded-2xl text-white shadow-lg relative overflow-hidden">
                                            <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_107%,rgba(255,255,255,0.1)_0%,rgba(255,255,255,0)_50%)]"></div>
                                            <div className="relative z-10">
                                                <h2 className="text-2xl font-bold tracking-tight">Pharmacy Command Center</h2>
                                                <p className="text-emerald-100 text-sm mt-1">A centralized financial, tax, and inventory risk control panel for administrators.</p>
                                            </div>
                                            <div className="flex flex-wrap gap-3 relative z-10">
                                                <button
                                                    onClick={handleExportLedger}
                                                    className="px-4 py-2.5 bg-white/10 hover:bg-white/20 border border-white/20 text-white rounded-xl text-xs font-bold transition-all flex items-center gap-2 active:scale-95 shadow-inner"
                                                >
                                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                                    </svg>
                                                    <span>Export Tax Ledger CSV</span>
                                                </button>
                                            </div>
                                        </div>

                                        {pharmacyStatsLoading ? (
                                            <SkeletonDashboard statCount={4} tableRows={5} tableCols={4} />
                                        ) : (
                                            <div className="space-y-6">
                                                
                                                {/* KPI Financial Overview Grid */}
                                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                                                    
                                                    {/* Revenue Card */}
                                                    <div className="bg-white p-6 rounded-2xl border border-gray-150 shadow-sm hover:shadow-md transition-all duration-300 relative overflow-hidden group">
                                                        <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-50 rounded-bl-full -z-10 group-hover:scale-110 transition-transform duration-300"></div>
                                                        <div className="flex items-center justify-between mb-4">
                                                            <span className="p-3 bg-emerald-100/80 rounded-xl text-emerald-600">
                                                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                                </svg>
                                                            </span>
                                                        </div>
                                                        <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Net Sales Revenue</p>
                                                        <h3 className="text-3xl font-black text-slate-800 mt-2">₹{(pharmacyStats?.kpis?.netRevenue || 0).toLocaleString()}</h3>
                                                        <div className="flex items-center gap-1.5 mt-2.5 text-xs">
                                                            <span className="text-gray-400">Gross Sales:</span>
                                                            <span className="font-semibold text-slate-700">₹{(pharmacyStats?.kpis?.totalSales || 0).toLocaleString()}</span>
                                                        </div>
                                                    </div>

                                                    {/* Profitability Card */}
                                                    <div className="bg-white p-6 rounded-2xl border border-gray-150 shadow-sm hover:shadow-md transition-all duration-300 relative overflow-hidden group">
                                                        <div className="absolute top-0 right-0 w-24 h-24 bg-teal-50 rounded-bl-full -z-10 group-hover:scale-110 transition-transform duration-300"></div>
                                                        <div className="flex items-center justify-between mb-4">
                                                            <span className="p-3 bg-teal-100/80 rounded-xl text-teal-600">
                                                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 002 2h2a2 2 0 002-2" />
                                                                </svg>
                                                            </span>
                                                            <span className="px-2 py-0.5 bg-teal-50 border border-teal-200 text-teal-700 rounded text-[10px] font-black uppercase">
                                                                Margin {pharmacyStats?.kpis?.profitMargin || 0}%
                                                            </span>
                                                        </div>
                                                        <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Gross profit</p>
                                                        <h3 className="text-3xl font-black text-slate-800 mt-2">₹{(pharmacyStats?.kpis?.grossProfit || 0).toLocaleString()}</h3>
                                                        <p className="text-xs text-gray-400 mt-2.5">Reflects margins after cost of sales</p>
                                                    </div>

                                                    {/* Stock Value Card */}
                                                    <div className="bg-white p-6 rounded-2xl border border-gray-150 shadow-sm hover:shadow-md transition-all duration-300 relative overflow-hidden group">
                                                        <div className="absolute top-0 right-0 w-24 h-24 bg-blue-50 rounded-bl-full -z-10 group-hover:scale-110 transition-transform duration-300"></div>
                                                        <div className="flex items-center justify-between mb-4">
                                                            <span className="p-3 bg-blue-100/80 rounded-xl text-blue-600">
                                                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                                                                </svg>
                                                            </span>
                                                        </div>
                                                        <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Inventory Valuation</p>
                                                        <h3 className="text-3xl font-black text-slate-800 mt-2">₹{(pharmacyStats?.kpis?.inventoryValue || 0).toLocaleString()}</h3>
                                                        <p className="text-xs text-gray-400 mt-2.5">Asset value of non-expired stock</p>
                                                    </div>

                                                    {/* Expired Asset Risk Card */}
                                                    <div className="bg-white p-6 rounded-2xl border border-gray-150 shadow-sm hover:shadow-md transition-all duration-300 relative overflow-hidden group">
                                                        <div className="absolute top-0 right-0 w-24 h-24 bg-rose-50 rounded-bl-full -z-10 group-hover:scale-110 transition-transform duration-300"></div>
                                                        <div className="flex items-center justify-between mb-4">
                                                            <span className="p-3 bg-rose-100/80 rounded-xl text-rose-600">
                                                                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                                                </svg>
                                                            </span>
                                                            {(pharmacyStats?.kpis?.expiredValue || 0) > 0 && (
                                                                <span className="px-2 py-0.5 bg-rose-50 border border-rose-200 text-rose-700 rounded text-[10px] font-black uppercase animate-pulse">
                                                                    Risk Detected
                                                                </span>
                                                            )}
                                                        </div>
                                                        <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Expired Loss Assets</p>
                                                        <h3 className="text-3xl font-black text-rose-600 mt-2">₹{(pharmacyStats?.kpis?.expiredValue || 0).toLocaleString()}</h3>
                                                        <p className="text-xs text-gray-400 mt-2.5">Loss value of already expired stock</p>
                                                    </div>

                                                </div>

                                                {/* GST Tax Ledger Summary */}
                                                <div className="bg-gradient-to-br from-slate-900 to-slate-800 p-6 rounded-2xl border border-slate-950 shadow-md text-white">
                                                    <div className="flex items-center gap-3 mb-6">
                                                        <span className="p-2 bg-slate-800 text-emerald-400 rounded-xl">
                                                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                                            </svg>
                                                        </span>
                                                        <div>
                                                            <h3 className="font-bold text-base">Enterprise Tax & GST Summary</h3>
                                                            <p className="text-slate-400 text-xs mt-0.5">Summary of input credit vs output tax liabilities.</p>
                                                        </div>
                                                    </div>
                                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                                        <div className="bg-slate-800/40 p-4 border border-slate-700/50 rounded-xl">
                                                            <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">Input GST (On Purchases)</div>
                                                            <div className="text-2xl font-black text-slate-100 mt-1">₹{(pharmacyStats?.taxSummary?.inputGst || 0).toLocaleString()}</div>
                                                            <p className="text-[10px] text-emerald-400 font-semibold mt-1">Claimable Input Credit</p>
                                                        </div>
                                                        <div className="bg-slate-800/40 p-4 border border-slate-700/50 rounded-xl">
                                                            <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">Output GST (Collected on Sales)</div>
                                                            <div className="text-2xl font-black text-slate-100 mt-1">₹{(pharmacyStats?.taxSummary?.outputGst || 0).toLocaleString()}</div>
                                                            <p className="text-[10px] text-slate-400 mt-1">Collected tax liabilities</p>
                                                        </div>
                                                        <div className="bg-slate-800/40 p-4 border border-slate-700/50 rounded-xl relative overflow-hidden group">
                                                            <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">Net Tax Payable</div>
                                                            <div className="text-2xl font-black text-emerald-400 mt-1">₹{(pharmacyStats?.taxSummary?.netGstPayable || 0).toLocaleString()}</div>
                                                            <p className="text-[10px] text-slate-400 mt-1">Output tax minus Input credits</p>
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Expiry Risk & Top Moving Split Grid */}
                                                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                                                    
                                                    {/* Expiry Risk Card */}
                                                    <div className="bg-white border border-gray-150 rounded-2xl p-6 shadow-sm flex flex-col justify-between">
                                                        <div>
                                                            <div className="flex justify-between items-center mb-6">
                                                                <div className="flex items-center gap-2">
                                                                    <span className="p-2 bg-rose-50 text-rose-600 rounded-lg">
                                                                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                                        </svg>
                                                                    </span>
                                                                    <h4 className="font-bold text-sm text-slate-800">Expiry Risk Analysis (FEFO Risk Profile)</h4>
                                                                </div>
                                                            </div>
                                                            
                                                            <div className="space-y-4">
                                                                <div>
                                                                    <div className="flex justify-between text-xs text-gray-500 font-semibold mb-1">
                                                                        <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-rose-500 rounded-full"></span> Critical (Next 30 Days)</span>
                                                                        <span className="font-bold text-slate-800">₹{(pharmacyStats?.expiryRisk?.next30Days || 0).toLocaleString()}</span>
                                                                    </div>
                                                                    <div className="w-full bg-gray-100 h-2.5 rounded-full overflow-hidden">
                                                                        <div 
                                                                            className="bg-rose-500 h-full rounded-full transition-all duration-500" 
                                                                            style={{ width: `${Math.min(100, ((pharmacyStats?.expiryRisk?.next30Days || 0) / (pharmacyStats?.kpis?.inventoryValue || 1)) * 100)}%` }}
                                                                        ></div>
                                                                    </div>
                                                                </div>

                                                                <div>
                                                                    <div className="flex justify-between text-xs text-gray-500 font-semibold mb-1">
                                                                        <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-amber-500 rounded-full"></span> Warning (Next 60 Days)</span>
                                                                        <span className="font-bold text-slate-800">₹{(pharmacyStats?.expiryRisk?.next60Days || 0).toLocaleString()}</span>
                                                                    </div>
                                                                    <div className="w-full bg-gray-100 h-2.5 rounded-full overflow-hidden">
                                                                        <div 
                                                                            className="bg-amber-500 h-full rounded-full transition-all duration-500" 
                                                                            style={{ width: `${Math.min(100, ((pharmacyStats?.expiryRisk?.next60Days || 0) / (pharmacyStats?.kpis?.inventoryValue || 1)) * 100)}%` }}
                                                                        ></div>
                                                                    </div>
                                                                </div>

                                                                <div>
                                                                    <div className="flex justify-between text-xs text-gray-500 font-semibold mb-1">
                                                                        <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-blue-400 rounded-full"></span> Monitoring (Next 90 Days)</span>
                                                                        <span className="font-bold text-slate-800">₹{(pharmacyStats?.expiryRisk?.next90Days || 0).toLocaleString()}</span>
                                                                    </div>
                                                                    <div className="w-full bg-gray-100 h-2.5 rounded-full overflow-hidden">
                                                                        <div 
                                                                            className="bg-blue-400 h-full rounded-full transition-all duration-500" 
                                                                            style={{ width: `${Math.min(100, ((pharmacyStats?.expiryRisk?.next90Days || 0) / (pharmacyStats?.kpis?.inventoryValue || 1)) * 100)}%` }}
                                                                        ></div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <p className="text-[10px] text-gray-400 mt-4 leading-relaxed bg-neutral-50 p-3 rounded-lg border border-neutral-100">
                                                            <strong>Note:</strong> Risk profile represents absolute purchase cost asset valuation. Expiring batches within 30 days should be locked in the Expiry Manager.
                                                        </p>
                                                    </div>

                                                    {/* Top Fast-Moving Medicines */}
                                                    <div className="bg-white border border-gray-150 rounded-2xl p-6 shadow-sm flex flex-col justify-between">
                                                        <div>
                                                            <div className="flex justify-between items-center mb-6">
                                                                <div className="flex items-center gap-2">
                                                                    <span className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
                                                                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                                                                        </svg>
                                                                    </span>
                                                                    <h4 className="font-bold text-sm text-slate-800">Fast-Moving Medicines (Demand Leaderboard)</h4>
                                                                </div>
                                                            </div>

                                                            <div className="overflow-x-auto">
                                                                <table className="min-w-full text-xs text-left">
                                                                    <thead className="bg-neutral-50 text-slate-500 uppercase font-black tracking-wider border-b border-neutral-100">
                                                                        <tr>
                                                                            <th className="px-3 py-2.5">Medicine Name</th>
                                                                            <th className="px-3 py-2.5 text-right">Units Sold</th>
                                                                            <th className="px-3 py-2.5 text-right">Revenue</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody className="divide-y divide-neutral-50">
                                                                        {pharmacyStats?.fastMoving?.length > 0 ? (
                                                                            pharmacyStats.fastMoving.map((item, idx) => (
                                                                                <tr key={idx} className="hover:bg-neutral-50/50 transition-colors">
                                                                                    <td className="px-3 py-3 font-semibold text-slate-800">{item.name}</td>
                                                                                    <td className="px-3 py-3 text-right font-bold text-slate-600">{(item.quantity || 0).toLocaleString()}</td>
                                                                                    <td className="px-3 py-3 text-right font-black text-slate-900">₹{(item.revenue || 0).toLocaleString()}</td>
                                                                                </tr>
                                                                            ))
                                                                        ) : (
                                                                            <tr>
                                                                                <td colSpan="3" className="px-3 py-6 text-center text-gray-400 italic">No sales transactions recorded yet.</td>
                                                                            </tr>
                                                                        )}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        </div>
                                                    </div>

                                                </div>

                                                {/* Category Allocation Dashboard */}
                                                <div className="bg-white border border-gray-150 rounded-2xl p-6 shadow-sm">
                                                    <div className="flex items-center gap-2 mb-6">
                                                        <span className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
                                                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                                                            </svg>
                                                        </span>
                                                        <h4 className="font-bold text-sm text-slate-800">Category Stock Asset Distribution</h4>
                                                    </div>

                                                    <div className="overflow-x-auto">
                                                        <table className="min-w-full text-xs text-left">
                                                            <thead className="bg-neutral-50 text-slate-500 uppercase font-black tracking-wider border-b border-neutral-100">
                                                                <tr>
                                                                    <th className="px-4 py-3">Category</th>
                                                                    <th className="px-4 py-3 text-right">Unique Batches</th>
                                                                    <th className="px-4 py-3 text-right">Inventory Stock Value</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody className="divide-y divide-neutral-50">
                                                                {pharmacyStats?.categoryValuation?.length > 0 ? (
                                                                    pharmacyStats.categoryValuation.map((cat, idx) => (
                                                                        <tr key={idx} className="hover:bg-neutral-50/50 transition-colors">
                                                                            <td className="px-4 py-3 font-semibold text-slate-800">{cat.category}</td>
                                                                            <td className="px-4 py-3 text-right font-bold text-slate-600">{cat.count || 0}</td>
                                                                            <td className="px-4 py-3 text-right font-black text-slate-900">₹{(cat.value || 0).toLocaleString()}</td>
                                                                        </tr>
                                                                    ))
                                                                ) : (
                                                                    <tr>
                                                                        <td colSpan="3" className="px-4 py-6 text-center text-gray-400 italic">No non-expired active stock batches in system.</td>
                                                                    </tr>
                                                                )}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                </div>

                                            </div>
                                        )}
                                    </div>
                                )}
                                {activeTab === 'pathology' && (
                                    <div className="flex flex-col items-center justify-center p-12 text-center h-96">
                                        <h2 className="text-2xl font-bold text-gray-900">Pathology</h2>
                                        <p className="text-gray-600 mt-2">Pathology Module is currently under development.</p>
                                    </div>
                                )}
                                {activeTab === 'ot' && (
                                    <div className="flex flex-col items-center justify-center p-12 text-center h-96">
                                        <h2 className="text-2xl font-bold text-gray-950">Operation Theatre</h2>
                                        <p className="text-gray-600 mt-2 font-medium">This feature will is in process and will be available soon</p>
                                    </div>
                                )}
                                {activeTab === 'analytics' && (
                                    <div className="p-6 space-y-8 bg-gray-50/50 min-h-screen">
                                        {/* Header and filters */}
                                        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-6 rounded-2xl border border-gray-200 shadow-sm">
                                            <div>
                                                <h2 className="text-2xl font-bold text-gray-900">Reports & Analytics</h2>
                                                <p className="text-sm text-gray-600 mt-1">Real-time operational insight, patient volume trends, and revenue metrics.</p>
                                            </div>
                                            <div className="flex items-center gap-3">
                                                <button 
                                                    onClick={handleExportCSV}
                                                    className="inline-flex items-center justify-center px-4 py-2 text-sm font-semibold text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all cursor-pointer shadow-sm gap-2"
                                                >
                                                    <svg className="w-4 h-4 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                                    </svg>
                                                    Export CSV
                                                </button>

                                                <button 
                                                    onClick={handleExportPDF}
                                                    className="inline-flex items-center justify-center px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all cursor-pointer shadow-sm gap-2"
                                                >
                                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                                                    </svg>
                                                    Print PDF
                                                </button>
                                            </div>
                                        </div>

                                        {!analyticsData ? (
                                            <div className="space-y-6">
                                                <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                                                    <div className="h-32 bg-white rounded-2xl border border-gray-200 animate-pulse animate-duration-1000" />
                                                    <div className="h-32 bg-white rounded-2xl border border-gray-200 animate-pulse animate-duration-1000" />
                                                    <div className="h-32 bg-white rounded-2xl border border-gray-200 animate-pulse animate-duration-1000" />
                                                    <div className="h-32 bg-white rounded-2xl border border-gray-200 animate-pulse animate-duration-1000" />
                                                </div>
                                                <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                                                    <div className="h-80 bg-white rounded-2xl border border-gray-200 animate-pulse animate-duration-1000" />
                                                    <div className="h-80 bg-white rounded-2xl border border-gray-200 animate-pulse animate-duration-1000" />
                                                </div>
                                            </div>
                                        ) : (() => {
                                            const COLORS = ['#4F46E5', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6'];
                                            return (
                                                <>
                                                    {/* KPI Metric Cards */}
                                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm relative overflow-hidden group hover:shadow-md transition-all duration-300">
                                                            <div className="flex justify-between items-start">
                                                                <div>
                                                                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Total Patients</p>
                                                                    <h3 className="text-3xl font-extrabold text-gray-900 mt-2">{analyticsData.totalPatients}</h3>
                                                                </div>
                                                                <div className="p-3 bg-indigo-50 text-indigo-600 rounded-xl">
                                                                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                                                                    </svg>
                                                                </div>
                                                            </div>
                                                            <div className="mt-4 text-xs font-medium text-emerald-600 flex items-center gap-1">
                                                                <span>+8.4%</span>
                                                                <span className="text-gray-600">vs last month</span>
                                                            </div>
                                                        </div>

                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm relative overflow-hidden group hover:shadow-md transition-all duration-300">
                                                            <div className="flex justify-between items-start">
                                                                <div>
                                                                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">OPD Consultations</p>
                                                                    <h3 className="text-3xl font-extrabold text-gray-900 mt-2">{analyticsData.totalOPDConsultations}</h3>
                                                                </div>
                                                                <div className="p-3 bg-emerald-50 text-emerald-600 rounded-xl">
                                                                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                                                    </svg>
                                                                </div>
                                                            </div>
                                                            <div className="mt-4 text-xs font-medium text-emerald-600 flex items-center gap-1">
                                                                <span>+12.1%</span>
                                                                <span className="text-gray-600">vs last month</span>
                                                            </div>
                                                        </div>

                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm relative overflow-hidden group hover:shadow-md transition-all duration-300">
                                                            <div className="flex justify-between items-start">
                                                                <div>
                                                                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Bed Occupancy Rate</p>
                                                                    <h3 className="text-3xl font-extrabold text-gray-900 mt-2">{analyticsData.bedOccupancyRate}%</h3>
                                                                </div>
                                                                <div className="p-3 bg-amber-50 text-amber-600 rounded-xl">
                                                                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                                                                    </svg>
                                                                </div>
                                                            </div>
                                                            <div className="mt-4 text-xs text-gray-600 font-medium">
                                                                {analyticsData.occupiedBeds} of {analyticsData.totalBeds} beds occupied
                                                            </div>
                                                        </div>

                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm relative overflow-hidden group hover:shadow-md transition-all duration-300">
                                                            <div className="flex justify-between items-start">
                                                                <div>
                                                                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Total Revenue</p>
                                                                    <h3 className="text-3xl font-extrabold text-gray-900 mt-2">₹{analyticsData.totalRevenue?.toLocaleString()}</h3>
                                                                </div>
                                                                <div className="p-3 bg-rose-50 text-rose-600 rounded-xl">
                                                                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
                                                                    </svg>
                                                                </div>
                                                            </div>
                                                            <div className="mt-4 text-xs font-medium text-emerald-600 flex items-center gap-1">
                                                                <span>+15.3%</span>
                                                                <span className="text-gray-600">vs last month</span>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    {/* Charts Grid Row 1 */}
                                                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                                                        {/* Patient Volume Trends */}
                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm">
                                                            <div className="flex justify-between items-center mb-6">
                                                                <h3 className="text-lg font-bold text-gray-900">OPD & IPD Trends</h3>
                                                                <span className="text-xs font-medium px-2.5 py-1 bg-indigo-50 text-indigo-700 rounded-full">Monthly Counts</span>
                                                            </div>
                                                            <div className="h-80">
                                                                <ResponsiveContainer width="100%" height="100%">
                                                                    <AreaChart data={analyticsData.monthlyTrends} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                                                                        <defs>
                                                                            <linearGradient id="colorOpd" x1="0" y1="0" x2="0" y2="1">
                                                                                <stop offset="5%" stopColor="#4F46E5" stopOpacity={0.2}/>
                                                                                <stop offset="95%" stopColor="#4F46E5" stopOpacity={0}/>
                                                                            </linearGradient>
                                                                            <linearGradient id="colorIpd" x1="0" y1="0" x2="0" y2="1">
                                                                                <stop offset="5%" stopColor="#10B981" stopOpacity={0.2}/>
                                                                                <stop offset="95%" stopColor="#10B981" stopOpacity={0}/>
                                                                            </linearGradient>
                                                                        </defs>
                                                                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                                                                        <XAxis dataKey="month" tickLine={false} axisLine={false} style={{ fontSize: '12px', fill: '#6B7280' }} />
                                                                        <YAxis tickLine={false} axisLine={false} style={{ fontSize: '12px', fill: '#6B7280' }} />
                                                                        <Tooltip contentStyle={{ background: '#FFF', borderRadius: '12px', border: '1px solid #E5E7EB', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)' }} />
                                                                        <Legend iconType="circle" />
                                                                        <Area name="OPD Consultations" type="monotone" dataKey="opdCount" stroke="#4F46E5" strokeWidth={2.5} fillOpacity={1} fill="url(#colorOpd)" />
                                                                        <Area name="IPD Admissions" type="monotone" dataKey="ipdCount" stroke="#10B981" strokeWidth={2.5} fillOpacity={1} fill="url(#colorIpd)" />
                                                                    </AreaChart>
                                                                </ResponsiveContainer>
                                                            </div>
                                                        </div>

                                                        {/* Revenue Breakdown */}
                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm">
                                                            <div className="flex justify-between items-center mb-6">
                                                                <h3 className="text-lg font-bold text-gray-900">Revenue Contribution</h3>
                                                                <span className="text-xs font-medium px-2.5 py-1 bg-emerald-50 text-emerald-700 rounded-full">Billing vs Pharmacy</span>
                                                            </div>
                                                            <div className="h-80">
                                                                <ResponsiveContainer width="100%" height="100%">
                                                                    <BarChart data={analyticsData.monthlyTrends} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                                                                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                                                                        <XAxis dataKey="month" tickLine={false} axisLine={false} style={{ fontSize: '12px', fill: '#6B7280' }} />
                                                                        <YAxis tickLine={false} axisLine={false} style={{ fontSize: '12px', fill: '#6B7280' }} />
                                                                        <Tooltip formatter={(value) => `₹${value.toLocaleString()}`} contentStyle={{ background: '#FFF', borderRadius: '12px', border: '1px solid #E5E7EB', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)' }} />
                                                                        <Legend iconType="circle" />
                                                                        <Bar name="OPD/IPD Billing" dataKey="billingRevenue" stackId="a" fill="#4F46E5" radius={[0, 0, 0, 0]} />
                                                                        <Bar name="Pharmacy Sales" dataKey="pharmacyRevenue" stackId="a" fill="#10B981" radius={[4, 4, 0, 0]} />
                                                                    </BarChart>
                                                                </ResponsiveContainer>
                                                            </div>
                                                        </div>
                                                    </div>

                                                    {/* Charts Grid Row 2 */}
                                                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                                                        {/* Doctor Workload */}
                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm lg:col-span-2">
                                                            <h3 className="text-lg font-bold text-gray-900 mb-6">Doctor Workload (OPD Consultations)</h3>
                                                            {analyticsData.doctorWorkload.length === 0 ? (
                                                                <div className="flex flex-col items-center justify-center h-64 text-gray-500">
                                                                    No consultations recorded.
                                                                </div>
                                                            ) : (
                                                                <div className="h-72">
                                                                    <ResponsiveContainer width="100%" height="100%">
                                                                        <BarChart 
                                                                            layout="vertical"
                                                                            data={analyticsData.doctorWorkload} 
                                                                            margin={{ top: 10, right: 20, left: 30, bottom: 5 }}
                                                                        >
                                                                            <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#E5E7EB" />
                                                                            <XAxis type="number" tickLine={false} axisLine={false} style={{ fontSize: '11px', fill: '#6B7280' }} />
                                                                            <YAxis type="category" dataKey="doctorName" tickLine={false} axisLine={false} style={{ fontSize: '11px', fill: '#374151', fontWeight: '500' }} />
                                                                            <Tooltip contentStyle={{ background: '#FFF', borderRadius: '12px', border: '1px solid #E5E7EB' }} />
                                                                            <Bar name="Consultations" dataKey="consultations" fill="#4F46E5" radius={[0, 4, 4, 0]} barSize={16} />
                                                                        </BarChart>
                                                                    </ResponsiveContainer>
                                                                </div>
                                                            )}
                                                        </div>

                                                        {/* Appointment Status Donut */}
                                                        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm lg:col-span-1">
                                                            <h3 className="text-lg font-bold text-gray-900 mb-6">Appointment Status</h3>
                                                            <div className="h-52 relative flex items-center justify-center">
                                                                <ResponsiveContainer width="100%" height="100%">
                                                                    <PieChart>
                                                                        <Pie
                                                                            data={analyticsData.appointmentStatus}
                                                                            cx="50%"
                                                                            cy="50%"
                                                                            innerRadius={55}
                                                                            outerRadius={75}
                                                                            paddingAngle={4}
                                                                            dataKey="value"
                                                                        >
                                                                            {analyticsData.appointmentStatus.map((entry, index) => (
                                                                                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                                                            ))}
                                                                        </Pie>
                                                                        <Tooltip formatter={(value) => `${value} appointments`} />
                                                                    </PieChart>
                                                                </ResponsiveContainer>
                                                                <div className="absolute text-center">
                                                                    <span className="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Fulfillment</span>
                                                                    <p className="text-2xl font-black text-gray-900">{analyticsData.fulfillmentRate}%</p>
                                                                </div>
                                                            </div>
                                                            <div className="flex justify-around mt-4">
                                                                {analyticsData.appointmentStatus.map((entry, index) => (
                                                                    <div key={entry.name} className="flex flex-col items-center">
                                                                        <div className="flex items-center gap-1.5 text-xs text-gray-500 font-medium">
                                                                            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: COLORS[index % COLORS.length] }}></span>
                                                                            {entry.name}
                                                                        </div>
                                                                        <span className="text-xs font-bold text-gray-800 mt-1">{entry.value}</span>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    </div>

                                                    {/* Ward Bed Occupancy Breakdown */}
                                                    <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm">
                                                        <h3 className="text-lg font-bold text-gray-900 mb-6">Ward Capacity & Utilization</h3>
                                                        {analyticsData.wardOccupancy.length === 0 ? (
                                                            <div className="flex flex-col items-center justify-center h-48 text-gray-500">
                                                                No ward or bed records found.
                                                            </div>
                                                        ) : (
                                                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                                                {analyticsData.wardOccupancy.map((ward) => (
                                                                    <div key={ward.wardName} className="p-5 border border-gray-100 rounded-xl bg-gray-50/50">
                                                                        <div className="flex justify-between items-center mb-3">
                                                                            <h4 className="font-bold text-gray-800 text-sm">{ward.wardName}</h4>
                                                                            <span className="text-xs font-bold text-indigo-600 bg-indigo-50 px-2.5 py-1 rounded-lg">
                                                                                {ward.occupancyRate}% Occ.
                                                                            </span>
                                                                        </div>
                                                                        <div className="space-y-2">
                                                                            <div className="flex justify-between text-xs text-gray-600 font-medium">
                                                                                <span>Occupied Beds: <strong>{ward.occupiedBeds}</strong></span>
                                                                                <span>Available Beds: <strong>{ward.availableBeds}</strong></span>
                                                                            </div>
                                                                            <div className="w-full bg-gray-200 rounded-full h-2 overflow-hidden">
                                                                                <div 
                                                                                    className="h-full bg-indigo-600 rounded-full transition-all duration-500"
                                                                                    style={{ width: `${ward.occupancyRate}%` }}
                                                                                />
                                                                            </div>
                                                                            <div className="text-[10px] text-gray-500 font-medium text-right">
                                                                                Total Bed Capacity: {ward.totalBeds}
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        )}
                                                    </div>
                                                </>
                                            );
                                        })()}
                                    </div>
                                )}
                                {activeTab === 'messages' && (
                                    <MessagesTab modules={modules} />
                                )}
                                {activeTab === 'ipd' && (
                                    ipds.length > 0 ? (
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
                                                    {ipds.map((item, idx) => {
                                                         const row = item.ipd || item;
                                                         const ipdNumber = row.ipdNumber || row.ipd?.ipdNumber || row.ipdNumber;
                                                         const patientName = row.patientName || row.patient?.name || '-';
                                                         const doctorName = row.doctorName || row.doctor?.name || '-';
                                                         const wardName = row.wardName || row.ward?.name || '-';
                                                         const bedNumber = row.bedNumber || row.bed?.bedNumber || row.bed?.bedCode || row.bed?.name || '-';
                                                         const admittedAt = row.admissionDateTime || row.admissionDatetime || row.ipd?.admissionDatetime;
                                                         const status = row.status || row.ipd?.status || 'ADMITTED';
                                                         const theId = row.ipdId || row.id || row.ipd?.id || row.ipd?.ipdId || null;
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
                                                                         className={`px-3 py-1 rounded ${theId ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-500 cursor-not-allowed'}`}
                                                                         onClick={() => { if (theId) window.location.href = `/ipd/${theId}` }}
                                                                         disabled={!theId}
                                                                         title={theId ? 'View IPD details' : 'IPD id not available'}
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
                                            message="No IPD admissions found for this hospital."
                                        />
                                    )
                                )}
                                {activeTab === 'audit-logs' && (
                                    auditLogs.length > 0 ? (
                                        <AuditLogsTable auditLogs={auditLogs} />
                                    ) : (
                                        <EmptyState
                                            icon={null}
                                            title="No Audit Logs"
                                            message="No activity has been logged yet for your hospital."
                                        />
                                    )
                                )}

                            {activeTab === 'support' && (
                                <div className="space-y-6">
                                    <div className="flex flex-col md:flex-row md:items-center md:justify-between border-b border-neutral-200 pb-5">
                                        <div>
                                            <h2 className="text-2xl font-bold text-gray-900">Help & Support Center</h2>
                                            <p className="text-sm text-gray-500 mt-1">Get answers to frequently asked questions or raise a support ticket with our team.</p>
                                        </div>
                                        <div className="mt-4 md:mt-0">
                                            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-sky-50 text-sky-700 border border-sky-200">
                                                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                                                </svg>
                                                Secure Support Channel
                                            </span>
                                        </div>
                                    </div>

                                    {supportLoading ? (
                                        <SkeletonTable rows={4} cols={6} />
                                    ) : (
                                        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
                                            {/* Left 7 Columns: FAQs */}
                                            <div className="lg:col-span-7 space-y-6">
                                                <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6">
                                                    <div className="flex items-center justify-between mb-6">
                                                        <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
                                                            <svg className="w-5 h-5 text-sky-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                            </svg>
                                                            Frequently Asked Questions
                                                        </h3>
                                                        <span className="text-xs text-gray-400 font-medium">{faqs.length} articles</span>
                                                    </div>

                                                    {/* Local search bar for FAQs */}
                                                    <div className="relative mb-6">
                                                        <input
                                                            type="text"
                                                            placeholder="Search FAQ questions or keywords..."
                                                            value={faqSearch}
                                                            onChange={(e) => setFaqSearch(e.target.value)}
                                                            className="w-full pl-10 pr-4 py-2.5 bg-neutral-50 hover:bg-neutral-100/50 focus:bg-white border border-neutral-200 rounded-xl text-sm transition-all focus:ring-2 focus:ring-sky-500 focus:border-transparent text-slate-800 placeholder-slate-400"
                                                        />
                                                        <span className="absolute left-3.5 top-3.5 text-slate-400">
                                                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                                            </svg>
                                                        </span>
                                                    </div>

                                                    {faqs.filter(faq => 
                                                        faq.question.toLowerCase().includes(faqSearch.toLowerCase()) || 
                                                        faq.answer.toLowerCase().includes(faqSearch.toLowerCase())
                                                    ).length === 0 ? (
                                                        <div className="text-center py-12 border border-dashed border-gray-200 rounded-xl bg-gray-50/50">
                                                            <svg className="w-10 h-10 text-gray-300 mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                            </svg>
                                                            <p className="text-sm font-medium text-gray-600">No matching FAQs found</p>
                                                            <p className="text-xs text-gray-400 mt-1">Try searching for other keywords</p>
                                                        </div>
                                                    ) : (
                                                        <div className="space-y-3">
                                                            {faqs.filter(faq => 
                                                                faq.question.toLowerCase().includes(faqSearch.toLowerCase()) || 
                                                                faq.answer.toLowerCase().includes(faqSearch.toLowerCase())
                                                            ).map((faq) => {
                                                                const isExpanded = expandedFaqId === faq.id;
                                                                return (
                                                                    <div
                                                                        key={faq.id}
                                                                        className={`group rounded-xl border transition-all duration-300 ${
                                                                            isExpanded
                                                                                ? 'bg-neutral-50/70 border-sky-200 shadow-sm'
                                                                                : 'bg-white border-neutral-200/80 hover:border-neutral-300 hover:shadow-sm'
                                                                        }`}
                                                                    >
                                                                        <button
                                                                            onClick={() => setExpandedFaqId(isExpanded ? null : faq.id)}
                                                                            className="w-full text-left px-5 py-4 flex items-center justify-between gap-4 focus:outline-none"
                                                                        >
                                                                            <span className="font-semibold text-neutral-800 text-sm md:text-base group-hover:text-sky-600 transition-colors">
                                                                                {faq.question}
                                                                            </span>
                                                                            <span className={`flex-shrink-0 text-neutral-400 group-hover:text-neutral-600 transition-transform duration-300 ${isExpanded ? 'rotate-180 text-sky-500' : ''}`}>
                                                                                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                                                                                </svg>
                                                                            </span>
                                                                        </button>
                                                                        <div
                                                                            className={`grid transition-all duration-300 ease-in-out ${
                                                                                isExpanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0 pointer-events-none'
                                                                            }`}
                                                                        >
                                                                            <div className="overflow-hidden">
                                                                                <div className="px-5 pb-5 pt-1 text-sm text-neutral-600 leading-relaxed border-t border-neutral-100 bg-white/50 rounded-b-xl">
                                                                                    {faq.answer}
                                                                                </div>
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                );
                                                            })}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>

                                            {/* Right 5 Columns: Submit Ticket & List of submitted tickets */}
                                            <div className="lg:col-span-5 space-y-6">
                                                {/* Submit New Ticket Card */}
                                                <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6">
                                                    <h3 className="text-lg font-bold text-gray-900 mb-4 flex items-center gap-2">
                                                        <svg className="w-5 h-5 text-sky-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v3m0 0v3m0-3h3m-3 0H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                        </svg>
                                                        Raise a Support Ticket
                                                    </h3>
                                                    <form onSubmit={handleCreateTicket} className="space-y-4">
                                                        <div>
                                                            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">Subject</label>
                                                            <input
                                                                type="text"
                                                                value={ticketForm.subject}
                                                                onChange={(e) => setTicketForm(prev => ({ ...prev, subject: e.target.value }))}
                                                                placeholder="Briefly describe the issue..."
                                                                required
                                                                className="w-full px-3.5 py-2 border border-neutral-200 rounded-xl text-sm focus:ring-2 focus:ring-sky-500 focus:border-transparent text-slate-800 placeholder-slate-400"
                                                            />
                                                        </div>

                                                        <div>
                                                            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">Priority</label>
                                                            <div className="grid grid-cols-3 gap-2">
                                                                {['LOW', 'MEDIUM', 'HIGH'].map((p) => {
                                                                    const isSelected = ticketForm.priority === p;
                                                                    return (
                                                                        <button
                                                                            key={p}
                                                                            type="button"
                                                                            onClick={() => setTicketForm(prev => ({ ...prev, priority: p }))}
                                                                            className={`py-2 px-3 text-xs font-semibold rounded-xl border text-center transition-all duration-200 focus:outline-none ${
                                                                                isSelected
                                                                                    ? `${p === 'LOW' ? 'bg-sky-600 border-sky-600 text-white shadow-sm' : p === 'MEDIUM' ? 'bg-amber-500 border-amber-500 text-white shadow-sm' : 'bg-rose-600 border-rose-600 text-white shadow-sm'}`
                                                                                    : 'bg-white hover:bg-neutral-50 text-neutral-600 border-neutral-200'
                                                                            }`}
                                                                        >
                                                                            {p}
                                                                        </button>
                                                                    );
                                                                })}
                                                            </div>
                                                        </div>

                                                        <div>
                                                            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">Detailed Description</label>
                                                            <textarea
                                                                value={ticketForm.message}
                                                                onChange={(e) => setTicketForm(prev => ({ ...prev, message: e.target.value }))}
                                                                placeholder="Explain what went wrong, step by step..."
                                                                rows={4}
                                                                required
                                                                className="w-full px-3.5 py-2 border border-neutral-200 rounded-xl text-sm focus:ring-2 focus:ring-sky-500 focus:border-transparent text-slate-800 placeholder-slate-400 resize-none"
                                                            ></textarea>
                                                        </div>

                                                        <button
                                                            type="submit"
                                                            disabled={supportSubmitting}
                                                            className="w-full py-2.5 bg-gray-900 hover:bg-gray-800 text-white font-semibold text-sm rounded-xl transition-all duration-300 flex items-center justify-center gap-2 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-900 disabled:opacity-50"
                                                        >
                                                            {supportSubmitting ? (
                                                                <>
                                                                    <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                                                                    Submitting...
                                                                </>
                                                            ) : (
                                                                <>
                                                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                                                                    </svg>
                                                                    Submit Ticket
                                                                </>
                                                            )}
                                                        </button>
                                                    </form>
                                                </div>

                                                {/* Ticket History Card */}
                                                <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6">
                                                    <h3 className="text-lg font-bold text-gray-900 mb-4 flex items-center gap-2">
                                                        <svg className="w-5 h-5 text-sky-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
                                                        </svg>
                                                        Your Support Tickets
                                                    </h3>

                                                    {tickets.length === 0 ? (
                                                        <div className="text-center py-10 border border-dashed border-gray-200 rounded-xl bg-gray-50/50">
                                                            <p className="text-sm font-medium text-gray-500">No support tickets logged yet</p>
                                                            <p className="text-xs text-gray-400 mt-1">If you face any issues, submit a ticket above.</p>
                                                        </div>
                                                    ) : (
                                                        <div className="space-y-4 max-h-[350px] overflow-y-auto pr-1">
                                                            {tickets.map((t) => (
                                                                <div key={t.id} className="p-4 rounded-xl border border-neutral-100 hover:border-neutral-200/80 bg-neutral-50/30 hover:bg-neutral-50/70 transition-all duration-300">
                                                                    <div className="flex justify-between items-start gap-2 mb-2">
                                                                        <h4 className="font-semibold text-neutral-800 text-sm truncate flex-1">{t.subject}</h4>
                                                                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                                                                            t.status === 'RESOLVED' ? 'bg-emerald-100 text-emerald-800' : 'bg-sky-100 text-sky-800'
                                                                        }`}>
                                                                            {t.status}
                                                                        </span>
                                                                    </div>
                                                                    <p className="text-xs text-neutral-600 mb-3 line-clamp-2">{t.message}</p>
                                                                    <div className="flex justify-between items-center text-[10px] text-neutral-400">
                                                                        <span className={`px-2 py-0.5 rounded font-semibold ${
                                                                            t.priority === 'HIGH' ? 'bg-rose-50 text-rose-700 border border-rose-100' : t.priority === 'MEDIUM' ? 'bg-amber-50 text-amber-700 border border-amber-100' : 'bg-sky-50 text-sky-700 border border-sky-100'
                                                                        }`}>
                                                                            {t.priority} Priority
                                                                        </span>
                                                                        <span>{new Date(t.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' })} {new Date(t.createdAt).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true })}</span>
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </>
                    )}
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

            {/* Appointment Modal - Using Shared Component */}
            {showModal && modalType === 'appointments' && (
                <AppointmentModal
                    isOpen={showModal}
                    onClose={() => {
                        setShowModal(false);
                        setModalType(null);
                        setIsNewPatient(false); // Reset toggle
                    }}
                    onSuccess={() => {
                        setShowModal(false);
                        setModalType(null);
                        success('Record saved successfully');
                        loadData(page, pageSize, false);
                    }}
                    doctors={doctors}
                    patients={patients}
                />
            )}

            {/* Patient Modal - Using Shared Component */}
            {showModal && modalType === 'patients' && (
                <PatientModal
                    isOpen={showModal}
                    onClose={() => {
                        setShowModal(false);
                        setModalType(null);
                    }}
                    onSuccess={() => {
                        setShowModal(false);
                        setModalType(null);
                        success('Patient saved successfully');
                        loadData();
                    }}
                    initialData={editData}
                />
            )}

            {/* Admin OPD Intake Modal */}
            {isAdminOpdModalOpen && (
                <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-2xl shadow-xl w-full max-w-3xl animate-scale-in overflow-hidden max-h-[90vh]">
                        <div className="bg-white px-8 py-6 border-b border-gray-200">
                            <div className="flex justify-between items-center">
                                <div>
                                    <h3 className="text-2xl font-bold text-neutral-800">New OPD Case</h3>
                                    <p className="text-sm text-neutral-600 mt-1">Register a patient into the OPD queue</p>
                                </div>
                                <button onClick={() => { setIsAdminOpdModalOpen(false); setAdminOpdPatientSearch(''); setAdminOpdShowDropdown(false); setAdminOpdForm({ patientId: null, doctorId: null, bp: '', temperature: '', pulse: '', weight: '', spo2: '', problem: '', visitType: 'NEW' }); }} className="w-10 h-10 rounded-xl bg-white/80 hover:bg-white flex items-center justify-center text-neutral-400 hover:text-neutral-600 cursor-pointer border-0">
                                    <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                                </button>
                            </div>
                        </div>
                        <form onSubmit={async (e) => {
                            e.preventDefault();
                            if (!adminOpdForm.patientId) { toastError('Please select a valid patient from the suggestions'); return; }
                            if (!adminOpdForm.doctorId) { toastError('Please select a doctor'); return; }
                            try {
                                const payload = {
                                    patientId: adminOpdForm.patientId,
                                    doctorId: adminOpdForm.doctorId,
                                    bp: adminOpdForm.bp,
                                    temperature: adminOpdForm.temperature ? parseFloat(adminOpdForm.temperature) : null,
                                    pulse: adminOpdForm.pulse ? parseInt(adminOpdForm.pulse) : null,
                                    weight: adminOpdForm.weight ? parseFloat(adminOpdForm.weight) : null,
                                    spo2: adminOpdForm.spo2 ? parseInt(adminOpdForm.spo2) : null,
                                    problem: adminOpdForm.problem,
                                    visitType: adminOpdForm.visitType
                                };
                                const res = await hospitalService.createOpd(payload);
                                setIsAdminOpdModalOpen(false);
                                setAdminOpdPatientSearch('');
                                setAdminOpdForm({ patientId: null, doctorId: null, bp: '', temperature: '', pulse: '', weight: '', spo2: '', problem: '', visitType: 'NEW' });
                                success('OPD Case created — ID: ' + res.caseId);
                                loadData();
                            } catch (err) {
                                console.error('Failed to create OPD', err);
                                toastError('Failed to create OPD case');
                            }
                        }} className="p-6 space-y-4 max-h-[76vh] overflow-auto">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                {/* Patient Search */}
                                <div className="relative">
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Patient <span className="text-red-600">*</span></label>
                                    <div className="relative">
                                        <span className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-neutral-400">
                                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
                                        </span>
                                        <input
                                            type="text"
                                            className="w-full border border-gray-300 rounded-xl pl-10 pr-4 py-2 focus:ring-2 focus:ring-sky-500 text-sm focus:border-transparent text-slate-800"
                                            value={adminOpdPatientSearch}
                                            onChange={(e) => { setAdminOpdPatientSearch(e.target.value); setAdminOpdShowDropdown(true); setAdminOpdForm(prev => ({ ...prev, patientId: null })); }}
                                            onFocus={() => setAdminOpdShowDropdown(true)}
                                            onBlur={() => setTimeout(() => setAdminOpdShowDropdown(false), 250)}
                                            placeholder="Type patient name to search..."
                                            autoComplete="off"
                                        />
                                    </div>
                                    {adminOpdShowDropdown && adminOpdPatientSearch.trim().length >= 2 && (
                                        <div className="absolute left-0 right-0 mt-1 bg-white border border-neutral-200 rounded-xl shadow-xl z-50 max-h-60 overflow-y-auto divide-y divide-neutral-100">
                                            {patients.filter(p => p.name?.toLowerCase().includes(adminOpdPatientSearch.toLowerCase())).length > 0 ? (
                                                patients.filter(p => p.name?.toLowerCase().includes(adminOpdPatientSearch.toLowerCase())).map(p => (
                                                    <button type="button" key={p.id} onClick={() => { setAdminOpdForm(prev => ({ ...prev, patientId: p.id })); setAdminOpdPatientSearch(`${p.name}${p.phone ? ` (${p.phone})` : ''}`); setAdminOpdShowDropdown(false); }} className="w-full px-4 py-3 hover:bg-neutral-50 cursor-pointer transition-colors duration-150 flex flex-col gap-0.5 text-left border-0">
                                                        <span className="font-semibold text-neutral-800 text-sm">{p.name}</span>
                                                        <span className="text-xs text-neutral-500">{p.phone ? `📞 ${p.phone}` : ''} | {p.age} Yrs | {p.gender}</span>
                                                    </button>
                                                ))
                                            ) : (
                                                <div className="px-4 py-4 text-sm text-neutral-500 text-center">No matching patients found</div>
                                            )}
                                        </div>
                                    )}
                                </div>
                                {/* Doctor Select */}
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Doctor <span className="text-red-600">*</span></label>
                                    <select
                                        value={adminOpdForm.doctorId || ''}
                                        onChange={(e) => setAdminOpdForm(prev => ({ ...prev, doctorId: e.target.value }))}
                                        className="w-full border border-gray-300 rounded-xl px-4 py-2 focus:ring-2 focus:ring-sky-500 text-sm text-slate-800"
                                    >
                                        <option value="">Select Doctor...</option>
                                        {doctors.map(d => <option key={d.id} value={d.id}>{d.name}{d.specialization ? ` — ${d.specialization}` : ''}</option>)}
                                    </select>
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">BP</label>
                                    <input className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={adminOpdForm.bp} onChange={(e) => setAdminOpdForm(prev => ({ ...prev, bp: e.target.value }))} placeholder="120/80" />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Temperature (°C)</label>
                                    <input type="number" step="0.1" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={adminOpdForm.temperature} onChange={(e) => setAdminOpdForm(prev => ({ ...prev, temperature: e.target.value }))} />
                                </div>
                            </div>
                            <div className="grid grid-cols-3 gap-4">
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Pulse</label>
                                    <input type="number" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={adminOpdForm.pulse} onChange={(e) => setAdminOpdForm(prev => ({ ...prev, pulse: e.target.value }))} />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">Weight (kg)</label>
                                    <input type="number" step="0.1" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={adminOpdForm.weight} onChange={(e) => setAdminOpdForm(prev => ({ ...prev, weight: e.target.value }))} />
                                </div>
                                <div>
                                    <label className="block text-sm font-semibold text-neutral-700 mb-2">SpO2 (%)</label>
                                    <input type="number" className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800" value={adminOpdForm.spo2} onChange={(e) => setAdminOpdForm(prev => ({ ...prev, spo2: e.target.value }))} />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-semibold text-neutral-700 mb-2">Problem / Reason</label>
                                <textarea rows={3} className="w-full border border-gray-300 rounded-xl px-4 py-2 text-sm text-slate-800 resize-none" value={adminOpdForm.problem} onChange={(e) => setAdminOpdForm(prev => ({ ...prev, problem: e.target.value }))} />
                            </div>
                            <div className="flex items-center gap-4">
                                <label className="text-sm font-medium">Visit Type:</label>
                                <label className="inline-flex items-center gap-2 cursor-pointer"><input type="radio" name="adminVisitType" value="NEW" checked={adminOpdForm.visitType === 'NEW'} onChange={() => setAdminOpdForm(prev => ({ ...prev, visitType: 'NEW' }))} /> New</label>
                                <label className="inline-flex items-center gap-2 cursor-pointer"><input type="radio" name="adminVisitType" value="FOLLOWUP" checked={adminOpdForm.visitType === 'FOLLOWUP'} onChange={() => setAdminOpdForm(prev => ({ ...prev, visitType: 'FOLLOWUP' }))} /> Follow-up</label>
                            </div>
                            <div className="flex gap-4 pt-4">
                                <button type="button" onClick={() => { setIsAdminOpdModalOpen(false); setAdminOpdPatientSearch(''); setAdminOpdForm({ patientId: null, doctorId: null, bp: '', temperature: '', pulse: '', weight: '', spo2: '', problem: '', visitType: 'NEW' }); }} className="flex-1 py-2.5 rounded-xl border border-gray-300 font-semibold text-gray-700 hover:bg-gray-50 transition">Cancel</button>
                                <button type="submit" className="flex-1 py-2.5 rounded-xl bg-slate-900 text-white font-semibold hover:bg-slate-800 transition">Create OPD Case</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Admin IPD Admit Modal */}
            {isAdminIpdAdmitOpen && (
                <IpdAdmitModal
                    isOpen={isAdminIpdAdmitOpen}
                    onClose={() => { setIsAdminIpdAdmitOpen(false); setAdminIpdOpdForAdmit(null); }}
                    opd={adminIpdOpdForAdmit}
                    onSuccess={() => { loadData(); }}
                />
            )}

            {/* Ward modal (use specialized modal for wards) */}
            {showModal && modalType === 'wards' && (
                <WardModal
                    open={showModal}
                    initial={editData}
                    onClose={() => { setShowModal(false); setModalType(null); }}
                    onSaved={() => { setShowModal(false); setModalType(null); success('Record saved successfully'); loadData(); }}
                />
            )}

            {/* Other Modals - doctors, receptionists, billing (exclude wards) */}
            {showModal && modalType !== 'appointments' && modalType !== 'patients' && modalType !== 'wards' && modalType && (
                <AddModal
                    type={modalType}
                    onClose={() => {
                        setShowModal(false);
                        setModalType(null);
                    }}
                    onSuccess={() => {
                        setShowModal(false);
                        setModalType(null);
                        success('Record saved successfully');
                        loadData();
                    }}
                    doctors={doctors}
                    patients={patients}
                    openConfirmation={openConfirmation}
                    initialData={editData}
                />
            )}

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

            {/* Staff Details Modal */}
            {staffDetailsModal.isOpen && (
                <StaffDetailsModal
                    staff={staffDetailsModal.staff}
                    role={staffDetailsModal.role}
                    onClose={() => setStaffDetailsModal({ isOpen: false, staff: null, role: null })}
                />
            )}

            {/* Password Reset Result Modal */}
            {resetPasswordModal.isOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-fade-in">
                    <div className="bg-white w-full max-w-md rounded-2xl shadow-2xl border border-neutral-100 overflow-hidden">
                        <div className="p-6 border-b border-neutral-100 flex items-center justify-between">
                            <div>
                                <h3 className="text-lg font-bold text-slate-800">Reset Password</h3>
                                <p className="text-xs text-slate-500 mt-0.5">
                                    {resetPasswordModal.staff?.name} &middot; <span className="capitalize">{resetPasswordModal.role}</span>
                                </p>
                            </div>
                            <button
                                onClick={() => setResetPasswordModal({ isOpen: false, staff: null, role: '' })}
                                className="text-gray-400 hover:text-gray-600 transition"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                                </svg>
                            </button>
                        </div>
                        <div className="p-6 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold text-slate-600 mb-1.5">New Password</label>
                                <div className="relative">
                                    <input
                                        type={resetPasswordForm.showNew ? 'text' : 'password'}
                                        value={resetPasswordForm.newPassword}
                                        onChange={e => setResetPasswordForm(f => ({ ...f, newPassword: e.target.value, error: '' }))}
                                        placeholder="Enter new password"
                                        className="w-full px-3 py-2 pr-10 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-gray-900/10 focus:border-gray-900"
                                    />
                                    <button type="button" onClick={() => setResetPasswordForm(f => ({ ...f, showNew: !f.showNew }))} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                                        {resetPasswordForm.showNew
                                            ? <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd"/></svg>
                                            : <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.268 5.943 14.478 3 10 3a9.958 9.958 0 00-4.512 1.074l-1.78-1.781zm4.261 4.26l1.514 1.515a2.003 2.003 0 012.45 2.45l1.514 1.514a4 4 0 00-5.478-5.478z" clipRule="evenodd"/><path d="M12.454 16.697L9.75 13.992a4 4 0 01-3.742-3.741L2.335 6.578A9.98 9.98 0 00.458 10c1.274 4.057 5.065 7 9.542 7 .847 0 1.669-.105 2.454-.303z"/></svg>
                                        }
                                    </button>
                                </div>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-slate-600 mb-1.5">Confirm New Password</label>
                                <div className="relative">
                                    <input
                                        type={resetPasswordForm.showConfirm ? 'text' : 'password'}
                                        value={resetPasswordForm.confirmPassword}
                                        onChange={e => setResetPasswordForm(f => ({ ...f, confirmPassword: e.target.value, error: '' }))}
                                        placeholder="Confirm new password"
                                        className="w-full px-3 py-2 pr-10 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-gray-900/10 focus:border-gray-900"
                                        onKeyDown={e => e.key === 'Enter' && handleResetPasswordSubmit()}
                                    />
                                    <button type="button" onClick={() => setResetPasswordForm(f => ({ ...f, showConfirm: !f.showConfirm }))} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                                        {resetPasswordForm.showConfirm
                                            ? <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd"/></svg>
                                            : <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.268 5.943 14.478 3 10 3a9.958 9.958 0 00-4.512 1.074l-1.78-1.781zm4.261 4.26l1.514 1.515a2.003 2.003 0 012.45 2.45l1.514 1.514a4 4 0 00-5.478-5.478z" clipRule="evenodd"/><path d="M12.454 16.697L9.75 13.992a4 4 0 01-3.742-3.741L2.335 6.578A9.98 9.98 0 00.458 10c1.274 4.057 5.065 7 9.542 7 .847 0 1.669-.105 2.454-.303z"/></svg>
                                        }
                                    </button>
                                </div>
                            </div>
                            {resetPasswordForm.error && (
                                <p className="text-xs text-red-600 font-medium">{resetPasswordForm.error}</p>
                            )}
                        </div>
                        <div className="p-4 bg-neutral-50/50 border-t border-neutral-100 flex justify-end gap-3">
                            <button
                                onClick={() => setResetPasswordModal({ isOpen: false, staff: null, role: '' })}
                                disabled={resetPasswordForm.submitting}
                                className="px-4 py-2 text-sm font-semibold text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-xl transition"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleResetPasswordSubmit}
                                disabled={resetPasswordForm.submitting}
                                className="px-5 py-2 bg-slate-800 hover:bg-slate-900 text-white rounded-xl text-sm font-semibold shadow-md active:scale-95 transition-all disabled:opacity-60"
                            >
                                {resetPasswordForm.submitting ? 'Resetting...' : 'Reset Password'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Payment Modal (Admin Dashboard consistent implementation) */}
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
                                    <div className="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
                                        <h3 className="text-lg leading-6 font-bold text-gray-900 mb-4">
                                            Collect Payment
                                        </h3>
                                        <div className="mt-2 space-y-4">
                                            <div className="bg-neutral-50 p-4 rounded-xl">
                                                <div className="text-xs text-gray-400 uppercase font-bold tracking-wide">Patient Name</div>
                                                <div className="text-sm font-semibold text-gray-900 mt-0.5">{paymentModal.patientName}</div>
                                                <div className="text-xs text-gray-400 uppercase font-bold tracking-wide mt-3">Outstanding Amount</div>
                                                <div className="text-xl font-bold text-red-600 mt-0.5">₹{paymentModal.amount}</div>
                                            </div>
                                            <div className="grid grid-cols-2 gap-4">
                                                <button
                                                    onClick={() => handleProcessPayment('Cash')}
                                                    disabled={!!paymentProcessing}
                                                    className={`flex flex-col items-center justify-center gap-2 p-4 border rounded-xl transition-all duration-200 group ${paymentProcessing ? 'opacity-50 cursor-not-allowed bg-gray-50' : 'bg-white hover:bg-neutral-50 hover:border-neutral-300 active:scale-95'}`}
                                                >
                                                    <span className="font-semibold text-neutral-800 text-sm">Cash</span>
                                                </button>
                                                <button
                                                    onClick={() => handleProcessPayment('Online')}
                                                    disabled={!!paymentProcessing}
                                                    className={`flex flex-col items-center justify-center gap-2 p-4 border rounded-xl transition-all duration-200 group ${paymentProcessing ? 'opacity-50 cursor-not-allowed bg-gray-50' : 'bg-white hover:bg-neutral-50 hover:border-neutral-300 active:scale-95'}`}
                                                >
                                                    <span className="font-semibold text-neutral-800 text-sm">Online / UPI</span>
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
                                    <div className="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
                                        <h3 className="text-lg leading-6 font-bold text-gray-900">
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
                                                className="w-full flex items-center justify-center gap-2 p-3 bg-gray-900 text-white rounded-lg hover:bg-gray-800 transition-colors mb-2 text-sm font-semibold"
                                            >
                                                <span>Print Receipt</span>
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

            {/* Custom Fee Modal */}
            {customFeeModal.isOpen && (
                <div className="fixed inset-0 z-50 overflow-y-auto">
                    <div className="flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
                        <div className="fixed inset-0 transition-opacity" aria-hidden="true">
                            <div className="absolute inset-0 bg-gray-500 opacity-75" onClick={() => setCustomFeeModal({ ...customFeeModal, isOpen: false })}></div>
                        </div>
                        <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>
                        <form onSubmit={handleSaveCustomFee} className="inline-block align-bottom bg-white rounded-2xl text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-md sm:w-full border border-gray-200">
                            <div className="bg-white px-6 pt-6 pb-4">
                                <h3 className="text-lg leading-6 font-bold text-gray-900 mb-4">
                                    {customFeeModal.mode === 'add' ? 'Add Custom Hospital Fee' : 'Edit Custom Hospital Fee'}
                                </h3>
                                <div className="space-y-4">
                                    <div>
                                        <label className="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1">Fee Description / Name</label>
                                        <input
                                            type="text"
                                            required
                                            value={customFeeModal.name}
                                            onChange={(e) => setCustomFeeModal(prev => ({ ...prev, name: e.target.value }))}
                                            className="w-full px-4 py-2 border border-gray-200 rounded-xl focus:ring-2 focus:ring-sky-500 focus:border-transparent"
                                            placeholder="e.g. Dressing Charges, Lab Test A"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-bold text-gray-500 uppercase tracking-wide mb-1">Default Amount (₹)</label>
                                        <input
                                            type="number"
                                            step="0.01"
                                            value={customFeeModal.defaultAmount}
                                            onChange={(e) => setCustomFeeModal(prev => ({ ...prev, defaultAmount: e.target.value }))}
                                            className="w-full px-4 py-2 border border-gray-200 rounded-xl focus:ring-2 focus:ring-sky-500 focus:border-transparent"
                                            placeholder="0.00"
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="bg-gray-50 px-6 py-4 flex flex-row-reverse gap-3">
                                <button
                                    type="submit"
                                    disabled={customFeeSubmitting}
                                    className="px-4 py-2 bg-gray-900 text-white rounded-xl text-sm font-semibold hover:bg-gray-800 transition disabled:opacity-50"
                                >
                                    {customFeeSubmitting ? 'Saving...' : 'Save Fee'}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setCustomFeeModal({ isOpen: false, mode: 'add', feeId: null, name: '', defaultAmount: '' })}
                                    className="px-4 py-2 bg-white border border-gray-200 text-gray-700 rounded-xl text-sm font-semibold hover:bg-gray-50 transition"
                                >
                                    Cancel
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

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
                                                                    updateBillItem(index, 'defaultAmount', fees.consultationFee || '0');
                                                                } else if (val === 'case_paper') {
                                                                    updateBillItem(index, 'name', 'Case Paper Fee');
                                                                    updateBillItem(index, 'defaultAmount', fees.casePaperFee || '0');
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
                                                            <option value="consultation">Consultation Fee (₹{fees.consultationFee || 0})</option>
                                                            <option value="case_paper">Case Paper Fee (₹{fees.casePaperFee || 0})</option>
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

// Patient Activity Table Component for Date View
const PatientActivityTable = ({ activities }) => {
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.No.',
            cell: info => info.row.index + 1,
        }),
        columnHelper.accessor('patientId', {
            header: 'Patient ID',
            cell: info => <span className="font-semibold text-gray-700">{info.getValue() || 'N/A'}</span>,
        }),
        columnHelper.accessor('patientName', {
            header: 'Patient Name',
            cell: info => <span className="font-medium text-gray-900">{info.getValue() || 'Unknown'}</span>,
        }),
        columnHelper.accessor('phone', {
            header: 'Phone',
            cell: info => info.getValue() || 'N/A',
        }),
        columnHelper.accessor('activityType', {
            header: 'Activity Type',
            cell: info => {
                const type = info.getValue();
                switch (type) {
                    case 'OPD':
                        return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 border border-blue-200">OPD</span>;
                    case 'APPOINTMENT':
                        return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">Appointment</span>;
                    case 'IPD':
                        return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-orange-100 text-orange-800 border border-orange-200">IPD</span>;
                    default:
                        return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-gray-100 text-gray-800 border border-gray-200">{type}</span>;
                }
            }
        }),
        columnHelper.accessor('activityTime', {
            header: 'Time',
            cell: info => {
                const val = info.getValue();
                if (!val) return 'N/A';
                try {
                    const date = new Date(val);
                    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                } catch (e) {
                    return val;
                }
            }
        }),
        columnHelper.accessor('doctorName', {
            header: 'Doctor Name',
            cell: info => info.getValue() || 'N/A',
        }),
        columnHelper.accessor('details', {
            header: 'Details',
            cell: info => <span className="text-gray-500 font-mono text-xs">{info.getValue() || 'N/A'}</span>,
        }),
    ];

    return <DataTable data={activities} columns={columns} />;
};

// Patients Table Component
const PatientsTable = ({ patients, isAdmin, onDelete, onEdit, onViewDetails, onHistory, startIndex = 0, pagination }) => {
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
        columnHelper.accessor('name', {
            header: 'Name',
            cell: info => <span className="font-medium text-gray-900">{info.getValue()}</span>,
        }),
        columnHelper.accessor('age', {
            header: 'Age',
        }),
        columnHelper.accessor('gender', {
            header: 'Gender',
        }),
        columnHelper.accessor('phone', {
            header: 'Phone',
        }),
        columnHelper.accessor('address', {
            header: 'Address',
        }),
        ...(isAdmin ? [
            columnHelper.display({
                id: 'actions',
                header: () => <div className="text-right">Actions</div>,
                cell: info => (
                    <div className="text-right">
                        <ActionMenu actions={[
                            {
                                label: 'View Details',
                                onClick: () => onViewDetails(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z" /><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" /></svg>
                            },
                            {
                                label: 'Edit',
                                onClick: () => onEdit(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" /></svg>
                            },
                            {
                                label: 'Delete',
                                onClick: () => onDelete(info.row.original.publicId || info.row.original.id),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" /></svg>,
                                danger: true
                            }
                        ]} />
                    </div>
                ),
            })
        ] : []),
    ];

    return <DataTable data={patients} columns={columns} pagination={pagination} />;
};

// Doctors Table Component
const DoctorsTable = ({ doctors, isAdmin, onDelete, onEdit, onViewDetails, onResetPassword, startIndex = 0, pagination }) => {
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
        columnHelper.accessor('name', {
            header: 'Name',
            cell: info => <span className="font-medium text-gray-900">{info.getValue()}</span>,
        }),
        columnHelper.accessor('specialization', {
            header: 'Specialization',
        }),
        columnHelper.accessor('phone', {
            header: 'Phone',
        }),
        columnHelper.accessor('email', {
            header: 'Email',
        }),
        ...(isAdmin ? [
            columnHelper.display({
                id: 'actions',
                header: () => <div className="text-right">Actions</div>,
                cell: info => (
                    <div className="text-right">
                        <ActionMenu actions={[
                            {
                                label: 'View Details',
                                onClick: () => onViewDetails(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" /></svg>
                            },
                            {
                                label: 'Edit',
                                onClick: () => onEdit(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" /></svg>
                            },
                            {
                                label: 'Reset Password',
                                onClick: () => onResetPassword(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v-2l2-2 1-.743A6 6 0 1118 8zm-6-2a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" /></svg>
                            },
                            {
                                label: 'Delete',
                                onClick: () => onDelete(info.row.original.publicId || info.row.original.id),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" /></svg>,
                                danger: true
                            }
                        ]} />
                    </div>
                ),
            })
        ] : []),
    ];

    return <DataTable data={doctors} columns={columns} pagination={pagination} />;
};

// Appointments Table Component
const AppointmentsTable = ({ appointments, doctors, isAdmin, onDelete, onStatusUpdate, onHistory, startIndex = 0, pagination }) => {
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
        ...(isAdmin ? [
            columnHelper.display({
                id: 'actions',
                header: () => <div className="text-right">Actions</div>,
                cell: info => {
                    const status = info.row.original.status;
                    const isFinal = ['CANCELLED', 'COMPLETED'].includes(status);
                    
                    return (
                        <div className="text-right">
                            <ActionMenu actions={[
                                {
                                    label: 'History',
                                    onClick: () => onHistory(info.row.original),
                                    icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" /></svg>
                                },
                                ...(isFinal ? [] : [
                                    {
                                        label: 'Delete',
                                        onClick: () => onDelete(info.row.original.publicId || info.row.original.id),
                                        icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" /></svg>,
                                        danger: true
                                    }
                                ])
                            ]} />
                        </div>
                    );
                },
            })
        ] : []),
    ];

    return <DataTable data={appointments} columns={columns} pagination={pagination} />;
};



// Add/Edit Modal Component
const AddModal = ({ type, onClose, onSuccess, doctors, patients, openConfirmation, initialData, isNewPatient, setIsNewPatient }) => {
    const [formData, setFormData] = useState(initialData || {});
    const [errors, setErrors] = useState({}); // Changed to object for field-level errors
    const isEdit = !!initialData;
    const { error: toastError } = useToast(); // Use toast for backend errors

    useEffect(() => {
        if (initialData) {
            setFormData(initialData);
        } else {
            setFormData({});
        }
        setErrors({});
    }, [initialData]);

    // Auto-select doctor if only one is available for appointments or billing
    useEffect(() => {
        if (!initialData && doctors && doctors.length === 1 && (type === 'appointment' || type === 'billing')) {
            setFormData(prev => ({ ...prev, doctorId: doctors[0].id }));
        }
    }, [type, doctors, initialData]);

    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
        // Clear error for this field
        if (errors[field]) {
            setErrors(prev => ({ ...prev, [field]: null }));
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrors({});

        const rules = {};

        if (type === 'patients') {
            Object.assign(rules, {
                name: ['required', 'name'],
                phone: ['required', 'phone'],
                age: ['required', 'age'],
                gender: ['required'],
                address: ['required']
            });
        } else if (type === 'doctors') {
            Object.assign(rules, {
                name: ['required', 'name'],
                phone: ['required', 'phone'],
                email: ['required', 'email'],
                specialization: ['required', 'text']
            });
            if (!isEdit) {
                rules.password = ['required', 'password'];
            }
        } else if (type === 'receptionists') {
            Object.assign(rules, {
                name: ['required', 'name'],
                email: ['required', 'email'],
                phone: ['required', 'phone']
            });
            if (!isEdit) {
                rules.password = ['required', 'password'];
            }
        } else if (type === 'pharmacists') {
            Object.assign(rules, {
                name: ['required', 'name'],
                email: ['required', 'email'],
                phone: ['required', 'phone']
            });
            if (!isEdit) {
                rules.password = ['required', 'password'];
            }
        } else if (type === 'billing') {
            rules.amount = ['required', 'positiveNumber'];
        } else if (type === 'appointments') {
            rules.doctorId = ['required'];
            rules.appointmentDate = ['required'];
            if (isNewPatient) {
                Object.assign(rules, {
                    patientName: ['required', 'name'],
                    patientPhone: ['required', 'phone'],
                    patientAge: ['required', 'age'],
                    patientGender: ['required']
                });
            } else {
                rules.patientId = ['required'];
            }
        }

        const validationErrors = validateForm(formData, rules);
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }

        const action = isEdit ? 'Update' : 'Add';
        const entity = type === 'patients' ? 'Patient' : type === 'doctors' ? 'Doctor' : type === 'receptionists' ? 'Receptionist' : type === 'pharmacists' ? 'Pharmacist' : type === 'appointments' ? 'Appointment' : 'Billing Record';

        openConfirmation(
            `${action} ${entity}`,
            `Are you sure you want to ${action.toLowerCase()} this ${entity.toLowerCase()}?`,
            async () => {
                try {
                    if (type === 'patients') {
                        if (isEdit) await hospitalService.updatePatient(initialData.publicId || initialData.id, formData);
                        else await hospitalService.addPatient(formData);
                    } else if (type === 'doctors') {
                        if (isEdit) await hospitalService.updateDoctor(initialData.publicId || initialData.id, formData);
                        else await hospitalService.addDoctor(formData);
                    } else if (type === 'receptionists') {
                        if (isEdit) await hospitalService.updateReceptionist(initialData.publicId || initialData.id, formData);
                        else await hospitalService.addReceptionist(formData);
                    } else if (type === 'pharmacists') {
                        if (isEdit) await hospitalService.updatePharmacist(initialData.publicId || initialData.id, formData);
                        else await hospitalService.addPharmacist(formData);
                    } else if (type === 'appointments') {
                        // Appointments editing not supported in this modal yet
                        await hospitalService.createAppointment(formData);
                    } else if (type === 'billing') {
                        await hospitalService.createBilling(formData);
                    }
                    onSuccess();
                } catch (err) {
                    const errorMsg = err.response?.data?.message || err.response?.data?.error || (typeof err.response?.data === 'string' ? err.response.data : 'Failed to save record');
                    toastError(errorMsg); // Use toast for backend errors
                }
            }
        );
    };

    const isFieldDisabled = (field) => {
        if (!isEdit) return false;
        // Disable email/password editing for doctors, receptionists, pharmacists as per security rules
        if ((type === 'doctors' || type === 'receptionists' || type === 'pharmacists') && (field === 'email' || field === 'password')) return true;
        return false;
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" onClick={onClose}>
            <div className="bg-white rounded-lg border border-gray-200 w-full max-w-md max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                <div className="p-6">
                    <h2 className="text-2xl font-bold text-gray-900 mb-6 capitalize">{isEdit ? 'Edit' : 'Add'} {type.slice(0, -1)}</h2>

                    <form onSubmit={handleSubmit} className="space-y-4">

                        {type === 'patients' && (
                            <>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Name</label>
                                    <input
                                        type="text"
                                        placeholder="John Doe"
                                        value={formData.name || ''}
                                        onChange={(e) => handleChange('name', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Age</label>
                                    <input
                                        type="number"
                                        placeholder="30"
                                        value={formData.age || ''}
                                        onChange={(e) => handleChange('age', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.age ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.age && <p className="text-red-500 text-xs mt-1">{errors.age}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Gender</label>
                                    <select
                                        value={formData.gender || ''}
                                        onChange={(e) => handleChange('gender', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.gender ? 'border-red-500' : 'border-gray-300'}`}
                                    >
                                        <option value="">Select Gender</option>
                                        <option value="Male">Male</option>
                                        <option value="Female">Female</option>
                                        <option value="Other">Other</option>
                                    </select>
                                    {errors.gender && <p className="text-red-500 text-xs mt-1">{errors.gender}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Phone</label>
                                    <input
                                        type="tel"
                                        placeholder="10-digit number"
                                        value={formData.phone || ''}
                                        onChange={(e) => handleChange('phone', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.phone ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.phone && <p className="text-red-500 text-xs mt-1">{errors.phone}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Address</label>
                                    <textarea
                                        placeholder="123 Main St, City, Country"
                                        value={formData.address || ''}
                                        onChange={(e) => handleChange('address', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.address ? 'border-red-500' : 'border-gray-300'}`}
                                        rows="3"
                                    />
                                    {errors.address && <p className="text-red-500 text-xs mt-1">{errors.address}</p>}
                                </div>
                            </>
                        )}

                        {type === 'doctors' && (
                            <>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Name</label>
                                    <input
                                        type="text"
                                        placeholder="Dr. John Doe"
                                        value={formData.name || ''}
                                        onChange={(e) => handleChange('name', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Specialization</label>
                                    <input
                                        type="text"
                                        placeholder="Cardiology"
                                        value={formData.specialization || ''}
                                        onChange={(e) => handleChange('specialization', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.specialization ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.specialization && <p className="text-red-500 text-xs mt-1">{errors.specialization}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Phone</label>
                                    <input
                                        type="tel"
                                        placeholder="10-digit number"
                                        value={formData.phone || ''}
                                        onChange={(e) => handleChange('phone', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.phone ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.phone && <p className="text-red-500 text-xs mt-1">{errors.phone}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Email</label>
                                    <input
                                        type="email"
                                        placeholder="doctor@hospital.com"
                                        value={formData.email || ''}
                                        onChange={(e) => handleChange('email', e.target.value)}
                                        disabled={isFieldDisabled('email')}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.email ? 'border-red-500' : 'border-gray-300'} ${isFieldDisabled('email') ? 'bg-gray-100 cursor-not-allowed' : ''}`}
                                    />
                                    {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
                                </div>
                                {!isEdit && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">Password</label>
                                        <input
                                            type="password"
                                            placeholder="******"
                                            value={formData.password || ''}
                                            onChange={(e) => handleChange('password', e.target.value)}
                                            className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.password ? 'border-red-500' : 'border-gray-300'}`}
                                        />
                                        {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
                                    </div>
                                )}
                            </>
                        )}



                        {type === 'pharmacists' && (
                            <>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Name</label>
                                    <input
                                        type="text"
                                        placeholder="Pharmacist Name"
                                        value={formData.name || ''}
                                        onChange={(e) => handleChange('name', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Email</label>
                                    <input
                                        type="email"
                                        placeholder="pharmacist@hospital.com"
                                        value={formData.email || ''}
                                        onChange={(e) => handleChange('email', e.target.value)}
                                        disabled={isFieldDisabled('email')}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.email ? 'border-red-500' : 'border-gray-300'} ${isFieldDisabled('email') ? 'bg-gray-100 cursor-not-allowed' : ''}`}
                                    />
                                    {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Phone</label>
                                    <input
                                        type="tel"
                                        placeholder="10-digit number"
                                        value={formData.phone || ''}
                                        onChange={(e) => handleChange('phone', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.phone ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.phone && <p className="text-red-500 text-xs mt-1">{errors.phone}</p>}
                                </div>
                                {!isEdit && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">Password</label>
                                        <input
                                            type="password"
                                            placeholder="******"
                                            value={formData.password || ''}
                                            onChange={(e) => handleChange('password', e.target.value)}
                                            className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.password ? 'border-red-500' : 'border-gray-300'}`}
                                        />
                                        {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
                                    </div>
                                )}
                            </>
                        )}

                        {type === 'receptionists' && (
                            <>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Name</label>
                                    <input
                                        type="text"
                                        placeholder="Receptionist Name"
                                        value={formData.name || ''}
                                        onChange={(e) => handleChange('name', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.name && <p className="text-red-500 text-xs mt-1">{errors.name}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Email</label>
                                    <input
                                        type="email"
                                        placeholder="receptionist@hospital.com"
                                        value={formData.email || ''}
                                        onChange={(e) => handleChange('email', e.target.value)}
                                        disabled={isFieldDisabled('email')}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.email ? 'border-red-500' : 'border-gray-300'} ${isFieldDisabled('email') ? 'bg-gray-100 cursor-not-allowed' : ''}`}
                                    />
                                    {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Phone</label>
                                    <input
                                        type="tel"
                                        placeholder="10-digit number"
                                        value={formData.phone || ''}
                                        onChange={(e) => handleChange('phone', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.phone ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.phone && <p className="text-red-500 text-xs mt-1">{errors.phone}</p>}
                                </div>
                                {!isEdit && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">Password</label>
                                        <input
                                            type="password"
                                            placeholder="******"
                                            value={formData.password || ''}
                                            onChange={(e) => handleChange('password', e.target.value)}
                                            className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.password ? 'border-red-500' : 'border-gray-300'}`}
                                        />
                                        {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
                                    </div>
                                )}
                            </>
                        )}

                        {type === 'appointments' && (
                            <>
                                {/* Toggle for Existing/New Patient */}
                                <div className="mb-4 p-3 bg-gray-50 rounded-lg">
                                    <label className="flex items-center cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={isNewPatient}
                                            onChange={(e) => setIsNewPatient(e.target.checked)}
                                            className="mr-3 h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                                        />
                                        <span className="text-sm font-medium text-gray-700">
                                            New Patient (not registered yet)
                                        </span>
                                    </label>
                                </div>

                                {/* Conditional Patient Fields */}
                                {isNewPatient ? (
                                    <>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-2">Patient Name *</label>
                                            <input
                                                type="text"
                                                placeholder="Enter patient name"
                                                value={formData.patientName || ''}
                                                onChange={(e) => handleChange('patientName', e.target.value)}
                                                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.patientName ? 'border-red-500' : 'border-gray-300'}`}
                                            />
                                            {errors.patientName && <p className="text-red-500 text-xs mt-1">{errors.patientName}</p>}
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-2">Patient Phone *</label>
                                            <input
                                                type="tel"
                                                placeholder="Enter phone number"
                                                value={formData.patientPhone || ''}
                                                onChange={(e) => handleChange('patientPhone', e.target.value)}
                                                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.patientPhone ? 'border-red-500' : 'border-gray-300'}`}
                                            />
                                            {errors.patientPhone && <p className="text-red-500 text-xs mt-1">{errors.patientPhone}</p>}
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-2">Patient Age *</label>
                                            <input
                                                type="number"
                                                placeholder="Age"
                                                value={formData.patientAge || ''}
                                                onChange={(e) => handleChange('patientAge', e.target.value)}
                                                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.patientAge ? 'border-red-500' : 'border-gray-300'}`}
                                            />
                                            {errors.patientAge && <p className="text-red-500 text-xs mt-1">{errors.patientAge}</p>}
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-2">Patient Gender *</label>
                                            <select
                                                value={formData.patientGender || ''}
                                                onChange={(e) => handleChange('patientGender', e.target.value)}
                                                className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.patientGender ? 'border-red-500' : 'border-gray-300'}`}
                                            >
                                                <option value="">Select Gender</option>
                                                <option value="Male">Male</option>
                                                <option value="Female">Female</option>
                                                <option value="Other">Other</option>
                                            </select>
                                            {errors.patientGender && <p className="text-red-500 text-xs mt-1">{errors.patientGender}</p>}
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-2">Patient Email</label>
                                            <input
                                                type="email"
                                                placeholder="Enter email (optional)"
                                                value={formData.patientEmail || ''}
                                                onChange={(e) => handleChange('patientEmail', e.target.value)}
                                                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                                            />
                                        </div>
                                    </>
                                ) : (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">Patient *</label>
                                        <select
                                            value={formData.patientId || ''}
                                            onChange={(e) => handleChange('patientId', parseInt(e.target.value))}
                                            className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.patientId ? 'border-red-500' : 'border-gray-300'}`}
                                        >
                                            <option value="">Select Patient</option>
                                            {patients.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                                        </select>
                                        {errors.patientId && <p className="text-red-500 text-xs mt-1">{errors.patientId}</p>}
                                    </div>
                                )}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Doctor</label>
                                    {doctors && doctors.length === 1 ? (
                                        <div className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 text-gray-800 rounded-lg text-sm font-semibold flex items-center justify-between">
                                            <span>{doctors[0].name}</span>
                                            <span className="text-xs px-2 py-0.5 bg-green-50 text-green-700 border border-green-200 rounded-full font-medium">Assigned</span>
                                        </div>
                                    ) : (
                                        <select
                                            value={formData.doctorId || ''}
                                            onChange={(e) => handleChange('doctorId', parseInt(e.target.value))}
                                            className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.doctorId ? 'border-red-500' : 'border-gray-300'}`}
                                        >
                                            <option value="">Select Doctor</option>
                                            {doctors.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                        </select>
                                    )}
                                    {errors.doctorId && <p className="text-red-500 text-xs mt-1">{errors.doctorId}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Date</label>
                                    <input
                                        type="date"
                                        value={formData.appointmentDate || ''}
                                        onChange={(e) => handleChange('appointmentDate', e.target.value)}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.appointmentDate ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.appointmentDate && <p className="text-red-500 text-xs mt-1">{errors.appointmentDate}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Notes</label>
                                    <textarea
                                        value={formData.notes || ''}
                                        onChange={(e) => handleChange('notes', e.target.value)}
                                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                                        rows="3"
                                    />
                                </div>
                            </>
                        )}

                        {type === 'billing' && (
                            <>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Patient</label>
                                    <select
                                        value={formData.patientId || ''}
                                        onChange={(e) => handleChange('patientId', parseInt(e.target.value))}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.patientId ? 'border-red-500' : 'border-gray-300'}`}
                                    >
                                        <option value="">Select Patient</option>
                                        {patients.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                                    </select>
                                    {errors.patientId && <p className="text-red-500 text-xs mt-1">{errors.patientId}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Doctor</label>
                                    {doctors && doctors.length === 1 ? (
                                        <div className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 text-gray-800 rounded-lg text-sm font-semibold flex items-center justify-between">
                                            <span>{doctors[0].name}</span>
                                            <span className="text-xs px-2 py-0.5 bg-green-50 text-green-700 border border-green-200 rounded-full font-medium">Assigned</span>
                                        </div>
                                    ) : (
                                        <select
                                            value={formData.doctorId || ''}
                                            onChange={(e) => handleChange('doctorId', parseInt(e.target.value))}
                                            className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.doctorId ? 'border-red-500' : 'border-gray-300'}`}
                                        >
                                            <option value="">Select Doctor</option>
                                            {doctors.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                        </select>
                                    )}
                                    {errors.doctorId && <p className="text-red-500 text-xs mt-1">{errors.doctorId}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Amount</label>
                                    <input
                                        type="number"
                                        step="0.01"
                                        placeholder="0.00"
                                        value={formData.amount || ''}
                                        onChange={(e) => handleChange('amount', parseFloat(e.target.value))}
                                        className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${errors.amount ? 'border-red-500' : 'border-gray-300'}`}
                                    />
                                    {errors.amount && <p className="text-red-500 text-xs mt-1">{errors.amount}</p>}
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Description</label>
                                    <textarea
                                        value={formData.description || ''}
                                        onChange={(e) => handleChange('description', e.target.value)}
                                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                                        rows="3"
                                    />
                                </div>
                            </>
                        )}

                        <div className="flex gap-3 pt-4">
                            <button type="button" onClick={onClose} className="flex-1 bg-gray-200 text-gray-700 px-4 py-2 rounded-lg font-medium hover:bg-gray-300 transition">
                                Cancel
                            </button>
                            <button type="submit" className="flex-1 bg-gray-900 text-white px-4 py-2 rounded-lg font-medium hover:bg-gray-800 transition">
                                Add
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
};



// Receptionists Table Component
const ReceptionistsTable = ({ receptionists, isAdmin, onDelete, onEdit, onViewDetails, onResetPassword, startIndex = 0, pagination }) => {
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
        columnHelper.accessor('email', {
            header: 'EMAIL',
        }),
        ...(isAdmin ? [
            columnHelper.display({
                id: 'actions',
                header: () => <div className="text-right">ACTIONS</div>,
                cell: info => (
                    <div className="text-right">
                        <ActionMenu actions={[
                            {
                                label: 'View Details',
                                onClick: () => onViewDetails(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" /></svg>
                            },
                            {
                                label: 'Edit',
                                onClick: () => onEdit(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" /></svg>
                            },
                            {
                                label: 'Reset Password',
                                onClick: () => onResetPassword(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v-2l2-2 1-.743A6 6 0 1118 8zm-6-2a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" /></svg>
                            },
                            {
                                label: 'Delete',
                                onClick: () => onDelete(info.row.original.publicId || info.row.original.id),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" /></svg>,
                                danger: true
                            }
                        ]} />
                    </div>
                ),
            })
        ] : []),
    ];

    return <DataTable data={receptionists} columns={columns} pagination={pagination} />;
};

// AuditLogsTable Component
const AuditLogsTable = ({ auditLogs, startIndex = 0 }) => {
    const columnHelper = createColumnHelper();
    const [page, setPage] = useState(0);
    const pageSize = 10;

    // Helper: format date part only
    const formatDate = (timestamp) => {
        return new Date(timestamp).toLocaleDateString('en-IN', {
            day: '2-digit', month: 'short', year: 'numeric'
        });
    };

    // Helper: format time part only
    const formatTime = (timestamp) => {
        return new Date(timestamp).toLocaleTimeString('en-IN', {
            hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true
        }).toUpperCase();
    };

    // Helper function to get action badge color (unchanged)
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
                <div>
                    <p className="text-sm font-semibold text-gray-900">{formatDate(info.getValue())}</p>
                    <p className="text-xs text-gray-400 mt-0.5">{formatTime(info.getValue())}</p>
                </div>
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

// Pharmacists Table Component (Reusing similar structure)
const PharmacistsTable = ({ pharmacists, isAdmin, onDelete, onEdit, onViewDetails, onResetPassword, startIndex = 0, pagination }) => {
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
        columnHelper.accessor('email', {
            header: 'EMAIL',
        }),
        ...(isAdmin ? [
            columnHelper.display({
                id: 'actions',
                header: () => <div className="text-right">ACTIONS</div>,
                cell: info => (
                    <div className="text-right">
                        <ActionMenu actions={[
                            {
                                label: 'View Details',
                                onClick: () => onViewDetails(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" /></svg>
                            },
                            {
                                label: 'Edit',
                                onClick: () => onEdit(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" /></svg>
                            },
                            {
                                label: 'Reset Password',
                                onClick: () => onResetPassword(info.row.original),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v-2l2-2 1-.743A6 6 0 1118 8zm-6-2a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" /></svg>
                            },
                            {
                                label: 'Delete',
                                onClick: () => onDelete(info.row.original.publicId || info.row.original.id),
                                icon: <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" /></svg>,
                                danger: true
                            }
                        ]} />
                    </div>
                ),
            })
        ] : []),
    ];

    return <DataTable data={pharmacists} columns={columns} pagination={pagination} />;
};

const AdminOpdTable = ({ opds, onPrintOpd, onAdmitToIpd, startIndex = 0, pagination }) => {
    const columnHelper = createColumnHelper();

    const columns = [
        columnHelper.display({
            id: 'sno',
            header: 'S.NO.',
            cell: info => startIndex + info.row.index + 1,
        }),
        columnHelper.accessor('caseId', {
            header: 'CASE ID',
            cell: info => <span className="font-medium text-slate-800">{info.getValue() || info.row.original.id}</span>,
        }),
        columnHelper.accessor(row => row.patient?.name || row.patientName || '-', {
            id: 'patient',
            header: 'PATIENT',
        }),
        columnHelper.accessor(row => row.doctor?.name || row.doctorName || '-', {
            id: 'doctor',
            header: 'DOCTOR',
        }),
        columnHelper.accessor('visitType', {
            header: 'VISIT TYPE',
        }),
        columnHelper.accessor('status', {
            header: 'STATUS',
            cell: info => (
                <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full ${
                    info.getValue() === 'QUEUED' ? 'bg-amber-100 text-amber-800' :
                    info.getValue() === 'CONSULTING' ? 'bg-blue-100 text-blue-800' :
                    info.getValue() === 'CONSULTED' ? 'bg-indigo-100 text-indigo-800' :
                    info.getValue() === 'COMPLETED' ? 'bg-emerald-100 text-emerald-800' :
                    'bg-slate-100 text-slate-800'
                }`}>
                    {info.getValue() === 'QUEUED' ? '⏳ In Queue' : info.getValue() === 'CONSULTING' ? '🩺 Consulting' : info.getValue()}
                </span>
            )
        }),
        columnHelper.accessor('createdAt', {
            header: 'REGISTERED AT',
            cell: info => new Date(info.getValue()).toLocaleString(),
        }),
        columnHelper.display({
            id: 'actions',
            header: () => <div className="text-right">ACTIONS</div>,
            cell: info => (
                <div className="text-right flex items-center justify-end gap-2">
                    <button
                        onClick={() => onPrintOpd(info.row.original)}
                        className="bg-sky-50 text-sky-700 hover:bg-sky-100 px-3 py-1 rounded-md text-xs font-semibold shadow-sm transition-all inline-flex items-center gap-1"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M5 4v3H4a2 2 0 00-2 2v3a2 2 0 002 2h1v2a2 2 0 002 2h6a2 2 0 002-2v-2h1a2 2 0 002-2V9a2 2 0 00-2-2h-1V4a2 2 0 00-2-2H7a2 2 0 00-2 2zm8 0H7v3h6V4zm0 8H7v4h6v-4z" clipRule="evenodd" />
                        </svg>
                        Print
                    </button>
                    {info.row.original.status !== 'IN_IPD' && (
                        <button
                            onClick={() => onAdmitToIpd && onAdmitToIpd(info.row.original)}
                            className="bg-emerald-50 text-emerald-700 hover:bg-emerald-100 px-3 py-1 rounded-md text-xs font-semibold shadow-sm transition-all inline-flex items-center gap-1"
                        >
                            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
                            Admit to IPD
                        </button>
                    )}
                </div>
            ),
        }),
    ];

    return <DataTable data={opds} columns={columns} pagination={pagination} />;
};

export default HospitalAdminDashboard;