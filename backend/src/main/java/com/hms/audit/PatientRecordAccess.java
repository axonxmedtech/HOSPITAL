package com.hms.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method that opens, prints or downloads a patient's record. After the method
 * returns successfully, {@link PatientAccessAspect} records who did it in {@code patient_access_logs}.
 * A refused or failed request (exception, 4xx, 5xx) is not recorded as an access.
 *
 * <p>The record is identified by the method's FIRST {@code @PathVariable}; {@link #kind()} says
 * what kind of id that is, so it can be traced to the patient.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PatientRecordAccess {

    /** What the first path variable identifies. */
    Kind kind();

    /** What the user did with it. */
    Action action() default Action.VIEW;

    /** Label shown in the access log; defaults to the record kind. */
    String label() default "";

    enum Action { VIEW, PRINT, DOWNLOAD }

    /** The kinds of id a path variable can carry, each traced to its patient differently. */
    enum Kind {
        /** Patient public id (PAT-…). */
        PATIENT,
        /** Patient numeric (internal) id. */
        PATIENT_PK,
        /** OPD visit id: 12 or OPD-12. */
        OPD,
        /** IPD admission id (numeric). */
        ADMISSION,
        /** Appointment public id, or its numeric id. */
        APPOINTMENT,
        /** Bill id (numeric). */
        BILL,
        /** Patient document public id. */
        DOCUMENT,
        /** ICU stay public id. */
        ICU_STAY
    }
}
