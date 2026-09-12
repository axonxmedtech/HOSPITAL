import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const toastError = vi.fn();
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: toastError }),
  ToastProvider: ({ children }) => children,
}));

vi.mock('../../hooks/useWebSocket', () => ({ default: () => ({ lastMessage: null }) }));
vi.mock('../../hooks/useEnabledVitals', () => ({
  default: () => ({ vitals: [], loading: false, isEnabled: () => true }),
}));
vi.mock('../../hooks/useOtPermissions', () => ({
  default: () => ({ can: () => false, loading: false, hasOt: false }),
}));
vi.mock('../../services/otService', () => ({
  default: { getRequests: vi.fn().mockResolvedValue([]), getBoard: vi.fn().mockResolvedValue([]) },
}));

vi.mock('../../components/ConsultationModal', () => ({
  default: () => null,
}));

let currentUser = {
  modules: ['OPD', 'APPOINTMENTS'],
  name: 'Dr Mandal',
  receptionMode: 'HAS_RECEPTIONIST',
};

vi.mock('../../services/authService', () => ({
  default: {
    getCurrentUser: () => currentUser,
    isDoctor: () => true,
    isReceptionist: () => false,
    getProfile: vi.fn().mockResolvedValue({}),
    updateCurrentUser: () => null,
    logout: vi.fn(),
    getLoginUrl: () => '/login/hospital',
  },
}));

const hospitalService = {
  getMyAppointments: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getAppointmentStats: vi.fn().mockResolvedValue({}),
  getTodaysFollowUps: vi.fn().mockResolvedValue([]),
  getPatients: vi.fn().mockResolvedValue({ content: [] }),
  getDoctorQueue: vi.fn().mockResolvedValue([]),
  getOpds: vi.fn().mockResolvedValue({
    content: [
      {
        id: 55,
        caseId: 'OPD-55',
        status: 'COMPLETED',
        createdAt: '2026-09-05T09:00:00.000Z',
        patient: { id: 7, name: 'Ravi Kumar' },
      },
    ],
    totalElements: 1,
  }),
  getDoctors: vi.fn().mockResolvedValue({ content: [] }),
  getIpdAdmissions: vi.fn().mockResolvedValue({ content: [] }),
  getDoctorProfile: vi.fn().mockResolvedValue({ id: 1, name: 'Dr Mandal' }),
};
vi.mock('../../services/hospitalService', () => ({ default: hospitalService }));

const renderDashboard = async (receptionMode) => {
  currentUser.receptionMode = receptionMode;
  const { default: DoctorDashboard } = await import('./DoctorDashboard');
  return render(
    <MemoryRouter initialEntries={['/']}>
      <DoctorDashboard />
    </MemoryRouter>
  );
};

describe('DoctorDashboard — Reception Mode front-desk capabilities', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('hides front-desk actions when receptionMode is HAS_RECEPTIONIST', async () => {
    await renderDashboard('HAS_RECEPTIONIST');

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    );

    expect(screen.queryByRole('button', { name: /Add Patient/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /OPD Intake/i })).not.toBeInTheDocument();
  });

  it('shows front-desk actions when receptionMode is BOTH', async () => {
    const { unmount } = await renderDashboard('BOTH');

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    );

    expect(screen.getByRole('button', { name: /Add Patient/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /OPD Intake/i })).toBeInTheDocument();
    unmount();
  });

  it('shows front-desk actions when receptionMode is SOLO', async () => {
    await renderDashboard('SOLO');

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    );

    expect(screen.getByRole('button', { name: /Add Patient/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /OPD Intake/i })).toBeInTheDocument();
  });
});

/**
 * The OPD row menu is built inside DoctorOpdTable, a separate component that receives `user`
 * as a prop. Its admit-to-IPD branch is the one place the mode is read outside the dashboard's
 * own scope, so it is exercised on purpose: a wrong identifier there throws only when the menu
 * for a completed case is opened with the IPD module on - which no other test does.
 */
describe('DoctorDashboard — OPD row admit-to-IPD action by reception mode', () => {
  /**
   * Opens the completed case's row menu. ActionMenu portals its items to document.body, so
   * they are read from `screen`, not the row. Under HAS_RECEPTIONIST the Live view rightly
   * hides a completed case (the desk owns that queue), so the Date view is used to make the
   * row visible at all.
   */
  const openCompletedRowMenu = async (receptionMode) => {
    currentUser = { ...currentUser, modules: ['OPD', 'IPD'], receptionMode };
    const { default: DoctorDashboard } = await import('./DoctorDashboard');
    render(
      <MemoryRouter initialEntries={['/']}>
        <DoctorDashboard />
      </MemoryRouter>
    );
    fireEvent.click(await screen.findByRole('button', { name: 'OPD' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Date' }));
    const row = (await screen.findByText('Ravi Kumar')).closest('tr');
    fireEvent.click(within(row).getAllByRole('button').at(-1));
    await screen.findByRole('menu');
  };

  it('offers Admit to IPD when receptionMode is BOTH', async () => {
    await openCompletedRowMenu('BOTH');
    expect(screen.getByRole('menuitem', { name: /Admit to IPD/ })).toBeInTheDocument();
  });

  it('offers Admit to IPD when receptionMode is SOLO', async () => {
    await openCompletedRowMenu('SOLO');
    expect(screen.getByRole('menuitem', { name: /Admit to IPD/ })).toBeInTheDocument();
  });

  it('withholds Admit to IPD when receptionMode is HAS_RECEPTIONIST', async () => {
    await openCompletedRowMenu('HAS_RECEPTIONIST');
    // The menu opened with its other actions; only the front-desk one is absent.
    expect(screen.getByRole('menuitem', { name: /View Prescription/ })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Admit to IPD/ })).not.toBeInTheDocument();
  });
});
