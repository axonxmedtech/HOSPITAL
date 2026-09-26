import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import authService from '../../services/authService';
import * as api from '../../services/patientImportService';
import { readImportHeaders } from './patient-import/readImportHeaders';
import PatientImportPage from './PatientImportPage';
const toast = vi.hoisted(() => ({ info: vi.fn() }));
vi.mock('../../context/ToastContext', () => ({ useToast: () => toast }));
vi.mock('../../services/authService', () => ({
  default: {
    getCurrentUser: vi.fn(),
    isAuthenticated: vi.fn(),
    getLoginUrl: () => '/login/hospital',
  },
}));
vi.mock('../../services/patientImportService', async (original) => ({
  ...(await original()),
  previewPatientImport: vi.fn(),
  commitPatientImport: vi.fn(),
  getPatientImportStatus: vi.fn(),
}));
vi.mock('./patient-import/readImportHeaders', () => ({ readImportHeaders: vi.fn() }));
const counts = { total: 4, created: 1, updated: 1, skipped: 0, needsReview: 1, failed: 1 };
const preview = {
  counts,
  samples: [
    {
      rowNum: 3,
      state: 'NEEDS_REVIEW',
      reasonCode: 'DUPLICATE_PHONE_REQUIRES_REVIEW',
      message: 'Shared phone requires review.',
      phoneMasked: '******1234',
    },
    { rowNum: 4, state: 'FAILED', reasonCode: 'INVALID_DOB', message: 'Date of birth is invalid.' },
  ],
  previousImport: null,
  samplesTruncated: true,
};
const id = '11111111-1111-4111-8111-111111111111';
function LocationProbe() {
  const location = useLocation();
  return <output aria-label="Current route">{location.pathname + location.search}</output>;
}
const renderPage = (entry = '/hospital/patients/import') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <LocationProbe />
      <Link to="/">Leave import</Link>
      <Routes>
        <Route path="/hospital/patients/import" element={<PatientImportPage />} />
        <Route path="/" element={<p>Home</p>} />
        <Route path="/login/hospital" element={<p>Sign in</p>} />
      </Routes>
    </MemoryRouter>
  );
const chooseFile = async () => {
  const file = new File(['Name,Phone,Extra\nPerson,9000000001,private'], 'patients.csv', {
    type: 'text/csv',
  });
  fireEvent.change(screen.getByLabelText(/Browse File/), { target: { files: [file] } });
  await screen.findByRole('combobox', { name: 'Map Extra' });
  fireEvent.change(screen.getByRole('combobox', { name: 'Map Extra' }), {
    target: { value: '__exclude__' },
  });
  return file;
};
const runPreview = async () => {
  await chooseFile();
  fireEvent.click(screen.getByRole('button', { name: 'Preview & validate' }));
  await screen.findByRole('region', { name: 'Preview results' });
};
beforeEach(() => {
  vi.clearAllMocks();
  authService.getCurrentUser.mockReturnValue({ role: 'HOSPITAL_ADMIN', hospitalType: 'HOSPITAL' });
  authService.isAuthenticated.mockReturnValue(true);
  readImportHeaders.mockResolvedValue([{ name: '', headers: ['Name', 'Phone', 'Extra'] }]);
  api.previewPatientImport.mockResolvedValue(preview);
  api.commitPatientImport.mockResolvedValue({ batchPublicId: id, status: 'PARTIAL', counts });
  api.getPatientImportStatus.mockResolvedValue({ publicId: id, status: 'PARTIAL', counts });
});
describe('patient import workflow', () => {
  it.each(['HOSPITAL', 'CLINIC'])('allows %s admins', (hospitalType) => {
    authService.getCurrentUser.mockReturnValue({ role: 'HOSPITAL_ADMIN', hospitalType });
    renderPage();
    expect(screen.getByRole('heading', { name: 'Import Patients' })).toBeInTheDocument();
  });
  it.each([
    { role: 'DOCTOR', hospitalType: 'HOSPITAL' },
    { role: 'HOSPITAL_ADMIN', hospitalType: 'PHARMACY' },
  ])('denies unsupported access %j', (user) => {
    authService.getCurrentUser.mockReturnValue(user);
    renderPage();
    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(api.previewPatientImport).not.toHaveBeenCalled();
  });
  it('uses existing login routing without a session', () => {
    authService.isAuthenticated.mockReturnValue(false);
    renderPage();
    expect(screen.getByText('Sign in')).toBeInTheDocument();
  });
  it('rejects unsupported uploads without parsing or calling the API', () => {
    renderPage();
    fireEvent.change(screen.getByLabelText(/Browse File/), {
      target: { files: [new File(['x'], 'bad.exe')] },
    });
    expect(screen.getByRole('alert')).toHaveTextContent('.csv or .xlsx');
    expect(readImportHeaders).not.toHaveBeenCalled();
  });
  it('requires an explicit choice for every column and a name mapping', async () => {
    renderPage();
    await chooseFile();
    fireEvent.change(screen.getByRole('combobox', { name: 'Map Extra' }), {
      target: { value: '' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Preview & validate' }));
    expect(screen.getByRole('alert')).toHaveTextContent('every source column');
    fireEvent.change(screen.getByRole('combobox', { name: 'Map Extra' }), {
      target: { value: '__exclude__' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: 'Map Name' }), {
      target: { value: '__exclude__' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Preview & validate' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Full name');
    expect(api.previewPatientImport).not.toHaveBeenCalled();
  });
  it('sends explicit exclusions, renders real preview counts and issues, and invalidates stale preview', async () => {
    renderPage();
    const file = await chooseFile();
    expect(
      within(screen.getByRole('combobox', { name: 'Map Extra' })).getByRole('option', {
        name: 'Phone',
      })
    ).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Preview & validate' }));
    await screen.findByRole('region', { name: 'Preview results' });
    expect(api.previewPatientImport).toHaveBeenCalledWith({
      file,
      mapping: { Name: 'name', Phone: 'phone' },
      excludedColumns: ['Extra'],
      sheetName: undefined,
    });
    expect(screen.getByText('Shared phone requires review.')).toBeInTheDocument();
    expect(screen.getByText('Date of birth is invalid.')).toBeInTheDocument();
    expect(screen.getByText('******1234')).toBeInTheDocument();
    expect(screen.getByText(/sample only/)).toBeInTheDocument();
    expect(api.commitPatientImport).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('combobox', { name: 'Map Phone' }), {
      target: { value: '__exclude__' },
    });
    expect(screen.queryByRole('region', { name: 'Preview results' })).not.toBeInTheDocument();
  });
  it('confirms a real mutation, prevents double submission, and displays final status', async () => {
    let finish;
    api.commitPatientImport.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      })
    );
    renderPage();
    await runPreview();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm import…' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('Preview made no patient changes');
    expect(api.commitPatientImport).not.toHaveBeenCalled();
    const submit = screen.getByRole('button', { name: 'Confirm action' });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(api.commitPatientImport).toHaveBeenCalledTimes(1);
    expect(submit).toBeDisabled();
    await act(async () => finish({ batchPublicId: id, status: 'PARTIAL', counts }));
    await screen.findByRole('heading', { name: 'Import results — PARTIAL' });
    expect(api.getPatientImportStatus).toHaveBeenCalledWith(id);
    expect(screen.getByLabelText('Current route')).toHaveTextContent(
      `/hospital/patients/import?batch=${id}`
    );
    expect(toast.info).toHaveBeenCalledTimes(1);
    expect(
      screen.getByText('Some rows were not imported. Successful changes were kept.')
    ).toBeInTheDocument();
  });
  it.each(['success', 'failure'])(
    'ignores late commit %s after navigating away without retrying',
    async (outcome) => {
      let resolveCommit;
      let rejectCommit;
      api.commitPatientImport.mockReturnValue(
        new Promise((resolve, reject) => {
          resolveCommit = resolve;
          rejectCommit = reject;
        })
      );
      renderPage();
      await runPreview();
      fireEvent.click(screen.getByRole('button', { name: 'Confirm import…' }));
      fireEvent.click(screen.getByRole('button', { name: 'Confirm action' }));
      expect(api.commitPatientImport).toHaveBeenCalledTimes(1);
      fireEvent.click(screen.getByRole('link', { name: 'Leave import' }));
      expect(screen.getByText('Home')).toBeInTheDocument();
      expect(screen.getByLabelText('Current route')).toHaveTextContent(/^\/$/);
      await act(async () => {
        if (outcome === 'success') resolveCommit({ batchPublicId: id, status: 'PARTIAL', counts });
        else rejectCommit({ response: { status: 500 } });
      });
      expect(screen.getByText('Home')).toBeInTheDocument();
      expect(screen.getByLabelText('Current route')).toHaveTextContent(/^\/$/);
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(toast.info).not.toHaveBeenCalled();
      expect(api.getPatientImportStatus).not.toHaveBeenCalled();
      expect(api.commitPatientImport).toHaveBeenCalledTimes(1);
    }
  );
  it.each(['success', 'failure'])(
    'ignores commit %s after component unmount without retrying',
    async (outcome) => {
      let resolveCommit;
      let rejectCommit;
      api.commitPatientImport.mockReturnValue(
        new Promise((resolve, reject) => {
          resolveCommit = resolve;
          rejectCommit = reject;
        })
      );
      const view = renderPage();
      await runPreview();
      fireEvent.click(screen.getByRole('button', { name: 'Confirm import…' }));
      fireEvent.click(screen.getByRole('button', { name: 'Confirm action' }));
      view.unmount();
      await act(async () => {
        if (outcome === 'success') resolveCommit({ batchPublicId: id, status: 'PARTIAL', counts });
        else rejectCommit({ response: { status: 500 } });
      });
      expect(view.container).toBeEmptyDOMElement();
      expect(toast.info).not.toHaveBeenCalled();
      expect(api.getPatientImportStatus).not.toHaveBeenCalled();
      expect(api.commitPatientImport).toHaveBeenCalledTimes(1);
    }
  );
  it('opens known batches after a refresh without inventing local history', async () => {
    renderPage(`/hospital/patients/import?batch=${id}`);
    await screen.findByRole('heading', { name: 'Import results — PARTIAL' });
    expect(readImportHeaders).not.toHaveBeenCalled();
    expect(api.getPatientImportStatus).toHaveBeenCalledWith(id);
  });
  it('blocks known repeated imports', async () => {
    api.previewPatientImport.mockResolvedValue({
      ...preview,
      previousImport: { batchPublicId: id, status: 'COMPLETED' },
    });
    renderPage();
    await runPreview();
    expect(screen.getByRole('button', { name: 'Confirm import…' })).toBeDisabled();
  });
  it.each([400, 401, 403, 409, 411, 413, 500, undefined])(
    'handles preview error %s without exposing arbitrary server text',
    async (status) => {
      api.previewPatientImport.mockRejectedValue({
        response: { status, data: { message: 'SECRET PHI' } },
      });
      renderPage();
      await chooseFile();
      fireEvent.click(screen.getByRole('button', { name: 'Preview & validate' }));
      await screen.findByRole('alert');
      expect(screen.getByRole('alert')).not.toHaveTextContent('SECRET');
      expect(api.commitPatientImport).not.toHaveBeenCalled();
    }
  );
  it('does not retry a failed mutation and offers status recovery', async () => {
    api.commitPatientImport.mockRejectedValue({
      response: { status: 500, data: { errors: { batchPublicId: id } } },
    });
    renderPage();
    await runPreview();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm import…' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm action' }));
    await screen.findByRole('alert');
    expect(screen.getByRole('alert')).toHaveTextContent('Some rows may already have been imported');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Confirm import…' })).toBeDisabled();
    expect(api.commitPatientImport).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Check import status' }));
    await screen.findByRole('heading', { name: 'Import results — PARTIAL' });
  });
});

it('sends the selected XLSX worksheet and clears preview when changing it', async () => {
  readImportHeaders.mockResolvedValue([
    { name: 'Current patients', headers: ['Name', 'Phone', 'Extra'] },
    { name: 'Archived patients', headers: ['Full name', 'DOB'] },
  ]);
  renderPage();
  fireEvent.change(screen.getByLabelText(/Browse File/), {
    target: { files: [new File(['xlsx fixture'], 'patients.xlsx')] },
  });
  await screen.findByRole('combobox', { name: 'Worksheet' });
  fireEvent.change(screen.getByRole('combobox', { name: 'Worksheet' }), { target: { value: '1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Preview & validate' }));
  await screen.findByRole('region', { name: 'Preview results' });
  expect(api.previewPatientImport.mock.calls[0][0]).toMatchObject({
    sheetName: 'Archived patients',
    mapping: { 'Full name': 'name', DOB: 'dateOfBirth' },
    excludedColumns: [],
  });
  fireEvent.change(screen.getByRole('combobox', { name: 'Worksheet' }), { target: { value: '0' } });
  expect(screen.queryByRole('region', { name: 'Preview results' })).not.toBeInTheDocument();
});

it('explains why an all-review file cannot be committed', async () => {
  api.previewPatientImport.mockResolvedValue({
    ...preview,
    counts: { total: 1, created: 0, updated: 0, skipped: 0, needsReview: 1, failed: 0 },
  });
  renderPage();
  await runPreview();
  expect(screen.getByText(/No patient changes are ready/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Confirm import…' })).toBeDisabled();
});
