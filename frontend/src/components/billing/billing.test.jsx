import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const toastError = vi.fn();
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: toastError }),
}));
vi.mock('../../services/hospitalService', () => ({
  default: { payBilling: vi.fn(), discountBill: vi.fn(), refundBill: vi.fn(), cancelBill: vi.fn() },
}));
let currentUser = null;
vi.mock('../../services/authService', () => ({
  default: { getCurrentUser: () => currentUser },
}));

import hospitalService from '../../services/hospitalService';
import { CollectPaymentModal, RefundModal } from './BillActionModals';
import OpdBillCell from './OpdBillCell';

const bill = {
  id: 9,
  customId: 'BIL9',
  amount: 600,
  grossAmount: 600,
  paidAmount: 0,
  balance: 600,
  status: 'PENDING',
};

describe('OpdBillCell', () => {
  beforeEach(() => {
    currentUser = { role: 'RECEPTIONIST', billingHandler: 'RECEPTIONIST', modules: ['BILLING'] };
  });

  it('shows the bill and a Collect button to whoever handles billing', () => {
    render(<OpdBillCell opd={{ id: 1, bill, patient: { name: 'A' } }} />);
    expect(screen.getByText('₹600')).toBeInTheDocument();
    expect(screen.getByText('Unpaid')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Collect' })).toBeInTheDocument();
  });

  it('shows status but no Collect to a role that does not bill', () => {
    currentUser = { role: 'DOCTOR', billingHandler: 'RECEPTIONIST', modules: ['BILLING'] };
    render(<OpdBillCell opd={{ id: 1, bill, patient: { name: 'A' } }} />);
    expect(screen.getByText('Unpaid')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Collect' })).not.toBeInTheDocument();
  });

  it('explains a missing bill rather than showing nothing', () => {
    render(<OpdBillCell opd={{ id: 1, patient: { name: 'A' } }} />);
    expect(screen.getByText('After consultation')).toBeInTheDocument();
  });

  it('marks a settled bill Paid', () => {
    render(
      <OpdBillCell
        opd={{ id: 1, bill: { ...bill, paidAmount: 600, balance: 0, status: 'PAID' } }}
      />
    );
    expect(screen.getByText('Paid')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Collect' })).not.toBeInTheDocument();
  });
});

describe('CollectPaymentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hospitalService.payBilling.mockResolvedValue({ balance: 400 });
  });

  it('takes a part payment by card', async () => {
    const onDone = vi.fn();
    render(<CollectPaymentModal bill={bill} onClose={() => {}} onDone={onDone} />);
    const amount = screen.getByLabelText('Amount received (₹)');
    await userEvent.clear(amount);
    await userEvent.type(amount, '200');
    await userEvent.click(screen.getByRole('radio', { name: 'Card' }));
    await userEvent.click(screen.getByRole('button', { name: /Collect ₹200 \(part\)/ }));
    await waitFor(() =>
      expect(hospitalService.payBilling).toHaveBeenCalledWith(9, {
        amount: 200,
        mode: 'CARD',
        reference: null,
      })
    );
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ collected: 200, mode: 'CARD' }));
  });

  it('will not submit UPI without a UTR', async () => {
    render(<CollectPaymentModal bill={bill} onClose={() => {}} onDone={() => {}} />);
    await userEvent.click(screen.getByRole('radio', { name: 'UPI / Online' }));
    expect(screen.getByRole('button', { name: /Collect ₹600/ })).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/UTR/), '4321');
    expect(screen.getByRole('button', { name: /Collect ₹600/ })).toBeEnabled();
  });
});

describe('RefundModal', () => {
  it('defaults to reducing the bill (patient overcharged)', async () => {
    hospitalService.refundBill.mockResolvedValue({});
    render(
      <RefundModal
        bill={{ ...bill, paidAmount: 600, balance: 0 }}
        onClose={() => {}}
        onDone={() => {}}
      />
    );
    await userEvent.type(screen.getByLabelText(/Amount to refund/), '100');
    await userEvent.type(screen.getByLabelText('Reason *'), 'Wrong fee');
    await userEvent.click(screen.getByRole('button', { name: 'Record Refund' }));
    await waitFor(() =>
      expect(hospitalService.refundBill).toHaveBeenCalledWith(9, 100, 'CASH', 'Wrong fee', true)
    );
  });
});
