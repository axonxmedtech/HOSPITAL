import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import hospitalService from '../../services/hospitalService';
import authService from '../../services/authService';
import wardService from '../../services/wardService';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import EmptyState from '../../components/EmptyState';
import { SkeletonDetailCard, SkeletonFormCard } from '../../components/Skeleton';
import useWebSocket from '../../hooks/useWebSocket';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import ProfileModal from '../../components/ProfileModal';

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

    // Layout States
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);

    const handleLogout = () => {
        authService.logout();
        navigate('/login');
    };

    const isSolo = user?.receptionMode === 'SOLO';
    const hasBilling = user?.billingHandler === 'DOCTOR' || user?.billingHandler === 'BOTH';
    const hasInClinic = user?.inClinic !== false;
    const modules = user?.modules || [];

    const getSidebarTabs = () => {
        if (user?.role === 'HOSPITAL_ADMIN') {
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
                { id: 'fees', label: 'Fees', requiredModule: 'OPD' },
                { id: 'audit-logs', label: 'Audit Logs', requiredModule: null },
                { id: 'settings', label: 'Settings', requiredModule: 'OPD' },
            ];
            return adminAllTabs.filter(tab => !tab.requiredModule || modules.includes(tab.requiredModule));
        } else if (user?.role === 'DOCTOR') {
            return [
                { id: 'overview', label: 'Overview' },
                { id: 'appointments', label: 'My Appointments' },
                { id: 'ipd', label: 'IPD' },
                { id: 'queue', label: 'Queue' },
                { id: 'opd', label: 'OPD' },
                ...(isSolo ? [{ id: 'patients', label: 'Patients' }] : []),
                ...((isSolo || hasBilling) ? [{ id: 'billing', label: 'Billing' }] : []),
                ...((isSolo && hasInClinic) ? [{ id: 'inventory', label: 'Medicine Inventory' }] : []),
                ...(isSolo ? [{ id: 'hospital-inventory', label: 'Hospital Inventory' }] : []),
            ];
        } else if (user?.role === 'RECEPTIONIST') {
            return [
                { id: 'overview', label: 'Overview' },
                { id: 'patients', label: 'Patients' },
                { id: 'opd', label: 'OPD' },
                { id: 'ipd', label: 'IPD' },
                { id: 'billing', label: 'Billing' },
                ...(user?.inClinic !== false ? [{ id: 'inventory', label: 'Medicine Inventory' }] : [])
            ].filter(tab => tab.id !== 'billing' || user?.billingHandler !== 'DOCTOR');
        } else if (user?.role === 'PHARMACIST') {
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
        }
        return [];
    };

    const handleTabChange = (tabId) => {
        if (user?.role === 'HOSPITAL_ADMIN') {
            navigate(`/hospital/admin?tab=${tabId}`);
        } else if (user?.role === 'DOCTOR') {
            navigate(`/hospital/doctor?tab=${tabId}`);
        } else if (user?.role === 'RECEPTIONIST') {
            navigate(`/hospital/receptionist?tab=${tabId}`);
        } else if (user?.role === 'PHARMACIST') {
            navigate(`/hospital/pharmacy?tab=${tabId}`);
        }
    };

    const isAdmin = user?.role === 'HOSPITAL_ADMIN';
    const canManageBilling = 
        isAdmin ||
        (isDoctor && (user?.billingHandler === 'DOCTOR' || user?.billingHandler === 'BOTH')) ||
        (isReceptionist && (user?.billingHandler === 'RECEPTIONIST' || user?.billingHandler === 'BOTH'));

    const [followupModal, setFollowupModal] = useState({ isOpen: false, diagnosis: '', notes: '', saving: false });
    const [dischargeModal, setDischargeModal] = useState({ isOpen: false, finalDiagnosis: '', treatmentGiven: '', dischargeNotes: '', followUpDate: '', saving: false });
    const [medicineModal, setMedicineModal] = useState({ isOpen: false, medicineId: null, medicineName: '', type: 'TABLET', route: 'ORAL', dose: '', frequency: '', durationDays: 0, startDate: '', saving: false });
    const [medSearchResults, setMedSearchResults] = useState([]);

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

    useEffect(() => {
        const q = medicineModal.medicineName || '';
        // Only search if query string is non-null and length >= 3, AND we don't currently have an active locked medicineId (meaning doctor is typing new)
        if (q.length >= 3 && !medicineModal.medicineId) {
            const delay = setTimeout(async () => {
                try {
                    const resp = await hospitalService.searchMedicines(q);
                    setMedSearchResults(resp || []);
                } catch (e) { console.error(e); }
            }, 400);
            return () => clearTimeout(delay);
        } else {
            setMedSearchResults([]);
        }
    }, [medicineModal.medicineName, medicineModal.medicineId]);
    const [billModal, setBillModal] = useState({ isOpen: false, loading: false, bill: null });
    const [printingBill, setPrintingBill] = useState(false);
    const [payment, setPayment] = useState({ amount: '', mode: 'CASH', saving: false });
    const [bedModal, setBedModal] = useState({ isOpen: false, wards: [], selectedWard: '', beds: [], selectedBed: '', saving: false });

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
            toastError(e.response?.data || 'Failed to change bed');
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
            toastError(err.response?.data || err.message || 'Failed to save follow-up');
        } finally {
            setFollowupModal(prev => ({ ...prev, saving: false }));
            setLoading(false);
        }
    };

    const onAddMedicine = () => {
        setMedicineModal({ isOpen: true, medicineName: '', type: 'TABLET', route: 'ORAL', dose: '', frequency: '', durationDays: 0, startDate: '', saving: false });
    };

    const onStopMedicine = async (prescriptionId) => {
        if (!window.confirm('Stop this medicine?')) return;
        try {
            await hospitalService.stopPrescription(prescriptionId);
            success('Medicine stopped');
            setLoading(true);
            const resp = await hospitalService.getIpdDetails(id);
            setData(resp);
        } catch (err) {
            console.error('Failed to stop medicine', err);
            toastError(err.response?.data || err.message || 'Failed to stop medicine');
        } finally {
            setLoading(false);
        }
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
            toastError(err.response?.data || err.message || 'Failed to plan discharge');
        } finally {
            setDischargeModal(prev => ({ ...prev, saving: false }));
            setLoading(false);
        }
    };

    const onConfirmDischarge = async () => {
        if (!window.confirm('Confirm discharge? This will finalize and free the bed.')) return;
        try {
            setLoading(true);
            await hospitalService.confirmDischarge(id);
            success('Discharge completed');
            // navigate back to list
            navigate('/');
        } catch (err) {
            console.error('Failed to confirm discharge', err);
            const msg = err.response?.data || err.message || 'Failed to confirm discharge';
            toastError(msg);
        } finally {
            setLoading(false);
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
                        {isDoctor && data.status !== 'DISCHARGE_PLANNED' && data.status !== 'DISCHARGED' && (
                            <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={onAddFollowUp}>+ Add Follow-up</button>
                        )}
                    </div>

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
                                                <div className="relative">
                                                    <label className="block text-sm font-medium mb-1">Medicine Name</label>
                                                    <input 
                                                        value={medicineModal.medicineName} 
                                                        onChange={e => setMedicineModal(prev => ({ ...prev, medicineName: e.target.value, medicineId: null }))} 
                                                        className="w-full border p-2 rounded text-sm outline-none bg-white" 
                                                        placeholder="Type min 3 letters..."
                                                    />
                                                    {medSearchResults.length > 0 && (
                                                        <div className="absolute z-20 left-0 right-0 mt-1 bg-white border border-gray-200 rounded shadow-lg max-h-48 overflow-y-auto">
                                                            {medSearchResults.map(m => (
                                                                <div 
                                                                    key={m.id} 
                                                                    onClick={() => {
                                                                        // Extract number from duration string e.g. "5 Days"
                                                                        let parsedDur = 0;
                                                                        if (m.defaultDuration) {
                                                                            const match = m.defaultDuration.match(/\d+/);
                                                                            if (match) parsedDur = parseInt(match[0]);
                                                                        }
                                                                        setMedicineModal(prev => ({
                                                                            ...prev,
                                                                            medicineId: m.id,
                                                                            medicineName: m.name,
                                                                            type: m.type?.toUpperCase() || 'TABLET',
                                                                            dose: m.defaultDosage || '',
                                                                            frequency: m.defaultFrequency || '',
                                                                            durationDays: parsedDur || prev.durationDays
                                                                        }));
                                                                        setMedSearchResults([]);
                                                                    }}
                                                                    className="p-2 hover:bg-blue-50 cursor-pointer border-b text-sm flex justify-between items-center"
                                                                >
                                                                    <span className="font-medium text-gray-800">{m.name}</span>
                                                                    <span className="text-xs text-gray-500">{m.type}</span>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    )}
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
                                                    setMedicineModal(prev => ({ ...prev, saving: true }));
                                                    try {
                                                        const payload = {
                                                            medicineId: medicineModal.medicineId,
                                                            medicineName: medicineModal.medicineName,
                                                            type: medicineModal.type,
                                                            route: medicineModal.route,
                                                            dose: medicineModal.dose,
                                                            frequency: medicineModal.frequency,
                                                            durationDays: medicineModal.durationDays,
                                                            startDate: medicineModal.startDate || null
                                                        };
                                                        await hospitalService.addIpdPrescription(id, payload);
                                                        success('Medicine prescribed successfully');
                                                        setMedicineModal(prev => ({ ...prev, isOpen: false }));
                                                        await load(false); // Silent reload to show updated prescriptions
                                                    } catch (err) {
                                                        console.error('Failed to add medicine', err);
                                                        toastError(err.response?.data || err.message || 'Failed to add medicine');
                                                    } finally {
                                                        setMedicineModal(prev => ({ ...prev, saving: false }));
                                                    }
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
                                                        toastError(err.response?.data || err.message || 'Failed to administer items');
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
                                                            toastError(err.response?.data || err.message || 'Failed to administer hospital items');
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
                                            {isDoctor ? (
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

                    {isDoctor && data.status !== 'DISCHARGE_PLANNED' && data.status !== 'DISCHARGED' && (
                        <div className="mt-3">
                            <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={onAddMedicine}>+ Add Medicine</button>
                        </div>
                    )}
                </div>

                <aside className="bg-white border rounded p-4">
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
                    {isDoctor && data.status === 'ADMITTED' && (
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
                                                alert('The bill is less than the payment amount');
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
                                                toastError(err.response?.data || 'Failed to record payment');
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
                    )}
                </main>
            </div>

            {/* Profile Settings Modal */}
            <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />
        </div>
    );
};

export default IpdDetails;
