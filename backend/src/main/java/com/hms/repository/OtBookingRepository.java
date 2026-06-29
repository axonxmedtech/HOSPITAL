package com.hms.repository;

import com.hms.entity.OtBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OtBookingRepository extends JpaRepository<OtBooking, Long> {
    List<OtBooking> findByIpdAdmissionIdAndHospitalIdOrderByScheduledDateTimeDesc(Long ipdAdmissionId, Long hospitalId);
    List<OtBooking> findByHospitalIdOrderByScheduledDateTimeDesc(Long hospitalId);
}
