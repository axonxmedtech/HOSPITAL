import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const success = vi.fn();
const toastError = vi.fn();
vi.mock('../../context/ToastContext', () => ({ useToast: () => ({ success, error: toastError }) }));
vi.mock('../../services/nurseService', () => ({
  default: {
    searchStock: vi.fn(),
    giveExtraItem: vi.fn(),
    getMedicationAccess: vi.fn(),
    setMedicationAccess: vi.fn(),
  },
}));

import nurseService from '../../services/nurseService';
import GiveExtraItem from './GiveExtraItem';
import MedicationAccessCard from './MedicationAccessCard';
import { StockBadge } from './StockPicker';

const saline = {
  source: 'HOSPITAL_ITEM',
  itemName: 'Saline NS 500ml',
  name: 'Saline NS 500ml',
  inventory: 'Hospital inventory',
  available: 2,
  minLevel: 3,
  low: true,
  outOfStock: false,
  unitPrice: 60,
};

describe('stock badge', () => {
  it('says low, out, or how many are in stock', () => {
    const { rerender } = render(<StockBadge option={saline} />);
    expect(screen.getByText('LOW · 2 left')).toBeInTheDocument();
    rerender(<StockBadge option={{ ...saline, outOfStock: true, available: 0 }} />);
    expect(screen.getByText('OUT OF STOCK')).toBeInTheDocument();
    rerender(<StockBadge option={{ ...saline, low: false, available: 9 }} />);
    expect(screen.getByText('9 in stock')).toBeInTheDocument();
  });
});

describe('GiveExtraItem', () => {
  beforeEach(() => vi.clearAllMocks());

  it('gives an item from stock, and refuses more than is there', async () => {
    nurseService.searchStock.mockResolvedValue([saline]);
    nurseService.giveExtraItem.mockResolvedValue({});
    const onDone = vi.fn();
    render(<GiveExtraItem admissionId={5} onDone={onDone} onCancel={() => {}} />);
    await userEvent.click(screen.getByLabelText('Item from stock'));
    await userEvent.click(await screen.findByRole('option', { name: /Saline NS 500ml/ }));
    const qty = screen.getByLabelText('Quantity');
    await userEvent.clear(qty);
    await userEvent.type(qty, '3');
    expect(screen.getByText(/Only 2 in stock/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Give' })).toBeDisabled();
    await userEvent.clear(qty);
    await userEvent.type(qty, '1');
    await userEvent.click(screen.getByRole('button', { name: 'Give' }));
    await waitFor(() =>
      expect(nurseService.giveExtraItem).toHaveBeenCalledWith(
        expect.objectContaining({
          ipdAdmissionId: 5,
          source: 'HOSPITAL_ITEM',
          name: 'Saline NS 500ml',
          quantity: 1,
          ownSupply: false,
        })
      )
    );
    expect(success).toHaveBeenCalledWith('Saline NS 500ml given and added to the bill');
    expect(onDone).toHaveBeenCalled();
  });

  it("records a typed item as the patient's own supply", async () => {
    nurseService.searchStock.mockResolvedValue([]);
    nurseService.giveExtraItem.mockResolvedValue({});
    render(<GiveExtraItem admissionId={5} onDone={() => {}} onCancel={() => {}} />);
    await userEvent.type(screen.getByLabelText(/Or type a name/), 'Ointment from home');
    await userEvent.click(screen.getByRole('button', { name: 'Give' }));
    await waitFor(() =>
      expect(nurseService.giveExtraItem).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Ointment from home', source: null, ownSupply: true })
      )
    );
  });
});

describe('MedicationAccessCard', () => {
  it('saves the roles the admin allows', async () => {
    nurseService.getMedicationAccess.mockResolvedValue({
      roles: ['NURSE', 'DOCTOR', 'RECEPTIONIST'],
      allRoles: ['NURSE', 'NURSE_INCHARGE', 'DOCTOR', 'HOSPITAL_ADMIN', 'RECEPTIONIST'],
    });
    nurseService.setMedicationAccess.mockResolvedValue({ roles: ['NURSE', 'DOCTOR'] });
    render(<MedicationAccessCard />);
    const reception = await screen.findByLabelText('Reception');
    expect(reception).toBeChecked();
    await userEvent.click(reception);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(nurseService.setMedicationAccess).toHaveBeenCalledWith(['NURSE', 'DOCTOR'])
    );
  });
});
