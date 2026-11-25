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

describe('keyForAct', () => {
  it('is the same for the same order and changes when the order changes', async () => {
    const { keyForAct } = await import('./idempotencyKey');
    const nonce = 'rx-abc';
    const order = { medicineName: 'Paracetamol', dose: '500mg', frequency: '1-0-1' };
    expect(keyForAct(nonce, order)).toBe(keyForAct(nonce, { ...order }));
    expect(keyForAct(nonce, order)).not.toBe(keyForAct(nonce, { ...order, dose: '650mg' }));
    expect(keyForAct(nonce, order)).not.toBe(keyForAct('rx-other', order));
    expect(keyForAct(nonce, order)).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
  });
});
