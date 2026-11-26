import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import hospitalService from '../../services/hospitalService';
import DietCard from './DietCard';
import DoctorRoundsTimeline from './DoctorRoundsTimeline';
import InvestigationsPanel from './InvestigationsPanel';

vi.mock('../../services/hospitalService', () => ({
  default: {
    getIpdRounds: vi.fn(),
    getIpdInvestigations: vi.fn(),
    orderIpdInvestigations: vi.fn(),
    markInvestigationSample: vi.fn(),
    recordInvestigationResult: vi.fn(),
    cancelInvestigation: vi.fn(),
    getIpdDiet: vi.fn(),
    setIpdDiet: vi.fn(),
    downloadDietSheet: vi.fn(),
  },
}));
vi.mock('../../utils/printPdf', () => ({ printBlob: vi.fn() }));
const toast = { success: vi.fn(), error: vi.fn() };
vi.mock('../../context/ToastContext', () => ({ useToast: () => toast }));

const ordered = {
  publicId: 'o1',
  testName: 'CBC',
  priority: 'URGENT',
  status: 'ORDERED',
  orderedAt: '2026-11-22T09:15:00',
};

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.getIpdInvestigations.mockResolvedValue([ordered]);
  hospitalService.orderIpdInvestigations.mockResolvedValue([]);
  hospitalService.markInvestigationSample.mockResolvedValue({});
  hospitalService.recordInvestigationResult.mockResolvedValue({});
  hospitalService.getIpdDiet.mockResolvedValue([]);
  hospitalService.setIpdDiet.mockResolvedValue({});
});

describe('DoctorRoundsTimeline', () => {
  it('shows each round with its time and doctor', async () => {
    hospitalService.getIpdRounds.mockResolvedValue([
      {
        at: '2026-11-22T10:30:00',
        doctor: 'Dr. Mehta',
        diagnosis: 'Dengue',
        notes: 'Platelets rising',
      },
    ]);
    render(<DoctorRoundsTimeline admissionId={5} />);
    expect(await screen.findByText('Platelets rising')).toBeInTheDocument();
    expect(screen.getByText(/Dr\. Mehta/)).toBeInTheDocument();
    expect(screen.getByText(/10:30/)).toBeInTheDocument();
  });
});

describe('InvestigationsPanel', () => {
  it('orders several tests from one line', async () => {
    render(<InvestigationsPanel admissionId={5} canOrder />);
    fireEvent.change(await screen.findByLabelText(/Order tests/), {
      target: { value: 'CBC, LFT\nUrine routine' },
    });
    fireEvent.change(screen.getByLabelText('Priority'), { target: { value: 'URGENT' } });
    fireEvent.click(screen.getByRole('button', { name: 'Order' }));
    await waitFor(() =>
      expect(hospitalService.orderIpdInvestigations).toHaveBeenCalledWith(5, {
        tests: ['CBC', 'LFT', 'Urine routine'],
        priority: 'URGENT',
        notes: null,
        // One key per order, so a resent order is placed once.
        idempotencyKey: expect.stringMatching(/^lab-[A-Za-z0-9_-]+$/),
      })
    );
  });

  it('lets the ward collect the sample and record the result, but not order or cancel', async () => {
    render(<InvestigationsPanel admissionId={5} canWork />);
    expect(await screen.findByText('URGENT')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Order tests/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel test' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Sample collected' }));
    await waitFor(() => expect(hospitalService.markInvestigationSample).toHaveBeenCalledWith('o1'));

    fireEvent.click(screen.getByRole('button', { name: 'Record result' }));
    fireEvent.change(screen.getByLabelText('Result'), { target: { value: 'Hb 11.2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save result' }));
    await waitFor(() =>
      expect(hospitalService.recordInvestigationResult).toHaveBeenCalledWith('o1', 'Hb 11.2')
    );
  });
});

describe('DietCard', () => {
  it('shows the current diet read-only to the ward', async () => {
    hospitalService.getIpdDiet.mockResolvedValue([
      {
        publicId: 'd1',
        dietType: 'NBM',
        instructions: 'Surgery at 8',
        startedAt: '2026-11-22T06:00:00',
      },
    ]);
    render(<DietCard admissionId={5} />);
    expect(await screen.findByText('Nil by mouth (NBM)')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Change' })).not.toBeInTheDocument();
  });

  it('asks for a description of an "other" diet before saving', async () => {
    render(<DietCard admissionId={5} canEdit />);
    fireEvent.click(await screen.findByRole('button', { name: 'Set diet' }));
    fireEvent.change(screen.getByLabelText('Diet'), { target: { value: 'OTHER' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save diet' }));
    expect(toast.error).toHaveBeenCalledWith('Describe the diet in the instructions');
    fireEvent.change(screen.getByLabelText('Diet instructions'), {
      target: { value: 'Jain, no onion' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save diet' }));
    await waitFor(() =>
      expect(hospitalService.setIpdDiet).toHaveBeenCalledWith(5, {
        dietType: 'OTHER',
        instructions: 'Jain, no onion',
      })
    );
  });
});
