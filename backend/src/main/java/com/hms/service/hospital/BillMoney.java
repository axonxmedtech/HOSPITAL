package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.BillingMedicine;
import com.hms.entity.BillingPayment;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * The money on one bill, computed one way everywhere.
 *
 * <pre>
 *   gross    = sum of charge lines (items + medicines); a line-less bill uses its stored amount,
 *              and a legacy line-less IPD bill stored at 0 uses its ward's bed price
 *   discount = the bill's discount, clamped to [0, gross]
 *   net      = gross - discount              (what the patient owes in total)
 *   paid     = sum of the payment ledger     (refunds are negative rows, so this is net cash in)
 *   balance  = net - paid                    (still to collect; negative = owed back)
 * </pre>
 *
 * <p>The bill list, the IPD bill, taking a payment, mark-as-paid, status sync, the receipt and
 * the discharge check each used to repeat their own copy of this sum. A discount added to only
 * some of them would have made the screens disagree about what a patient owes.
 */
public record BillMoney(BigDecimal gross, BigDecimal discount, BigDecimal net, BigDecimal paid, BigDecimal balance) {

    public static BillMoney of(Billing bill,
                               List<BillingItem> items,
                               List<BillingMedicine> medicines,
                               List<BillingPayment> payments,
                               Supplier<BigDecimal> legacyBedPrice) {
        boolean hasLines = (items != null && !items.isEmpty()) || (medicines != null && !medicines.isEmpty());
        BigDecimal gross = BigDecimal.ZERO;
        if (hasLines) {
            if (items != null) {
                for (BillingItem it : items) {
                    if (it.getAmount() != null) gross = gross.add(it.getAmount());
                }
            }
            if (medicines != null) {
                for (BillingMedicine m : medicines) {
                    if (m.getAmount() != null) gross = gross.add(m.getAmount());
                }
            }
        } else {
            gross = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
            if (gross.signum() == 0 && legacyBedPrice != null) {
                BigDecimal bed = legacyBedPrice.get();
                if (bed != null) gross = bed;
            }
        }

        BigDecimal discount = bill.getDiscountAmount() != null ? bill.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.signum() < 0) discount = BigDecimal.ZERO;
        if (discount.compareTo(gross) > 0) discount = gross;

        BigDecimal paid = BigDecimal.ZERO;
        if (payments != null) {
            for (BillingPayment p : payments) {
                if (p.getAmount() != null) paid = paid.add(p.getAmount());
            }
        }

        BigDecimal net = gross.subtract(discount);
        return new BillMoney(gross, discount, net, paid, net.subtract(paid));
    }

    /** Sum of refund rows, as a positive number. */
    public static BigDecimal refunded(List<BillingPayment> payments) {
        BigDecimal out = BigDecimal.ZERO;
        if (payments == null) return out;
        for (BillingPayment p : payments) {
            if (p.getAmount() != null && p.getAmount().signum() < 0) out = out.add(p.getAmount().negate());
        }
        return out;
    }

    /** Status the ledger implies for a live bill: PAID / PARTIAL / PENDING. */
    public String impliedStatus() {
        if (gross.signum() > 0 && net.signum() == 0) return "PAID"; // fully waived
        if (net.signum() <= 0) return null; // nothing billed yet: leave the status alone
        if (paid.compareTo(net) >= 0) return "PAID";
        if (paid.signum() > 0) return "PARTIAL";
        return "PENDING";
    }
}
