package com.hms.service.hospital;

import com.hms.entity.WardType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moving a patient into ICU is an ordinary bed change to an ICU-typed ward. It deliberately does
 * NOT open a second admission: the IPD admission is the billing episode, so a patient who goes
 * ward → ICU → ward → home is one admission and one bill, with BillingSchedulerService charging
 * each day at whatever ward they are in that day.
 */
class IcuTransferTest {

    @Test
    void icuIsAValidTransferDestination() {
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.ICU)).isTrue();
    }

    @Test
    void anIpdWardIsAValidTransferDestinationSoPatientsCanComeBack() {
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.IPD)).isTrue();
    }

    /**
     * A theatre is not somewhere a patient is admitted. Surgery records its OT ward on the surgery
     * itself; pointing the admission at one would make the nightly bed charge follow the theatre
     * rate and leave the patient with no ward to return to.
     */
    @Test
    void aTheatreIsNeverATransferDestination() {
        assertThat(IpdAdmissionService.isTransferrableTo(WardType.OT)).isFalse();
    }

    /**
     * Admission and transfer must agree on what a patient may occupy. Two separately maintained
     * lists would eventually let a bed be admittable but not transferrable, or the reverse.
     */
    @Test
    void transferAndAdmissionAgreeOnWhatAPatientMayOccupy() {
        for (WardType type : WardType.values()) {
            assertThat(IpdAdmissionService.isTransferrableTo(type))
                    .as("transfer and admission disagree about %s", type)
                    .isEqualTo(WardService.ADMITTABLE_TYPES.contains(type));
        }
    }
}
