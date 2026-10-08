import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ToastProvider } from '../context/ToastContext';
import platformService from '../services/platformService';
import PlatformStatementsTab from './PlatformStatementsTab';

vi.mock('../services/platformService', () => ({
  default: {
    getStatements: vi.fn(),
    createStatement: vi.fn(),
    updateStatement: vi.fn(),
    deleteStatement: vi.fn(),
  },
}));

describe('PlatformStatementsTab', () => {
  const sampleStatements = [
    {
      id: 1,
      hospitalType: 'ALL',
      category: 'MEDICINE_INSTRUCTION',
      englishText: 'Take with water',
      marathiText: 'पाण्यासोबत घ्या',
      hindiText: 'पानी के साथ लें',
      displayOrder: 1,
      isActive: true,
    },
    {
      id: 2,
      hospitalType: 'ALL',
      category: 'DOCTOR_ADVICE',
      englishText: 'Drink more water.',
      marathiText: 'जास्त पाणी प्या.',
      hindiText: 'अधिक पानी पिएं।',
      displayOrder: 2,
      isActive: true,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    platformService.getStatements.mockResolvedValue(sampleStatements);
  });

  const renderComponent = (hospitalType = 'HOSPITAL') => {
    return render(
      <ToastProvider>
        <PlatformStatementsTab hospitalType={hospitalType} />
      </ToastProvider>
    );
  };

  it('renders statements with English, Marathi and Hindi translations', async () => {
    renderComponent('HOSPITAL');

    expect(platformService.getStatements).toHaveBeenCalledWith('HOSPITAL');
    await waitFor(() => {
      expect(screen.getByText('Take with water')).toBeInTheDocument();
      expect(screen.getByText('पाण्यासोबत घ्या')).toBeInTheDocument();
      expect(screen.getByText('पानी के साथ लें')).toBeInTheDocument();
      expect(screen.getByText('Drink more water.')).toBeInTheDocument();
      expect(screen.getByText('जास्त पाणी प्या.')).toBeInTheDocument();
      expect(screen.getByText('अधिक पानी पिएं।')).toBeInTheDocument();
    });
  });

  it('filters statements by category and search text', async () => {
    const user = userEvent.setup();
    renderComponent();

    await waitFor(() => expect(screen.getByText('Take with water')).toBeInTheDocument());

    // Filter by Doctor Advice
    const select = screen.getByRole('combobox');
    await user.selectOptions(select, 'DOCTOR_ADVICE');

    expect(screen.queryByText('Take with water')).not.toBeInTheDocument();
    expect(screen.getByText('Drink more water.')).toBeInTheDocument();

    // Reset filter and search
    await user.selectOptions(select, '');
    const searchInput = screen.getByPlaceholderText('Search instructions...');
    await user.type(searchInput, 'पाण्या');

    expect(screen.getByText('Take with water')).toBeInTheDocument();
    expect(screen.queryByText('Drink more water.')).not.toBeInTheDocument();
  });

  it('allows adding a new consultation statement', async () => {
    const user = userEvent.setup();
    platformService.createStatement.mockResolvedValue({
      id: 3,
      hospitalType: 'HOSPITAL',
      category: 'MEDICINE_INSTRUCTION',
      englishText: 'After meals',
      marathiText: 'जेवणानंतर',
      hindiText: 'भोजन के बाद',
      displayOrder: 10,
      isActive: true,
    });

    renderComponent('HOSPITAL');
    await waitFor(() => expect(screen.getByText('Take with water')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /Add Statement/i }));
    expect(screen.getByText('Add Consultation Statement')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('e.g. Take with warm water'), 'After meals');
    await user.type(screen.getByPlaceholderText('उदा. कोमट पाण्यासोबत घ्या'), 'जेवणानंतर');
    await user.type(screen.getByPlaceholderText('उदा. गुनगुने पानी के साथ लें'), 'भोजन के बाद');

    await user.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => {
      expect(platformService.createStatement).toHaveBeenCalledWith(
        expect.objectContaining({
          category: 'MEDICINE_INSTRUCTION',
          englishText: 'After meals',
          marathiText: 'जेवणानंतर',
          hindiText: 'भोजन के बाद',
        }),
        'HOSPITAL'
      );
    });
  });

  it('allows editing an existing statement', async () => {
    const user = userEvent.setup();
    platformService.updateStatement.mockResolvedValue({
      ...sampleStatements[0],
      englishText: 'Take with warm water',
    });

    renderComponent('HOSPITAL');
    await waitFor(() => expect(screen.getByText('Take with water')).toBeInTheDocument());

    const editButtons = screen.getAllByRole('button', { name: 'Edit' });
    await user.click(editButtons[0]);

    expect(screen.getByText('Edit Statement')).toBeInTheDocument();
    const englishInput = screen.getByDisplayValue('Take with water');
    await user.clear(englishInput);
    await user.type(englishInput, 'Take with warm water');

    await user.click(screen.getByRole('button', { name: 'Update' }));

    await waitFor(() => {
      expect(platformService.updateStatement).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          englishText: 'Take with warm water',
        }),
        'HOSPITAL'
      );
    });
  });

  it('allows deleting a statement after confirmation', async () => {
    const user = userEvent.setup();
    platformService.deleteStatement.mockResolvedValue({ message: 'Deleted' });

    renderComponent('HOSPITAL');
    await waitFor(() => expect(screen.getByText('Take with water')).toBeInTheDocument());

    const deleteButtons = screen.getAllByRole('button', { name: 'Delete' });
    await user.click(deleteButtons[0]);

    // Modal opens
    expect(screen.getByText('Delete Statement')).toBeInTheDocument();
    const allDeleteBtns = screen.getAllByRole('button', { name: 'Delete' });
    await user.click(allDeleteBtns[allDeleteBtns.length - 1]);

    await waitFor(() => {
      expect(platformService.deleteStatement).toHaveBeenCalledWith(1, 'HOSPITAL');
    });
  });
});
