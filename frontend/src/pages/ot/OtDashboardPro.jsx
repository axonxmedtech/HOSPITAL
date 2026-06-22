import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import StatusBadge from '../../components/StatusBadge';
import authService from '../../services/authService';
import otService from '../../services/otService';

const tabs = [
    ['dashboard', 'Dashboard'], ['booking', 'OT Booking'], ['calendar', 'OT Calendar'],
    ['allocation', 'Room Allocation'], ['staff', 'Staff Allocation'], ['preop', 'Pre-Op Checklist'],
    ['who', 'WHO Checklist'], ['anesthesia', 'Anesthesia'], ['tracking', 'Live Status'],
    ['notes', 'Intra-Op Notes'], ['implants', 'Implants'], ['recovery', 'PACU Recovery'],
    ['billing', 'OT Billing'], ['reports', 'Reports'], ['admin', 'Admin Panel'],
].map(([id, label]) => ({ id, label }));

const statCards = [
    ['todaysSurgeries', "Today's Surgeries"], ['ongoingSurgeries', 'Ongoing Surgeries'],
    ['completedSurgeries', 'Completed Surgeries'], ['emergencySurgeries', 'Emergency Surgeries'],
    ['availableOtRooms', 'Available OT Rooms'], ['otUtilization', 'OT Utilization %'],
    ['pendingClearances', 'Pending Pre-Op Clearance'], ['otStaffOnDuty', 'OT Staff On Duty'],
    ['equipmentAvailable', 'Equipment Status'], ['sterilizedInstrumentSets', 'Sterilized Sets'],
];

const preOpFields = [
    ['consentSigned', 'Consent signed'], ['bloodAvailable', 'Blood available'], ['cbc', 'CBC'],
    ['lft', 'LFT'], ['kft', 'KFT'], ['ptInr', 'PT/INR'], ['ecg', 'ECG'],
    ['chestXray', 'Chest X-ray'], ['crossMatching', 'Cross matching'],
    ['physicianFitness', 'Fitness by physician'], ['pacClearance', 'PAC'],
];

const whoGroups = [
    ['Before Induction', [['patientIdentity', 'Patient identity'], ['siteMarked', 'Site marked'], ['consentSigned', 'Consent signed'], ['allergiesChecked', 'Allergies checked'], ['bloodAvailable', 'Blood available']]],
    ['Before Incision', [['teamIntroduction', 'Team introduction'], ['antibioticGiven', 'Antibiotic given'], ['imagingDisplayed', 'Imaging displayed'], ['instrumentCount', 'Instrument count'], ['spongeCount', 'Sponge count']]],
    ['Before Patient Leaves OT', [['finalCount', 'Final count'], ['specimenLabeled', 'Specimen labeled'], ['procedureConfirmed', 'Procedure confirmed'], ['recoveryPlan', 'Recovery plan']]],
];

const statusSteps = ['SCHEDULED', 'PATIENT_IN_OT', 'ANESTHESIA_STARTED', 'INCISION', 'ONGOING', 'CLOSURE', 'COMPLETED', 'RECOVERY'];
const blankBooking = { patientId: '', patientUhid: '', ipdNumber: '', surgeonId: '', assistantSurgeonId: '', otRoomId: '', otTable: '', specialty: '', procedureName: '', diagnosis: '', expectedDurationMinutes: 60, priority: 'ELECTIVE', surgeryType: 'ELECTIVE', scheduledStart: '', scheduledEnd: '', remarks: '' };
const blankPreOp = Object.fromEntries(preOpFields.map(([key]) => [key, false]));
const blankWho = Object.fromEntries(whoGroups.flatMap(([, fields]) => fields).map(([key]) => [key, false]));

const OtDashboardPro = () => {
    const navigate = useNavigate();
    const user = authService.getCurrentUser();
    const [activeTab, setActiveTab] = useState('dashboard');
    const [stats, setStats] = useState({});
    const [bookings, setBookings] = useState([]);
    const [lookups, setLookups] = useState({ patients: [], doctors: [], rooms: [], equipment: [], instrumentSets: [], inventory: [] });
    const [details, setDetails] = useState(null);
    const [reports, setReports] = useState(null);
    const [selectedId, setSelectedId] = useState('');
    const [booking, setBooking] = useState(blankBooking);
    const [preOp, setPreOp] = useState({ ...blankPreOp, notes: '' });
    const [who, setWho] = useState(blankWho);
    const [staff, setStaff] = useState({ role: 'Surgeon', staffName: '', doctorId: '' });
    const [equipment, setEquipment] = useState({ equipmentId: '', status: 'ASSIGNED' });
    const [instrument, setInstrument] = useState({ instrumentSetId: '', status: 'ASSIGNED' });
    const [anesthesia, setAnesthesia] = useState({ anesthesiaType: 'General', drugChart: '', bp: '', pulse: '', spo2: '', temperature: '', respiration: '', complications: '' });
    const [implant, setImplant] = useState({ implantName: '', brand: '', lotNumber: '', batchNumber: '', serialNumber: '', manufacturer: '', expiryDate: '', charge: 0 });
    const [recovery, setRecovery] = useState({ bp: '', pulse: '', spo2: '', painScore: '', consciousness: '', nausea: false, drainOutput: '', urineOutput: '', disposition: 'Ward', notes: '' });
    const [consumable, setConsumable] = useState({ inventoryItemId: '', itemName: '', quantity: 1, unitCharge: 0 });
    const [charge, setCharge] = useState({ chargeType: 'OT Charges', amount: 0, notes: '' });
    const [notes, setNotes] = useState({ intraOpNotes: '', postOpOrders: '' });
    const [room, setRoom] = useState({ name: '', roomCode: '', tableCount: 1, status: 'AVAILABLE' });
    const [surgery, setSurgery] = useState({ name: '', specialty: '', procedureCode: '', defaultDurationMinutes: 60, defaultCharge: 0, active: true });
    const [equipMaster, setEquipMaster] = useState({ name: '', category: '', serialNumber: '', status: 'AVAILABLE', notes: '' });
    const [instMaster, setInstMaster] = useState({ name: '', setType: '', status: 'STERILIZED', contents: '' });
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

    const selected = useMemo(() => bookings.find(x => String(x.id) === String(selectedId)) || bookings[0] || null, [bookings, selectedId]);
    const patient = lookups.patients.find(x => String(x.id) === String(booking.patientId));
    const doctors = lookups.doctors.map(x => ({ value: x.id, label: `${x.name} - ${x.specialization || 'Doctor'}` }));
    const patients = lookups.patients.map(x => ({ value: x.id, label: `${x.customId || x.publicId || x.id} - ${x.name} (${x.age}/${x.gender})` }));

    const load = async () => {
        setLoading(true); setError('');
        try {
            const [dashboardData, bookingData, lookupData] = await Promise.all([otService.dashboard(), otService.bookings(), otService.lookups()]);
            const rows = Array.isArray(bookingData) ? bookingData : [];
            setStats(dashboardData || {});
            setBookings(rows);
            setLookups({
                patients: lookupData?.patients || [], doctors: lookupData?.doctors || [], rooms: lookupData?.rooms || [],
                equipment: lookupData?.equipment || [], instrumentSets: lookupData?.instrumentSets || [], inventory: lookupData?.inventory || [],
            });
            if (!selectedId && rows[0]) setSelectedId(rows[0].id);
        } catch (err) { setError(apiError(err, 'Unable to load OT data')); }
        finally { setLoading(false); }
    };

    const loadDetails = async (id) => {
        if (!id) return;
        try {
            const data = await otService.details(id);
            setDetails(data);
            setPreOp({ ...blankPreOp, notes: '', ...(data.preOpChecklist || {}) });
            setWho({ ...blankWho, ...(data.whoChecklist || {}) });
            setNotes({ intraOpNotes: data.booking?.intraOpNotes || '', postOpOrders: data.booking?.postOpOrders || '' });
        } catch (err) { setError(apiError(err, 'Unable to load OT case')); }
    };

    useEffect(() => { load(); }, []);
    useEffect(() => { if (selected?.id) { setSelectedId(selected.id); loadDetails(selected.id); } }, [selected?.id]);
    useEffect(() => { if (activeTab === 'reports' && !reports) otService.reports().then(setReports).catch(err => setError(apiError(err, 'Unable to load reports'))); }, [activeTab, reports]);

    const ok = (text) => { setMessage(text); setError(''); };
    const fail = (err, text) => { setMessage(''); setError(apiError(err, text)); };
    const withSelected = async (action, success, fallback) => {
        if (!selected) return setError('Select an OT booking first.');
        try { await action(selected.id); ok(success); await load(); await loadDetails(selected.id); }
        catch (err) { fail(err, fallback); }
    };

    const submitBooking = async (event) => {
        event.preventDefault();
        if (!booking.patientId || !booking.procedureName || !booking.scheduledStart || !booking.scheduledEnd) return setError('Patient, procedure, preferred start, and preferred end are required.');
        try {
            await otService.createBooking(toNumbers(booking, ['patientId', 'surgeonId', 'assistantSurgeonId', 'otRoomId', 'expectedDurationMinutes']));
            setBooking(blankBooking); ok('Surgery booking saved.'); await load(); setActiveTab('dashboard');
        } catch (err) { fail(err, 'Unable to save booking'); }
    };

    const saveMaster = async (event, action, data, reset, success, fallback) => {
        event.preventDefault();
        try { await action(data); reset(); ok(success); await load(); }
        catch (err) { fail(err, fallback); }
    };

    const render = () => {
        if (activeTab === 'booking') return BookingTab();
        if (activeTab === 'calendar') return <Calendar bookings={bookings} rooms={lookups.rooms} onSelect={setSelectedId} />;
        if (activeTab === 'allocation') return AllocationTab();
        if (activeTab === 'staff') return StaffTab();
        if (activeTab === 'preop') return <Checklist title="Pre-Operative Checklist" status={details?.preOpChecklist?.status || selected?.clearanceStatus} onSave={() => withSelected(id => otService.updatePreOp(id, preOp), 'Pre-op checklist saved.', 'Unable to save pre-op checklist')}>{preOpFields.map(([key, label]) => <Check key={key} label={label} checked={!!preOp[key]} onChange={v => setPreOp({ ...preOp, [key]: v })} />)}<Textarea label="Notes" value={preOp.notes} onChange={v => setPreOp({ ...preOp, notes: v })} /></Checklist>;
        if (activeTab === 'who') return <Checklist title="WHO Surgical Safety Checklist" status={details?.whoChecklist?.status} onSave={() => withSelected(id => otService.updateWho(id, who), 'WHO checklist saved.', 'Unable to save WHO checklist')}>{whoGroups.map(([group, fields]) => <section key={group} className="space-y-2"><h3 className="text-sm font-semibold text-gray-900">{group}</h3><div className="grid grid-cols-1 md:grid-cols-3 gap-2">{fields.map(([key, label]) => <Check key={key} label={label} checked={!!who[key]} onChange={v => setWho({ ...who, [key]: v })} />)}</div></section>)}</Checklist>;
        if (activeTab === 'anesthesia') return AnesthesiaTab();
        if (activeTab === 'tracking') return <Tracking selected={selected} details={details} onStatus={status => withSelected(id => otService.updateStatus(id, { status, notes: `Moved to ${status}` }), `Status updated to ${label(status)}.`, 'Unable to update status')} />;
        if (activeTab === 'notes') return NotesTab();
        if (activeTab === 'implants') return ImplantTab();
        if (activeTab === 'recovery') return RecoveryTab();
        if (activeTab === 'billing') return BillingTab();
        if (activeTab === 'reports') return <Reports reports={reports} bookings={bookings} />;
        if (activeTab === 'admin') return AdminTab();
        return Dashboard();
    };

    return (
        <div className="h-screen bg-gray-50 flex overflow-hidden">
            <Sidebar title="Operation Theatre" tabs={tabs} activeTab={activeTab} onTabChange={setActiveTab} footerTitle="OT User" footerData={user?.name || user?.email || 'OT'} isCollapsed={sidebarCollapsed} />
            <div className="flex-1 flex flex-col min-w-0">
                <Navbar title="Operation Theatre" user={user} onLogout={() => { authService.logout(); navigate('/login', { replace: true }); }} onProfile={() => ok('Profile is available from hospital settings.')} onToggleSidebar={() => setSidebarCollapsed(value => !value)} actions={<button type="button" onClick={load} className="h-9 px-3 border border-gray-300 text-sm font-semibold text-gray-700 hover:bg-gray-50">Refresh</button>} />
                <main className="flex-1 overflow-y-auto p-6">
                    <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 mb-5">
                        <div><h1 className="text-2xl font-bold text-gray-900">Operation Theatre</h1><p className="text-sm text-gray-500">Scheduling, clearance, allocation, live tracking, recovery, inventory, and OT billing</p></div>
                        <select value={selected?.id || ''} onChange={e => setSelectedId(e.target.value)} className="h-10 min-w-72 border border-gray-300 bg-white px-3 text-sm"><option value="">Select OT case</option>{bookings.map(x => <option key={x.id} value={x.id}>#{x.id} - {x.procedureName} - {date(x.scheduledStart)}</option>)}</select>
                    </div>
                    {loading && <Alert tone="neutral">Loading OT data...</Alert>}
                    {message && <Alert tone="success">{message}</Alert>}
                    {error && <Alert tone="danger">{String(error)}</Alert>}
                    {render()}
                </main>
            </div>
        </div>
    );

    function Dashboard() {
        return <div className="space-y-6"><div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4">{statCards.map(([key, text]) => <Card key={key} label={text} value={stats[key] ?? 0} />)}</div><div className="grid grid-cols-1 xl:grid-cols-[1fr_380px] gap-6"><BookingsTable bookings={bookings.slice(0, 8)} onOpen={setSelectedId} onStart={() => withSelected(id => otService.updateStatus(id, { status: 'ONGOING', notes: 'Started from dashboard' }), 'Surgery started.', 'Unable to start surgery')} onComplete={() => withSelected(id => otService.complete(id), 'Surgery completed and bill generated.', 'Complete WHO checklist before billing.')} /><Panel title="Selected Surgery"><Summary selected={selected} details={details} rooms={lookups.rooms} /></Panel></div></div>;
    }

    function BookingTab() {
        return <div className="grid grid-cols-1 xl:grid-cols-[430px_1fr] gap-6"><Panel title="Schedule Surgery"><form onSubmit={submitBooking} className="space-y-4"><Select label="Patient ID" value={booking.patientId} onChange={v => { const p = lookups.patients.find(x => String(x.id) === String(v)); setBooking({ ...booking, patientId: v, patientUhid: p?.customId || p?.publicId || '' }); }} options={patients} /><Input label="Patient Name" value={patient?.name || ''} readOnly /><div className="grid grid-cols-2 gap-3"><Input label="UHID" value={booking.patientUhid} onChange={v => setBooking({ ...booking, patientUhid: v })} /><Input label="Age / Gender" value={patient ? `${patient.age}/${patient.gender}` : ''} readOnly /></div><Input label="IPD Number" value={booking.ipdNumber} onChange={v => setBooking({ ...booking, ipdNumber: v })} /><div className="grid grid-cols-2 gap-3"><Select label="Surgeon" value={booking.surgeonId} onChange={v => setBooking({ ...booking, surgeonId: v })} options={doctors} /><Select label="Assistant Surgeon" value={booking.assistantSurgeonId} onChange={v => setBooking({ ...booking, assistantSurgeonId: v })} options={doctors} /></div><div className="grid grid-cols-2 gap-3"><Input label="Specialty" value={booking.specialty} onChange={v => setBooking({ ...booking, specialty: v })} /><Input label="Procedure" value={booking.procedureName} onChange={v => setBooking({ ...booking, procedureName: v })} /></div><div className="grid grid-cols-2 gap-3"><Select label="Elective / Emergency" value={booking.priority} onChange={v => setBooking({ ...booking, priority: v, surgeryType: v })} options={opts(['ELECTIVE', 'EMERGENCY'])} /><Input label="Expected Duration" type="number" value={booking.expectedDurationMinutes} onChange={v => setBooking({ ...booking, expectedDurationMinutes: v })} /></div><div className="grid grid-cols-2 gap-3"><Input label="Preferred Start" type="datetime-local" value={booking.scheduledStart} onChange={v => setBooking({ ...booking, scheduledStart: v })} /><Input label="Preferred End" type="datetime-local" value={booking.scheduledEnd} onChange={v => setBooking({ ...booking, scheduledEnd: v })} /></div><div className="grid grid-cols-2 gap-3"><Select label="OT Room" value={booking.otRoomId} onChange={v => setBooking({ ...booking, otRoomId: v })} options={lookups.rooms.map(x => ({ value: x.id, label: x.name }))} /><Input label="Table" value={booking.otTable} onChange={v => setBooking({ ...booking, otTable: v })} /></div><Textarea label="Diagnosis" value={booking.diagnosis} onChange={v => setBooking({ ...booking, diagnosis: v })} /><Textarea label="Remarks" value={booking.remarks} onChange={v => setBooking({ ...booking, remarks: v })} /><button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Save Booking</button></form></Panel><div className="space-y-6"><Panel title="Auto-Pulled Patient Data"><div className="grid grid-cols-1 md:grid-cols-3 gap-3">{['Blood Group', 'Allergies', 'Current Medications', 'Investigation Reports', 'Radiology', 'Previous Surgeries'].map(x => <Info key={x} label={x} value={x === 'Allergies' || x === 'Previous Surgeries' ? patient?.medicalHistory || 'Not recorded' : patient?.bloodGroup || 'Not recorded'} />)}</div></Panel><BookingsTable bookings={bookings} onOpen={setSelectedId} onStart={() => withSelected(id => otService.updateStatus(id, { status: 'ONGOING' }), 'Surgery started.', 'Unable to start surgery')} onComplete={() => withSelected(id => otService.complete(id), 'Surgery completed and bill generated.', 'Complete WHO checklist before billing.')} /></div></div>;
    }

    function AllocationTab() {
        return <div className="grid grid-cols-1 xl:grid-cols-3 gap-6"><Panel title="Room and Table"><Summary selected={selected} details={details} rooms={lookups.rooms} /><p className="mt-3 text-xs text-gray-500">Double booking is blocked automatically for OT rooms, equipment, instruments, and staff.</p></Panel><Panel title="Equipment"><form onSubmit={e => { e.preventDefault(); withSelected(id => otService.assignEquipment(id, toNumbers(equipment, ['equipmentId'])), 'Equipment allocated.', 'Unable to allocate equipment'); }} className="space-y-4"><Select label="Equipment" value={equipment.equipmentId} onChange={v => setEquipment({ ...equipment, equipmentId: v })} options={lookups.equipment.map(x => ({ value: x.id, label: `${x.name} - ${x.status}` }))} /><Select label="Status" value={equipment.status} onChange={v => setEquipment({ ...equipment, status: v })} options={opts(['ASSIGNED', 'IN_USE', 'RELEASED'])} /><button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Assign Equipment</button></form><Mini items={details?.equipmentAssignments || []} render={x => `${nameById(x.equipmentId, lookups.equipment)} - ${x.status}`} /></Panel><Panel title="Instrument Sets"><form onSubmit={e => { e.preventDefault(); withSelected(id => otService.assignInstrument(id, toNumbers(instrument, ['instrumentSetId'])), 'Instrument set allocated.', 'Unable to allocate instrument set'); }} className="space-y-4"><Select label="Instrument Set" value={instrument.instrumentSetId} onChange={v => setInstrument({ ...instrument, instrumentSetId: v })} options={lookups.instrumentSets.map(x => ({ value: x.id, label: `${x.name} - ${x.status}` }))} /><Select label="Status" value={instrument.status} onChange={v => setInstrument({ ...instrument, status: v })} options={opts(['ASSIGNED', 'IN_USE', 'UNDER_CLEANING', 'STERILIZED'])} /><button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Assign Instrument</button></form><Mini items={details?.instrumentAssignments || []} render={x => `${nameById(x.instrumentSetId, lookups.instrumentSets)} - ${x.status}`} /></Panel></div>;
    }

    function StaffTab() {
        return <div className="grid grid-cols-1 xl:grid-cols-[380px_1fr] gap-6"><Panel title="Assign OT Staff"><form onSubmit={e => { e.preventDefault(); withSelected(id => otService.assignStaff(id, toNumbers(staff, ['doctorId'])), 'Staff allocated.', 'Unable to allocate staff'); }} className="space-y-4"><Select label="Role" value={staff.role} onChange={v => setStaff({ ...staff, role: v })} options={opts(['Surgeon', 'Assistant Surgeon', 'Scrub Nurse', 'Circulating Nurse', 'Anaesthetist', 'Technician', 'Ward Boy'])} /><Input label="Staff Name" value={staff.staffName} onChange={v => setStaff({ ...staff, staffName: v })} /><Select label="Linked Doctor" value={staff.doctorId} onChange={v => setStaff({ ...staff, doctorId: v })} options={doctors} /><button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Assign Staff</button></form></Panel><Panel title="Staff On Duty"><Mini items={details?.staffAssignments || []} render={x => `${x.role}: ${x.staffName}`} /></Panel></div>;
    }

    function AnesthesiaTab() {
        return <FormPanel title="Anesthesia Chart" onSubmit={() => withSelected(id => otService.addAnesthesia(id, toNumbers(anesthesia, ['pulse', 'spo2', 'temperature', 'respiration'])), 'Anesthesia record saved.', 'Unable to save anesthesia record')} button="Save 5-Minute Vitals"><Select label="Type" value={anesthesia.anesthesiaType} onChange={v => setAnesthesia({ ...anesthesia, anesthesiaType: v })} options={opts(['General', 'Spinal', 'Local', 'Epidural'])} /><Textarea label="Drug Chart" value={anesthesia.drugChart} onChange={v => setAnesthesia({ ...anesthesia, drugChart: v })} /><div className="grid grid-cols-2 md:grid-cols-5 gap-3"><Input label="BP" value={anesthesia.bp} onChange={v => setAnesthesia({ ...anesthesia, bp: v })} /><Input label="Pulse" type="number" value={anesthesia.pulse} onChange={v => setAnesthesia({ ...anesthesia, pulse: v })} /><Input label="SpO2" type="number" value={anesthesia.spo2} onChange={v => setAnesthesia({ ...anesthesia, spo2: v })} /><Input label="Temperature" type="number" value={anesthesia.temperature} onChange={v => setAnesthesia({ ...anesthesia, temperature: v })} /><Input label="Respiration" type="number" value={anesthesia.respiration} onChange={v => setAnesthesia({ ...anesthesia, respiration: v })} /></div><Textarea label="Complications" value={anesthesia.complications} onChange={v => setAnesthesia({ ...anesthesia, complications: v })} /><Mini items={details?.anesthesiaRecords || []} render={x => `${x.anesthesiaType} - BP ${x.bp || '-'} Pulse ${x.pulse || '-'}`} /></FormPanel>;
    }

    function NotesTab() {
        return <Panel title="Intra-Operative Notes and Post-Op Orders"><form onSubmit={e => { e.preventDefault(); withSelected(id => otService.saveNotes(id, notes), 'Notes and post-op orders saved.', 'Unable to save notes'); }} className="grid grid-cols-1 lg:grid-cols-2 gap-4"><Textarea rows={12} label="Diagnosis, procedure done, findings, blood loss, specimen, complications, implants, drain, closure, duration" value={notes.intraOpNotes} onChange={v => setNotes({ ...notes, intraOpNotes: v })} /><Textarea rows={12} label="Antibiotics, painkillers, IV fluids, diet, drain care, physiotherapy, lab orders, follow-up" value={notes.postOpOrders} onChange={v => setNotes({ ...notes, postOpOrders: v })} /><button className="lg:col-span-2 h-10 bg-gray-900 text-white text-sm font-semibold">Save Notes and Orders</button></form></Panel>;
    }

    function ImplantTab() {
        return <FormPanel title="Implant Tracking" onSubmit={() => withSelected(id => otService.addImplant(id, toNumbers(implant, ['charge'])), 'Implant tracked.', 'Unable to save implant')} button="Save Implant"><div className="grid grid-cols-1 md:grid-cols-3 gap-3"><Input label="Implant" value={implant.implantName} onChange={v => setImplant({ ...implant, implantName: v })} /><Input label="Brand" value={implant.brand} onChange={v => setImplant({ ...implant, brand: v })} /><Input label="Lot Number" value={implant.lotNumber} onChange={v => setImplant({ ...implant, lotNumber: v })} /><Input label="Batch Number" value={implant.batchNumber} onChange={v => setImplant({ ...implant, batchNumber: v })} /><Input label="Serial Number" value={implant.serialNumber} onChange={v => setImplant({ ...implant, serialNumber: v })} /><Input label="Manufacturer" value={implant.manufacturer} onChange={v => setImplant({ ...implant, manufacturer: v })} /><Input label="Expiry" type="date" value={implant.expiryDate} onChange={v => setImplant({ ...implant, expiryDate: v })} /><Input label="Charge" type="number" value={implant.charge} onChange={v => setImplant({ ...implant, charge: v })} /></div><Mini items={details?.implants || []} render={x => `${x.implantName} ${x.brand || ''} ${x.serialNumber || ''}`} /></FormPanel>;
    }

    function RecoveryTab() {
        return <FormPanel title="PACU Recovery Room" onSubmit={() => withSelected(id => otService.addRecovery(id, toNumbers(recovery, ['pulse', 'spo2', 'painScore'])), 'Recovery record saved.', 'Unable to save recovery')} button="Save Recovery"><div className="grid grid-cols-2 md:grid-cols-4 gap-3"><Input label="BP" value={recovery.bp} onChange={v => setRecovery({ ...recovery, bp: v })} /><Input label="Pulse" type="number" value={recovery.pulse} onChange={v => setRecovery({ ...recovery, pulse: v })} /><Input label="SpO2" type="number" value={recovery.spo2} onChange={v => setRecovery({ ...recovery, spo2: v })} /><Input label="Pain Score" type="number" value={recovery.painScore} onChange={v => setRecovery({ ...recovery, painScore: v })} /></div><Input label="Consciousness" value={recovery.consciousness} onChange={v => setRecovery({ ...recovery, consciousness: v })} /><Check label="Nausea" checked={!!recovery.nausea} onChange={v => setRecovery({ ...recovery, nausea: v })} /><div className="grid grid-cols-2 gap-3"><Input label="Drain Output" value={recovery.drainOutput} onChange={v => setRecovery({ ...recovery, drainOutput: v })} /><Input label="Urine Output" value={recovery.urineOutput} onChange={v => setRecovery({ ...recovery, urineOutput: v })} /></div><Select label="Shift To" value={recovery.disposition} onChange={v => setRecovery({ ...recovery, disposition: v })} options={opts(['Ward', 'ICU', 'Home'])} /><Textarea label="Notes" value={recovery.notes} onChange={v => setRecovery({ ...recovery, notes: v })} /><Mini items={details?.recoveryRecords || []} render={x => `${x.disposition || 'PACU'} - BP ${x.bp || '-'} Pain ${x.painScore ?? '-'}`} /></FormPanel>;
    }

    function BillingTab() {
        return <div className="grid grid-cols-1 xl:grid-cols-3 gap-6"><Panel title="Consumables"><form onSubmit={e => { e.preventDefault(); withSelected(id => otService.addConsumable(id, toNumbers(consumable, ['inventoryItemId', 'quantity', 'unitCharge'])), 'Consumable saved and inventory deducted.', 'Unable to save consumable'); }} className="space-y-4"><Select label="Inventory Item" value={consumable.inventoryItemId} onChange={v => { const item = lookups.inventory.find(x => String(x.id) === String(v)); setConsumable({ ...consumable, inventoryItemId: v, itemName: item?.name || '', unitCharge: item?.unitPrice || 0 }); }} options={lookups.inventory.map(x => ({ value: x.id, label: `${x.name} - Stock ${x.stockQuantity}` }))} /><Input label="Item Name" value={consumable.itemName} onChange={v => setConsumable({ ...consumable, itemName: v })} /><div className="grid grid-cols-2 gap-3"><Input label="Quantity" type="number" value={consumable.quantity} onChange={v => setConsumable({ ...consumable, quantity: v })} /><Input label="Unit Charge" type="number" value={consumable.unitCharge} onChange={v => setConsumable({ ...consumable, unitCharge: v })} /></div><button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Deduct and Add</button></form></Panel><Panel title="OT Charges"><form onSubmit={e => { e.preventDefault(); withSelected(id => otService.addCharge(id, toNumbers(charge, ['amount'])), 'OT charge added.', 'Unable to add charge'); }} className="space-y-4"><Select label="Charge Type" value={charge.chargeType} onChange={v => setCharge({ ...charge, chargeType: v })} options={opts(['OT Charges', 'Surgeon Fee', 'Assistant Fee', 'Anaesthesia Fee', 'Equipment Charges', 'Implants', 'Consumables', 'Medicines', 'Nursing Charges', 'Recovery Charges', 'Cleaning Charges', 'GST'])} /><Input label="Amount" type="number" value={charge.amount} onChange={v => setCharge({ ...charge, amount: v })} /><Textarea label="Notes" value={charge.notes} onChange={v => setCharge({ ...charge, notes: v })} /><button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Add Charge</button></form></Panel><Panel title="Bill Summary"><Mini items={details?.charges || []} render={x => `${x.chargeType}: ${money(x.amount)}`} /><div className="border-t border-gray-200 mt-4 pt-4 flex justify-between font-bold"><span>Total</span><span>{money((details?.charges || []).reduce((s, x) => s + Number(x.amount || 0), 0))}</span></div><button onClick={() => withSelected(id => otService.complete(id), 'Surgery completed and bill generated.', 'Complete WHO checklist before billing.')} className="mt-4 w-full h-10 bg-gray-900 text-white text-sm font-semibold">Complete Surgery and Generate Bill</button></Panel></div>;
    }

    function AdminTab() {
        return <div className="grid grid-cols-1 xl:grid-cols-4 gap-6"><MasterPanel title="OT Rooms" onSubmit={(e) => saveMaster(e, otService.saveRoom, toNumbers(room, ['tableCount']), () => setRoom({ name: '', roomCode: '', tableCount: 1, status: 'AVAILABLE' }), 'OT room saved.', 'Unable to save room')}><Input label="Room Name" value={room.name} onChange={v => setRoom({ ...room, name: v })} /><Input label="Room Code" value={room.roomCode} onChange={v => setRoom({ ...room, roomCode: v })} /><Input label="Tables" type="number" value={room.tableCount} onChange={v => setRoom({ ...room, tableCount: v })} /><Select label="Status" value={room.status} onChange={v => setRoom({ ...room, status: v })} options={opts(['AVAILABLE', 'IN_USE', 'CLEANING', 'MAINTENANCE'])} /></MasterPanel><MasterPanel title="Surgery Types" onSubmit={(e) => saveMaster(e, otService.saveSurgery, toNumbers(surgery, ['defaultDurationMinutes', 'defaultCharge']), () => setSurgery({ name: '', specialty: '', procedureCode: '', defaultDurationMinutes: 60, defaultCharge: 0, active: true }), 'Surgery type saved.', 'Unable to save surgery type')}><Input label="Name" value={surgery.name} onChange={v => setSurgery({ ...surgery, name: v })} /><Input label="Specialty" value={surgery.specialty} onChange={v => setSurgery({ ...surgery, specialty: v })} /><Input label="Procedure Code" value={surgery.procedureCode} onChange={v => setSurgery({ ...surgery, procedureCode: v })} /><Input label="Duration" type="number" value={surgery.defaultDurationMinutes} onChange={v => setSurgery({ ...surgery, defaultDurationMinutes: v })} /><Input label="Charge" type="number" value={surgery.defaultCharge} onChange={v => setSurgery({ ...surgery, defaultCharge: v })} /></MasterPanel><MasterPanel title="Equipment" onSubmit={(e) => saveMaster(e, otService.saveEquipment, equipMaster, () => setEquipMaster({ name: '', category: '', serialNumber: '', status: 'AVAILABLE', notes: '' }), 'Equipment saved.', 'Unable to save equipment')}><Input label="Name" value={equipMaster.name} onChange={v => setEquipMaster({ ...equipMaster, name: v })} /><Input label="Category" value={equipMaster.category} onChange={v => setEquipMaster({ ...equipMaster, category: v })} /><Input label="Serial Number" value={equipMaster.serialNumber} onChange={v => setEquipMaster({ ...equipMaster, serialNumber: v })} /><Select label="Status" value={equipMaster.status} onChange={v => setEquipMaster({ ...equipMaster, status: v })} options={opts(['AVAILABLE', 'IN_USE', 'UNDER_MAINTENANCE'])} /></MasterPanel><MasterPanel title="Instrument Sets" onSubmit={(e) => saveMaster(e, otService.saveInstrument, instMaster, () => setInstMaster({ name: '', setType: '', status: 'STERILIZED', contents: '' }), 'Instrument set saved.', 'Unable to save instrument set')}><Input label="Name" value={instMaster.name} onChange={v => setInstMaster({ ...instMaster, name: v })} /><Input label="Set Type" value={instMaster.setType} onChange={v => setInstMaster({ ...instMaster, setType: v })} /><Select label="Status" value={instMaster.status} onChange={v => setInstMaster({ ...instMaster, status: v })} options={opts(['AVAILABLE', 'STERILIZED', 'IN_USE', 'UNDER_CLEANING'])} /><Textarea label="Contents" value={instMaster.contents} onChange={v => setInstMaster({ ...instMaster, contents: v })} /></MasterPanel></div>;
    }
};

const Calendar = ({ bookings, rooms, onSelect }) => <Panel title="OT Calendar"><div className="grid grid-cols-[140px_1fr] border border-gray-200"><div className="bg-gray-50 p-3 text-xs font-bold uppercase text-gray-500">Room</div><div className="bg-gray-50 grid grid-cols-4 md:grid-cols-8 divide-x divide-gray-200">{['8 AM', '9 AM', '10 AM', '11 AM', '12 PM', '1 PM', '2 PM', '3 PM'].map(x => <div key={x} className="p-3 text-xs font-bold text-gray-500">{x}</div>)}</div>{(rooms.length ? rooms : [{ id: 'unallocated', name: 'Unallocated' }]).map(room => { const rows = bookings.filter(x => String(x.otRoomId || 'unallocated') === String(room.id)); return <React.Fragment key={room.id}><div className="border-t border-r border-gray-200 p-4 font-semibold">{room.name}</div><div className="border-t border-gray-200 p-3 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">{rows.map(x => <button key={x.id} onClick={() => onSelect(x.id)} className={`text-left border p-3 ${statusClass(x)}`}><p className="text-sm font-semibold">{time(x.scheduledStart)} {x.procedureName}</p><p className="text-xs mt-1">{x.priority} - {x.status}</p></button>)}{!rows.length && <span className="text-sm text-gray-400">No surgery booked</span>}</div></React.Fragment>; })}</div></Panel>;
const Tracking = ({ selected, details, onStatus }) => <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-6"><Panel title="Live Surgery Status"><div className="grid grid-cols-1 md:grid-cols-4 gap-3">{statusSteps.map(step => { const done = (details?.statusLog || []).some(x => x.status === step) || selected?.status === step; return <button key={step} onClick={() => onStatus(step)} className={`border p-4 text-left ${done ? 'bg-blue-50 border-blue-200 text-blue-900' : 'bg-white border-gray-200 text-gray-700'}`}><p className="text-sm font-semibold">{label(step)}</p><p className="text-xs mt-1">{done ? 'Updated' : 'Click to update'}</p></button>; })}</div></Panel><Panel title="Timeline Log"><Mini items={details?.statusLog || []} render={x => `${label(x.status)} - ${date(x.eventTime)}`} /></Panel></div>;
const Reports = ({ reports, bookings }) => <div className="space-y-6"><div className="grid grid-cols-1 md:grid-cols-4 gap-4"><Card label="Emergency Cases" value={reports?.emergencySurgeries ?? 0} /><Card label="Cancelled Cases" value={reports?.cancelledSurgeries ?? 0} /><Card label="Completed Cases" value={reports?.completedSurgeries ?? 0} /><Card label="Revenue" value={money(reports?.otRevenue || 0)} /></div><BookingsTable bookings={reports?.dailyOtRegister || bookings} onOpen={() => {}} onStart={() => {}} onComplete={() => {}} /></div>;
const Checklist = ({ title, status, onSave, children }) => <Panel title={title} action={<StatusBadge status={status || 'PENDING'} />}><div className="space-y-5">{children}<button type="button" onClick={onSave} className="h-10 px-4 bg-gray-900 text-white text-sm font-semibold">Save Checklist</button></div></Panel>;
const FormPanel = ({ title, onSubmit, button, children }) => <Panel title={title}><form onSubmit={e => { e.preventDefault(); onSubmit(); }} className="space-y-4">{children}<button className="h-10 px-4 bg-gray-900 text-white text-sm font-semibold">{button}</button></form></Panel>;
const MasterPanel = ({ title, onSubmit, children }) => <Panel title={title}><form onSubmit={onSubmit} className="space-y-3">{children}<button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Save</button></form></Panel>;
const Panel = ({ title, action, children }) => <section className="bg-white border border-gray-200 p-5"><div className="flex items-start justify-between gap-4 mb-4"><h2 className="font-semibold text-gray-900">{title}</h2>{action}</div>{children}</section>;
const Card = ({ label, value }) => <div className="bg-white border border-gray-200 p-4"><p className="text-xs font-semibold uppercase text-gray-500">{label}</p><p className="text-2xl font-bold text-gray-900 mt-2">{value}</p></div>;
const Info = ({ label, value }) => <div className="border border-gray-200 bg-gray-50 p-3"><p className="text-xs font-semibold uppercase text-gray-500">{label}</p><p className="text-sm font-medium text-gray-900 mt-1 break-words">{value || '-'}</p></div>;
const Summary = ({ selected, details, rooms }) => selected ? <div className="space-y-3"><Info label="Procedure" value={selected.procedureName} /><Info label="Room" value={rooms.find(x => String(x.id) === String(selected.otRoomId))?.name || 'Unallocated'} /><Info label="Schedule" value={`${date(selected.scheduledStart)} to ${time(selected.scheduledEnd)}`} /><Info label="Clearance" value={selected.clearanceStatus} /><Info label="WHO Status" value={details?.whoChecklist?.status || 'Pending'} /><StatusBadge status={selected.status || 'WAITING'} /></div> : <p className="text-sm text-gray-500">No surgery selected.</p>;
const BookingsTable = ({ bookings, onOpen, onStart, onComplete }) => <div className="bg-white border border-gray-200 overflow-hidden"><div className="overflow-x-auto"><table className="min-w-full divide-y divide-gray-200"><thead className="bg-gray-50"><tr>{['Procedure', 'Patient', 'Schedule', 'Priority', 'Status', 'Clearance', 'Actions'].map(x => <th key={x} className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase">{x}</th>)}</tr></thead><tbody className="divide-y divide-gray-100">{bookings.map(x => <tr key={x.id}><td className="px-4 py-3 text-sm font-semibold text-gray-900">{x.procedureName}</td><td className="px-4 py-3 text-sm text-gray-600">{x.patientUhid || x.patientId}</td><td className="px-4 py-3 text-sm text-gray-600">{date(x.scheduledStart)}</td><td className="px-4 py-3 text-sm"><span className={`border px-2 py-1 text-xs ${statusClass(x)}`}>{x.priority}</span></td><td className="px-4 py-3 text-sm"><StatusBadge status={x.status || 'WAITING'} /></td><td className="px-4 py-3 text-sm text-gray-600">{x.clearanceStatus}</td><td className="px-4 py-3 text-sm"><div className="flex flex-wrap gap-2"><button type="button" onClick={() => onOpen(x.id)} className="px-2 py-1 text-xs border border-gray-300 hover:bg-gray-50">Open</button><button type="button" onClick={onStart} className="px-2 py-1 text-xs border border-blue-300 text-blue-700 hover:bg-blue-50">Start</button><button type="button" onClick={onComplete} className="px-2 py-1 text-xs border border-gray-900 bg-gray-900 text-white hover:bg-gray-700">Complete + Bill</button></div></td></tr>)}{!bookings.length && <tr><td colSpan="7" className="px-4 py-8 text-center text-sm text-gray-500">No surgeries scheduled.</td></tr>}</tbody></table></div></div>;
const Mini = ({ items, render }) => <div className="mt-4 space-y-2">{items.map((x, i) => <div key={x.id || i} className="border border-gray-200 p-3 text-sm text-gray-700">{render(x)}</div>)}{!items.length && <p className="text-sm text-gray-500">No records yet.</p>}</div>;
const Input = ({ label, value, onChange, type = 'text', readOnly = false }) => <label className="block"><span className="text-sm font-medium text-gray-700">{label}</span><input type={type} value={value || ''} readOnly={readOnly} onChange={e => onChange?.(e.target.value)} className={`mt-1 w-full h-10 border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900 ${readOnly ? 'bg-gray-50 text-gray-500' : 'bg-white'}`} /></label>;
const Textarea = ({ label, value, onChange, rows = 4 }) => <label className="block"><span className="text-sm font-medium text-gray-700">{label}</span><textarea value={value || ''} rows={rows} onChange={e => onChange(e.target.value)} className="mt-1 w-full border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900" /></label>;
const Select = ({ label, value, onChange, options }) => <label className="block"><span className="text-sm font-medium text-gray-700">{label}</span><select value={value || ''} onChange={e => onChange(e.target.value)} className="mt-1 w-full h-10 border border-gray-300 bg-white px-3 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"><option value="">Select</option>{options.map(x => <option key={x.value} value={x.value}>{x.label}</option>)}</select></label>;
const Check = ({ label, checked, onChange }) => <label className="flex items-center gap-2 border border-gray-200 p-3 text-sm text-gray-700"><input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} className="h-4 w-4 accent-gray-900" /><span>{label}</span></label>;
const Alert = ({ tone, children }) => <div className={`mb-4 border p-3 text-sm ${tone === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : tone === 'danger' ? 'border-red-200 bg-red-50 text-red-800' : 'border-gray-200 bg-white text-gray-700'}`}>{children}</div>;
const opts = values => values.map(value => ({ value, label: label(value) }));
const label = value => String(value || '').replaceAll('_', ' ').replace(/\b\w/g, char => char.toUpperCase());
const date = value => value ? new Date(value).toLocaleString() : '-';
const time = value => value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '-';
const money = value => `Rs. ${Number(value || 0).toFixed(2)}`;
const apiError = (err, fallback) => err.response?.data?.message || err.response?.data || fallback;
const nameById = (id, rows) => rows.find(x => String(x.id) === String(id))?.name || `#${id}`;
const toNumbers = (source, keys) => { const data = { ...source }; keys.forEach(key => { data[key] = data[key] === '' || data[key] === null || data[key] === undefined ? null : Number(data[key]); }); return data; };
const statusClass = booking => { const status = String(booking?.priority === 'EMERGENCY' ? 'EMERGENCY' : booking?.status || '').toUpperCase(); if (status.includes('READY') || status.includes('CONFIRMED')) return 'bg-emerald-100 text-emerald-800 border-emerald-200'; if (status.includes('ONGOING') || status.includes('INCISION') || status.includes('ANESTHESIA')) return 'bg-blue-100 text-blue-800 border-blue-200'; if (status.includes('EMERGENCY')) return 'bg-red-100 text-red-800 border-red-200'; if (status.includes('CANCELLED')) return 'bg-gray-100 text-gray-700 border-gray-200'; if (status.includes('COMPLETED')) return 'bg-slate-100 text-slate-800 border-slate-200'; return 'bg-amber-100 text-amber-800 border-amber-200'; };

export default OtDashboardPro;
