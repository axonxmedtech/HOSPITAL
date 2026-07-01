import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import hospitalService from '../../services/hospitalService';
import authService from '../../services/authService';
import cdssService from '../../services/cdssService';
import CdssAlertModal from '../../components/CdssAlertModal';
import masterDataService from '../../services/masterDataService';
import SearchableSelect from '../../components/SearchableSelect';
import wardService from '../../services/wardService';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';
import { SkeletonDetailCard, SkeletonFormCard } from '../../components/Skeleton';
import useWebSocket from '../../hooks/useWebSocket';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import ProfileModal from '../../components/ProfileModal';
import ConfirmationModal from '../../components/ConfirmationModal';
import DoctorOrdersPanel from '../../components/nurse/DoctorOrdersPanel';
import LabResultsPanel from '../../components/lab/LabResultsPanel';
import RadiologyResultsPanel from '../../components/radiology/RadiologyResultsPanel';
import DoctorRoundsPanel from '../../components/doctor/DoctorRoundsPanel';
import OtWorkflowPanel from '../../components/ot/OtWorkflowPanel';
import ConsentPanel from '../../components/ipd/ConsentPanel';
import RiskAssessmentPanel from '../../components/ipd/RiskAssessmentPanel';
import FluidChartPanel from '../../components/ipd/FluidChartPanel';
import NursingProgressPanel from '../../components/ipd/NursingProgressPanel';
import ClinicalAssessmentPanel from '../../components/ipd/ClinicalAssessmentPanel';

const IpdDetails = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [data, setData] = useState(null);
    const [user, setUser] = useState(() => authService.getCurrentUser() || {});
    const isDoctor = authService.isDoctor();
    const isReceptionist = authService.isReceptionist();
    const isSoloDoctor = isDoctor && user?.receptionMode === 'SOLO';
    const { success, error: toastError } = useToast();

    const parseError = (err, fallback) => {
        if (!err) return fallback;
        if (err.response?.data) {
            const data = err.response.data;
            if (typeof data === 'object') {
                return data.error || data.message || JSON.stringify(data);
            }
            return data;
        }
        return err.message || fallback;
    };

    // Layout States
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);

    const handleLogout = () => {
        const loginUrl = authService.getLoginUrl();
        authService.logout();
        navigate(loginUrl);
    };

    const isSolo = user?.receptionMode === 'SOLO';
    const hasBilling = user?.billingHandler === 'DOCTOR' || user?.billingHandler === 'BOTH';
    const hasInClinic = user?.inClinic !== false;
    const modules = user?.modules || [];

    const activeDashboard = sessionStorage.getItem('activeDashboard');
    const effectiveRole = (user?.role === 'HOSPITAL_ADMIN' && user?.isSingleDoctor && activeDashboard !== 'admin')
        ? 'DOCTOR'
        : user?.role;

    const getSidebarTabs = () => {
        if (effectiveRole === 'HOSPITAL_ADMIN') {
            const adminAllTabs = [
                { id: 'overview', label: 'Overview', requiredModule: 'OPD' },
                { id: 'patients', label: 'Patients', requiredModule: 'OPD' },
                { id: 'opd', label: 'OPD', requiredModule: 'OPD' },
                { id: 'wards', label: 'Wards & Beds', requiredModule: 'IPD' },
                { id: 'doctors', label: 'Doctors', requiredModule: 'OPD' },
                { id: 'receptionists', label: 'Receptionists', requiredModule: 'OPD' },
                { id: 'billing', label: 'Billing', requiredModule: 'BILLING' },
                { id: 'pharmacy', label: 'Pharmacy', requiredModule: 'PHARMACY' },
                { id: 'pharmacists', label: 'Pharmacists', requiredModule: 'PHARMACY' },
                { id: 'inventory', label: 'Medicine Inventory', requiredModule: 'OPD' },
                { id: 'hospital-inventory', label: 'Hospital Inventory', requiredModule: 'OPD' },
                { id: 'pathology', label: 'Pathology', requiredModule: 'PATHOLOGY' },
                { id: 'ipd', label: 'IPD', requiredModule: 'IPD' },
                { id: 'mrd', label: 'MRD Archive', requiredModule: 'IPD' },
                { id: 'fees', label: 'Fees', requiredModule: 'OPD' },
                { id: 'audit-logs', label: 'Audit Logs', requiredModule: null },
                { id: 'settings', label: 'Settings', requiredModule: 'OPD' },
            ];
            return adminAllTabs.filter(tab => !tab.requiredModule || modules.includes(tab.requiredModule));
        } else if (effectiveRole === 'DOCTOR') {
            return [
                { id: 'overview', label: 'Overview' },
                { id: 'appointments', label: 'My Appointments' },
                { id: 'ipd', label: 'IPD' },
                { id: 'mrd', label: 'MRD Archive' },
                { id: 'queue', label: 'Queue' },
                { id: 'opd', label: 'OPD' },
                ...(isSolo ? [{ id: 'patients', label: 'Patients' }] : []),
                ...((isSolo || hasBilling) ? [{ id: 'billing', label: 'Billing' }] : []),
                ...((isSolo && hasInClinic) ? [{ id: 'inventory', label: 'Medicine Inventory' }] : []),
                ...(isSolo ? [{ id: 'hospital-inventory', label: 'Hospital Inventory' }] : []),
            ];
        } else if (effectiveRole === 'RECEPTIONIST') {
            return [
                { id: 'overview', label: 'Overview' },
                { id: 'patients', label: 'Patients' },
                { id: 'opd', label: 'OPD' },
                { id: 'ipd', label: 'IPD' },
                { id: 'mrd', label: 'MRD Archive' },
                { id: 'billing', label: 'Billing' },
                ...(user?.inClinic !== false ? [{ id: 'inventory', label: 'Medicine Inventory' }] : [])
            ].filter(tab => tab.id !== 'billing' || user?.billingHandler !== 'DOCTOR');
        } else if (effectiveRole === 'PHARMACIST') {
            const isStandalonePharmacy = modules.includes('PHARMACY') && !modules.includes('OPD');
            return [
                { id: 'dashboard', label: 'Dashboard' },
                { id: 'billing', label: 'Billing Counter' },
                ...(!isStandalonePharmacy ? [{ id: 'prescriptions', label: 'Prescriptions' }] : []),
                { id: 'inventory', label: 'Inventory' },
                { id: 'medicine_master', label: 'Medicine Master' },
                { id: 'purchase', label: 'Purchase Management' },
                { id: 'suppliers', label: 'Suppliers' },
                { id: 'returns', label: 'Returns & Refunds' },
                { id: 'expiry', label: 'Expiry Management' },
                { id: 'reports', label: 'Reports & Analytics' }
            ];
        } else if (effectiveRole === 'NURSE') {
            return [
                { id: 'tasks', label: 'My Tasks' },
                { id: 'patients', label: 'My Patients' },
            ];
        }
        return [];
    };

    const handleTabChange = (tabId) => {
        if (effectiveRole === 'HOSPITAL_ADMIN') {
            navigate(`/hospital/admin?tab=${tabId}`);
        } else if (effectiveRole === 'DOCTOR') {
            navigate(`/hospital/doctor?tab=${tabId}`);
        } else if (effectiveRole === 'RECEPTIONIST') {
            navigate(`/hospital/receptionist?tab=${tabId}`);
        } else if (effectiveRole === 'PHARMACIST') {
            navigate(`/hospital/pharmacy?tab=${tabId}`);
        } else if (effectiveRole === 'NURSE') {
            navigate(`/nurse-dashboard`);
        }
    };

    const isAdmin = user?.role === 'HOSPITAL_ADMIN';

    // Clinical tab navigation
    const [clinicalTab, setClinicalTab] = useState('overview');

    const CLINICAL_TABS = [
        { id: 'overview', label: 'Overview', icon: '🏠' },
        { id: 'assessment', label: 'Clinical Assessment', icon: '🩺' },
        { id: 'consent', label: 'Consents', icon: '📋' },
        { id: 'risk', label: 'Risk Assessment', icon: '📊' },
        { id: 'fluid', label: 'Fluid Chart', icon: '💧' },
        { id: 'nursing', label: 'Nursing Progress', icon: '📓' },
    ];

    const canManageBilling = 
        isAdmin ||
        (isDoctor && (user?.billingHandler === 'DOCTOR' || user?.billingHandler === 'BOTH')) ||
        (isReceptionist && (user?.billingHandler === 'RECEPTIONIST' || user?.billingHandler === 'BOTH'));

    const [confirmState, setConfirmState] = useState({ open: false, title: '', message: '', onConfirm: null });

    // CDSS state
    const [smartSummary, setSmartSummary] = useState(null);
    const [ewsResult, setEwsResult] = useState(null);
    const [cdssAlerts, setCdssAlerts] = useState([]);
    const [showCdssModal, setShowCdssModal] = useState(false);
    const [pendingPrescriptionSave, setPendingPrescriptionSave] = useState(null);

    const [followupModal, setFollowupModal] = useState({ isOpen: false, diagnosis: '', notes: '', saving: false });
    const [dischargeModal, setDischargeModal] = useState({ isOpen: false, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false });
    const [medicineModal, setMedicineModal] = useState({ isOpen: false, medicineId: null, medicineMasterId: null, medicineName: '', type: 'TABLET', route: 'ORAL', dose: '', frequency: '', durationDays: 0, startDate: '', saving: false });
    // medSearchResults removed — medicine name now uses masterDataService.searchMedicines via SearchableSelect

    const [inventory, setInventory] = useState([]);
    const [administeredList, setAdministeredList] = useState([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [showSuggestions, setShowSuggestions] = useState(false);

    const [medicineTab, setMedicineTab] = useState('prescribe'); // 'prescribe', 'administer', or 'hospital-item'

    const [hospitalInventory, setHospitalInventory] = useState([]);
    const [hospitalInventoryCatalog, setHospitalInventoryCatalog] = useState([]);
    const [hospitalInvItems, setHospitalInvItems] = useState([]); // Selected items: [{stockId, name, qty, maxStock, linkedFeeId, feeName, feeAmount}]
    const [hospitalInvSearch, setHospitalInvSearch] = useState('');
    const [hospitalInvDropdown, setHospitalInvDropdown] = useState(false);
    const [availableCustomFees, setAvailableCustomFees] = useState([]);

    useEffect(() => {
        if (medicineModal.isOpen) {
            if (medicineTab === 'administer' && user?.inClinic !== false) {
                const fetchInventory = async () => {
                    try {
                        const res = await hospitalService.getInventoryMedicines();
                        const filtered = (res || []).filter(item => {
                            if (!item) return false;
                            // Handle both active and isActive serialization key variations
                            const activeVal = item.isActive !== undefined ? item.isActive : item.active;
                            const isNotInactive = activeVal !== false && activeVal !== 0 && activeVal !== '0';
                            // Safety checks for stock quantity
                            const stock = item.stockQuantity !== undefined ? item.stockQuantity : 0;
                            return isNotInactive && stock > 0;
                        });
                        setInventory(filtered);
                    } catch (err) {
                        console.error("Failed to load clinical stock inventory", err);
                    }
                };
                fetchInventory();
            } else if (medicineTab === 'hospital-item') {
                const fetchHospitalInventory = async () => {
                    try {
                        const [invRes, catRes, feesRes] = await Promise.all([
                            hospitalService.getHospitalInventory(),
                            hospitalService.getHospitalInventoryCatalog(),
                            hospitalService.getCustomFees()
                        ]);
                        setHospitalInventory((invRes || []).filter(x => x.isActive !== false && x.stockQuantity > 0));
                        setHospitalInventoryCatalog(catRes || []);
                        setAvailableCustomFees(feesRes || []);
                    } catch (err) {
                        console.error('Failed to load hospital inventory or fees', err);
                    }
                };
                fetchHospitalInventory();
            }
        }
        if (!medicineModal.isOpen) {
            setAdministeredList([]);
            setSearchQuery('');
            setShowSuggestions(false);
            setHospitalInvItems([]);
            setHospitalInvSearch('');
            setHospitalInvDropdown(false);
            setMedicineTab('prescribe');
        }
    }, [medicineModal.isOpen, medicineTab]);

    // Medicine name search is now handled by SearchableSelect + masterDataService.searchMedicines
    const [allergies, setAllergies] = useState([]);
    const [showAllergyModal, setShowAllergyModal] = useState(false);
    const [allergyForm, setAllergyForm] = useState({ allergyMasterId: null, allergyName: '', severity: 'UNKNOWN', notes: '' });

    const [billModal, setBillModal] = useState({ isOpen: false, loading: false, bill: null });
    const [printingBill, setPrintingBill] = useState(false);
    const [downloadingPdf, setDownloadingPdf] = useState(false);
    const [payment, setPayment] = useState({ amount: '', mode: 'CASH', saving: false });
    const [bedModal, setBedModal] = useState({ isOpen: false, wards: [], selectedWard: '', beds: [], selectedBed: '', saving: false });
    const [labOpenTrigger, setLabOpenTrigger] = useState(0);
    const [radOpenTrigger, setRadOpenTrigger] = useState(0);

    useEffect(() => {
        if (bedModal.isOpen && bedModal.wards.length === 0) {
            wardService.getWards().then(w => setBedModal(p => ({ ...p, wards: w || [] }))).catch(e => console.error(e));
        }
    }, [bedModal.isOpen, bedModal.wards.length]);

    useEffect(() => {
        if (bedModal.isOpen && bedModal.selectedWard) {
            wardService.getAvailableBeds(bedModal.selectedWard).then(b => setBedModal(p => ({ ...p, beds: b || [] }))).catch(e => console.error(e));
        } else {
            setBedModal(p => ({ ...p, beds: [] }));
        }
    }, [bedModal.selectedWard, bedModal.isOpen]);

    const handleBedChange = async () => {
        if (!bedModal.selectedBed) return toastError('Please select a new bed');
        setBedModal(p => ({ ...p, saving: true }));
        try {
            await hospitalService.changeBed(id, bedModal.selectedBed);
            success('Bed changed successfully');
            setBedModal(p => ({ ...p, isOpen: false, selectedBed: '', selectedWard: '' }));
            // Re-fetch latest detail
            setLoading(true);
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
        } catch (e) {
            toastError(parseError(e, 'Failed to change bed'));
        } finally {
            setBedModal(p => ({ ...p, saving: false }));
            setLoading(false);
        }
    };

    const load = async (showSpinner = true) => {
        if (showSpinner) setLoading(true);
        try {
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
            if (resp?.patient?.id) {
                hospitalService.getPatientAllergies(resp.patient.id)
                    .then(setAllergies)
                    .catch(() => {});
            }
            if (id) {
                cdssService.getSmartSummary(id)
                    .then(setSmartSummary)
                    .catch(() => {});
                cdssService.getEws(id)
                    .then(setEwsResult)
                    .catch(() => {});
            }
        } catch (err) {
            console.error('Failed to load IPD details', err);
            setData(null);
        } finally {
            if (showSpinner) setLoading(false);
        }
    };

    useEffect(() => {
        load(true);
    }, [id]);

    useWebSocket(user, setUser, (silent) => {
        if (!followupModal.isOpen && !dischargeModal.isOpen && !medicineModal.isOpen && !bedModal.isOpen && !billModal.isOpen) {
            load(silent);
        }
    });

    useEffect(() => {
        const handleKeyDown = (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'l') {
                e.preventDefault();
                setLabOpenTrigger(t => t + 1);
            }
            if ((e.ctrlKey || e.metaKey) && e.key === 'r') {
                e.preventDefault();
                setRadOpenTrigger(t => t + 1);
            }
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, []);




    const onAddFollowUp = () => {
        setFollowupModal({ isOpen: true, diagnosis: '', notes: '', saving: false });
    };

    const closeFollowupModal = () => setFollowupModal({ isOpen: false, diagnosis: '', notes: '', saving: false });

    const saveFollowup = async () => {
        if (!followupModal.diagnosis) return toastError('Diagnosis is required');
        setFollowupModal(prev => ({ ...prev, saving: true }));
        try {
            await hospitalService.addIpdFollowup(id, { 
                diagnosis: followupModal.diagnosis, 
                notes: followupModal.notes,
                administeredItems: []
            });
            success('Follow-up saved');
            closeFollowupModal();
            // reload data
            setLoading(true);
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
        } catch (err) {
            console.error('Failed to save follow-up', err);
            toastError(parseError(err, 'Failed to save follow-up'));
        } finally {
            setFollowupModal(prev => ({ ...prev, saving: false }));
            setLoading(false);
        }
    };

    const onAddMedicine = () => {
        setMedicineModal({ isOpen: true, medicineId: null, medicineMasterId: null, medicineName: '', type: 'TABLET', route: 'ORAL', dose: '', frequency: '', durationDays: 0, startDate: '', saving: false });
    };

    const onStopMedicine = (prescriptionId) => {
        setConfirmState({
            open: true,
            title: 'Stop Medicine',
            message: 'Stop this medicine? This will mark it as discontinued.',
            onConfirm: async () => {
                try {
                    await hospitalService.stopPrescription(prescriptionId);
                    success('Medicine stopped');
                    setLoading(true);
                    const resp = await hospitalService.getIpdDetails(id);
                    setData(resp);
                } catch (err) {
                    console.error('Failed to stop medicine', err);
                    toastError(parseError(err, 'Failed to stop medicine'));
                } finally {
                    setLoading(false);
                }
            }
        });
    };

    const onPlanDischarge = () => {
        setDischargeModal({ isOpen: true, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false });
    };

    const savePlanDischarge = async () => {
        if (!dischargeModal.finalDiagnosis) return toastError('Final diagnosis is required');
        setDischargeModal(prev => ({ ...prev, saving: true }));
        try {
            const payload = {
                finalDiagnosis: dischargeModal.finalDiagnosis,
                treatmentGiven: dischargeModal.treatmentGiven,
                dischargeNotes: dischargeModal.dischargeNotes,
                followUpDate: dischargeModal.followUpDate || null
            };
            await hospitalService.planDischarge(id, payload);
            success('Discharge planned');
            setDischargeModal({ isOpen: false, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false });
            setLoading(true);
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
        } catch (err) {
            console.error('Failed to plan discharge', err);
            toastError(parseError(err, 'Failed to plan discharge'));
        } finally {
            setDischargeModal(prev => ({ ...prev, saving: false }));
            setLoading(false);
        }
    };

    const onConfirmDischarge = () => {
        setConfirmState({
            open: true,
            title: 'Confirm Discharge',
            message: 'Confirm discharge? This will finalize the admission and free the bed.',
            onConfirm: async () => {
                try {
                    setLoading(true);
                    await hospitalService.confirmDischarge(id);
                    success('Discharge completed');
                    navigate('/');
                } catch (err) {
                    console.error('Failed to confirm discharge', err);
                    toastError(parseError(err, 'Failed to confirm discharge'));
                } finally {
                    setLoading(false);
                }
            }
        });
    };

    const handleDownloadDischargeSummaryPdf = async () => {
        if (downloadingPdf) return;
        setDownloadingPdf(true);
        try {
            const blob = await hospitalService.downloadDischargeSummaryPdf(id);
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `discharge_summary_${data?.ipdNumber || id}.pdf`);
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
            success('Discharge summary PDF downloaded successfully!');
        } catch (err) {
            console.error('Failed to download discharge summary PDF', err);
            toastError('Failed to download discharge summary PDF');
        } finally {
            setDownloadingPdf(false);
        }
    };

    const openBillModal = async () => {
        setBillModal({ isOpen: true, loading: true, bill: null });
        try {
            const b = await hospitalService.getIpdBill(id);
            setBillModal({ isOpen: true, loading: false, bill: b });
        } catch (err) {
            console.error('Failed to load bill', err);
            toastError('Failed to load bill');
            setBillModal({ isOpen: false, loading: false, bill: null });
        }
    };

    const handlePrintIpdBill = async () => {
        if (printingBill) return;
        setPrintingBill(true);
        
        // Pre-open the window synchronously to bypass popup blocker
        const printWindow = window.open('', '_blank');
        if (printWindow) {
            printWindow.document.write('<p style="font-family: sans-serif; text-align: center; margin-top: 20px;">Generating bill PDF, please wait...</p>');
        }
        
        try {
            const billData = await hospitalService.getIpdBill(id);
            if (!billData || !billData.billingId) {
                throw new Error("No billing ID found for this IPD admission");
            }
            
            const blob = await hospitalService.downloadReceipt(billData.billingId);
            const url = window.URL.createObjectURL(blob);
            if (printWindow) {
                printWindow.document.open();
                printWindow.document.write(
                    '<!DOCTYPE html><html><head><title>Bill</title></head>' +
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
            toastError(err.message || 'Failed to load bill for printing');
        } finally {
            setPrintingBill(false);
        }
    };

    const doSavePrescription = async (prescriptionData) => {
        setMedicineModal(prev => ({ ...prev, saving: true }));
        try {
            const payload = {
                medicineId: prescriptionData.medicineId,
                medicineMasterId: prescriptionData.medicineMasterId,
                medicineName: prescriptionData.medicineName,
                type: prescriptionData.type,
                route: prescriptionData.route,
                dose: prescriptionData.dose,
                frequency: prescriptionData.frequency,
                durationDays: prescriptionData.durationDays,
                startDate: prescriptionData.startDate || null
            };
            await hospitalService.addIpdPrescription(id, payload);
            success('Medicine prescribed successfully');
            setMedicineModal(prev => ({ ...prev, isOpen: false }));
            await load(false);
        } catch (err) {
            console.error('Failed to add medicine', err);
            toastError(parseError(err, 'Failed to add medicine'));
        } finally {
            setMedicineModal(prev => ({ ...prev, saving: false }));
        }
    };

    const handlePrescriptionSaveWithCdss = async (prescriptionData) => {
        try {
            const patientId = data?.patient?.id;
            const admId = Number(id);
            const alerts = await cdssService.checkPrescription(
                patientId,
                admId,
                prescriptionData.medicineName,
                prescriptionData.medicineMasterId || null
            );
            if (alerts && alerts.length > 0) {
                setCdssAlerts(alerts);
                setPendingPrescriptionSave(() => prescriptionData);
                setShowCdssModal(true);
            } else {
                await doSavePrescription(prescriptionData);
            }
        } catch (e) {
            console.warn('CDSS check failed, proceeding with save:', e);
            await doSavePrescription(prescriptionData);
        }
    };

    const handleCdssModalProceed = async (overrideReason) => {
        setShowCdssModal(false);
        try {
            const patientId = data?.patient?.id;
            const admId = Number(id);
            await cdssService.acknowledge(patientId, admId, cdssAlerts, overrideReason);
        } catch (e) { console.warn('CDSS acknowledge failed:', e); }
        if (pendingPrescriptionSave) {
            await doSavePrescription(pendingPrescriptionSave);
            setPendingPrescriptionSave(null);
        }
        setCdssAlerts([]);
    };

    return (
        <div className="flex h-screen bg-white overflow-hidden">
            {/* Sidebar */}
            <Sidebar
                title="HMS Portal"
                tabs={getSidebarTabs()}
                activeTab="ipd"
                onTabChange={handleTabChange}
                footerTitle="Hospital"
                footerData={user?.hospitalName || 'Hospital'}
                variant="plain"
                isCollapsed={sidebarCollapsed}
            />

            {/* Main Content Wrapper */}
            <div className="flex-1 flex flex-col h-full relative overflow-hidden">
                {/* Navbar */}
                <Navbar
                    title="IPD Details"
                    user={user}
                    onLogout={handleLogout}
                    onProfile={() => setProfileOpen(true)}
                    onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
                />

                {/* Main Content Area */}
                <main className="flex-1 overflow-x-hidden overflow-y-auto bg-[#fafafa] p-6">
                    {loading && !data ? (
                        <SkeletonDetailCard />
                    ) : !data ? (
                        <EmptyState title="Not Found" message="IPD record not found" />
                    ) : (
                        <div>
            <div className="mb-3">
                <button
                    onClick={() => navigate(-1)}
                    className="text-sm text-gray-600 hover:text-gray-900 flex items-center gap-2"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                    </svg>
                    Back
                </button>
            </div>
            <PageHeader title={`IPD ${data.ipdNumber || ''}`} subtitle={`${data.patient?.name || ''} • ${data.patient?.age || ''} • ${data.patient?.gender || ''}`} />

            {/* Lock Banner — shown when patient is discharged or archived in MRD */}
            {(data.isArchived || data.status === 'DISCHARGED') && (
                <div className={`mt-3 flex items-center gap-3 px-4 py-3 rounded-lg border text-sm font-medium ${data.isArchived ? 'bg-purple-50 border-purple-200 text-purple-800' : 'bg-gray-100 border-gray-300 text-gray-700'}`}>
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                    </svg>
                    {data.isArchived
                        ? 'This record is archived in MRD. All clinical modifications are permanently locked.'
                        : 'This patient has been discharged. Clinical record is read-only.'}
                </div>
            )}

            {/* ===== Clinical Tab Navigation Bar ===== */}
            <div className="mt-4 border-b border-gray-200">
                <nav className="-mb-px flex gap-1 overflow-x-auto scrollbar-none" aria-label="Clinical tabs">
                    {CLINICAL_TABS.map(tab => (
                        <button
                            key={tab.id}
                            onClick={() => setClinicalTab(tab.id)}
                            className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-semibold whitespace-nowrap border-b-2 transition-all ${
                                clinicalTab === tab.id
                                    ? 'border-blue-600 text-blue-700 bg-blue-50/50'
                                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                            }`}
                        >
                            <span>{tab.icon}</span>
                            {tab.label}
                        </button>
                    ))}
                </nav>
            </div>

            {/* ===== Clinical Panel Content ===== */}
            {clinicalTab === 'assessment' && (
                <div className="mt-4">
                    <ClinicalAssessmentPanel
                        admissionId={id}
                        patientId={data?.patient?.id}
                        isLocked={data?.isArchived || data?.status === 'DISCHARGED'}
                    />
                </div>
            )}
            {clinicalTab === 'consent' && (
                <div className="mt-4">
                    <ConsentPanel
                        admissionId={id}
                        patientId={data?.patient?.id}
                        isLocked={data?.isArchived || data?.status === 'DISCHARGED'}
                    />
                </div>
            )}
            {clinicalTab === 'risk' && (
                <div className="mt-4">
                    <RiskAssessmentPanel
                        admissionId={id}
                        patientId={data?.patient?.id}
                        isLocked={data?.isArchived || data?.status === 'DISCHARGED'}
                    />
                </div>
            )}
            {clinicalTab === 'fluid' && (
                <div className="mt-4">
                    <FluidChartPanel
                        admissionId={id}
                        patientId={data?.patient?.id}
                        isLocked={data?.isArchived || data?.status === 'DISCHARGED'}
                    />
                </div>
            )}
            {clinicalTab === 'nursing' && (
                <div className="mt-4">
                    <NursingProgressPanel
                        admissionId={id}
                        patientId={data?.patient?.id}
                        isLocked={data?.isArchived || data?.status === 'DISCHARGED'}
                    />
                </div>
            )}

            {/* ===== Overview Tab: existing content ===== */}
            {clinicalTab === 'overview' && (
            <div>

            {/* Patient Context Bar — allergies, EWS, pending labs, alerts */}
            {smartSummary && (
                (smartSummary.allergies?.length > 0 ||
                 !!ewsResult ||
                 (smartSummary.pendingLabTests?.length ?? 0) > 0 ||
                 (smartSummary.unacknowledgedAlerts?.length ?? 0) > 0)
            ) && (
                <div className="mt-3 flex flex-wrap items-center gap-2 px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl">
                    {/* Allergies */}
                    {smartSummary.allergies?.length > 0 && (
                        <div className="flex items-center gap-1.5">
                            <span className="text-xs font-semibold text-red-600">⚠️ Allergies:</span>
                            {smartSummary.allergies.slice(0, 3).map((a, i) => (
                                <span key={a} className="text-xs px-2 py-0.5 bg-red-50 text-red-700 border border-red-200 rounded-full font-medium">
                                    {a}
                                </span>
                            ))}
                            {smartSummary.allergies.length > 3 && (
                                <span className="text-xs px-2 py-0.5 bg-red-50 text-red-600 border border-red-200 rounded-full">
                                    +{smartSummary.allergies.length - 3} more
                                </span>
                            )}
                        </div>
                    )}

                    {/* EWS Score */}
                    {ewsResult && (
                        <span className={`text-xs px-2.5 py-0.5 rounded-full font-semibold border ${
                            ewsResult.severity === 'HIGH' ? 'bg-red-50 text-red-700 border-red-200' :
                            ewsResult.severity === 'MEDIUM' ? 'bg-orange-50 text-orange-700 border-orange-200' :
                            'bg-green-50 text-green-700 border-green-200'
                        }`}>
                            EWS {ewsResult.totalScore ?? 0}
                            {ewsResult.severity === 'HIGH' && ' 🔴'}
                            {ewsResult.severity === 'MEDIUM' && ' ⚠️'}
                            {ewsResult.severity === 'NORMAL' && ' 🟢'}
                        </span>
                    )}

                    {/* Pending Labs */}
                    {(smartSummary.pendingLabTests?.length ?? 0) > 0 && (
                        <span className="text-xs px-2.5 py-0.5 bg-amber-50 text-amber-700 border border-amber-200 rounded-full font-medium">
                            🧪 {smartSummary.pendingLabTests.length} pending lab{smartSummary.pendingLabTests.length > 1 ? 's' : ''}
                        </span>
                    )}

                    {/* Unacknowledged CDSS Alerts */}
                    {(smartSummary.unacknowledgedAlerts?.length ?? 0) > 0 && (
                        <span className="text-xs px-2.5 py-0.5 bg-red-50 text-red-700 border border-red-200 rounded-full font-semibold">
                            🔔 {smartSummary.unacknowledgedAlerts.length} alert{smartSummary.unacknowledgedAlerts.length > 1 ? 's' : ''}
                        </span>
                    )}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">
                <div className="col-span-2 bg-white border rounded p-4">
                    <h3 className="font-semibold mb-2">Admission Info</h3>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                        <div><strong>Admitted:</strong> {data.admission?.admissionDateTime ? new Date(data.admission.admissionDateTime).toLocaleString() : '-'}</div>
                        <div><strong>Type:</strong> {data.admission?.admissionType || '-'}</div>
                        <div><strong>Doctor:</strong> {data.admission?.doctor || '-'}</div>
                        <div><strong>Diagnosis:</strong> {data.admission?.primaryDiagnosis || '-'}</div>
                        <div>
                            <strong>Ward / Bed:</strong> {data.admission?.ward || '-'} / {data.admission?.bed || '-'}
                             {(isReceptionist || isSoloDoctor || isAdmin) && (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED') && (
                                <button onClick={() => setBedModal(p => ({ ...p, isOpen: true, selectedWard: '', selectedBed: '' }))} className="ml-2 px-1.5 py-0.5 bg-gray-100 hover:bg-blue-50 border border-gray-300 text-blue-700 text-[10px] font-bold uppercase rounded shadow-sm transition-colors">Change</button>
                            )}
                        </div>
                        <div><strong>Status:</strong> {data.status || '-'}</div>
                    </div>

                    <hr className="my-4" />

                    <h3 className="font-semibold mb-2">Daily Follow-ups</h3>
                    {data.medicalRecords && data.medicalRecords.length > 0 ? (
                        <ul className="space-y-2">
                            {data.medicalRecords.map((m, i) => (
                                <li key={i} className="p-2 border rounded">
                                    <div className="text-xs text-gray-500">{m.date} • {m.doctor}</div>
                                    <div className="text-sm font-medium">{m.diagnosis}</div>
                                    <div className="text-sm text-gray-700">{m.notes}</div>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <div className="text-sm text-gray-500">No follow-ups recorded.</div>
                    )}

                    <div className="mt-3">
                        {isDoctor && data.status !== 'DISCHARGE_PLANNED' && data.status !== 'DISCHARGED' && !data.isArchived && (
                            <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={onAddFollowUp}>+ Add Follow-up</button>
                        )}
                    </div>

                    <hr className="my-4" />
                    <DoctorRoundsPanel admissionId={id} isLocked={data?.isArchived || data?.status === 'DISCHARGED'} />

                    <hr className="my-4" />
                    <OtWorkflowPanel admissionId={id} isLocked={data?.isArchived || data?.status === 'DISCHARGED'} />

                    <hr className="my-4" />

                    {(isDoctor || isAdmin) && (
                        <DoctorOrdersPanel admissionId={id} isLocked={data?.isArchived || data?.status === 'DISCHARGED'} />
                    )}

                    {/* Lab Orders & Results — visible to Doctor, Nurse, and Admin */}
                    <hr className="my-4" />
                    <LabResultsPanel
                        ipdAdmissionId={Number(id)}
                        patientId={data?.patient?.id}
                        canOrder={(isDoctor || isAdmin) && !data?.isArchived && data?.status !== 'DISCHARGED'}
                        openTrigger={labOpenTrigger}
                    />

                    {/* Radiology Orders & Reports — visible to Doctor, Nurse, and Admin */}
                    <hr className="my-4" />
                    <RadiologyResultsPanel
                        ipdAdmissionId={Number(id)}
                        patientId={data?.patient?.id}
                        canOrder={(isDoctor || isAdmin) && !data?.isArchived && data?.status !== 'DISCHARGED'}
                        openTrigger={radOpenTrigger}
                    />

                    {followupModal.isOpen && (
                        <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                            <div className="bg-white rounded-lg w-full max-w-lg p-6">
                                <h3 className="text-lg font-semibold mb-3">Add Follow-up</h3>
                                <div className="mb-3">
                                    <label className="block text-sm font-medium mb-1">Diagnosis</label>
                                    <textarea value={followupModal.diagnosis} onChange={e => setFollowupModal(prev => ({ ...prev, diagnosis: e.target.value }))} className="w-full border p-2 rounded" rows={3} />
                                </div>
                                <div className="mb-3">
                                    <label className="block text-sm font-medium mb-1">Notes</label>
                                    <textarea value={followupModal.notes} onChange={e => setFollowupModal(prev => ({ ...prev, notes: e.target.value }))} className="w-full border p-2 rounded" rows={4} />
                                </div>

                                <div className="flex justify-end gap-3">
                                    <button onClick={closeFollowupModal} className="px-3 py-1 bg-gray-100 rounded">Cancel</button>
                                    <button onClick={saveFollowup} disabled={followupModal.saving} className="px-3 py-1 bg-green-600 text-white rounded">{followupModal.saving ? 'Saving...' : 'Save'}</button>
                                </div>
                            </div>
                        </div>
                    )}

                        {medicineModal.isOpen && (
                            <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                                <div className="bg-white rounded-lg w-full max-w-lg p-6">
                                    <h3 className="text-lg font-semibold mb-3">Add Medicine / Stock Item</h3>
                                    
                                    <div className="flex border-b mb-4">
                                        <button
                                            type="button"
                                            onClick={() => setMedicineTab('prescribe')}
                                            className={`flex-1 py-2 text-sm font-semibold border-b-2 transition-all ${
                                                medicineTab === 'prescribe'
                                                    ? 'border-blue-600 text-blue-600'
                                                    : 'border-transparent text-gray-500 hover:text-gray-700'
                                            }`}
                                        >
                                            Prescribe Medicine
                                        </button>
                                        {user?.inClinic !== false && (
                                            <button
                                                type="button"
                                                onClick={() => setMedicineTab('administer')}
                                                className={`flex-1 py-2 text-sm font-semibold border-b-2 transition-all ${
                                                    medicineTab === 'administer'
                                                        ? 'border-blue-600 text-blue-600'
                                                        : 'border-transparent text-gray-500 hover:text-gray-700'
                                                }`}
                                            >
                                                Administer Stock Item
                                            </button>
                                        )}
                                        <button
                                            type="button"
                                            onClick={() => setMedicineTab('hospital-item')}
                                            className={`flex-1 py-2 text-sm font-semibold border-b-2 transition-all ${
                                                medicineTab === 'hospital-item'
                                                    ? 'border-blue-600 text-blue-600'
                                                    : 'border-transparent text-gray-500 hover:text-gray-700'
                                            }`}
                                        >
                                            Hospital Items
                                        </button>
                                    </div>

                                    {medicineTab === 'prescribe' && (
                                        <>
                                            <div className="grid grid-cols-2 gap-3">
                                                <div>
                                                    <label className="block text-sm font-medium mb-1">Medicine Name</label>
                                                    <SearchableSelect
                                                        onSearch={masterDataService.searchMedicines}
                                                        onSelect={item => setMedicineModal(prev => ({
                                                            ...prev,
                                                            medicineName: item.medicineName,
                                                            medicineMasterId: item.id,
                                                            medicineId: null,
                                                            type: item.medicineType?.toUpperCase() || prev.type,
                                                        }))}
                                                        getLabel={item => `${item.medicineName}${item.strength ? ' ' + item.strength : ''}`}
                                                        placeholder="Search medicine (e.g. Paracetamol, Amoxicillin)"
                                                        value={medicineModal.medicineName || ''}
                                                    />
                                                </div>
                                                <div>
                                                    <label className="block text-sm font-medium mb-1">Type</label>
                                                    <select value={medicineModal.type} onChange={e => setMedicineModal(prev => ({ ...prev, type: e.target.value }))} className="w-full border p-2 rounded text-sm">
                                                        <option>TABLET</option>
                                                        <option>SYRUP</option>
                                                        <option>INJECTION</option>
                                                    </select>
                                                </div>
                                                <div>
                                                    <label className="block text-sm font-medium mb-1">Route</label>
                                                    <select value={medicineModal.route} onChange={e => setMedicineModal(prev => ({ ...prev, route: e.target.value }))} className="w-full border p-2 rounded text-sm">
                                                        <option>ORAL</option>
                                                        <option>IV</option>
                                                        <option>IM</option>
                                                    </select>
                                                </div>
                                                <div>
                                                    <label className="block text-sm font-medium mb-1">Dose</label>
                                                    <input value={medicineModal.dose} onChange={e => setMedicineModal(prev => ({ ...prev, dose: e.target.value }))} className="w-full border p-2 rounded text-sm" />
                                                </div>
                                                <div>
                                                    <label className="block text-sm font-medium mb-1">Frequency</label>
                                                    <input value={medicineModal.frequency} onChange={e => setMedicineModal(prev => ({ ...prev, frequency: e.target.value }))} className="w-full border p-2 rounded text-sm" />
                                                </div>
                                                <div>
                                                    <label className="block text-sm font-medium mb-1">Duration (days)</label>
                                                    <input type="number" value={medicineModal.durationDays} onChange={e => setMedicineModal(prev => ({ ...prev, durationDays: parseInt(e.target.value || '0') }))} className="w-full border p-2 rounded text-sm" />
                                                </div>
                                                <div>
                                                    <label className="block text-sm font-medium mb-1">Start Date</label>
                                                    <input type="date" value={medicineModal.startDate} onChange={e => setMedicineModal(prev => ({ ...prev, startDate: e.target.value }))} className="w-full border p-2 rounded text-sm" />
                                                </div>
                                            </div>
                                            <div className="flex justify-end gap-3 mt-4">
                                                <button onClick={() => setMedicineModal(prev => ({ ...prev, isOpen: false }))} className="px-3 py-1 bg-gray-100 rounded text-sm">Cancel</button>
                                                <button onClick={async () => {
                                                    if (!medicineModal.medicineName) return toastError('Medicine name required');
                                                    await handlePrescriptionSaveWithCdss({
                                                        medicineId: medicineModal.medicineId,
                                                        medicineMasterId: medicineModal.medicineMasterId,
                                                        medicineName: medicineModal.medicineName,
                                                        type: medicineModal.type,
                                                        route: medicineModal.route,
                                                        dose: medicineModal.dose,
                                                        frequency: medicineModal.frequency,
                                                        durationDays: medicineModal.durationDays,
                                                        startDate: medicineModal.startDate || null
                                                    });
                                                }} disabled={medicineModal.saving} className="px-3 py-1 bg-blue-600 text-white rounded text-sm">{medicineModal.saving ? 'Saving...' : 'Save Prescription'}</button>
                                            </div>
                                        </>
                                    )}

                                    {medicineTab === 'administer' && (
                                        <>
                                            <div className="bg-slate-50 p-3 rounded-lg border border-gray-200 mb-3 space-y-3">
                                                <div>
                                                    <h4 className="text-xs font-bold text-gray-800 uppercase tracking-wide">Administered Clinical Stock Items</h4>
                                                    <p className="text-[10px] text-gray-500 mt-0.5">Deducted from physical stock and billed to patient.</p>
                                                </div>

                                                <div className="relative">
                                                    <input
                                                        type="text"
                                                        placeholder="Search active inventory stock..."
                                                        value={searchQuery}
                                                        onChange={(e) => {
                                                            setSearchQuery(e.target.value);
                                                            setShowSuggestions(true);
                                                        }}
                                                        onFocus={() => setShowSuggestions(true)}
                                                        className="w-full border border-gray-300 p-2 text-xs rounded outline-none bg-white"
                                                    />

                                                    {showSuggestions && searchQuery.trim().length >= 1 && (
                                                        <div className="absolute left-0 right-0 mt-1 max-h-40 overflow-auto bg-white rounded border border-gray-200 shadow-lg z-50 divide-y divide-gray-100">
                                                            {inventory
                                                                .filter(item => item && item.name && item.name.toLowerCase().includes(searchQuery.toLowerCase().trim()))
                                                                .map(item => (
                                                                    <button
                                                                        key={item.id}
                                                                        type="button"
                                                                        onClick={() => {
                                                                            const existing = administeredList.find(x => x.medicineId === item.id);
                                                                            if (existing) {
                                                                                if (existing.quantity < item.stockQuantity) {
                                                                                    setAdministeredList(prev => prev.map(x => x.medicineId === item.id ? { ...x, quantity: x.quantity + 1 } : x));
                                                                                } else {
                                                                                    toastError(`Cannot add more. Only ${item.stockQuantity} units available.`);
                                                                                }
                                                                            } else {
                                                                                setAdministeredList(prev => [...prev, {
                                                                                    medicineId: item.id,
                                                                                    medicineName: item.name,
                                                                                    quantity: 1,
                                                                                    maxStock: item.stockQuantity
                                                                                }]);
                                                                            }
                                                                            setSearchQuery('');
                                                                            setShowSuggestions(false);
                                                                        }}
                                                                        className="w-full text-left px-3 py-2 hover:bg-slate-50 flex justify-between items-center text-xs"
                                                                    >
                                                                        <div>
                                                                            <span className="font-semibold text-gray-800">{item.name}</span>
                                                                        </div>
                                                                        <span className="text-[10px] text-gray-500 font-bold">
                                                                            Stock: {item.stockQuantity}
                                                                        </span>
                                                                    </button>
                                                                ))}
                                                            {inventory.filter(item => item && item.name && item.name.toLowerCase().includes(searchQuery.toLowerCase().trim())).length === 0 && (
                                                                <div className="p-2 text-center text-xs text-gray-400">No matching stock found.</div>
                                                            )}
                                                        </div>
                                                    )}
                                                </div>

                                                {administeredList.length > 0 && (
                                                    <div className="border border-gray-200 rounded overflow-hidden bg-white max-h-48 overflow-y-auto">
                                                        <table className="min-w-full text-xs">
                                                            <thead className="bg-slate-50 text-gray-500 font-medium border-b border-gray-200">
                                                                <tr>
                                                                    <th className="px-3 py-1.5 text-left">Item</th>
                                                                    <th className="px-3 py-1.5 text-center">Qty</th>
                                                                    <th className="px-3 py-1.5 text-right">Action</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody className="divide-y divide-gray-100">
                                                                {administeredList.map((item) => (
                                                                    <tr key={item.medicineId} className="hover:bg-slate-50/50">
                                                                        <td className="px-3 py-2 font-semibold text-gray-800">{item.medicineName}</td>
                                                                        <td className="px-3 py-2 text-center">
                                                                            <div className="inline-flex items-center gap-1.5">
                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() => {
                                                                                        if (item.quantity > 1) {
                                                                                            setAdministeredList(prev => prev.map(x => x.medicineId === item.medicineId ? { ...x, quantity: x.quantity - 1 } : x));
                                                                                        }
                                                                                    }}
                                                                                    disabled={item.quantity <= 1}
                                                                                    className="w-5 h-5 flex items-center justify-center border border-gray-300 rounded text-gray-500 hover:bg-slate-100 disabled:opacity-50"
                                                                                >
                                                                                    -
                                                                                </button>
                                                                                <span className="font-bold text-xs w-4 text-center">{item.quantity}</span>
                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() => {
                                                                                        if (item.quantity < item.maxStock) {
                                                                                            setAdministeredList(prev => prev.map(x => x.medicineId === item.medicineId ? { ...x, quantity: x.quantity + 1 } : x));
                                                                                        } else {
                                                                                            toastError(`Only ${item.maxStock} available.`);
                                                                                        }
                                                                                    }}
                                                                                    className="w-5 h-5 flex items-center justify-center border border-gray-300 rounded text-gray-500 hover:bg-slate-100"
                                                                                >
                                                                                    +
                                                                                </button>
                                                                            </div>
                                                                        </td>
                                                                        <td className="px-3 py-2 text-right">
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => {
                                                                                    setAdministeredList(prev => prev.filter(x => x.medicineId !== item.medicineId));
                                                                                }}
                                                                                className="text-red-500 hover:text-red-700 font-semibold"
                                                                            >
                                                                                Remove
                                                                            </button>
                                                                        </td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                )}
                                            </div>
                                            <div className="flex justify-end gap-3 mt-4">
                                                <button onClick={() => setMedicineModal(prev => ({ ...prev, isOpen: false }))} className="px-3 py-1 bg-gray-100 rounded text-sm">Cancel</button>
                                                <button onClick={async () => {
                                                    if (administeredList.length === 0) return toastError('No stock items selected');
                                                    setMedicineModal(prev => ({ ...prev, saving: true }));
                                                    try {
                                                        const itemsPayload = administeredList.map(item => ({
                                                            medicineId: item.medicineId,
                                                            medicineName: item.medicineName,
                                                            quantity: item.quantity
                                                        }));
                                                        await hospitalService.administerIpdItems(id, itemsPayload);
                                                        success('Stock items administered successfully');
                                                        setMedicineModal(prev => ({ ...prev, isOpen: false }));
                                                        setLoading(true);
                                                        const resp = await hospitalService.getIpdDetails(id);
                                                        setData(resp);
                                                    } catch (err) {
                                                        console.error('Failed to administer items', err);
                                                        toastError(parseError(err, 'Failed to administer items'));
                                                    } finally {
                                                        setMedicineModal(prev => ({ ...prev, saving: false }));
                                                        setLoading(false);
                                                    }
                                                }} disabled={medicineModal.saving} className="px-3 py-1 bg-blue-600 text-white rounded text-sm">{medicineModal.saving ? 'Administering...' : 'Administer Stock'}</button>
                                            </div>
                                        </>
                                    )}

                                    {medicineTab === 'hospital-item' && (
                                        <>
                                            <div className="bg-slate-50 p-3 rounded-lg border border-gray-200 mb-3 space-y-3">
                                                <div>
                                                    <h4 className="text-xs font-bold text-gray-800 uppercase tracking-wide">Administered Hospital Stock Items</h4>
                                                    <p className="text-[10px] text-gray-500 mt-0.5">Deducted from physical stock and added to IPD bill.</p>
                                                </div>

                                                <div className="relative">
                                                    <input
                                                        type="text"
                                                        placeholder="Search hospital inventory stock (saline, injections, gloves...)..."
                                                        value={hospitalInvSearch}
                                                        onChange={(e) => {
                                                            setHospitalInvSearch(e.target.value);
                                                            setHospitalInvDropdown(true);
                                                        }}
                                                        onFocus={() => setHospitalInvDropdown(true)}
                                                        onBlur={() => setTimeout(() => setHospitalInvDropdown(false), 200)}
                                                        className="w-full border border-gray-300 p-2 text-xs rounded outline-none bg-white"
                                                    />

                                                    {hospitalInvDropdown && hospitalInvSearch.trim().length >= 1 && (
                                                        <div className="absolute left-0 right-0 mt-1 max-h-40 overflow-auto bg-white rounded border border-gray-200 shadow-lg z-50 divide-y divide-gray-100">
                                                            {hospitalInventory
                                                                .filter(item => item && item.name && item.name.toLowerCase().includes(hospitalInvSearch.toLowerCase().trim()))
                                                                .map(item => {
                                                                    const catItem = hospitalInventoryCatalog.find(c => c.name?.toLowerCase() === item.name?.toLowerCase());
                                                                    const linkedFeeId = catItem?.linkedFeeId || null;
                                                                    return (
                                                                        <button
                                                                            key={item.id}
                                                                            type="button"
                                                                            onClick={() => {
                                                                                const existing = hospitalInvItems.find(x => x.stockId === item.id);
                                                                                if (existing) {
                                                                                    if (existing.qty < item.stockQuantity) {
                                                                                        setHospitalInvItems(prev => prev.map(x => x.stockId === item.id ? { ...x, qty: x.qty + 1 } : x));
                                                                                    } else {
                                                                                        toastError(`Cannot add more. Only ${item.stockQuantity} units available.`);
                                                                                    }
                                                                                } else {
                                                                                    let feeName = null, feeAmount = 0;
                                                                                    if (linkedFeeId) {
                                                                                        const fee = availableCustomFees.find(f => String(f.id) === String(linkedFeeId));
                                                                                        if (fee) { feeName = fee.name; feeAmount = Number(fee.defaultAmount); }
                                                                                    }
                                                                                    setHospitalInvItems(prev => [...prev, {
                                                                                        stockId: item.id,
                                                                                        name: item.name,
                                                                                        qty: 1,
                                                                                        maxStock: item.stockQuantity,
                                                                                        linkedFeeId,
                                                                                        feeName: feeName || item.name,
                                                                                        feeAmount
                                                                                    }]);
                                                                                }
                                                                                setHospitalInvSearch('');
                                                                                setHospitalInvDropdown(false);
                                                                            }}
                                                                            className="w-full text-left px-3 py-2 hover:bg-slate-50 flex justify-between items-center text-xs"
                                                                        >
                                                                            <div>
                                                                                <span className="font-semibold text-gray-800">{item.name}</span>
                                                                                {catItem?.linkedFeeId && <span className="ml-2 text-[10px] text-teal-600 font-bold border border-teal-200 bg-teal-50 px-1 rounded">+fee</span>}
                                                                            </div>
                                                                            <span className="text-[10px] text-gray-500 font-bold">
                                                                                Stock: {item.stockQuantity}
                                                                            </span>
                                                                        </button>
                                                                    );
                                                                })}
                                                            {hospitalInventory.filter(item => item && item.name && item.name.toLowerCase().includes(hospitalInvSearch.toLowerCase().trim())).length === 0 && (
                                                                <div className="p-2 text-center text-xs text-gray-400">No matching hospital stock found.</div>
                                                            )}
                                                        </div>
                                                    )}
                                                </div>

                                                {hospitalInvItems.length > 0 && (
                                                    <div className="border border-gray-200 rounded overflow-hidden bg-white max-h-48 overflow-y-auto">
                                                        <table className="min-w-full text-xs">
                                                            <thead className="bg-slate-50 text-gray-500 font-medium border-b border-gray-200">
                                                                <tr>
                                                                    <th className="px-3 py-1.5 text-left">Item</th>
                                                                    <th className="px-3 py-1.5 text-center">Qty</th>
                                                                    <th className="px-3 py-1.5 text-right">Fee Charge</th>
                                                                    <th className="px-3 py-1.5 text-right">Action</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody className="divide-y divide-gray-100">
                                                                {hospitalInvItems.map((item) => (
                                                                    <tr key={item.stockId} className="hover:bg-slate-50/50">
                                                                        <td className="px-3 py-2 font-semibold text-gray-800">{item.name}</td>
                                                                        <td className="px-3 py-2 text-center">
                                                                            <div className="inline-flex items-center gap-1.5">
                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() => {
                                                                                        if (item.qty > 1) {
                                                                                            setHospitalInvItems(prev => prev.map(x => x.stockId === item.stockId ? { ...x, qty: x.qty - 1 } : x));
                                                                                        }
                                                                                    }}
                                                                                    disabled={item.qty <= 1}
                                                                                    className="w-5 h-5 flex items-center justify-center border border-gray-300 rounded text-gray-500 hover:bg-slate-100 disabled:opacity-50"
                                                                                >
                                                                                    -
                                                                                </button>
                                                                                <span className="font-bold text-xs w-4 text-center">{item.qty}</span>
                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() => {
                                                                                        if (item.qty < item.maxStock) {
                                                                                            setHospitalInvItems(prev => prev.map(x => x.stockId === item.stockId ? { ...x, qty: x.qty + 1 } : x));
                                                                                        } else {
                                                                                            toastError(`Only ${item.maxStock} available.`);
                                                                                        }
                                                                                    }}
                                                                                    className="w-5 h-5 flex items-center justify-center border border-gray-300 rounded text-gray-500 hover:bg-slate-100"
                                                                                >
                                                                                    +
                                                                                </button>
                                                                            </div>
                                                                        </td>
                                                                        <td className="px-3 py-2 text-right text-teal-700 font-bold">
                                                                            {item.feeAmount ? `₹${item.feeAmount * item.qty}` : 'No Charge'}
                                                                        </td>
                                                                        <td className="px-3 py-2 text-right">
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => {
                                                                                    setHospitalInvItems(prev => prev.filter(x => x.stockId !== item.stockId));
                                                                                }}
                                                                                className="text-red-500 hover:text-red-700 font-semibold"
                                                                            >
                                                                                Remove
                                                                            </button>
                                                                        </td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                )}
                                            </div>
                                            <div className="flex justify-end gap-3 mt-4">
                                                <button onClick={() => setMedicineModal(prev => ({ ...prev, isOpen: false }))} className="px-3 py-1 bg-gray-100 rounded text-sm">Cancel</button>
                                                <button
                                                    onClick={async () => {
                                                        if (hospitalInvItems.length === 0) return toastError('No hospital items selected');
                                                        setMedicineModal(prev => ({ ...prev, saving: true }));
                                                        try {
                                                            const itemsPayload = hospitalInvItems.map(item => ({
                                                                stockId: item.stockId,
                                                                name: item.name,
                                                                quantity: item.qty,
                                                                feeName: item.feeName,
                                                                feeAmount: item.feeAmount
                                                            }));
                                                            await hospitalService.administerIpdHospitalItems(id, itemsPayload);
                                                            success('Hospital items administered successfully');
                                                            setMedicineModal(prev => ({ ...prev, isOpen: false }));
                                                            setLoading(true);
                                                            const resp = await hospitalService.getIpdDetails(id);
                                                            setData(resp);
                                                        } catch (err) {
                                                            console.error('Failed to administer hospital items', err);
                                                            toastError(parseError(err, 'Failed to administer hospital items'));
                                                        } finally {
                                                            setMedicineModal(prev => ({ ...prev, saving: false }));
                                                            setLoading(false);
                                                        }
                                                    }}
                                                    disabled={medicineModal.saving}
                                                    className="px-3 py-1 bg-blue-600 text-white rounded text-sm"
                                                >
                                                    {medicineModal.saving ? 'Administering...' : 'Administer Items'}
                                                </button>
                                            </div>
                                        </>
                                    )}
                                </div>
                            </div>
                        )}

                    <hr className="my-4" />

                    <h3 className="font-semibold mb-2">Current Medicines & Items</h3>

                    {/* Prescribed Medicines */}
                    {data.activePrescriptions && data.activePrescriptions.length > 0 && (
                        <>
                            <p className="text-xs font-semibold text-blue-700 uppercase tracking-wide mb-1">Prescribed Medicines</p>
                            <ul className="space-y-2 mb-3">
                                {data.activePrescriptions.map((p, i) => (
                                    <li key={i} className="p-2 border border-blue-100 rounded bg-blue-50 flex justify-between items-center">
                                        <div>
                                            <div className="font-medium">{p.name}</div>
                                            <div className="text-xs text-gray-600">{p.type} • {p.route} • {p.frequency}</div>
                                        </div>
                                        <div>
                                            {isDoctor && !data.isArchived && data.status !== 'DISCHARGED' ? (
                                                <div className="flex gap-2">
                                                    <button className="px-2 py-1 bg-yellow-500 text-white rounded text-xs" onClick={() => onStopMedicine(p.id)}>Stop</button>
                                                </div>
                                            ) : (
                                                <div className="text-xs text-gray-500">{p.status}</div>
                                            )}
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        </>
                    )}

                    {/* Administered Stock Items */}
                    {data.administeredItems && data.administeredItems.length > 0 && (
                        <>
                            <p className="text-xs font-semibold text-teal-700 uppercase tracking-wide mb-1">Administered Stock Items</p>
                            <ul className="space-y-2 mb-3">
                                {data.administeredItems.map((item, i) => (
                                    <li key={i} className="p-2 border border-teal-100 rounded bg-teal-50 flex justify-between items-center">
                                        <div>
                                            <div className="font-medium flex items-center gap-2">
                                                {item.name}
                                                <span className="text-xs bg-teal-100 text-teal-700 px-1.5 py-0.5 rounded font-semibold">Stock</span>
                                            </div>
                                            <div className="text-xs text-gray-500">Qty: {item.quantity}{item.administeredAt ? ` • ${item.administeredAt}` : ''}</div>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        </>
                    )}

                    {/* Empty state — no prescriptions AND no administered items */}
                    {(!data.activePrescriptions || data.activePrescriptions.length === 0) &&
                     (!data.administeredItems || data.administeredItems.length === 0) && (
                        <div className="text-sm text-gray-500">No active medicines or administered items.</div>
                    )}

                    {isDoctor && data.status !== 'DISCHARGE_PLANNED' && data.status !== 'DISCHARGED' && !data.isArchived && (
                        <div className="mt-3">
                            <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={onAddMedicine}>+ Add Medicine</button>
                        </div>
                    )}
                </div>

                <aside className="bg-white border rounded p-4">
                    {/* Clinical Summary (CDSS Smart Summary + EWS) */}
                    {smartSummary && (
                      <div className="bg-white rounded-xl border border-gray-200 p-4 mb-4">
                        <div className="flex items-center justify-between mb-3">
                          <h3 className="text-sm font-semibold text-gray-700">Clinical Summary</h3>
                          {ewsResult && (
                            <span className={`text-xs font-bold px-2 py-1 rounded-full ${
                              ewsResult.severity === 'HIGH' ? 'bg-red-100 text-red-700' :
                              ewsResult.severity === 'MEDIUM' ? 'bg-orange-100 text-orange-700' :
                              ewsResult.severity === 'NORMAL' ? 'bg-green-100 text-green-700' :
                              'bg-gray-100 text-gray-500'
                            }`}>
                              EWS: {ewsResult.totalScore || 0}
                              {ewsResult.severity === 'HIGH' && ' 🔴'}
                              {ewsResult.severity === 'MEDIUM' && ' ⚠️'}
                            </span>
                          )}
                        </div>
                        {smartSummary.allergies?.length > 0 && (
                          <div className="mb-2">
                            <p className="text-xs font-medium text-red-600 mb-1">🔴 Allergies</p>
                            <div className="flex flex-wrap gap-1">
                              {smartSummary.allergies.map((a, i) => (
                                <span key={i} className="bg-red-50 text-red-700 text-xs px-2 py-0.5 rounded-full border border-red-200">{a}</span>
                              ))}
                            </div>
                          </div>
                        )}
                        {smartSummary.activeMedicines?.length > 0 && (
                          <div className="mb-2">
                            <p className="text-xs font-medium text-blue-600 mb-1">💊 Active Medicines</p>
                            <p className="text-xs text-gray-600">{smartSummary.activeMedicines.slice(0,5).join(', ')}{smartSummary.activeMedicines.length > 5 ? ` +${smartSummary.activeMedicines.length - 5} more` : ''}</p>
                          </div>
                        )}
                        {smartSummary.pendingLabTests?.length > 0 && (
                          <div className="mb-2">
                            <p className="text-xs font-medium text-amber-600 mb-1">🧪 Pending Labs</p>
                            <p className="text-xs text-gray-600">{smartSummary.pendingLabTests.join(', ')}</p>
                          </div>
                        )}
                        {smartSummary.pendingRadiology?.length > 0 && (
                          <div className="mb-2">
                            <p className="text-xs font-medium text-purple-600 mb-1">📷 Pending Radiology</p>
                            <p className="text-xs text-gray-600">{smartSummary.pendingRadiology.join(', ')}</p>
                          </div>
                        )}
                        {(!smartSummary.allergies?.length && !smartSummary.activeMedicines?.length &&
                          !smartSummary.pendingLabTests?.length && !smartSummary.pendingRadiology?.length) && (
                          <p className="text-xs text-gray-400">No clinical flags at this time.</p>
                        )}
                      </div>
                    )}

                    {/* Patient Allergies */}
                    <div className="bg-white rounded-xl border border-gray-200 p-4 mb-4">
                      <div className="flex items-center justify-between mb-3">
                        <h3 className="text-sm font-semibold text-gray-700">Known Allergies</h3>
                        {(isDoctor || isAdmin) && !data?.isArchived && (
                          <button
                            onClick={() => setShowAllergyModal(true)}
                            className="text-xs px-2 py-1 rounded bg-red-50 text-red-600 hover:bg-red-100 font-medium"
                          >
                            + Add
                          </button>
                        )}
                      </div>
                      {allergies.length === 0 ? (
                        <p className="text-xs text-gray-400">No known allergies recorded</p>
                      ) : (
                        <div className="flex flex-wrap gap-2">
                          {allergies.map(a => (
                            <span key={a.id}
                              className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium
                                ${a.severity === 'SEVERE' ? 'bg-red-100 text-red-700' :
                                  a.severity === 'MODERATE' ? 'bg-orange-100 text-orange-700' :
                                  'bg-yellow-100 text-yellow-700'}`}>
                              ⚠ {a.allergyName || `Allergy #${a.allergyMasterId}`}
                              <span className="opacity-60">· {a.severity}</span>
                              {(isDoctor || isAdmin) && !data?.isArchived && (
                                <button
                                  onClick={async () => {
                                    await hospitalService.removePatientAllergy(data.patient?.id, a.id);
                                    setAllergies(prev => prev.filter(x => x.id !== a.id));
                                  }}
                                  className="ml-1 opacity-50 hover:opacity-100"
                                >×</button>
                              )}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Add Allergy Modal */}
                    {showAllergyModal && (
                      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
                        <div className="bg-white rounded-xl shadow-2xl w-full max-w-md p-6">
                          <div className="flex items-center justify-between mb-4">
                            <h3 className="text-lg font-semibold text-gray-800">Add Allergy</h3>
                            <button onClick={() => setShowAllergyModal(false)} className="text-gray-400 hover:text-gray-600 text-2xl">&times;</button>
                          </div>
                          <div className="space-y-3">
                            <div>
                              <label className="block text-xs font-medium text-gray-600 mb-1">Search Allergy</label>
                              <SearchableSelect
                                onSearch={masterDataService.searchAllergies}
                                onSelect={item => setAllergyForm(f => ({ ...f, allergyMasterId: item.id, allergyName: item.allergyName }))}
                                getLabel={item => item.allergyName}
                                placeholder="Search allergy (e.g. Penicillin)"
                                value={allergyForm.allergyName}
                              />
                            </div>
                            <div>
                              <label className="block text-xs font-medium text-gray-600 mb-1">Severity</label>
                              <select value={allergyForm.severity}
                                onChange={e => setAllergyForm(f => ({ ...f, severity: e.target.value }))}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                                <option value="UNKNOWN">Unknown</option>
                                <option value="MILD">Mild</option>
                                <option value="MODERATE">Moderate</option>
                                <option value="SEVERE">Severe</option>
                              </select>
                            </div>
                            <div>
                              <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
                              <input type="text" value={allergyForm.notes}
                                onChange={e => setAllergyForm(f => ({ ...f, notes: e.target.value }))}
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                                placeholder="e.g. Anaphylaxis history" />
                            </div>
                            <div className="flex justify-end gap-3 pt-2">
                              <button onClick={() => setShowAllergyModal(false)}
                                className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-600">Cancel</button>
                              <button
                                disabled={!allergyForm.allergyMasterId}
                                onClick={async () => {
                                  try {
                                    const saved = await hospitalService.addPatientAllergy(data.patient?.id, {
                                      allergyMasterId: allergyForm.allergyMasterId,
                                      severity: allergyForm.severity,
                                      notes: allergyForm.notes,
                                    });
                                    setAllergies(prev => [...prev, { ...saved, allergyName: allergyForm.allergyName }]);
                                    setShowAllergyModal(false);
                                    setAllergyForm({ allergyMasterId: null, allergyName: '', severity: 'UNKNOWN', notes: '' });
                                  } catch (e) {
                                    alert(e.response?.data?.message || 'Failed to add allergy');
                                  }
                                }}
                                className="px-4 py-2 text-sm rounded-lg bg-red-600 text-white hover:bg-red-700 font-medium disabled:opacity-50">
                                Save Allergy
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}

                    <h3 className="font-semibold mb-2">Billing</h3>
                    {canManageBilling ? (
                        <div>
                            {data.billing ? (
                                <div className="text-sm">
                                    <div><strong>Total:</strong> ₹{data.billing.totalAmount}</div>
                                    <div><strong>Paid:</strong> ₹{data.billing.paidAmount}</div>
                                    <div><strong>Balance:</strong> ₹{data.billing.balance}</div>
                                </div>
                            ) : (
                                <div className="text-sm text-gray-500">No billing records found.</div>
                            )}
                            <div className="mt-3 flex gap-2">
                                <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={openBillModal}>Take Payment</button>
                                <button 
                                    className="px-3 py-1 bg-gray-600 text-white rounded disabled:opacity-50" 
                                    onClick={handlePrintIpdBill}
                                    disabled={printingBill}
                                >
                                    {printingBill ? 'Printing...' : 'Print Bill'}
                                </button>
                            </div>
                        </div>
                    ) : (
                        <div className="text-sm text-gray-500">Billing is not visible to your role.</div>
                    )}

                    <hr className="my-4" />

                    <h3 className="font-semibold mb-2">Discharge</h3>
                    {data?.dischargeSummary && (
                        <div className="bg-gray-50 border border-gray-200 rounded-xl p-3 mb-3 text-xs space-y-2">
                            <div><strong>Diagnosis:</strong> {data.dischargeSummary.finalDiagnosis}</div>
                            {data.dischargeSummary.treatmentGiven && <div><strong>Treatment:</strong> {data.dischargeSummary.treatmentGiven}</div>}
                            {data.dischargeSummary.dischargeNotes && <div><strong>Instructions:</strong> {data.dischargeSummary.dischargeNotes}</div>}
                            {data.dischargeSummary.followUpDate && <div><strong>Follow-up:</strong> {new Date(data.dischargeSummary.followUpDate).toLocaleDateString()}</div>}
                            <button
                                onClick={handleDownloadDischargeSummaryPdf}
                                disabled={downloadingPdf}
                                className="w-full mt-2 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-center transition-all disabled:opacity-50 text-[10px] font-bold"
                            >
                                {downloadingPdf ? 'Downloading...' : '📥 Download Summary PDF'}
                            </button>
                        </div>
                    )}
                    {isDoctor && data.status === 'ADMITTED' && !data.isArchived && (
                        <button className="px-3 py-1 bg-yellow-600 text-white rounded" onClick={onPlanDischarge}>📝 Plan Discharge</button>
                    )}
                    {(isReceptionist || isSoloDoctor || isAdmin) && data.status === 'DISCHARGE_PLANNED' && (
                        <div>
                            <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={onConfirmDischarge}>✅ Confirm Discharge</button>
                        </div>
                    )}
                </aside>
            </div>

            {/* Discharge Modal */}
            {dischargeModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg w-full max-w-lg p-6">
                        <h3 className="text-lg font-semibold mb-3">Plan Discharge</h3>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Final Diagnosis</label>
                            <textarea value={dischargeModal.finalDiagnosis} onChange={e => setDischargeModal(prev => ({ ...prev, finalDiagnosis: e.target.value }))} className="w-full border p-2 rounded" rows={2} />
                        </div>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Treatment Given</label>
                            <textarea value={dischargeModal.treatmentGiven} onChange={e => setDischargeModal(prev => ({ ...prev, treatmentGiven: e.target.value }))} className="w-full border p-2 rounded" rows={3} />
                        </div>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Discharge Notes</label>
                            <textarea value={dischargeModal.dischargeNotes} onChange={e => setDischargeModal(prev => ({ ...prev, dischargeNotes: e.target.value }))} className="w-full border p-2 rounded" rows={3} />
                        </div>
                        <div className="mb-3">
                            <label className="block text-sm font-medium mb-1">Follow-up Date</label>
                            <input type="date" value={dischargeModal.followUpDate} onChange={e => setDischargeModal(prev => ({ ...prev, followUpDate: e.target.value }))} className="w-full border p-2 rounded" />
                        </div>
                        <div className="flex justify-end gap-3">
                            <button onClick={() => setDischargeModal({ isOpen: false, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false })} className="px-3 py-1 bg-gray-100 rounded">Cancel</button>
                            <button onClick={savePlanDischarge} disabled={dischargeModal.saving} className="px-3 py-1 bg-yellow-600 text-white rounded">{dischargeModal.saving ? 'Saving...' : 'Plan Discharge'}</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Bed Change Modal */}
            {bedModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg w-full max-w-md p-6 shadow-xl">
                        <h3 className="text-lg font-bold text-gray-900 mb-4">Change Assigned Bed</h3>
                        <div className="mb-4">
                            <label className="block text-sm font-medium text-gray-700 mb-1">Select Ward</label>
                            <select 
                                className="w-full border border-gray-300 p-2 rounded"
                                value={bedModal.selectedWard}
                                onChange={e => setBedModal(p => ({ ...p, selectedWard: e.target.value, selectedBed: '' }))}
                            >
                                <option value="">-- Choose Ward --</option>
                                {bedModal.wards.map(w => (
                                    <option key={w.wardId} value={w.wardId}>{w.wardName} (₹{w.bedPrice}/day)</option>
                                ))}
                            </select>
                        </div>
                        <div className="mb-5">
                            <label className="block text-sm font-medium text-gray-700 mb-1">Available Beds</label>
                            <select 
                                className="w-full border border-gray-300 p-2 rounded"
                                value={bedModal.selectedBed}
                                onChange={e => setBedModal(p => ({ ...p, selectedBed: e.target.value }))}
                                disabled={!bedModal.selectedWard}
                            >
                                <option value="">-- Select Bed --</option>
                                {bedModal.beds.map(b => (
                                    <option key={b.bedId} value={b.bedId}>{b.bedCode}</option>
                                ))}
                            </select>
                            {bedModal.selectedWard && bedModal.beds.length === 0 && (
                                <p className="text-xs text-red-500 mt-1">No available beds in this ward.</p>
                            )}
                        </div>
                        <div className="flex justify-end gap-3">
                            <button onClick={() => setBedModal(p => ({ ...p, isOpen: false }))} className="px-4 py-2 text-gray-600 bg-gray-100 hover:bg-gray-200 rounded font-medium">Cancel</button>
                            <button 
                                onClick={handleBedChange} 
                                disabled={!bedModal.selectedBed || bedModal.saving} 
                                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded font-medium disabled:opacity-50"
                            >
                                {bedModal.saving ? 'Saving...' : 'Update Bed'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {billModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg w-full max-w-2xl p-6">
                        <h3 className="text-lg font-semibold mb-3">IPD Bill</h3>
                        {billModal.loading ? (
                            <SkeletonFormCard fields={3} />
                        ) : billModal.bill ? (
                            <div>
                                <div className="mb-3 text-sm">
                                    <div><strong>Total:</strong> ₹{billModal.bill.totalAmount}</div>
                                    <div><strong>Paid:</strong> ₹{billModal.bill.paidAmount}</div>
                                    <div><strong>Balance:</strong> ₹{billModal.bill.balance}</div>
                                </div>
                                <div className="mb-3">
                                    <h4 className="font-medium">Items</h4>
                                    <ul className="mt-2 space-y-2">
                                        {billModal.bill.items.map((it, i) => (
                                            <li key={i} className="flex justify-between border p-2 rounded">
                                                <div>{it.description}</div>
                                                <div>₹{it.amount}</div>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                                <div className="mt-4">
                                    <h4 className="font-medium mb-2">Take Payment</h4>
                                    <div className="grid grid-cols-2 gap-3">
                                        <input value={payment.amount} onChange={e => setPayment(prev => ({ ...prev, amount: e.target.value }))} placeholder="Amount" className="border p-2 rounded" />
                                        <select value={payment.mode} onChange={e => setPayment(prev => ({ ...prev, mode: e.target.value }))} className="border p-2 rounded">
                                            <option value="CASH">CASH</option>
                                            <option value="CARD">CARD</option>
                                            <option value="UPI">UPI</option>
                                        </select>
                                    </div>
                                    {Number(payment.amount) > billModal.bill.balance && (
                                        <p className="text-xs text-amber-600 font-semibold mt-2">
                                            ⚠️ Warning: The bill is less than the payment amount
                                        </p>
                                    )}
                                    <div className="flex justify-end gap-3 mt-4">
                                        <button onClick={() => setBillModal({ isOpen: false, loading: false, bill: null })} className="px-3 py-1 bg-gray-100 rounded">Close</button>
                                        <button onClick={async () => {
                                            if (!payment.amount) return toastError('Enter amount');
                                            const amountVal = Number(payment.amount);
                                            if (isNaN(amountVal) || amountVal <= 0) {
                                                return toastError('Enter a valid amount');
                                            }
                                            if (amountVal > billModal.bill.balance) {
                                                toastError('Payment amount exceeds the outstanding balance');
                                                return;
                                            }
                                            setPayment(prev => ({ ...prev, saving: true }));
                                            try {
                                                const payload = { amount: amountVal, mode: payment.mode };
                                                const resp = await hospitalService.payBilling(billModal.bill.billingId, payload);
                                                success('Payment recorded');
                                                setBillModal({ isOpen: false, loading: false, bill: null });
                                                // reload details
                                                setLoading(true);
                                                const resp2 = await hospitalService.getIpdDetails(id);
                                                setData(resp2);
                                            } catch (err) {
                                                console.error(err);
                                                toastError(parseError(err, 'Failed to record payment'));
                                            } finally {
                                                setPayment(prev => ({ ...prev, saving: false }));
                                                setLoading(false);
                                            }
                                        }} className="px-3 py-1 bg-green-600 text-white rounded">Paid</button>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <div>
                                <div className="text-sm text-gray-500 mb-4">No bill available.</div>
                                <div className="flex justify-end">
                                    <button onClick={() => setBillModal({ isOpen: false, loading: false, bill: null })} className="px-3 py-1 bg-gray-100 rounded">Close</button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
                        )}
                    </div>
                )}\r
                        </div>
                    )}

                </main>
            </div>

            {/* CDSS Alert Modal */}
            {showCdssModal && (
              <CdssAlertModal
                alerts={cdssAlerts}
                onProceed={handleCdssModalProceed}
                onCancel={() => {
                  setShowCdssModal(false);
                  setCdssAlerts([]);
                  setPendingPrescriptionSave(null);
                }}
              />
            )}

            {/* Profile Settings Modal */}
            <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />

            <ConfirmationModal
                isOpen={confirmState.open}
                title={confirmState.title}
                message={confirmState.message}
                onConfirm={confirmState.onConfirm}
                onCancel={() => setConfirmState({ open: false })}
            />
        </div>
    );
};

export default IpdDetails;
