package com.hms.service.hospital;

import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules that differ per ward type. Everything else — beds, pricing, incharge — is identical
 * across types on purpose, so there is one implementation to keep correct rather than three.
 */
class WardTypeRulesTest {

    /** The ceiling is the rule that matters: two cases cannot share one theatre. */
    @Test
    void anOtWardIsNeverAllowedASecondBed() {
        assertThat(WardService.bedCountIsValidFor(WardType.OT, 1)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.OT, 2)).isFalse();
        assertThat(WardService.bedCountIsValidFor(WardType.OT, 40)).isFalse();
    }

    /**
     * Wards have always been creatable with no beds and filled in afterwards. Enforcing a minimum
     * would change behaviour well outside this feature - two existing WardServiceTest cases depend
     * on it - so zero stays valid for every type, theatres included.
     */
    @Test
    void aWardWithNoBedsYetIsStillValidForEveryType() {
        assertThat(WardService.bedCountIsValidFor(WardType.IPD, 0)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.ICU, 0)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.OT, 0)).isTrue();
    }

    @Test
    void ipdAndIcuWardsMayHaveManyBedsButNeverANegativeCount() {
        assertThat(WardService.bedCountIsValidFor(WardType.IPD, 30)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.ICU, 8)).isTrue();
        assertThat(WardService.bedCountIsValidFor(WardType.IPD, -1)).isFalse();
    }

    /** A patient is admitted to a ward or moved to ICU. A theatre is never an admission target. */
    @Test
    void onlyIpdAndIcuWardsCanHoldAnAdmittedPatient() {
        assertThat(WardService.ADMITTABLE_TYPES).containsExactlyInAnyOrder(WardType.IPD, WardType.ICU);
        assertThat(WardService.ADMITTABLE_TYPES).doesNotContain(WardType.OT);
    }
}
