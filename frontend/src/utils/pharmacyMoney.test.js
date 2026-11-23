import { describe, expect, it } from 'vitest';
import { billTotals, gstIncluded, paidPerUnit } from './pharmacyMoney';

describe('pharmacy bill money (prices include GST)', () => {
  it('charges the shelf price, never MRP plus GST', () => {
    const t = billTotals([{ qty: 1, price: 100, taxPercent: 12 }], 0);
    expect(t.net).toBe(100);
    expect(t.gst).toBe(10.71);
  });

  it('applies the bill discount and the GST on the discounted price', () => {
    const t = billTotals([{ qty: 2, price: 100, taxPercent: 12 }], 26);
    expect(t).toEqual({ gross: 200, discount: 26, net: 174, gst: 18.64 });
  });

  it('never lets the discount exceed the bill or go below zero', () => {
    expect(billTotals([{ qty: 1, price: 50, taxPercent: 5 }], 500).net).toBe(0);
    expect(billTotals([{ qty: 1, price: 50, taxPercent: 5 }], -10).net).toBe(50);
  });

  it('works out the GST inside an amount', () => {
    expect(gstIncluded(112, 12)).toBe(12);
    expect(gstIncluded(50, 0)).toBe(0);
  });

  it('refunds what was paid per unit, including the discount share', () => {
    const sale = { netAmount: 174, items: [{ quantity: 2, totalAmount: 174 }] };
    expect(paidPerUnit(sale, sale.items[0])).toBe(87);
    // an older bill that added GST on top: line totals did not include GST
    const old = { netAmount: 112, items: [{ quantity: 1, totalAmount: 100 }] };
    expect(paidPerUnit(old, old.items[0])).toBe(112);
  });
});
