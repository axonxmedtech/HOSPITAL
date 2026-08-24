package com.hms.repository;

import com.hms.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    List<MedicalRecord> findByPatientId(Long patientId);
    List<MedicalRecord> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    Optional<MedicalRecord> findByAppointmentId(Long appointmentId);

    Optional<MedicalRecord> findByOpdId(Long opdId);

    List<MedicalRecord> findTop5ByPatientIdOrderByCreatedAtDesc(Long patientId);

    Optional<MedicalRecord> findTopByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<MedicalRecord> findByIpdAdmissionIdOrderByCreatedAtDesc(Long ipdAdmissionId);
    List<MedicalRecord> findByIpdAdmissionIdOrderByCreatedAtAsc(Long ipdAdmissionId);
    List<MedicalRecord> findByIpdAdmissionIdIn(List<Long> ipdAdmissionIds);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM MedicalRecord r WHERE r.id = :id AND r.hospitalId = :hospitalId")
    java.util.Optional<MedicalRecord> findByIdAndHospitalId(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId);


    List<MedicalRecord> findByHospitalIdAndFollowUpDate(Long hospitalId, java.time.LocalDate followUpDate);
    List<MedicalRecord> findByHospitalIdAndDoctorIdAndFollowUpDate(Long hospitalId, Long doctorId, java.time.LocalDate followUpDate);
}
