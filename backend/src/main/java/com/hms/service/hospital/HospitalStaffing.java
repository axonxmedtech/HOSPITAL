package com.hms.service.hospital;

import com.hms.repository.HospitalRepository;
import com.hms.repository.HospitalSettingRepository;
import com.hms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who actually works at a facility — reception, nurses, an OT in-charge — so a workflow step
 * whose usual owner does not exist there falls to someone who does.
 *
 * <p>Every step used to assume its owner existed: without a receptionist (Solo mode) nobody
 * could schedule or start a surgery; without nurses nobody could fill the pre-op checklist and a
 * hospital admin could not record vitals; without a nurse in-charge only the admin could free a
 * cleaned bed. Callers ask this service instead of assuming.
 *
 * <p>Read on every authenticated request (OT permissions), so a snapshot is cached briefly.
 */
@Service
public class HospitalStaffing {

    /** A staffing snapshot. */
    public record Snapshot(boolean hasReception, boolean doctorActsAsReception, boolean hasNurses,
                           boolean hasNurseIncharge, boolean hasOtIncharge) {}

    private static final long TTL_MS = 30_000;
    private record Cached(Snapshot snapshot, long at) {}
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    @Autowired private HospitalRepository hospitalRepository;
    @Autowired private HospitalSettingRepository settingRepository;
    @Autowired private UserRepository userRepository;

    public Snapshot of(Long hospitalId) {
        if (hospitalId == null) return new Snapshot(true, false, true, true, false);
        Cached c = cache.get(hospitalId);
        long now = System.currentTimeMillis();
        if (c != null && now - c.at() < TTL_MS) return c.snapshot();
        Snapshot s = load(hospitalId);
        cache.put(hospitalId, new Cached(s, now));
        return s;
    }

    /** Forget a facility's snapshot after its staff or settings change. */
    public void evict(Long hospitalId) {
        if (hospitalId != null) cache.remove(hospitalId);
    }

    private Snapshot load(Long hospitalId) {
        var settings = settingRepository.findByHospital_Id(hospitalId).orElse(null);
        String mode = settings == null || settings.getReceptionMode() == null ? "HAS_RECEPTIONIST" : settings.getReceptionMode();
        boolean solo = "SOLO".equalsIgnoreCase(mode);
        boolean receptionists = userRepository.countActiveByHospitalIdAndRole(hospitalId, "RECEPTIONIST") > 0;
        List<String> modules = hospitalRepository.findById(hospitalId).map(h -> h.getModules()).orElse(List.of());
        boolean nursing = modules != null && modules.contains("NURSING");
        boolean incharge = nursing && userRepository.countActiveByHospitalIdAndRole(hospitalId, "NURSE_INCHARGE") > 0;
        boolean nurses = nursing && (incharge || userRepository.countActiveByHospitalIdAndRole(hospitalId, "NURSE") > 0);
        boolean otIncharge = settings != null && Boolean.TRUE.equals(settings.getOtInchargeEnabled())
                && userRepository.countActiveByHospitalIdAndRole(hospitalId, "OT_INCHARGE") > 0;
        // The doctor stands in for the desk only by the hospital's explicit choice (Solo / Both):
        // a hospital still hiring its receptionist keeps its intended roles.
        return new Snapshot(!solo && receptionists, solo || "BOTH".equalsIgnoreCase(mode),
                nurses, incharge, otIncharge);
    }
}
