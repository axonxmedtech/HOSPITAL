import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Open/closed state of the phone navigation drawer.
 *
 * Every dashboard renders <Navbar> and <Sidebar> as siblings. On phones the sidebar is hidden,
 * so the Navbar's menu button opens it as a drawer instead; this context connects the two
 * without each dashboard having to wire it up. The drawer closes on every route change.
 */
const MobileNavContext = createContext(null);

export const MobileNavProvider = ({ children }) => {
  const [isOpen, setIsOpen] = useState(false);
  const { pathname } = useLocation();

  useEffect(() => {
    setIsOpen(false);
  }, [pathname]);

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);

  const value = useMemo(() => ({ isOpen, open, close }), [isOpen, open, close]);
  return <MobileNavContext.Provider value={value}>{children}</MobileNavContext.Provider>;
};

/** Returns null outside a provider, so Navbar/Sidebar still render in isolated tests. */
export const useMobileNav = () => useContext(MobileNavContext);

/** Tailwind's `md` breakpoint: below it the desktop sidebar is hidden. */
export const DESKTOP_NAV_QUERY = '(min-width: 768px)';
