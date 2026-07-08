package com.hms.repository;

import com.hms.entity.SurgeryForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurgeryFormRepository extends JpaRepository<SurgeryForm, Long> {
    Optional<SurgeryForm> findByIpdAdmissionIdAndFormType(Long ipdAdmissionId, String formType);
    List<SurgeryForm> findByIpdAdmissionId(Long ipdAdmissionId);
}
