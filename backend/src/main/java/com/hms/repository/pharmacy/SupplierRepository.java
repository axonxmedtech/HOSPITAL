package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Page<Supplier> findByHospitalIdAndSupplierNameContainingIgnoreCase(Long hospitalId, String name, Pageable pageable);
    Page<Supplier> findByHospitalId(Long hospitalId, Pageable pageable);
    Optional<Supplier> findByIdAndHospitalId(Long id, Long hospitalId);

    // Branch-scoped. When branchId is null (admin / single-shop) all rows for the
    // hospital are returned; otherwise show admin-shared suppliers (branchId IS NULL) + branch's own suppliers.
    @Query("SELECT s FROM Supplier s WHERE s.hospitalId = :hid " +
           "AND (:branchId IS NULL OR s.branchId IS NULL OR s.branchId = :branchId) " +
           "AND (:search IS NULL OR LOWER(s.supplierName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Supplier> findScoped(@Param("hid") Long hid, @Param("branchId") Long branchId,
                              @Param("search") String search, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.id = :id AND s.hospitalId = :hid " +
           "AND (:branchId IS NULL OR s.branchId IS NULL OR s.branchId = :branchId)")
    Optional<Supplier> findByIdScoped(@Param("id") Long id, @Param("hid") Long hid, @Param("branchId") Long branchId);

    /** Another supplier of this pharmacy (and branch) with the same name, ignoring case and spaces. */
    @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE s.hospitalId = :hid "
            + "AND ((:branchId IS NULL AND s.branchId IS NULL) OR s.branchId = :branchId) "
            + "AND LOWER(TRIM(s.supplierName)) = LOWER(TRIM(:name)) AND (:exceptId IS NULL OR s.id <> :exceptId)")
    boolean nameTaken(@Param("hid") Long hid, @Param("branchId") Long branchId, @Param("name") String name,
            @Param("exceptId") Long exceptId);

    @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE s.hospitalId = :hid "
            + "AND ((:branchId IS NULL AND s.branchId IS NULL) OR s.branchId = :branchId) "
            + "AND s.phone = :phone AND (:exceptId IS NULL OR s.id <> :exceptId)")
    boolean phoneTaken(@Param("hid") Long hid, @Param("branchId") Long branchId, @Param("phone") String phone,
            @Param("exceptId") Long exceptId);
}
