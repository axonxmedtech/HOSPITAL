package com.hms.exception;

import java.util.Map;

/**
 * A new visit was asked for a patient who already has one open: admitted to IPD, or already
 * waiting in the same doctor's OPD queue. A 409 like any conflict, but it carries where the open
 * visit is (for example {@code ipdAdmissionId}) so the screen can take the user straight to it.
 */
public class ActiveEncounterConflictException extends ConflictException {
    private final transient Map<String, String> encounter;

    public ActiveEncounterConflictException(String message, Map<String, String> encounter) {
        super(message);
        this.encounter = Map.copyOf(encounter);
    }

    public Map<String, String> getEncounter() {
        return encounter;
    }
}
