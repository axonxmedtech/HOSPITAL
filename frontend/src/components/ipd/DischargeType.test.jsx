import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDischargeModal } from './DischargeType';

describe('ConfirmDischargeModal', () => {
  it('sends a normal discharge by default', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    render(<ConfirmDischargeModal onClose={() => {}} onConfirm={onConfirm} />);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm Discharge' }));
    await waitFor(() =>
      expect(onConfirm).toHaveBeenCalledWith({ dischargeType: 'NORMAL', dischargeNote: null })
    );
  });

  it('needs the cause of death before an expired discharge', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    render(<ConfirmDischargeModal onClose={() => {}} onConfirm={onConfirm} />);
    await userEvent.selectOptions(screen.getByLabelText('Discharge type'), 'EXPIRED');
    const button = screen.getByRole('button', { name: 'Confirm Discharge' });
    expect(button).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/cause of death/), '10:30, septic shock');
    await userEvent.click(button);
    await waitFor(() =>
      expect(onConfirm).toHaveBeenCalledWith({
        dischargeType: 'EXPIRED',
        dischargeNote: '10:30, septic shock',
      })
    );
  });

  it('explains that unpaid bills block a normal discharge but not a death', async () => {
    render(
      <ConfirmDischargeModal owing="BIL1 (IPD) ₹500" onClose={() => {}} onConfirm={vi.fn()} />
    );
    expect(screen.getByText(/refused until every bill is settled/)).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Discharge type'), 'EXPIRED');
    expect(screen.getByText(/bill stays open/)).toBeInTheDocument();
  });

  it('starts from the type the doctor planned', () => {
    render(
      <ConfirmDischargeModal
        initialType="REFERRED"
        initialNote="Sassoon General"
        onClose={() => {}}
        onConfirm={vi.fn()}
      />
    );
    expect(screen.getByLabelText('Discharge type')).toHaveValue('REFERRED');
    expect(screen.getByLabelText(/Referred to/)).toHaveValue('Sassoon General');
  });
});
