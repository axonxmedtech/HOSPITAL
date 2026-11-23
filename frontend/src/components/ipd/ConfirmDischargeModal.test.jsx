import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDischargeModal } from './DischargeType';

describe('ConfirmDischargeModal', () => {
  it('asks how unused advance is returned and sends it', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    render(<ConfirmDischargeModal unusedAdvance={2500} onClose={vi.fn()} onConfirm={onConfirm} />);
    expect(screen.getByText(/₹2500 of the advance is unused/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Return it by'), { target: { value: 'UPI' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Discharge' }));
    await waitFor(() =>
      expect(onConfirm).toHaveBeenCalledWith({
        dischargeType: 'NORMAL',
        dischargeNote: null,
        refundMode: 'UPI',
      })
    );
  });

  it('sends no refund mode when nothing is owed back', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    render(<ConfirmDischargeModal onClose={vi.fn()} onConfirm={onConfirm} />);
    expect(screen.queryByLabelText('Return it by')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Discharge' }));
    await waitFor(() =>
      expect(onConfirm).toHaveBeenCalledWith({ dischargeType: 'NORMAL', dischargeNote: null })
    );
  });
});
