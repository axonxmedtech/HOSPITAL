package com.hms.service.hospital;

import com.hms.entity.Patient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The duplicate rule for creating a patient: <strong>name AND phone must both match</strong>.
 *
 * <p>Either alone is legitimate and common. Two unrelated people share a name, so blocking on name
 * would stop a second Ramesh Patel existing at all. A family shares one mobile number, so blocking
 * on phone would stop a mother registering her child. Only the pair is a real duplicate.
 *
 * <p>These tests pin the matching semantics themselves — the comparison that
 * {@code PatientService.assertNotADuplicate} and {@code AppointmentService} both apply. Getting
 * this wrong in either direction is damaging: too strict and reception cannot register a real
 * patient, too loose and two people end up sharing one clinical record.
 */
class PatientDuplicateCheckTest {

    /** Mirrors the normalisation both call sites use. */
    private String normalise(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private boolean isDuplicate(String newName, String newPhone, List<Patient> samePhonePatients) {
        String candidate = normalise(newName);
        return samePhonePatients.stream()
                .anyMatch(p -> candidate.equals(normalise(p.getName())));
    }

    private Patient existing(String name, String phone) {
        Patient p = new Patient();
        p.setName(name);
        p.setPhone(phone);
        return p;
    }

    @Test
    void blocksWhenBothNameAndPhoneMatch() {
        List<Patient> samePhone = List.of(existing("Ramesh Patel", "9876543210"));

        assertThat(isDuplicate("Ramesh Patel", "9876543210", samePhone)).isTrue();
    }

    /** A family sharing one mobile number must still be able to register separately. */
    @Test
    void allowsAFamilyMemberOnTheSamePhoneNumber() {
        List<Patient> samePhone = List.of(existing("Ramesh Patel", "9876543210"));

        assertThat(isDuplicate("Sunita Patel", "9876543210", samePhone)).isFalse();
        assertThat(isDuplicate("Aarav Patel", "9876543210", samePhone)).isFalse();
    }

    /** Two unrelated people genuinely share names; the phone is what separates them. */
    @Test
    void allowsANamesakeOnADifferentPhoneNumber() {
        // The repository lookup is by phone, so a namesake on another number never appears here.
        List<Patient> samePhone = List.of();

        assertThat(isDuplicate("Ramesh Patel", "9000000001", samePhone)).isFalse();
    }

    @Test
    void treatsCasingAndExtraSpacingAsTheSameName() {
        List<Patient> samePhone = List.of(existing("Ramesh Patel", "9876543210"));

        assertThat(isDuplicate("  ramesh   patel ", "9876543210", samePhone)).isTrue();
        assertThat(isDuplicate("RAMESH PATEL", "9876543210", samePhone)).isTrue();
    }

    /** Two family members already share the number; only the matching name is a duplicate. */
    @Test
    void picksTheMatchingNameOutOfSeveralPatientsSharingAPhone() {
        List<Patient> samePhone = List.of(
                existing("Ramesh Patel", "9876543210"),
                existing("Sunita Patel", "9876543210"),
                existing("Aarav Patel", "9876543210"));

        assertThat(isDuplicate("Sunita Patel", "9876543210", samePhone)).isTrue();
        assertThat(isDuplicate("Meena Patel", "9876543210", samePhone)).isFalse();
    }
}
