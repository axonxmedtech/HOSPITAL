package com.hms.security;

import com.hms.service.hospital.DuplicatePhoneConstraint;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The predicate that decides whether a failed write was the duplicate-phone race or something else.
 *
 * <p>This matters more than its size suggests: translating EVERY integrity violation into
 * "this phone number is already registered" would tell reception a comforting lie about a missing
 * foreign key or a duplicated public_id, and the real defect would never be reported. The messages
 * below are the shapes MySQL and Hibernate actually produce — the duplicate-phone one is copied
 * from a real failure observed on MySQL 8.0.46.
 */
class DuplicatePhoneConstraintTest {

    private static DataIntegrityViolationException wrapped(String driverMessage) {
        // Spring wraps Hibernate, which wraps the driver. The index name is several levels down.
        return new DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException("could not execute statement",
                        new SQLIntegrityConstraintViolationException(driverMessage), "some-constraint"));
    }

    @Test
    void recognisesTheActivePhoneIndexThroughTheWholeWrapperChain() {
        assertThat(DuplicatePhoneConstraint.isViolation(wrapped(
                "Duplicate entry '1-9900011111' for key 'patients.uq_patient_active_phone'")))
                .isTrue();
    }

    @Test
    void doesNotClaimUnrelatedConstraintFailures() {
        assertThat(DuplicatePhoneConstraint.isViolation(wrapped(
                "Duplicate entry 'abc' for key 'patients.UK_8isyrjl9ji56k5uv4cgp9p2q6'"))
        ).as("a duplicated public_id is not a duplicate phone").isFalse();

        assertThat(DuplicatePhoneConstraint.isViolation(wrapped(
                "Cannot add or update a child row: a foreign key constraint fails"))
        ).as("a foreign key failure must keep its own generic handling").isFalse();

        assertThat(DuplicatePhoneConstraint.isViolation(wrapped(
                "Column 'created_at' cannot be null"))
        ).as("a NOT NULL violation is a defect, not a duplicate phone").isFalse();

        assertThat(DuplicatePhoneConstraint.isViolation(
                new DataIntegrityViolationException("no cause at all")))
                .isFalse();
    }

    @Test
    void survivesASelfReferencingCauseWithoutLooping() {
        // Defensive: a malformed exception chain must not hang the request thread.
        RuntimeException loop = new RuntimeException("boom") {
            @Override public synchronized Throwable getCause() { return this; }
        };
        assertThat(DuplicatePhoneConstraint.isViolation(
                new DataIntegrityViolationException("wrapper", loop))).isFalse();
    }
}
