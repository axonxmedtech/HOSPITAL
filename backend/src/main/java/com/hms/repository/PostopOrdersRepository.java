package com.hms.repository;

import com.hms.entity.PostopOrders;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostopOrdersRepository extends JpaRepository<PostopOrders, Long> {
    Optional<PostopOrders> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);
}
