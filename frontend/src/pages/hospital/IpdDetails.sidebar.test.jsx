import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();
let currentUser = {};

vi.mock('react-router-dom', () => ({
  useParams: () => ({ id: '10' }),
  useNavigate: () => navigateMock,
  useLocation: () => ({ pathname: '/ipd/10', search: '' }),
  useSearchParams: () => [new URLSearchParams(''), vi.fn()],
  Link: ({ children }) => children,
}));

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

vi.mock('../../hooks/useWebSocket', () => ({ default: () => ({ lastMessage: null }) }));

vi.mock('../../services/authService', () => ({
  default: {
    getCurrentUser: () => currentUser,
    isDoctor: () => currentUser.role === 'DOCTOR',
    isReceptionist: () => currentUser.role === 'RECEPTIONIST',
    getLoginUrl: () => '/login',
    logout: vi.fn(),
  },
}));

vi.mock('../../services/formAccessService', () => ({
  default: { effective: vi.fn().mockResolvedValue({}) },
}));

const hospitalService = new Proxy(
  {
    getIpdDetails: vi.fn().mockResolvedValue({
      id: 10,
      status: 'ADMITTED',
      patient: { id: 1, name: 'Aarav Sharma', age: 30, gender: 'MALE' },
      admission: { admissionType: 'GENERAL', doctor: 'Dr Demo' },
      prescriptions: [],
      administeredItems: [],
    }),
  },
  {
    get: (target, prop) => {
      if (!target[prop]) target[prop] = vi.fn().mockResolvedValue([]);
      return target[prop];
    },
  }
);
vi.mock('../../services/hospitalService', () => ({ default: hospitalService }));

const emptyApi = () => ({ default: new Proxy({}, { get: () => vi.fn().mockResolvedValue([]) }) });
vi.mock('../../services/otService', () => emptyApi());
vi.mock('../../services/wardService', () => emptyApi());
vi.mock('../../services/icuService', () => emptyApi());
vi.mock('../../services/icuWardService', () => emptyApi());

const { default: IpdDetails } = await import('./IpdDetails');

describe('IpdDetails Sidebar consistency', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders full grouped sidebar for HOSPITAL_ADMIN without hiding tabs', async () => {
    currentUser = {
      role: 'HOSPITAL_ADMIN',
      hospitalType: 'HOSPITAL',
      hospitalName: 'Apollo Hospital',
      modules: [
        'OPD',
        'IPD',
        'APPOINTMENTS',
        'ICU',
        'OT',
        'PATHOLOGY',
        'BILLING',
        'PHARMACY',
        'NURSING',
      ],
    };

    render(<IpdDetails />);
    await waitFor(() => expect(screen.getByText('Patient Management')).toBeInTheDocument());

    const aside = document.querySelector('aside');
    const sidebar = within(aside);

    // Overview top level in sidebar
    expect(sidebar.getByRole('button', { name: 'Overview' })).toBeInTheDocument();

    // Group headers should exist
    expect(sidebar.getByText('Patient Management')).toBeInTheDocument();
    expect(sidebar.getByText('Rooms')).toBeInTheDocument();
    expect(sidebar.getByText('Critical Care')).toBeInTheDocument();
    expect(sidebar.getByText('Staff')).toBeInTheDocument();
    expect(sidebar.getByText('Finance')).toBeInTheDocument();

    // Patient Management is auto-expanded because activeTab is ipd
    expect(sidebar.getByRole('button', { name: 'IPD' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Patients' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Follow-ups' })).toBeInTheDocument();

    // Clicking a tab navigates back to admin dashboard
    const patientsBtn = sidebar.getByRole('button', { name: 'Patients' });
    await userEvent.click(patientsBtn);
    expect(navigateMock).toHaveBeenCalledWith('/hospital/admin?tab=patients');
  });

  it('renders full doctor sidebar for DOCTOR with all tabs matching DoctorDashboard', async () => {
    currentUser = {
      role: 'DOCTOR',
      hospitalType: 'HOSPITAL',
      hospitalName: 'Apollo Hospital',
      modules: ['OPD', 'IPD', 'APPOINTMENTS', 'BILLING', 'OT', 'ICU'],
      billingHandler: 'DOCTOR',
    };

    render(<IpdDetails />);
    await waitFor(() => expect(screen.getByText('Apollo Hospital')).toBeInTheDocument());

    const aside = document.querySelector('aside');
    const sidebar = within(aside);

    expect(sidebar.getByRole('button', { name: 'Overview' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Patients' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Appointments' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'OPD' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Follow-ups' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'IPD' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Billing' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Operation Theatre' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'ICU Dashboard' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'ICU Bed Board' })).toBeInTheDocument();

    // No old "Queue" glitch tab
    expect(sidebar.queryByRole('button', { name: 'Queue' })).not.toBeInTheDocument();

    // Clicking a tab navigates to doctor dashboard
    const opdBtn = sidebar.getByRole('button', { name: 'OPD' });
    await userEvent.click(opdBtn);
    expect(navigateMock).toHaveBeenCalledWith('/hospital/doctor?tab=opd');
  });

  it('renders full receptionist sidebar for RECEPTIONIST with all tabs matching ReceptionistDashboard', async () => {
    currentUser = {
      role: 'RECEPTIONIST',
      hospitalType: 'HOSPITAL',
      hospitalName: 'Apollo Hospital',
      modules: ['OPD', 'IPD', 'APPOINTMENTS', 'BILLING', 'OT', 'ICU'],
      billingHandler: 'RECEPTIONIST',
    };

    render(<IpdDetails />);
    await waitFor(() => expect(screen.getByText('Apollo Hospital')).toBeInTheDocument());

    const aside = document.querySelector('aside');
    const sidebar = within(aside);

    expect(sidebar.getByRole('button', { name: 'Overview' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Patients' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Appointments' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'OPD' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Follow-ups' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'IPD' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'ICU' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Billing' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'Operation Theatre' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'ICU Dashboard' })).toBeInTheDocument();
    expect(sidebar.getByRole('button', { name: 'ICU Bed Board' })).toBeInTheDocument();

    // Clicking a tab navigates to receptionist dashboard
    const apptsBtn = sidebar.getByRole('button', { name: 'Appointments' });
    await userEvent.click(apptsBtn);
    expect(navigateMock).toHaveBeenCalledWith('/hospital/receptionist?tab=appointments');
  });
});
