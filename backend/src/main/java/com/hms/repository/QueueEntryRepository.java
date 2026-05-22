package com.hms.repository;

import com.hms.entity.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    @Query(value = "SELECT COALESCE(MAX(q.token_number),0) FROM queue_entry q WHERE q.doctor_id = :doctorId AND DATE(q.created_at) = CURRENT_DATE", nativeQuery = true)
    Integer findMaxTokenForDoctorToday(@Param("doctorId") Long doctorId);
        @Query(value = "SELECT * FROM queue_entry q WHERE q.doctor_id = :doctorId ORDER BY q.token_number", nativeQuery = true)
        java.util.List<QueueEntry> findQueueForDoctorToday(@Param("doctorId") Long doctorId);

        @Query(value = "SELECT q.* FROM queue_entry q JOIN opd o ON q.opd_id = o.id JOIN patients p ON o.patient_id = p.id " +
            "WHERE p.hospital_id = :hospitalId ORDER BY q.token_number", nativeQuery = true)
        java.util.List<QueueEntry> findQueueForHospitalToday(@Param("hospitalId") Long hospitalId);

        // Remove queue entries associated with an OPD case
        @Modifying
        @Transactional
        void deleteByOpdId(Long opdId);
}
