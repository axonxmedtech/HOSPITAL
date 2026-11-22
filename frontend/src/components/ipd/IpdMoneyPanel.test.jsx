import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import hospitalService from '../../services/hospitalService';
import IpdMoneyPanel from './IpdMoneyPanel';

vi.mock('../../services/hospitalService', () => ({
  default: {
    takeIpdAdvance: vi.fn(),
    downloadAdvanceReceipt: vi.fn(),
    getIpdPackages: vi.fn(),
    applyIpdPackage: vi.fn(),
    removeIpdPackage: vi.fn(),
  },
}));
vi.mock('../../utils/printPdf', () => ({ printBlob: vi.fn() }));
const toast = { success: vi.fn(), error: vi.fn() };
vi.mock('../../context/ToastContext', () => ({ useToast: () => toast }));

const data = (overrides = {}) => ({
  admission: { bedChargeRule: 'CALENDAR_DAY', ...overrides.admission },
  billing: { balance: 0, advancePaid: 0, advances: [], ...overrides.billing },
});

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.takeIpdAdvance.mockResolvedValue({ paymentId: 7, amount: 5000 });
  hospitalService.downloadAdvanceReceipt.mockResolvedValue(new Blob());
  hospitalService.getIpdPackages.mockResolvedValue([
    { id: 3, name: 'Delivery', price: 20000, includedDays: 3 },
  ]);
  hospitalService.applyIpdPackage.mockResolvedValue({});
});

describe('IpdMoneyPanel', () => {
  it('takes an advance and prints its receipt', async () => {
    const onChanged = vi.fn();
    render(<IpdMoneyPanel ipdId={9} data={data()} canAct onChanged={onChanged} />);
    fireEvent.click(screen.getByRole('button', { name: 'Take Advance' }));
    fireEvent.change(screen.getByLabelText('Amount (₹)'), { target: { value: '5000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Record & print receipt' }));
    await waitFor(() =>
      expect(hospitalService.takeIpdAdvance).toHaveBeenCalledWith(9, {
        amount: 5000,
        mode: 'CASH',
        reference: null,
      })
    );
    await waitFor(() => expect(hospitalService.downloadAdvanceReceipt).toHaveBeenCalledWith(7));
    expect(onChanged).toHaveBeenCalled();
  });

  it('asks for the UTR of a UPI advance', async () => {
    render(<IpdMoneyPanel ipdId={9} data={data()} canAct />);
    fireEvent.click(screen.getByRole('button', { name: 'Take Advance' }));
    fireEvent.change(screen.getByLabelText('Amount (₹)'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('Paid by'), { target: { value: 'UPI' } });
    fireEvent.click(screen.getByRole('button', { name: 'Record & print receipt' }));
    expect(toast.error).toHaveBeenCalledWith('Enter the UPI reference (UTR)');
    expect(hospitalService.takeIpdAdvance).not.toHaveBeenCalled();
  });

  it('shows unused advance, and applies a package', async () => {
    render(
      <IpdMoneyPanel
        ipdId={9}
        data={data({ billing: { balance: -2500, advancePaid: 5000 } })}
        canAct
      />
    );
    expect(screen.getByText(/₹2,500 unused/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Apply package' }));
    fireEvent.change(await screen.findByLabelText('Package'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(hospitalService.applyIpdPackage).toHaveBeenCalledWith(9, '3'));
  });

  it('offers removing a package only to the admin', () => {
    const withPackage = data({ admission: { packageName: 'Delivery', packageDays: 3 } });
    const { rerender } = render(<IpdMoneyPanel ipdId={9} data={withPackage} canAct />);
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument();
    rerender(<IpdMoneyPanel ipdId={9} data={withPackage} canAct isAdmin />);
    expect(screen.getByRole('button', { name: 'Remove' })).toBeInTheDocument();
  });
});
