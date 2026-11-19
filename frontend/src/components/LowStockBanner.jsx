import React, { useState, useEffect } from 'react';
import nurseService from '../services/nurseService';

/**
 * Dashboard banner listing medicines and hospital items (saline, IV fluids, consumables) at or
 * below their reorder level — out of stock first — so the doctor can get them reordered before a
 * patient's dose is due, or tell the patient to bring it. Renders nothing when all is stocked.
 */
const LowStockBanner = () => {
  const [items, setItems] = useState([]);

  useEffect(() => {
    let active = true;
    nurseService
      .getLowStock()
      .then((data) => {
        if (active) setItems(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (active) setItems([]);
      });
    return () => {
      active = false;
    };
  }, []);

  if (!items || items.length === 0) return null;
  const out = items.filter((i) => i.outOfStock);
  const low = items.filter((i) => !i.outOfStock);

  return (
    <output className="mb-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 space-y-1 block">
      <div className="text-sm font-semibold text-amber-900">Low stock — reorder soon</div>
      {out.length > 0 && (
        <p className="text-xs text-red-700">
          <span className="font-semibold">Out of stock:</span> {out.map((i) => i.name).join(' · ')}
          <span className="text-red-600"> — patients will need to bring these.</span>
        </p>
      )}
      {low.length > 0 && (
        <p className="text-xs text-amber-800">
          <span className="font-semibold">Running low:</span>{' '}
          {low.map((i) => `${i.name} (${i.available} left, reorder at ${i.minLevel})`).join(' · ')}
        </p>
      )}
    </output>
  );
};

export default LowStockBanner;
