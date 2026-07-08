package com.hms.service.hospital;

import org.springframework.stereotype.Service;
import java.time.LocalTime;

/**
 * NurseShiftScheduleService - nurse shift scheduling. Phase B2 fills this in.
 * For now only the template-change propagation hook exists (no-op until schedules exist).
 */
@Service
public class NurseShiftScheduleService {
    /** Future-dated schedules using this template adopt the new times; past rows unchanged. */
    public int applyTemplateChangeToFuture(Long templateId, LocalTime start, LocalTime end) { return 0; }
}
