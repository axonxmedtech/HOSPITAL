import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const get = vi.fn();
vi.mock('../../services/apiService', () => ({ default: { get: (...a) => get(...a) } }));
vi.mock('../../services/authService', () => ({
  default: { getCurrentUser: () => ({ hospitalId: 4, role: 'HOSPITAL_ADMIN' }) },
}));

import { resetAccessStatus } from '../../hooks/useAccessStatus';
import AccessBanner from './AccessBanner';
import AccessLabel from './AccessLabel';
import PlanUsage from './PlanUsage';

const status = (over) => ({
  state: 'DEMO',
  demo: true,
  daysLeft: 3,
  message: 'Demo account: 3 days left.',
  supportPhone: '9022915865',
  ...over,
});

describe('demo label and banner', () => {
  beforeEach(() => {
    get.mockReset();
    resetAccessStatus();
  });

  it('shows the days left on a running demo, without a banner', async () => {
    get.mockResolvedValue({ data: status() });
    render(
      <>
        <AccessLabel />
        <AccessBanner />
      </>
    );
    expect(await screen.findByText('Demo · 3 days left')).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('says the demo has ended and how to reach support', async () => {
    get.mockResolvedValue({
      data: status({
        state: 'DEMO_ENDED',
        daysLeft: 0,
        message: 'Your demo has ended. Contact 9022915865.',
      }),
    });
    render(
      <>
        <AccessLabel />
        <AccessBanner />
      </>
    );
    expect(await screen.findByText('Demo ended')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Your demo has ended');
    expect(screen.getByRole('link', { name: 'Call 9022915865' })).toHaveAttribute(
      'href',
      'tel:9022915865'
    );
  });

  it('shows nothing for a paid, active account', async () => {
    get.mockResolvedValue({ data: { state: 'ACTIVE', demo: false } });
    const { container } = render(
      <>
        <AccessLabel />
        <AccessBanner />
      </>
    );
    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});

describe('PlanUsage', () => {
  it('lists each limit with its use, and says how to upgrade', async () => {
    get.mockResolvedValue({
      data: {
        planName: 'Basic',
        state: 'ACTIVE',
        demo: false,
        supportPhone: '9022915865',
        limits: [
          { key: 'BEDS', label: 'Beds', used: 10, limit: 10 },
          { key: 'ICU_BEDS', label: 'ICU beds', used: 0, limit: 0 },
          { key: 'STAFF_LOGINS', label: 'Staff logins', used: 4, limit: null },
        ],
      },
    });
    render(<PlanUsage />);
    expect(await screen.findByText('10 / 10')).toBeInTheDocument();
    expect(screen.getByText('Not included')).toBeInTheDocument();
    expect(screen.getByText('4 used · unlimited')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Beds' })).toHaveAttribute('value', '10');
    expect(screen.getByRole('link', { name: '9022915865' })).toBeInTheDocument();
  });
});
