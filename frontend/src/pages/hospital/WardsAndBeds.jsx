import React, { useEffect, useState } from 'react';
import WardCard from '../../components/WardCard';
import BedListDrawer from '../../components/BedListDrawer';
import WardModal from '../../components/WardModal';
import WardService from '../../services/wardService';
import { useToast } from '../../context/ToastContext';

const WardsAndBeds = () => {
    const { error: toastError } = useToast();
    const [wards, setWards] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selectedWard, setSelectedWard] = useState(null);
    const [showBeds, setShowBeds] = useState(false);
    const [editWard, setEditWard] = useState(null);
    const [editOpen, setEditOpen] = useState(false);
    const [deleting, setDeleting] = useState(null);

    useEffect(() => { fetchWards(); }, []);

    const fetchWards = async () => {
        setLoading(true);
        try {
            const data = await WardService.getWards();
            setWards(data);
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    };

    const onViewBeds = (ward) => {
        setSelectedWard(ward);
        setShowBeds(true);
    };

    const onEdit = (ward) => {
        setEditWard(ward);
        setEditOpen(true);
    };

    const onDelete = async (ward) => {
        if (!window.confirm(`Delete ward "${ward.wardName}"? This will also delete all its unoccupied beds.`)) return;
        setDeleting(ward.wardId);
        try {
            await WardService.deleteWard(ward.wardId);
            await fetchWards();
        } catch (err) {
            toastError(err.response?.data || err.message || 'Delete failed');
        } finally {
            setDeleting(null);
        }
    };

    return (
        <div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {loading && <div>Loading wards...</div>}
                {!loading && wards.length === 0 && (
                    <div className="text-slate-500">No wards found. Use "Create Ward" to add one.</div>
                )}

                {wards.map(w => (
                    <WardCard
                        key={w.wardId}
                        ward={w}
                        onViewBeds={onViewBeds}
                        onEdit={onEdit}
                        onDelete={onDelete}
                        deleting={deleting === w.wardId}
                    />
                ))}
            </div>

            <BedListDrawer open={showBeds} ward={selectedWard} onClose={() => setShowBeds(false)} onStatusChange={fetchWards} />

            <WardModal
                open={editOpen}
                initial={editWard}
                onClose={() => { setEditOpen(false); setEditWard(null); }}
                onSaved={() => { setEditOpen(false); setEditWard(null); fetchWards(); }}
            />
        </div>
    );
};

export default WardsAndBeds;
