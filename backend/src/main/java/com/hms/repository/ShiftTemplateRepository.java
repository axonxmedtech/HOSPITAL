package com.hms.repository;
import com.hms.entity.ShiftTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.Optional;
public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, Long> {
    Optional<ShiftTemplate> findByPublicId(String publicId);
    List<ShiftTemplate> findByHospitalIdOrderByStartTimeAsc(Long hospitalId);
    List<ShiftTemplate> findByHospitalIdAndIsActiveTrueOrderByStartTimeAsc(Long hospitalId);
}
