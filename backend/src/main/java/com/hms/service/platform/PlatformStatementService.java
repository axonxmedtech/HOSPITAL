package com.hms.service.platform;

import com.hms.dto.ConsultationStatementDTO;
import com.hms.entity.ConsultationStatement;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.ConsultationStatementRepository;
import com.hms.service.RealtimeNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlatformStatementService {

    @Autowired
    private ConsultationStatementRepository repository;

    @Autowired
    private RealtimeNotifier notifier;

    public List<ConsultationStatement> getStatementsByType(String hospitalType) {
        if (hospitalType == null || hospitalType.trim().isEmpty()) {
            throw new IllegalArgumentException("Hospital type is required");
        }
        return repository.findByHospitalTypeOrAllOrderByIdAsc(hospitalType.trim().toUpperCase());
    }

    public ConsultationStatement getStatementById(Long id, String hospitalType) {
        ConsultationStatement stmt = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));
        if (!stmt.getHospitalType().equalsIgnoreCase(hospitalType) && !"ALL".equalsIgnoreCase(stmt.getHospitalType())) {
            throw new RuntimeException("Unauthorized: Statement does not belong to " + hospitalType);
        }
        return stmt;
    }

    @Transactional
    public ConsultationStatement createStatement(String hospitalType, ConsultationStatementDTO dto) {
        if (hospitalType == null || hospitalType.trim().isEmpty()) {
            throw new IllegalArgumentException("Hospital type is required");
        }
        if (dto.getEnglishText() == null || dto.getEnglishText().trim().isEmpty()) {
            throw new IllegalArgumentException("English text is required");
        }
        if (dto.getMarathiText() == null || dto.getMarathiText().trim().isEmpty()) {
            throw new IllegalArgumentException("Marathi text is required");
        }
        if (dto.getHindiText() == null || dto.getHindiText().trim().isEmpty()) {
            throw new IllegalArgumentException("Hindi text is required");
        }

        ConsultationStatement stmt = new ConsultationStatement();
        stmt.setHospitalType(hospitalType.trim().toUpperCase());
        stmt.setCategory(dto.getCategory() != null ? dto.getCategory().trim().toUpperCase() : ConsultationStatement.CATEGORY_DOCTOR_ADVICE);
        stmt.setEnglishText(dto.getEnglishText().trim());
        stmt.setMarathiText(dto.getMarathiText().trim());
        stmt.setHindiText(dto.getHindiText().trim());
        stmt.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0);
        stmt.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

        ConsultationStatement saved = repository.save(stmt);
        notifier.refreshAllTenants();
        return saved;
    }

    @Transactional
    public ConsultationStatement updateStatement(Long id, String hospitalType, ConsultationStatementDTO dto) {
        ConsultationStatement stmt = getStatementById(id, hospitalType);

        if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
            stmt.setCategory(dto.getCategory().trim().toUpperCase());
        }
        if (dto.getEnglishText() != null && !dto.getEnglishText().trim().isEmpty()) {
            stmt.setEnglishText(dto.getEnglishText().trim());
        }
        if (dto.getMarathiText() != null && !dto.getMarathiText().trim().isEmpty()) {
            stmt.setMarathiText(dto.getMarathiText().trim());
        }
        if (dto.getHindiText() != null && !dto.getHindiText().trim().isEmpty()) {
            stmt.setHindiText(dto.getHindiText().trim());
        }
        if (dto.getDisplayOrder() != null) {
            stmt.setDisplayOrder(dto.getDisplayOrder());
        }
        if (dto.getIsActive() != null) {
            stmt.setIsActive(dto.getIsActive());
        }

        ConsultationStatement saved = repository.save(stmt);
        notifier.refreshAllTenants();
        return saved;
    }

    @Transactional
    public void deleteStatement(Long id, String hospitalType) {
        ConsultationStatement stmt = getStatementById(id, hospitalType);
        repository.delete(stmt);
        notifier.refreshAllTenants();
    }
}
