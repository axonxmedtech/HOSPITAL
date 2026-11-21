import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import inventoryApi from '../services/pharmacy/inventoryApi';
import StockAdjustmentModal from './StockAdjustmentModal';

vi.mock('../services/pharmacy/inventoryApi', () => ({ default: { adjustStock: vi.fn() } }));

beforeEach(() => vi.clearAllMocks());

const open = () =>
  render(
    <StockAdjustmentModal
      isOpen
      onClose={vi.fn()}
      onAdjustmentComplete={vi.fn()}
      item={{
        id: 7,
        batchNumber: 'B1',
        currentQuantity: 10,
        medicine: { medicineName: 'Cetirizine' },
      }}
    />
  );

describe('Stock correction', () => {
  it('asks for a reason before correcting stock', async () => {
    open();
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '-2' } });
    fireEvent.submit(screen.getByRole('spinbutton').closest('form'));
    expect(await screen.findByText(/Give a reason for the correction/)).toBeInTheDocument();
    expect(inventoryApi.adjustStock).not.toHaveBeenCalled();
  });

  it('sends the quantity and the reason', async () => {
    inventoryApi.adjustStock.mockResolvedValue({});
    open();
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '-2' } });
    fireEvent.change(screen.getByPlaceholderText(/Reason for adjustment/), {
      target: { value: '  damaged strip ' },
    });
    fireEvent.submit(screen.getByRole('spinbutton').closest('form'));
    await waitFor(() =>
      expect(inventoryApi.adjustStock).toHaveBeenCalledWith({
        batchId: 7,
        quantity: -2,
        remarks: 'damaged strip',
      })
    );
  });
});
