import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import icuService from '../services/icuService';
import icuWardService from '../services/icuWardService';
import IcuWardModal from './IcuWardModal';

vi.mock('../services/icuService', () => ({
  default: {
    getUnitTypes: vi.fn(),
  },
}));

vi.mock('../services/icuWardService', () => ({
  default: {
    createIcuWard: vi.fn(),
    updateIcuWard: vi.fn(),
  },
}));

vi.mock('../context/ToastContext', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
  }),
}));

describe('IcuWardModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    icuService.getUnitTypes.mockResolvedValue([
      { key: 'ICU', label: 'Intensive Care Unit', criticalCare: true },
      { key: 'MICU', label: 'Medical ICU', criticalCare: true },
      { key: 'GENERAL', label: 'General Ward', criticalCare: false },
    ]);
  });

  it('renders create modal and filters only criticalCare unit types', async () => {
    render(<IcuWardModal open={true} onClose={vi.fn()} onSaved={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Create ICU Ward' })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText('Intensive Care Unit (ICU)')).toBeInTheDocument();
      expect(screen.getByText('Medical ICU (MICU)')).toBeInTheDocument();
      expect(screen.queryByText(/General Ward/)).not.toBeInTheDocument();
    });
  });

  it('populates fields when initial is provided for edit', async () => {
    const initialWard = {
      publicId: 'icu-123',
      wardName: 'Cardiac ICU',
      unitType: 'MICU',
      bedPrice: 5000,
      totalBeds: 10,
      floorNumber: 3,
    };

    render(<IcuWardModal open={true} initial={initialWard} onClose={vi.fn()} onSaved={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Edit ICU Ward' })).toBeInTheDocument();
    expect(screen.getByDisplayValue('Cardiac ICU')).toBeInTheDocument();
    expect(screen.getByDisplayValue('5000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('10')).toBeInTheDocument();
    expect(screen.getByDisplayValue('3')).toBeInTheDocument();
  });

  it('submits create payload when form is submitted', async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    const onClose = vi.fn();
    icuWardService.createIcuWard.mockResolvedValue({ id: 1 });

    render(<IcuWardModal open={true} onClose={onClose} onSaved={onSaved} />);

    await user.type(screen.getByPlaceholderText(/Cardiac ICU/), 'Neuro ICU');
    await user.type(screen.getByPlaceholderText('0.00'), '4500');
    await user.type(screen.getByPlaceholderText('Number of beds'), '8');

    await user.click(screen.getByRole('button', { name: 'Create ICU Ward' }));

    await waitFor(() => {
      expect(icuWardService.createIcuWard).toHaveBeenCalledWith(
        expect.objectContaining({
          wardName: 'Neuro ICU',
          bedPrice: 4500,
          totalBeds: 8,
          unitType: 'ICU',
        })
      );
      expect(onSaved).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });
});
