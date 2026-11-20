import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../services/apiService', () => ({ default: { get: vi.fn(), put: vi.fn() } }));
const success = vi.fn();
const error = vi.fn();
vi.mock('../../context/ToastContext', () => ({ useToast: () => ({ success, error }) }));

import apiClient from '../../services/apiService';
import HelpInsightsTab from './HelpInsightsTab';

const DATA = {
  days: 30,
  questions: [
    {
      key: 'xray report',
      question: 'where is the x-ray report',
      count: 5,
      lastAsked: '2026-11-19T10:00:00',
      roles: ['DOCTOR', 'RECEPTIONIST'],
    },
  ],
  answers: [
    { entryId: 'desk-admit-direct', helpful: 1, notHelpful: 3 },
    { entryId: 'not-a-real-answer', helpful: 9, notHelpful: 0 },
  ],
};

describe('Help Insights', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiClient.get.mockResolvedValue({ data: DATA });
    apiClient.put.mockResolvedValue({ data: { resolved: 5 } });
  });

  it('lists unanswered questions with who asked, and the answer ratings', async () => {
    render(<HelpInsightsTab />);
    expect(await screen.findByText('where is the x-ray report')).toBeInTheDocument();
    expect(screen.getByText(/Asked 5 times/)).toBeInTheDocument();
    expect(screen.getByText(/Doctor, Reception/)).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(
      within(table).getByText('How do I admit a patient (including an emergency)?')
    ).toBeInTheDocument();
    // Ratings for answers that no longer exist are left out.
    expect(within(table).getAllByRole('row')).toHaveLength(2);
    expect(screen.getByText('25%')).toBeInTheDocument();
    expect(apiClient.get).toHaveBeenCalledWith('/platform/help/insights', { params: { days: 30 } });
  });

  it('marks a question answered and changes the period', async () => {
    render(<HelpInsightsTab />);
    await userEvent.click(await screen.findByRole('button', { name: 'Mark answered' }));
    expect(apiClient.put).toHaveBeenCalledWith('/platform/help/questions/resolve', {
      key: 'xray report',
    });
    expect(screen.queryByText('where is the x-ray report')).not.toBeInTheDocument();
    expect(success).toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Last 7 days' }));
    expect(apiClient.get).toHaveBeenLastCalledWith('/platform/help/insights', {
      params: { days: 7 },
    });
  });

  it('offers a retry when it cannot load', async () => {
    apiClient.get.mockRejectedValueOnce(new Error('down'));
    render(<HelpInsightsTab />);
    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('where is the x-ray report')).toBeInTheDocument();
  });
});
