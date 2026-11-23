import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import apiClient from '../../services/apiService';
import PharmacyInvoiceDetailsCard from './PharmacyInvoiceDetailsCard';

vi.mock('../../services/apiService', () => ({ default: { get: vi.fn(), put: vi.fn() } }));
const success = vi.fn();
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success, error: vi.fn() }),
}));

beforeEach(() => vi.clearAllMocks());

describe('Invoice details card', () => {
  it('warns when the details are missing and saves a valid GSTIN in capitals', async () => {
    apiClient.get.mockResolvedValue({
      data: { gstin: null, drugLicenseNumber: null, complete: false },
    });
    apiClient.put.mockResolvedValue({
      data: { gstin: '27ABCDE1234F1Z5', drugLicenseNumber: '20B/MH/1', complete: true },
    });
    render(<PharmacyInvoiceDetailsCard />);
    expect(await screen.findByText(/Not complete yet/)).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('27ABCDE1234F1Z5'), {
      target: { value: '27abcde1234f1z5' },
    });
    fireEvent.change(screen.getByPlaceholderText('20B/MH/123, 21B/MH/124'), {
      target: { value: '20B/MH/1' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save invoice details' }));

    await waitFor(() =>
      expect(apiClient.put).toHaveBeenCalledWith('/hospital/settings/invoice-details', {
        gstin: '27ABCDE1234F1Z5',
        drugLicenseNumber: '20B/MH/1',
      })
    );
    expect(success).toHaveBeenCalled();
  });

  it('refuses a malformed GSTIN before sending it', async () => {
    apiClient.get.mockResolvedValue({
      data: { gstin: null, drugLicenseNumber: null, complete: false },
    });
    render(<PharmacyInvoiceDetailsCard />);
    await screen.findByText(/Not complete yet/);
    fireEvent.change(screen.getByPlaceholderText('27ABCDE1234F1Z5'), {
      target: { value: '12345' },
    });
    expect(screen.getByText(/must be 15 characters/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save invoice details' })).toBeDisabled();
  });
});
