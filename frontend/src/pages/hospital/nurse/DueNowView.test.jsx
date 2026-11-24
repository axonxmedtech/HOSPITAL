import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import authService from '../../../services/authService';
import hospitalService from '../../../services/hospitalService';
import nurseService from '../../../services/nurseService';
import DueNowView, { describe as describeJob, groupByPatient } from './DueNowView';
import { dueCounts } from './NurseOverviewView';

vi.mock('../../../services/nurseService', () => ({
  default: {
    getWorklist: vi.fn(),
    recordMedication: vi.fn(),
    createVitals: vi.fn(),
    getSeparateNurseLogin: vi.fn(),
    getMyNurses: vi.fn(),
  },
}));
vi.mock('../../../services/hospitalService', () => ({
  default: { markInvestigationSample: vi.fn() },
}));
vi.mock('../../../services/authService', () => ({ default: { getCurrentUser: vi.fn() } }));
vi.mock('../../../hooks/useLiveRefresh', () => ({ default: () => {} }));
const toast = { success: vi.fn(), error: vi.fn(), info: vi.fn() };
vi.mock('../../../context/ToastContext', () => ({ useToast: () => toast }));

const patient = (id, bed, name) => ({
  ipdAdmissionId: id,
  bedCode: bed,
  patientName: name,
  ipdNumber: `IPD-${id}`,
  wardName: 'General',
});
const items = [
  {
    ...patient(2, 'G-2', 'Bed Two'),
    kind: 'DOSE',
    state: 'UPCOMING',
    prescriptionId: 21,
    medicineName: 'Pantoprazole',
    slot: 'NIGHT',
    scheduledTime: '2026-11-24T20:00:00',
    quantityPerDose: 1,
  },
  {
    ...patient(1, 'G-1', 'Bed One'),
    kind: 'DOSE',
    state: 'OVERDUE',
    prescriptionId: 11,
    medicineName: 'Paracetamol',
    dosage: '500mg',
    route: 'ORAL',
    slot: 'MORNING',
    scheduledTime: '2026-11-24T08:00:00',
    quantityPerDose: 2,
  },
  { ...patient(1, 'G-1', 'Bed One'), kind: 'VITALS', state: 'DUE', lastRecordedAt: null },
  {
    ...patient(1, 'G-1', 'Bed One'),
    kind: 'SAMPLE',
    state: 'DUE',
    investigationPublicId: 'lab-1',
    testName: 'CBC',
    priority: 'STAT',
  },
  {
    ...patient(1, 'G-1', 'Bed One'),
    kind: 'DOSE',
    state: 'DONE',
    prescriptionId: 12,
    medicineName: 'Ceftriaxone',
    slot: 'MORNING',
    recordedStatus: 'GIVEN',
    recordedAt: '2026-11-24T08:05:00',
  },
];

beforeEach(() => {
  vi.clearAllMocks();
  authService.getCurrentUser.mockReturnValue({ role: 'NURSE' });
  nurseService.getWorklist.mockResolvedValue(items);
  nurseService.recordMedication.mockResolvedValue({});
  nurseService.createVitals.mockResolvedValue({});
  nurseService.getSeparateNurseLogin.mockResolvedValue(true);
  nurseService.getMyNurses.mockResolvedValue([{ nurseProfileId: 7, name: 'Nurse Seven' }]);
  hospitalService.markInvestigationSample.mockResolvedValue({});
});

describe('groupByPatient', () => {
  it('puts patients in bed order and their jobs most urgent first', () => {
    const groups = groupByPatient(items);
    expect(groups.map((g) => g.bedCode)).toEqual(['G-1', 'G-2']);
    expect(groups[0].items.map((i) => i.state)).toEqual(['OVERDUE', 'DUE', 'DUE', 'DONE']);
  });

  it('describes a dose with its dose, route, units and time', () => {
    const d = describeJob(items[1]);
    expect(d.title).toBe('Paracetamol');
    expect(d.detail).toBe('500mg · ORAL · 2 units');
    expect(d.when).toMatch(/^Morning · 8:00/);
  });
});

describe('dueCounts', () => {
  it('counts what is due or overdue now, by kind', () => {
    expect(dueCounts(items)).toEqual({ doses: 1, overdue: 1, vitals: 1, samples: 1 });
  });
});

describe('DueNowView', () => {
  it('shows what is due now, and the rest behind the other filters', async () => {
    render(<DueNowView />);
    expect(await screen.findByText('Paracetamol')).toBeInTheDocument();
    expect(screen.getByText('CBC')).toBeInTheDocument();
    expect(screen.queryByText('Pantoprazole')).toBeNull(); // later today
    expect(screen.queryByText('Ceftriaxone')).toBeNull(); // done
    fireEvent.click(screen.getByRole('button', { name: /Later today/ }));
    expect(screen.getByText('Pantoprazole')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Done/ }));
    expect(screen.getByText(/Given 8:05/)).toBeInTheDocument();
  });

  it('records a dose against its dose time in one tap', async () => {
    render(<DueNowView />);
    const row = (await screen.findByText('Paracetamol')).closest('li');
    fireEvent.click(within(row).getByRole('button', { name: 'Given ✓' }));
    await waitFor(() =>
      expect(nurseService.recordMedication).toHaveBeenCalledWith({
        ipdAdmissionId: 1,
        prescriptionId: 11,
        status: 'GIVEN',
        scheduledTime: '2026-11-24T08:00:00',
        quantity: 2,
      })
    );
    expect(nurseService.getWorklist).toHaveBeenCalledTimes(2); // reloaded
  });

  it('marks a sample collected in one tap', async () => {
    render(<DueNowView />);
    const row = (await screen.findByText('CBC')).closest('li');
    fireEvent.click(within(row).getByRole('button', { name: 'Collected ✓' }));
    await waitFor(() => expect(hospitalService.markInvestigationSample).toHaveBeenCalledWith('lab-1'));
  });

  it('records vitals without leaving the list', async () => {
    render(<DueNowView />);
    fireEvent.click(await screen.findByRole('button', { name: 'Record vitals' }));
    fireEvent.change(screen.getByLabelText('BP systolic'), { target: { value: '120' } });
    fireEvent.change(screen.getByLabelText('BP diastolic'), { target: { value: '80' } });
    fireEvent.change(screen.getByLabelText('Pulse'), { target: { value: '88' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save vitals' }));
    await waitFor(() =>
      expect(nurseService.createVitals).toHaveBeenCalledWith({
        ipdAdmissionId: 1,
        bpSystolic: 120,
        bpDiastolic: 80,
        pulse: 88,
        temperature: null,
        spo2: null,
        respiratoryRate: null,
      })
    );
  });

  it('with a shared nurse login, asks once who is giving the care and sends it with each dose', async () => {
    authService.getCurrentUser.mockReturnValue({ role: 'NURSE_INCHARGE' });
    nurseService.getSeparateNurseLogin.mockResolvedValue(false);
    render(<DueNowView />);
    const row = (await screen.findByText('Paracetamol')).closest('li');
    await screen.findByRole('option', { name: 'Nurse Seven' });
    fireEvent.click(within(row).getByRole('button', { name: 'Given ✓' }));
    expect(toast.error).toHaveBeenCalledWith('Choose who is giving the care (Recording for) first');
    expect(nurseService.recordMedication).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Recording for'), { target: { value: '7' } });
    fireEvent.click(within(row).getByRole('button', { name: 'Given ✓' }));
    await waitFor(() =>
      expect(nurseService.recordMedication).toHaveBeenCalledWith(
        expect.objectContaining({ prescriptionId: 11, performedByNurseId: 7 })
      )
    );
  });

  it('says so when nothing is due', async () => {
    nurseService.getWorklist.mockResolvedValue([items[0]]);
    render(<DueNowView />);
    expect(await screen.findByText('Nothing due right now')).toBeInTheDocument();
  });
});
