package com.hms.service.hospital;

import com.hms.audit.PatientRecordAccess.Action;
import com.hms.audit.PatientRecordAccess.Kind;
import com.hms.entity.PatientAccessLog;
import com.hms.entity.User;
import com.hms.repository.PatientAccessLogRepository;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who opened, printed or downloaded which patient's record ("access log"). Written by
 * {@link com.hms.audit.PatientAccessAspect}; read by the hospital admin from the Patient File.
 */
@Service
@RequiredArgsConstructor
public class PatientAccessLogService {

    /** The same user re-opening the same record within this window is one access, not many. */
    static final int REPEAT_WINDOW_MINUTES = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String OPD_PREFIX = "OPD-";

    private final PatientAccessLogRepository repository;
    private final UserRepository userRepository;
    private final SecurityContextHelper securityHelper;

    /** Record one access. Its own transaction, so it never rides on (or rolls back) the read. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccess(Kind kind, String ref, Action action, String label) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            return; // platform user: not a hospital record access
        }
        String email = securityHelper.getCurrentUserEmail();
        String recordRef = truncate(ref, 64);
        Long patientId = patientOf(kind, ref, hospitalId);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        if (repository.existsRecent(hospitalId, patientId, email, label, recordRef, action.name(),
                now.minusMinutes(REPEAT_WINDOW_MINUTES))) {
            return;
        }
        PatientAccessLog row = new PatientAccessLog();
        row.setHospitalId(hospitalId);
        row.setPatientId(patientId);
        row.setRecordType(label);
        row.setRecordRef(recordRef);
        row.setAction(action.name());
        row.setUserEmail(email == null ? "unknown" : email);
        row.setUserId(currentUserIdOrNull());
        row.setUserRole(currentRoleOrNull());
        row.setClientIp(clientIp());
        repository.save(row);
    }

    /** The patient's access log, newest first, for the hospital admin. */
    @Transactional(readOnly = true)
    public Map<String, Object> forPatient(Long patientId, int page, int size) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<PatientAccessLog> rows = repository.findByHospitalIdAndPatientIdOrderByAccessedAtDesc(
                hospitalId, patientId, PageRequest.of(Math.max(page, 0), pageSize));

        List<Long> userIds = rows.stream().map(PatientAccessLog::getUserId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> names = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .filter(u -> hospitalId.equals(u.getHospitalId()))
                        .collect(Collectors.toMap(User::getId, PatientAccessLogService::displayName, (a, b) -> a));

        List<Map<String, Object>> content = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accessedAt", r.getAccessedAt());
            m.put("action", r.getAction());
            m.put("recordType", r.getRecordType());
            m.put("recordRef", r.getRecordRef());
            m.put("userName", names.getOrDefault(r.getUserId(), r.getUserEmail()));
            m.put("userEmail", r.getUserEmail());
            m.put("userRole", r.getUserRole());
            m.put("clientIp", r.getClientIp());
            return m;
        }).toList();

        Map<String, Object> out = new HashMap<>();
        out.put("content", content);
        out.put("page", rows.getNumber());
        out.put("size", rows.getSize());
        out.put("totalElements", rows.getTotalElements());
        out.put("totalPages", rows.getTotalPages());
        return out;
    }

    // ── tracing a requested record to its patient ───────────────────────────────────────────

    private Long patientOf(Kind kind, String ref, Long hospitalId) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String r = ref.trim();
        List<Long> found = switch (kind) {
            case PATIENT -> repository.patientByPublicId(r, hospitalId);
            case PATIENT_PK -> byNumber(r, id -> repository.patientByPk(id, hospitalId));
            case OPD -> byNumber(r.startsWith(OPD_PREFIX) ? r.substring(OPD_PREFIX.length()) : r,
                    id -> repository.patientOfOpd(id, hospitalId));
            case ADMISSION -> byNumber(r, id -> repository.patientOfAdmission(id, hospitalId));
            case APPOINTMENT -> appointment(r, hospitalId);
            case BILL -> byNumber(r, id -> repository.patientOfBill(id, hospitalId));
            case DOCUMENT -> repository.patientOfDocument(r, hospitalId);
            case ICU_STAY -> repository.patientOfIcuStay(r, hospitalId);
        };
        return found.isEmpty() ? null : found.get(0);
    }

    private List<Long> appointment(String ref, Long hospitalId) {
        List<Long> byPublicId = repository.patientOfAppointmentPublicId(ref, hospitalId);
        return byPublicId.isEmpty() ? byNumber(ref, id -> repository.patientOfAppointmentPk(id, hospitalId)) : byPublicId;
    }

    private static List<Long> byNumber(String ref, java.util.function.LongFunction<List<Long>> lookup) {
        try {
            return lookup.apply(Long.parseLong(ref));
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    // ── who and from where ───────────────────────────────────────────────────────────────────

    private Long currentUserIdOrNull() {
        try {
            return securityHelper.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String currentRoleOrNull() {
        try {
            return securityHelper.getCurrentUserRole();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return truncate(ip, 45);
    }

    private static String displayName(User u) {
        String name = u.getName();
        return name == null || name.isBlank() ? u.getEmail() : name;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
