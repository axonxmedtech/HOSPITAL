/**
 * Extract a human-readable string from an Axios/API error — never an object.
 *
 * Passing an object (e.g. Spring's validation body `{ errors: { field: msg } }`) to a toast or
 * rendering it as a React child throws the minified React #31 crash. This normalises every shape
 * the backend returns into a single string:
 *   - ApiResponse.error   -> { error: "..." }
 *   - validation failures  -> { errors: { field: "..." } }
 *   - misc                 -> { message: "..." } or a raw string body
 *
 * @param {unknown} err the caught error (typically an AxiosError)
 * @param {string} fallback message to use when nothing usable is found
 * @returns {string} a safe, renderable message
 */
export const extractApiError = (err, fallback = 'Something went wrong. Please try again.') => {
  const data = err?.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  if (data?.error && typeof data.error === 'string') return data.error;
  if (data?.message && typeof data.message === 'string') return data.message;
  if (data?.errors && typeof data.errors === 'object') {
    const msgs = Object.values(data.errors).filter((v) => typeof v === 'string');
    if (msgs.length) return msgs.join(', ');
  }
  if (err?.message && typeof err.message === 'string') return err.message;
  return fallback;
};

export default extractApiError;
