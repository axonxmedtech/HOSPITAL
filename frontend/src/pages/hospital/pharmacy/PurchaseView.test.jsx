import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import purchaseApi from '../../../services/pharmacy/purchaseApi';
import PurchaseView from './PurchaseView';

vi.mock('../../../services/pharmacy/purchaseApi', () => ({
  default: {
    getAll: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    postInvoice: vi.fn(),
    cancel: vi.fn(),
    recordPayment: vi.fn(),
  },
}));
vi.mock('../../../services/pharmacy/suppliersApi', () => ({
  default: { getAll: vi.fn().mockResolvedValue({ content: [] }) },
}));
const toast = { success: vi.fn(), error: vi.fn() };
vi.mock('../../../context/ToastContext', () => ({ useToast: () => toast }));

const posted = {
  id: 1,
  invoiceNumber: 'INV-9',
  invoiceDate: '2026-11-20',
  supplier: { supplierName: 'Mahaveer' },
  supplierId: 3,
  totalAmount: 672,
  paidAmount: 500,
  postingStatus: 'POSTED',
  paymentStatus: 'PARTIAL',
};
const cancelled = {
  ...posted,
  id: 2,
  invoiceNumber: 'INV-10',
  postingStatus: 'CANCELLED',
  paidAmount: 0,
  cancelReason: 'Wrong supplier',
};

beforeEach(() => {
  vi.clearAllMocks();
  purchaseApi.getAll.mockResolvedValue({ content: [posted, cancelled], totalElements: 2 });
});

describe('Purchases', () => {
  it('shows what is still due and counts only that as payable', async () => {
    render(<PurchaseView />);
    expect(await screen.findByText(/due ₹172.00/)).toBeInTheDocument();
    expect(screen.getByText('Wrong supplier')).toBeInTheDocument();
    expect(screen.getByText('₹172')).toBeInTheDocument(); // Pending Payables card
  });

  it('records a supplier payment of the balance', async () => {
    purchaseApi.recordPayment.mockResolvedValue({});
    render(<PurchaseView />);
    fireEvent.click(await screen.findByRole('button', { name: 'Record payment' }));
    // the dialog's submit button (the row's button is still behind it)
    fireEvent.click(screen.getAllByRole('button', { name: 'Record payment' }).at(-1));
    await waitFor(() =>
      expect(purchaseApi.recordPayment).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ amount: 172 })
      )
    );
  });

  it('cancels a posted purchase only with a reason', async () => {
    purchaseApi.cancel.mockResolvedValue({});
    render(<PurchaseView />);
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel' }));
    fireEvent.change(screen.getByPlaceholderText('Reason (kept in the audit log)'), {
      target: { value: 'Duplicate entry' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Proceed|Confirm|Yes/ }));
    await waitFor(() => expect(purchaseApi.cancel).toHaveBeenCalledWith(1, 'Duplicate entry'));
  });
});
