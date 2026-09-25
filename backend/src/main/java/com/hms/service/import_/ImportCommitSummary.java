package com.hms.service.import_;

import com.hms.entity.import_.ImportStatus;
import java.time.LocalDateTime;

/** A finished commit, as the administrator sees it. */
public record ImportCommitSummary(String batchPublicId, ImportStatus status, ImportCounters.Snapshot counts, LocalDateTime committedAt) {}
