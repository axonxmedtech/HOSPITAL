import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import authService from '../../../services/authService';
import nurseService from '../../../services/nurseService';
import InitialAssessmentPanel from './InitialAssessmentPanel';
import VulnerabilityAssessmentPanel from './VulnerabilityAssessmentPanel';

vi.mock('../../../services/nurseService', () => ({
  default: {
    getAdmissionForm: vi.fn(),
    getMedicationChart: vi.fn(),
    getInitialAssessment: vi.fn(),
    saveInitialAssessment: vi.fn(),
    getVulnerabilityAssessment: vi.fn(),
    saveVulnerabilityAssessment: vi.fn(),
    getSeparateNurseLogin: vi.fn(),
    getWardStaffNurses: vi.fn(),
  },
}));
vi.mock('../../../services/authService', () => ({ default: { getCurrentUser: vi.fn() } }));
const toast = { success: vi.fn(), error: vi.fn(), info: vi.fn() };
vi.mock('../../../context/ToastContext', () => ({ useToast: () => toast }));

const NURSES = [
  { id: 31, name: 'Asha Patil' },
  { id: 32, name: 'Meena Rao' },
];

const panels = [
  {
    name: 'Initial Assessment',
    Panel: InitialAssessmentPanel,
    save: () => nurseService.saveInitialAssessment,
    load: (saved) => nurseService.getInitialAssessment.mockResolvedValue(saved),
  },
  {
    name: 'Vulnerability Assessment',
    Panel: VulnerabilityAssessmentPanel,
    save: () => nurseService.saveVulnerabilityAssessment,
    load: (saved) => nurseService.getVulnerabilityAssessment.mockResolvedValue(saved),
  },
];

beforeEach(() => {
  vi.clearAllMocks();
  nurseService.getAdmissionForm.mockResolvedValue({ wardId: 5, patientFirstName: 'Ravi' });
  nurseService.getMedicationChart.mockResolvedValue([]);
  nurseService.getWardStaffNurses.mockResolvedValue(NURSES);
  nurseService.saveInitialAssessment.mockImplementation(async (_id, p) => p);
  nurseService.saveVulnerabilityAssessment.mockImplementation(async (_id, p) => p);
  panels.forEach((p) => p.load({}));
});

describe.each(panels)('$name — Performed By Nurse', ({ Panel, save, load }) => {
  it('under shared login, a nurse must name who did it, and the choice is sent', async () => {
    authService.getCurrentUser.mockReturnValue({ role: 'NURSE_INCHARGE' });
    nurseService.getSeparateNurseLogin.mockResolvedValue(false);
    render(<Panel admissionId={9} />);

    const select = await screen.findByLabelText(/Performed By Nurse/);
    await screen.findByRole('option', { name: 'Meena Rao' });

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(toast.error).toHaveBeenCalledWith('Select the nurse who performed this assessment');
    expect(save()).not.toHaveBeenCalled();

    fireEvent.change(select, { target: { value: '32' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(save()).toHaveBeenCalled());
    expect(save().mock.calls[0][1]).toMatchObject({ performedByNurseId: 32 });
  });

  it('re-opening a saved assessment starts on the nurse already credited', async () => {
    authService.getCurrentUser.mockReturnValue({ role: 'NURSE_INCHARGE' });
    nurseService.getSeparateNurseLogin.mockResolvedValue(false);
    load({ performedByNurseId: 31 });
    render(<Panel admissionId={9} />);

    const select = await screen.findByLabelText(/Performed By Nurse/);
    await screen.findByRole('option', { name: 'Asha Patil' });
    expect(select).toHaveValue('31');
  });

  it('with separate nurse logins there is no picker — the logged-in nurse is credited', async () => {
    authService.getCurrentUser.mockReturnValue({ role: 'NURSE' });
    nurseService.getSeparateNurseLogin.mockResolvedValue(true);
    render(<Panel admissionId={9} />);

    fireEvent.click(await screen.findByRole('button', { name: 'Save' }));
    await waitFor(() => expect(save()).toHaveBeenCalled());
    expect(screen.queryByLabelText(/Performed By Nurse/)).toBeNull();
  });

  it('a doctor records as themselves — no picker, nothing to choose', async () => {
    authService.getCurrentUser.mockReturnValue({ role: 'DOCTOR' });
    render(<Panel admissionId={9} />);

    fireEvent.click(await screen.findByRole('button', { name: 'Save' }));
    await waitFor(() => expect(save()).toHaveBeenCalled());
    expect(screen.queryByLabelText(/Performed By Nurse/)).toBeNull();
    expect(nurseService.getSeparateNurseLogin).not.toHaveBeenCalled();
  });
});
