package com.hms.security;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.hms.entity.HospitalType;
import com.hms.exception.UnauthorizedException;
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
 * @author HMS Team
 */
@Aspect
@Component
public class ModuleAccessAspect {

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

        // Only HOSPITAL tenants are module-gated (see class javadoc). A null type means
        // the token was minted before the claim existed: skip for one release rather
        // than revoke access from a live session mid-token.
        if (!HospitalType.HOSPITAL.name().equals(details.getHospitalType())) {
            return;
        }

        RequireModule requireModule = resolveAnnotation(joinPoint);
        if (requireModule == null) {
            return;
        }

        List<String> enabledModules = details.getModules();
        String requiredModule = requireModule.value();
        if (enabledModules == null || !enabledModules.contains(requiredModule)) {
            throw new UnauthorizedException(
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
