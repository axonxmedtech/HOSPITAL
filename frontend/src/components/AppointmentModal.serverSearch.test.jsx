import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * The booking form used to filter a preloaded list of "all patients" — at most 1000, so in a
 * bigger hospital an older patient could not be booked at all — and loaded the doctor's whole
 * appointment history to grey out taken slots. Now both ask the server for exactly what is needed.
 */

vi.spyOn(console, 'error').mockImplementation(() => {});
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

const hospitalService = {
  createAppointment: vi.fn(),
  getPatients: vi.fn(),
  getAppointmentsByDoctor: vi.fn().mockResolvedValue([]),
};
vi.mock('../services/hospitalService', () => ({ default: hospitalService }));

const { default: AppointmentModal } = await import('./AppointmentModal');

const doctors = [{ id: 1, name: 'Dr Mandal' }];

const renderModal = () =>
  render(<AppointmentModal isOpen onClose={vi.fn()} onSuccess={vi.fn()} doctors={doctors} />);

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.getAppointmentsByDoctor.mockResolvedValue([]);
});

describe('AppointmentModal — server-side lookups', () => {
  it('searches the whole hospital for the typed patient, not a preloaded page', async () => {
    const user = userEvent.setup();
    hospitalService.getPatients.mockResolvedValue({
      content: [{ id: 1501, name: 'Ravi Kulkarni', phone: '9800001501', age: 61, gender: 'MALE' }],
    });
    renderModal();

    await user.type(screen.getByPlaceholderText(/Search by name or phone/i), 'Ravi');

    await waitFor(() => expect(hospitalService.getPatients).toHaveBeenCalledWith('Ravi', 0, 10));
    expect(await screen.findByText('Ravi Kulkarni')).toBeInTheDocument();
  });

  it('asks for at least two letters before searching', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByPlaceholderText(/Search by name or phone/i), 'R');

    expect(await screen.findByText(/Type at least 2 letters/i)).toBeInTheDocument();
    expect(hospitalService.getPatients).not.toHaveBeenCalled();
  });

  it('loads only the chosen day to grey out taken slots', async () => {
    const user = userEvent.setup();
    renderModal();
    const when = new Date();
    when.setDate(when.getDate() + 1);
    const iso = `${when.getFullYear()}-${String(when.getMonth() + 1).padStart(2, '0')}-${String(
      when.getDate()
    ).padStart(2, '0')}`;

    await user.selectOptions(screen.getAllByLabelText('Year').at(-1), String(when.getFullYear()));
    await user.selectOptions(
      screen.getAllByLabelText('Month').at(-1),
      String(when.getMonth() + 1).padStart(2, '0')
    );
    await user.selectOptions(
      screen.getAllByLabelText('Day').at(-1),
      String(when.getDate()).padStart(2, '0')
    );

    await waitFor(() =>
      expect(hospitalService.getAppointmentsByDoctor).toHaveBeenCalledWith(1, 'date', iso)
    );
  });
});
