import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

const success = vi.fn();
const toastError = vi.fn();
// Stable functions, as the real ToastContext provides (it memoises them).
vi.mock('../../context/ToastContext', () => ({ useToast: () => ({ success, error: toastError }) }));
vi.mock('../../services/platformService', () => ({
  default: {
    getHospitalLimits: vi.fn(),
    hospitalDemoAction: vi.fn(),
    setHospitalLimits: vi.fn(),
    extendSubscription: vi.fn(),
  },
}));

import platformService from '../../services/platformService';
import { limitsSummary } from '../PlansTab';
import HospitalPlanControls from './HospitalPlanControls';
import { subscriptionBadge } from './SubscriptionBadge';

const limits = (over = {}) => ({
  state: 'DEMO',
  isDemo: true,
  daysLeft: 2,
  endsAt: '2026-11-21T10:00:00',
  limits: [{ key: 'BEDS', label: 'Beds', used: 9, planLimit: 10, override: null, limit: 10 }],
  ...over,
});

describe('HospitalPlanControls', () => {
  it('extends a demo and saves an override', async () => {
    platformService.getHospitalLimits.mockResolvedValue(limits());
    platformService.hospitalDemoAction.mockResolvedValue({});
    platformService.setHospitalLimits.mockResolvedValue({});
    render(<HospitalPlanControls hospitalPublicId="h-1" />);
    await userEvent.click(await screen.findByRole('button', { name: '+7d' }));
    await waitFor(() =>
      expect(platformService.hospitalDemoAction).toHaveBeenCalledWith('h-1', 'EXTEND', 7)
    );
    expect(success).toHaveBeenCalledWith('Demo extended by 7 days');
    // The panel re-reads after every change; type once that has landed.
    await waitFor(() => expect(platformService.getHospitalLimits).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save limits' })).toBeEnabled());

    await userEvent.type(screen.getByLabelText('Beds override'), '15');
    await userEvent.click(screen.getByRole('button', { name: 'Save limits' }));
    await waitFor(() =>
      expect(platformService.setHospitalLimits).toHaveBeenCalledWith('h-1', { BEDS: 15 })
    );
  });

  it('offers extension and starting a demo for a paid account', async () => {
    platformService.getHospitalLimits.mockResolvedValue(limits({ state: 'ACTIVE', isDemo: false }));
    render(<HospitalPlanControls hospitalPublicId="h-2" />);
    expect(await screen.findByRole('button', { name: 'Extend subscription' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Make it a demo' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'End demo now' })).not.toBeInTheDocument();
  });
});

describe('list labels', () => {
  it('summarises plan limits', () => {
    expect(limitsSummary({ BEDS: 10, DOCTORS: 3 })).toBe('10 beds · 3 doctors');
    expect(limitsSummary({})).toBe('Unlimited');
  });

  it('labels demos and renewals in the hospital list', () => {
    const inThree = new Date(Date.now() + 2.5 * 86400000).toISOString();
    expect(subscriptionBadge({ isDemo: true, demoEndsAt: inThree })[0]).toBe('Demo · 3d left');
    expect(subscriptionBadge({ isDemo: true, demoEndsAt: '2020-01-01T00:00:00' })[0]).toBe(
      'Demo ended'
    );
    expect(subscriptionBadge({ subscriptionStatus: 'GRACE' })[0]).toBe('Grace period');
    expect(subscriptionBadge({ subscriptionStatus: 'ACTIVE' })[0]).toBe('Paid');
  });
});
