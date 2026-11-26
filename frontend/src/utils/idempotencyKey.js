/**
 * A fresh key for one act of changing money or stock (a payment, a dispense). Mint it when the
 * form opens and send the same key on every retry of that act: a double-click, or a resend after
 * a response that never arrived, then reaches the server under the same key and is applied once.
 *
 * The server accepts letters, digits, `-` and `_`, up to 64 characters.
 */
export const newIdempotencyKey = (prefix) => {
  // randomUUID needs a secure context; getRandomValues works everywhere and is still a
  // cryptographic source (Math.random is not, and keys must not be guessable).
  const random =
    globalThis.crypto?.randomUUID?.() ??
    Array.from(globalThis.crypto.getRandomValues(new Uint8Array(16)), (b) =>
      b.toString(16).padStart(2, '0')
    ).join('');
  return `${prefix}-${random}`.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 64);
};

/** Short, stable fingerprint of a request body (djb2 over its JSON), for {@link keyForAct}. */
const fingerprint = (payload) => {
  const text = JSON.stringify(payload ?? null);
  let h = 5381;
  for (let i = 0; i < text.length; i += 1) h = ((h << 5) + h + text.charCodeAt(i)) >>> 0;
  return h.toString(36);
};

/**
 * Key for a clinical order: the form's nonce plus a fingerprint of what is being ordered. A resend
 * of the same order (a double-click, a retry after a lost response) gets the same key and is
 * applied once; editing the order before resending gives a new key, so the server never answers
 * a changed order with the old one. Mint the nonce when the form opens (newIdempotencyKey).
 */
export const keyForAct = (nonce, payload) => `${nonce}-${fingerprint(payload)}`.slice(0, 64);
