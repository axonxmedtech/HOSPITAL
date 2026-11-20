import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let currentUser = null;
vi.mock('../services/authService', () => ({ default: { getCurrentUser: () => currentUser } }));
vi.mock('../services/apiService', () => ({
  default: { get: vi.fn(), put: vi.fn(), post: vi.fn(() => Promise.resolve({ data: {} })) },
}));
vi.mock('../services/hospitalService', () => ({
  default: { getSetupStatus: vi.fn(() => Promise.resolve(null)) },
}));

import apiClient from '../services/apiService';
import CommandPalette from './CommandPalette';
import { ALL_ENTRIES, entriesFor } from './content';
import { ROLE_NAMES } from './content/roles';
import HelpPanel from './HelpPanel';
import { helpStore } from './helpStore';
import { handleHelpKey } from './keys';
import { CONFIDENT_SCORE, rank } from './search';

const VALID_ROLES = new Set([...Object.keys(ROLE_NAMES), 'DESK', '*']);
const MODULES = [
  'OPD',
  'APPOINTMENTS',
  'IPD',
  'ICU',
  'OT',
  'BILLING',
  'NURSING',
  'PHARMACY',
  'HOSPITAL_INVENTORY',
];
// Every module key the backend knows (EntitlementRegistry), including the pharmacy tiers.
const VALID_MODULES = [
  ...MODULES,
  'MEDICAL_INVENTORY',
  'SINGLE_PHARMACIST_ADMIN',
  'SINGLE_PHARMACY',
  'MULTI_PHARMACY',
];

const user = (role, extra = {}) => ({ role, name: 'Asha Rao', modules: MODULES, ...extra });

const nav = (onTabChange) => ({
  tabs: [
    { id: 'overview', label: 'Overview' },
    { id: 'opd', label: 'OPD' },
    { id: 'group-x', label: 'Rooms', subItems: [{ id: 'wards', label: 'Wards & Beds' }] },
  ],
  activeTab: 'opd',
  onTabChange,
});

const top = (role, question) => {
  const [best] = rank(entriesFor(user(role)), question);
  return best && best.score >= CONFIDENT_SCORE ? best.entry.id : null;
};

const reset = () => {
  helpStore.closePanel();
  helpStore.closePalette();
  helpStore.setView('assistant');
  helpStore.clearNav(helpStore.get().nav?.onTabChange);
};

describe('help content', () => {
  it('every answer is complete, uniquely named and aimed at real roles', () => {
    const ids = new Set();
    for (const e of ALL_ENTRIES) {
      expect(ids.has(e.id), `duplicate id ${e.id}`).toBe(false);
      ids.add(e.id);
      expect(e.q?.length, e.id).toBeGreaterThan(5);
      expect(e.a?.length, e.id).toBeGreaterThan(20);
      expect(e.category, e.id).toBeTruthy();
      expect(e.roles?.length, e.id).toBeGreaterThan(0);
      e.roles.forEach((r) => expect(VALID_ROLES.has(r), `${e.id}: role ${r}`).toBe(true));
      (e.modules || []).forEach((m) => expect(VALID_MODULES, `${e.id}: module ${m}`).toContain(m));
    }
    expect(ALL_ENTRIES.length).toBeGreaterThan(100);
  });

  it('gives every role a solid set of answers', () => {
    for (const role of Object.keys(ROLE_NAMES)) {
      expect(entriesFor(user(role)).length, role).toBeGreaterThanOrEqual(10);
    }
  });

  it('keeps answers to the role and the modules the hospital has', () => {
    const nurse = entriesFor(user('NURSE')).map((e) => e.id);
    expect(nurse).toContain('nurse-give-medicine');
    expect(nurse).not.toContain('admin-add-doctor');

    const clinic = entriesFor(user('HOSPITAL_ADMIN', { modules: ['OPD', 'BILLING'] })).map(
      (e) => e.id
    );
    expect(clinic).toContain('admin-fees');
    expect(clinic).not.toContain('admin-wards');
  });

  it('a standalone pharmacy gets pharmacy answers, never hospital ones', () => {
    const owner = entriesFor({
      role: 'HOSPITAL_ADMIN',
      hospitalType: 'PHARMACY',
      modules: ['PHARMACY', 'MULTI_PHARMACY'],
    }).map((e) => e.id);
    expect(owner).toEqual(
      expect.arrayContaining([
        'pa-branches',
        'ph-sell',
        'admin-plan-usage',
        'common-change-password',
      ])
    );
    [
      'admin-add-doctor',
      'admin-reception-mode',
      'desk-register-patient',
      'admin-import-patients',
      'pa-add-pharmacist',
    ].forEach((id) => expect(owner).not.toContain(id));
    const pharmacist = entriesFor({
      role: 'PHARMACIST',
      hospitalType: 'PHARMACY',
      modules: ['PHARMACY'],
    }).map((e) => e.id);
    expect(pharmacist).not.toContain('ph-dispense');
  });

  it('a doctor who runs the desk also gets the front-desk answers', () => {
    const plain = entriesFor(user('DOCTOR')).length;
    const solo = entriesFor(user('DOCTOR', { receptionMode: 'SOLO' })).length;
    expect(solo).toBeGreaterThan(plain);
  });
});

describe('help search understands everyday questions', () => {
  it.each([
    ['RECEPTIONIST', 'how do i admit a patient'],
    ['RECEPTIONIST', 'cancel opd visit'],
    ['NURSE', 'give saline'],
    ['NURSE', 'record bp'],
    ['HOSPITAL_ADMIN', 'add new doctor'],
    ['HOSPITAL_ADMIN', 'set consultation fee'],
    ['PHARMACIST', 'expired medicines'],
    ['DOCTOR', 'prescribe medicine'],
  ])('%s: "%s" finds a confident answer', (role, q) => {
    expect(top(role, q)).not.toBeNull();
  });

  it('does not pretend to know nonsense', () => {
    expect(top('NURSE', 'zzqx blorf')).toBeNull();
  });
});

describe('help panel', () => {
  beforeEach(reset);
  afterEach(reset);

  const renderPanel = () =>
    render(
      <MemoryRouter>
        <HelpPanel />
      </MemoryRouter>
    );

  it('answers a question with steps and a button to the screen', async () => {
    currentUser = user('HOSPITAL_ADMIN');
    const onTabChange = vi.fn();
    helpStore.registerNav(nav(onTabChange));
    renderPanel();
    act(() => helpStore.openPanel());

    await userEvent.type(screen.getByLabelText('Ask a question'), 'how to add wards and beds');
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }));

    expect(screen.getByText('How do I add wards and beds?', { selector: 'p' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Go to Wards & Beds/ }));
    expect(onTabChange).toHaveBeenCalledWith('wards');
  });

  it('non-admins are pointed to their admin, admins to a support ticket', async () => {
    currentUser = user('NURSE');
    const { unmount } = renderPanel();
    act(() => helpStore.openPanel());
    await userEvent.type(screen.getByLabelText('Ask a question'), 'zzqx blorf');
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }));
    expect(screen.getByText('Still stuck? Your hospital admin can help.')).toBeInTheDocument();
    expect(screen.queryByText(/support ticket/i)).not.toBeInTheDocument();
    unmount();

    currentUser = user('HOSPITAL_ADMIN');
    renderPanel();
    act(() => helpStore.openPanel());
    expect(
      screen.getAllByRole('button', { name: 'Raise a support ticket' }).length
    ).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Guides' })).toBeInTheDocument();
  });

  it('an answer can be rated, and a question help cannot answer is reported', async () => {
    currentUser = user('RECEPTIONIST');
    apiClient.post.mockClear();
    renderPanel();
    act(() => helpStore.openPanel());
    await userEvent.type(screen.getByLabelText('Ask a question'), 'how do i admit a patient');
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }));
    await userEvent.click(screen.getByRole('button', { name: 'No' }));
    expect(screen.getByText(/we will improve this answer/)).toBeInTheDocument();
    expect(apiClient.post).toHaveBeenCalledWith(
      '/help/feedback',
      expect.objectContaining({ kind: 'NOT_HELPFUL', entryId: 'desk-admit-direct' })
    );

    await userEvent.type(screen.getByLabelText('Ask a question'), 'zzqx blorf');
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }));
    expect(apiClient.post).toHaveBeenCalledWith(
      '/help/feedback',
      expect.objectContaining({ kind: 'UNANSWERED', question: 'zzqx blorf' })
    );
    expect(screen.getByText(/don’t type patient names or numbers/)).toBeInTheDocument();
  });

  it('topics open the chosen answer, and the shortcuts are listed', async () => {
    currentUser = user('RECEPTIONIST');
    renderPanel();
    act(() => helpStore.openPanel('topics'));
    const first = entriesFor(currentUser)[0];
    await userEvent.click(screen.getByRole('button', { name: first.q }));
    expect(screen.getByText(first.a)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Shortcuts' }));
    expect(screen.getByText('Command bar: go to any screen or search help')).toBeInTheDocument();
  });
});

describe('command bar', () => {
  beforeEach(reset);
  afterEach(reset);

  it('jumps to a screen with the keyboard', async () => {
    currentUser = user('HOSPITAL_ADMIN');
    const onTabChange = vi.fn();
    helpStore.registerNav(nav(onTabChange));
    render(<CommandPalette />);
    act(() => helpStore.openPalette());
    await userEvent.type(screen.getByLabelText('Go to a screen or search help'), 'ward{Enter}');
    expect(onTabChange).toHaveBeenCalledWith('wards');
    expect(helpStore.get().paletteOpen).toBe(false);
  });

  it('can hand a question to the assistant', async () => {
    currentUser = user('NURSE');
    render(<CommandPalette />);
    act(() => helpStore.openPalette());
    await userEvent.type(screen.getByLabelText('Go to a screen or search help'), 'zzqx');
    await userEvent.click(screen.getByRole('button', { name: /Ask help/ }));
    expect(helpStore.get()).toMatchObject({ panelOpen: true, question: 'zzqx' });
  });
});

describe('keyboard shortcuts', () => {
  beforeEach(reset);
  afterEach(reset);

  const key = (k, extra = {}, target = document.body) => ({
    key: k,
    target,
    ctrlKey: false,
    metaKey: false,
    altKey: false,
    preventDefault: vi.fn(),
    ...extra,
  });

  it('Ctrl+K, F1, ? and Esc', () => {
    const pending = { current: 0 };
    handleHelpKey(key('k', { ctrlKey: true }), pending);
    expect(helpStore.get().paletteOpen).toBe(true);
    handleHelpKey(key('Escape'), pending);
    expect(helpStore.get().paletteOpen).toBe(false);
    handleHelpKey(key('?'), pending);
    expect(helpStore.get()).toMatchObject({ panelOpen: true, panelView: 'shortcuts' });
    handleHelpKey(key('Escape'), pending);
    expect(helpStore.get().panelOpen).toBe(false);
    handleHelpKey(key('F1'), pending);
    expect(helpStore.get().panelOpen).toBe(true);
  });

  it('G then O opens OPD', () => {
    const onTabChange = vi.fn();
    helpStore.registerNav(nav(onTabChange));
    const pending = { current: 0 };
    handleHelpKey(key('g'), pending);
    handleHelpKey(key('o'), pending);
    expect(onTabChange).toHaveBeenCalledWith('opd');
  });

  it('single keys do nothing while typing', () => {
    const onTabChange = vi.fn();
    helpStore.registerNav(nav(onTabChange));
    const input = document.createElement('input');
    const pending = { current: 0 };
    expect(handleHelpKey(key('?', {}, input), pending)).toBe(false);
    handleHelpKey(key('g', {}, input), pending);
    handleHelpKey(key('o', {}, input), pending);
    expect(onTabChange).not.toHaveBeenCalled();
    expect(helpStore.get().panelOpen).toBe(false);
  });
});
