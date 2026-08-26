import { useEffect, useMemo, useState } from 'react';
import icuService from '../services/icuService';

/**
 * useEnabledVentilatorParams - the ventilator parameters this hospital has switched ON, plus the
 * controlled mode values (ICU Phase 7, D-5).
 *
 * Grouped by category so the chart can render "Ventilator Settings" and
 * "Ventilator Observations / Measurements" without knowing a single parameter name. Adding a
 * custom parameter must require no frontend change, or the catalogue is decorative.
 *
 * Unlike `useEnabledVitals`, nothing is assumed on while loading: there is no fixed field list to
 * flicker, and showing an input for a parameter the hospital has disabled would invite an entry
 * the server is going to drop.
 */
export default function useEnabledVentilatorParams() {
  const [params, setParams] = useState(null); // null = not loaded yet
  const [modes, setModes] = useState([]);

  useEffect(() => {
    let active = true;
    Promise.all([
      icuService.getEnabledVentilatorParams().catch(() => []),
      icuService.getVentilatorModes().catch(() => []),
    ]).then(([p, m]) => {
      if (!active) return;
      setParams(Array.isArray(p) ? p : []);
      setModes(Array.isArray(m) ? m : []);
    });
    return () => {
      active = false;
    };
  }, []);

  const loaded = params !== null;

  const settings = useMemo(() => (params || []).filter((p) => p.category === 'SETTING'), [params]);
  const observations = useMemo(
    () => (params || []).filter((p) => p.category === 'OBSERVATION'),
    [params]
  );

  return { params: params || [], settings, observations, modes, loaded };
}
