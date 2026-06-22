import React, { useEffect, useMemo, useState } from 'react';
import { CalendarDaysIcon, ClipboardDocumentCheckIcon, Cog6ToothIcon, HeartIcon, PlusIcon, WrenchScrewdriverIcon } from '@heroicons/react/24/outline';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../../components/Sidebar';
import Navbar from '../../components/Navbar';
import StatusBadge from '../../components/StatusBadge';
import authService from '../../services/authService';
import otService from '../../services/otService';

const tabs = [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'booking', label: 'Surgery Booking' },
    { id: 'calendar', label: 'OT Calendar' },
    { id: 'rooms', label: 'Room Allocation' },
    { id: 'staff', label: 'Staff Allocation' },
    { id: 'preop', label: 'Pre-Operative Checklist' },
    { id: 'who', label: 'WHO Checklist' },
    { id: 'tracking', label: 'Live Surgery Tracking' },
    { id: 'recovery', label: 'Recovery Room' },
    { id: 'implants', label: 'Implant Tracking' },
    { id: 'equipment', label: 'Equipment Tracking' },
    { id: 'reports', label: 'Reports' },
    { id: 'settings', label: 'Settings' },
];

const emptyBooking = {
    patientId: '',
    patientUhid: '',
    ipdNumber: '',
    surgeonId: '',
    assistantSurgeonId: '',
    otRoomId: '',
    specialty: '',
    procedureName: '',
    diagnosis: '',
    expectedDurationMinutes: 60,
    priority: 'ELECTIVE',
    surgeryType: 'ELECTIVE',
    scheduledStart: '',
    scheduledEnd: '',
    remarks: ''
};

const cardMeta = [
    ['todaysSurgeries', "Today's Surgeries"],
    ['ongoingSurgeries', 'Ongoing Surgeries'],
    ['completedSurgeries', 'Completed Surgeries'],
    ['emergencySurgeries', 'Emergency Surgeries'],
    ['availableOtRooms', 'Available OT Rooms'],
    ['otUtilization', 'OT Utilization %'],
    ['pendingClearances', 'Pending Clearances'],
    ['otStaffOnDuty', 'OT Staff On Duty'],
    ['equipmentAvailable', 'Equipment Available'],
    ['sterilizedInstrumentSets', 'Sterilized Instrument Sets'],
];

const colorForStatus = (status) => {
    const value = String(status || '').toUpperCase();
    if (value.includes('CONFIRMED') || value.includes('READY')) return 'bg-emerald-100 text-emerald-800 border-emerald-200';
    if (value.includes('ONGOING')) return 'bg-blue-100 text-blue-800 border-blue-200';
    if (value.includes('EMERGENCY')) return 'bg-red-100 text-red-800 border-red-200';
    if (value.includes('CANCELLED')) return 'bg-gray-100 text-gray-700 border-gray-200';
    if (value.includes('COMPLETED')) return 'bg-slate-100 text-slate-800 border-slate-200';
    return 'bg-amber-100 text-amber-800 border-amber-200';
};

const OtDashboard = () => {
    const navigate = useNavigate();
    const user = authService.getCurrentUser();
    const [activeTab, setActiveTab] = useState('dashboard');
    const [stats, setStats] = useState({});
    const [bookings, setBookings] = useState([]);
    const [rooms, setRooms] = useState([]);
    const [equipment, setEquipment] = useState([]);
    const [instruments, setInstruments] = useState([]);
    const [patients, setPatients] = useState([]);
    const [doctors, setDoctors] = useState([]);
    const [reports, setReports] = useState(null);
    const [form, setForm] = useState(emptyBooking);
    const [roomForm, setRoomForm] = useState({ name: '', roomCode: '', tableCount: 1 });
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const selectedBooking = bookings[0];

    const loadData = async () => {
        setLoading(true);
        setError('');
        try {
            const [dashboardData, bookingData, lookupData] = await Promise.all([
                otService.dashboard(),
                otService.bookings(),
                otService.lookups(),
            ]);
            setStats(dashboardData || {});
            setBookings(Array.isArray(bookingData) ? bookingData : []);
            setRooms(Array.isArray(lookupData?.rooms) ? lookupData.rooms : []);
            setEquipment(Array.isArray(lookupData?.equipment) ? lookupData.equipment : []);
            setInstruments(Array.isArray(lookupData?.instrumentSets) ? lookupData.instrumentSets : []);
            setPatients(Array.isArray(lookupData?.patients) ? lookupData.patients : []);
            setDoctors(Array.isArray(lookupData?.doctors) ? lookupData.doctors : []);
        } catch (err) {
            setError(err.response?.data?.message || err.response?.data || 'Unable to load OT data');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    useEffect(() => {
        if (activeTab === 'reports' && !reports) {
            otService.reports().then(setReports).catch(err => setError(err.response?.data?.message || 'Unable to load reports'));
        }
    }, [activeTab, reports]);

    const calendarRows = useMemo(() => {
        return [...bookings].sort((a, b) => new Date(a.scheduledStart) - new Date(b.scheduledStart));
    }, [bookings]);

    const submitBooking = async (e) => {
        e.preventDefault();
        setError('');
        setMessage('');
        if (!form.patientId || !form.procedureName || !form.scheduledStart || !form.scheduledEnd) {
            setError('Patient, procedure, scheduled start, and scheduled end are required.');
            return;
        }
        try {
            await otService.createBooking({
                ...form,
                patientId: Number(form.patientId),
                surgeonId: form.surgeonId ? Number(form.surgeonId) : null,
                assistantSurgeonId: form.assistantSurgeonId ? Number(form.assistantSurgeonId) : null,
                otRoomId: form.otRoomId ? Number(form.otRoomId) : null,
                expectedDurationMinutes: Number(form.expectedDurationMinutes || 60),
            });
            setForm(emptyBooking);
            setMessage('Surgery booking saved.');
            setActiveTab('dashboard');
            loadData();
        } catch (err) {
            setError(err.response?.data?.message || err.response?.data || 'Unable to save booking');
        }
    };

    const markStatus = async (booking, status) => {
        setError('');
        setMessage('');
        try {
            await otService.updateStatus(booking.id, { status, notes: `Updated from OT dashboard` });
            setMessage(`Surgery marked ${status}.`);
            loadData();
        } catch (err) {
            setError(err.response?.data?.message || err.response?.data || 'Unable to update status');
        }
    };

    const completeBooking = async (booking) => {
        setError('');
        setMessage('');
        try {
            const bill = await otService.complete(booking.id);
            setMessage(`Surgery completed and bill ${bill?.customId || bill?.id || ''} generated.`);
            loadData();
        } catch (err) {
            setError(err.response?.data?.message || err.response?.data || 'Complete the WHO checklist before completing surgery.');
        }
    };

    const markPreOpReady = async (booking) => {
        setError('');
        setMessage('');
        try {
            await otService.updatePreOp(booking.id, {
                consentSigned: true,
                bloodAvailable: true,
                cbc: true,
                lft: true,
                kft: true,
                ptInr: true,
                ecg: true,
                chestXray: true,
                crossMatching: true,
                physicianFitness: true,
                pacClearance: true,
                notes: 'Marked ready from OT dashboard'
            });
            setMessage('Pre-operative checklist marked ready.');
            loadData();
        } catch (err) {
            setError(err.response?.data?.message || err.response?.data || 'Unable to update pre-op checklist');
        }
    };

    const markWhoDone = async (booking) => {
        setError('');
        setMessage('');
        try {
            await otService.updateWho(booking.id, {
                patientIdentity: true,
                siteMarked: true,
                consentSigned: true,
                allergiesChecked: true,
                bloodAvailable: true,
                teamIntroduction: true,
                antibioticGiven: true,
                imagingDisplayed: true,
                instrumentCount: true,
                spongeCount: true,
                finalCount: true,
                specimenLabeled: true,
                procedureConfirmed: true,
                recoveryPlan: true
            });
            setMessage('WHO checklist completed.');
            loadData();
        } catch (err) {
            setError(err.response?.data?.message || err.response?.data || 'Unable to update WHO checklist');
        }
    };

    const saveRoom = async (e) => {
        e.preventDefault();
        setError('');
        setMessage('');
        if (!roomForm.name.trim()) {
            setError('OT room name is required.');
            return;
        }
        try {
            await otService.saveRoom({ ...roomForm, tableCount: Number(roomForm.tableCount || 1), status: 'AVAILABLE' });
            setRoomForm({ name: '', roomCode: '', tableCount: 1 });
            setMessage('OT room saved.');
            loadData();
        } catch (err) {
            setError(err.response?.data?.message || err.response?.data || 'Unable to save OT room');
        }
    };

    const logout = () => {
        authService.logout();
        navigate('/login', { replace: true });
    };

    const openProfile = () => {
        setMessage('Profile settings are available from the main hospital dashboard.');
    };

    const patientOptions = patients.map(patient => ({
        value: patient.id,
        label: `${patient.customId || patient.publicId || patient.id} - ${patient.name} (${patient.age}/${patient.gender})`
    }));

    const doctorOptions = doctors.map(doctor => ({
        value: doctor.id,
        label: `${doctor.name} - ${doctor.specialization || 'Doctor'}`
    }));

    const applyPatientDefaults = (patientId) => {
        const patient = patients.find(p => String(p.id) === String(patientId));
        setForm({
            ...form,
            patientId,
            patientUhid: patient?.customId || patient?.publicId || ''
        });
    };

    const renderDashboard = () => (
        <div className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4">
                {cardMeta.map(([key, label]) => (
                    <div key={key} className="bg-white border border-gray-200 p-4">
                        <p className="text-xs font-semibold uppercase text-gray-500">{label}</p>
                        <p className="text-2xl font-bold text-gray-900 mt-2">{stats[key] ?? 0}</p>
                    </div>
                ))}
            </div>
            <BookingsTable bookings={bookings.slice(0, 8)} onStatus={markStatus} onComplete={completeBooking} onPreOpReady={markPreOpReady} onWhoDone={markWhoDone} />
        </div>
    );

    const renderBooking = () => (
        <div className="grid grid-cols-1 xl:grid-cols-[420px_1fr] gap-6">
            <form onSubmit={submitBooking} className="bg-white border border-gray-200 p-5 space-y-4">
                <div className="flex items-center gap-2">
                    <PlusIcon className="w-5 h-5 text-gray-700" />
                    <h2 className="font-semibold text-gray-900">Surgery Booking</h2>
                </div>
                <Select label="Patient" value={form.patientId} onChange={applyPatientDefaults} options={patientOptions} />
                <Input label="UHID" value={form.patientUhid} onChange={v => setForm({ ...form, patientUhid: v })} />
                <Input label="IPD Number" value={form.ipdNumber} onChange={v => setForm({ ...form, ipdNumber: v })} />
                <Select label="Surgeon" value={form.surgeonId} onChange={v => setForm({ ...form, surgeonId: v })} options={doctorOptions} />
                <Select label="Assistant Surgeon" value={form.assistantSurgeonId} onChange={v => setForm({ ...form, assistantSurgeonId: v })} options={doctorOptions} />
                <Select label="OT Room" value={form.otRoomId} onChange={v => setForm({ ...form, otRoomId: v })} options={rooms.map(r => ({ value: r.id, label: r.name }))} />
                <Input label="Specialty" value={form.specialty} onChange={v => setForm({ ...form, specialty: v })} />
                <Input label="Procedure" value={form.procedureName} onChange={v => setForm({ ...form, procedureName: v })} />
                <Input label="Diagnosis" value={form.diagnosis} onChange={v => setForm({ ...form, diagnosis: v })} />
                <Input label="Expected Duration" type="number" value={form.expectedDurationMinutes} onChange={v => setForm({ ...form, expectedDurationMinutes: v })} />
                <Select label="Priority" value={form.priority} onChange={v => setForm({ ...form, priority: v })} options={[{ value: 'ELECTIVE', label: 'Elective' }, { value: 'EMERGENCY', label: 'Emergency' }]} />
                <Input label="Scheduled Start" type="datetime-local" value={form.scheduledStart} onChange={v => setForm({ ...form, scheduledStart: v })} />
                <Input label="Scheduled End" type="datetime-local" value={form.scheduledEnd} onChange={v => setForm({ ...form, scheduledEnd: v })} />
                <button className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Save Booking</button>
            </form>
            <BookingsTable bookings={bookings} onStatus={markStatus} onComplete={completeBooking} onPreOpReady={markPreOpReady} onWhoDone={markWhoDone} />
        </div>
    );

    const renderCalendar = () => (
        <div className="bg-white border border-gray-200 p-5">
            <div className="flex items-center gap-2 mb-4">
                <CalendarDaysIcon className="w-5 h-5" />
                <h2 className="font-semibold text-gray-900">OT Calendar</h2>
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                {calendarRows.map(booking => (
                    <div key={booking.id} className={`border p-4 ${colorForStatus(booking.priority === 'EMERGENCY' ? 'EMERGENCY' : booking.status)}`}>
                        <p className="font-semibold">{booking.procedureName}</p>
                        <p className="text-sm">{formatDate(booking.scheduledStart)} - {formatTime(booking.scheduledEnd)}</p>
                        <p className="text-sm">Room: {rooms.find(r => r.id === booking.otRoomId)?.name || 'Unallocated'}</p>
                    </div>
                ))}
            </div>
        </div>
    );

    const renderResourceList = (title, items, icon) => (
        <div className="bg-white border border-gray-200 p-5">
            <div className="flex items-center gap-2 mb-4">
                {icon}
                <h2 className="font-semibold text-gray-900">{title}</h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
                {items.map(item => (
                    <div key={item.id} className="border border-gray-200 p-4">
                        <p className="font-semibold text-gray-900">{item.name}</p>
                        <p className="text-sm text-gray-500">{item.status || item.setType || item.category || 'Active'}</p>
                    </div>
                ))}
                {items.length === 0 && <p className="text-sm text-gray-500">No records yet.</p>}
            </div>
        </div>
    );

    const renderRooms = () => (
        <div className="grid grid-cols-1 xl:grid-cols-[360px_1fr] gap-6">
            <form onSubmit={saveRoom} className="bg-white border border-gray-200 p-5 space-y-4">
                <div className="flex items-center gap-2">
                    <HeartIcon className="w-5 h-5" />
                    <h2 className="font-semibold text-gray-900">Add OT Room</h2>
                </div>
                <Input label="Room Name" value={roomForm.name} onChange={v => setRoomForm({ ...roomForm, name: v })} />
                <Input label="Room Code" value={roomForm.roomCode} onChange={v => setRoomForm({ ...roomForm, roomCode: v })} />
                <Input label="Tables" type="number" value={roomForm.tableCount} onChange={v => setRoomForm({ ...roomForm, tableCount: v })} />
                <button type="submit" className="w-full h-10 bg-gray-900 text-white text-sm font-semibold">Save Room</button>
            </form>
            {renderResourceList('Room Allocation', rooms, <HeartIcon className="w-5 h-5" />)}
        </div>
    );

    const renderChecklist = (type) => (
        <div className="bg-white border border-gray-200 p-5">
            <div className="flex items-center gap-2 mb-4">
                <ClipboardDocumentCheckIcon className="w-5 h-5" />
                <h2 className="font-semibold text-gray-900">{type === 'who' ? 'WHO Surgical Safety Checklist' : 'Pre-Operative Checklist'}</h2>
            </div>
            <p className="text-sm text-gray-600 mb-4">Open a booking to update checklist details through the OT API. The dashboard enforces pending clearance and WHO completion rules before completion.</p>
            <BookingsTable bookings={bookings} onStatus={markStatus} onComplete={completeBooking} onPreOpReady={markPreOpReady} onWhoDone={markWhoDone} />
        </div>
    );

    const renderReports = () => (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {[
                ['Emergency Surgeries', reports?.emergencySurgeries ?? 0],
                ['Cancelled Surgeries', reports?.cancelledSurgeries ?? 0],
                ['Completed Surgeries', reports?.completedSurgeries ?? 0],
                ['OT Revenue', reports?.otRevenue ?? 0],
            ].map(([label, value]) => (
                <div key={label} className="bg-white border border-gray-200 p-5">
                    <p className="text-xs font-semibold uppercase text-gray-500">{label}</p>
                    <p className="text-2xl font-bold text-gray-900 mt-2">{value}</p>
                </div>
            ))}
        </div>
    );

    const renderContent = () => {
        if (activeTab === 'booking') return renderBooking();
        if (activeTab === 'calendar') return renderCalendar();
        if (activeTab === 'rooms') return renderRooms();
        if (activeTab === 'equipment') return renderResourceList('Equipment Tracking', equipment, <WrenchScrewdriverIcon className="w-5 h-5" />);
        if (activeTab === 'settings') return renderResourceList('OT Settings', [...rooms, ...equipment, ...instruments], <Cog6ToothIcon className="w-5 h-5" />);
        if (activeTab === 'preop') return renderChecklist('preop');
        if (activeTab === 'who') return renderChecklist('who');
        if (activeTab === 'reports') return renderReports();
        if (['staff', 'tracking', 'recovery', 'implants'].includes(activeTab)) return <BookingsTable bookings={bookings} onStatus={markStatus} onComplete={completeBooking} onPreOpReady={markPreOpReady} onWhoDone={markWhoDone} />;
        return renderDashboard();
    };

    return (
        <div className="h-screen bg-gray-50 flex overflow-hidden">
            <Sidebar title="Operation Theatre" tabs={tabs} activeTab={activeTab} onTabChange={setActiveTab} footerTitle="OT User" footerData={user?.name || user?.email || 'OT'} />
            <div className="flex-1 flex flex-col min-w-0">
                <Navbar
                    title="Operation Theatre"
                    user={user}
                    subtitle={user?.hospitalName || 'Surgical operations'}
                    onLogout={logout}
                    onProfile={openProfile}
                    onToggleSidebar={() => setMessage('Sidebar is already visible on this screen.')}
                />
                <main className="flex-1 overflow-y-auto p-6">
                    <div className="flex items-center justify-between mb-5">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">Operation Theatre</h1>
                            <p className="text-sm text-gray-500">Surgery scheduling, safety checks, allocation, recovery, and OT billing</p>
                        </div>
                        {loading && <span className="text-sm text-gray-500">Loading...</span>}
                    </div>
                    {message && <div className="mb-4 border border-emerald-200 bg-emerald-50 text-emerald-800 p-3 text-sm">{message}</div>}
                    {error && <div className="mb-4 border border-red-200 bg-red-50 text-red-800 p-3 text-sm">{String(error)}</div>}
                    {renderContent()}
                    {selectedBooking && <p className="mt-4 text-xs text-gray-500">Latest booking: #{selectedBooking.id} {selectedBooking.procedureName}</p>}
                </main>
            </div>
        </div>
    );
};

const Input = ({ label, value, onChange, type = 'text' }) => (
    <label className="block">
        <span className="text-sm font-medium text-gray-700">{label}</span>
        <input type={type} value={value || ''} onChange={e => onChange(e.target.value)} className="mt-1 w-full h-10 border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900" />
    </label>
);

const Select = ({ label, value, onChange, options }) => (
    <label className="block">
        <span className="text-sm font-medium text-gray-700">{label}</span>
        <select value={value || ''} onChange={e => onChange(e.target.value)} className="mt-1 w-full h-10 border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900">
            <option value="">Select</option>
            {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
    </label>
);

const BookingsTable = ({ bookings, onStatus, onComplete, onPreOpReady, onWhoDone }) => (
    <div className="bg-white border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                    <tr>
                        {['Procedure', 'Patient', 'Schedule', 'Priority', 'Status', 'Clearance', 'Actions'].map(h => (
                            <th key={h} className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase">{h}</th>
                        ))}
                    </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                    {bookings.map(booking => (
                        <tr key={booking.id}>
                            <td className="px-4 py-3 text-sm font-medium text-gray-900">{booking.procedureName}</td>
                            <td className="px-4 py-3 text-sm text-gray-600">{booking.patientUhid || booking.patientId}</td>
                            <td className="px-4 py-3 text-sm text-gray-600">{formatDate(booking.scheduledStart)}</td>
                            <td className="px-4 py-3 text-sm"><span className={`border px-2 py-1 text-xs ${colorForStatus(booking.priority)}`}>{booking.priority}</span></td>
                            <td className="px-4 py-3 text-sm"><StatusBadge status={booking.status || 'WAITING'} /></td>
                            <td className="px-4 py-3 text-sm">{booking.clearanceStatus}</td>
                            <td className="px-4 py-3 text-sm">
                                <div className="flex flex-wrap gap-2">
                                    <button type="button" onClick={() => onPreOpReady(booking)} className="px-2 py-1 text-xs border border-emerald-300 text-emerald-700 hover:bg-emerald-50">Pre-op Ready</button>
                                    <button type="button" onClick={() => onWhoDone(booking)} className="px-2 py-1 text-xs border border-blue-300 text-blue-700 hover:bg-blue-50">WHO Done</button>
                                    <button type="button" onClick={() => onStatus(booking, 'ONGOING')} className="px-2 py-1 text-xs border border-gray-300 hover:bg-gray-50">Start</button>
                                    <button type="button" onClick={() => onComplete(booking)} className="px-2 py-1 text-xs border border-gray-900 bg-gray-900 text-white hover:bg-gray-700">Complete + Bill</button>
                                </div>
                            </td>
                        </tr>
                    ))}
                    {bookings.length === 0 && (
                        <tr><td colSpan="7" className="px-4 py-8 text-center text-sm text-gray-500">No surgeries scheduled.</td></tr>
                    )}
                </tbody>
            </table>
        </div>
    </div>
);

const formatDate = (value) => value ? new Date(value).toLocaleString() : '-';
const formatTime = (value) => value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '-';

export default OtDashboard;
