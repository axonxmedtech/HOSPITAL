package com.hms.service.hospital;

import com.hms.entity.HospitalSidebarSetting;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.HospitalSidebarSettingRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Settings → Sidebar: the admin chooses, per role, which tabs appear in the sidebar.
 *
 * <p>Display only. Hiding a tab never grants or removes access: every screen's API keeps its own
 * role and module checks, so a hidden tab is not a way around them, and showing a tab cannot
 * open a screen the role is not allowed to use.
 */
@Service
public class SidebarSettingsService {

    /** The roles whose sidebar can be configured. */
    public static final List<String> ROLES = List.of(
            "HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "NURSE", "NURSE_INCHARGE", "PHARMACIST", "OT_INCHARGE");

    /**
     * Hidden unless the admin shows them. Doctors: follow-ups (already on their Overview),
     * stock screens and the ICU bed board are front-desk / nursing work (client feedback).
     */
    static final Map<String, Set<String>> DEFAULT_HIDDEN = Map.of(
            "DOCTOR", Set.of("follow-ups", "inventory", "hospital-inventory", "icu-beds"));

    /** Tabs that can never be hidden: the landing screen, and the admin's way back to Settings. */
    static final Map<String, Set<String>> ALWAYS_SHOWN = Map.of(
            "HOSPITAL_ADMIN", Set.of("overview", "settings"),
            "DOCTOR", Set.of("overview"),
            "RECEPTIONIST", Set.of("overview"),
            "NURSE", Set.of("dashboard"),
            "NURSE_INCHARGE", Set.of("dashboard"),
            "PHARMACIST", Set.of("dashboard"),
            "OT_INCHARGE", Set.of("board"));

    private static final Pattern TAB_ID = Pattern.compile("^[a-z0-9][a-z0-9_:-]{0,39}$");

    private final HospitalSidebarSettingRepository repository;
    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;

    public SidebarSettingsService(HospitalSidebarSettingRepository repository,
            SecurityContextHelper securityHelper, AuditLogService auditLogService) {
        this.repository = repository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    /** The tabs hidden for the signed-in user's role. */
    @Transactional(readOnly = true)
    public List<String> hiddenForCurrentUser() {
        String role = normalizeRole(securityHelper.getCurrentUserRole());
        if (role == null) return List.of();
        return sorted(hidden(requireHospitalId(), role));
    }

    /** Every role's hidden tabs, its defaults and what can never be hidden — for the settings card. */
    @Transactional(readOnly = true)
    public Map<String, Object> all() {
        Long hospitalId = requireHospitalId();
        Map<String, Object> roles = new LinkedHashMap<>();
        for (String role : ROLES) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("hidden", sorted(hidden(hospitalId, role)));
            r.put("defaultHidden", sorted(DEFAULT_HIDDEN.getOrDefault(role, Set.of())));
            r.put("alwaysShown", sorted(ALWAYS_SHOWN.getOrDefault(role, Set.of())));
            r.put("customised", repository.findByHospitalIdAndRole(hospitalId, role).isPresent());
            roles.put(role, r);
        }
        return Map.of("roles", roles);
    }

    @Transactional
    public List<String> update(String roleParam, Collection<String> hiddenTabs) {
        Long hospitalId = requireHospitalId();
        String role = requireRole(roleParam);
        Set<String> clean = new TreeSet<>();
        for (String t : hiddenTabs == null ? List.<String>of() : hiddenTabs) {
            if (t == null || !TAB_ID.matcher(t).matches()) {
                throw new IllegalArgumentException("Unknown sidebar tab: " + t);
            }
            if (ALWAYS_SHOWN.getOrDefault(role, Set.of()).contains(t)) {
                throw new IllegalArgumentException("'" + t + "' always stays in the sidebar.");
            }
            clean.add(t);
        }
        if (clean.size() > 60) throw new IllegalArgumentException("Too many tabs");
        HospitalSidebarSetting row = repository.findByHospitalIdAndRole(hospitalId, role)
                .orElseGet(() -> {
                    HospitalSidebarSetting s = new HospitalSidebarSetting();
                    s.setHospitalId(hospitalId);
                    s.setRole(role);
                    return s;
                });
        row.setHiddenTabs(String.join(",", clean));
        row.setUpdatedBy(securityHelper.getCurrentUserEmail());
        repository.save(row);
        audit("SIDEBAR_UPDATED", role + " hidden: " + (clean.isEmpty() ? "none" : String.join(", ", clean)), hospitalId);
        return new ArrayList<>(clean);
    }

    /** Back to the role's defaults. */
    @Transactional
    public List<String> reset(String roleParam) {
        Long hospitalId = requireHospitalId();
        String role = requireRole(roleParam);
        repository.findByHospitalIdAndRole(hospitalId, role).ifPresent(repository::delete);
        audit("SIDEBAR_RESET", role + " sidebar restored to defaults", hospitalId);
        return sorted(DEFAULT_HIDDEN.getOrDefault(role, Set.of()));
    }

    private Set<String> hidden(Long hospitalId, String role) {
        return repository.findByHospitalIdAndRole(hospitalId, role)
                .map(r -> parse(r.getHiddenTabs()))
                .orElseGet(() -> DEFAULT_HIDDEN.getOrDefault(role, Set.of()));
    }

    private static Set<String> parse(String csv) {
        Set<String> out = new TreeSet<>();
        if (csv == null || csv.isBlank()) return out;
        for (String t : csv.split(",")) if (!t.isBlank()) out.add(t.trim());
        return out;
    }

    private static List<String> sorted(Collection<String> c) {
        return new ArrayList<>(new TreeSet<>(c));
    }

    static String normalizeRole(String role) {
        if (role == null) return null;
        String r = role.startsWith("ROLE_") ? role.substring(5) : role;
        return ROLES.contains(r) ? r : null;
    }

    private static String requireRole(String role) {
        String r = normalizeRole(role == null ? null : role.trim().toUpperCase(Locale.ROOT));
        if (r == null) throw new IllegalArgumentException("Unknown role: " + role);
        return r;
    }

    private Long requireHospitalId() {
        Long id = securityHelper.getCurrentHospitalId();
        if (id == null) throw new UnauthorizedException("Hospital ID not found in context");
        return id;
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(), hospitalId,
                    "SIDEBAR_SETTINGS", null, null);
        } catch (Exception ignored) {
            // Best effort, like every audit write.
        }
    }
}
