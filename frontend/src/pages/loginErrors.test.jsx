import { render, screen, fireEvent } from '@testing-library/react';
import { AxiosError } from 'axios';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../services/authService', () => ({
  default: {
    hospitalLogin: vi.fn(),
    platformLogin: vi.fn(),
    logout: vi.fn(),
  },
}));

import authService from '../services/authService';
import { NETWORK_MESSAGE } from '../utils/apiError';
import HospitalLogin from './hospital/HospitalLogin';
import PlatformLogin from './platform/PlatformLogin';

const response = (status, data) => ({ status, data, headers: {}, config: {} });
const NGINX_502 = '<html><head><title>502 Bad Gateway</title></head><body>nginx</body></html>';

const pages = [
  ['HospitalLogin', <HospitalLogin key="h" />, 'hospitalLogin'],
  ['PlatformLogin', <PlatformLogin key="p" />, 'platformLogin'],
];

const submit = () => {
  fireEvent.change(document.getElementById('email'), { target: { value: 'admin@hms.com' } });
  fireEvent.change(document.getElementById('password'), { target: { value: 'secret' } });
  fireEvent.submit(document.getElementById('password').closest('form'));
};

describe.each(pages)('%s error messages', (_name, page, loginFn) => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('shows what the API said for bad credentials', async () => {
    authService[loginFn].mockRejectedValue(
      new AxiosError(
        'Request failed with status code 401',
        'ERR_BAD_REQUEST',
        {},
        {},
        response(401, {
          error: 'Invalid email or password',
          message: 'Invalid email or password',
        })
      )
    );
    render(<MemoryRouter>{page}</MemoryRouter>);
    submit();
    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
  });

  it('explains a network failure instead of showing "Network Error"', async () => {
    authService[loginFn].mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK', {}, {}));
    render(<MemoryRouter>{page}</MemoryRouter>);
    submit();
    expect(await screen.findByText(NETWORK_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByText('Network Error')).not.toBeInTheDocument();
  });

  it('never renders an nginx HTML page during a backend restart', async () => {
    authService[loginFn].mockRejectedValue(
      new AxiosError(
        'Request failed with status code 502',
        'ERR_BAD_RESPONSE',
        {},
        {},
        response(502, NGINX_502)
      )
    );
    const { container } = render(<MemoryRouter>{page}</MemoryRouter>);
    submit();
    expect(
      await screen.findByText('The server is temporarily unavailable. Please try again shortly.')
    ).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/Bad Gateway|<html/);
  });
});

describe('HospitalLogin department mismatch', () => {
  it('names the department the account signs in with and selects it', async () => {
    authService.hospitalLogin.mockResolvedValue({ role: 'RECEPTIONIST', modules: ['OPD'] });
    render(
      <MemoryRouter>
        <HospitalLogin />
      </MemoryRouter>
    );
    fireEvent.change(document.getElementById('role'), { target: { value: 'DOCTOR' } });
    submit();
    expect(await screen.findByText(/signs in as "Reception & Registration"/)).toBeInTheDocument();
    expect(document.getElementById('role')).toHaveValue('RECEPTIONIST');
  });

  it('turns a bare "Invalid credentials" into a hint about the page', async () => {
    authService.hospitalLogin.mockRejectedValue(
      new AxiosError(
        'Request failed with status code 401',
        'ERR_BAD_REQUEST',
        {},
        {},
        response(401, { error: 'Invalid credentials' })
      )
    );
    render(
      <MemoryRouter>
        <HospitalLogin />
      </MemoryRouter>
    );
    submit();
    expect(await screen.findByText(/Wrong email or password/)).toBeInTheDocument();
  });
});
