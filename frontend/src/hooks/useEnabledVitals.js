import { useState, useEffect } from 'react';
import vitalsService from '../services/vitalsService';

/**
 * useEnabledVitals - the vitals this hospital has switched ON for OPD entry.
 * Returns `isOn(key)` for the six built-ins and `customs` (hospital-defined
 * vitals, free text, no validation). While loading, every built-in is treated as
 * on so the OPD form never flickers fields away.
 *
 * `skip`: don't fetch (a standalone pharmacy has no vitals endpoint, and the shared admin
 * dashboard otherwise logged a 404 on every login).
 */
export default function useEnabledVitals({ skip = false } = {}) {
  const [vitals, setVitals] = useState(null); // null = not loaded yet

  useEffect(() => {
    if (skip) return undefined;
    let active = true;
    vitalsService
      .enabled()
      .then((list) => {
        if (active) setVitals(Array.isArray(list) ? list : []);
      })
      .catch(() => {
        if (active) setVitals([]);
      });
    return () => {
      active = false;
    };
  }, [skip]);

  const loaded = vitals !== null;
  const isOn = (key) => (!loaded ? true : vitals.some((v) => v.key === key && !v.isCustom));
  const customs = loaded ? vitals.filter((v) => v.isCustom) : [];

  return { isOn, customs, loaded };
}
