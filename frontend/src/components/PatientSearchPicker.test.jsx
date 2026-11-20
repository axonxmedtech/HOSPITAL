import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../services/hospitalService', () => ({ default: { getPatients: vi.fn() } }));

import hospitalService from '../services/hospitalService';
import PatientSearchPicker from './PatientSearchPicker';

function Harness({ onSelect, onRegisterNew }) {
  const [text, setText] = useState('');
  return (
    <PatientSearchPicker
      text={text}
      onTextChange={setText}
      onSelect={onSelect}
      onRegisterNew={onRegisterNew}
    />
  );
}

beforeEach(() => vi.clearAllMocks());

describe('PatientSearchPicker', () => {
  it('searches the whole hospital on the server and selects a patient', async () => {
    hospitalService.getPatients.mockResolvedValue({
      content: [{ id: 7, name: 'Asha Rao', phone: '9876543210' }],
    });
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={onSelect} onRegisterNew={vi.fn()} />);
    await user.type(screen.getByRole('combobox', { name: 'Patient' }), 'Asha');
    await waitFor(() => expect(hospitalService.getPatients).toHaveBeenCalledWith('Asha', 0, 10));
    await user.pointer({
      keys: '[MouseLeft>]',
      target: await screen.findByRole('option', { name: /Asha Rao/ }),
    });
    expect(onSelect).toHaveBeenLastCalledWith(expect.objectContaining({ id: 7 }));
    expect(screen.getByRole('combobox', { name: 'Patient' })).toHaveValue('Asha Rao (9876543210)');
  });

  it('offers to register a new patient with what was typed', async () => {
    hospitalService.getPatients.mockResolvedValue({ content: [] });
    const onRegisterNew = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={vi.fn()} onRegisterNew={onRegisterNew} />);
    await user.type(screen.getByRole('combobox', { name: 'Patient' }), 'Ravi Kumar');
    expect(await screen.findByText('No matching patients found')).toBeInTheDocument();
    await user.pointer({
      keys: '[MouseLeft>]',
      target: screen.getByRole('button', { name: /Register new patient/ }),
    });
    expect(onRegisterNew).toHaveBeenCalledWith('Ravi Kumar');
  });
});
