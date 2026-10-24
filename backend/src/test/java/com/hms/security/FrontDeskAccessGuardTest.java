package com.hms.security;

import com.hms.entity.HospitalSetting;
import com.hms.repository.HospitalSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The front-desk condition, in isolation.
 *
 * <p>Who may register a patient or book an appointment is two questions, not one: the role gate
 * ({@code @PreAuthorize}) and, for a doctor, whether this hospital keeps a reception desk. This
 * covers the second. The rule is the same one the controllers enforced inline before it was
 * extracted, so these cases are the contract that extraction had to preserve.
 */
class FrontDeskAccessGuardTest {

    private static final long HOSPITAL = 42L;

    private FrontDeskAccessGuard guard(String role, Long hospitalId, String mode) {
        SecurityContextHelper helper = mock(SecurityContextHelper.class);
        when(helper.getCurrentUserRole()).thenReturn(role);
        when(helper.getCurrentHospitalId()).thenReturn(hospitalId);

        HospitalSettingRepository settings = mock(HospitalSettingRepository.class);
        if (mode == null) {
            when(settings.findByHospital_Id(anyLong())).thenReturn(Optional.empty());
        } else {
            HospitalSetting s = new HospitalSetting();
            s.setReceptionMode(mode);
            when(settings.findByHospital_Id(anyLong())).thenReturn(Optional.of(s));
        }
        return new FrontDeskAccessGuard(helper, settings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOSPITAL_ADMIN", "RECEPTIONIST"})
    void aRoleThatOwnsTheFrontDeskIsNeverAskedAboutReceptionMode(String role) {
        assertThatCode(() -> guard(role, HOSPITAL, "HAS_RECEPTIONIST").require()).doesNotThrowAnyException();
    }

    @Test
    void aDoctorIsRefusedWhereTheHospitalKeepsAReceptionDesk() {
        assertThatThrownBy(() -> guard("DOCTOR", HOSPITAL, "HAS_RECEPTIONIST").require())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("handled by reception");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLO", "BOTH", "solo", "both"})
    void aDoctorActsAsTheFrontDeskUnderSoloAndBoth(String mode) {
        assertThatCode(() -> guard("DOCTOR", HOSPITAL, mode).require()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "ROLE_DOCTOR", "doctor"})
    void theDoctorCheckIsIndifferentToTheAuthorityPrefixAndCase(String role) {
        assertThatThrownBy(() -> guard(role, HOSPITAL, "HAS_RECEPTIONIST").require())
                .isInstanceOf(AccessDeniedException.class);
    }

    /** No settings row yet must deny, not grant: the transient default is HAS_RECEPTIONIST. */
    @Test
    void aHospitalWithNoSettingsRowFailsClosed() {
        assertThatThrownBy(() -> guard("DOCTOR", HOSPITAL, null).require())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("handled by reception");
    }

    @Test
    void aDoctorWithoutAHospitalContextIsRefused() {
        assertThatThrownBy(() -> guard("DOCTOR", null, "BOTH").require())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid hospital context");
    }

    /** An unknown mode is not SOLO and not BOTH, so it denies — the check is allow-list shaped. */
    @Test
    void anUnrecognisedReceptionModeDenies() {
        assertThatThrownBy(() -> guard("DOCTOR", HOSPITAL, "SOMETHING_ELSE").require())
                .isInstanceOf(AccessDeniedException.class);
    }
}
