import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { DiscountModal } from '../../components/billing/BillActionModals';
import ConfirmationModal from '../../components/ConfirmationModal';
import DateSelect from '../../components/DateSelect';
import EmptyState from '../../components/EmptyState';
import FrequencyInput from '../../components/FrequencyInput';
import IcuAdmitModal from '../../components/IcuAdmitModal';
import IcuDischargeToIpdModal from '../../components/IcuDischargeToIpdModal';
import CancelAdmissionModal from '../../components/ipd/CancelAdmissionModal';
import DietCard from '../../components/ipd/DietCard';
import DischargeSummaryModal from '../../components/ipd/DischargeSummaryModal';
import { ConfirmDischargeModal, dischargeTypeLabel } from '../../components/ipd/DischargeType';
import DoctorRoundsTimeline from '../../components/ipd/DoctorRoundsTimeline';
import InsurancePanel from '../../components/ipd/InsurancePanel';
import InvestigationsPanel from '../../components/ipd/InvestigationsPanel';
import IpdMoneyPanel from '../../components/ipd/IpdMoneyPanel';
import Navbar from '../../components/Navbar';
import { ChangeDoctorModal } from '../../components/opd/OpdVisitActions';
import PageHeader from '../../components/PageHeader';
import ProfileModal from '../../components/ProfileModal';
import Sidebar from '../../components/Sidebar';
import { SkeletonDetailCard, SkeletonFormCard } from '../../components/Skeleton';
import { StockBadge } from '../../components/stock/StockPicker';
import { useToast } from '../../context/ToastContext';
import useWebSocket from '../../hooks/useWebSocket';
import authService from '../../services/authService';
import formAccessService from '../../services/formAccessService';
import hospitalService from '../../services/hospitalService';
import icuService from '../../services/icuService';
import nurseService from '../../services/nurseService';
import otService from '../../services/otService';
import wardService from '../../services/wardService';
import { extractApiError } from '../../utils/apiError';
import { canManageBilling as userCanManageBilling } from '../../utils/billingAccess';
import { FOOD_TIMING_OPTIONS, isFoodTimingApplicable } from '../../utils/foodTiming';
import { printBlob } from '../../utils/printPdf';
import { buildSidebarConfig, getRoleNavigateUrl } from '../../utils/sidebarNavigation';
import IcuStayCard from './icu/IcuStayCard';
import AdmissionFormModal from './nurse/AdmissionFormModal';
import ConsentFormsPanel from './nurse/ConsentFormsPanel';
import InfusionPanel from './nurse/InfusionPanel';
import InitialAssessmentPanel from './nurse/InitialAssessmentPanel';
import IoChartPanel from './nurse/IoChartPanel';
import MedicationPanel from './nurse/MedicationPanel';
import NotesPanel from './nurse/NotesPanel';
import SeverityScorePanel from './nurse/SeverityScorePanel';
import SugarChartPanel from './nurse/SugarChartPanel';
import VentilatorPanel from './nurse/VentilatorPanel';
import VitalsPanel from './nurse/VitalsPanel';
import VulnerabilityAssessmentPanel from './nurse/VulnerabilityAssessmentPanel';
import SurgeryRequestModal from './ot/SurgeryRequestModal';
import StayBillsSummary from './StayBillsSummary';

const IpdDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState(null);
  // MedicationPanel fetches its own chart once; bump this after prescribing or stopping a
  // medicine so it reloads instead of saying "No prescriptions yet" beside the new order.
  const [medChartVersion, setMedChartVersion] = useState(0);
  // The admission's bill, loaded when Discount is opened (DiscountModal needs gross/paid).
  const [discountBill, setDiscountBill] = useState(null);
  const [user, setUser] = useState(() => authService.getCurrentUser() || {});
  const isDoctor = authService.isDoctor();
  const isNurse = user?.role === 'NURSE' || user?.role === 'NURSE_INCHARGE';
  const isReceptionist = authService.isReceptionist();
  const isSoloDoctor =
    isDoctor && (user?.receptionMode === 'SOLO' || user?.receptionMode === 'BOTH');
  const [notesModal, setNotesModal] = useState({ isOpen: false, category: 'DOCTOR' });
  const { success, error: toastError } = useToast();
  const [otSurgery, setOtSurgery] = useState(null);
  const [otModalOpen, setOtModalOpen] = useState(false);
  const [changeDoctorOpen, setChangeDoctorOpen] = useState(false);
  const hasOT = (user?.modules || []).includes('OT');

  // ICU admission & discharge state
  const [icuStays, setIcuStays] = useState([]);
  const [isIcuAdmitOpen, setIsIcuAdmitOpen] = useState(false);
  const [isAdmissionFormOpen, setIsAdmissionFormOpen] = useState(false);
  const [isIcuDischargeOpen, setIsIcuDischargeOpen] = useState(false);
  const isIcuActive = icuStays[0]?.status === 'ACTIVE';

  // Files & Access: which clinical forms this role may see / edit here.
  const [tab, setTab] = useState('overview');
  // Bumped on every realtime REFRESH_DATA. `load()` refetches the case header only;
  // the clinical sub-tab panels fetch their own data and need their own signal, or a
  // reading a nurse charts stays invisible in an open case until someone reloads.
  const [panelRefreshKey, setPanelRefreshKey] = useState(0);
  const [formVerdicts, setFormVerdicts] = useState({});
  useEffect(() => {
    let active = true;
    formAccessService
      .effective()
      .then((v) => {
        if (active) setFormVerdicts(v || {});
      })
      .catch(() => {
        if (active) setFormVerdicts({});
      });
    return () => {
      active = false;
    };
  }, []);

  const FORM_KEY_BY_TAB = {
    vitals: 'VITALS',
    medication: 'MEDICATION',
    notes: 'NOTES',
    assessment: 'INITIAL_ASSESSMENT',
    vulnerability: 'VULNERABILITY_ASSESSMENT',
    sugar: 'SUGAR_CHART',
    io: 'IO_CHART',
    ventilator: 'VENTILATOR',
    scores: 'SEVERITY_SCORE',
  };
  const verdictFor = (tabId) => formVerdicts[FORM_KEY_BY_TAB[tabId]] || 'EDITABLE';
  const admissionId = Number(id);

  // Fluid balance, ventilation, severity scores and infusions are ICU charting: the server
  // serves them only to a facility with the ICU module, so they are not offered without it.
  const hasIcuModule = (authService.getCurrentUser()?.modules || []).includes('ICU');
  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'investigations', label: 'Investigations' },
    // Defined before hasBilling / isAdmin below, so worked out from the user directly.
    ...(userCanManageBilling(user) &&
    (isReceptionist || isDoctor || user?.role === 'HOSPITAL_ADMIN')
      ? [{ id: 'insurance', label: 'Insurance' }]
      : []),
    ...(verdictFor('vitals') !== 'HIDDEN' ? [{ id: 'vitals', label: 'Vitals' }] : []),
    ...(verdictFor('medication') !== 'HIDDEN' ? [{ id: 'medication', label: 'Medication' }] : []),
    ...(verdictFor('assessment') !== 'HIDDEN'
      ? [{ id: 'assessment', label: 'Initial Assessment' }]
      : []),
    ...(verdictFor('vulnerability') !== 'HIDDEN'
      ? [{ id: 'vulnerability', label: 'Vulnerability Assessment' }]
      : []),
    ...(verdictFor('sugar') !== 'HIDDEN' ? [{ id: 'sugar', label: 'Sugar Chart' }] : []),
    ...(hasIcuModule && verdictFor('io') !== 'HIDDEN'
      ? [{ id: 'io', label: 'Intake / Output' }]
      : []),
    ...(hasIcuModule && verdictFor('ventilator') !== 'HIDDEN'
      ? [{ id: 'ventilator', label: 'Ventilator' }]
      : []),
    ...(hasIcuModule && verdictFor('scores') !== 'HIDDEN'
      ? [{ id: 'scores', label: 'Severity Scores' }]
      : []),
    ...(hasOT && otSurgery ? [{ id: 'consent', label: 'Consent Forms' }] : []),
  ];
  // If the active tab got hidden by a Files & Access change, fall back to Overview.
  useEffect(() => {
    if (!tabs.some((t) => t.id === tab)) setTab('overview');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formVerdicts, otSurgery, tab]);

  useEffect(() => {
    if (!hasOT || !isDoctor || !id) return;
    let active = true;
    otService
      .getActiveForAdmission(id)
      .then((s) => {
        if (active) setOtSurgery(s || null);
      })
      .catch(() => {
        if (active) setOtSurgery(null);
      });
    return () => {
      active = false;
    };
  }, [hasOT, isDoctor, id, otModalOpen]);

  // Never JSON.stringify a body or show axios's transport text — see utils/apiError.
  const parseError = extractApiError;

  // Layout States
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const handleLogout = () => {
    const loginUrl = authService.getLoginUrl();
    authService.logout();
    navigate(loginUrl);
  };

  const hasBilling = userCanManageBilling(user);
  const activeDashboard = sessionStorage.getItem('activeDashboard');

  // Manually-expanded sidebar groups (Hospital Admin's grouped sidebar).
  // Default to expanding Patient Management because activeTab is 'ipd'.
  const [expandedSidebarGroups, setExpandedSidebarGroups] = useState(
    () => new Set(['group-patient-management'])
  );

  const handleToggleSidebarGroup = (groupId) => {
    setExpandedSidebarGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupId)) {
        next.delete(groupId);
      } else {
        next.add(groupId);
      }
      return next;
    });
  };

  const sidebarConfig = buildSidebarConfig({
    user,
    activeDashboard,
    expandedGroups: expandedSidebarGroups,
    activeTab: 'ipd',
  });

  const handleTabChange = (tabId) => {
    const url = getRoleNavigateUrl(tabId, user, activeDashboard);
    navigate(url);
  };

  const isAdmin = user?.role === 'HOSPITAL_ADMIN';
  // ICU moves are bed transfers, so they follow the bed-transfer rule the server enforces
  // (receptionist, admin, or a doctor in Solo/Both mode) and need the ICU module, without which
  // the ICU ward list is refused and the modal opens empty.
  const canMoveToIcu =
    (user?.modules || []).includes('ICU') && (isAdmin || isSoloDoctor || isReceptionist);
  // The backend's rule, module and solo mode included: a solo doctor was refused here though the
  // server allows them, and a hospital without BILLING was offered Take Payment.
  const canManageBilling = hasBilling;

  const [confirmState, setConfirmState] = useState({
    open: false,
    title: '',
    message: '',
    onConfirm: null,
  });

  const [followupModal, setFollowupModal] = useState({
    isOpen: false,
    diagnosis: '',
    notes: '',
    saving: false,
  });
  // The discharge summary form; editing = the discharge is already planned.
  const [dischargeModal, setDischargeModal] = useState({ isOpen: false, editing: false });
  const [printingSummary, setPrintingSummary] = useState(false);
  // Local calendar date as YYYY-MM-DD (en-CA), matching the <input type="date"> value and the
  // start-date validation. Prescriptions usually start the same day, so default to today.
  const todayStr = () => new Date().toLocaleDateString('en-CA');
  const [medicineModal, setMedicineModal] = useState({
    isOpen: false,
    medicineId: null,
    medicineName: '',
    hospitalItemName: null,
    quantityPerDose: 1,
    stock: null, // the picked stock row: available, low, out of stock
    type: 'TABLET',
    route: 'ORAL',
    dose: '',
    frequency: '',
    durationDays: '',
    foodTiming: '',
    startDate: todayStr(),
    saving: false,
  });
  const [medSearchResults, setMedSearchResults] = useState([]);

  // Stock levels for the search box: medicines by id, and hospital items (saline, IV fluids).
  const [stockResults, setStockResults] = useState([]);

  useEffect(() => {
    const q = medicineModal.medicineName || '';
    // Only search if query string is non-null and length >= 3, AND we don't currently have an active locked medicineId (meaning doctor is typing new)
    if (q.length >= 3 && !medicineModal.medicineId && !medicineModal.hospitalItemName) {
      const delay = setTimeout(async () => {
        try {
          const [resp, stock] = await Promise.all([
            hospitalService.searchMedicines(q),
            nurseService.searchStock(q).catch(() => []),
          ]);
          setMedSearchResults(resp || []);
          setStockResults(Array.isArray(stock) ? stock : []);
        } catch (e) {
          console.error(e);
        }
      }, 400);
      return () => clearTimeout(delay);
    } else {
      setMedSearchResults([]);
      setStockResults([]);
    }
  }, [medicineModal.medicineName, medicineModal.medicineId, medicineModal.hospitalItemName]);
  const medicineStockById = Object.fromEntries(
    stockResults.filter((o) => o.source === 'MEDICINE').map((o) => [o.medicineId, o])
  );
  const hospitalItemResults = stockResults.filter((o) => o.source === 'HOSPITAL_ITEM');
  const [billModal, setBillModal] = useState({ isOpen: false, loading: false, bill: null });
  const [printingBill, setPrintingBill] = useState(false);
  const [payment, setPayment] = useState({ amount: '', mode: 'CASH', saving: false });
  const [bedModal, setBedModal] = useState({
    isOpen: false,
    wards: [],
    selectedWard: '',
    beds: [],
    selectedBed: '',
    saving: false,
  });

  useEffect(() => {
    if (bedModal.isOpen && bedModal.wards.length === 0) {
      wardService
        .getWards()
        .then((w) => setBedModal((p) => ({ ...p, wards: w || [] })))
        .catch((e) => console.error(e));
    }
  }, [bedModal.isOpen, bedModal.wards.length]);

  useEffect(() => {
    if (bedModal.isOpen && bedModal.selectedWard) {
      wardService
        .getAvailableBeds(bedModal.selectedWard)
        .then((b) => setBedModal((p) => ({ ...p, beds: b || [] })))
        .catch((e) => console.error(e));
    } else {
      setBedModal((p) => ({ ...p, beds: [] }));
    }
  }, [bedModal.selectedWard, bedModal.isOpen]);

  const handleBedChange = async () => {
    if (!bedModal.selectedBed) return toastError('Please select a new bed');
    setBedModal((p) => ({ ...p, saving: true }));
    try {
      await hospitalService.changeBed(id, bedModal.selectedBed);
      success('Bed changed successfully');
      setBedModal((p) => ({ ...p, isOpen: false, selectedBed: '', selectedWard: '' }));
      // Re-fetch latest detail
      setLoading(true);
      const resp = await hospitalService.getIpdDetails(id);
      setData(resp);
    } catch (e) {
      toastError(parseError(e, 'Failed to change bed'));
    } finally {
      setBedModal((p) => ({ ...p, saving: false }));
      setLoading(false);
    }
  };

  const load = async (showSpinner = true) => {
    if (showSpinner) setLoading(true);
    try {
      const [resp, stays] = await Promise.all([
        hospitalService.getIpdDetails(id),
        hasIcuModule ? icuService.getStaysForAdmission(id).catch(() => []) : [],
      ]);
      setData(resp);
      setIcuStays(Array.isArray(stays) ? stays : []);
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
    // The panels refresh even while a modal is open: they are read-only surfaces that no modal
    // is editing, so nothing the user is typing can be thrown away by reloading them.
    setPanelRefreshKey((k) => k + 1);
    if (
      !followupModal.isOpen &&
      !dischargeModal.isOpen &&
      !medicineModal.isOpen &&
      !bedModal.isOpen &&
      !billModal.isOpen &&
      !isIcuAdmitOpen &&
      !isIcuDischargeOpen &&
      !isAdmissionFormOpen &&
      !notesModal.isOpen
    ) {
      load(silent);
    }
  });

  const onAddFollowUp = () => {
    setFollowupModal({ isOpen: true, diagnosis: '', notes: '', saving: false });
  };

  const closeFollowupModal = () =>
    setFollowupModal({ isOpen: false, diagnosis: '', notes: '', saving: false });

  const saveFollowup = async () => {
    if (!followupModal.diagnosis) return toastError('Diagnosis is required');
    setFollowupModal((prev) => ({ ...prev, saving: true }));
    try {
      await hospitalService.addIpdFollowup(id, {
        diagnosis: followupModal.diagnosis,
        notes: followupModal.notes,
        administeredItems: [],
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
      setFollowupModal((prev) => ({ ...prev, saving: false }));
      setLoading(false);
    }
  };

  const onAddMedicine = () => {
    setMedicineModal({
      isOpen: true,
      medicineId: null,
      medicineName: '',
      hospitalItemName: null,
      quantityPerDose: 1,
      stock: null,
      type: 'TABLET',
      route: 'ORAL',
      dose: '',
      frequency: '',
      durationDays: '',
      foodTiming: '',
      startDate: todayStr(),
      saving: false,
    });
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
          setMedChartVersion((v) => v + 1);
          setLoading(true);
          const resp = await hospitalService.getIpdDetails(id);
          setData(resp);
        } catch (err) {
          console.error('Failed to stop medicine', err);
          toastError(parseError(err, 'Failed to stop medicine'));
        } finally {
          setLoading(false);
        }
      },
    });
  };

  const [confirmDischargeOpen, setConfirmDischargeOpen] = useState(false);
  const [cancelAdmissionOpen, setCancelAdmissionOpen] = useState(false);
  const [printingRx, setPrintingRx] = useState('');

  const doCancelAdmission = async (reason, refundMode) => {
    try {
      await hospitalService.cancelIpdAdmission(id, reason, refundMode);
      setCancelAdmissionOpen(false);
      success('Admission cancelled. The bed is free and the request is back under Requested.');
      setData(await hospitalService.getIpdDetails(id));
    } catch (err) {
      toastError(parseError(err, 'Failed to cancel the admission'));
    }
  };

  /** Medication orders (what was prescribed) or medicines given (what reached the patient). */
  const printMedicationPdf = async (which) => {
    if (printingRx) return;
    setPrintingRx(which);
    try {
      const blob =
        which === 'orders'
          ? await hospitalService.downloadIpdPrescription(id)
          : await hospitalService.downloadIpdMedicinesList(id);
      printBlob(blob);
    } catch (err) {
      toastError(extractApiError(err, 'Failed to load the print-out'));
    } finally {
      setPrintingRx('');
    }
  };

  const onPlanDischarge = () => setDischargeModal({ isOpen: true, editing: false });
  const onEditDischargeSummary = () => setDischargeModal({ isOpen: true, editing: true });

  const printDischargeSummary = async () => {
    if (printingSummary) return;
    setPrintingSummary(true);
    try {
      printBlob(await hospitalService.downloadDischargeSummary(id));
    } catch (err) {
      toastError(extractApiError(err, 'Failed to load the discharge summary'));
    } finally {
      setPrintingSummary(false);
    }
  };

  const onCancelDischargePlan = () => {
    setConfirmState({
      open: true,
      title: 'Cancel planned discharge',
      message:
        'The patient stays admitted and returns to normal care. You can plan the discharge again later.',
      onConfirm: async () => {
        try {
          setLoading(true);
          await hospitalService.cancelDischargePlan(id);
          success('Discharge plan cancelled');
          setData(await hospitalService.getIpdDetails(id));
        } catch (err) {
          toastError(parseError(err, 'Failed to cancel the discharge plan'));
        } finally {
          setLoading(false);
        }
      },
    });
  };

  // Errors are shown by toast and re-thrown so the form stays open with what was typed.
  const savePlanDischarge = async (payload) => {
    try {
      await hospitalService.planDischarge(id, payload);
      success(dischargeModal.editing ? 'Discharge summary saved' : 'Discharge planned');
      setDischargeModal({ isOpen: false, editing: false });
      setData(await hospitalService.getIpdDetails(id));
    } catch (err) {
      console.error('Failed to plan discharge', err);
      toastError(parseError(err, 'Failed to plan discharge'));
    }
  };

  // Every unpaid bill of the patient blocks a normal discharge — the IPD bill and the OPD bill
  // from the consultation that led to admission. The server lists them; show them up front.
  const owingText = () => {
    // The insurer's approved share is not the patient's to pay at the desk.
    let insurer = Number(data?.billing?.insuranceExpected) || 0;
    const owing = (data?.billing?.outstandingBills || [])
      .map((o) => {
        if (o.billingType !== 'IPD' || insurer <= 0) return o;
        const covered = Math.min(insurer, Number(o.balance) || 0);
        insurer -= covered;
        return { ...o, balance: (Number(o.balance) || 0) - covered };
      })
      .filter((o) => Number(o.balance) > 0);
    const balance =
      (Number(data?.billing?.balance) || 0) - (Number(data?.billing?.insuranceExpected) || 0);
    return owing.length
      ? owing.map((o) => `${o.customId} (${o.billingType}) ₹${o.balance}`).join(', ')
      : balance > 0
        ? `₹${balance} on this admission's bill`
        : '';
  };

  const onConfirmDischarge = () => setConfirmDischargeOpen(true);

  const doConfirmDischarge = async (body) => {
    try {
      setLoading(true);
      await hospitalService.confirmDischarge(id, body);
      setConfirmDischargeOpen(false);
      success('Discharge completed');
      navigate('/');
    } catch (err) {
      console.error('Failed to confirm discharge', err);
      toastError(parseError(err, 'Failed to confirm discharge'));
    } finally {
      setLoading(false);
    }
  };

  const openBillModal = async () => {
    setBillModal({ isOpen: true, loading: true, bill: null });
    try {
      const b = await hospitalService.getIpdBill(id);
      setBillModal({ isOpen: true, loading: false, bill: b });
      // Start from the balance due; the user can lower it for a part payment.
      const due = Number(b?.balance) || 0;
      setPayment((prev) => ({ ...prev, amount: due > 0 ? String(due) : '' }));
    } catch (err) {
      toastError(extractApiError(err, 'Failed to load bill'));
      setBillModal({ isOpen: false, loading: false, bill: null });
    }
  };

  const handlePrintIpdBill = async () => {
    if (printingBill) return;
    setPrintingBill(true);
    try {
      const billData = await hospitalService.getIpdBill(id);
      if (!billData || !billData.billingId) {
        throw new Error('No billing ID found for this IPD admission');
      }
      const blob = await hospitalService.downloadReceipt(billData.billingId);
      printBlob(blob); // print on this page (hidden iframe), not a new tab
    } catch (err) {
      console.error(err);
      toastError(extractApiError(err, 'Failed to load bill for printing'));
    } finally {
      setPrintingBill(false);
    }
  };

  return (
    <div className="flex h-screen bg-white overflow-hidden">
      {/* Sidebar */}
      <Sidebar
        title={sidebarConfig.title}
        tabs={sidebarConfig.tabs}
        activeTab="ipd"
        onTabChange={handleTabChange}
        onToggleGroup={sidebarConfig.usesGroupedSidebar ? handleToggleSidebarGroup : undefined}
        footerTitle={sidebarConfig.footerTitle}
        footerData={sidebarConfig.footerData}
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
        <main className="flex-1 overflow-x-hidden overflow-y-auto bg-[#fafafa] p-4 md:p-6">
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
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    className="h-4 w-4"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M15 19l-7-7 7-7"
                    />
                  </svg>
                  Back
                </button>
              </div>
              {/* Case header — the only top-right actions are Discharge and Create Surgery Request. */}
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <PageHeader
                    title={
                      // ipdNumber already carries the prefix ("IPD-1"); don't print "IPD IPD-1".
                      /^IPD/i.test(data.ipdNumber || '')
                        ? data.ipdNumber
                        : `IPD ${data.ipdNumber || ''}`.trim()
                    }
                    subtitle={`${data.patient?.name || ''} • ${data.patient?.age || ''} • ${data.patient?.gender || ''}`}
                  />
                </div>
                <div className="flex items-center gap-2 shrink-0 pt-1">
                  {hasOT && isDoctor && !otSurgery && (
                    <button
                      onClick={() => setOtModalOpen(true)}
                      className="px-3 py-1.5 rounded-lg text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 whitespace-nowrap"
                    >
                      Create Surgery Request
                    </button>
                  )}
                  {/* The admission form is commonly filled and signed at the admission desk. */}
                  {(isReceptionist || isAdmin || isSoloDoctor) &&
                    !data.admission?.admissionConfirmed &&
                    (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED') && (
                      <button
                        type="button"
                        onClick={() => setIsAdmissionFormOpen(true)}
                        className="px-3 py-1.5 rounded-lg text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 whitespace-nowrap"
                      >
                        Complete Admission
                      </button>
                    )}
                  {/* Contextual ICU Action Button: Admit to ICU or Discharge to IPD */}
                  {canMoveToIcu &&
                    (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED') &&
                    (isIcuActive ? (
                      <button
                        type="button"
                        onClick={() => setIsIcuDischargeOpen(true)}
                        className="px-3 py-1.5 rounded-lg text-sm font-semibold text-amber-900 bg-amber-100 hover:bg-amber-200 border border-amber-300 shadow-xs transition-colors flex items-center gap-1.5 whitespace-nowrap cursor-pointer"
                        title="Step down / transfer patient from ICU to General IPD Ward"
                      >
                        <svg
                          className="w-4 h-4 text-amber-700"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"
                          />
                        </svg>
                        Discharge to IPD
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setIsIcuAdmitOpen(true)}
                        className="px-3 py-1.5 rounded-lg text-sm font-semibold text-white bg-rose-600 hover:bg-rose-700 shadow-xs transition-colors flex items-center gap-1.5 whitespace-nowrap cursor-pointer"
                        title="Transfer patient to Intensive Care Unit (ICU)"
                      >
                        <svg
                          className="w-4 h-4 text-white"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M12 4v16m8-8H4"
                          />
                        </svg>
                        Admit to ICU
                      </button>
                    ))}
                  {(isReceptionist || isAdmin || isSoloDoctor) && data.status === 'ADMITTED' && (
                    <button
                      type="button"
                      onClick={() => setCancelAdmissionOpen(true)}
                      title="Cancel an admission made by mistake"
                      className="px-3 py-1.5 rounded-lg text-sm font-semibold text-red-700 bg-white border border-red-300 hover:bg-red-50 whitespace-nowrap"
                    >
                      Cancel Admission
                    </button>
                  )}
                  {(isDoctor || isAdmin) && data.status === 'ADMITTED' && (
                    <button
                      onClick={onPlanDischarge}
                      className="px-3 py-1.5 rounded-lg text-sm font-semibold text-white bg-yellow-600 hover:bg-yellow-700 whitespace-nowrap"
                    >
                      Plan Discharge
                    </button>
                  )}
                  {(isDoctor || isAdmin) && data.status === 'DISCHARGE_PLANNED' && (
                    <button
                      type="button"
                      onClick={onEditDischargeSummary}
                      className="px-3 py-1.5 rounded-lg text-sm font-semibold text-yellow-800 bg-white border border-yellow-300 hover:bg-yellow-50 whitespace-nowrap"
                    >
                      Edit Summary
                    </button>
                  )}
                  {data.dischargeSummary &&
                    (data.status === 'DISCHARGE_PLANNED' || data.status === 'DISCHARGED') && (
                      <button
                        type="button"
                        onClick={printDischargeSummary}
                        disabled={printingSummary}
                        className="px-3 py-1.5 rounded-lg text-sm font-semibold text-gray-800 bg-white border border-gray-300 hover:bg-gray-50 whitespace-nowrap disabled:opacity-60"
                      >
                        {printingSummary ? 'Preparing…' : 'Print Discharge Summary'}
                      </button>
                    )}
                  {(isDoctor || isAdmin) && data.status === 'DISCHARGE_PLANNED' && (
                    <button
                      onClick={onCancelDischargePlan}
                      className="px-3 py-1.5 rounded-lg text-sm font-semibold text-yellow-800 bg-yellow-50 border border-yellow-300 hover:bg-yellow-100 whitespace-nowrap"
                    >
                      Cancel Discharge Plan
                    </button>
                  )}
                  {(isReceptionist || isSoloDoctor || isAdmin) &&
                    data.status === 'DISCHARGE_PLANNED' && (
                      <button
                        onClick={onConfirmDischarge}
                        className="px-3 py-1.5 rounded-lg text-sm font-semibold text-white bg-green-600 hover:bg-green-700 whitespace-nowrap"
                      >
                        Confirm Discharge
                      </button>
                    )}
                </div>
              </div>

              {/* Sub-tabs — same set the nurse sees; form tabs respect Files & Access. */}
              <div className="mt-4 border-b border-gray-200 flex gap-1 overflow-x-auto overflow-y-hidden">
                {tabs.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => setTab(t.id)}
                    className={`px-4 py-2 text-sm font-semibold border-b-2 -mb-px whitespace-nowrap ${tab === t.id ? 'border-gray-900 text-gray-900' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
                  >
                    {t.label}
                  </button>
                ))}
              </div>

              {tab === 'vitals' && (
                <div className="mt-4">
                  <VitalsPanel
                    admissionId={admissionId}
                    readOnly={verdictFor('vitals') === 'READ_ONLY'}
                    refreshKey={panelRefreshKey}
                  />
                </div>
              )}
              {tab === 'io' && (
                <div className="mt-4">
                  <IoChartPanel
                    admissionId={admissionId}
                    readOnly={verdictFor('io') === 'READ_ONLY'}
                    refreshKey={panelRefreshKey}
                  />
                </div>
              )}
              {tab === 'ventilator' && (
                <div className="mt-4">
                  {/* Writable here, gated by Files & Access like every other panel on this page:
                      the IPD case is the only ventilator surface an admin or doctor can reach. */}
                  <VentilatorPanel
                    admissionId={admissionId}
                    readOnly={verdictFor('ventilator') === 'READ_ONLY'}
                    refreshKey={panelRefreshKey}
                  />
                </div>
              )}
              {tab === 'scores' && (
                <div className="mt-4">
                  <SeverityScorePanel
                    admissionId={admissionId}
                    readOnly={verdictFor('scores') === 'READ_ONLY'}
                    refreshKey={panelRefreshKey}
                  />
                </div>
              )}
              {tab === 'assessment' && (
                <div className="mt-4">
                  <InitialAssessmentPanel
                    admissionId={admissionId}
                    readOnly={verdictFor('assessment') === 'READ_ONLY'}
                  />
                </div>
              )}
              {tab === 'vulnerability' && (
                <div className="mt-4">
                  <VulnerabilityAssessmentPanel
                    admissionId={admissionId}
                    readOnly={verdictFor('vulnerability') === 'READ_ONLY'}
                  />
                </div>
              )}
              {tab === 'sugar' && (
                <div className="mt-4">
                  <SugarChartPanel
                    admissionId={admissionId}
                    readOnly={verdictFor('sugar') === 'READ_ONLY'}
                  />
                </div>
              )}
              {tab === 'insurance' && (
                <div className="mt-4 bg-white border rounded p-4">
                  <InsurancePanel
                    admissionId={admissionId}
                    canEdit={isReceptionist || isAdmin || isSoloDoctor}
                    onChanged={async () => setData(await hospitalService.getIpdDetails(id))}
                  />
                </div>
              )}
              {tab === 'investigations' && (
                <div className="mt-4 bg-white border rounded p-4">
                  <InvestigationsPanel
                    admissionId={admissionId}
                    canOrder={
                      (isDoctor || isAdmin) &&
                      (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED')
                    }
                    canWork
                  />
                </div>
              )}
              {tab === 'consent' && (
                <div className="mt-4">
                  <ConsentFormsPanel admissionId={admissionId} formVerdicts={formVerdicts} />
                </div>
              )}

              <div
                className={
                  tab === 'overview' || tab === 'medication'
                    ? 'grid grid-cols-1 md:grid-cols-3 gap-6 mt-4'
                    : 'hidden'
                }
              >
                <div className="col-span-2 bg-white border rounded p-4">
                  {tab === 'overview' && (
                    <>
                      <h3 className="font-semibold mb-2">Admission Info</h3>
                      <div className="grid grid-cols-2 gap-2 text-sm">
                        <div>
                          <strong>Admitted:</strong>{' '}
                          {data.admission?.admissionDateTime
                            ? new Date(data.admission.admissionDateTime).toLocaleString()
                            : '-'}
                        </div>
                        <div>
                          <strong>Type:</strong> {data.admission?.admissionType || '-'}
                        </div>
                        <div>
                          <strong>Doctor:</strong> {data.admission?.doctor || '-'}
                          {(isReceptionist || isAdmin || isDoctor) &&
                            (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED') && (
                              <button
                                type="button"
                                onClick={() => setChangeDoctorOpen(true)}
                                aria-label="Change doctor"
                                className="ml-2 text-xs font-semibold text-sky-700 hover:underline"
                              >
                                Change
                              </button>
                            )}
                        </div>
                        <div>
                          <strong>Diagnosis:</strong> {data.admission?.primaryDiagnosis || '-'}
                        </div>
                        <div>
                          <strong>Ward / Bed:</strong> {data.admission?.ward || '-'} /{' '}
                          {data.admission?.bed || '-'}
                          {(isReceptionist || isSoloDoctor || isAdmin) &&
                            (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED') && (
                              <button
                                onClick={() =>
                                  setBedModal((p) => ({
                                    ...p,
                                    isOpen: true,
                                    selectedWard: '',
                                    selectedBed: '',
                                  }))
                                }
                                className="ml-2 px-1.5 py-0.5 bg-gray-100 hover:bg-blue-50 border border-gray-300 text-blue-700 text-[10px] font-bold uppercase rounded shadow-sm transition-colors"
                              >
                                Change
                              </button>
                            )}
                        </div>
                        <div>
                          <strong>Status:</strong> {data.status || '-'}
                          {data.dischargeType &&
                            (data.status === 'DISCHARGE_PLANNED' ||
                              data.status === 'DISCHARGED') && (
                              <span
                                className={`ml-2 text-xs px-2 py-0.5 rounded-full ${data.dischargeType === 'EXPIRED' ? 'bg-gray-800 text-white' : data.dischargeType === 'NORMAL' ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-800'}`}
                              >
                                {dischargeTypeLabel(data.dischargeType)}
                                {data.dischargeNote ? ` — ${data.dischargeNote}` : ''}
                              </span>
                            )}
                        </div>
                        {data.dischargeSummary?.followUpDate && (
                          <div className="col-span-2 text-sm">
                            <strong>Follow-up:</strong>{' '}
                            {new Date(data.dischargeSummary.followUpDate).toLocaleDateString()}
                            {data.dischargeSummary.followUpTime
                              ? ` at ${String(data.dischargeSummary.followUpTime).slice(0, 5)}`
                              : ''}
                            {data.dischargeSummary.followUpAppointmentId
                              ? ` — booked (${data.dischargeSummary.followUpAppointmentId})`
                              : data.status === 'DISCHARGED'
                                ? ' — not booked (no free slot); book it from Appointments'
                                : ' — booked when the patient goes home'}
                          </div>
                        )}
                        {data.status === 'CANCELLED' && (
                          <div className="col-span-2 text-sm bg-red-50 border border-red-200 text-red-800 rounded p-2">
                            <strong>Admission cancelled</strong>
                            {data.cancelledAt
                              ? ` on ${new Date(data.cancelledAt).toLocaleString()}`
                              : ''}
                            {data.cancelReason ? ` — ${data.cancelReason}` : ''}
                          </div>
                        )}
                      </div>

                      <hr className="my-4" />

                      <h3 className="font-semibold mb-2">Doctor&apos;s Rounds</h3>
                      <DoctorRoundsTimeline
                        admissionId={admissionId}
                        refreshKey={`${panelRefreshKey}-${data.medicalRecords?.length || 0}`}
                      />

                      <div className="mt-3 flex items-center gap-2">
                        {isDoctor &&
                          (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED') && (
                            <button
                              className="px-3 py-1 bg-green-700 text-white rounded hover:bg-green-800"
                              onClick={onAddFollowUp}
                            >
                              + Add Round
                            </button>
                          )}
                      </div>
                    </>
                  )}

                  {followupModal.isOpen && (
                    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                      <div className="bg-white rounded-lg w-full max-w-lg p-6">
                        <h3 className="text-lg font-semibold mb-3">Add Round</h3>
                        <div className="mb-3">
                          <label htmlFor="fld-131" className="block text-sm font-medium mb-1">
                            Diagnosis
                          </label>
                          <textarea
                            id="fld-131"
                            value={followupModal.diagnosis}
                            onChange={(e) =>
                              setFollowupModal((prev) => ({ ...prev, diagnosis: e.target.value }))
                            }
                            className="w-full border p-2 rounded"
                            rows={3}
                          />
                        </div>
                        <div className="mb-3">
                          <label htmlFor="fld-130" className="block text-sm font-medium mb-1">
                            Notes
                          </label>
                          <textarea
                            id="fld-130"
                            value={followupModal.notes}
                            onChange={(e) =>
                              setFollowupModal((prev) => ({ ...prev, notes: e.target.value }))
                            }
                            className="w-full border p-2 rounded"
                            rows={4}
                          />
                        </div>

                        <div className="flex justify-end gap-3">
                          <button
                            onClick={closeFollowupModal}
                            className="px-3 py-1 bg-gray-100 rounded"
                          >
                            Cancel
                          </button>
                          <button
                            onClick={saveFollowup}
                            disabled={followupModal.saving}
                            className="px-3 py-1 bg-green-700 text-white rounded hover:bg-green-800"
                          >
                            {followupModal.saving ? 'Saving...' : 'Save'}
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {medicineModal.isOpen && (
                    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                      <div className="bg-white rounded-lg w-full max-w-lg p-6">
                        <h3 className="text-lg font-semibold mb-3">Prescribe Medicine</h3>
                        <p className="text-xs text-gray-500 -mt-2 mb-3">
                          Pick from stock (medicines, saline, IV fluids) so each dose given comes
                          off the inventory and onto the bill, or type a name the patient will
                          bring.
                        </p>

                        {
                          <>
                            <div className="grid grid-cols-2 gap-3">
                              <div className="relative">
                                <label htmlFor="fld-129" className="block text-sm font-medium mb-1">
                                  Medicine Name
                                </label>
                                <input
                                  id="fld-129"
                                  value={medicineModal.medicineName}
                                  onChange={(e) =>
                                    setMedicineModal((prev) => ({
                                      ...prev,
                                      medicineName: e.target.value,
                                      medicineId: null,
                                      hospitalItemName: null,
                                      stock: null,
                                    }))
                                  }
                                  className="w-full border p-2 rounded text-sm outline-none bg-white"
                                  placeholder="Type min 3 letters..."
                                />
                                {(medSearchResults.length > 0 ||
                                  hospitalItemResults.length > 0) && (
                                  <div className="absolute z-20 left-0 right-0 mt-1 bg-white border border-gray-200 rounded shadow-lg max-h-64 overflow-y-auto">
                                    {hospitalItemResults.length > 0 && (
                                      <>
                                        <p className="px-2 pt-2 text-[11px] font-semibold text-gray-500 uppercase">
                                          Hospital inventory
                                        </p>
                                        {hospitalItemResults.map((o) => (
                                          <button
                                            type="button"
                                            key={o.itemName}
                                            onClick={() => {
                                              const fluid = /fluid|saline|ringer|dextrose|iv/i.test(
                                                `${o.type} ${o.name}`
                                              );
                                              setMedicineModal((prev) => ({
                                                ...prev,
                                                medicineId: null,
                                                hospitalItemName: o.itemName,
                                                medicineName: o.name,
                                                stock: o,
                                                type: fluid ? 'IV_FLUID' : prev.type,
                                                route: fluid ? 'IV' : prev.route,
                                                foodTiming: fluid ? '' : prev.foodTiming,
                                              }));
                                              setStockResults([]);
                                            }}
                                            className="w-full text-left p-2 hover:bg-blue-50 border-b text-sm flex justify-between items-center"
                                          >
                                            <span className="font-medium text-gray-800">
                                              {o.name}
                                            </span>
                                            <StockBadge option={o} />
                                          </button>
                                        ))}
                                      </>
                                    )}
                                    {medSearchResults.length > 0 && (
                                      <p className="px-2 pt-2 text-[11px] font-semibold text-gray-500 uppercase">
                                        Medicines
                                      </p>
                                    )}
                                    {medSearchResults.map((m) => (
                                      <div
                                        key={m.id}
                                        role="button"
                                        tabIndex={0}
                                        onKeyDown={(e) => {
                                          if (e.key === 'Enter' || e.key === ' ') {
                                            e.preventDefault();
                                            e.currentTarget.click();
                                          }
                                        }}
                                        onClick={() => {
                                          // Extract number from duration string e.g. "5 Days"
                                          let parsedDur = 0;
                                          if (m.defaultDuration) {
                                            const match = m.defaultDuration.match(/\d+/);
                                            if (match) parsedDur = parseInt(match[0]);
                                          }
                                          const pickedType = m.type?.toUpperCase() || 'TABLET';
                                          setMedicineModal((prev) => ({
                                            ...prev,
                                            medicineId: m.id,
                                            medicineName: m.name,
                                            hospitalItemName: null,
                                            stock: medicineStockById[m.id] || null,
                                            type: pickedType,
                                            dose: m.defaultDosage || '',
                                            frequency: m.defaultFrequency || '',
                                            durationDays: parsedDur || prev.durationDays,
                                            // This is the quiet path to an injection: picking one
                                            // from the catalogue sets the type without the doctor
                                            // touching the Type field, so any timing already
                                            // chosen has to go with it.
                                            foodTiming: isFoodTimingApplicable(
                                              pickedType,
                                              prev.route
                                            )
                                              ? prev.foodTiming
                                              : '',
                                          }));
                                          setMedSearchResults([]);
                                        }}
                                        className="p-2 hover:bg-blue-50 cursor-pointer border-b text-sm flex justify-between items-center"
                                      >
                                        <span className="font-medium text-gray-800">{m.name}</span>
                                        <span className="text-xs text-gray-500 flex items-center gap-2">
                                          {m.type}
                                          <StockBadge option={medicineStockById[m.id]} />
                                        </span>
                                      </div>
                                    ))}
                                  </div>
                                )}
                                {medicineModal.stock?.outOfStock && (
                                  <p className="mt-1 text-xs text-red-700">
                                    Out of stock — ask the patient to bring it, and get it
                                    reordered.
                                  </p>
                                )}
                                {medicineModal.stock?.low && !medicineModal.stock?.outOfStock && (
                                  <p className="mt-1 text-xs text-amber-800">
                                    Low stock: {medicineModal.stock.available} left — ask for it to
                                    be reordered soon.
                                  </p>
                                )}
                                {!medicineModal.medicineId &&
                                  !medicineModal.hospitalItemName &&
                                  medicineModal.medicineName.length >= 3 && (
                                    <p className="mt-1 text-xs text-gray-500">
                                      Not picked from stock — the patient brings it.
                                    </p>
                                  )}
                              </div>
                              <div>
                                <label htmlFor="fld-128" className="block text-sm font-medium mb-1">
                                  Type
                                </label>
                                <select
                                  id="fld-128"
                                  value={medicineModal.type}
                                  onChange={(e) =>
                                    setMedicineModal((prev) => ({
                                      ...prev,
                                      type: e.target.value,
                                      // Drop a timing the new type cannot have, so the form
                                      // never shows an answer it is no longer asking for.
                                      foodTiming: isFoodTimingApplicable(e.target.value, prev.route)
                                        ? prev.foodTiming
                                        : '',
                                    }))
                                  }
                                  className="w-full border p-2 rounded text-sm"
                                >
                                  <option>TABLET</option>
                                  <option>CAPSULE</option>
                                  <option>SYRUP</option>
                                  <option>INJECTION</option>
                                  <option value="IV_FLUID">IV FLUID</option>
                                  <option>OINTMENT</option>
                                  <option>DROPS</option>
                                  <option>INHALER</option>
                                </select>
                              </div>
                              <div>
                                <label htmlFor="fld-127" className="block text-sm font-medium mb-1">
                                  Route
                                </label>
                                <select
                                  id="fld-127"
                                  value={medicineModal.route}
                                  onChange={(e) =>
                                    setMedicineModal((prev) => ({
                                      ...prev,
                                      route: e.target.value,
                                      // Same reason as Type: an IV/IM route also makes the
                                      // question meaningless, so the answer goes with it.
                                      foodTiming: isFoodTimingApplicable(prev.type, e.target.value)
                                        ? prev.foodTiming
                                        : '',
                                    }))
                                  }
                                  className="w-full border p-2 rounded text-sm"
                                >
                                  <option>ORAL</option>
                                  <option>IV</option>
                                  <option>IM</option>
                                </select>
                              </div>
                              <div>
                                <label htmlFor="fld-126" className="block text-sm font-medium mb-1">
                                  Dose
                                </label>
                                <input
                                  id="fld-126"
                                  value={medicineModal.dose}
                                  onChange={(e) =>
                                    setMedicineModal((prev) => ({ ...prev, dose: e.target.value }))
                                  }
                                  className="w-full border p-2 rounded text-sm"
                                />
                              </div>
                              {(medicineModal.medicineId || medicineModal.hospitalItemName) && (
                                <div>
                                  <label
                                    htmlFor="fld-qty-per-dose"
                                    className="block text-sm font-medium mb-1"
                                  >
                                    Units per dose (from stock)
                                  </label>
                                  <input
                                    id="fld-qty-per-dose"
                                    type="number"
                                    min="1"
                                    value={medicineModal.quantityPerDose}
                                    onChange={(e) =>
                                      setMedicineModal((prev) => ({
                                        ...prev,
                                        quantityPerDose: e.target.value,
                                      }))
                                    }
                                    className="w-full border p-2 rounded text-sm"
                                  />
                                </div>
                              )}
                              <div>
                                <span className="block text-sm font-medium mb-1">Frequency</span>
                                <div className="w-full border p-2 rounded">
                                  <FrequencyInput
                                    value={medicineModal.frequency}
                                    onChange={(val) =>
                                      setMedicineModal((prev) => ({ ...prev, frequency: val }))
                                    }
                                  />
                                </div>
                              </div>
                              <div>
                                <label htmlFor="fld-125" className="block text-sm font-medium mb-1">
                                  Duration (days)
                                </label>
                                <input
                                  id="fld-125"
                                  type="number"
                                  value={medicineModal.durationDays}
                                  onChange={(e) =>
                                    setMedicineModal((prev) => ({
                                      ...prev,
                                      durationDays: e.target.value,
                                    }))
                                  }
                                  className="w-full border p-2 rounded text-sm"
                                />
                              </div>
                              <div>
                                <label
                                  htmlFor="fld-food-timing"
                                  className="block text-sm font-medium mb-1"
                                >
                                  Food Timing
                                </label>
                                <select
                                  id="fld-food-timing"
                                  value={medicineModal.foodTiming || ''}
                                  disabled={
                                    !isFoodTimingApplicable(medicineModal.type, medicineModal.route)
                                  }
                                  aria-describedby={
                                    isFoodTimingApplicable(medicineModal.type, medicineModal.route)
                                      ? undefined
                                      : 'fld-food-timing-na'
                                  }
                                  onChange={(e) =>
                                    setMedicineModal((prev) => ({
                                      ...prev,
                                      foodTiming: e.target.value,
                                    }))
                                  }
                                  className="w-full border p-2 rounded text-sm disabled:bg-gray-100 disabled:text-gray-400 disabled:cursor-not-allowed"
                                >
                                  <option value="">Not stated</option>
                                  {FOOD_TIMING_OPTIONS.map(([value, label]) => (
                                    <option key={value} value={value}>
                                      {label}
                                    </option>
                                  ))}
                                </select>
                                {!isFoodTimingApplicable(
                                  medicineModal.type,
                                  medicineModal.route
                                ) && (
                                  <p id="fld-food-timing-na" className="mt-1 text-xs text-gray-500">
                                    Not applicable for injections.
                                  </p>
                                )}
                              </div>
                              <div>
                                <label htmlFor="fld-124" className="block text-sm font-medium mb-1">
                                  Start Date
                                </label>
                                <DateSelect
                                  value={medicineModal.startDate}
                                  onChange={(v) =>
                                    setMedicineModal((prev) => ({
                                      ...prev,
                                      startDate: v,
                                    }))
                                  }
                                />
                              </div>
                            </div>
                            <div className="flex justify-end gap-3 mt-4">
                              <button
                                onClick={() =>
                                  setMedicineModal((prev) => ({ ...prev, isOpen: false }))
                                }
                                className="px-3 py-1 bg-gray-100 rounded text-sm"
                              >
                                Cancel
                              </button>
                              <button
                                onClick={async () => {
                                  if (!medicineModal.medicineName)
                                    return toastError('Medicine name required');
                                  if (!medicineModal.dose || !medicineModal.dose.trim())
                                    return toastError('Dose is required');
                                  if (!medicineModal.frequency || !medicineModal.frequency.trim())
                                    return toastError('Frequency is required');
                                  if (!medicineModal.type) return toastError('Type is required');
                                  if (!medicineModal.route) return toastError('Route is required');
                                  if (!medicineModal.startDate)
                                    return toastError('Start date is required');

                                  // Compare against local today (same basis as the default + the date input),
                                  // so a same-day prescription is never wrongly flagged as "in the past".
                                  if (medicineModal.startDate < todayStr()) {
                                    return toastError(
                                      'Prescription start date cannot be in the past'
                                    );
                                  }
                                  if (
                                    !Number(medicineModal.durationDays) ||
                                    Number(medicineModal.durationDays) <= 0
                                  ) {
                                    return toastError('Duration must be at least 1 day');
                                  }

                                  setMedicineModal((prev) => ({ ...prev, saving: true }));
                                  try {
                                    const payload = {
                                      medicineId: medicineModal.medicineId,
                                      hospitalItemName: medicineModal.hospitalItemName,
                                      quantityPerDose: Number(medicineModal.quantityPerDose) || 1,
                                      medicineName: medicineModal.medicineName,
                                      type: medicineModal.type,
                                      route: medicineModal.route,
                                      dose: medicineModal.dose,
                                      frequency: medicineModal.frequency,
                                      durationDays: Number(medicineModal.durationDays),
                                      startDate: medicineModal.startDate || null,
                                      // Authoritative guard, not a convenience: the field is
                                      // also cleared as the order changes, but only this
                                      // decides what leaves the browser, so no present or
                                      // future path can put a meal time on an injection.
                                      foodTiming: isFoodTimingApplicable(
                                        medicineModal.type,
                                        medicineModal.route
                                      )
                                        ? medicineModal.foodTiming || null
                                        : null,
                                    };
                                    await hospitalService.addIpdPrescription(id, payload);
                                    success('Medicine prescribed successfully');
                                    setMedicineModal((prev) => ({ ...prev, isOpen: false }));
                                    setMedChartVersion((v) => v + 1);
                                    await load(false); // Silent reload to show updated prescriptions
                                  } catch (err) {
                                    console.error('Failed to add medicine', err);
                                    toastError(parseError(err, 'Failed to add medicine'));
                                  } finally {
                                    setMedicineModal((prev) => ({ ...prev, saving: false }));
                                  }
                                }}
                                disabled={medicineModal.saving}
                                className="px-3 py-1 bg-blue-600 text-white rounded text-sm"
                              >
                                {medicineModal.saving ? 'Saving...' : 'Save Prescription'}
                              </button>
                            </div>
                          </>
                        }
                      </div>
                    </div>
                  )}

                  {tab === 'medication' && (
                    <>
                      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                        <h3 className="font-semibold">Current Medicines & Items</h3>
                        <div className="flex gap-2">
                          <button
                            type="button"
                            onClick={() => printMedicationPdf('orders')}
                            disabled={!!printingRx}
                            className="px-2.5 py-1 text-xs font-semibold rounded border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-60"
                          >
                            {printingRx === 'orders' ? 'Preparing…' : 'Print medication orders'}
                          </button>
                          <button
                            type="button"
                            onClick={() => printMedicationPdf('given')}
                            disabled={!!printingRx}
                            className="px-2.5 py-1 text-xs font-semibold rounded border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-60"
                          >
                            {printingRx === 'given' ? 'Preparing…' : 'Print medicines given'}
                          </button>
                        </div>
                      </div>

                      {/* Prescribed Medicines — full history; stopped kept & struck-through */}
                      {(() => {
                        const rxList = data.allPrescriptions || data.activePrescriptions || [];
                        if (rxList.length === 0) return null;
                        return (
                          <>
                            <p className="text-xs font-semibold text-blue-700 uppercase tracking-wide mb-1">
                              Prescribed Medicines
                            </p>
                            <ul className="space-y-2 mb-3">
                              {rxList.map((p, i) => {
                                const st = (p.status || '').toUpperCase();
                                const stopped = st === 'STOPPED';
                                const completed = st === 'COMPLETED';
                                return (
                                  <li
                                    key={i}
                                    className={`p-2 border rounded flex justify-between items-center ${stopped ? 'border-red-100 bg-red-50' : completed ? 'border-gray-200 bg-gray-50' : 'border-blue-100 bg-blue-50'}`}
                                  >
                                    <div>
                                      <div
                                        className={`font-medium ${stopped ? 'line-through text-gray-500' : ''}`}
                                      >
                                        {p.name}
                                      </div>
                                      <div className="text-xs text-gray-600">
                                        {p.type} • {p.route} • {p.frequency}
                                      </div>
                                    </div>
                                    <div>
                                      {stopped ? (
                                        <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-red-100 text-red-700">
                                          STOPPED
                                        </span>
                                      ) : completed ? (
                                        <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-gray-200 text-gray-600">
                                          COMPLETED
                                        </span>
                                      ) : isDoctor ? (
                                        <button
                                          className="px-2 py-1 bg-yellow-500 text-white rounded text-xs"
                                          onClick={() => onStopMedicine(p.id)}
                                        >
                                          Stop
                                        </button>
                                      ) : (
                                        <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-green-100 text-green-700">
                                          ACTIVE
                                        </span>
                                      )}
                                    </div>
                                  </li>
                                );
                              })}
                            </ul>
                          </>
                        );
                      })()}

                      {/* Administered Stock Items */}
                      {data.administeredItems && data.administeredItems.length > 0 && (
                        <>
                          <p className="text-xs font-semibold text-teal-700 uppercase tracking-wide mb-1">
                            Administered Stock Items
                          </p>
                          <ul className="space-y-2 mb-3">
                            {data.administeredItems.map((item, i) => (
                              <li
                                key={i}
                                className="p-2 border border-teal-100 rounded bg-teal-50 flex justify-between items-center"
                              >
                                <div>
                                  <div className="font-medium flex items-center gap-2">
                                    {item.name}
                                    <span className="text-xs bg-teal-100 text-teal-700 px-1.5 py-0.5 rounded font-semibold">
                                      Stock
                                    </span>
                                  </div>
                                  <div className="text-xs text-gray-500">
                                    Qty: {item.quantity}
                                    {item.administeredAt ? ` • ${item.administeredAt}` : ''}
                                  </div>
                                </div>
                              </li>
                            ))}
                          </ul>
                        </>
                      )}

                      {/* Empty state — no prescriptions AND no administered items */}
                      {(!(data.allPrescriptions || data.activePrescriptions) ||
                        (data.allPrescriptions || data.activePrescriptions).length === 0) &&
                        (!data.administeredItems || data.administeredItems.length === 0) && (
                          <div className="text-sm text-gray-500">
                            No medicines or administered items.
                          </div>
                        )}

                      {isDoctor && data.status !== 'DISCHARGED' && (
                        <div className="mt-3">
                          <button
                            className="px-3 py-1 bg-blue-600 text-white rounded"
                            onClick={onAddMedicine}
                          >
                            + Add Medicine
                          </button>
                        </div>
                      )}

                      {/* Medication Administration Record — recorded here by whoever the hospital allows
                          (Settings → who may record medicines). */}
                      <hr className="my-4" />
                      <h3 className="font-semibold mb-2">
                        Medication Administration History (MAR)
                      </h3>
                      <MedicationPanel key={medChartVersion} admissionId={admissionId} />

                      {/* Continuous infusions sit with the MAR. Unlike the MAR above — which is
                          read-only here because nurses record administration — infusions follow
                          Files & Access like every other panel on this page, so whoever the
                          server lets edit MEDICATION can start and titrate one from the case. */}
                      <hr className="my-4" />
                      {hasIcuModule && (
                        <InfusionPanel
                          admissionId={admissionId}
                          readOnly={verdictFor('medication') === 'READ_ONLY'}
                          refreshKey={panelRefreshKey}
                        />
                      )}
                    </>
                  )}
                </div>

                <aside className="bg-white border rounded p-4">
                  <DietCard
                    admissionId={admissionId}
                    refreshKey={panelRefreshKey}
                    canEdit={
                      (isDoctor || isAdmin) &&
                      (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED')
                    }
                  />
                  {/* Discharge + Create Surgery Request now live in the case header (top right). */}
                  {/* ICU-10: renders nothing unless this admission has an ICU stay. */}
                  {hasIcuModule && (
                    <IcuStayCard admissionId={admissionId} refreshKey={panelRefreshKey} />
                  )}
                  {hasOT && otSurgery && (
                    <>
                      <h3 className="font-semibold mb-2">Operation Theatre</h3>
                      <div className="text-sm space-y-1">
                        <div className="font-medium text-gray-800">{otSurgery.procedureName}</div>
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-indigo-50 text-indigo-700">
                          Surgery: {otSurgery.status?.replace('_', ' ')}
                        </span>
                      </div>
                      <hr className="my-4" />
                    </>
                  )}

                  <div className="mb-4">
                    <h3 className="font-semibold mb-2">Nurse Notes</h3>
                    <div className="flex flex-col gap-2">
                      <button
                        type="button"
                        className="w-full flex items-center justify-between px-3 py-2 text-sm font-medium rounded-lg border border-teal-200 bg-teal-50 text-teal-800 hover:bg-teal-100 transition shadow-sm"
                        onClick={() => setNotesModal({ isOpen: true, category: 'NURSE' })}
                      >
                        <span className="flex items-center gap-1.5">
                          <svg
                            className="w-4 h-4 text-teal-600"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth="2"
                              d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
                            />
                          </svg>
                          Nurse Round Notes
                        </span>
                        <span
                          className={`text-[10px] uppercase tracking-wide font-bold px-1.5 py-0.5 rounded ${
                            isNurse ? 'bg-teal-200 text-teal-900' : 'bg-gray-100 text-gray-600'
                          }`}
                        >
                          {isNurse ? 'Write & Read' : 'Read Only'}
                        </span>
                      </button>
                    </div>
                  </div>

                  <h3 className="font-semibold mb-2">Billing</h3>
                  {canManageBilling ? (
                    <div>
                      {data.billing ? (
                        <div className="text-sm">
                          <div>
                            <strong>Total:</strong> ₹{data.billing.totalAmount}
                          </div>
                          {Number(data.billing.discountAmount) > 0 && (
                            <div className="text-emerald-700">
                              <strong>Discount:</strong> ₹{data.billing.discountAmount} (included)
                            </div>
                          )}
                          <div>
                            <strong>Paid:</strong> ₹{data.billing.paidAmount}
                          </div>
                          <div>
                            <strong>Balance:</strong> ₹{data.billing.balance}
                          </div>
                          <StayBillsSummary ipdId={id} />
                          <IpdMoneyPanel
                            ipdId={id}
                            data={data}
                            isAdmin={isAdmin}
                            canAct={
                              (isReceptionist || isAdmin || isSoloDoctor) &&
                              (data.status === 'ADMITTED' || data.status === 'DISCHARGE_PLANNED')
                            }
                            onChanged={async () => setData(await hospitalService.getIpdDetails(id))}
                          />
                        </div>
                      ) : (
                        <div className="text-sm text-gray-500">No billing records found.</div>
                      )}
                      <div className="mt-3 flex gap-2">
                        <button
                          className="px-3 py-1 bg-green-700 text-white rounded hover:bg-green-800"
                          onClick={openBillModal}
                        >
                          Take Payment
                        </button>
                        <button
                          className="px-3 py-1 border border-gray-300 text-gray-700 rounded hover:bg-gray-50"
                          onClick={async () => {
                            try {
                              setDiscountBill(await hospitalService.getIpdBill(id));
                            } catch (err) {
                              toastError(parseError(err, 'Could not load the bill'));
                            }
                          }}
                        >
                          Discount
                        </button>
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
                    <div className="text-sm text-gray-500">
                      Billing is not visible to your role.
                    </div>
                  )}
                </aside>
              </div>

              {/* Surgery Request Modal — top level so it opens from any sub-tab (its trigger is the header button). */}
              {otModalOpen && (
                <SurgeryRequestModal
                  admissionId={id}
                  onClose={() => setOtModalOpen(false)}
                  onCreated={() => {}}
                />
              )}

              {isAdmissionFormOpen && (
                <AdmissionFormModal
                  admissionId={Number(id)}
                  onClose={() => setIsAdmissionFormOpen(false)}
                  onConfirmed={() => load(false)}
                />
              )}

              {/* ICU Admit Modal (single-patient contextual) */}
              {isIcuAdmitOpen && data && (
                <IcuAdmitModal
                  isOpen={isIcuAdmitOpen}
                  onClose={() => setIsIcuAdmitOpen(false)}
                  initialPatient={{
                    id: admissionId,
                    ipdId: admissionId,
                    ipdNumber: data.ipdNumber,
                    patientName: data.patient?.name,
                    wardName: data.admission?.ward,
                    bedNumber: data.admission?.bed,
                  }}
                  onSuccess={() => {
                    load(false);
                    setPanelRefreshKey((k) => k + 1);
                  }}
                />
              )}

              {/* ICU Discharge to IPD Modal (single-patient contextual) */}
              {isIcuDischargeOpen && data && (
                <IcuDischargeToIpdModal
                  isOpen={isIcuDischargeOpen}
                  onClose={() => setIsIcuDischargeOpen(false)}
                  initialIcuPatient={{
                    id: admissionId,
                    ipdId: admissionId,
                    ipdNumber: data.ipdNumber,
                    patientName: data.patient?.name,
                    icuWardName: data.admission?.ward,
                    bedNumber: data.admission?.bed,
                  }}
                  onSuccess={() => {
                    load(false);
                    setPanelRefreshKey((k) => k + 1);
                  }}
                />
              )}

              {changeDoctorOpen && (
                <ChangeDoctorModal
                  title="Change attending doctor"
                  subtitle={`${data.patient?.name || ''} · now under ${data.admission?.doctor || 'no doctor'}`}
                  currentDoctorId={data.admission?.doctorId}
                  onSave={(doctorId) => hospitalService.changeIpdDoctor(id, doctorId)}
                  onClose={() => setChangeDoctorOpen(false)}
                  onDone={() => load(false)}
                />
              )}
              {confirmDischargeOpen && (
                <ConfirmDischargeModal
                  initialType={data.dischargeType}
                  initialNote={data.dischargeNote}
                  owing={owingText()}
                  unusedAdvance={Math.max(
                    0,
                    -(
                      Number(data.billing?.balance || 0) -
                      Number(data.billing?.insuranceExpected || 0)
                    )
                  )}
                  onClose={() => setConfirmDischargeOpen(false)}
                  onConfirm={doConfirmDischarge}
                />
              )}

              {/* Discharge summary (plan / edit) */}
              {dischargeModal.isOpen && (
                <DischargeSummaryModal
                  summary={dischargeModal.editing ? data.dischargeSummary : null}
                  dischargeType={dischargeModal.editing ? data.dischargeType : 'NORMAL'}
                  dischargeNote={dischargeModal.editing ? data.dischargeNote : ''}
                  currentMedicines={data.activePrescriptions || []}
                  admissionId={admissionId}
                  editing={dischargeModal.editing}
                  onClose={() => setDischargeModal({ isOpen: false, editing: false })}
                  onSave={savePlanDischarge}
                />
              )}

              {/* Bed Change Modal */}
              {bedModal.isOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
                  <div className="bg-white rounded-lg w-full max-w-md p-6 shadow-xl">
                    <h3 className="text-lg font-bold text-gray-900 mb-4">Change Assigned Bed</h3>
                    <div className="mb-4">
                      <label
                        htmlFor="fld-119"
                        className="block text-sm font-medium text-gray-700 mb-1"
                      >
                        Select Ward
                      </label>
                      <select
                        id="fld-119"
                        className="w-full border border-gray-300 p-2 rounded"
                        value={bedModal.selectedWard}
                        onChange={(e) =>
                          setBedModal((p) => ({
                            ...p,
                            selectedWard: e.target.value,
                            selectedBed: '',
                          }))
                        }
                      >
                        <option value="">-- Choose Ward --</option>
                        {bedModal.wards.map((w) => (
                          <option key={w.wardId} value={w.wardId}>
                            {w.wardName} (₹{w.bedPrice}/day)
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="mb-5">
                      <label
                        htmlFor="fld-118"
                        className="block text-sm font-medium text-gray-700 mb-1"
                      >
                        Available Beds
                      </label>
                      <select
                        id="fld-118"
                        className="w-full border border-gray-300 p-2 rounded"
                        value={bedModal.selectedBed}
                        onChange={(e) =>
                          setBedModal((p) => ({ ...p, selectedBed: e.target.value }))
                        }
                        disabled={!bedModal.selectedWard}
                      >
                        <option value="">-- Select Bed --</option>
                        {bedModal.beds.map((b) => (
                          <option key={b.bedId} value={b.bedId}>
                            {b.bedCode}
                          </option>
                        ))}
                      </select>
                      {bedModal.selectedWard && bedModal.beds.length === 0 && (
                        <p className="text-xs text-red-500 mt-1">No available beds in this ward.</p>
                      )}
                    </div>
                    <div className="flex justify-end gap-3">
                      <button
                        onClick={() => setBedModal((p) => ({ ...p, isOpen: false }))}
                        className="px-4 py-2 text-gray-600 bg-gray-100 hover:bg-gray-200 rounded font-medium"
                      >
                        Cancel
                      </button>
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
                  <div className="bg-white rounded-lg w-full max-w-lg max-h-[80vh] flex flex-col p-6">
                    <h3 className="text-lg font-semibold mb-3">IPD Bill</h3>
                    {billModal.loading ? (
                      <SkeletonFormCard fields={3} />
                    ) : billModal.bill ? (
                      <div>
                        <div className="mb-3 text-sm">
                          <div>
                            <strong>Total:</strong> ₹{billModal.bill.totalAmount}
                            {Number(billModal.bill.discountAmount) > 0 && (
                              <span className="text-emerald-700 text-xs">
                                {' '}
                                (after ₹{billModal.bill.discountAmount} discount)
                              </span>
                            )}
                          </div>
                          <div>
                            <strong>Paid:</strong> ₹{billModal.bill.paidAmount}
                          </div>
                          <div>
                            <strong>Balance:</strong> ₹{billModal.bill.balance}
                          </div>
                        </div>
                        <div className="mb-3">
                          <h4 className="font-medium">Items</h4>
                          <ul className="mt-2 space-y-2 max-h-48 overflow-y-auto pr-2">
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
                            <input
                              value={payment.amount}
                              onChange={(e) =>
                                setPayment((prev) => ({ ...prev, amount: e.target.value }))
                              }
                              placeholder="Amount"
                              className="border p-2 rounded"
                            />
                            <select
                              value={payment.mode}
                              onChange={(e) =>
                                setPayment((prev) => ({ ...prev, mode: e.target.value }))
                              }
                              className="border p-2 rounded"
                            >
                              <option value="CASH">CASH</option>
                              <option value="CARD">CARD</option>
                              <option value="UPI">UPI</option>
                            </select>
                          </div>
                          {payment.mode !== 'CASH' && (
                            <input
                              value={payment.reference || ''}
                              onChange={(e) =>
                                setPayment((prev) => ({ ...prev, reference: e.target.value }))
                              }
                              aria-label={
                                payment.mode === 'UPI' ? 'UTR / reference' : 'Card reference'
                              }
                              placeholder={
                                payment.mode === 'UPI'
                                  ? 'UTR / transaction reference (required)'
                                  : 'Card reference / last 4 digits (optional)'
                              }
                              className="border p-2 rounded w-full mt-3"
                            />
                          )}
                          {Number(payment.amount) > billModal.bill.balance && (
                            <p className="text-xs text-amber-600 font-semibold mt-2">
                              ⚠️ Warning: The bill is less than the payment amount
                            </p>
                          )}
                          <div className="flex justify-end gap-3 mt-4">
                            <button
                              onClick={() =>
                                setBillModal({ isOpen: false, loading: false, bill: null })
                              }
                              className="px-3 py-1 bg-gray-100 rounded"
                            >
                              Close
                            </button>
                            <button
                              onClick={async () => {
                                if (!payment.amount) return toastError('Enter amount');
                                const amountVal = Number(payment.amount);
                                if (isNaN(amountVal) || amountVal <= 0) {
                                  return toastError('Enter a valid amount');
                                }
                                if (amountVal > billModal.bill.balance) {
                                  toastError('Payment amount exceeds the outstanding balance');
                                  return;
                                }
                                setPayment((prev) => ({ ...prev, saving: true }));
                                try {
                                  if (payment.mode === 'UPI' && !(payment.reference || '').trim()) {
                                    return toastError('Enter the UPI UTR / reference');
                                  }
                                  const payload = {
                                    amount: amountVal,
                                    mode: payment.mode,
                                    reference: (payment.reference || '').trim() || null,
                                  };
                                  const resp = await hospitalService.payBilling(
                                    billModal.bill.billingId,
                                    payload
                                  );
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
                                  setPayment((prev) => ({ ...prev, saving: false }));
                                  setLoading(false);
                                }
                              }}
                              disabled={payment.saving}
                              title={payment.saving ? 'Recording payment…' : undefined}
                              className="px-3 py-1 bg-green-700 text-white rounded hover:bg-green-800 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                              {payment.saving ? 'Recording…' : 'Paid'}
                            </button>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div>
                        <div className="text-sm text-gray-500 mb-4">No bill available.</div>
                        <div className="flex justify-end">
                          <button
                            onClick={() =>
                              setBillModal({ isOpen: false, loading: false, bill: null })
                            }
                            className="px-3 py-1 bg-gray-100 rounded"
                          >
                            Close
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Doctors / Nurse Round Notes Modal */}
              {notesModal.isOpen && (
                <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4 overflow-y-auto">
                  <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in duration-200">
                    <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 bg-gray-50/80">
                      <div className="flex items-center gap-3">
                        <div
                          className={`w-3 h-3 rounded-full ${
                            notesModal.category === 'DOCTOR' ? 'bg-blue-600' : 'bg-teal-600'
                          }`}
                        />
                        <div>
                          <h3 className="text-lg font-bold text-gray-900">
                            {notesModal.category === 'DOCTOR'
                              ? "Doctor's Round Notes"
                              : "Nurse's Round Notes"}
                          </h3>
                          <p className="text-xs text-gray-500">
                            Patient:{' '}
                            <span className="font-semibold text-gray-700">{data?.patientName}</span>
                            {' • '}
                            <span className="font-medium text-gray-600">
                              {notesModal.category === 'DOCTOR'
                                ? isDoctor
                                  ? 'Write & Read Access'
                                  : 'Read Only Mode'
                                : isNurse
                                  ? 'Write & Read Access'
                                  : 'Read Only Mode'}
                            </span>
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => setNotesModal({ isOpen: false, category: 'DOCTOR' })}
                        className="p-1.5 text-gray-400 hover:text-gray-700 rounded-lg hover:bg-gray-200 transition"
                      >
                        <svg
                          className="w-5 h-5"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth="2"
                            d="M6 18L18 6M6 6l12 12"
                          />
                        </svg>
                      </button>
                    </div>
                    <div className="p-6 overflow-y-auto flex-1">
                      <NotesPanel
                        admissionId={admissionId}
                        category={notesModal.category}
                        readOnly={
                          verdictFor('notes') === 'READ_ONLY' ||
                          (notesModal.category === 'DOCTOR' ? !isDoctor : !isNurse)
                        }
                        refreshKey={panelRefreshKey}
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </main>
      </div>

      {/* Profile Settings Modal */}
      <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />

      {discountBill && (
        <DiscountModal
          bill={{ ...discountBill, patientName: data?.patient?.name }}
          onClose={() => setDiscountBill(null)}
          onDone={async () => {
            setDiscountBill(null);
            await load(false);
          }}
        />
      )}
      <CancelAdmissionModal
        isOpen={cancelAdmissionOpen}
        paidAmount={data?.billing?.paidAmount}
        isAdmin={isAdmin}
        onClose={() => setCancelAdmissionOpen(false)}
        onConfirm={doCancelAdmission}
      />
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
