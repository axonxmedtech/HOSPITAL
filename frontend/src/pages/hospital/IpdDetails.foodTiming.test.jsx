import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Food timing on an IPD medicine order.
 *
 * "Before food" says nothing about a drug that never meets a meal, and it does not stay in
 * the form: it reaches the nurse's medication chart and reads there as an instruction. So for
 * an injection the control is unavailable AND the value must not be submitted — including
 * when the order became an injection after a timing had already been chosen, which is the
 * easy mistake, because picking an injection from the catalogue sets the type on its own.
 *
 * Oral orders must keep working exactly as before; that is half of what these tests pin.
 */

const toastError = vi.fn();
const toastSuccess = vi.fn();
vi.spyOn(console, 'error').mockImplementation(() => {});

vi.mock('react-router-dom', () => ({
  useParams: () => ({ id: '5' }),
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: '/hospital/ipd/5', search: '' }),
  useSearchParams: () => [new URLSearchParams(''), vi.fn()],
  Link: ({ children }) => children,
}));

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError }),
}));

vi.mock('../../hooks/useWebSocket', () => ({ default: () => ({ lastMessage: null }) }));

vi.mock('../../services/authService', () => ({
  default: {
    getCurrentUser: () => ({ role: 'DOCTOR', name: 'Dr Demo', modules: ['IPD'] }),
    isDoctor: () => true,
    isReceptionist: () => false,
  },
}));

// No Files & Access overrides: every tab falls back to EDITABLE.
vi.mock('../../services/formAccessService', () => ({
  default: { effective: vi.fn().mockResolvedValue({}) },
}));

const hospitalService = {
  getIpdDetails: vi.fn(),
  addIpdPrescription: vi.fn(),
  searchMedicines: vi.fn(),
  getInventoryMedicines: vi.fn().mockResolvedValue([]),
  administerIpdItems: vi.fn().mockResolvedValue({}),
};
vi.mock('../../services/hospitalService', () => ({ default: hospitalService }));

const emptyApi = () => ({ default: new Proxy({}, { get: () => vi.fn().mockResolvedValue([]) }) });
vi.mock('../../services/otService', () => emptyApi());
vi.mock('../../services/wardService', () => emptyApi());

// Static import: userEvent.setup() redefines HTMLElement.prototype.focus as a getter, which
// breaks @react-aria if the component graph is loaded after that point.
const { default: IpdDetails } = await import('./IpdDetails');

const ADMISSION = {
  id: 5,
  status: 'ADMITTED',
  patient: { id: 7, name: 'Ravi Kumar', age: 46, gender: 'MALE' },
  admission: { admissionType: 'GENERAL', doctor: 'Dr Demo' },
  prescriptions: [],
  administeredItems: [],
};

/** Opens the Medication tab's Add Medicine modal and returns a scope over the form. */
const openMedicineForm = async (user) => {
  render(<IpdDetails />);
  await waitFor(() => expect(hospitalService.getIpdDetails).toHaveBeenCalled());
  await user.click(await screen.findByRole('button', { name: 'Medication' }));
  await user.click(await screen.findByRole('button', { name: '+ Add Medicine' }));
  return await screen.findByLabelText('Food Timing');
};

const typeSelect = () => screen.getByLabelText('Type');
const routeSelect = () => screen.getByLabelText('Route');

/** Fills every field the save handler validates, then saves. */
const fillAndSave = async (user, { name = 'Paracetamol' } = {}) => {
  if (!screen.getByLabelText('Medicine Name').value) {
    await user.type(screen.getByLabelText('Medicine Name'), name);
  }
  await user.type(screen.getByLabelText('Dose'), '500mg');
  const morning = screen.getByRole('textbox', { name: 'Morning dose' });
  await user.clear(morning);
  await user.type(morning, '1');
  await user.type(screen.getByLabelText('Duration (days)'), '5');
  await user.click(screen.getByRole('button', { name: /Save Prescription/ }));
  await waitFor(() => expect(hospitalService.addIpdPrescription).toHaveBeenCalledTimes(1));
  return hospitalService.addIpdPrescription.mock.calls[0][1];
};

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.getIpdDetails.mockResolvedValue(ADMISSION);
  hospitalService.addIpdPrescription.mockResolvedValue({});
  hospitalService.searchMedicines.mockResolvedValue([]);
});

describe('IPD food timing — applicable medicines keep working', () => {
  it('submits the chosen timing for an oral tablet', async () => {
    const user = userEvent.setup();
    const foodTiming = await openMedicineForm(user);

    expect(foodTiming).toBeEnabled();
    await user.selectOptions(foodTiming, 'AFTER_FOOD');

    const payload = await fillAndSave(user);
    expect(payload).toMatchObject({ type: 'TABLET', route: 'ORAL', foodTiming: 'AFTER_FOOD' });
  });

  it('submits the chosen timing for an oral syrup', async () => {
    const user = userEvent.setup();
    await openMedicineForm(user);

    await user.selectOptions(typeSelect(), 'SYRUP');
    expect(screen.getByLabelText('Food Timing')).toBeEnabled();
    await user.selectOptions(screen.getByLabelText('Food Timing'), 'BEFORE_FOOD');

    const payload = await fillAndSave(user);
    expect(payload).toMatchObject({ type: 'SYRUP', foodTiming: 'BEFORE_FOOD' });
  });

  it('still sends null when an oral order simply leaves it unstated', async () => {
    const user = userEvent.setup();
    await openMedicineForm(user);

    const payload = await fillAndSave(user);
    expect(payload.foodTiming).toBeNull();
  });
});

describe('IPD food timing — injections', () => {
  it('disables the control and explains why', async () => {
    const user = userEvent.setup();
    await openMedicineForm(user);

    await user.selectOptions(typeSelect(), 'INJECTION');

    expect(screen.getByLabelText('Food Timing')).toBeDisabled();
    expect(screen.getByText('Not applicable for injections.')).toBeInTheDocument();
  });

  it('drops a timing chosen before the order became an injection', async () => {
    const user = userEvent.setup();
    const foodTiming = await openMedicineForm(user);

    // The doctor answers the question, then changes the medicine.
    await user.selectOptions(foodTiming, 'AFTER_FOOD');
    expect(foodTiming).toHaveValue('AFTER_FOOD');

    await user.selectOptions(typeSelect(), 'INJECTION');

    // Cleared on screen, and null on the wire.
    expect(screen.getByLabelText('Food Timing')).toHaveValue('');
    const payload = await fillAndSave(user);
    expect(payload).toMatchObject({ type: 'INJECTION' });
    expect(payload.foodTiming).toBeNull();
  });

  it.each([
    ['IV', 'IV'],
    ['IM', 'IM'],
  ])('treats a %s route as non-applicable even for a tablet type', async (_label, route) => {
    const user = userEvent.setup();
    const foodTiming = await openMedicineForm(user);

    await user.selectOptions(foodTiming, 'WITH_FOOD');
    await user.selectOptions(routeSelect(), route);

    expect(screen.getByLabelText('Food Timing')).toBeDisabled();
    const payload = await fillAndSave(user);
    expect(payload).toMatchObject({ type: 'TABLET', route });
    expect(payload.foodTiming).toBeNull();
  });

  it('clears a stale timing when an injection is picked from the catalogue', async () => {
    // The quiet path: the catalogue sets the type, the doctor never touches the Type field.
    hospitalService.searchMedicines.mockResolvedValue([
      { id: 31, name: 'Ceftriaxone', type: 'Injection', defaultDosage: '1g' },
    ]);
    const user = userEvent.setup();
    const foodTiming = await openMedicineForm(user);

    await user.selectOptions(foodTiming, 'BEFORE_FOOD');
    await user.type(screen.getByLabelText('Medicine Name'), 'Ceft');
    await user.click(await screen.findByText('Ceftriaxone'));

    await waitFor(() => expect(screen.getByLabelText('Food Timing')).toBeDisabled());
    expect(screen.getByLabelText('Food Timing')).toHaveValue('');

    const payload = await fillAndSave(user);
    expect(payload).toMatchObject({ medicineName: 'Ceftriaxone', type: 'INJECTION' });
    expect(payload.foodTiming).toBeNull();
  });

  it('offers the question again when the order goes back to an oral medicine', async () => {
    const user = userEvent.setup();
    await openMedicineForm(user);

    await user.selectOptions(typeSelect(), 'INJECTION');
    expect(screen.getByLabelText('Food Timing')).toBeDisabled();

    await user.selectOptions(typeSelect(), 'TABLET');

    const reEnabled = screen.getByLabelText('Food Timing');
    expect(reEnabled).toBeEnabled();
    expect(screen.queryByText('Not applicable for injections.')).not.toBeInTheDocument();

    await user.selectOptions(reEnabled, 'WITH_FOOD');
    const payload = await fillAndSave(user);
    expect(payload.foodTiming).toBe('WITH_FOOD');
  });
});
