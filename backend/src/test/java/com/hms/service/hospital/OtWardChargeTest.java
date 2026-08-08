package com.hms.service.hospital;

import com.hms.entity.Ward;
import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A theatre is charged once for the case, not per day.
 *
 * <p>BillingSchedulerService charges the patient's current ward nightly, and the patient's current
 * ward is never the theatre — they stay admitted in a ward or an ICU throughout. So the theatre fee
 * has to be added when the surgery takes the theatre, and exactly once.
 */
class OtWardChargeTest {

    private Ward ward(WardType type, String price) {
        Ward w = new Ward();
        w.setWardId(9L);
        w.setWardName("OT-2");
        w.setWardType(type);
        w.setBedPrice(price == null ? null : new BigDecimal(price));
        return w;
    }

    @Test
    void chargesTheOtWardsPriceForTheCase() {
        assertThat(SurgeryService.otChargeFor(ward(WardType.OT, "2500.00")))
                .isEqualByComparingTo("2500.00");
    }

    /** An unpriced theatre adds nothing, rather than a zero line nobody asked for. */
    @Test
    void aTheatreWithNoPriceSetAddsNothing() {
        assertThat(SurgeryService.otChargeFor(ward(WardType.OT, null)))
                .isEqualByComparingTo("0");
    }

    @Test
    void aWardThatIsNotATheatreIsNeverChargedAsOne() {
        assertThat(SurgeryService.otChargeFor(ward(WardType.IPD, "1500.00")))
                .isEqualByComparingTo("0");
        assertThat(SurgeryService.otChargeFor(ward(WardType.ICU, "6000.00")))
                .isEqualByComparingTo("0");
    }

    @Test
    void noWardAtAllIsNotACharge() {
        assertThat(SurgeryService.otChargeFor(null)).isEqualByComparingTo("0");
    }

    /**
     * The bill line is keyed on the surgery, not the theatre, so rescheduling a case — including
     * into a different theatre — cannot produce a second theatre charge for the same surgery.
     */
    @Test
    void theBillLineIdentifiesTheSurgerySoItCanOnlyBeChargedOnce() {
        String first = SurgeryService.theatreChargeDescription(418L, "OT-2");
        String afterMovingTheatres = SurgeryService.theatreChargeDescription(418L, "OT-5");

        assertThat(first).contains("(Surgery #418)").startsWith("Theatre charge");
        assertThat(afterMovingTheatres).contains("(Surgery #418)");

        // A different surgery is a different line, so two cases in one admission both get charged.
        assertThat(SurgeryService.theatreChargeDescription(419L, "OT-2"))
                .doesNotContain("(Surgery #418)");
    }

    /** The theatre name is on the line so a bill reads sensibly, not just an opaque id. */
    @Test
    void theBillLineNamesTheTheatre() {
        assertThat(SurgeryService.theatreChargeDescription(418L, "OT-2")).contains("OT-2");
    }
}
