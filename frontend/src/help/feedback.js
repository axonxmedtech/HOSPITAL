import apiClient from '../services/apiService';

/**
 * Sends help feedback: an answer rated helpful or not, or a question help could not answer.
 * Best effort — help never waits for it or shows an error if it fails.
 */
export function sendFeedback({ kind, entryId = null, question = null, screen = null }) {
  return apiClient
    .post('/help/feedback', { kind, entryId, question, screen })
    .catch(() => undefined);
}
