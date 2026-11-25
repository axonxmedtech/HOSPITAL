import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import icuWardService from '../../services/icuWardService';
import wardService from '../../services/wardService';
import BedCleaningView, { isTheatreWard } from './BedCleaningView';

vi.mock('../../services/wardService', () => ({
  default: {
    getWards: vi.fn(),
    getBeds: vi.fn(),
    markBedCleaned: vi.fn(),
    getReservations: vi.fn(),
  },
}));
vi.mock('../../services/icuWardService', () => ({ default: { getIcuWards: vi.fn() } }));
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));
vi.mock('../../components/IpdAdmitModal', () => ({
  default: ({ initialBed }) => (
    <div>
      Ward admit form for bed {initialBed.bedId} in ward {initialBed.wardId}
    </div>
  ),
}));
vi.mock('../../components/IcuAdmitModal', () => ({
  default: ({ initialBed }) => <div>ICU admit form for bed {initialBed.bedId}</div>,
}));

const beds = {
  1: [
    {
      bedId: 11,
      bedCode: 'GW-B1',
      status: 'cleaning',
      statusSince: new Date(Date.now() - 25 * 60000).toISOString(),
    },
    { bedId: 12, bedCode: 'GW-B2', status: 'available' },
  ],
  3: [
    { bedId: 31, bedCode: 'ICU-B1', status: 'cleaning' },
    { bedId: 32, bedCode: 'ICU-B2', status: 'available' },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  wardService.getWards.mockResolvedValue([
    { wardId: 1, wardName: 'General Ward A', wardType: 'General' },
  ]);
  icuWardService.getIcuWards.mockResolvedValue([
    { wardId: 3, wardName: 'Medical ICU', unitType: 'ICU' },
  ]);
  wardService.getBeds.mockImplementation((id) => Promise.resolve(beds[id] || []));
  wardService.markBedCleaned.mockResolvedValue({});
  wardService.getReservations.mockResolvedValue([]);
});

describe('isTheatreWard', () => {
  it('knows an operating theatre by OT as a word, not inside another word', () => {
    expect(isTheatreWard('OT Room 1')).toBe(true);
    expect(isTheatreWard('Main Operation Theatre')).toBe(true);
    expect(isTheatreWard('Cottage Ward')).toBe(false);
    expect(isTheatreWard('General Ward A')).toBe(false);
  });
});

describe('Reception beds board', () => {
  it('opens on the free beds, each one an admission away', async () => {
    render(<BedCleaningView />);
    expect(await screen.findByText('GW-B2')).toBeInTheDocument();
    expect(screen.getByText('ICU-B2')).toBeInTheDocument();
    expect(screen.queryByText('GW-B1')).not.toBeInTheDocument(); // needs cleaning
    expect(screen.getAllByRole('button', { name: 'Admit here' })).toHaveLength(2);
  });

  it('admits into a ward bed with the ward form', async () => {
    const user = userEvent.setup();
    render(<BedCleaningView />);
    await user.click(
      within((await screen.findByText('GW-B2')).closest('li')).getByRole('button', {
        name: 'Admit here',
      })
    );
    expect(screen.getByText('Ward admit form for bed 12 in ward 1')).toBeInTheDocument();
  });

  it('admits into an ICU bed with the ICU form', async () => {
    const user = userEvent.setup();
    render(<BedCleaningView />);
    await user.click(
      within((await screen.findByText('ICU-B2')).closest('li')).getByRole('button', {
        name: 'Admit here',
      })
    );
    expect(screen.getByText('ICU admit form for bed 32')).toBeInTheDocument();
  });

  it('lists beds waiting for cleaning in general wards and ICU units, with how long they waited', async () => {
    const user = userEvent.setup();
    render(<BedCleaningView />);
    await screen.findByText('GW-B2');
    await user.click(screen.getByRole('button', { name: /Needs cleaning/ }));
    expect(screen.getByText('GW-B1')).toBeInTheDocument();
    expect(screen.getByText('ICU-B1')).toBeInTheDocument();
    expect(screen.queryByText('GW-B2')).not.toBeInTheDocument();
    expect(screen.getByText(/waiting 2\d min/)).toBeInTheDocument();
  });

  it('marks a bed cleaned and reloads', async () => {
    const user = userEvent.setup();
    render(<BedCleaningView />);
    await screen.findByText('GW-B2');
    await user.click(screen.getByRole('button', { name: /Needs cleaning/ }));
    const row = screen.getByText('GW-B1').closest('li');
    await user.click(within(row).getByRole('button', { name: 'Mark cleaned' }));
    await waitFor(() =>
      expect(wardService.markBedCleaned).toHaveBeenCalledWith(11, expect.any(String))
    );
    expect(wardService.getBeds.mock.calls.length).toBeGreaterThan(2);
  });

  it('shows every bed under All beds', async () => {
    const user = userEvent.setup();
    render(<BedCleaningView />);
    await screen.findByText('GW-B2');
    await user.click(screen.getByRole('button', { name: 'All beds' }));
    expect(screen.getByText('GW-B1')).toBeInTheDocument();
    expect(screen.getByText('GW-B2')).toBeInTheDocument();
  });
});
