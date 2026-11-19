import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const toastError = vi.fn();
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: toastError }),
}));
vi.mock('../services/hospitalService', () => ({
  default: {
    getPatients: vi.fn(),
    getAdmittedIpdAdmissions: vi.fn(),
    getDoctors: vi.fn(),
    createIpdAdmission: vi.fn(),
  },
}));
vi.mock('../services/wardService', () => ({
  default: { getWardsForAdmission: vi.fn(), getWards: vi.fn(), getAvailableBeds: vi.fn() },
}));

import hospitalService from '../services/hospitalService';
import wardService from '../services/wardService';
import IpdAdmitModal from './IpdAdmitModal';

describe('IpdAdmitModal direct admission', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hospitalService.getPatients.mockResolvedValue({ content: [{ id: 4, name: 'Sita' }] });
    hospitalService.getAdmittedIpdAdmissions.mockResolvedValue([]);
  });

  it('explains an empty hospital instead of showing empty lists', async () => {
    hospitalService.getDoctors.mockResolvedValue({ content: [] });
    wardService.getWardsForAdmission.mockResolvedValue([]);
    wardService.getWards.mockResolvedValue([]);
    render(<IpdAdmitModal isOpen onClose={() => {}} opd={null} />);
    expect(await screen.findByText(/No doctors added yet/)).toBeInTheDocument();
    expect(screen.getByText(/No wards or beds are set up yet/)).toBeInTheDocument();
  });

  it('will not admit without an attending doctor', async () => {
    hospitalService.getDoctors.mockResolvedValue({
      content: [
        { id: 1, name: 'Dr A' },
        { id: 2, name: 'Dr B' },
      ],
    });
    wardService.getWardsForAdmission.mockResolvedValue([{ wardId: 3, wardName: 'General' }]);
    wardService.getAvailableBeds.mockResolvedValue([
      { bedId: 8, bedCode: 'G-1', status: 'available' },
    ]);
    render(<IpdAdmitModal isOpen onClose={() => {}} opd={null} />);
    await screen.findByText('G-1 — available');
    await userEvent.selectOptions(screen.getByLabelText('Select Patient *'), '4');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm Admission' }));
    expect(toastError).toHaveBeenCalledWith('Select the doctor the patient is admitted under');
    expect(hospitalService.createIpdAdmission).not.toHaveBeenCalled();
  });
});
