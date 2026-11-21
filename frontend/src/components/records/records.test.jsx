import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PatientFile from '../../pages/hospital/PatientFile';
import apiClient from '../../services/apiService';
import { rangeFor, rangeParams } from '../../utils/dateRange';
import ConfirmationModal from '../ConfirmationModal';
import IpdHistoryView from './IpdHistoryView';

vi.mock('../../services/apiService', () => ({ default: { get: vi.fn() } }));
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

const iso = (d) => d.toISOString().slice(0, 10);

beforeEach(() => vi.clearAllMocks());

describe('date ranges', () => {
  it('turns a quick pick into from/to dates, and "All time" into no dates', () => {
    const r = rangeFor('7d');
    const days = (new Date(r.to) - new Date(r.from)) / 86400000;
    expect(days).toBe(6);
    expect(rangeParams(rangeFor('all'))).toEqual({});
    expect(rangeParams({ from: '2026-01-01', to: '' })).toEqual({ from: '2026-01-01' });
  });
});

describe('IPD history', () => {
  it('asks the server for discharged admissions in the last 30 days and lists them', async () => {
    apiClient.get.mockResolvedValue({
      data: {
        content: [
          {
            ipd: {
              id: 7,
              ipdNumber: 'IPD-7',
              status: 'DISCHARGED',
              admissionDatetime: '2026-11-01T10:00:00',
              dischargeDatetime: '2026-11-04T10:00:00',
            },
            patient: { publicId: 'p-1', name: 'Kavita Shinde', phone: '9800000000' },
            doctor: { name: 'Dr Asha' },
            ward: { wardName: 'General' },
          },
        ],
        totalElements: 1,
        totalPages: 1,
      },
    });
    render(
      <MemoryRouter>
        <IpdHistoryView />
      </MemoryRouter>
    );
    expect(await screen.findByText('IPD-7')).toBeInTheDocument();
    const [url, { params }] = apiClient.get.mock.calls[0];
    expect(url).toBe('/hospital/ipd');
    expect(params.view).toBe('DISCHARGED');
    expect(params.from).toBeTruthy();
    expect(params.to).toBe(iso(new Date(Date.now() - new Date().getTimezoneOffset() * 60000)));
    expect(screen.getByRole('button', { name: 'Kavita Shinde' })).toBeInTheDocument();
  });

  it('switches to All and drops the dates for All time', async () => {
    apiClient.get.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } });
    render(
      <MemoryRouter>
        <IpdHistoryView />
      </MemoryRouter>
    );
    await screen.findByText('No admissions match these filters.');
    await userEvent.click(screen.getByRole('tab', { name: 'All' }));
    await userEvent.selectOptions(screen.getByRole('combobox', { name: /date/i }), 'all');
    await waitFor(() => {
      const params = apiClient.get.mock.calls.at(-1)[1].params;
      expect(params.view).toBe('ALL');
      expect(params.from).toBeUndefined();
    });
  });
});

const fileFor = (access) => ({
  patient: { publicId: 'p-1', name: 'Sunanda Joshi', isActive: true, medicalHistory: 'Diabetic' },
  access,
  counts: { opdVisits: 1, ipdStays: 1, icuStays: 0, surgeries: 0 },
  opdVisits: [
    { id: 3, caseId: 'OPD-3', status: 'COMPLETED', problem: access.clinical ? 'Fever' : undefined },
  ],
  ipdStays: [
    { id: 9, ipdNumber: 'IPD-9', status: 'DISCHARGED', admittedAt: '2026-11-01T10:00:00' },
  ],
  icuStays: [],
  surgeries: [],
  bills: access.billing
    ? [{ id: 5, customId: 'BILL-5', category: 'IPD', status: 'PENDING', totalAmount: 550 }]
    : [],
  billTotals: { totalAmount: 550, paidAmount: 0, balance: 550 },
  documents: [],
});

const renderFile = () =>
  render(
    <MemoryRouter initialEntries={['/patients/p-1/file']}>
      <Routes>
        <Route path="/patients/:publicId/file" element={<PatientFile />} />
      </Routes>
    </MemoryRouter>
  );

describe('Patient File', () => {
  it('shows every visit, stay and bill to a clinical role with billing access', async () => {
    apiClient.get.mockResolvedValue({ data: fileFor({ clinical: true, billing: true }) });
    renderFile();
    expect(await screen.findByRole('heading', { name: 'Sunanda Joshi' })).toBeInTheDocument();
    expect(screen.getByText('OPD-3')).toBeInTheDocument();
    expect(screen.getByText('IPD-9')).toBeInTheDocument();
    expect(screen.getByText('BILL-5')).toBeInTheDocument();
    expect(screen.getByText('Fever')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Clinical timeline/ })).toBeInTheDocument();
  });

  it('gives reception no clinical tabs or detail', async () => {
    apiClient.get.mockResolvedValue({ data: fileFor({ clinical: false, billing: true }) });
    renderFile();
    await screen.findByRole('heading', { name: 'Sunanda Joshi' });
    expect(screen.queryByText('Diabetic')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Clinical timeline/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Documents/ })).not.toBeInTheDocument();
    expect(screen.getByText(/visible to doctors and nurses only/)).toBeInTheDocument();
  });

  it('says why when the server refuses', async () => {
    apiClient.get.mockRejectedValue({
      response: {
        status: 403,
        data: { error: 'You can open the file of patients currently in your care only' },
      },
    });
    renderFile();
    expect(await screen.findByRole('alert')).toHaveTextContent('currently in your care');
  });
});

describe('typed confirmation', () => {
  it('keeps the confirm button disabled until the exact name is typed', async () => {
    const onConfirm = vi.fn().mockResolvedValue();
    render(
      <ConfirmationModal
        isOpen
        title="Delete permanently"
        message="x"
        showReasonInput
        confirmText="City Care"
        confirmLabel="Delete permanently"
        onConfirm={onConfirm}
        onCancel={() => {}}
      />
    );
    const button = screen.getByRole('button', { name: 'Delete permanently' });
    // (the dialog moves focus to Cancel on open, so set values directly rather than typing)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: 'Contract ended' } });
    fireEvent.change(screen.getByLabelText(/to confirm/), { target: { value: 'City Car' } });
    expect(button).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/to confirm/), { target: { value: ' city care ' } });
    expect(button).toBeEnabled();
    fireEvent.click(button);
    await waitFor(() => expect(onConfirm).toHaveBeenCalled());
    expect(onConfirm).toHaveBeenCalledWith('Contract ended', 'city care');
  });
});

describe('date range picker', () => {
  it('warns when "To" is before "From" and sends no dates until it is fixed', async () => {
    const { DateRangeFilter } = await import('../RecordsFilterBar');
    const { rangeParams: params } = await import('../../utils/dateRange');
    const backwards = { preset: 'custom', from: '2026-11-10', to: '2026-11-01' };
    render(<DateRangeFilter range={backwards} onChange={() => {}} label="Bill date" />);
    expect(screen.getByRole('alert')).toHaveTextContent('before the “From” date');
    expect(params(backwards)).toEqual({});
    expect(params({ preset: 'custom', from: '2026-11-01', to: '2026-11-10' })).toEqual({
      from: '2026-11-01',
      to: '2026-11-10',
    });
  });
});
