package com.hms.service.pharmacy;

import com.hms.service.pharmacy.PharmacyBillCalculator.Bill;
import com.hms.service.pharmacy.PharmacyBillCalculator.Line;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pharmacy bills: prices include GST, so the customer pays the shelf price and never more. */
class PharmacyBillCalculatorTest {

    private static Line line(String qty, String price, String gst) {
        return new Line(new BigDecimal(qty), new BigDecimal(price), new BigDecimal(gst));
    }

    @Test
    void theCustomerPaysTheShelfPriceAndGstIsInsideIt() {
        Bill bill = PharmacyBillCalculator.calculate(List.of(line("1", "100", "12")), BigDecimal.ZERO);

        assertThat(bill.net()).isEqualByComparingTo("100.00"); // not 112
        assertThat(bill.taxable()).isEqualByComparingTo("89.29");
        assertThat(bill.gst()).isEqualByComparingTo("10.71");
        assertThat(bill.taxable().add(bill.gst())).isEqualByComparingTo(bill.net());
    }

    @Test
    void aBillDiscountLowersThePriceAndTheGstOnIt() {
        Bill bill = PharmacyBillCalculator.calculate(List.of(line("2", "100", "12")), new BigDecimal("26"));

        assertThat(bill.gross()).isEqualByComparingTo("200.00");
        assertThat(bill.net()).isEqualByComparingTo("174.00");
        assertThat(bill.gst()).isEqualByComparingTo("18.64"); // 174 - 174/1.12
        assertThat(bill.lines().get(0).discount()).isEqualByComparingTo("26.00");
    }

    @Test
    void theDiscountIsSharedAcrossLinesAndAddsUpToThePaisa() {
        Bill bill = PharmacyBillCalculator.calculate(
                List.of(line("1", "33.33", "5"), line("3", "10", "12"), line("1", "7.77", "0")), new BigDecimal("10"));

        BigDecimal shares = bill.lines().stream().map(l -> l.discount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nets = bill.lines().stream().map(l -> l.net()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shares).isEqualByComparingTo("10.00");
        assertThat(nets).isEqualByComparingTo(bill.net());
        assertThat(bill.net()).isEqualByComparingTo("61.10");
        assertThat(bill.lines().get(2).gst()).isEqualByComparingTo("0.00"); // 0% GST line
    }

    @Test
    void aDiscountLargerThanTheBillIsRefused() {
        assertThatThrownBy(() -> PharmacyBillCalculator.calculate(List.of(line("1", "100", "12")), new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be more than the bill");
    }

    @Test
    void aNegativeDiscountAndAnEmptyBillAreRefused() {
        assertThatThrownBy(() -> PharmacyBillCalculator.calculate(List.of(line("1", "100", "12")), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PharmacyBillCalculator.calculate(List.of(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one medicine");
    }

    @Test
    void invoiceSeriesFollowTheIndianFinancialYear() {
        assertThat(PharmacyInvoiceNumberService.financialYear(LocalDate.of(2026, 11, 21))).isEqualTo("26-27");
        assertThat(PharmacyInvoiceNumberService.financialYear(LocalDate.of(2027, 3, 31))).isEqualTo("26-27");
        assertThat(PharmacyInvoiceNumberService.financialYear(LocalDate.of(2027, 4, 1))).isEqualTo("27-28");
        assertThat(PharmacyInvoiceNumberService.financialYear(LocalDate.of(2099, 6, 1))).isEqualTo("99-00");
    }
}
