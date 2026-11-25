/**
 * A fresh key for one act of changing money or stock (a payment, a dispense). Mint it when the
 * form opens and send the same key on every retry of that act: a double-click, or a resend after
 * a response that never arrived, then reaches the server under the same key and is applied once.
 *
 * The server accepts letters, digits, `-` and `_`, up to 64 characters.
 */
export const newIdempotencyKey = (prefix) => {
  const random =
    globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}-${random}`.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 64);
};
