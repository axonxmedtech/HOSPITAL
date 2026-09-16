package com.hms.security;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * ModuleAccessAspect - enforces {@link RequireModule} on the current session.
 *
 * The pointcut matches BOTH placements (method and class). It cannot bind the
 * annotation as an advice parameter, because Spring AOP rejects a bound
 * parameter inside an `||` expression ("inconsistent binding"), so the
 * annotation is resolved from the JoinPoint: method first, then the class.
 *
 * Scope: HOSPITAL tenants only. Clinic and Pharmacy have never had module
 * enforcement, and three controllers they share with Hospital
 * (AppointmentController, HospitalFeeController, HospitalInventoryController)
 * are gated on modules their plan types cannot even be granted -- enforcing
 * here would permanently 403 them.
 *
 * Trust boundary: the tenant id comes from the authenticated principal, and
 * EVERYTHING else -- the tenant type that decides whether this gate runs at all,
 * and the modules it checks -- is read from the hospital row. The type used to
 * come from the JWT's hospitalType claim, which meant a stale or tampered claim
 * could switch module enforcement off (claim CLINIC) or on (claim HOSPITAL) for
 * a tenant it did not describe. FacilityAccessAspect already resolves the type
 * this way; both aspects now agree on what is authoritative. A vanished row
 * denies rather than falling back to the token: with no authoritative state, a
 * claim must not be able to grant a module.
 *
 * @author HMS Team
 */
@Aspect
@Component
public class ModuleAccessAspect {

    @Autowired
    private HospitalRepository hospitalRepository;

    @Before("@annotation(com.hms.security.RequireModule) || @within(com.hms.security.RequireModule)")
    public void checkModuleAccess(JoinPoint joinPoint) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getDetails() instanceof UserAuthenticationDetails)) {
            return;
        }
        UserAuthenticationDetails details = (UserAuthenticationDetails) authentication.getDetails();

        // Super Admin has no hospital and bypasses module checks.
        if (details.getHospitalId() == null) {
            return;
        }

        RequireModule requireModule = resolveAnnotation(joinPoint);
        if (requireModule == null) {
            return;
        }

        // One read of the authoritative row, answering both questions it is asked: which kind of
        // tenant is this, and what does its plan hold. Neither answer comes from the caller's token.
        Hospital hospital = hospitalRepository.findById(details.getHospitalId())
                .orElseThrow(() -> new AccessDeniedException("Access Denied: tenant is unavailable."));

        // Only HOSPITAL tenants are module-gated (see class javadoc). A row with no type is read
        // as HOSPITAL -- the stricter reading, and the same default FacilityAccessAspect applies.
        HospitalType type = hospital.getType() == null ? HospitalType.HOSPITAL : hospital.getType();
        if (type != HospitalType.HOSPITAL) {
            return;
        }

        List<String> enabledModules = hospital.getModules();
        String requiredModule = requireModule.value();
        if (enabledModules == null || !enabledModules.contains(requiredModule)) {
            // 403, not 401: the session is valid, the plan simply lacks this module. A 401
            // makes the frontend interceptor clear the token and bounce the user to /login.
            throw new AccessDeniedException(
                    "Access Denied: Module '" + requiredModule + "' is not enabled for your hospital.");
        }
    }

    /** Method-level annotation wins over the class-level one. */
    private RequireModule resolveAnnotation(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RequireModule onMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequireModule.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), RequireModule.class);
    }
}
