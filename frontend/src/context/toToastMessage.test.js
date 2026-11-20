import { describe, expect, it } from 'vitest';
import { toToastMessage } from './ToastContext';

describe('toToastMessage', () => {
  it("shows the server's reason, not axios's status text, for a caught request error", () => {
    const err = Object.assign(new Error('Request failed with status code 400'), {
      isAxiosError: true,
      response: { status: 400, data: { success: false, error: 'Bed is no longer available' } },
    });
    expect(toToastMessage(err)).toBe('Bed is no longer available');
  });

  it('never shows axios text when the body is empty', () => {
    const err = Object.assign(new Error('Request failed with status code 500'), {
      isAxiosError: true,
      response: { status: 500, data: '' },
    });
    expect(toToastMessage(err)).not.toMatch(/status code/);
  });
});
