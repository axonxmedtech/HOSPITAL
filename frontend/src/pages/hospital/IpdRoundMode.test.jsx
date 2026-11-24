import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import hospitalService from '../../services/hospitalService';
import nurseService from '../../services/nurseService';
import IpdRoundMode, { vitalsLine } from './IpdRoundMode';

vi.mock('../../services/hospitalService', () => ({
  default: {
    getAdmittedIpdAdmissions: vi.fn(),
    getPrescriptionPresets: vi.fn(),
    getIpdDetails: vi.fn(),
    getIpdRounds: vi.fn(),
    addIpdFollowup: vi.fn(),
    addIpdPrescription: vi.fn(),
    stopPrescription: vi.fn(),
    planDischarge: vi.fn(),
    searchMedicines: vi.fn(),
  },
}));
vi.mock('../../services/nurseService', () => ({
  default: { getVitals: vi.fn(), searchStock: vi.fn() },
}));
vi.mock('../../components/ipd/InvestigationsPanel', () => ({ default: () => <div>tests panel</div> }));
vi.mock('../../components/ipd/DietCard', () => ({ default: () => <div>diet card</div> }));
vi.mock('../../components/ipd/DoctorRoundsTimeline', () => ({ default: () => <div>timeline</div> }));
vi.mock('../../components/ipd/DischargeSummaryModal', () => ({ default: () => <div>plan form</div> }));
const toast = { success: vi.fn(), error: vi.fn(), info: vi.fn() };
vi.mock('../../context/ToastContext', () => ({ useToast: () => toast }));
vi.mock('../../context/ConfirmContext', () => ({ useConfirm: () => async () => true }));

const list = [
  { ipdId: 9, patientName: 'Bed Two', wardName: 'General', bedNumber: 'G-2' },
  { ipdId: 4, patientName: 'Bed One', wardName: 'General', bedNumber: 'G-1' },
  { ipdId: 7, patientName: 'In ICU', wardName: 'MICU', bedNumber: 'M-1', inIcu: true },
];
const details = (over = {}) => ({
  ipdNumber: 'IPD-4',
  status: 'ADMITTED',
  patient: { name: 'Bed One', age: 40, gender: 'MALE' },
  admission: { ward: 'General', bed: 'G-1', primaryDiagnosis: 'Fever' },
  activePrescriptions: [{ id: 31, name: 'Paracetamol', dosage: '500mg', frequency: '1-0-1' }],
  ...over,
});

function Where() {
  const loc = useLocation();
  return <div data-testid="where">{loc.pathname}</div>;
}

const renderAt = (id) =>
  render(
    <MemoryRouter initialEntries={[{ pathname: `/ipd/round/${id}`, state: { from: '/hospital/doctor?tab=ipd' } }]}>
      <Routes>
        <Route
          path="/ipd/round/:id"
          element={
            <>
              <IpdRoundMode />
              <Where />
            </>
          }
        />
        <Route path="/hospital/doctor" element={<div>doctor dashboard</div>} />
      </Routes>
    </MemoryRouter>
  );

beforeEach(() => {
  vi.clearAllMocks();
  hospitalService.getAdmittedIpdAdmissions.mockResolvedValue(list);
  hospitalService.getPrescriptionPresets.mockResolvedValue([
    {
      id: 1,
      name: 'Fever set',
      items: [
        { medicineName: 'Paracetamol 650', dosage: '1 tab', frequency: '1-1-1', duration: '3 days' },
        { medicineName: 'ORS', dosage: '1 sachet', frequency: '1-0-1', duration: '2 days' },
      ],
    },
  ]);
  hospitalService.getIpdDetails.mockResolvedValue(details());
  hospitalService.getIpdRounds.mockResolvedValue([
    { at: '2026-11-20T09:00:00', diagnosis: 'Dengue fever', notes: 'Stable' },
  ]);
  hospitalService.addIpdFollowup.mockResolvedValue({});
  hospitalService.addIpdPrescription.mockResolvedValue({});
  hospitalService.stopPrescription.mockResolvedValue({});
  nurseService.getVitals.mockResolvedValue([
    { recordedAt: '2026-11-24T06:00:00', bpSystolic: 110, bpDiastolic: 70, pulse: 90 },
    { recordedAt: '2026-11-24T08:00:00', bpSystolic: 120, bpDiastolic: 80, pulse: 88, spo2: 97 },
  ]);
});

describe('vitalsLine', () => {
  it('lists only the values that were recorded', () => {
    expect(vitalsLine({ bpSystolic: 120, bpDiastolic: 80, spo2: 97 })).toBe('BP 120/80 · SpO₂ 97%');
    expect(vitalsLine({})).toBeNull();
  });
});

describe('IpdRoundMode', () => {
  it('shows the latest vitals, the running medicines and the last diagnosis on one screen', async () => {
    renderAt(4);
    expect(await screen.findByText(/BP 120\/80 · Pulse 88 · SpO₂ 97%/)).toBeInTheDocument();
    expect(screen.getByText('Paracetamol')).toBeInTheDocument();
    expect(screen.getByLabelText('Diagnosis *')).toHaveValue('Dengue fever');
    expect(screen.getByText('Patient 1 of 3')).toBeInTheDocument(); // ward and bed order
    expect(screen.getByText('tests panel')).toBeInTheDocument();
    expect(screen.getByText('diet card')).toBeInTheDocument();
  });

  it('starts "first" at the first bed in ward order', async () => {
    renderAt('first');
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/ipd/round/4'));
  });

  it('saves the round and every preset medicine in one go, then moves to the next patient', async () => {
    renderAt(4);
    await screen.findByText('Paracetamol');
    fireEvent.change(screen.getByLabelText('Round note'), { target: { value: 'Afebrile' } });
    fireEvent.change(screen.getByLabelText('Add from preset'), { target: { value: '1' } });
    expect(screen.getByText('New medicines (2)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Save & next ›' }));

    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/ipd/round/9'));
    expect(hospitalService.addIpdFollowup).toHaveBeenCalledWith('4', {
      diagnosis: 'Dengue fever',
      notes: 'Afebrile',
      administeredItems: [],
    });
    expect(hospitalService.addIpdPrescription).toHaveBeenCalledTimes(2);
    expect(hospitalService.addIpdPrescription).toHaveBeenCalledWith(
      '4',
      expect.objectContaining({ medicineName: 'Paracetamol 650', dose: '1 tab', frequency: '1-1-1', durationDays: 3 })
    );
    expect(toast.success).toHaveBeenCalledWith('Round saved · 2 medicines ordered');
  });

  it('refuses to save while a new medicine is incomplete', async () => {
    renderAt(4);
    await screen.findByText('Paracetamol');
    fireEvent.click(screen.getByRole('button', { name: '+ Medicine' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(toast.error).toHaveBeenCalledWith('Fill in medicine 1: medicine name');
    expect(hospitalService.addIpdFollowup).not.toHaveBeenCalled();
  });

  it('keeps a refused medicine on screen and stays on the patient', async () => {
    hospitalService.addIpdPrescription
      .mockResolvedValueOnce({})
      .mockRejectedValueOnce({ response: { data: { error: 'Allergy recorded' } } });
    renderAt(4);
    await screen.findByText('Paracetamol');
    fireEvent.change(screen.getByLabelText('Add from preset'), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save & next ›' }));
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('ORS: Allergy recorded'));
    expect(screen.getByTestId('where')).toHaveTextContent('/ipd/round/4');
    expect(screen.getByText('New medicines (1)')).toBeInTheDocument();
  });

  it('stops a running medicine in one click', async () => {
    renderAt(4);
    fireEvent.click(await screen.findByRole('button', { name: 'Stop' }));
    await waitFor(() => expect(hospitalService.stopPrescription).toHaveBeenCalledWith(31));
  });

  it('exits back to the list it came from', async () => {
    renderAt(4);
    await screen.findByText('Paracetamol');
    fireEvent.click(screen.getByRole('button', { name: '← Exit round' }));
    expect(await screen.findByText('doctor dashboard')).toBeInTheDocument();
  });
});
