package com.hms.service.hospital;

import com.hms.dto.PatientRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Appointment booking auto-creates a patient when only name and phone are supplied, which makes it
 * the <em>second</em> place a patient record can be created.
 *
 * <p>Strict field rules used to live on the {@code Patient} entity, so Hibernate rejected a
 * malformed phone no matter which path reached it. Those rules moved to {@link PatientRequest} so
 * the legacy importer could keep blank source values blank — and that quietly removed the net from
 * this path. A phone the reception desk refuses would have been accepted through booking.
 *
 * <p>{@code AppointmentService.assertValidNewPatientDetails} closes that hole by validating a
 * {@code PatientRequest} built from the booking details. These tests pin the rules it applies. They
 * exercise the same validator and the same DTO the service uses, so if someone relaxes a constraint
 * on {@code PatientRequest}, this fails alongside the controller-level guards in
 * {@code PatientApiTest}.
 */
class AppointmentPatientValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** Mirrors AppointmentService.assertValidNewPatientDetails, including its gender default. */
    private boolean wouldBeAccepted(String name, String phone, String gender, String email) {
        PatientRequest candidate = new PatientRequest();
        candidate.setName(name);
        candidate.setPhone(phone);
        candidate.setGender(gender != null ? gender : "Unknown");
        candidate.setEmail(email != null && !email.isBlank() ? email : null);
        return validator.validate(candidate).isEmpty();
    }

    @Test
    void acceptsAnOrdinaryWalkInBooking() {
        assertThat(wouldBeAccepted("Ramesh Patel", "9876543210", null, null)).isTrue();
    }

    @Test
    void defaultsMissingGenderRatherThanRejectingTheBooking() {
        // Pre-existing behaviour: booking never required gender, and this change must not start.
        assertThat(wouldBeAccepted("Ramesh Patel", "9876543210", null, null)).isTrue();
    }

    @Test
    void rejectsANonTenDigitPhoneJustLikeManualRegistration() {
        assertThat(wouldBeAccepted("Ramesh Patel", "+919876543210", null, null)).isFalse();
        assertThat(wouldBeAccepted("Ramesh Patel", "98765", null, null)).isFalse();
        assertThat(wouldBeAccepted("Ramesh Patel", "022-24445555", null, null)).isFalse();
    }

    @Test
    void rejectsABlankName() {
        assertThat(wouldBeAccepted("", "9876543210", null, null)).isFalse();
    }

    @Test
    void rejectsAMalformedEmailWhenOneIsSupplied() {
        assertThat(wouldBeAccepted("Ramesh Patel", "9876543210", null, "not-an-email")).isFalse();
    }

    @Test
    void treatsABlankEmailAsAbsentRatherThanInvalid() {
        assertThat(wouldBeAccepted("Ramesh Patel", "9876543210", null, "")).isTrue();
    }

    @Test
    void rejectsAMalformedGenderWhenOneIsSupplied() {
        assertThat(wouldBeAccepted("Ramesh Patel", "9876543210", "M/F/Other?", null)).isFalse();
    }
}
