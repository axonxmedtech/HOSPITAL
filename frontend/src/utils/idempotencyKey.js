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
