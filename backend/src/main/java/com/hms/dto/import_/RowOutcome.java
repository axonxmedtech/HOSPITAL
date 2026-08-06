package com.hms.dto.import_;

import com.hms.entity.Patient;

/**
 * The verdict for one parsed row. `patient` is populated for CREATE and UPDATE and is null
 * otherwise. Nothing here is persisted — the engine decides whether this is a dry run.
 */
public record RowOutcome(
        Action action,
        Patient patient,
        String columnName,
        String message
) {
    public enum Action { CREATE, UPDATE, SKIP, ERROR }

    public static RowOutcome create(Patient p) { return new RowOutcome(Action.CREATE, p, null, null); }
    public static RowOutcome update(Patient p) { return new RowOutcome(Action.UPDATE, p, null, null); }
    public static RowOutcome skip(String message) { return new RowOutcome(Action.SKIP, null, null, message); }
    public static RowOutcome error(String column, String message) {
        return new RowOutcome(Action.ERROR, null, column, message);
    }
}
