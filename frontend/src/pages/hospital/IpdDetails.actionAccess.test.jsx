import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Who sees "Admit to ICU" on an IPD case.
 *
 * Moving a patient into or out of ICU is a bed transfer, and the server allows bed transfers
 * only to a receptionist, an admin, or a doctor in Solo/Both mode. The button used to show for
 * every doctor, who then picked a bed and got "access denied". It also showed without the ICU
 * module, where the ICU ward list is refused and the modal opens empty.
 */

vi.spyOn(console, 'error').mockImplementation(() => {});

let currentUser;
let doctor;
let receptionist;

vi.mock('react-router-dom', () => ({
  useParams: () => ({ id: '5' }),
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: '/hospital/ipd/5', search: '' }),
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
    isDoctor: () => doctor,
    isReceptionist: () => receptionist,
    getLoginUrl: () => '/login',
    logout: vi.fn(),
  },
}));

vi.mock('../../services/formAccessService', () => ({
  default: { effective: vi.fn().mockResolvedValue({}) },
}));

const hospitalService = new Proxy(
  { getIpdDetails: vi.fn() },
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

const ADMISSION = {
  id: 5,
  status: 'ADMITTED',
  patient: { id: 7, name: 'Ravi Kumar', age: 46, gender: 'MALE' },
  admission: { admissionType: 'GENERAL', doctor: 'Dr Demo' },
  prescriptions: [],
  administeredItems: [],
};

const as = (role, extra = {}) => {
  currentUser = { role, name: 'User', modules: ['IPD', 'ICU'], ...extra };
  doctor = role === 'DOCTOR';
  receptionist = role === 'RECEPTIONIST';
};

const renderCase = async () => {
  render(<IpdDetails />);
  await waitFor(() => expect(hospitalService.getIpdDetails).toHaveBeenCalled());
  // The name renders inside a combined subtitle ("Ravi Kumar • 46 • MALE").
  await screen.findByText(/Ravi Kumar/);
};

describe('IpdDetails — Admit to ICU visibility', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hospitalService.getIpdDetails.mockResolvedValue(ADMISSION);
  });

  it('a doctor with a receptionist on staff does not see it (the server refuses them)', async () => {
    as('DOCTOR', { receptionMode: 'HAS_RECEPTIONIST' });
    await renderCase();
    expect(screen.queryByText('Admit to ICU')).not.toBeInTheDocument();
  });

  it('a doctor in Solo mode sees it', async () => {
    as('DOCTOR', { receptionMode: 'SOLO' });
    await renderCase();
    expect(await screen.findByText('Admit to ICU')).toBeInTheDocument();
  });

  it('a receptionist sees it', async () => {
    as('RECEPTIONIST');
    await renderCase();
    expect(await screen.findByText('Admit to ICU')).toBeInTheDocument();
  });

  it('nobody sees it when the hospital has no ICU module', async () => {
    as('HOSPITAL_ADMIN', { modules: ['IPD'] });
    await renderCase();
    expect(screen.queryByText('Admit to ICU')).not.toBeInTheDocument();
  });
});

describe('IpdDetails — Complete Admission (admission form at the desk)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hospitalService.getIpdDetails.mockResolvedValue(ADMISSION);
  });

  it('reception sees it while the admission is pending', async () => {
    as('RECEPTIONIST');
    await renderCase();
    expect(await screen.findByText('Complete Admission')).toBeInTheDocument();
  });

  it('an admin sees it while the admission is pending', async () => {
    as('HOSPITAL_ADMIN');
    await renderCase();
    expect(await screen.findByText('Complete Admission')).toBeInTheDocument();
  });

  it('it is gone once the admission is confirmed', async () => {
    as('RECEPTIONIST');
    hospitalService.getIpdDetails.mockResolvedValue({
      ...ADMISSION,
      admission: { ...ADMISSION.admission, admissionConfirmed: true },
    });
    await renderCase();
    expect(screen.queryByText('Complete Admission')).not.toBeInTheDocument();
  });

  it('a doctor does not see it (the server only lets them read the form)', async () => {
    as('DOCTOR', { receptionMode: 'SOLO' });
    await renderCase();
    expect(screen.queryByText('Complete Admission')).not.toBeInTheDocument();
  });
});
