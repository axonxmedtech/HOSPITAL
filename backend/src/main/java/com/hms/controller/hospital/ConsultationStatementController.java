package com.hms.controller.hospital;

import com.hms.entity.ConsultationStatement;
import com.hms.repository.ConsultationStatementRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.util.ApiErrors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/hospital/consultation-statements", "/clinic/consultation-statements", "/pharmacy/consultation-statements"})
public class ConsultationStatementController {

    @Autowired
    private ConsultationStatementRepository repository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.HospitalRepository hospitalRepository;

    private String resolveTenantHospitalType() {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            if (hospitalId != null) {
                return hospitalRepository.findById(hospitalId)
                        .map(h -> h.getType() != null ? h.getType().name() : "HOSPITAL")
                        .orElse("HOSPITAL");
            }
        } catch (Exception ignored) {
        }
        return "HOSPITAL";
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> listStatements(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query) {
        try {
            String hospitalType = resolveTenantHospitalType();
            List<ConsultationStatement> list;

            if (query != null && !query.trim().isEmpty()) {
                list = repository.searchActive(hospitalType, query.trim());
                if (category != null && !category.trim().isEmpty()) {
                    String cat = category.trim().toUpperCase();
                    list = list.stream().filter(s -> {
                        if (("DOCTOR_ADVICE".equals(cat) || "TREATMENT_NOTES".equals(cat)) &&
                            ("DOCTOR_ADVICE".equalsIgnoreCase(s.getCategory()) || "TREATMENT_NOTES".equalsIgnoreCase(s.getCategory()))) {
                            return true;
                        }
                        return cat.equalsIgnoreCase(s.getCategory());
                    }).toList();
                }
            } else if (category != null && !category.trim().isEmpty()) {
                String cat = category.trim().toUpperCase();
                if ("DOCTOR_ADVICE".equals(cat) || "TREATMENT_NOTES".equals(cat)) {
                    list = repository.findActiveByHospitalTypeOrAll(hospitalType).stream()
                            .filter(s -> "DOCTOR_ADVICE".equalsIgnoreCase(s.getCategory()) || "TREATMENT_NOTES".equalsIgnoreCase(s.getCategory()))
                            .toList();
                } else {
                    list = repository.findActiveByHospitalTypeOrAllAndCategory(hospitalType, cat);
                }
                if (list.isEmpty()) {
                    list = repository.findActiveByHospitalTypeOrAll(hospitalType);
                }
            } else {
                list = repository.findActiveByHospitalTypeOrAll(hospitalType);
            }

            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ApiErrors.handle(e);
        }
    }
}
