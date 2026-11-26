package com.hms.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The address of the client behind the current request, for audit trails (audit_logs and
 * patient_access_logs). Honours the first X-Forwarded-For hop, as the rate limiter does, since the
 * app runs behind Nginx. Null outside a web request (a scheduled job, startup).
 */
public final class ClientIp {

    private static final int MAX = 45; // fits an IPv6 address

    private ClientIp() {
    }

    public static String current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip == null || ip.length() <= MAX ? ip : ip.substring(0, MAX);
    }
}
