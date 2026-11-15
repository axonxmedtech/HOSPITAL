import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { useState } from 'react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mobileNav } from '../hooks/useMobileNav';
import Navbar from './Navbar';
import Sidebar from './Sidebar';

vi.mock('./UserMenu', () => ({ default: () => <div>user-menu</div> }));

/** Makes matchMedia report a phone (below md) or a desktop viewport. */
const setViewport = (isDesktop) =>
  vi.stubGlobal(
    'matchMedia',
    vi.fn((query) => ({ matches: isDesktop, media: query, addListener() {}, removeListener() {} }))
  );

const TABS = [
  { id: 'overview', label: 'Overview' },
  {
    id: 'patients-group',
    label: 'Patient Management',
    subItems: [
      { id: 'patients', label: 'Patients' },
      { id: 'opd', label: 'OPD' },
    ],
  },
];

/** A minimal dashboard: Navbar + Sidebar as siblings, like every real dashboard. */
const Dashboard = ({ onToggleSidebar = () => {} }) => {
  const [activeTab, setActiveTab] = useState('overview');
  const [expanded, setExpanded] = useState({});
  const navigate = useNavigate();
  const tabs = TABS.map((t) => ({ ...t, isExpanded: !!expanded[t.id] }));
  return (
    <>
      <Sidebar
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        onToggleGroup={(id) => setExpanded((e) => ({ ...e, [id]: !e[id] }))}
        footerTitle="Hospital"
        footerData="test hospital"
        isCollapsed={false}
      />
      <Navbar user={{ role: 'HOSPITAL_ADMIN' }} onToggleSidebar={onToggleSidebar} />
      <p>active:{activeTab}</p>
      <button type="button" onClick={() => navigate('/elsewhere')}>
        go elsewhere
      </button>
    </>
  );
};

const renderDashboard = (props) =>
  render(
    <MemoryRouter initialEntries={['/hospital/admin']}>
      <Dashboard {...props} />
    </MemoryRouter>
  );

const openMenu = () =>
  fireEvent.click(screen.getByRole('button', { name: 'Toggle navigation menu' }));
const drawer = () => screen.queryByRole('dialog', { name: 'Navigation menu' });

describe('phone navigation drawer', () => {
  afterEach(() => {
    act(() => mobileNav.close());
    vi.unstubAllGlobals();
  });

  it('desktop: the menu button toggles the sidebar rail and never opens a drawer', () => {
    setViewport(true);
    const onToggleSidebar = vi.fn();
    renderDashboard({ onToggleSidebar });
    openMenu();
    expect(onToggleSidebar).toHaveBeenCalledTimes(1);
    expect(drawer()).not.toBeInTheDocument();
  });

  it('phone: the menu button opens the navigation drawer with every section', () => {
    setViewport(false);
    const onToggleSidebar = vi.fn();
    renderDashboard({ onToggleSidebar });
    expect(drawer()).not.toBeInTheDocument();

    openMenu();

    expect(onToggleSidebar).not.toHaveBeenCalled();
    const d = within(drawer());
    expect(d.getByRole('button', { name: 'Overview' })).toBeInTheDocument();
    expect(d.getByRole('button', { name: 'Patient Management' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Toggle navigation menu' })).toHaveAttribute(
      'aria-expanded',
      'true'
    );
  });

  it('phone: expanding a group keeps the drawer open; choosing a page navigates and closes it', () => {
    setViewport(false);
    renderDashboard();
    openMenu();

    const group = within(drawer()).getByRole('button', { name: 'Patient Management' });
    fireEvent.click(group);
    expect(drawer()).toBeInTheDocument();
    expect(group).toHaveAttribute('aria-expanded', 'true');

    fireEvent.click(within(drawer()).getByRole('button', { name: 'OPD' }));
    expect(screen.getByText('active:opd')).toBeInTheDocument();
    expect(drawer()).not.toBeInTheDocument();
  });

  it('phone: closes on Escape, on the close button, and on the backdrop', () => {
    setViewport(false);
    const { container } = renderDashboard();

    openMenu();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(drawer()).not.toBeInTheDocument();

    openMenu();
    fireEvent.click(screen.getByRole('button', { name: 'Close navigation menu' }));
    expect(drawer()).not.toBeInTheDocument();

    openMenu();
    fireEvent.click(container.ownerDocument.querySelector('.bg-gray-900\\/50'));
    expect(drawer()).not.toBeInTheDocument();
  });

  it('phone: closes when the route changes', () => {
    setViewport(false);
    renderDashboard();
    openMenu();
    expect(drawer()).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'go elsewhere' }));
    expect(drawer()).not.toBeInTheDocument();
  });

  it('phone: moves focus into the drawer and back to the menu button on close', () => {
    setViewport(false);
    renderDashboard();
    const menuButton = screen.getByRole('button', { name: 'Toggle navigation menu' });
    menuButton.focus();
    openMenu();
    expect(drawer().contains(document.activeElement)).toBe(true);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(document.activeElement).toBe(menuButton);
  });

  it('marks the current page for assistive technology', () => {
    setViewport(true);
    renderDashboard();
    const desktopNav = document.querySelector('aside');
    expect(within(desktopNav).getByRole('button', { name: 'Overview' })).toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('browsers without matchMedia fall back to toggling the rail', () => {
    vi.stubGlobal('matchMedia', undefined);
    const onToggleSidebar = vi.fn();
    renderDashboard({ onToggleSidebar });
    openMenu();
    expect(onToggleSidebar).toHaveBeenCalledTimes(1);
    expect(drawer()).not.toBeInTheDocument();
  });

  it('closes the drawer when the dashboard unmounts', () => {
    setViewport(false);
    const { unmount } = renderDashboard();
    openMenu();
    expect(mobileNav.getSnapshot()).toBe(true);
    unmount();
    expect(mobileNav.getSnapshot()).toBe(false);
  });
});
