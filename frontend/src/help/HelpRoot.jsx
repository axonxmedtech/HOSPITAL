import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import authService from '../services/authService';
import CommandPalette from './CommandPalette';
import HelpPanel from './HelpPanel';
import { handleHelpKey } from './keys';

/**
 * Mounts the help panel and command bar once for the whole app, and listens for the keyboard
 * shortcuts. Renders nothing for someone who is not signed in.
 */

export default function HelpRoot() {
  const pending = useRef(0);
  useLocation(); // re-check sign-in after login / logout navigation
  const signedIn = !!authService.getCurrentUser();

  useEffect(() => {
    if (!signedIn) return undefined;
    const onKey = (e) => handleHelpKey(e, pending);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [signedIn]);

  if (!signedIn) return null;
  return (
    <>
      <HelpPanel />
      <CommandPalette />
    </>
  );
}
