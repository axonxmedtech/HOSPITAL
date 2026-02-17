import React, { useEffect, useState } from 'react';
import WardCard from '../../components/WardCard';
import BedListDrawer from '../../components/BedListDrawer';
import WardService from '../../services/wardService';
import Button from '../../components/Button';

const WardsAndBeds = () => {
    const [wards, setWards] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selectedWard, setSelectedWard] = useState(null);
    const [showBeds, setShowBeds] = useState(false);

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
        // open inline edit modal inside this component
        // reuse local behaviour: open modal by setting component state
        // For simplicity keep existing behaviour by opening a small prompt
        // (detailed edit handled via top-level modal in the dashboard header)
        window.alert('Edit ward is available via the main header Add/Edit flow.');
    };

    return (
        <div className="p-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {loading && <div>Loading wards...</div>}
                {!loading && wards.length === 0 && (
                    <div className="text-slate-500">No wards found. Use "Create Ward" to add one.</div>
                )}

                {wards.map(w => (
                    <WardCard key={w.wardId} ward={w} onViewBeds={onViewBeds} onEdit={onEdit} />
                ))}
            </div>

            <BedListDrawer open={showBeds} ward={selectedWard} onClose={() => setShowBeds(false)} onStatusChange={fetchWards} />
        </div>
    );
};

export default WardsAndBeds;
