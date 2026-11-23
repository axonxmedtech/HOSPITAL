package com.hms.audit;

import com.hms.service.hospital.PatientAccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;

/**
 * Records patient-record reads marked with {@link PatientRecordAccess}. Runs only after the
 * controller returned (so the role and tenant checks already passed) and only for a 2xx answer.
 *
 * <p>Best effort by design: a failure to write the log is logged and swallowed, never turned into
 * a failed request for the clinician in front of a patient.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PatientAccessAspect {

    private final PatientAccessLogService accessLogService;

    @AfterReturning(pointcut = "@annotation(access)", returning = "result")
    public void afterAccess(JoinPoint joinPoint, PatientRecordAccess access, Object result) {
        if (result instanceof ResponseEntity<?> response && !response.getStatusCode().is2xxSuccessful()) {
            return;
        }
        String ref = firstPathVariable(joinPoint);
        if (ref == null) {
            return;
        }
        try {
            accessLogService.logAccess(access.kind(), ref, access.action(), label(access));
        } catch (RuntimeException e) {
            log.warn("Patient access log not written ({} {}): {}", access.kind(), access.action(), e.getMessage());
        }
    }

    private static String label(PatientRecordAccess access) {
        return access.label().isBlank() ? access.kind().name() : access.label();
    }

    private static String firstPathVariable(JoinPoint joinPoint) {
        Annotation[][] params = ((MethodSignature) joinPoint.getSignature()).getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < params.length; i++) {
            for (Annotation a : params[i]) {
                if (a instanceof PathVariable && args[i] != null) {
                    return String.valueOf(args[i]);
                }
            }
        }
        return null;
    }
}
