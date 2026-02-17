import React from 'react';
import Button from './Button';

const WardCard = ({ ward, onViewBeds, onEdit }) => {
    return (
        <div className="bg-white rounded-xl shadow p-4 flex flex-col justify-between">
            <div>
                <h3 className="text-lg font-semibold text-slate-800">{ward.wardName}</h3>
                <p className="text-sm text-slate-500">Beds: {ward.totalBeds}</p>
                <p className="text-sm text-slate-500">Price: ₹{ward.bedPrice}</p>
                {ward.floorNumber !== null && (
                    <p className="text-sm text-slate-500">Floor: {ward.floorNumber}</p>
                )}
            </div>

            <div className="mt-4 flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => onViewBeds(ward)}>
                    View Beds
                </Button>
                <Button variant="outline" size="sm" onClick={() => onEdit(ward)}>
                    Edit
                </Button>
            </div>
        </div>
    );
};

export default WardCard;
