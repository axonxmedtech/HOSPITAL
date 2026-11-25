import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * The pharmacy audit trail is browsed in full, one server page at a time, with the real total —
 * not the newest 1000 entries paged in the browser.
 */
const hospitalService = { getPharmacyAuditLogsPage: vi.fn() };
vi.mock('../../../services/hospitalService', () => ({ default: hospitalService }));

const { default: BranchAuditLogsView } = await import('./BranchAuditLogsView');

const row = (i) => ({
  id: i,
  action: 'BATCH_ADJUSTED',
  details: `Adjustment ${i}`,
  performedBy: 'pharma@x.test',
  timestamp: '2026-11-20T10:00:00',
});

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.getPharmacyAuditLogsPage.mockImplementation(async (_s, _r, page) => ({
    content: Array.from({ length: 10 }, (_, i) => row(page * 10 + i + 1)),
    page,
    size: 10,
    totalPages: 30,
    totalElements: 295,
  }));
});

describe('BranchAuditLogsView — server paging', () => {
  it('asks the server for one page and shows the full total', async () => {
    render(<BranchAuditLogsView />);

    await waitFor(() =>
      expect(hospitalService.getPharmacyAuditLogsPage).toHaveBeenCalledWith('', null, 0, 10)
    );
    expect(await screen.findByText(/of 295 trails/)).toBeInTheDocument();
    // A window of page buttons, not 30 of them.
    expect(screen.queryByRole('button', { name: '30' })).toBeNull();
  });

  it('fetches the next page from the server', async () => {
    const user = userEvent.setup();
    render(<BranchAuditLogsView />);
    await screen.findByText(/of 295 trails/);

    await user.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(hospitalService.getPharmacyAuditLogsPage).toHaveBeenLastCalledWith('', null, 1, 10)
    );
  });
});
