import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import WardModal from './WardModal';

vi.mock('../services/wardService', () => ({
  default: { createWard: vi.fn(), updateWard: vi.fn() },
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ error: vi.fn(), success: vi.fn() }),
}));

import WardService from '../services/wardService';

/** Fills the required fields so onSubmit reaches the service. */
const fillRequired = () => {
  fireEvent.change(screen.getByLabelText('Ward Name'), { target: { value: 'General Ward A' } });
  fireEvent.change(screen.getByLabelText('Bed Price'), { target: { value: '1500' } });
  fireEvent.change(screen.getByLabelText('Total Beds'), { target: { value: '10' } });
};

describe('WardModal — General Ward', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    WardService.createWard.mockResolvedValue({});
    WardService.updateWard.mockResolvedValue({});
  });

  it('renders fields and does not contain unit type selector', () => {
    render(<WardModal open initial={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    expect(screen.getByLabelText('Ward Name')).toBeInTheDocument();
    expect(screen.getByLabelText('Bed Price')).toBeInTheDocument();
    expect(screen.getByLabelText('Total Beds')).toBeInTheDocument();
    expect(screen.getByLabelText('Floor Number')).toBeInTheDocument();
    expect(screen.queryByLabelText('Unit Type')).not.toBeInTheDocument();
  });

  it('sends the payload without unitType when creating a ward', async () => {
    const onSaved = vi.fn();
    const onClose = vi.fn();
    render(<WardModal open initial={null} onClose={onClose} onSaved={onSaved} />);

    fillRequired();
    fireEvent.change(screen.getByLabelText('Floor Number'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(WardService.createWard).toHaveBeenCalled());
    expect(WardService.createWard).toHaveBeenCalledWith({
      wardName: 'General Ward A',
      bedPrice: 1500,
      totalBeds: 10,
      floorNumber: 2,
    });
    expect(onSaved).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('preloads the existing fields when editing a ward and submits update', async () => {
    const onSaved = vi.fn();
    const onClose = vi.fn();
    render(
      <WardModal
        open
        initial={{
          wardId: 5,
          wardName: 'Recovery Ward',
          bedPrice: 2000,
          totalBeds: 8,
          floorNumber: 1,
        }}
        onClose={onClose}
        onSaved={onSaved}
      />
    );

    expect(screen.getByLabelText('Ward Name').value).toBe('Recovery Ward');
    expect(screen.getByLabelText('Bed Price').value).toBe('2000');
    expect(screen.getByLabelText('Total Beds').value).toBe('8');
    expect(screen.getByLabelText('Floor Number').value).toBe('1');
    expect(screen.queryByLabelText('Unit Type')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Bed Price'), { target: { value: '2500' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(WardService.updateWard).toHaveBeenCalled());
    expect(WardService.updateWard).toHaveBeenCalledWith(5, {
      wardName: 'Recovery Ward',
      bedPrice: 2500,
      totalBeds: 8,
      floorNumber: 1,
    });
  });
});
