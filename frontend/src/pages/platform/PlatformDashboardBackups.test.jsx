import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import platformService from '../../services/platformService';
import PlatformDashboard from './PlatformDashboard';

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

vi.mock('../../services/authService', () => ({
  default: { getCurrentUser: () => ({ name: 'Super Admin', email: 'sa@example.com' }) },
}));

vi.mock('../../services/platformService', () => ({
  default: {
    getHospitals: vi.fn(() => Promise.resolve({ content: [] })),
    getHospitalStats: vi.fn(() =>
      Promise.resolve({
        hospitals: { total: 0, active: 0, inactive: 0 },
        clinics: { total: 0, active: 0, inactive: 0 },
        pharmacies: { total: 0, active: 0, inactive: 0 },
      })
    ),
    getAuditLogs: vi.fn(() => Promise.resolve({ content: [] })),
    getTickets: vi.fn(() => Promise.resolve([])),
    getPlatformFaqs: vi.fn(() => Promise.resolve([])),
    getPlans: vi.fn(() => Promise.resolve([])),
    getSubscriptionOverview: vi.fn(() =>
      Promise.resolve({ counts: {}, demos: [], renewals: [], nearLimit: [] })
    ),
    getBackups: vi.fn(),
    downloadBackup: vi.fn(),
    uploadBackup: vi.fn(),
    validateBackupRestore: vi.fn(),
    executeBackupRestore: vi.fn(),
  },
}));

describe('PlatformDashboard Backup Reminder Banner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders overdue reminder banner when backups are overdue (> 7 days)', async () => {
    platformService.getBackups.mockResolvedValue({
      backups: [],
      hasOverdueDownload: true,
      daysSinceLastDownload: 8,
    });

    render(
      <MemoryRouter initialEntries={['/platform/dashboard?tab=dashboard']}>
        <PlatformDashboard />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Weekly Backup Routine Overdue')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: 'Go to Backups' })).toBeInTheDocument();
  });

  it('does not render overdue reminder banner when backups are up to date', async () => {
    platformService.getBackups.mockResolvedValue({
      backups: [
        {
          fileName: 'hms-latest.sql.gz.enc',
          createdAt: '2026-11-24T00:00:00Z',
          sizeBytes: 1024,
          verified: true,
          lastDownloadedAt: '2026-11-23T00:00:00Z',
          lastDownloadedBy: 'sa@example.com',
        },
      ],
      hasOverdueDownload: false,
      daysSinceLastDownload: 1,
    });

    render(
      <MemoryRouter initialEntries={['/platform/dashboard?tab=dashboard']}>
        <PlatformDashboard />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.queryByText('Weekly Backup Routine Overdue')).not.toBeInTheDocument();
    });
  });

  it('navigates to the Backups tab when clicking Go to Backups button in the banner', async () => {
    const user = userEvent.setup();
    platformService.getBackups.mockResolvedValue({
      backups: [],
      hasOverdueDownload: true,
      daysSinceLastDownload: 9,
    });

    render(
      <MemoryRouter initialEntries={['/platform/dashboard?tab=dashboard']}>
        <PlatformDashboard />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Go to Backups' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: 'Go to Backups' }));

    // Backups tab should now be displayed
    await waitFor(() => {
      expect(screen.getByText('End-to-End Encrypted Backups')).toBeInTheDocument();
    });
  });
});
