package com.hms.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Client feedback #8: a saved medicine never appears in a list without its days. */
class PrescriptionDurationTest {

    private static Prescription with(String duration, Integer days) {
        Prescription p = new Prescription();
        p.setDuration(duration);
        p.setDurationDays(days);
        p.alignDuration();
        return p;
    }

    @Test
    void anIpdOrderWithOnlyANumberOfDaysGetsItsDurationText() {
        assertThat(with(null, 5).getDuration()).isEqualTo("5 Days");
        assertThat(with("", 1).getDuration()).isEqualTo("1 Day");
    }

    @Test
    void aTypedDurationGivesTheNumberOfDays() {
        assertThat(with("3 Days", null).getDurationDays()).isEqualTo(3);
        assertThat(with("7", null).getDurationDays()).isEqualTo(7);
    }

    @Test
    void weeksAndFreeTextAreLeftAsWritten() {
        Prescription weeks = with("2 Weeks", null);
        assertThat(weeks.getDuration()).isEqualTo("2 Weeks");
        assertThat(weeks.getDurationDays()).isNull();
        assertThat(with("Once", 1).getDuration()).isEqualTo("Once");
    }
}
