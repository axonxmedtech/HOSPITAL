package com.hms.service.import_;

import com.hms.entity.import_.ImportRowState;

/** Running totals of FINAL row outcomes — what was persisted, not what evaluation proposed. */
public final class ImportCounters {
    private int total;
    private int created;
    private int updated;
    private int skipped;
    private int needsReview;
    private int failed;

    public void count(ImportRowState state) {
        total++;
        switch (state) {
            case CREATED -> created++;
            case UPDATED -> updated++;
            case SKIPPED -> skipped++;
            case NEEDS_REVIEW -> needsReview++;
            case FAILED -> failed++;
        }
    }

    public int total() { return total; }
    public int created() { return created; }
    public int updated() { return updated; }
    public int skipped() { return skipped; }
    public int needsReview() { return needsReview; }
    public int failed() { return failed; }

    public boolean allClean() {
        return needsReview == 0 && failed == 0;
    }

    public Snapshot snapshot() {
        return new Snapshot(total, created, updated, skipped, needsReview, failed);
    }

    public record Snapshot(int total, int created, int updated, int skipped, int needsReview, int failed) {}
}
