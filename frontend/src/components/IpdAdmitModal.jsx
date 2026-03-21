import React, { useEffect, useState } from 'react';
import wardService from '../services/wardService';
import hospitalService from '../services/hospitalService';
import { useToast } from '../context/ToastContext';

const IpdAdmitModal = ({ isOpen, onClose, opd, onSuccess }) => {
    const [wards, setWards] = useState([]);
    const [selectedWard, setSelectedWard] = useState(null);
    const [beds, setBeds] = useState([]);
    const [selectedBed, setSelectedBed] = useState(null);
    const [admissionType, setAdmissionType] = useState('ELECTIVE');
    const [primaryDiagnosis, setPrimaryDiagnosis] = useState(opd?.problem || '');
    const [loading, setLoading] = useState(false);
    const { success, error: toastError } = useToast();

    useEffect(() => {
        if (!isOpen) return;
        const load = async () => {
            try {
                const w = await wardService.getWards();
                setWards(w || []);
            } catch (err) {
                console.error('Failed to load wards', err);
            }
        };
        load();
        setPrimaryDiagnosis(opd?.problem || '');
    }, [isOpen, opd?.id]);

    useEffect(() => {
        const loadBeds = async () => {
            if (!selectedWard) return;
            try {
                const b = await wardService.getAvailableBeds(selectedWard);
                setBeds(b || []);
            } catch (err) {
                console.error('Failed to load beds', err);
            }
        };
        loadBeds();
    }, [selectedWard]);

    if (!isOpen) return null;

    const handleSubmit = async () => {
        if (!opd || !selectedWard || !selectedBed) {
            toastError('Please select ward and bed');
            return;
        }
        setLoading(true);
        try {
            const payload = {
                opdId: opd.id,
                wardId: selectedWard,
                bedId: selectedBed,
                admissionType,
                primaryDiagnosis
            };
            const res = await hospitalService.createIpdAdmission(payload);
            success('Patient admitted to IPD');
            onSuccess && onSuccess(res);
            onClose();
        } catch (err) {
            console.error('IPD admit failed', err);
            toastError(err.response?.data || 'Failed to admit patient');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
            <div className="bg-white rounded-lg shadow-lg w-full max-w-xl p-6">
                <div className="flex justify-between items-center mb-4">
                    <h3 className="text-lg font-bold">Admit to IPD</h3>
                    <button onClick={onClose} className="text-gray-500 hover:text-gray-700">Close</button>
                </div>

                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Ward</label>
                        <select className="w-full border rounded px-3 py-2" value={selectedWard || ''} onChange={e => setSelectedWard(Number(e.target.value))}>
                            <option value="">Select ward</option>
                            {wards.map(w => <option key={w.wardId} value={w.wardId}>{w.wardName}</option>)}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Bed (Available)</label>
                        <select className="w-full border rounded px-3 py-2" value={selectedBed || ''} onChange={e => setSelectedBed(Number(e.target.value))}>
                            <option value="">Select bed</option>
                            {beds.map(b => <option key={b.bedId} value={b.bedId}>{b.bedCode} — {b.status}</option>)}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Admission Type</label>
                        <select className="w-full border rounded px-3 py-2" value={admissionType} onChange={e => setAdmissionType(e.target.value)}>
                            <option value="ELECTIVE">Elective</option>
                            <option value="EMERGENCY">Emergency</option>
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Primary Diagnosis</label>
                        <textarea rows={3} value={primaryDiagnosis} onChange={e => setPrimaryDiagnosis(e.target.value)} className="w-full border rounded px-3 py-2" />
                    </div>

                    <div className="flex justify-end gap-3">
                        <button onClick={onClose} className="px-4 py-2 bg-gray-200 rounded">Cancel</button>
                        <button onClick={handleSubmit} disabled={loading} className="px-4 py-2 bg-primary-600 text-white rounded">{loading ? 'Admitting...' : 'Admit'}</button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default IpdAdmitModal;
