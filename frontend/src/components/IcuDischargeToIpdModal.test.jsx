import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import hospitalService from '../services/hospitalService';
import icuService from '../services/icuService';
import wardService from '../services/wardService';
import IcuDischargeToIpdModal from './IcuDischargeToIpdModal';

vi.mock('../services/hospitalService', () => ({
  default: {
    changeBed: vi.fn(),
  },
}));

vi.mock('../services/icuService', () => ({
  default: {
    getIcuPatients: vi.fn(),
  },
}));

vi.mock('../services/wardService', () => ({
  default: {
    getWards: vi.fn(),
    getAvailableBeds: vi.fn(),
  },
}));

const mockSuccess = vi.fn();
const mockError = vi.fn();
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({
    success: mockSuccess,
    error: mockError,
  }),
}));

describe('IcuDischargeToIpdModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    icuService.getIcuPatients.mockResolvedValue([
      {
        ipdId: 102,
        patientName: 'Kavita Verma',
        ipdNumber: 'IPD-002',
        icuWardName: 'Neuro ICU',
        bedNumber: 'NICU-03',
      },
    ]);
    wardService.getWards.mockResolvedValue([
      {
        wardId: 10,
        wardName: 'Female General',
        wardType: 'GENERAL',
      },
    ]);
    wardService.getAvailableBeds.mockResolvedValue([
      {
        bedId: 1001,
        bedCode: 'FG-05',
      },
    ]);
  });

  it('renders discharge modal and loads ICU patients and general wards', async () => {
    render(<IcuDischargeToIpdModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Discharge from ICU to IPD' })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Kavita Verma/)).toBeInTheDocument();
      expect(screen.getByText(/Female General/)).toBeInTheDocument();
      expect(screen.getByText(/FG-05/)).toBeInTheDocument();
    });
  });

  it('submits step-down transfer via changeBed and triggers success', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();
    render(<IcuDischargeToIpdModal isOpen={true} onClose={onClose} onSuccess={onSuccess} />);

    await waitFor(() => {
      expect(screen.getByText(/Kavita Verma/)).toBeInTheDocument();
    });

    const user = userEvent.setup();
    const selects = screen.getAllByRole('combobox');
    await user.selectOptions(selects[0], '102');
    await user.selectOptions(selects[1], '10');

    await waitFor(() => {
      expect(screen.getByText(/FG-05/)).toBeInTheDocument();
    });
    await user.selectOptions(selects[2], '1001');

    const submitBtn = screen.getByRole('button', { name: 'Discharge to IPD Ward' });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(hospitalService.changeBed).toHaveBeenCalledWith(102, 1001);
      expect(mockSuccess).toHaveBeenCalledWith(
        'Patient successfully discharged from ICU and moved to General IPD ward'
      );
      expect(onSuccess).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('renders in locked single-patient mode when initialIcuPatient is provided', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();
    const patient = {
      ipdId: 102,
      ipdNumber: 'IPD-2026-002',
      patientName: 'Kavita Verma',
      icuWardName: 'Cardiac ICU',
      bedNumber: 'CICU-01',
    };

    render(
      <IcuDischargeToIpdModal
        isOpen={true}
        onClose={onClose}
        onSuccess={onSuccess}
        initialIcuPatient={patient}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('ICU Patient to Step-down / Discharge')).toBeInTheDocument();
      expect(screen.getByText('Current ICU Patient')).toBeInTheDocument();
      expect(screen.getByText(/Cardiac ICU/)).toBeInTheDocument();
    });

    const user = userEvent.setup();
    // Only General Ward and General Bed selects should be present
    const selects = screen.getAllByRole('combobox');
    expect(selects.length).toBe(2);

    await user.selectOptions(selects[0], '10');
    await waitFor(() => {
      expect(screen.getByText(/FG-05/)).toBeInTheDocument();
    });
    await user.selectOptions(selects[1], '1001');

    const submitBtn = screen.getByRole('button', { name: 'Discharge to IPD Ward' });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(hospitalService.changeBed).toHaveBeenCalledWith(102, 1001);
      expect(onSuccess).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });
});
