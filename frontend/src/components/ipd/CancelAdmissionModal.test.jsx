import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import CancelAdmissionModal from './CancelAdmissionModal';

describe('CancelAdmissionModal', () => {
  it('asks for a reason before cancelling', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    render(
      <CancelAdmissionModal
        isOpen
        paidAmount={0}
        isAdmin={false}
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Cancel admission' }));
    expect(
      await screen.findByText('Enter why the admission is being cancelled')
    ).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Reason *'), { target: { value: 'Wrong patient' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel admission' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('Wrong patient', null));
  });

  it('tells reception only the admin can cancel once money was collected', () => {
    render(
      <CancelAdmissionModal
        isOpen
        paidAmount={500}
        isAdmin={false}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />
    );
    expect(screen.getByText(/Only the hospital admin can cancel it/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel admission' })).not.toBeInTheDocument();
  });

  it('lets the admin choose how the money is refunded', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    render(
      <CancelAdmissionModal
        isOpen
        paidAmount={500}
        isAdmin
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />
    );
    fireEvent.change(screen.getByLabelText('Reason *'), { target: { value: 'Duplicate' } });
    fireEvent.change(screen.getByLabelText('Refund ₹500 by'), { target: { value: 'UPI' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel admission' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('Duplicate', 'UPI'));
  });
});
