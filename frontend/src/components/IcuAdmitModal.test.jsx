import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import hospitalService from '../services/hospitalService';
import icuWardService from '../services/icuWardService';
import wardService from '../services/wardService';
import IcuAdmitModal from './IcuAdmitModal';

vi.mock('../services/hospitalService', () => ({
  default: {
    getAdmittedIpdAdmissions: vi.fn(),
    getOpds: vi.fn(),
    changeBed: vi.fn(),
    createIpdAdmission: vi.fn(),
  },
}));

vi.mock('../services/icuWardService', () => ({
  default: {
    getIcuWards: vi.fn(),
  },
}));

vi.mock('../services/wardService', () => ({
  default: {
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

describe('IcuAdmitModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hospitalService.getAdmittedIpdAdmissions.mockResolvedValue([
      {
        ipdId: 101,
        patientName: 'Ramesh Patel',
        ipdNumber: 'IPD-001',
        wardName: 'Male General',
        bedNumber: 'MG-01',
      },
    ]);
    hospitalService.getOpds.mockResolvedValue([
      {
        id: 201,
        patientName: 'Priya Sharma',
        opdNumber: 'OPD-101',
        problem: 'Severe chest pain',
      },
    ]);
    icuWardService.getIcuWards.mockResolvedValue([
      {
        wardId: 50,
        wardName: 'Medical ICU',
        unitType: 'MICU',
      },
    ]);
    wardService.getAvailableBeds.mockResolvedValue([
      {
        bedId: 501,
        bedCode: 'ICU-B01',
      },
    ]);
  });

  it('renders modal with From IPD mode by default', async () => {
    render(<IcuAdmitModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Admit Patient to ICU' })).toBeInTheDocument();
    expect(screen.getByText(/From Admitted IPD/)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Ramesh Patel/)).toBeInTheDocument();
      expect(screen.getByText(/Medical ICU/)).toBeInTheDocument();
      expect(screen.getByText(/ICU-B01/)).toBeInTheDocument();
    });
  });

  it('submits IPD-to-ICU transfer via changeBed', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();
    render(<IcuAdmitModal isOpen={true} onClose={onClose} onSuccess={onSuccess} />);

    await waitFor(() => {
      expect(screen.getByText(/Ramesh Patel/)).toBeInTheDocument();
    });

    const user = userEvent.setup();
    const selects = screen.getAllByRole('combobox');
    // selects[0]: IPD Patient, selects[1]: ICU Ward, selects[2]: ICU Bed
    await user.selectOptions(selects[0], '101');
    await user.selectOptions(selects[1], '50');

    await waitFor(() => {
      expect(screen.getByText(/ICU-B01/)).toBeInTheDocument();
    });
    await user.selectOptions(selects[2], '501');

    const submitBtn = screen.getByRole('button', { name: 'Confirm ICU Admission' });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(hospitalService.changeBed).toHaveBeenCalledWith(101, 501, 50);
      expect(mockSuccess).toHaveBeenCalledWith('Patient successfully admitted to ICU ward');
      expect(onSuccess).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('allows switching to OPD direct admission mode', async () => {
    render(<IcuAdmitModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />);

    const user = userEvent.setup();
    const opdTab = screen.getByRole('button', { name: /From OPD \/ Queue/ });
    await user.click(opdTab);

    await waitFor(() => {
      expect(screen.getByText(/Priya Sharma/)).toBeInTheDocument();
      expect(screen.getByText('Admission Type')).toBeInTheDocument();
    });
  });

  it('renders in locked single-patient mode when initialPatient is provided', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();
    const patient = {
      id: 101,
      ipdNumber: 'IPD-2026-001',
      patientName: 'Ramesh Patel',
      wardName: 'Male General',
      bedNumber: 'B-101',
    };

    render(
      <IcuAdmitModal
        isOpen={true}
        onClose={onClose}
        onSuccess={onSuccess}
        initialPatient={patient}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Patient to Admit to ICU')).toBeInTheDocument();
      expect(screen.getByText('Current Patient')).toBeInTheDocument();
      expect(screen.getByText(/Male General/)).toBeInTheDocument();
    });

    // Tab toggle should not be visible in single-patient mode
    expect(screen.queryByRole('button', { name: /From OPD \/ Queue/ })).not.toBeInTheDocument();

    const user = userEvent.setup();
    // Only ICU ward and ICU bed selects should be present
    const selects = screen.getAllByRole('combobox');
    expect(selects.length).toBe(2);

    await user.selectOptions(selects[0], '50');
    await waitFor(() => {
      expect(screen.getByText(/ICU-B01/)).toBeInTheDocument();
    });
    await user.selectOptions(selects[1], '501');

    const submitBtn = screen.getByRole('button', { name: 'Confirm ICU Admission' });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(hospitalService.changeBed).toHaveBeenCalledWith(101, 501, 50);
      expect(onSuccess).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });

  it.each(['success', 'failure'])(
    'clears the old bed while a new destination loads (%s)',
    async (outcome) => {
      icuWardService.getIcuWards.mockResolvedValue([
        { wardId: 50, wardName: 'First ward' },
        { wardId: 51, wardName: 'Second ward' },
      ]);
      let resolveBeds;
      let rejectBeds;
      wardService.getAvailableBeds
        .mockResolvedValueOnce([{ bedId: 501, bedCode: 'FIRST' }])
        .mockImplementationOnce(
          () =>
            new Promise((resolve, reject) => {
              resolveBeds = resolve;
              rejectBeds = reject;
            })
        );
      render(<IcuAdmitModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />);
      const user = userEvent.setup();
      await screen.findByText('First ward');
      const selects = screen.getAllByRole('combobox');
      await user.selectOptions(selects[0], '101');
      await user.selectOptions(selects[1], '50');
      await screen.findByText(/FIRST/);
      expect(selects[2]).toHaveValue('501');
      await user.selectOptions(selects[1], '51');
      expect(selects[2]).toHaveValue('');
      const submit = screen.getByRole('button', { name: 'Confirm ICU Admission' });
      expect(submit).toBeDisabled();
      await user.click(submit);
      expect(hospitalService.changeBed).not.toHaveBeenCalled();
      await act(async () => {
        if (outcome === 'success') resolveBeds([{ bedId: 502, bedCode: 'SECOND' }]);
        else rejectBeds(new Error('Unavailable'));
      });
      if (outcome === 'failure') {
        expect(submit).toBeDisabled();
        expect(selects[2]).toHaveValue('');
        expect(hospitalService.changeBed).not.toHaveBeenCalled();
      } else {
        await user.click(submit);
        expect(hospitalService.changeBed).toHaveBeenCalledExactlyOnceWith(101, 502, 51);
      }
    }
  );

  it('ignores a late bed response from a previous destination', async () => {
    icuWardService.getIcuWards.mockResolvedValue([
      { wardId: 50, wardName: 'First ward' },
      { wardId: 51, wardName: 'Second ward' },
    ]);
    let resolveOld;
    wardService.getAvailableBeds
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          })
      )
      .mockResolvedValueOnce([{ bedId: 502, bedCode: 'SECOND' }]);
    render(<IcuAdmitModal isOpen={true} onClose={vi.fn()} onSuccess={vi.fn()} />);
    const user = userEvent.setup();
    await screen.findByText('First ward');
    const selects = screen.getAllByRole('combobox');
    await user.selectOptions(selects[0], '101');
    await user.selectOptions(selects[1], '50');
    await user.selectOptions(selects[1], '51');
    await screen.findByText(/SECOND/);
    await act(async () => resolveOld([{ bedId: 501, bedCode: 'OLD' }]));
    expect(selects[2]).toHaveValue('502');
    expect(screen.queryByText(/OLD/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirm ICU Admission' }));
    expect(hospitalService.changeBed).toHaveBeenCalledExactlyOnceWith(101, 502, 51);
  });
});
