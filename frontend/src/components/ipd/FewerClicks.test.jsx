import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import hospitalService from '../../services/hospitalService';
import wardService from '../../services/wardService';
import { printBlob } from '../../utils/printPdf';
import CurrentAdmissionsTable from './CurrentAdmissionsTable';
import DischargeCheckoutModal, { checkoutMoney, payInTurn } from './DischargeCheckoutModal';

vi.mock('../../services/hospitalService', () => ({
  default: {
    getIpdDetails: vi.fn(),
    payBilling: vi.fn(),
    confirmDischarge: vi.fn(),
    downloadDischargePack: vi.fn(),
    getIpdBill: vi.fn(),
    downloadReceipt: vi.fn(),
  },
}));
vi.mock('../../services/wardService', () => ({ default: { markBedCleaned: vi.fn() } }));
vi.mock('../../utils/printPdf', () => ({ printBlob: vi.fn() }));
vi.mock('../../pages/hospital/nurse/AdmissionFormModal', () => ({
  default: ({ admissionId }) => <div>Admission form for {admissionId}</div>,
}));
const toast = { success: vi.fn(), error: vi.fn(), info: vi.fn() };
vi.mock('../../context/ToastContext', () => ({ useToast: () => toast }));

const planned = {
  ipdNumber: 'IPD-7',
  status: 'DISCHARGE_PLANNED',
  dischargeType: 'NORMAL',
  patient: { name: 'Test Patient' },
  admission: { bed: 'B-2', bedId: 22 },
  billing: {
    totalAmount: 30000,
    paidAmount: 10000,
    balance: 20000,
    insuranceExpected: 15000,
    outstandingBills: [
      { billingId: 1, customId: 'BILL-1', billingType: 'IPD', balance: 20000 },
      { billingId: 2, customId: 'BILL-2', billingType: 'OPD', balance: 500 },
    ],
  },
};

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.getIpdDetails.mockResolvedValue(planned);
  hospitalService.payBilling.mockResolvedValue({});
  hospitalService.confirmDischarge.mockResolvedValue({});
  hospitalService.downloadDischargePack.mockResolvedValue(new Blob(['pdf']));
  wardService.markBedCleaned.mockResolvedValue({});
});

describe('checkoutMoney', () => {
  it('takes the insurer share off the IPD bill and keeps other bills whole', () => {
    const m = checkoutMoney(planned.billing);
    expect(m.bills).toEqual([
      { billingId: 1, customId: 'BILL-1', billingType: 'IPD', due: 5000 },
      { billingId: 2, customId: 'BILL-2', billingType: 'OPD', due: 500 },
    ]);
    expect(m.due).toBe(5500);
    expect(m.unusedAdvance).toBe(0);
  });

  it('reports an unused advance when the IPD bill is in credit', () => {
    const m = checkoutMoney({ balance: -2500, insuranceExpected: 0, outstandingBills: [] });
    expect(m).toMatchObject({ due: 0, unusedAdvance: 2500, bills: [] });
  });
});

describe('DischargeCheckoutModal', () => {
  it('collects every bill, discharges, then prints the pack and frees the bed', async () => {
    const onDischarged = vi.fn();
    render(
      <DischargeCheckoutModal
        ipdId={7}
        data={planned}
        onClose={vi.fn()}
        onDischarged={onDischarged}
      />
    );
    fireEvent.change(screen.getByLabelText('Paid by'), { target: { value: 'UPI' } });
    const go = screen.getByRole('button', { name: /Collect ₹5,500 & discharge/ });
    expect(go).toBeDisabled(); // UPI needs its UTR
    fireEvent.change(screen.getByLabelText('UTR *'), { target: { value: 'UTR9' } });
    fireEvent.click(go);

    await screen.findByText('Discharged');
    expect(hospitalService.payBilling).toHaveBeenNthCalledWith(1, 1, {
      amount: 5000,
      mode: 'UPI',
      reference: 'UTR9',
      idempotencyKey: expect.stringMatching(/^ipdpay-/),
    });
    expect(hospitalService.payBilling).toHaveBeenNthCalledWith(2, 2, {
      amount: 500,
      mode: 'UPI',
      reference: 'UTR9',
      idempotencyKey: expect.stringMatching(/^ipdpay-/),
    });
    // Each bill gets its own key, so the two payments are never mistaken for one retried.
    const [[, first], [, second]] = hospitalService.payBilling.mock.calls;
    expect(first.idempotencyKey).not.toBe(second.idempotencyKey);
    expect(hospitalService.confirmDischarge).toHaveBeenCalledWith(7, {
      dischargeType: 'NORMAL',
      dischargeNote: null,
    });
    expect(onDischarged).toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Print summary + bill' }));
    await waitFor(() => expect(printBlob).toHaveBeenCalled());
    expect(hospitalService.downloadDischargePack).toHaveBeenCalledWith(7);
    fireEvent.click(screen.getByRole('button', { name: 'Mark bed cleaned' }));
    await screen.findByRole('button', { name: 'Bed available ✓' });
    expect(wardService.markBedCleaned).toHaveBeenCalledWith(22);
  });

  it('stays open and shows the new balance when the discharge is refused after a payment', async () => {
    hospitalService.confirmDischarge.mockRejectedValue({
      response: { data: { error: 'A bill is still unpaid' } },
    });
    render(<DischargeCheckoutModal ipdId={7} data={planned} onClose={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: /Collect ₹5,500 & discharge/ }));
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('A bill is still unpaid'));
    expect(hospitalService.getIpdDetails).toHaveBeenCalledWith(7);
    expect(screen.getByText('Discharge checkout')).toBeInTheDocument();
  });

  it('lets a death or absconding leave the bill open, and returns an unused advance', async () => {
    render(
      <DischargeCheckoutModal
        ipdId={7}
        data={{
          ...planned,
          billing: {
            ...planned.billing,
            outstandingBills: [],
            balance: -1000,
            insuranceExpected: 0,
          },
        }}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByText(/₹1,000 of the advance is unused/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Return the advance by'), { target: { value: 'UPI' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm discharge' }));
    await screen.findByText('Discharged');
    expect(hospitalService.payBilling).not.toHaveBeenCalled();
    expect(hospitalService.confirmDischarge).toHaveBeenCalledWith(7, {
      dischargeType: 'NORMAL',
      dischargeNote: null,
      refundMode: 'UPI',
    });
  });
});

const rows = [
  {
    ipdId: 1,
    ipdNumber: 'IPD-1',
    patientName: 'Asha Test',
    status: 'ADMITTED',
    admissionConfirmed: true,
  },
  {
    ipdId: 2,
    ipdNumber: 'IPD-2',
    patientName: 'Ravi Test',
    status: 'DISCHARGE_PLANNED',
    admissionConfirmed: true,
  },
  {
    ipdId: 3,
    ipdNumber: 'IPD-3',
    patientName: 'Meera Test',
    status: 'ADMITTED',
    admissionConfirmed: false,
  },
];

function Where() {
  const loc = useLocation();
  return (
    <div data-testid="where">
      {loc.pathname}
      {loc.search}|{loc.state?.from}
    </div>
  );
}

const renderTable = (props = {}, url = '/dashboard?tab=ipd') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route
          path="/dashboard"
          element={<CurrentAdmissionsTable rows={rows} canDesk hasBilling {...props} />}
        />
        <Route path="/ipd/:id" element={<Where />} />
      </Routes>
    </MemoryRouter>
  );

describe('CurrentAdmissionsTable', () => {
  it('counts and filters the patients ready to go', () => {
    renderTable();
    expect(screen.getByRole('button', { name: /Ready to go\s*1/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Form pending\s*1/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Ready to go/ }));
    expect(screen.getAllByText('Ravi Test').length).toBeGreaterThan(0);
    expect(screen.queryByText('Asha Test')).not.toBeInTheDocument();
  });

  it('opens the checkout from the row without leaving the list', async () => {
    renderTable();
    fireEvent.click(screen.getAllByRole('button', { name: 'Checkout' })[0]);
    expect(await screen.findByText('Discharge checkout')).toBeInTheDocument();
    expect(hospitalService.getIpdDetails).toHaveBeenCalledWith(2);
  });

  it('opens a case in-app and remembers the list it came from', () => {
    renderTable({}, '/dashboard?tab=ipd&ipdFilter=form');
    fireEvent.click(screen.getAllByRole('button', { name: 'Meera Test' })[0]);
    expect(screen.getByTestId('where')).toHaveTextContent(
      '/ipd/3|/dashboard?tab=ipd&ipdFilter=form'
    );
  });

  it('announces a discharge planned while the list is open', () => {
    const view = renderTable({ rows: rows.filter((r) => r.ipdId !== 2) });
    expect(toast.info).not.toHaveBeenCalled();
    view.rerender(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={<CurrentAdmissionsTable rows={rows} canDesk hasBilling />}
          />
        </Routes>
      </MemoryRouter>
    );
    expect(toast.info).toHaveBeenCalledWith('Discharge planned: Ravi Test is ready to go');
  });

  it('offers the admission form only while it is pending', async () => {
    renderTable();
    const row = screen
      .getAllByText('Meera Test')
      .map((el) => el.closest('tr'))
      .find(Boolean);
    fireEvent.click(within(row).getAllByRole('button').at(-1)); // the ⋮ menu
    expect(within(row).queryByText('Discharge checkout')).toBeNull();
    fireEvent.click(await screen.findByText('Complete admission form'));
    expect(screen.getByText('Admission form for 3')).toBeInTheDocument();
  });
});

describe('payInTurn', () => {
  it('pays one bill after another and reports each payment', async () => {
    const order = [];
    hospitalService.payBilling.mockImplementation(async (id) => {
      order.push(`pay ${id}`);
    });
    const paid = vi.fn(() => order.push('paid'));
    await payInTurn(
      [
        { billingId: 1, due: 10 },
        { billingId: 2, due: 20 },
      ],
      { mode: 'CASH', reference: null },
      paid
    );
    expect(order).toEqual(['pay 1', 'paid', 'pay 2', 'paid']);
    expect(hospitalService.payBilling).toHaveBeenLastCalledWith(2, {
      mode: 'CASH',
      reference: null,
      amount: 20,
    });
  });
});
