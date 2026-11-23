import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import inventoryApi from '../../../services/pharmacy/inventoryApi';
import salesApi from '../../../services/pharmacy/salesApi';
import BillingCounterView from './BillingCounterView';

vi.mock('../../../services/pharmacy/inventoryApi', () => ({
  default: { searchBatches: vi.fn(), searchAvailableBatches: vi.fn() },
}));
vi.mock('../../../services/pharmacy/salesApi', () => ({ default: { create: vi.fn() } }));
vi.mock('../../../services/hospitalService', () => ({
  default: { getPendingPrescriptions: vi.fn().mockResolvedValue([]) },
}));
vi.mock('../../../services/authService', () => ({
  default: { getCurrentUser: () => ({ userId: 42, role: 'PHARMACIST', modules: ['PHARMACY'] }) },
}));
const toast = { success: vi.fn(), error: vi.fn() };
vi.mock('../../../context/ToastContext', () => ({ useToast: () => toast }));

const HELD_SLOT = 'hms.pharmacy.heldBills.42';
const held = {
  id: 'h1',
  heldAt: new Date().toISOString(),
  label: 'Ramesh',
  patientSearch: 'Ramesh',
  billDiscount: 0,
  paymentMethod: 'UPI',
  total: 200,
  billItems: [
    {
      id: 1,
      batchId: 7,
      medicineId: 3,
      name: 'Dolo 650',
      batch: 'B1',
      expiry: '2027-11',
      qty: 2,
      price: 100,
      taxPercent: 12,
      discount: 0,
      total: 200,
    },
  ],
};

// A complete in-memory Storage for these tests (the shared test setup stubs only part of it).
const memory = new Map();
Object.defineProperty(window, 'localStorage', {
  configurable: true,
  value: {
    getItem: (k) => (memory.has(k) ? memory.get(k) : null),
    setItem: (k, v) => memory.set(k, String(v)),
    removeItem: (k) => memory.delete(k),
    clear: () => memory.clear(),
  },
});

beforeEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
});

describe('Billing counter', () => {
  it('resumes a held bill with its items and total', async () => {
    window.localStorage.setItem(HELD_SLOT, JSON.stringify([held]));
    render(<BillingCounterView />);
    fireEvent.click(screen.getByRole('button', { name: 'Held bills (1)' }));
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }));
    expect(await screen.findByText('Dolo 650')).toBeInTheDocument();
    expect(screen.getAllByText('₹200.00').length).toBeGreaterThan(0);
    expect(JSON.parse(window.localStorage.getItem(HELD_SLOT))).toHaveLength(0);
  });

  it('holds the current bill with F4 and clears the counter', async () => {
    window.localStorage.setItem(HELD_SLOT, JSON.stringify([held]));
    render(<BillingCounterView />);
    fireEvent.click(screen.getByRole('button', { name: 'Held bills (1)' }));
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }));
    await screen.findByText('Dolo 650');

    fireEvent.keyDown(window, { key: 'F4' });
    await waitFor(() => expect(screen.queryByText('Dolo 650')).not.toBeInTheDocument());
    expect(JSON.parse(window.localStorage.getItem(HELD_SLOT))).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Held bills (1)' })).toBeInTheDocument();
  });

  it('asks before clearing a bill that has items', async () => {
    window.localStorage.setItem(HELD_SLOT, JSON.stringify([held]));
    render(<BillingCounterView />);
    fireEvent.click(screen.getByRole('button', { name: 'Held bills (1)' }));
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }));
    await screen.findByText('Dolo 650');

    fireEvent.click(screen.getByRole('button', { name: 'Clear Cart' }));
    expect(screen.getByText('Clear this bill?')).toBeInTheDocument();
    expect(screen.getByText('Dolo 650')).toBeInTheDocument();
  });

  it('completes the sale with Ctrl+Enter', async () => {
    salesApi.create.mockResolvedValue({ id: 9, billNumber: 'PH/26-27/00009', netAmount: 200 });
    window.localStorage.setItem(HELD_SLOT, JSON.stringify([held]));
    render(<BillingCounterView />);
    fireEvent.click(screen.getByRole('button', { name: 'Held bills (1)' }));
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }));
    await screen.findByText('Dolo 650');

    fireEvent.keyDown(window, { key: 'Enter', ctrlKey: true });
    await waitFor(() => expect(salesApi.create).toHaveBeenCalledTimes(1));
    expect(salesApi.create.mock.calls[0][0].items[0]).toMatchObject({
      medicineBatchId: 7,
      quantity: 2,
    });
    expect(inventoryApi).toBeDefined();
  });
});
