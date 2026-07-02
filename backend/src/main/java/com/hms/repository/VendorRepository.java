package com.hms.repository;

import com.hms.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    List<Vendor> findByHospitalId(Long hospitalId);
    Optional<Vendor> findByIdAndHospitalId(Long id, Long hospitalId);
    Optional<Vendor> findByHospitalIdAndVendorCode(Long hospitalId, String vendorCode);
}
