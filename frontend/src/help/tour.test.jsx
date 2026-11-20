import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../services/apiService', () => ({ default: { get: vi.fn(), put: vi.fn() } }));

import apiClient from '../services/apiService';
import { helpRolesFor } from './content';
import { helpStore } from './helpStore';
import { merge, progressStore } from './progressStore';
import { TOURS, toursFor } from './tourData';
import TourRunner from './TourRunner';
import { findTarget, tourStore } from './tourStore';
import WelcomeOffer from './WelcomeOffer';

const HOSPITAL = ['OPD', 'IPD', 'BILLING', 'NURSING', 'OT', 'ICU'];
const ids = (user, screens) => toursFor(user, helpRolesFor(user), screens).map((t) => t.id);

beforeEach(() => {
  vi.useRealTimers();
  const store = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
  });
  apiClient.get.mockReset();
  apiClient.put.mockReset();
  apiClient.put.mockResolvedValue({});
  progressStore.clear();
  sessionStorage.clear();
});

afterEach(() => {
  act(() => tourStore.end());
  helpStore.clearNav(helpStore.get().nav?.onTabChange);
});

describe('which tours a user gets', () => {
  it('everyone gets the basics, plus the tour for their own work', () => {
    const base = { hospitalType: 'HOSPITAL', modules: HOSPITAL };
    expect(ids({ ...base, role: 'NURSE' })).toEqual(['basics', 'nurse']);
    expect(ids({ ...base, role: 'RECEPTIONIST' })).toEqual(['basics', 'desk']);
    expect(ids({ ...base, role: 'DOCTOR', receptionMode: 'SOLO' })).toEqual([
      'basics',
      'desk',
      'doctor',
    ]);
    expect(ids({ role: 'SUPER_ADMIN' })).toEqual(['basics', 'super-admin']);
  });

  it('a pharmacy owner gets the pharmacy tour, a hospital admin the admin tour', () => {
    expect(
      ids({ role: 'HOSPITAL_ADMIN', hospitalType: 'PHARMACY', modules: ['PHARMACY'] })
    ).toContain('pharmacy-admin');
    expect(ids({ role: 'HOSPITAL_ADMIN', hospitalType: 'HOSPITAL', modules: HOSPITAL })).toContain(
      'admin'
    );
  });

  it('drops steps for screens this dashboard does not have', () => {
    const user = { role: 'HOSPITAL_ADMIN', hospitalType: 'PHARMACY', modules: ['PHARMACY'] };
    const tour = toursFor(
      user,
      helpRolesFor(user),
      new Set(['overview', 'pharmacists', 'settings'])
    ).find((t) => t.id === 'pharmacy-admin');
    expect(tour.steps.map((s) => s.goTo)).toEqual(['overview', 'pharmacists', 'settings']);
  });
});

describe('finding what a step points at', () => {
  it('finds a button by its label, but never inside the help panel', () => {
    document.body.innerHTML = `
      <aside aria-label="Help and support"><button>Admit Patient</button></aside>
      <main><button id="real">  Admit   Patient </button></main>`;
    // jsdom has no layout; treat every element as visible.
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 10,
      height: 10,
      top: 1,
      left: 1,
      bottom: 11,
      right: 11,
    });
    expect(findTarget({ text: 'Admit Patient' }).id).toBe('real');
    expect(findTarget('main button').id).toBe('real');
    expect(findTarget({ text: 'Nothing like this' })).toBeNull();
    vi.restoreAllMocks();
    document.body.innerHTML = '';
  });
});

describe('running a tour', () => {
  const tour = {
    id: 'demo',
    title: 'Demo tour',
    steps: [
      { title: 'Hello', text: 'First step.' },
      { goTo: 'opd', title: 'OPD', text: 'Second step.' },
    ],
  };

  it('opens each step’s screen and records the tour as done', async () => {
    const onTabChange = vi.fn();
    helpStore.registerNav({
      tabs: [{ id: 'opd', label: 'OPD' }],
      activeTab: 'overview',
      onTabChange,
    });
    render(<TourRunner />);
    act(() => tourStore.start(tour));

    expect(await screen.findByRole('heading', { name: 'Hello' })).toBeInTheDocument();
    expect(screen.getByText('Demo tour · 1 of 2')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onTabChange).toHaveBeenCalledWith('opd');
    expect(await screen.findByRole('heading', { name: 'OPD' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Back' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Finish' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(progressStore.get().tours.demo).toBe('done');
  });

  it('Esc ends the tour as skipped, and the help panel closes when a tour starts', async () => {
    helpStore.openPanel();
    render(<TourRunner />);
    act(() => tourStore.start(tour));
    expect(helpStore.get().panelOpen).toBe(false);
    await screen.findByRole('heading', { name: 'Hello' });
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(progressStore.get().tours.demo).toBe('skipped');
  });

  it('every built-in tour step has a title and text', () => {
    for (const t of TOURS) for (const s of t.steps) expect(s.title && s.text, t.id).toBeTruthy();
  });
});

describe('progress follows the user', () => {
  const user = { role: 'NURSE', userId: 7, hospitalId: 1 };

  it('combines this device’s copy with the server’s', () => {
    expect(
      merge(
        { guides: { g: ['a'] }, tours: { basics: 'done' } },
        {
          guides: { g: ['b'], h: ['c'] },
          tours: { basics: 'skipped', nurse: 'skipped' },
          welcome: 'shown',
        }
      )
    ).toEqual({
      guides: { g: ['a', 'b'], h: ['c'] },
      tours: { basics: 'done', nurse: 'skipped' },
      welcome: 'shown',
    });
  });

  const signIn = () => {
    sessionStorage.setItem('token', 't');
    sessionStorage.setItem('user', JSON.stringify(user));
  };

  it('loads from the server, keeps ticks made on this device and saves the result', async () => {
    vi.useFakeTimers();
    signIn();
    localStorage.setItem('hms.help.guides.1.7', JSON.stringify({ 'first-nurse': ['patients'] }));
    apiClient.get.mockResolvedValue({ data: { guides: { 'first-nurse': ['vitals'] }, tours: {} } });
    await progressStore.load(user);
    expect([...progressStore.get().guides['first-nurse']].sort()).toEqual(['patients', 'vitals']);
    expect(progressStore.isLoaded()).toBe(true);
    await vi.advanceTimersByTimeAsync(1000);
    expect(apiClient.put).toHaveBeenCalledWith(
      '/help/progress',
      expect.objectContaining({ guides: { 'first-nurse': ['vitals', 'patients'] } })
    );
  });

  it('never sends a save after the user signed out (it would fail without a session)', async () => {
    vi.useFakeTimers();
    signIn();
    apiClient.get.mockResolvedValue({ data: {} });
    await progressStore.load(user);
    apiClient.put.mockClear();
    progressStore.toggleStep('first-nurse', 'tasks');
    sessionStorage.clear(); // logout
    await vi.advanceTimersByTimeAsync(1000);
    expect(apiClient.put).not.toHaveBeenCalled();
    // The tick is kept on this device for the next sign-in.
    expect(JSON.parse(localStorage.getItem('hms.help.guides.1.7')).guides['first-nurse']).toEqual([
      'tasks',
    ]);
  });

  it('keeps working on this device when the server cannot be reached', async () => {
    apiClient.get.mockRejectedValue(new Error('offline'));
    apiClient.put.mockRejectedValue(new Error('offline'));
    await progressStore.load(user);
    act(() => progressStore.toggleStep('first-nurse', 'tasks'));
    expect(progressStore.get().guides['first-nurse']).toEqual(['tasks']);
    expect(JSON.parse(localStorage.getItem('hms.help.guides.1.7')).guides['first-nurse']).toEqual([
      'tasks',
    ]);
  });
});

describe('welcome offer', () => {
  const user = { role: 'NURSE', userId: 7, hospitalId: 1 };

  it('is offered once, after the server copy is known', async () => {
    apiClient.get.mockResolvedValue({ data: {} });
    render(<WelcomeOffer firstName="Asha" />);
    expect(screen.queryByRole('region', { name: 'Welcome tour' })).not.toBeInTheDocument();
    await act(() => progressStore.load(user));
    expect(screen.getByText('Welcome, Asha! New to AxonX?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Not now' }));
    expect(screen.queryByRole('region', { name: 'Welcome tour' })).not.toBeInTheDocument();
    expect(progressStore.get().welcome).toBe('shown');
  });

  it('is not offered to someone who already took the tour elsewhere', async () => {
    apiClient.get.mockResolvedValue({ data: { tours: { basics: 'done' } } });
    render(<WelcomeOffer />);
    await act(() => progressStore.load(user));
    expect(screen.queryByRole('region', { name: 'Welcome tour' })).not.toBeInTheDocument();
  });

  it('starts the tour', async () => {
    apiClient.get.mockResolvedValue({ data: {} });
    render(
      <>
        <WelcomeOffer />
        <TourRunner />
      </>
    );
    await act(() => progressStore.load(user));
    await userEvent.click(screen.getByRole('button', { name: 'Take the tour' }));
    expect(await screen.findByRole('heading', { name: 'Welcome to AxonX' })).toBeInTheDocument();
  });
});
