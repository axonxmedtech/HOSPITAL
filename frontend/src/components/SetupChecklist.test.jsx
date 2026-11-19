import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../services/hospitalService', () => ({ default: { getSetupStatus: vi.fn() } }));
vi.mock('../services/authService', () => ({
  default: { getCurrentUser: () => ({ hospitalId: 3 }) },
}));

import hospitalService from '../services/hospitalService';
import SetupChecklist from './SetupChecklist';

const status = (complete) => ({
  complete,
  done: 1,
  total: 2,
  steps: [
    {
      key: 'doctors',
      title: 'Add your doctors',
      hint: 'Needed for OPD',
      done: false,
      tab: 'doctors',
    },
    {
      key: 'wards',
      title: 'Add wards and beds',
      hint: 'Needed to admit',
      done: true,
      tab: 'wards',
    },
  ],
});

describe('SetupChecklist', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // A plain in-memory store: the test runner's own localStorage is not a usable Storage.
    const store = new Map();
    vi.stubGlobal('localStorage', {
      getItem: (k) => (store.has(k) ? store.get(k) : null),
      setItem: (k, v) => store.set(k, String(v)),
      removeItem: (k) => store.delete(k),
    });
  });

  it('lists what is left and opens the right screen', async () => {
    hospitalService.getSetupStatus.mockResolvedValue(status(false));
    const onGo = vi.fn();
    render(<SetupChecklist onGo={onGo} />);
    expect(await screen.findByText('Get your hospital ready')).toBeInTheDocument();
    expect(
      screen.getByText('1 of 2 done. Finish these so your staff can start working.')
    ).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Set up' })).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Set up' }));
    expect(onGo).toHaveBeenCalledWith('doctors');
  });

  it('stays out of the way once setup is complete', async () => {
    hospitalService.getSetupStatus.mockResolvedValue(status(true));
    const { container } = render(<SetupChecklist onGo={vi.fn()} />);
    await Promise.resolve();
    expect(container).toBeEmptyDOMElement();
  });

  it('can be hidden, and stays hidden for that hospital', async () => {
    hospitalService.getSetupStatus.mockResolvedValue(status(false));
    const { unmount } = render(<SetupChecklist onGo={vi.fn()} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Hide' }));
    expect(screen.queryByText('Get your hospital ready')).not.toBeInTheDocument();
    unmount();
    render(<SetupChecklist onGo={vi.fn()} />);
    expect(hospitalService.getSetupStatus).toHaveBeenCalledTimes(1);
  });
});
