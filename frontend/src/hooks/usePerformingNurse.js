import { useEffect, useState } from 'react';
import authService from '../services/authService';
import nurseService from '../services/nurseService';

/**
 * "Performed By Nurse" for a nursing form, the same rule as vitals, notes and the sugar chart
 * (server: PerformingNurseResolver). With Separate Nurse Login OFF the in-charge records on behalf
 * of the ward's staff nurses and must name who did it; with it ON the logged-in nurse is credited,
 * so no picker is shown. Doctors and the admin record as themselves.
 *
 * @param {number|undefined} wardId  the admission's ward, whose staff nurses are offered
 * @param {number|null|undefined} initialNurseId  the nurse already credited on a saved form
 */
export default function usePerformingNurse(wardId, initialNurseId) {
  const role = authService.getCurrentUser()?.role;
  const isNurse = role === 'NURSE' || role === 'NURSE_INCHARGE';
  const [separateLogin, setSeparateLogin] = useState(true);
  const [nurses, setNurses] = useState([]);
  const [nurseId, setNurseId] = useState(initialNurseId ? String(initialNurseId) : '');

  useEffect(() => {
    setNurseId(initialNurseId ? String(initialNurseId) : '');
  }, [initialNurseId]);

  useEffect(() => {
    if (!isNurse) return undefined;
    let active = true;
    nurseService
      .getSeparateNurseLogin()
      .then((v) => {
        if (active) setSeparateLogin(v);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [isNurse]);

  const needsPicker = isNurse && separateLogin === false;

  useEffect(() => {
    if (!needsPicker || !wardId) return undefined;
    let active = true;
    nurseService
      .getWardStaffNurses(wardId)
      .then((list) => {
        if (active) setNurses(Array.isArray(list) ? list : []);
      })
      .catch(() => {
        if (active) setNurses([]);
      });
    return () => {
      active = false;
    };
  }, [needsPicker, wardId]);

  const nurseName = nurses.find((n) => String(n.id) === nurseId)?.name;

  return {
    needsPicker,
    nurses,
    nurseId,
    setNurseId,
    /** Name of the chosen nurse, for the printout (undefined when none is chosen). */
    nurseName,
    /** True when the form may be saved: a nurse is chosen, or none is needed. */
    ready: !needsPicker || Boolean(nurseId),
    /** Fields to merge into the save payload. */
    payload: needsPicker ? { performedByNurseId: Number(nurseId) } : {},
  };
}
