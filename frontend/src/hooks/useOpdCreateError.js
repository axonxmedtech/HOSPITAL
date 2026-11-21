import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useConfirm } from '../context/ConfirmContext';
import { useToast } from '../context/ToastContext';
import { apiErrorStatus, extractApiDetails, extractApiError } from '../utils/apiError';

/**
 * Explains why an OPD visit could not be opened, using the server's own reason (never a bare
 * "Failed"). When the patient is admitted in IPD the server says which admission, so the user is
 * offered the IPD case straight away — that is where this visit belongs.
 */
export default function useOpdCreateError() {
  const navigate = useNavigate();
  const confirm = useConfirm();
  const { error: toastError } = useToast();

  return useCallback(
    async (err, fallback = 'Could not create the OPD case') => {
      const where = extractApiDetails(err);
      const message = extractApiError(err, fallback);
      if (apiErrorStatus(err) === 409 && where?.encounter === 'IPD' && where.ipdAdmissionId) {
        const open = await confirm({
          title: 'Patient is admitted in IPD',
          message,
          confirmLabel: 'Open IPD case',
        });
        if (open) navigate(`/ipd/${where.ipdAdmissionId}`);
        return;
      }
      toastError(message);
    },
    [confirm, navigate, toastError]
  );
}
