package com.hms.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WardTypeTest {

    /**
     * Every ward that exists today predates typing and is an ordinary inpatient ward. Defaulting to
     * IPD is what makes the migration safe: nothing is reclassified by guessing at its name, which
     * the OT module already learned the hard way ("FOOT WARD" contains "OT").
     */
    @Test
    void aNewWardIsAnIpdWardUntilSaidOtherwise() {
        Ward ward = new Ward();
        assertThat(ward.getWardType()).isEqualTo(WardType.IPD);
    }

    @Test
    void theThreeTypesAreIpdIcuAndOt() {
        assertThat(WardType.values()).containsExactly(WardType.IPD, WardType.ICU, WardType.OT);
    }
}
