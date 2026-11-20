import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const success = vi.fn();
const toastError = vi.fn();
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success, error: toastError }),
}));
vi.mock('../../services/hospitalService', () => ({
  default: { cancelOpd: vi.fn(), changeOpdDoctor: vi.fn(), getDoctors: vi.fn() },
}));

import hospitalService from '../../services/hospitalService';
import OpdVisitActions from './OpdVisitActions';

const waiting = {
  id: 7,
  caseId: 'OPD-7',
  status: 'QUEUED',
  patient: { name: 'Ramesh' },
  doctor: { id: 1, name: 'Dr Asha' },
};

describe('OpdVisitActions', () => {
  beforeEach(() => vi.clearAllMocks());

  it('offers nothing once the doctor has seen the patient', () => {
    const { container } = render(<OpdVisitActions opd={{ ...waiting, status: 'COMPLETED' }} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('cancels a waiting visit with the chosen reason and reports the server message', async () => {
    hospitalService.cancelOpd.mockResolvedValue({ message: 'OPD visit OPD-7 cancelled.' });
    const onChanged = vi.fn();
    render(<OpdVisitActions opd={waiting} onChanged={onChanged} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel visit' }));
    await userEvent.selectOptions(screen.getByLabelText('Reason'), 'Registered twice by mistake');
    await userEvent.click(screen.getAllByRole('button', { name: 'Cancel visit' }).at(-1));
    await waitFor(() =>
      expect(hospitalService.cancelOpd).toHaveBeenCalledWith(
        7,
        'Registered twice by mistake',
        'CASH'
      )
    );
    expect(success).toHaveBeenCalledWith('OPD visit OPD-7 cancelled.');
    expect(onChanged).toHaveBeenCalled();
  });

  it('tells reception that a paid visit needs the admin to refund it', async () => {
    render(<OpdVisitActions opd={{ ...waiting, bill: { paidAmount: 600 } }} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel visit' }));
    expect(screen.getByText(/stays until the admin refunds it/)).toBeInTheDocument();
  });

  it('moves the patient to another doctor, never listing the current one', async () => {
    hospitalService.getDoctors.mockResolvedValue({
      content: [
        { id: 1, name: 'Dr Asha' },
        { id: 2, name: 'Dr Ravi', specialization: 'Ortho' },
      ],
    });
    hospitalService.changeOpdDoctor.mockResolvedValue({});
    render(<OpdVisitActions opd={waiting} />);
    await userEvent.click(screen.getByRole('button', { name: 'Change doctor' }));
    const select = await screen.findByLabelText('New doctor');
    expect(screen.queryByRole('option', { name: /Dr Asha/ })).not.toBeInTheDocument();
    await userEvent.selectOptions(select, '2');
    await userEvent.click(screen.getAllByRole('button', { name: 'Change doctor' }).at(-1));
    await waitFor(() => expect(hospitalService.changeOpdDoctor).toHaveBeenCalledWith(7, 2));
    expect(success).toHaveBeenCalledWith('Moved to Dr Ravi');
  });

  it('explains when there is no other doctor to move to', async () => {
    hospitalService.getDoctors.mockResolvedValue({ content: [{ id: 1, name: 'Dr Asha' }] });
    render(<OpdVisitActions opd={waiting} />);
    await userEvent.click(screen.getByRole('button', { name: 'Change doctor' }));
    expect(await screen.findByText(/no other doctor to move to/)).toBeInTheDocument();
  });
});
