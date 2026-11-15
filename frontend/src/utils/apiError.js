export const NETWORK_MESSAGE =
  'Unable to reach the server. Please check your connection and try again.';
export const TIMEOUT_MESSAGE = 'Request timed out. Please try again.';

// Messages for transport failures whose body carries nothing usable. Anything not listed falls
// back to the caller's own message, which is written for the screen the user is on.
const STATUS_MESSAGES = {
  413: 'The file is too large to upload.',
  429: 'Too many requests. Please wait a moment and try again.',
  502: 'The server is temporarily unavailable. Please try again shortly.',
  503: 'The server is temporarily unavailable. Please try again shortly.',
  504: 'The server is temporarily unavailable. Please try again shortly.',
};

/** True when the request was aborted on purpose (AbortController / unmount) — not a failure. */
export const isRequestCancelled = (err) =>
  err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED' || err?.__CANCEL__ === true;

const isTimeout = (err) => err?.code === 'ECONNABORTED' || err?.code === 'ETIMEDOUT';

// An error produced by the HTTP layer rather than thrown by our own code. Its `message` is
// axios's transport text ("Request failed with status code 500", "Network Error", "canceled"),
// never something to show a user.
const isTransportError = (err) =>
  Boolean(err?.isAxiosError || err?.response || err?.config || err?.code?.startsWith?.('ERR_')) ||
  isTimeout(err) ||
  isRequestCancelled(err);

// A string body is only a message if it is plain text — nginx answers 502/504 with an HTML page.
const textBody = (data) => {
  if (typeof data !== 'string') return null;
  const text = data.trim();
  if (!text || text.startsWith('<') || /<html|<body|<!doctype/i.test(text)) return null;
  return text;
};

// The message the API deliberately put in the response body, or null.
const bodyMessage = (data) => {
  const text = textBody(data);
  if (text) return text;
  if (typeof data?.error === 'string' && data.error.trim()) return data.error;
  if (typeof data?.message === 'string' && data.message.trim()) return data.message;
  if (data?.errors && typeof data.errors === 'object') {
    const msgs = Object.values(data.errors).filter((v) => typeof v === 'string' && v.trim());
    if (msgs.length) return msgs.join(', ');
  }
  return null;
};

/**
 * Extract a human-readable string from an Axios/API error — never an object.
 *
 * Passing an object (e.g. Spring's validation body `{ errors: { field: msg } }`) to a toast or
 * rendering it as a React child throws the minified React #31 crash. This normalises every shape
 * the backend returns into a single string:
 *   - ApiResponse.error   -> { error: "..." }
 *   - validation failures  -> { errors: { field: "..." } }
 *   - misc                 -> { message: "..." } or a plain-text body (never an HTML page)
 * Transport failures with no usable body get a readable message (offline, timeout, 413, 429,
 * 502-504) or the fallback — never axios's own "Request failed with status code …" text.
 * An Error our own code threw (e.g. a client-side validation) keeps its message.
 *
 * @param {unknown} err the caught error (typically an AxiosError)
 * @param {string} fallback message to use when nothing usable is found
 * @returns {string} a safe, renderable message
 */
export const extractApiError = (err, fallback = 'Something went wrong. Please try again.') => {
  const fromBody = bodyMessage(err?.response?.data);
  if (fromBody) return fromBody;

  if (isTransportError(err)) {
    if (isRequestCancelled(err)) return fallback;
    if (isTimeout(err)) return TIMEOUT_MESSAGE;
    if (!err.response) return NETWORK_MESSAGE;
    return STATUS_MESSAGES[err.response.status] || fallback;
  }

  if (typeof err?.message === 'string' && err.message.trim()) return err.message;
  return fallback;
};

export default extractApiError;

/**
 * A message safe to show a user when a screen fails to load.
 *
 * <p>Unlike {@link extractApiError} this never falls back to `err.message`. That field carries
 * whatever the transport or the server happened to throw — during testing a raw
 * `java.lang.NullPointerException at com.hms…` reached a user-facing banner through it. Only text
 * the API deliberately put in the response body is shown; anything else becomes the caller's
 * fallback, which is written for the person reading it.
 */
export const safeLoadMessage = (err, fallback) => {
  const data = err?.response?.data;
  const fromServer =
    (typeof data?.error === 'string' && data.error) ||
    (typeof data?.message === 'string' && data.message) ||
    textBody(data);
  return fromServer || fallback;
};
