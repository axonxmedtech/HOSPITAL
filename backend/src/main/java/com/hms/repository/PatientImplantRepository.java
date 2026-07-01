package com.hms.repository;

import com.hms.entity.PatientImplant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientImplantRepository extends JpaRepository<PatientImplant, Long> {

    /** All implants for a booking, scoped to the tenant. */
    List<PatientImplant> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);

    /** Patient lifetime implant history (all admissions). */
    List<PatientImplant> findByPatientIdAndHospitalId(Long patientId, Long hospitalId);

    /** Recall search by batch number within a tenant. */
    List<PatientImplant> findByBatchNumberAndHospitalId(String batchNumber, Long hospitalId);

    /** Tenant-scoped lookup by serial number (for BR-7 uniqueness check). */
    List<PatientImplant> findBySerialNumberAndHospitalId(String serialNumber, Long hospitalId);
}
