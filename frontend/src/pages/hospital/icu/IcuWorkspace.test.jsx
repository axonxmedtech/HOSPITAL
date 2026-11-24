import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import hospitalService from '../../../services/hospitalService';
import icuService from '../../../services/icuService';
import wardService from '../../../services/wardService';
import IcuHousekeeping from './IcuHousekeeping';
import IcuWorkspace, { toAdmissionRow } from './IcuWorkspace';

vi.mock('../../../services/icuService', () => ({
  default: { getBoard: vi.fn(), getUnits: vi.fn(), getIcuPatients: vi.fn() },
}));
vi.mock('../../../services/wardService', () => ({ default: { markBedCleaned: vi.fn() } }));
vi.mock('../../../services/hospitalService', () => ({
  default: {
    getIcuCleaningTasks: vi.fn(),
    createIcuCleaningTask: vi.fn(),
    updateIcuCleaningTaskStatus: vi.fn(),
    deleteIcuCleaningTask: vi.fn(),
    getIpdDetails: vi.fn(),
  },
}));
vi.mock('../../../components/IcuAdmitModal', () => ({
  default: ({ initialBed, initialPatient }) => (
    <div>
      ICU admit {initialBed ? `bed ${initialBed.bedId}` : 'any bed'}
      {initialPatient ? ` for ${initialPatient.patientName}` : ''}
    </div>
  ),
}));
vi.mock('../../../components/IcuDischargeToIpdModal', () => ({
  default: ({ initialIcuPatient }) => <div>Move out of ICU for {initialIcuPatient.patientName}</div>,
}));
vi.mock('../../../components/records/IcuHistoryView', () => ({ default: () => <div>history</div> }));
vi.mock('../../../hooks/useLiveRefresh', () => ({ default: () => {} }));
const toast = { success: vi.fn(), error: vi.fn(), info: vi.fn() };
vi.mock('../../../context/ToastContext', () => ({ useToast: () => toast }));
vi.mock('../../../context/ConfirmContext', () => ({ useConfirm: () => async () => true }));

const counts = { totalBeds: 3, occupied: 1, available: 1, cleaning: 1, patients: 1 };
const board = {
  hasCriticalCareUnits: true,
  totals: counts,
  units: [{ wardId: 10, wardName: 'MICU', unitTypeLabel: 'Medical ICU', counts }],
  beds: [
    { bedId: 1, bedCode: 'M-1', wardId: 10, wardName: 'MICU', status: 'occupied', ipdAdmissionId: 7, patientName: 'Ravi Test' },
    { bedId: 2, bedCode: 'M-2', wardId: 10, wardName: 'MICU', status: 'cleaning' },
    { bedId: 3, bedCode: 'M-3', wardId: 10, wardName: 'MICU', status: 'available' },
  ],
};
const icuPatient = {
  ipdId: 7,
  ipdNumber: 'IPD-7',
  patientName: 'Ravi Test',
  doctorName: 'Dr A',
  intensivistName: 'Dr B',
  icuWardName: 'MICU',
  unitType: 'MICU',
  bedNumber: 'M-1',
  status: 'DISCHARGE_PLANNED',
  admissionConfirmed: true,
};

beforeEach(() => {
  vi.clearAllMocks();
  icuService.getBoard.mockResolvedValue(board);
  icuService.getUnits.mockResolvedValue(board);
  icuService.getIcuPatients.mockResolvedValue([icuPatient]);
  wardService.markBedCleaned.mockResolvedValue({});
  hospitalService.getIcuCleaningTasks.mockResolvedValue([]);
  hospitalService.createIcuCleaningTask.mockResolvedValue({});
});

const renderAt = (props, url = '/hospital/receptionist?tab=icu') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <IcuWorkspace {...props} />
    </MemoryRouter>
  );

describe('toAdmissionRow', () => {
  it('shows the intensivist beside the admitting doctor and marks the row as ICU', () => {
    expect(toAdmissionRow(icuPatient)).toMatchObject({
      ipdId: 7,
      doctorName: 'Dr A · ICU: Dr B',
      wardName: 'MICU',
      inIcu: true,
      admissionConfirmed: true,
    });
  });
});

describe('IcuWorkspace', () => {
  it('gives each bed its one job: mark cleaned, admit here', async () => {
    renderAt({ canDesk: true, canHousekeep: true });
    fireEvent.click(await screen.findByRole('button', { name: 'Mark cleaned' }));
    await waitFor(() => expect(wardService.markBedCleaned).toHaveBeenCalledWith(2));
    fireEvent.click(screen.getByRole('button', { name: 'Admit here' }));
    expect(screen.getByText('ICU admit bed 3')).toBeInTheDocument();
  });

  it('is read-only for a doctor who does not run the desk', async () => {
    renderAt({ canDesk: false });
    await screen.findByText('M-2');
    expect(screen.queryByRole('button', { name: 'Mark cleaned' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Admit here' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Move patient to ICU' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Housekeeping' })).toBeNull();
  });

  it('counts the ICU patients ready to go and checks them out from the list', async () => {
    renderAt({ canDesk: true, hasBilling: true }, '/x?tab=icu&icuView=patients');
    expect(
      await screen.findByRole('button', { name: /Patients \(1\) · 1 ready to go/ })
    ).toBeInTheDocument();
    const row = (await screen.findAllByText('Ravi Test')).map((el) => el.closest('tr')).find(Boolean);
    expect(within(row).getByText('MICU')).toBeInTheDocument();
    // Every row here is in critical care, so no row repeats an ICU badge.
    expect(within(row).queryByText('ICU')).toBeNull();
    fireEvent.click(within(row).getAllByRole('button').at(-1));
    fireEvent.click(await screen.findByText('Move out of ICU'));
    expect(screen.getByText('Move out of ICU for Ravi Test')).toBeInTheDocument();
  });
});

describe('IcuHousekeeping', () => {
  it('ties a task to a real bed picked from the list', async () => {
    render(<IcuHousekeeping />);
    fireEvent.click(await screen.findByRole('button', { name: '+ Add task' }));
    await screen.findByRole('option', { name: 'MICU · M-2' });
    fireEvent.change(screen.getByLabelText('Bed'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Task *'), { target: { value: 'Deep clean' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add task' }));
    await waitFor(() =>
      expect(hospitalService.createIcuCleaningTask).toHaveBeenCalledWith({
        taskDescription: 'Deep clean',
        assignedTo: null,
        priority: 'MEDIUM',
        bedId: 2,
        wardId: 10,
        wardName: 'MICU',
        bedNumber: 'M-2',
      })
    );
  });
});
