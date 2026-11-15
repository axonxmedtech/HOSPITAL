import { render, screen } from '@testing-library/react';
import { AxiosError } from 'axios';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import hospitalService from '../services/hospitalService';
import PrescriptionViewModal from './PrescriptionViewModal';

vi.mock('../services/hospitalService', () => ({
  default: { getLatestPrescription: vi.fn() },
}));

const rejectWith = (status, data) =>
  hospitalService.getLatestPrescription.mockRejectedValue(
    new AxiosError(
      `Request failed with status code ${status}`,
      'ERR_BAD_REQUEST',
      {},
      {},
      {
        status,
        data,
        headers: {},
        config: {},
      }
    )
  );

const open = () =>
  render(<PrescriptionViewModal isOpen onClose={() => {}} patient={{ publicId: 'p-1' }} />);

describe('PrescriptionViewModal error state', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  // The modal used to setError(err.response.data): a JSON body is an object, and rendering it as
  // a React child crashed the whole modal (React #31).
  it('renders the API message instead of crashing on a JSON error body', async () => {
    rejectWith(404, {
      success: false,
      error: 'No prescription found',
      message: 'No prescription found',
    });
    open();
    expect(await screen.findByText('No prescription found')).toBeInTheDocument();
  });

  it('falls back to its own message when the body has nothing usable', async () => {
    rejectWith(500, { success: false });
    open();
    expect(
      await screen.findByText('No prescription records found for this patient.')
    ).toBeInTheDocument();
  });
});
