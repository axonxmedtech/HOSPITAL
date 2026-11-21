import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import icuWardService from '../../services/icuWardService';
import wardService from '../../services/wardService';
import BedCleaningView from './BedCleaningView';

vi.mock('../../services/wardService', () => ({
  default: { getWards: vi.fn(), getBeds: vi.fn(), markBedCleaned: vi.fn() },
}));
vi.mock('../../services/icuWardService', () => ({ default: { getIcuWards: vi.fn() } }));
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
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
  3: [{ bedId: 31, bedCode: 'ICU-B1', status: 'cleaning' }],
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
});

describe('Reception bed cleaning', () => {
  it('lists beds waiting for cleaning in general wards and ICU units, with how long they waited', async () => {
    render(<BedCleaningView />);
    expect(await screen.findByText('GW-B1')).toBeInTheDocument();
    expect(screen.getByText('ICU-B1')).toBeInTheDocument();
    expect(screen.queryByText('GW-B2')).not.toBeInTheDocument(); // available beds hidden by default
    expect(screen.getByText(/waiting 2\d min/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Needs cleaning \(2\)/ })).toBeInTheDocument();
  });

  it('marks a bed cleaned and reloads', async () => {
    const user = userEvent.setup();
    render(<BedCleaningView />);
    const row = (await screen.findByText('GW-B1')).closest('li');
    await user.click(within(row).getByRole('button', { name: 'Mark cleaned' }));
    await waitFor(() =>
      expect(wardService.markBedCleaned).toHaveBeenCalledWith(11, expect.any(String))
    );
    expect(wardService.getBeds.mock.calls.length).toBeGreaterThan(2);
  });

  it('shows every bed under All beds', async () => {
    const user = userEvent.setup();
    render(<BedCleaningView />);
    await screen.findByText('GW-B1');
    await user.click(screen.getByRole('button', { name: 'All beds' }));
    expect(screen.getByText('GW-B2')).toBeInTheDocument();
  });
});
