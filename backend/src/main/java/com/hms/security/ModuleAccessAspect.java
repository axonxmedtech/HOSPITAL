package com.hms.security;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.hms.exception.UnauthorizedException;
import java.util.List;

/**
 * ModuleAccessAspect - Aspect to enforce module permissions
 * 
 * This aspect intercepts methods annotated with @RequireModule
 * and checks if the current user's hospital has the required module enabled.
 * 
 * @author HMS Team
 * @version Phase-3
 */
@Aspect
@Component
public class ModuleAccessAspect {

    @Before("@annotation(requireModule)")
    public void checkModuleAccess(JoinPoint joinPoint, RequireModule requireModule) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getDetails() instanceof UserAuthenticationDetails) {
            UserAuthenticationDetails details = (UserAuthenticationDetails) authentication.getDetails();
            List<String> enabledModules = details.getModules();
            String requiredModule = requireModule.value();

            // Super Admin might not have modules (or has all), depending on logic.
            // Currently Super Admin has null hospitalId.
            if (details.getHospitalId() == null) {
                return; // Super Admin bypasses module checks
            }

            if (enabledModules == null || !enabledModules.contains(requiredModule)) {
                throw new UnauthorizedException(
                        "Access Denied: Module '" + requiredModule + "' is not enabled for your hospital.");
            }
        }
    }
}
