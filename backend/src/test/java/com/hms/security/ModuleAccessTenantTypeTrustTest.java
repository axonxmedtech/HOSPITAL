package com.hms.security;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.repository.HospitalRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which tenant type is this? The answer decides whether module enforcement runs at all, so it must
 * come from the hospital row rather than from a claim inside the caller's own token.
 *
 * <p>{@link FacilityAccessAspect} already settles this question the authoritative way — it loads the
 * row (line 37) and reads {@code hospital.getType()} (line 39), never the JWT. These tests hold
 * {@link ModuleAccessAspect} to the same standard, by making the row and the token disagree and
 * asserting the row wins every time.
 *
 * <p>The disagreement is not hypothetical only under a stolen signing key: a token minted before a
 * tenant's type was corrected, or simply before the claim existed, carries the same stale answer.
 */
class ModuleAccessTenantTypeTrustTest {

    private final HospitalRepository hospitalRepository = mock(HospitalRepository.class);
    private final ModuleAccessAspect aspect = new ModuleAccessAspect();

    /** A plan WITHOUT the OT module, so an enforced gate must deny. */
    private static final List<String> NO_OT = List.of("OPD", "BILLING");

    ModuleAccessTenantTypeTrustTest() {
        ReflectionTestUtils.setField(aspect, "hospitalRepository", hospitalRepository);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @RequireModule("OT")
    static class GatedController {
        public void handler() { }
    }

    /** The authoritative row. */
    private void row(HospitalType type, List<String> modules) {
        Hospital hospital = new Hospital();
        hospital.setType(type);
        hospital.setModules(new ArrayList<>(modules));
        when(hospitalRepository.findById(anyLong())).thenReturn(Optional.of(hospital));
    }

    /** What the caller's token says — deliberately allowed to disagree with the row. */
    private void tokenClaims(String hospitalType, List<String> modules) {
        UserAuthenticationDetails details =
                new UserAuthenticationDetails(1L, "DOCTOR", 7L, new ArrayList<>(modules));
        details.setHospitalType(hospitalType);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user@tenant.test", null, List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void invoke() throws Exception {
        Method method = GatedController.class.getMethod("handler");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new GatedController());
        aspect.checkModuleAccess(joinPoint);
    }

    // ── A hospital must be gated no matter what its token claims ──────────────

    @Test
    void aHospitalWhoseTokenClaimsClinicIsStillGated() {
        row(HospitalType.HOSPITAL, NO_OT);
        tokenClaims(HospitalType.CLINIC.name(), NO_OT);
        assertThatThrownBy(this::invoke)
                .as("the row says HOSPITAL; a CLINIC claim in the token must not switch enforcement off")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aHospitalWhoseTokenClaimsPharmacyIsStillGated() {
        row(HospitalType.HOSPITAL, NO_OT);
        tokenClaims(HospitalType.PHARMACY.name(), NO_OT);
        assertThatThrownBy(this::invoke).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aHospitalWhoseTokenCarriesNoTypeClaimIsStillGated() {
        row(HospitalType.HOSPITAL, NO_OT);
        tokenClaims(null, NO_OT); // a token minted before the claim existed
        assertThatThrownBy(this::invoke)
                .as("an absent claim must not be a way out of module enforcement")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRowWithNoTypeIsTreatedAsHospital_theStricterReading() {
        row(null, NO_OT);
        tokenClaims(HospitalType.CLINIC.name(), NO_OT);
        assertThatThrownBy(this::invoke)
                .as("FacilityAccessAspect defaults a null type to HOSPITAL; this must agree")
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Non-hospital tenant types keep their existing intentional skip ─────────

    @Test
    void aClinicIsSkipped_evenWhenItsTokenClaimsHospital() {
        row(HospitalType.CLINIC, NO_OT);
        tokenClaims(HospitalType.HOSPITAL.name(), NO_OT);
        assertThatCode(this::invoke)
                .as("the clinic skip is existing product policy and the row is what establishes it")
                .doesNotThrowAnyException();
    }

    @Test
    void aStandalonePharmacyIsSkipped_evenWhenItsTokenClaimsHospital() {
        row(HospitalType.PHARMACY, NO_OT);
        tokenClaims(HospitalType.HOSPITAL.name(), NO_OT);
        assertThatCode(this::invoke).doesNotThrowAnyException();
    }

    // ── Authoritative state changes mid-session ───────────────────────────────

    @Test
    void aTypeCorrectionTakesEffectOnTheNextRequest_withoutANewToken() {
        row(HospitalType.CLINIC, NO_OT);
        tokenClaims(HospitalType.CLINIC.name(), NO_OT);
        assertThatCode(this::invoke).doesNotThrowAnyException();

        // The row is corrected to HOSPITAL. The principal, and its CLINIC claim, are untouched.
        row(HospitalType.HOSPITAL, NO_OT);
        assertThatThrownBy(this::invoke)
                .as("the same session must follow the current row, exactly as module revocation does")
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Missing authoritative state ───────────────────────────────────────────

    @Test
    void aVanishedTenantRowDeniesRatherThanFallingBackToTheToken() {
        tokenClaims(HospitalType.HOSPITAL.name(), List.of("OPD", "OT")); // token claims OT
        when(hospitalRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(this::invoke)
                .as("with no authoritative state, the token must not be able to grant a module")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superAdminStillBypasses() {
        UserAuthenticationDetails details =
                new UserAuthenticationDetails(1L, "SUPER_ADMIN", null, List.of());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("sa@platform.test", null, List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThatCode(this::invoke).doesNotThrowAnyException();
    }
}
