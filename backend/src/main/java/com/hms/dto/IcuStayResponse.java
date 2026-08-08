package com.hms.dto;

import java.time.LocalDateTime;

/**
 * One stint an admitted patient spent in an ICU ward.
 *
 * <p>Derived from {@code ipd_bed_history} rather than stored again. Every transfer already writes a
 * row there with the ward, bed and in/out times, so a dedicated ICU table would be a copy that can
 * disagree with the original — and the disagreement would surface on a bill.
 */
public record IcuStayResponse(
        Long wardId,
        String wardName,
        Long bedId,
        LocalDateTime assignedAt,
        LocalDateTime releasedAt,
        long nights,
        boolean current
) { }
