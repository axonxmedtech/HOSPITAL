import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import ConfirmationModal from '../components/ConfirmationModal';

/**
 * A second step before an important one-click action — "Send to the front desk for admission?",
 * "Complete this consultation?" — so a mis-click does not do something that cannot be undone.
 *
 *   const confirm = useConfirm();
 *   if (!(await confirm({ title, message, confirmLabel }))) return;
 *
 * Resolves true when the user confirms, false when they cancel (button, Esc or clicking outside).
 * Only for actions that are hard to undo or easy to hit by mistake: a form the user filled in and
 * submitted has already had its second step.
 */
const ConfirmContext = createContext(null);

export function ConfirmProvider({ children }) {
  const [request, setRequest] = useState(null);
  const resolveRef = useRef(null);

  const confirm = useCallback(
    (options) =>
      new Promise((resolve) => {
        resolveRef.current?.(false); // a newer question replaces an unanswered one
        resolveRef.current = resolve;
        setRequest(options || {});
      }),
    []
  );

  const settle = (answer) => {
    resolveRef.current?.(answer);
    resolveRef.current = null;
    setRequest(null);
  };

  const value = useMemo(() => confirm, [confirm]);
  return (
    <ConfirmContext.Provider value={value}>
      {children}
      <ConfirmationModal
        isOpen={!!request}
        title={request?.title}
        message={request?.message}
        confirmLabel={request?.confirmLabel}
        cancelLabel={request?.cancelLabel}
        onConfirm={() => settle(true)}
        onCancel={() => settle(false)}
      />
    </ConfirmContext.Provider>
  );
}

/**
 * Outside a ConfirmProvider (a component rendered alone in a unit test) the question is answered
 * "yes", so the component behaves as it did before confirmations existed.
 */
const answerYes = () => Promise.resolve(true);

export function useConfirm() {
  return useContext(ConfirmContext) || answerYes;
}
