package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.MedicalRecord;
import com.hms.entity.NursingNote;
import com.hms.repository.DoctorRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.repository.NursingNoteRepository;
import com.hms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The doctor's rounds on one admission, as one timeline: the rounds written on the case
 * ("follow-ups") and the older "Doctors Round Notes", newest first, each with its date and time.
 * Readable by the ward as well, so the nurse sees what the doctor decided.
 */
@Service
public class IpdRoundsService {

    private static final String INITIAL_RECORD = "Initial IPD Admission Medical Record";

    private final MedicalRecordRepository medicalRecords;
    private final NursingNoteRepository notes;
    private final DoctorRepository doctors;
    private final UserRepository users;
    private final IpdCareAccess access;

    public IpdRoundsService(MedicalRecordRepository medicalRecords, NursingNoteRepository notes,
                            DoctorRepository doctors, UserRepository users, IpdCareAccess access) {
        this.medicalRecords = medicalRecords;
        this.notes = notes;
        this.doctors = doctors;
        this.users = users;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rounds(Long ipdAdmissionId) {
        IpdAdmission a = access.requireReadable(ipdAdmissionId);
        Long hospitalId = a.getHospitalId();
        List<Map<String, Object>> out = new ArrayList<>();

        List<MedicalRecord> records = medicalRecords.findByIpdAdmissionIdOrderByCreatedAtDesc(a.getId());
        Set<Long> doctorIds = new HashSet<>();
        for (MedicalRecord r : records) if (r.getDoctorId() != null) doctorIds.add(r.getDoctorId());
        Map<Long, String> doctorNames = new HashMap<>();
        if (!doctorIds.isEmpty()) {
            doctors.findByHospitalIdAndIdIn(hospitalId, doctorIds)
                    .forEach(d -> doctorNames.put(d.getId(), com.hms.util.DoctorNames.withTitle(d.getName())));
        }
        for (MedicalRecord r : records) {
            // The placeholder record made to hang prescriptions and tests on is not a round.
            if (INITIAL_RECORD.equals(r.getTreatmentNotes())) continue;
            if (!Objects.equals(r.getHospitalId(), hospitalId)) continue;
            out.add(entry(r.getCreatedAt(), doctorNames.get(r.getDoctorId()), r.getDiagnosis(), r.getTreatmentNotes(), "ROUND"));
        }
        for (NursingNote n : notes.findByAdmissionAndCategory(a.getId(), "DOCTOR")) {
            if (!Objects.equals(n.getHospitalId(), hospitalId)) continue;
            String author = n.getNurseUserId() == null ? null
                    : users.findByIdAndHospitalId(n.getNurseUserId(), hospitalId).map(u -> u.getName()).orElse(null);
            String text = n.getNoteText();
            if (n.getOrders() != null && !n.getOrders().isBlank()) text = (text == null ? "" : text + "\n") + "Orders: " + n.getOrders();
            out.add(entry(n.getRecordedAt() != null ? n.getRecordedAt() : n.getCreatedAt(), author, null, text, "NOTE"));
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> (LocalDateTime) m.get("at"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private static Map<String, Object> entry(LocalDateTime at, String doctor, String diagnosis, String notes, String kind) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", at);
        m.put("doctor", doctor);
        m.put("diagnosis", diagnosis);
        m.put("notes", notes);
        m.put("kind", kind);
        return m;
    }
}
