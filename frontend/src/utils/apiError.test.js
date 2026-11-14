import { describe, expect, it } from 'vitest';
import { AxiosError, CanceledError } from 'axios';
import {
  extractApiError,
  isRequestCancelled,
  NETWORK_MESSAGE,
  safeLoadMessage,
  TIMEOUT_MESSAGE,
} from './apiError';

const FALLBACK = 'Failed to save patient';

// A real AxiosError, as the API client rejects with.
const axiosError = ({ status, data, code = 'ERR_BAD_RESPONSE', message } = {}) =>
  new AxiosError(
    message || (status ? `Request failed with status code ${status}` : 'Network Error'),
    code,
    {},
    {},
    status ? { status, data, headers: {}, config: {} } : undefined
  );

const NGINX_502 =
  '<html>\r\n<head><title>502 Bad Gateway</title></head>\r\n<body>\r\n<center><h1>502 Bad Gateway</h1></center>\r\n</body>\r\n</html>';

describe('extractApiError', () => {
  it('returns canonical API errors before generic fallback messages', () => {
    expect(
      extractApiError({ response: { data: { error: 'Duplicate patient phone number' } } })
    ).toBe('Duplicate patient phone number');
  });

  it('normalizes validation error maps into a safe toast message', () => {
    expect(extractApiError({ response: { data: { errors: { name: 'Name is required' } } } })).toBe(
      'Name is required'
    );
  });

  it('joins several validation messages', () => {
    const err = axiosError({
      status: 400,
      data: { errors: { name: 'Name is required', age: 'Age must be positive' } },
    });
    expect(extractApiError(err, FALLBACK)).toBe('Name is required, Age must be positive');
  });

  it('uses the message field when there is no error field', () => {
    expect(
      extractApiError(
        axiosError({ status: 409, data: { message: 'Bed already occupied' } }),
        FALLBACK
      )
    ).toBe('Bed already occupied');
  });

  it('accepts a plain-text body', () => {
    expect(extractApiError(axiosError({ status: 400, data: 'Invalid date range' }), FALLBACK)).toBe(
      'Invalid date range'
    );
  });

  describe('never shows axios transport text', () => {
    it('uses the fallback for a 500 with an empty body', () => {
      expect(extractApiError(axiosError({ status: 500, data: '' }), FALLBACK)).toBe(FALLBACK);
    });

    it('uses the fallback for a 404 with an unusable body', () => {
      expect(
        extractApiError(axiosError({ status: 404, data: { error: { code: 404 } } }), FALLBACK)
      ).toBe(FALLBACK);
    });

    it('never renders an nginx HTML error page', () => {
      const msg = extractApiError(axiosError({ status: 502, data: NGINX_502 }), FALLBACK);
      expect(msg).not.toMatch(/<html|Bad Gateway/);
      expect(msg).toBe('The server is temporarily unavailable. Please try again shortly.');
    });

    it('explains a network failure instead of "Network Error"', () => {
      expect(extractApiError(axiosError({ code: 'ERR_NETWORK' }), FALLBACK)).toBe(NETWORK_MESSAGE);
    });

    it('explains a timeout', () => {
      const err = axiosError({ code: 'ECONNABORTED', message: 'timeout of 30000ms exceeded' });
      expect(extractApiError(err, FALLBACK)).toBe(TIMEOUT_MESSAGE);
    });

    it.each([
      [413, 'The file is too large to upload.'],
      [429, 'Too many requests. Please wait a moment and try again.'],
      [503, 'The server is temporarily unavailable. Please try again shortly.'],
      [504, 'The server is temporarily unavailable. Please try again shortly.'],
    ])('gives a readable message for a bodiless %i', (status, expected) => {
      expect(extractApiError(axiosError({ status, data: '' }), FALLBACK)).toBe(expected);
    });

    it('does not show "canceled" for a cancelled request', () => {
      expect(extractApiError(new CanceledError(), FALLBACK)).toBe(FALLBACK);
    });

    it('still prefers what the API said over a status message', () => {
      expect(
        extractApiError(
          axiosError({ status: 503, data: { error: 'Pharmacy module is disabled' } }),
          FALLBACK
        )
      ).toBe('Pharmacy module is disabled');
    });
  });

  it('keeps the message of an Error our own code threw', () => {
    expect(extractApiError(new Error('Select a ward first'), FALLBACK)).toBe('Select a ward first');
  });

  it('survives null, undefined and blank bodies', () => {
    expect(extractApiError(null, FALLBACK)).toBe(FALLBACK);
    expect(extractApiError(undefined, FALLBACK)).toBe(FALLBACK);
    expect(
      extractApiError({ response: { data: { error: '  ', errors: { a: '' } } } }, FALLBACK)
    ).toBe(FALLBACK);
  });

  it('always returns a string', () => {
    const shapes = [
      axiosError({ status: 400, data: { errors: { a: { nested: true } } } }),
      axiosError({ status: 500, data: { message: 42 } }),
      { response: { data: ['x'] } },
      'oops',
      42,
    ];
    shapes.forEach((err) => expect(typeof extractApiError(err, FALLBACK)).toBe('string'));
  });
});

describe('isRequestCancelled', () => {
  it('recognises axios cancellations', () => {
    expect(isRequestCancelled(new CanceledError())).toBe(true);
    expect(isRequestCancelled({ code: 'ERR_CANCELED' })).toBe(true);
  });

  it('does not treat real failures as cancellations', () => {
    expect(isRequestCancelled(axiosError({ code: 'ERR_NETWORK' }))).toBe(false);
    expect(isRequestCancelled(null)).toBe(false);
  });
});

describe('safeLoadMessage with an HTML body', () => {
  it('uses the fallback instead of rendering an nginx error page', () => {
    expect(safeLoadMessage({ response: { data: NGINX_502 } }, "Couldn't load patients.")).toBe(
      "Couldn't load patients."
    );
  });
});
