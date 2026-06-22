import React, { useState, useEffect, useCallback } from 'react';
import apiClient from '../../services/apiService';
import ConfirmationModal from '../../components/ConfirmationModal';
import StatusBadge from '../../components/StatusBadge';
import { useToast } from '../../context/ToastContext';

const MESSAGE_TYPES = ['APPOINTMENT_CONFIRMATION', 'APPOINTMENT_REMINDER', 'BILLING', 'CASE_PAPER', 'PRESCRIPTION', 'MEDICINE_LIST', 'BROADCAST'];
const STATUSES = ['SENT', 'FAILED', 'RETRYING', 'PERMANENTLY_FAILED'];

export default function MessagesTab({ modules }) {
    const { success, error: toastError } = useToast();
    const isCustom = modules?.includes('WHATSAPP_CUSTOM');

    // --- Broadcast state ---
    const [messageText, setMessageText] = useState('');
    const [imageUrl, setImageUrl] = useState('');
    const [patientCount, setPatientCount] = useState(null);
    const [broadcasting, setBroadcasting] = useState(false);
    const [broadcastConfirm, setBroadcastConfirm] = useState(false);

    // --- Log state ---
    const [logs, setLogs] = useState([]);
    const [logPage, setLogPage] = useState(0);
    const [logTotalPages, setLogTotalPages] = useState(1);
    const [logType, setLogType] = useState('');
    const [logStatus, setLogStatus] = useState('');
    const [logsLoading, setLogsLoading] = useState(false);
    const [failedCount, setFailedCount] = useState(0);

    // --- Config state ---
    const [config, setConfig] = useState(null);       // null = not loaded, {} = loaded (may be empty)
    const [configLoaded, setConfigLoaded] = useState(false);
    const [configForm, setConfigForm] = useState({
        accessToken: '',
        phoneNumberId: '',
        wabaId: '',
        active: true,
        sendAppointments: true,
        sendBilling: true,
        sendCasePapers: true,
        sendPrescription: true,
        sendMedicineList: true,
    });
    const [configSaving, setConfigSaving] = useState(false);
    const [testResult, setTestResult] = useState(null);  // {success, message}
    const [testing, setTesting] = useState(false);
    const [deleteConfirm, setDeleteConfirm] = useState(false);

    const loadFailedCount = useCallback(async () => {
        try {
            const r = await apiClient.get('/hospital/whatsapp/logs/failed-count');
            setFailedCount(r.data.count || 0);
        } catch { /* non-fatal */ }
    }, []);

    const loadLogs = useCallback(async (page, type, status) => {
        setLogsLoading(true);
        try {
            let url = `/hospital/whatsapp/logs?page=${page}&size=50`;
            if (type) url += `&type=${type}`;
            if (status) url += `&status=${status}`;
            const r = await apiClient.get(url);
            setLogs(r.data.content || []);
            setLogTotalPages(r.data.totalPages || 1);
        } catch { toastError('Failed to load message logs'); }
        finally { setLogsLoading(false); }
    }, [toastError]);

    const loadConfig = useCallback(async () => {
        if (!isCustom) return;
        try {
            const r = await apiClient.get('/hospital/whatsapp/config');
            if (r.status === 204 || !r.data) {
                setConfig(null);
                setConfigLoaded(true);
                return;
            }
            const d = r.data;
            setConfig(d);
            setConfigForm(prev => ({
                ...prev,
                accessToken: '',   // always blank — user must re-enter to update
                phoneNumberId: d.phoneNumberId || '',
                wabaId: d.wabaId || '',
                active: d.active ?? true,
                sendAppointments: d.sendAppointments ?? true,
                sendBilling: d.sendBilling ?? true,
                sendCasePapers: d.sendCasePapers ?? true,
                sendPrescription: d.sendPrescription ?? true,
                sendMedicineList: d.sendMedicineList ?? true,
            }));
            setConfigLoaded(true);
        } catch (e) {
            if (e.response?.status === 204) { setConfig(null); setConfigLoaded(true); }
            else toastError('Failed to load WhatsApp config');
        }
    }, [isCustom, toastError]);

    useEffect(() => {
        loadFailedCount();
        loadConfig();
    }, [loadFailedCount, loadConfig]);

    // Reload logs when filter/page changes
    useEffect(() => {
        loadLogs(logPage, logType, logStatus);
    }, [logPage, logType, logStatus, loadLogs]);

    const handleBroadcast = async () => {
        setBroadcasting(true);
        setBroadcastConfirm(false);
        try {
            const r = await apiClient.post('/hospital/whatsapp/broadcast', { messageText, imageUrl: imageUrl || null });
            setPatientCount(r.data.patientCount);
            success(`Broadcast queued for ${r.data.patientCount} patients`);
            setMessageText('');
            setImageUrl('');
        } catch { toastError('Failed to send broadcast'); }
        finally { setBroadcasting(false); }
    };

    const handleSaveConfig = async () => {
        setConfigSaving(true);
        try {
            const body = { ...configForm };
            if (!body.accessToken) delete body.accessToken;
            const r = await apiClient.post('/hospital/whatsapp/config', body);
            setConfig(r.data);
            success('WhatsApp credentials saved');
            setTestResult(null);
        } catch { toastError('Failed to save credentials'); }
        finally { setConfigSaving(false); }
    };

    const handleTestConfig = async () => {
        setTesting(true);
        setTestResult(null);
        try {
            const r = await apiClient.post('/hospital/whatsapp/config/test', {
                accessToken: configForm.accessToken,
                phoneNumberId: configForm.phoneNumberId,
            });
            setTestResult(r.data);
        } catch (e) {
            setTestResult({ success: false, message: e.response?.data?.message || e.message });
        } finally { setTesting(false); }
    };

    const handleDeleteConfig = async () => {
        setDeleteConfirm(false);
        try {
            await apiClient.delete('/hospital/whatsapp/config');
            setConfig(null);
            setConfigLoaded(false);
            setConfigForm({ accessToken: '', phoneNumberId: '', wabaId: '', active: true, sendAppointments: true, sendBilling: true, sendCasePapers: true, sendPrescription: true, sendMedicineList: true });
            success('WhatsApp config removed');
            await loadConfig();
        } catch { toastError('Failed to remove config'); }
    };

    const handleFilterChange = (setter) => (e) => {
        setter(e.target.value);
        setLogPage(0);
    };

    return (
        <div className="space-y-8 p-1">

            {/* Section 1: Broadcast */}
            <div className="bg-white border border-gray-200 rounded-xl p-6">
                <h2 className="text-lg font-bold text-gray-900 mb-4">Broadcast Message</h2>
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Message</label>
                        <textarea
                            value={messageText}
                            onChange={e => setMessageText(e.target.value.slice(0, 1024))}
                            rows={4}
                            placeholder="Type your broadcast message here…"
                            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                        />
                        <p className="text-xs text-gray-400 mt-1 text-right">{messageText.length}/1024</p>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Image URL (optional)</label>
                        <input
                            value={imageUrl}
                            onChange={e => setImageUrl(e.target.value)}
                            placeholder="https://..."
                            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                        />
                    </div>
                    <div className="flex items-center justify-between">
                        <p className="text-sm text-gray-500">
                            {patientCount !== null ? `Will be sent to ${patientCount} patients` : '—'}
                        </p>
                        <button
                            onClick={() => setBroadcastConfirm(true)}
                            disabled={!messageText.trim() || broadcasting}
                            className="px-4 py-2 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-700 disabled:opacity-50"
                        >
                            {broadcasting ? 'Sending…' : 'Broadcast Now'}
                        </button>
                    </div>
                </div>
            </div>

            {/* Section 2: Notification Log */}
            <div className="bg-white border border-gray-200 rounded-xl p-6">
                <div className="flex items-center gap-3 mb-4">
                    <h2 className="text-lg font-bold text-gray-900">Notification Log</h2>
                    {failedCount > 0 && (
                        <span className="px-2 py-0.5 text-xs font-semibold bg-red-100 text-red-700 rounded-full">
                            {failedCount} failed
                        </span>
                    )}
                </div>
                <div className="flex gap-3 mb-4">
                    <select
                        value={logType}
                        onChange={handleFilterChange(setLogType)}
                        className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                    >
                        <option value="">All Types</option>
                        {MESSAGE_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
                    </select>
                    <select
                        value={logStatus}
                        onChange={handleFilterChange(setLogStatus)}
                        className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                    >
                        <option value="">All Statuses</option>
                        {STATUSES.map(s => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
                    </select>
                </div>
                {logsLoading ? (
                    <div className="text-center py-8 text-gray-400">Loading…</div>
                ) : logs.length === 0 ? (
                    <div className="text-center py-8 text-gray-400">No messages yet</div>
                ) : (
                    <>
                        <div className="overflow-x-auto">
                            <table className="min-w-full text-sm">
                                <thead className="bg-gray-50">
                                    <tr>
                                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Phone</th>
                                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Error</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {logs.map(log => (
                                        <tr key={log.id} className="hover:bg-gray-50">
                                            <td className="px-4 py-2 font-mono text-xs">{log.patientPhone}</td>
                                            <td className="px-4 py-2 text-xs text-gray-600">{(log.messageType || '').replace(/_/g, ' ')}</td>
                                            <td className="px-4 py-2">
                                                <StatusBadge status={log.status} />
                                            </td>
                                            <td className="px-4 py-2 text-xs text-gray-500">
                                                {log.sentAt || log.createdAt
                                                    ? new Date(log.sentAt || log.createdAt).toLocaleString()
                                                    : '—'}
                                            </td>
                                            <td className="px-4 py-2 text-xs text-red-500 max-w-xs truncate"
                                                title={log.errorMessage || ''}>
                                                {log.errorMessage ? log.errorMessage.slice(0, 60) + (log.errorMessage.length > 60 ? '…' : '') : ''}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        {logTotalPages > 1 && (
                            <div className="flex items-center justify-between mt-4 text-sm text-gray-600">
                                <button
                                    onClick={() => setLogPage(p => Math.max(0, p - 1))}
                                    disabled={logPage === 0}
                                    className="px-3 py-1 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-40"
                                >
                                    Prev
                                </button>
                                <span>Page {logPage + 1} of {logTotalPages}</span>
                                <button
                                    onClick={() => setLogPage(p => Math.min(logTotalPages - 1, p + 1))}
                                    disabled={logPage >= logTotalPages - 1}
                                    className="px-3 py-1 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-40"
                                >
                                    Next
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>

            {/* Section 3: WhatsApp Settings (CUSTOM only) */}
            {isCustom && (
                <div className="bg-white border border-gray-200 rounded-xl p-6">
                    <h2 className="text-lg font-bold text-gray-900 mb-4">WhatsApp Settings</h2>
                    {!configLoaded ? (
                        <div className="text-sm text-gray-400">Loading…</div>
                    ) : (
                        <div className="space-y-4">
                            {!config && (
                                <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-700">
                                    No credentials saved yet. To use your own WhatsApp number: get credentials from Meta Business Manager → WhatsApp Manager → Phone Numbers.
                                </div>
                            )}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Access Token {config && <span className="text-xs text-gray-400">(stored as ••••••••, enter new value to update)</span>}
                                </label>
                                <input
                                    type="password"
                                    value={configForm.accessToken}
                                    onChange={e => setConfigForm(p => ({ ...p, accessToken: e.target.value }))}
                                    placeholder={config ? 'Enter new token to replace' : 'Paste your Meta access token'}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Phone Number ID</label>
                                <input
                                    value={configForm.phoneNumberId}
                                    onChange={e => setConfigForm(p => ({ ...p, phoneNumberId: e.target.value }))}
                                    placeholder="e.g. 123456789012345"
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">WABA ID (optional)</label>
                                <input
                                    value={configForm.wabaId}
                                    onChange={e => setConfigForm(p => ({ ...p, wabaId: e.target.value }))}
                                    placeholder="WhatsApp Business Account ID"
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">Send notifications for</label>
                                <div className="space-y-2">
                                    {[
                                        ['sendAppointments', 'Appointments'],
                                        ['sendBilling', 'Billing'],
                                        ['sendCasePapers', 'Case Papers'],
                                        ['sendPrescription', 'Prescriptions'],
                                        ['sendMedicineList', 'In-Clinic Medicine List'],
                                    ].map(([key, label]) => (
                                        <label key={key} className="flex items-center gap-2 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={configForm[key]}
                                                onChange={e => setConfigForm(p => ({ ...p, [key]: e.target.checked }))}
                                                className="rounded"
                                            />
                                            <span className="text-sm text-gray-700">{label}</span>
                                        </label>
                                    ))}
                                </div>
                            </div>

                            {testResult && (
                                <div className={`p-3 rounded-lg text-sm ${testResult.success ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>
                                    {testResult.success ? '✓ ' : '✕ '}{testResult.message}
                                </div>
                            )}

                            <div className="flex gap-3 flex-wrap pt-2">
                                <button
                                    onClick={handleSaveConfig}
                                    disabled={configSaving}
                                    className="px-4 py-2 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-700 disabled:opacity-50"
                                >
                                    {configSaving ? 'Saving…' : 'Save Credentials'}
                                </button>
                                <button
                                    onClick={handleTestConfig}
                                    disabled={testing || !configForm.phoneNumberId || !configForm.accessToken}
                                    className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-50 disabled:opacity-50"
                                >
                                    {testing ? 'Testing…' : 'Test Connection'}
                                </button>
                                {config && (
                                    <button
                                        onClick={() => setDeleteConfirm(true)}
                                        className="px-4 py-2 border border-red-200 text-red-600 text-sm font-medium rounded-lg hover:bg-red-50"
                                    >
                                        Remove Custom Config
                                    </button>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            )}

            {/* Confirmation Modals */}
            <ConfirmationModal
                isOpen={broadcastConfirm}
                title="Send Broadcast?"
                message="Send WhatsApp broadcast to all patients?"
                onConfirm={handleBroadcast}
                onCancel={() => setBroadcastConfirm(false)}
            />
            <ConfirmationModal
                isOpen={deleteConfirm}
                title="Remove WhatsApp Config?"
                message="This will delete your custom WhatsApp credentials. The hospital will stop sending WhatsApp messages until new credentials are configured."
                onConfirm={handleDeleteConfig}
                onCancel={() => setDeleteConfirm(false)}
            />
        </div>
    );
}
