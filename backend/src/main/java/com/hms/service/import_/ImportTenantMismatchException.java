package com.hms.service.import_;

/**
 * A candidate evaluated under one hospital was handed to a writer working for another. This is
 * a programming or security error, never a data problem, so it is not a row outcome: it
 * propagates and stops the run.
 */
public class ImportTenantMismatchException extends IllegalStateException {
    public ImportTenantMismatchException(Long candidateHospital, Long contextHospital) {
        super("Import candidate was evaluated for hospital " + candidateHospital
                + " but the write context is hospital " + contextHospital);
    }
}
