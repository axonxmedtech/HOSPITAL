package com.hms.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RequireModule - Annotation to restrict access based on enabled modules
 * 
 * Usage: @RequireModule("IPD")
 * 
 * @author HMS Team
 * @version Phase-3
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireModule {
    String value();
}
