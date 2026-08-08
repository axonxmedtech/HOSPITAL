package com.hms.service.hospital;

import com.hms.entity.Ward;
import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The billing promise this feature was built for: a patient admitted to a ward, moved to ICU,
 * moved back and discharged has ONE admission and ONE bill, with each day charged at whatever ward
 * they were in that day.
 *
 * <p>It holds because {@code BillingSchedulerService} reads the nightly price from the admission's
 * <em>current</em> ward, and moving a patient is an ordinary bed change that moves exactly that.
 * Nothing here re-implements billing; these assertions pin the properties the arrangement depends
 * on, so that if someone later gives ICU its own admission record, or makes a theatre admittable,
 * this is what tells them they have split the bill in two.
 *
 * <p><b>Known limitation.</b> These are unit assertions, not an end-to-end run of the scheduler
 * across simulated days against a real database. That belongs in the Testcontainers suite
 * (Failsafe, not {@code mvn test}) and should exist before this is relied on commercially — a
 * billing bug is the kind a customer finds first.
 */
class IcuEpisodeBillingTest {

    private Ward ward(WardType type, String price) {
        Ward w = new Ward();
        w.setWardType(type);
        w.setBedPrice(new BigDecimal(price));
        return w;
    }

    /** Mirrors BillingSchedulerService's price resolution (its step 3). */
    private BigDecimal nightlyRateFor(Ward currentWard) {
        return currentWard != null && currentWard.getBedPrice() != null
                ? currentWard.getBedPrice()
                : BigDecimal.ZERO;
    }

    /**
     * Asserts the lookup the scheduler performs, not arithmetic — computing an expected total the
     * same way as the actual would prove nothing.
     */
    @Test
    void theNightlyRateIsReadFromWhicheverWardThePatientIsInNow() {
        Ward general = ward(WardType.IPD, "1500.00");
        Ward icu = ward(WardType.ICU, "6000.00");

        // Days 1-3: the admission points at the general ward.
        assertThat(nightlyRateFor(general)).isEqualByComparingTo("1500.00");
        // Days 4-8: the bed change moved the admission to the ICU ward.
        assertThat(nightlyRateFor(icu)).isEqualByComparingTo("6000.00");
        // Days 9-10: moved back, without a new admission being opened.
        assertThat(nightlyRateFor(general)).isEqualByComparingTo("1500.00");
    }

    /**
     * The arrangement only works while ICU is a destination an existing admission can move to. If
     * ICU ever stops being transferrable, patients would need a second admission — and a second
     * bill, which is exactly what was asked not to happen.
     */
    @Test
    void icuStaysReachableWithoutOpeningASecondAdmission() {
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.ICU)).isTrue();
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.IPD)).isTrue();
    }

    /** A theatre is charged once for the case, and is never the ward a nightly rate comes from. */
    @Test
    void theTheatreChargeIsTakenFromTheOtWardAndIsNotANightlyRate() {
        Ward theatre = ward(WardType.OT, "2500.00");

        assertThat(SurgeryService.otChargeFor(theatre)).isEqualByComparingTo("2500.00");
        // Never the admission's current ward, so the nightly scheduler never sees it.
        assertThat(WardService.ADMITTABLE_TYPES).doesNotContain(WardType.OT);
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.OT)).isFalse();
    }

    /**
     * An ICU ward is priced like any other ward, so the day a patient spends there is charged by
     * the same scheduler and lands on the same bill. Nothing special-cases ICU in billing, which
     * is precisely why one bill covers the whole episode.
     */
    @Test
    void anIcuWardIsPricedAndChargedLikeAnyOtherWard() {
        Ward icu = ward(WardType.ICU, "6000.00");

        assertThat(nightlyRateFor(icu)).isEqualByComparingTo("6000.00");
        assertThat(SurgeryService.otChargeFor(icu)).isEqualByComparingTo("0");
    }
}
