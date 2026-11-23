package com.hms.service.pharmacy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place a pharmacy bill's money is worked out. The server never trusts amounts sent by
 * the browser; it recomputes them here from the batch prices.
 *
 * <p><b>Prices include GST</b>, as MRP does in India. The selling price (at most the MRP) is what
 * the customer pays per unit, and GST is the part of that price the law treats as tax:
 * <pre>
 *   gross     = quantity x selling price            (what the shelf price says)
 *   discount  = the bill discount, shared across lines in proportion to their gross
 *   net       = gross - discount                     (what the customer pays for the line)
 *   taxable   = net / (1 + GST% / 100)
 *   GST       = net - taxable                        (so taxable + GST = net, to the paisa)
 * </pre>
 * The customer therefore never pays more than the MRP, and the GST on the invoice is the GST
 * actually collected after discount.
 */
public final class PharmacyBillCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PharmacyBillCalculator() {
    }

    /** One bill line as sold: how many, at what GST-inclusive unit price, at what GST rate. */
    public record Line(BigDecimal quantity, BigDecimal unitPrice, BigDecimal gstPercentage) {
    }

    /** The worked-out money for one line. */
    public record LineResult(BigDecimal gross, BigDecimal discount, BigDecimal net, BigDecimal taxable,
                             BigDecimal gst) {
    }

    /** The worked-out bill: per line, and the totals (gross, discount, net, taxable value, GST). */
    public record Bill(List<LineResult> lines, BigDecimal gross, BigDecimal discount, BigDecimal net,
                       BigDecimal taxable, BigDecimal gst) {
    }

    public static Bill calculate(List<Line> lines, BigDecimal billDiscount) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A bill needs at least one medicine");
        }
        BigDecimal discount = money(billDiscount == null ? BigDecimal.ZERO : billDiscount);
        if (discount.signum() < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }

        List<BigDecimal> grosses = new ArrayList<>();
        BigDecimal grossTotal = BigDecimal.ZERO;
        for (Line line : lines) {
            BigDecimal gross = money(line.quantity().multiply(line.unitPrice()));
            grosses.add(gross);
            grossTotal = grossTotal.add(gross);
        }
        if (discount.compareTo(grossTotal) > 0) {
            throw new IllegalArgumentException("Discount (₹" + discount.toPlainString()
                    + ") cannot be more than the bill (₹" + grossTotal.toPlainString() + ")");
        }

        List<BigDecimal> shares = shareDiscount(grosses, grossTotal, discount);
        List<LineResult> results = new ArrayList<>();
        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            BigDecimal net = grosses.get(i).subtract(shares.get(i));
            BigDecimal rate = lines.get(i).gstPercentage() == null ? BigDecimal.ZERO : lines.get(i).gstPercentage();
            BigDecimal taxable = net.multiply(HUNDRED).divide(HUNDRED.add(rate), 2, RoundingMode.HALF_UP);
            BigDecimal gst = net.subtract(taxable);
            results.add(new LineResult(grosses.get(i), shares.get(i), net, taxable, gst));
            taxableTotal = taxableTotal.add(taxable);
            gstTotal = gstTotal.add(gst);
        }
        return new Bill(results, grossTotal, discount, grossTotal.subtract(discount), taxableTotal, gstTotal);
    }

    /** Discount shared by gross; the last line takes the rounding remainder so shares add up exactly. */
    private static List<BigDecimal> shareDiscount(List<BigDecimal> grosses, BigDecimal grossTotal, BigDecimal discount) {
        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < grosses.size(); i++) {
            BigDecimal share;
            if (i == grosses.size() - 1) {
                share = discount.subtract(given);
            } else if (grossTotal.signum() == 0) {
                share = BigDecimal.ZERO;
            } else {
                share = discount.multiply(grosses.get(i)).divide(grossTotal, 2, RoundingMode.HALF_UP);
            }
            shares.add(share);
            given = given.add(share);
        }
        return shares;
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
