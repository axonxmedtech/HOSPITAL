import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Reception Mode on the admin Settings tab, with its three values.
 *
 * SOLO turns the front desk off, so billing must go to the doctor and the billing choice is
 * locked. BOTH keeps a receptionist AND lets the doctor do front-desk work, so billing stays
 * whatever the admin chose - it is a separate decision. These tests drive the real select and
 * assert what actually reaches the service, which is the only thing that matters.
 */

const toastError = vi.fn();
const toastSuccess = vi.fn();
vi.spyOn(console, 'error').mockImplementation(() => {});

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError }),
  ToastProvider: ({ children }) => children,
}));
vi.mock('../../hooks/useWebSocket', () => ({ default: () => ({ lastMessage: null }) }));
vi.mock('../../hooks/useEnabledVitals', () => ({
  default: () => ({ isOn: () => false, customs: [], loaded: true }),
}));

const currentUser = {
  id: 3,
  role: 'HOSPITAL_ADMIN',
  name: 'Admin',
  modules: ['OPD', 'BILLING'],
  tenantType: 'HOSPITAL',
};
vi.mock('../../services/authService', () => ({
  default: {
    getCurrentUser: () => currentUser,
    isHospitalAdmin: () => true,
    isDoctor: () => false,
    getProfile: vi.fn().mockResolvedValue(currentUser),
    updateCurrentUser: () => null,
    logout: vi.fn(),
    getLoginUrl: () => '/login/hospital',
  },
}));

const hospitalService = {
  getAppointmentStats: vi.fn().mockResolvedValue({}),
  getGlobalStats: vi.fn().mockResolvedValue({}),
  getHospitalOperationsSettings: vi.fn(),
  updateHospitalOperationsSettings: vi.fn(),
  getHospitalFees: vi.fn().mockResolvedValue([]),
  getCustomFees: vi.fn().mockResolvedValue([]),
};
vi.mock('../../services/hospitalService', () => ({ default: hospitalService }));

const emptyApi = () => ({ default: new Proxy({}, { get: () => vi.fn().mockResolvedValue([]) }) });
vi.mock('../../services/wardService', () => emptyApi());
vi.mock('../../services/timeSlotService', () => emptyApi());
vi.mock('../../services/pharmacy/branchesApi', () => emptyApi());
vi.mock('../../services/pharmacy/inventoryApi', () => emptyApi());
vi.mock('../../services/pharmacy/reportsApi', () => emptyApi());
vi.mock('../../services/pharmacy/salesApi', () => emptyApi());

// Static import: userEvent.setup() redefines HTMLElement.prototype.focus as a getter, which
// breaks @react-aria if the component graph is loaded after that point.
const { default: HospitalAdminDashboard } = await import('./HospitalAdminDashboard');

/** The selects carry no label; the option text is the stable handle. */
const receptionSelect = () =>
  screen.getByRole('option', { name: /Both \(Doctor & Receptionist - Shared\)/ }).closest('select');
const billingSelect = () =>
  screen.getByRole('option', { name: /^Both \(Doctor & Receptionist\)$/ }).closest('select');

/** The Settings tab opens on a menu of cards; Reception Mode lives behind "Operations Settings". */
const renderSettings = async (user, settings) => {
  hospitalService.getHospitalOperationsSettings.mockResolvedValue(settings);
  render(
    <MemoryRouter initialEntries={['/hospital/admin?tab=settings']}>
      <HospitalAdminDashboard />
    </MemoryRouter>
  );
  await waitFor(() => expect(hospitalService.getHospitalOperationsSettings).toHaveBeenCalled());
  await user.click(await screen.findByText('Operations Settings'));
  await waitFor(() => expect(receptionSelect()).toHaveValue(settings.receptionMode));
};

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.updateHospitalOperationsSettings.mockImplementation(async (s) => s);
});

describe('Reception Mode setting', () => {
  it('offers all three modes', async () => {
    const user = userEvent.setup();
    await renderSettings(user, {
      receptionMode: 'HAS_RECEPTIONIST',
      billingHandler: 'RECEPTIONIST',
    });

    const values = Array.from(receptionSelect().options).map((o) => o.value);
    expect(values).toEqual(['HAS_RECEPTIONIST', 'SOLO', 'BOTH']);
  });

  it('BOTH keeps the billing handler the admin chose - it is not forced to the doctor', async () => {
    const user = userEvent.setup();
    await renderSettings(user, {
      receptionMode: 'HAS_RECEPTIONIST',
      billingHandler: 'RECEPTIONIST',
    });

    await user.selectOptions(receptionSelect(), 'BOTH');

    await waitFor(() =>
      expect(hospitalService.updateHospitalOperationsSettings).toHaveBeenCalledTimes(1)
    );
    expect(hospitalService.updateHospitalOperationsSettings.mock.calls[0][0]).toMatchObject({
      receptionMode: 'BOTH',
      billingHandler: 'RECEPTIONIST',
    });
    // And the billing choice stays open to the admin.
    expect(billingSelect()).toBeEnabled();
    expect(screen.getByText('Both (Shared)')).toBeInTheDocument();
  });

  it('SOLO locks billing to the doctor', async () => {
    const user = userEvent.setup();
    await renderSettings(user, { receptionMode: 'SOLO', billingHandler: 'DOCTOR' });

    expect(billingSelect()).toBeDisabled();
    expect(billingSelect()).toHaveValue('DOCTOR');
    expect(screen.getByText('Self Manage')).toBeInTheDocument();
  });
});
