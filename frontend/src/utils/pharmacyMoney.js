/**
 * Pharmacy bill money, the same rules the server applies (PharmacyBillCalculator):
 * selling prices INCLUDE GST, so the customer pays the shelf price and never more than MRP.
 * GST is the part of the price the law treats as tax. These figures are for display only;
 * the server works the bill out again and its numbers are what get saved.
 */

const round2 = (v) => Math.round((v + Number.EPSILON) * 100) / 100;

/** Gross, discount, net, and the GST contained in the net, for counter lines {qty, price, taxPercent}. */
export function billTotals(lines, discount) {
  const gross = round2(
    lines.reduce((sum, l) => sum + (Number(l.qty) || 0) * (Number(l.price) || 0), 0)
  );
  const disc = Math.min(Math.max(Number(discount) || 0, 0), gross);
  let gst = 0;
  lines.forEach((l) => {
    const lineGross = (Number(l.qty) || 0) * (Number(l.price) || 0);
    const share = gross > 0 ? (disc * lineGross) / gross : 0;
    const net = lineGross - share;
    const rate = Number(l.taxPercent) || 0;
    gst += net - net / (1 + rate / 100);
  });
  return { gross, discount: round2(disc), net: round2(gross - disc), gst: round2(gst) };
}

/** GST contained in a GST-inclusive amount. */
export function gstIncluded(amount, rate) {
  const r = Number(rate) || 0;
  return round2(amount - amount / (1 + r / 100));
}

/**
 * What the customer actually paid per unit of a sold line: its share of the bill's net amount.
 * Refunds are based on this, never on the shelf price (works for older bills too).
 */
export function paidPerUnit(sale, item) {
  const lines = sale?.items || [];
  const linesTotal = lines.reduce((s, i) => s + (Number(i.totalAmount) || 0), 0);
  const net = sale?.netAmount == null ? linesTotal : Number(sale.netAmount);
  const qty = Number(item?.quantity) || 0;
  if (!linesTotal || !qty) return 0;
  return ((Number(item.totalAmount) || 0) * net) / (linesTotal * qty);
}

export const roundMoney = round2;
