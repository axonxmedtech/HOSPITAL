import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import authService from '../services/authService';
import CommandPalette from './CommandPalette';
import HelpPanel from './HelpPanel';
import { handleHelpKey } from './keys';
import { progressStore } from './progressStore';
import TourRunner from './TourRunner';
import WelcomeOffer from './WelcomeOffer';

/**
 * Mounts the help panel and command bar once for the whole app, and listens for the keyboard
 * shortcuts. Renders nothing for someone who is not signed in.
 */

export default function HelpRoot() {
  const pending = useRef(0);
  const { pathname } = useLocation(); // re-check sign-in after login / logout navigation
  const user = authService.getCurrentUser();
  const signedIn = !!user;
  const userKey = user
    ? `${user.role}:${user.hospitalId ?? 'p'}:${user.userId ?? user.email}`
    : null;
  const onLoginPage = /\/login\b/.test(pathname);

  // Help progress (guide ticks, tours seen) follows the user; forget it when they sign out.
  useEffect(() => {
    if (userKey) progressStore.load(authService.getCurrentUser());
    else progressStore.clear();
  }, [userKey]);

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
      <TourRunner />
      {!onLoginPage && <WelcomeOffer firstName={(user.name || '').split(' ')[0]} />}
    </>
  );
}
