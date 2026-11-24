import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import platformService from '../../services/platformService';
import PlatformBackupsTab from './PlatformBackupsTab';

const mockToast = {
  success: vi.fn(),
  error: vi.fn(),
};

vi.mock('../../context/ToastContext', () => ({
  useToast: () => mockToast,
}));

vi.mock('../../services/platformService', () => ({
  default: {
    getBackups: vi.fn(),
    downloadBackup: vi.fn(),
    uploadBackup: vi.fn(),
    validateBackupRestore: vi.fn(),
    executeBackupRestore: vi.fn(),
  },
}));

describe('PlatformBackupsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state initially and then shows the backups list', async () => {
    platformService.getBackups.mockResolvedValue({
      backups: [
        {
          fileName: 'hms-2026-11-24.sql.gz.enc',
          createdAt: '2026-11-24T00:00:00Z',
          sizeBytes: 1048576,
          sha256: 'abc1234567890abcdef',
          verified: true,
          lastDownloadedAt: '2026-11-24T02:00:00Z',
          lastDownloadedBy: 'sa@example.com',
        },
      ],
      overdue7Days: false,
    });

    render(<PlatformBackupsTab />);

    expect(screen.getByText('Loading backups...')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('hms-2026-11-24.sql.gz.enc')).toBeInTheDocument();
    });

    expect(screen.getByText('Checked OK')).toBeInTheDocument();
    expect(screen.getByText('sa@example.com')).toBeInTheDocument();
  });

  it('renders empty state when there are no backup files', async () => {
    platformService.getBackups.mockResolvedValue({
      backups: [],
      overdue7Days: true,
    });

    render(<PlatformBackupsTab />);

    await waitFor(() => {
      expect(screen.getByText('No backups found on this server yet.')).toBeInTheDocument();
    });
  });

  it('handles backup download successfully', async () => {
    const user = userEvent.setup();
    platformService.getBackups.mockResolvedValue({
      backups: [
        {
          fileName: 'hms-2026-11-24.sql.gz.enc',
          createdAt: '2026-11-24T00:00:00Z',
          sizeBytes: 2048,
          sha256: 'def456',
          verified: true,
          lastDownloadedAt: null,
          lastDownloadedBy: null,
        },
      ],
      overdue7Days: false,
    });

    const mockBlob = new Blob(['encrypted-content'], { type: 'application/octet-stream' });
    platformService.downloadBackup.mockResolvedValue(mockBlob);

    const originalCreateObjectURL = window.URL.createObjectURL;
    const originalRevokeObjectURL = window.URL.revokeObjectURL;
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    window.URL.revokeObjectURL = vi.fn();

    render(<PlatformBackupsTab />);

    await waitFor(() => {
      expect(screen.getByText('hms-2026-11-24.sql.gz.enc')).toBeInTheDocument();
    });

    const downloadBtn = screen.getByRole('button', { name: 'Download' });
    await user.click(downloadBtn);

    expect(platformService.downloadBackup).toHaveBeenCalledWith('hms-2026-11-24.sql.gz.enc');
    await waitFor(() => {
      expect(mockToast.success).toHaveBeenCalledWith(
        'Backup hms-2026-11-24.sql.gz.enc downloaded successfully.'
      );
    });

    window.URL.createObjectURL = originalCreateObjectURL;
    window.URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('displays rate limit error message on 429 response during download', async () => {
    const user = userEvent.setup();
    platformService.getBackups.mockResolvedValue({
      backups: [
        {
          fileName: 'hms-2026-11-24.sql.gz.enc',
          createdAt: '2026-11-24T00:00:00Z',
          sizeBytes: 2048,
          sha256: 'def456',
          verified: true,
          lastDownloadedAt: null,
          lastDownloadedBy: null,
        },
      ],
      overdue7Days: false,
    });

    platformService.downloadBackup.mockRejectedValue({
      response: {
        status: 429,
        data: 'Rate limit exceeded: Maximum 5 downloads per hour allowed.',
      },
    });

    render(<PlatformBackupsTab />);

    await waitFor(() => {
      expect(screen.getByText('hms-2026-11-24.sql.gz.enc')).toBeInTheDocument();
    });

    const downloadBtn = screen.getByRole('button', { name: 'Download' });
    await user.click(downloadBtn);

    await waitFor(() => {
      expect(mockToast.error).toHaveBeenCalledWith(
        'Download limit reached: maximum 5 downloads per hour.'
      );
    });
  });

  it('allows uploading a backup file and performing scratch database test validation', async () => {
    const user = userEvent.setup();
    platformService.getBackups.mockResolvedValue({
      backups: [
        {
          fileName: 'existing-backup.sql.gz.enc',
          createdAt: '2026-11-24T00:00:00Z',
          sizeBytes: 2048,
          sha256: 'def456',
          verified: true,
          lastDownloadedAt: null,
          lastDownloadedBy: null,
        },
      ],
      overdue7Days: false,
    });

    platformService.uploadBackup.mockResolvedValue({
      message: 'Backup uploaded successfully.',
      fileName: 'uploaded-backup.sql.gz.enc',
      fileSizeBytes: 1024,
    });

    platformService.validateBackupRestore.mockResolvedValue({
      valid: true,
      message: 'Backup verified successfully in scratch database.',
      tableCount: 45,
      validationErrors: [],
    });

    const { container } = render(<PlatformBackupsTab />);

    await waitFor(() => {
      expect(screen.getByText('Disaster Recovery (Upload & Restore)')).toBeInTheDocument();
    });

    const file = new File(['dummy backup'], 'uploaded-backup.sql.gz.enc', {
      type: 'application/octet-stream',
    });
    const fileInput = container.querySelector('input[type="file"]');
    expect(fileInput).not.toBeNull();
    await user.upload(fileInput, file);

    const uploadBtn = screen.getByRole('button', { name: 'Upload to Server' });
    await user.click(uploadBtn);

    await waitFor(() => {
      expect(platformService.uploadBackup).toHaveBeenCalledWith(file);
    });

    // Enter master key
    const keyInput = screen.getByPlaceholderText(/Enter encryption secret key/i);
    await user.type(keyInput, 'StrongSecretKey123!');

    const testValidateBtn = screen.getByRole('button', { name: /1\. Test in Scratch DB/i });
    await user.click(testValidateBtn);

    await waitFor(() => {
      expect(platformService.validateBackupRestore).toHaveBeenCalledWith(
        'uploaded-backup.sql.gz.enc',
        'StrongSecretKey123!'
      );
    });

    await waitFor(() => {
      expect(
        screen.getByText(/Table count: 45\. Core tables verified restorable\./i)
      ).toBeInTheDocument();
    });
  });

  it('prompts for RESTORE-LIVE-DATABASE confirmation before executing live database restore', async () => {
    const user = userEvent.setup();
    platformService.getBackups.mockResolvedValue({
      backups: [
        {
          fileName: 'existing-backup.sql.gz.enc',
          createdAt: '2026-11-24T00:00:00Z',
          sizeBytes: 2048,
          sha256: 'def456',
          verified: true,
          lastDownloadedAt: null,
          lastDownloadedBy: null,
        },
      ],
      overdue7Days: false,
    });

    platformService.validateBackupRestore.mockResolvedValue({
      valid: true,
      message: 'Backup verified successfully in scratch database.',
      tableCount: 45,
      validationErrors: [],
    });

    platformService.executeBackupRestore.mockResolvedValue({
      valid: true,
      message: 'Live database restore completed successfully.',
      tableCount: 45,
      validationErrors: [],
    });

    render(<PlatformBackupsTab />);

    await waitFor(() => {
      expect(screen.getByText('Disaster Recovery (Upload & Restore)')).toBeInTheDocument();
    });

    // Select existing file
    const select = screen.getByRole('combobox');
    await user.selectOptions(select, 'existing-backup.sql.gz.enc');

    // Type master key
    const keyInput = screen.getByPlaceholderText(/Enter encryption secret key/i);
    await user.type(keyInput, 'StrongSecretKey123!');

    // Click validate
    const testValidateBtn = screen.getByRole('button', { name: /1\. Test in Scratch DB/i });
    await user.click(testValidateBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Table count: 45\. Core tables verified restorable\./i)
      ).toBeInTheDocument();
    });

    // Mock prompt with correct confirmation
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('RESTORE-LIVE-DATABASE');

    const restoreBtn = screen.getByRole('button', { name: /2\. Restore to Live DB/i });
    expect(restoreBtn).not.toBeDisabled();
    await user.click(restoreBtn);

    expect(promptSpy).toHaveBeenCalled();
    await waitFor(() => {
      expect(platformService.executeBackupRestore).toHaveBeenCalledWith(
        'existing-backup.sql.gz.enc',
        'StrongSecretKey123!',
        'RESTORE-LIVE-DATABASE'
      );
    });

    promptSpy.mockRestore();
  });
});
