import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import reportsApi from '../../../services/pharmacy/reportsApi';
import ReportsView from './ReportsView';

vi.mock('../../../services/pharmacy/reportsApi', () => ({
  default: { getDashboardData: vi.fn(), exportLedgerCsv: vi.fn() },
}));
vi.mock('../../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

describe('Pharmacy reports', () => {
  it('asks for the last 30 days and shows revenue without GST and refunds', async () => {
    reportsApi.getDashboardData.mockResolvedValue({
      kpis: {
        totalSales: 174,
        totalRefunds: 87,
        netSales: 87,
        netRevenue: 77.68,
        cogs: 120,
        grossProfit: -42.32,
        profitMargin: -54.48,
        inventoryValue: 0,
        expiredValue: 0,
      },
      taxSummary: { inputGst: 0, outputGst: 9.32, netGstPayable: 9.32 },
      expiryRisk: { next30Days: 0, next60Days: 0, next90Days: 0 },
      fastMoving: [],
      categoryValuation: [],
      salesTrend: [],
    });
    render(<ReportsView />);

    await waitFor(() => expect(reportsApi.getDashboardData).toHaveBeenCalled());
    const params = reportsApi.getDashboardData.mock.calls[0][0];
    expect(params.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(params.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(await screen.findByText('Revenue (excl. GST)')).toBeInTheDocument();
    expect(screen.getByText('GST collected (not income):')).toBeInTheDocument();
    expect(screen.getByLabelText('Report period')).toHaveValue('30d');
  });
});
