package com.hms.service.hospital;

import com.hms.dto.IcuStayResponse;
import com.hms.entity.IpdBedHistory;
import com.hms.entity.Ward;
import com.hms.entity.WardType;
import com.hms.repository.IpdBedHistoryRepository;
import com.hms.repository.WardRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ICU stints on an admission, read out of the bed history.
 *
 * <p>There is no ICU table on purpose. A stay in intensive care is a stint in an ICU-typed ward,
 * and {@code ipd_bed_history} already records every ward/bed stint with its start and end. Storing
 * it a second time would create two answers to "when was this patient in ICU", and the one on the
 * bill would be whichever the billing code happened to read.
 */
@Service
public class IcuStayService {

    private final IpdBedHistoryRepository bedHistoryRepository;
    private final WardRepository wardRepository;

    public IcuStayService(IpdBedHistoryRepository bedHistoryRepository, WardRepository wardRepository) {
        this.bedHistoryRepository = bedHistoryRepository;
        this.wardRepository = wardRepository;
    }

    /** Every ICU stint on this admission, oldest first. Empty when the patient never went to ICU. */
    public List<IcuStayResponse> forAdmission(Long ipdAdmissionId) {
        if (ipdAdmissionId == null) {
            return List.of();
        }

        List<IpdBedHistory> stints =
                bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(ipdAdmissionId);

        // A patient bounces between the same two or three wards, so cache the lookups rather than
        // hitting the repository once per stint.
        Map<Long, Optional<Ward>> wardCache = new HashMap<>();

        List<IcuStayResponse> stays = new ArrayList<>();
        for (IpdBedHistory stint : stints) {
            if (stint.getWardId() == null) {
                continue;
            }
            Optional<Ward> ward =
                    wardCache.computeIfAbsent(stint.getWardId(), wardRepository::findById);
            if (ward.isEmpty() || ward.get().getWardType() != WardType.ICU) {
                continue;
            }
            stays.add(new IcuStayResponse(
                    stint.getWardId(),
                    ward.get().getWardName(),
                    stint.getBedId(),
                    stint.getAssignedAt(),
                    stint.getReleasedAt(),
                    nightsBetween(stint.getAssignedAt(), stint.getReleasedAt()),
                    stint.getReleasedAt() == null));
        }
        return stays;
    }

    /**
     * Nights counted the way a bill counts them. An ongoing stay is measured to now, so it reads as
     * the days accrued so far rather than zero — a patient who has been in ICU for a week should
     * not show "0 nights" simply because they have not left yet.
     */
    private long nightsBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null) {
            return 0;
        }
        LocalDateTime end = (to == null) ? LocalDateTime.now() : to;
        return Math.max(Duration.between(from, end).toDays(), 0);
    }
}
