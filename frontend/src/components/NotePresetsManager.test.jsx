import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import hospitalService from '../services/hospitalService';
import NotePresetsManager from './NotePresetsManager';

// Mock the toast context and API service the component depends on.
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

vi.mock('../services/hospitalService', () => ({
  default: {
    getConsultationNotePresets: vi.fn(),
    createConsultationNotePreset: vi.fn(),
    updateConsultationNotePreset: vi.fn(),
    deleteConsultationNotePreset: vi.fn(),
    getDoctors: vi.fn(),
    getConsultationStatements: vi.fn(),
  },
}));

describe('NotePresetsManager', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    hospitalService.getConsultationNotePresets.mockResolvedValue([
      { id: 1, text: 'Avoid oily food', displayOrder: 0 },
      { id: 2, text: 'Drink water', displayOrder: 1 },
    ]);
    hospitalService.getDoctors.mockResolvedValue({ content: [{ id: 7, name: 'Dr House' }] });
    hospitalService.getConsultationStatements.mockResolvedValue([]);
  });

  it('loads and renders existing quick notes for the field type', async () => {
    render(<NotePresetsManager fieldType="TREATMENT_NOTES" />);
    expect(await screen.findByText('Avoid oily food')).toBeInTheDocument();
    expect(screen.getByText('Drink water')).toBeInTheDocument();
    expect(hospitalService.getConsultationNotePresets).toHaveBeenCalledWith('TREATMENT_NOTES');
  });

  it('creates a new quick note from the add form', async () => {
    hospitalService.createConsultationNotePreset.mockResolvedValue({
      id: 3,
      text: 'Rest well',
      displayOrder: 2,
    });
    render(<NotePresetsManager fieldType="TREATMENT_NOTES" />);
    await screen.findByText('Avoid oily food');

    fireEvent.change(screen.getByPlaceholderText(/Search platform notes|Avoid oily food/i), {
      target: { value: 'Rest well' },
    });
    fireEvent.click(screen.getByRole('button', { name: /add/i }));

    await waitFor(() =>
      expect(hospitalService.createConsultationNotePreset).toHaveBeenCalledWith(
        expect.objectContaining({ fieldType: 'TREATMENT_NOTES', text: 'Rest well' })
      )
    );
  });

  it('renders platform suggestions and auto-populates translation when selected', async () => {
    hospitalService.getConsultationStatements.mockResolvedValue([
      {
        id: 99,
        englishText: 'Take complete rest',
        marathiText: 'पूर्ण विश्रांती घ्या.',
        hindiText: 'पूरा आराम करें.',
      },
    ]);
    render(<NotePresetsManager fieldType="TREATMENT_NOTES" />);
    await screen.findByText('Avoid oily food');

    expect(await screen.findByText('Take complete rest')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Take complete rest'));

    expect(screen.getByPlaceholderText(/Search platform notes|Avoid oily food/i)).toHaveValue(
      'Take complete rest'
    );

    hospitalService.createConsultationNotePreset.mockResolvedValue({
      id: 4,
      text: 'Take complete rest',
      marathiText: 'पूर्ण विश्रांती घ्या.',
      hindiText: 'पूरा आराम करें.',
      displayOrder: 2,
    });
    fireEvent.click(screen.getByRole('button', { name: /add/i }));

    await waitFor(() =>
      expect(hospitalService.createConsultationNotePreset).toHaveBeenCalledWith(
        expect.objectContaining({
          fieldType: 'TREATMENT_NOTES',
          text: 'Take complete rest',
          marathiText: 'पूर्ण विश्रांती घ्या.',
          hindiText: 'पूरा आराम करें.',
        })
      )
    );
  });

  it('shows the doctor assignment control only in admin mode', async () => {
    const { rerender } = render(<NotePresetsManager fieldType="TREATMENT_NOTES" />);
    await screen.findByText('Avoid oily food');
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();

    rerender(<NotePresetsManager fieldType="TREATMENT_NOTES" isAdmin />);
    await waitFor(() => expect(hospitalService.getDoctors).toHaveBeenCalled());
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });
});
