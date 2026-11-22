import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DischargeSummaryModal, { validateSummary } from './DischargeSummaryModal';

const base = {
  finalDiagnosis: 'Dengue',
  treatmentGiven: 'IV fluids',
  conditionAtDischarge: 'IMPROVED',
  dischargeType: 'NORMAL',
  dischargeTypeNote: '',
  dischargeMedications: [],
};

describe('validateSummary', () => {
  it('requires diagnosis, course and condition', () => {
    expect(validateSummary({ ...base, finalDiagnosis: ' ' })).toMatch(/final diagnosis/);
    expect(validateSummary({ ...base, treatmentGiven: '' })).toMatch(/course in hospital/);
    expect(validateSummary({ ...base, conditionAtDischarge: '' })).toMatch(/condition/);
    expect(validateSummary(base)).toBeNull();
  });

  it('asks no condition for a death, but needs the cause', () => {
    const expired = { ...base, conditionAtDischarge: '', dischargeType: 'EXPIRED' };
    expect(validateSummary(expired)).toMatch(/cause of death/);
    expect(validateSummary({ ...expired, dischargeTypeNote: '10:40, arrest' })).toBeNull();
  });

  it('refuses a nameless medicine row', () => {
    const meds = [{ name: ' ', dose: '', frequency: '', durationDays: '', instructions: '' }];
    expect(validateSummary({ ...base, dischargeMedications: meds })).toMatch(/needs a name/);
  });
});

describe('DischargeSummaryModal', () => {
  it('copies current medicines and saves the summary', async () => {
    const onSave = vi.fn().mockResolvedValue();
    render(
      <DischargeSummaryModal
        currentMedicines={[{ name: 'Paracetamol 500', dosage: '1 tab', frequency: '1-0-1' }]}
        onClose={vi.fn()}
        onSave={onSave}
      />
    );
    fireEvent.change(screen.getByLabelText('Final diagnosis *'), { target: { value: 'Dengue' } });
    fireEvent.change(screen.getByLabelText('Course in hospital / treatment given *'), {
      target: { value: 'IV fluids' },
    });
    fireEvent.change(screen.getByLabelText('Condition at discharge *'), {
      target: { value: 'IMPROVED' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Copy current medicines' }));
    expect(screen.getByLabelText('Medicine 1 name')).toHaveValue('Paracetamol 500');
    fireEvent.click(screen.getByRole('button', { name: 'Plan Discharge' }));
    await waitFor(() => expect(onSave).toHaveBeenCalled());
    const payload = onSave.mock.calls[0][0];
    expect(payload.conditionAtDischarge).toBe('IMPROVED');
    expect(payload.dischargeMedications).toEqual([
      {
        name: 'Paracetamol 500',
        dose: '1 tab',
        frequency: '1-0-1',
        durationDays: null,
        instructions: null,
      },
    ]);
  });

  it('opens with the saved summary when editing', () => {
    render(
      <DischargeSummaryModal
        editing
        summary={{ finalDiagnosis: 'Dengue', treatmentGiven: 'IV', conditionAtDischarge: 'STABLE' }}
        dischargeType="NORMAL"
        onClose={vi.fn()}
        onSave={vi.fn()}
      />
    );
    expect(screen.getByText('Edit discharge summary')).toBeInTheDocument();
    expect(screen.getByLabelText('Final diagnosis *')).toHaveValue('Dengue');
    expect(screen.getByLabelText('Condition at discharge *')).toHaveValue('STABLE');
  });
});
