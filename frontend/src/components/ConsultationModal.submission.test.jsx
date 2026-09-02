import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const toastError = vi.fn();
const toastSuccess = vi.fn();

vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError, info: vi.fn() }),
}));

vi.mock('../services/authService', () => ({
  default: {
    getCurrentUser: () => ({
      role: 'DOCTOR',
      name: 'Dr Demo',
      modules: ['OPD', 'BILLING', 'MEDICAL_INVENTORY'],
      userId: 1,
    }),
  },
}));

vi.mock('../services/vitalsService', () => ({
  default: { enabled: vi.fn().mockResolvedValue([]) },
}));

vi.mock('../services/hospitalService', () => ({
  default: {
    submitConsultation: vi.fn(),
    getInventoryMedicines: vi.fn().mockResolvedValue([]),
    getHospitalFees: vi.fn().mockResolvedValue([]),
    getCustomFees: vi.fn().mockResolvedValue([]),
    getHospitalInventory: vi.fn().mockResolvedValue([]),
    getHospitalServices: vi.fn().mockResolvedValue([]),
    getInClinicPresets: vi.fn().mockResolvedValue([]),
    getPrescriptionPresets: vi.fn().mockResolvedValue([]),
    getNotePresets: vi.fn().mockResolvedValue([]),
    getConsultationNotePresets: vi.fn().mockResolvedValue([]),
    getPatientConsultationDetails: vi
      .fn()
      .mockResolvedValue({
        patient: { name: 'Ravi Kumar', age: 46 },
        opdHistory: [],
        ipdHistory: [],
      }),
    searchMedicines: vi.fn().mockResolvedValue([]),
  },
}));

import hospitalService from '../services/hospitalService';
import ConsultationModal from './ConsultationModal';

/**
 * What the doctor's last click actually sends, and what they see when it fails.
 *
 * <p>The backend suite proves the server handles this payload; these prove the payload leaving
 * the browser is the one it was tested with, and that a refused consultation says so instead of
 * quietly emptying the form the doctor just spent five minutes filling.
 */
describe('ConsultationModal — submission', () => {
  const opd = { id: 42, patient: { id: 7, publicId: 'ppub-7' } };
  const patient = { id: 7, publicId: 'ppub-7', name: 'Ravi Kumar' };

  const open = () =>
    render(
      <ConsultationModal
        isOpen
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        appointment={null}
        patient={patient}
        opd={opd}
      />
    );

  const openPrescriptionTab = async (user) => {
    await user.click(screen.getByRole('button', { name: /^Prescription \(/ }));
  };

  /** The catalogue field only yields a name when one is picked from the list, so pick one. */
  const addMedicine = async (user, { name, dosage, duration, frequency = true }) => {
    if (!screen.queryByPlaceholderText('Search medicine from catalog...')) {
      await openPrescriptionTab(user);
    }
    hospitalService.searchMedicines.mockResolvedValue([
      { id: Math.floor(Math.random() * 10000), name, type: 'Tablet' },
    ]);
    await user.type(screen.getByPlaceholderText('Search medicine from catalog...'), name);
    await user.click(await screen.findByRole('option', { name: new RegExp(name) }));

    await user.type(screen.getByPlaceholderText('Dosage (e.g., 500mg)'), dosage);
    await user.type(screen.getByPlaceholderText('Duration (e.g., 5 Days)'), duration);
    if (frequency) {
      const morning = screen.getByLabelText('Morning dose');
      await user.clear(morning);
      await user.type(morning, '1');
    }
    await user.click(screen.getByRole('button', { name: '+ Add Medicine' }));
  };

  const submit = async (user) =>
    user.click(screen.getByRole('button', { name: /Complete Consultation/ }));

  const payloadOf = () => hospitalService.submitConsultation.mock.calls[0][0];

  beforeEach(() => {
    vi.clearAllMocks();
    hospitalService.getInventoryMedicines.mockResolvedValue([]);
    hospitalService.submitConsultation.mockResolvedValue({ message: 'ok' });
  });

  it('sends the case, the diagnosis and a complete medicine row', async () => {
    const user = userEvent.setup();
    open();

    await user.type(screen.getByPlaceholderText('Enter diagnosis...'), 'Viral fever');
    await addMedicine(user, { name: 'Paracetamol', dosage: '500mg', duration: '5 Days' });
    await submit(user);

    await waitFor(() => expect(hospitalService.submitConsultation).toHaveBeenCalledTimes(1));
    const payload = payloadOf();
    expect(payload.opdId).toBe(42);
    expect(payload.diagnosis).toBe('Viral fever');
    expect(payload.prescription).toHaveLength(1);
    expect(payload.prescription[0]).toMatchObject({
      medicineName: 'Paracetamol',
      dosage: '500mg',
      duration: '5 Days',
    });
    expect(payload.prescription[0].frequency).toBeTruthy();
    // The shapes the server is tested against must all be present, even when empty.
    expect(payload.administeredItems).toEqual([]);
    expect(Array.isArray(payload.charges)).toBe(true);
    expect(payload.hospitalInventoryItems).toEqual([]);
    expect(payload.labTests).toEqual([]);
  });

  it('serialises several medicines in order', async () => {
    const user = userEvent.setup();
    open();

    await addMedicine(user, { name: 'Paracetamol', dosage: '500mg', duration: '5 Days' });
    await addMedicine(user, { name: 'Azithromycin', dosage: '250mg', duration: '3 Days' });
    await submit(user);

    await waitFor(() => expect(hospitalService.submitConsultation).toHaveBeenCalled());
    expect(payloadOf().prescription.map((p) => p.medicineName)).toEqual([
      'Paracetamol',
      'Azithromycin',
    ]);
  });

  it('serialises an administered medicine with the fields the server reads', async () => {
    hospitalService.getInventoryMedicines.mockResolvedValue([
      { id: 91, name: 'Saline 500ml', stockQuantity: 20, unitPrice: 40, isActive: true },
    ]);
    const user = userEvent.setup();
    open();

    const stockSearch = await screen.findByPlaceholderText(/Search active clinical stock/);
    await user.type(stockSearch, 'Saline');
    await user.click(await screen.findByRole('button', { name: /Saline 500ml/ }));

    // Given at the clinic, so the row needs a frequency like any other -- "As Per Required" is
    // what a nurse ticks for a one-off.
    await user.click(screen.getAllByLabelText('As Per Required')[0]);
    await submit(user);

    await waitFor(() => expect(hospitalService.submitConsultation).toHaveBeenCalled());
    const [item] = payloadOf().administeredItems;
    expect(item).toMatchObject({ medicineId: 91, medicineName: 'Saline 500ml', quantity: 1 });
    expect(item).toHaveProperty('dosage');
    expect(item).toHaveProperty('frequency');
    expect(item).toHaveProperty('duration');
    expect(item).toHaveProperty('instructions');
  });

  // -- refusals before the request ---------------------------------------------

  it('will not submit a medicine the doctor typed but never added', async () => {
    hospitalService.searchMedicines.mockResolvedValue([
      { id: 5, name: 'Paracetamol 500', type: 'Tablet' },
    ]);
    const user = userEvent.setup();
    open();

    await openPrescriptionTab(user);
    await user.type(screen.getByPlaceholderText('Search medicine from catalog...'), 'Paracet');
    await user.click(await screen.findByRole('option', { name: /Paracetamol 500/ }));
    await submit(user);

    expect(hospitalService.submitConsultation).not.toHaveBeenCalled();
    expect(toastError).toHaveBeenCalledWith(
      "Please click '+ Add Medicine' or clear the medicine fields before submitting."
    );
  });

  /**
   * The catalogue field reports a name only once one is picked from the list. Typing alone leaves
   * the parent's medicineName empty, which is why Add stays disabled -- so no nameless row can
   * reach the server. Worth pinning: it is also why a doctor who types a medicine and never picks
   * it submits with no prescription and no warning (see the P1 raised with this test).
   */
  it('cannot add a medicine that was typed but never picked from the catalogue', async () => {
    const user = userEvent.setup();
    open();

    await openPrescriptionTab(user);
    await user.type(
      screen.getByPlaceholderText('Search medicine from catalog...'),
      'Some Unlisted Syrup'
    );
    await user.type(screen.getByPlaceholderText('Dosage (e.g., 500mg)'), '10ml');
    await user.type(screen.getByPlaceholderText('Duration (e.g., 5 Days)'), '3 Days');
    const morning = screen.getByLabelText('Morning dose');
    await user.clear(morning);
    await user.type(morning, '1');
    expect(screen.getByRole('button', { name: '+ Add Medicine' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /^Prescription \(0\)/ })).toBeInTheDocument();
  });

  it('will not submit a medicine with no frequency, and says which one', async () => {
    const user = userEvent.setup();
    open();

    await addMedicine(user, {
      name: 'Paracetamol',
      dosage: '500mg',
      duration: '5 Days',
      frequency: false,
    });

    // Refused where the mistake was made, so the row never joins the prescription.
    expect(toastError).toHaveBeenCalledWith(expect.stringContaining('Set a frequency'));
    expect(screen.getByRole('button', { name: /^Prescription \(0\)/ })).toBeInTheDocument();

    // And the half-finished row still in the form blocks the submit, rather than being dropped.
    await submit(user);
    expect(hospitalService.submitConsultation).not.toHaveBeenCalled();
    expect(toastError).toHaveBeenCalledWith(
      "Please click '+ Add Medicine' or clear the medicine fields before submitting."
    );
  });

  // -- refusals from the server -------------------------------------------------

  it('shows what the server refused, not a generic failure', async () => {
    hospitalService.submitConsultation.mockRejectedValue({
      response: { status: 400, data: { error: 'Insufficient stock for: Paracetamol 500' } },
    });
    const user = userEvent.setup();
    open();

    await addMedicine(user, { name: 'Paracetamol', dosage: '500mg', duration: '5 Days' });
    await submit(user);

    await waitFor(() =>
      expect(toastError).toHaveBeenCalledWith('Insufficient stock for: Paracetamol 500')
    );
    expect(toastSuccess).not.toHaveBeenCalled();
  });

  it('stays safe when the server fails without saying anything useful', async () => {
    hospitalService.submitConsultation.mockRejectedValue({
      response: { status: 500, data: {} },
    });
    const user = userEvent.setup();
    open();

    await addMedicine(user, { name: 'Paracetamol', dosage: '500mg', duration: '5 Days' });
    await submit(user);

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Failed to submit consultation'));
    expect(toastSuccess).not.toHaveBeenCalled();
  });

  it('lets the doctor try again, with everything they typed still there', async () => {
    hospitalService.submitConsultation.mockRejectedValueOnce({
      response: { status: 400, data: { error: 'Insufficient stock for: Paracetamol 500' } },
    });
    const user = userEvent.setup();
    open();

    await user.type(screen.getByPlaceholderText('Enter diagnosis...'), 'Viral fever');
    await addMedicine(user, { name: 'Paracetamol', dosage: '500mg', duration: '5 Days' });
    await submit(user);

    await waitFor(() => expect(toastError).toHaveBeenCalled());

    // The button comes back, and nothing the doctor entered was thrown away.
    const button = await screen.findByRole('button', { name: /Complete Consultation/ });
    expect(button).not.toBeDisabled();
    expect(screen.getByRole('button', { name: /^Prescription \(1\)/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^Clinical Notes/ }));
    expect(screen.getByPlaceholderText('Enter diagnosis...')).toHaveValue('Viral fever');

    hospitalService.submitConsultation.mockResolvedValue({ message: 'ok' });
    await user.click(button);
    await waitFor(() => expect(hospitalService.submitConsultation).toHaveBeenCalledTimes(2));
    expect(hospitalService.submitConsultation.mock.calls[1][0].diagnosis).toBe('Viral fever');
  });
});
