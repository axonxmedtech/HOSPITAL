package com.hms.controller.hospital;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalSetting;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.BedRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.HospitalSettingRepository;
import com.hms.repository.IcuWardRepository;
import com.hms.repository.MedicineRepository;
import com.hms.repository.OtRoomRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a new hospital still has to set up before its staff can work: a fresh hospital opened on
 * an empty dashboard, and the first receptionist found an empty doctor list with no hint why.
 * Each step is only listed when the hospital has the module it belongs to.
 */
@RestController
@RequestMapping({"/hospital/setup-status", "/clinic/setup-status"})
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class SetupStatusController {

    private final SecurityContextHelper securityHelper;
    private final HospitalRepository hospitalRepository;
    private final HospitalSettingRepository settingRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final IcuWardRepository icuWardRepository;
    private final OtRoomRepository otRoomRepository;
    private final MedicineRepository medicineRepository;

    public SetupStatusController(SecurityContextHelper securityHelper, HospitalRepository hospitalRepository,
                                 HospitalSettingRepository settingRepository, DoctorRepository doctorRepository,
                                 UserRepository userRepository, WardRepository wardRepository, BedRepository bedRepository,
                                 IcuWardRepository icuWardRepository, OtRoomRepository otRoomRepository,
                                 MedicineRepository medicineRepository) {
        this.securityHelper = securityHelper;
        this.hospitalRepository = hospitalRepository;
        this.settingRepository = settingRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.wardRepository = wardRepository;
        this.bedRepository = bedRepository;
        this.icuWardRepository = icuWardRepository;
        this.otRoomRepository = otRoomRepository;
        this.medicineRepository = medicineRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Hospital hospital = hospitalId == null ? null : hospitalRepository.findById(hospitalId).orElse(null);
        if (hospital == null) throw new ResourceNotFoundException("Hospital not found");
        Set<String> modules = hospital.getModules() == null ? Set.of() : Set.copyOf(hospital.getModules());

        List<Map<String, Object>> steps = new ArrayList<>();
        boolean clinical = modules.contains("OPD") || modules.contains("IPD") || modules.contains("APPOINTMENTS");
        if (clinical) {
            steps.add(doctorsStep(hospitalId));
            steps.add(receptionStep(hospitalId));
            if (modules.contains("BILLING")) steps.add(feesStep(hospital));
        }
        if (modules.contains("IPD")) {
            steps.add(wardsStep(hospitalId));
            if (modules.contains("ICU")) steps.add(icuStep(hospitalId));
            if (modules.contains("NURSING")) steps.add(nursesStep(hospitalId));
        }
        if (modules.contains("OT")) steps.add(otStep(hospitalId));
        if (modules.contains("PHARMACY")) steps.add(medicinesStep(hospitalId));
        return ResponseEntity.ok(summary(steps));
    }

    private Map<String, Object> doctorsStep(Long hospitalId) {
        return step("doctors", "Add your doctors",
                "Patients can only be registered for OPD, appointments or admission under a doctor.",
                !doctorRepository.findByHospitalIdAndIsActiveTrue(hospitalId).isEmpty(), "doctors");
    }

    private Map<String, Object> receptionStep(Long hospitalId) {
        HospitalSetting settings = settingRepository.findByHospital_Id(hospitalId).orElse(null);
        String mode = settings == null ? null : settings.getReceptionMode();
        boolean doctorsRunDesk = "SOLO".equalsIgnoreCase(mode) || "BOTH".equalsIgnoreCase(mode);
        return step("reception", "Add a receptionist, or let doctors run the desk",
                "Add a receptionist login, or switch Reception Mode to Solo / Both in Settings.",
                doctorsRunDesk || userRepository.countActiveByHospitalIdAndRole(hospitalId, "RECEPTIONIST") > 0,
                "receptionists");
    }

    private static Map<String, Object> feesStep(Hospital hospital) {
        return step("fees", "Set your consultation and case-paper fees",
                "Until they are set, OPD bills use the default ₹500 + ₹100.",
                hospital.getConsultationFee() != null && hospital.getCasePaperFee() != null, "fees");
    }

    private Map<String, Object> wardsStep(Long hospitalId) {
        return step("wards", "Add wards and beds", "A patient can only be admitted into a free bed.",
                !wardRepository.findByHospitalId(hospitalId).isEmpty() && !bedRepository.findByHospitalId(hospitalId).isEmpty(),
                "wards");
    }

    private Map<String, Object> icuStep(Long hospitalId) {
        return step("icu", "Mark your ICU wards",
                "ICU charting and the ICU board only work for wards marked as ICU.",
                !icuWardRepository.findByHospitalId(hospitalId).isEmpty(), "icu-wards");
    }

    private Map<String, Object> nursesStep(Long hospitalId) {
        boolean any = userRepository.countActiveByHospitalIdAndRole(hospitalId, "NURSE") > 0
                || userRepository.countActiveByHospitalIdAndRole(hospitalId, "NURSE_INCHARGE") > 0;
        return step("nurses", "Add nurses to your wards",
                "Optional: without nurses, doctors and the admin record vitals and medicines themselves.",
                any, "nurses", true);
    }

    private Map<String, Object> otStep(Long hospitalId) {
        return step("ot", "Add an operation theatre", "Surgeries can only be scheduled into a theatre.",
                !otRoomRepository.findByHospitalIdAndIsActiveTrueOrderByNameAsc(hospitalId).isEmpty(), "ot-theatres");
    }

    private Map<String, Object> medicinesStep(Long hospitalId) {
        return step("medicines", "Add medicines and stock",
                "The pharmacy can only dispense medicines that are in stock.",
                !medicineRepository.findByHospitalId(hospitalId).isEmpty(), "pharmacy");
    }

    private static Map<String, Object> summary(List<Map<String, Object>> steps) {
        long done = steps.stream().filter(s -> Boolean.TRUE.equals(s.get("done"))).count();
        boolean requiredDone = steps.stream()
                .allMatch(s -> Boolean.TRUE.equals(s.get("done")) || Boolean.TRUE.equals(s.get("optional")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("steps", steps);
        out.put("done", done);
        out.put("total", steps.size());
        out.put("complete", requiredDone);
        return out;
    }

    private static Map<String, Object> step(String key, String title, String hint, boolean done, String tab) {
        return step(key, title, hint, done, tab, false);
    }

    /** An optional step is shown until done, but the hospital can work without it. */
    private static Map<String, Object> step(String key, String title, String hint, boolean done, String tab,
                                            boolean optional) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("optional", optional);
        m.put("key", key);
        m.put("title", title);
        m.put("hint", hint);
        m.put("done", done);
        m.put("tab", tab);
        return m;
    }
}
