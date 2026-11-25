import { describe, expect, it } from 'vitest';
import { newIdempotencyKey } from './idempotencyKey';

describe('newIdempotencyKey', () => {
  it('is unique per call, prefixed, and only uses characters the server accepts', () => {
    const a = newIdempotencyKey('pay');
    const b = newIdempotencyKey('pay');
    expect(a).not.toBe(b);
    expect(a).toMatch(/^pay-[A-Za-z0-9_-]+$/);
    expect(a.length).toBeLessThanOrEqual(64);
  });
});
