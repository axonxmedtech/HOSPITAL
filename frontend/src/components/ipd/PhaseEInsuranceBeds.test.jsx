import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import hospitalService from '../../services/hospitalService';
import wardService from '../../services/wardService';
import { ReservationsList, ReserveBedModal } from './BedReservations';
import InsurancePanel from './InsurancePanel';

vi.mock('../../services/hospitalService', () => ({
  default: {
    getIpdClaims: vi.fn(),
    openIpdClaim: vi.fn(),
    stepIpdClaim: vi.fn(),
    settleIpdClaim: vi.fn(),
  },
}));
vi.mock('../../services/wardService', () => ({
  default: { reserveBed: vi.fn(), cancelReservation: vi.fn() },
}));
const toast = { success: vi.fn(), error: vi.fn() };
vi.mock('../../context/ToastContext', () => ({ useToast: () => toast }));

const submitted = {
  publicId: 'c1',
  insurerName: 'Star Health',
  tpaName: 'Medi Assist',
  status: 'PREAUTH_SUBMITTED',
  requestedAmount: 50000,
  nextSteps: ['PREAUTH_APPROVED', 'PREAUTH_REJECTED', 'QUERY', 'WITHDRAWN'],
  canSettle: false,
  events: [{ id: 1, status: 'DRAFT', createdAt: '2026-11-22T10:00:00', note: 'Claim opened' }],
};

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.getIpdClaims.mockResolvedValue([]);
  hospitalService.openIpdClaim.mockResolvedValue({});
  hospitalService.stepIpdClaim.mockResolvedValue({});
  hospitalService.settleIpdClaim.mockResolvedValue({});
  wardService.reserveBed.mockResolvedValue({});
  wardService.cancelReservation.mockResolvedValue({});
});

describe('InsurancePanel', () => {
  it('records a claim for the stay', async () => {
    render(<InsurancePanel admissionId={4} canEdit />);
    fireEvent.click(await screen.findByRole('button', { name: /Record insurance/ }));
    fireEvent.change(screen.getByLabelText('Insurer / scheme *'), {
      target: { value: 'Star Health' },
    });
    fireEvent.change(screen.getByLabelText('Estimated amount (₹)'), { target: { value: '50000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save claim' }));
    await waitFor(() =>
      expect(hospitalService.openIpdClaim).toHaveBeenCalledWith(
        4,
        expect.objectContaining({
          payerType: 'TPA',
          insurerName: 'Star Health',
          requestedAmount: 50000,
        })
      )
    );
  });

  it("offers only the insurer's next steps, and records the approved amount", async () => {
    hospitalService.getIpdClaims.mockResolvedValue([submitted]);
    render(<InsurancePanel admissionId={4} canEdit />);
    expect(await screen.findByText('Pre-auth submitted')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record settlement' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Record approval' }));
    fireEvent.change(screen.getByLabelText('Amount approved (₹) *'), {
      target: { value: '40000' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(hospitalService.stepIpdClaim).toHaveBeenCalledWith('c1', {
        status: 'PREAUTH_APPROVED',
        amount: 40000,
        claimNumber: null,
        note: null,
      })
    );
  });

  it('asks why the insurer deducted before posting a settlement', async () => {
    hospitalService.getIpdClaims.mockResolvedValue([
      {
        ...submitted,
        status: 'FINAL_SUBMITTED',
        approvedAmount: 40000,
        nextSteps: [],
        canSettle: true,
      },
    ]);
    render(<InsurancePanel admissionId={4} canEdit />);
    fireEvent.click(await screen.findByRole('button', { name: 'Record settlement' }));
    fireEvent.change(screen.getByLabelText('Deduction (₹)'), { target: { value: '2000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Post to bill' }));
    expect(toast.error).toHaveBeenCalledWith('Enter the reason for the deduction');
    fireEvent.change(screen.getByLabelText('Deduction reason *'), {
      target: { value: 'Consumables' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Post to bill' }));
    await waitFor(() =>
      expect(hospitalService.settleIpdClaim).toHaveBeenCalledWith('c1', {
        amount: 40000,
        deduction: 2000,
        deductionReason: 'Consumables',
        reference: null,
      })
    );
  });

  it('is read-only for the doctor', async () => {
    hospitalService.getIpdClaims.mockResolvedValue([submitted]);
    render(<InsurancePanel admissionId={4} />);
    expect(await screen.findByText('Star Health')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record approval' })).not.toBeInTheDocument();
  });
});

describe('Bed reservations', () => {
  it('reserves a bed for a named patient', async () => {
    const onDone = vi.fn();
    render(
      <ReserveBedModal
        bed={{ bedId: 7, bedCode: 'GW-07' }}
        wardName="General"
        onClose={vi.fn()}
        onDone={onDone}
      />
    );
    fireEvent.change(screen.getByLabelText('Patient name *'), { target: { value: 'Ramesh' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reserve bed' }));
    await waitFor(() =>
      expect(wardService.reserveBed).toHaveBeenCalledWith(
        expect.objectContaining({ bedId: 7, patientName: 'Ramesh', reservedUntil: null })
      )
    );
    expect(onDone).toHaveBeenCalled();
  });

  it('releases a reservation', async () => {
    const onChanged = vi.fn();
    render(
      <ReservationsList
        reservations={[
          {
            publicId: 'r1',
            ward: 'General',
            bed: 'GW-07',
            patientName: 'Ramesh',
            expectedAt: '2026-11-23T09:00:00',
            reservedUntil: '2026-11-23T15:00:00',
          },
        ]}
        onChanged={onChanged}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Release' }));
    await waitFor(() =>
      expect(wardService.cancelReservation).toHaveBeenCalledWith('r1', 'Released at the desk')
    );
    expect(onChanged).toHaveBeenCalled();
  });
});
