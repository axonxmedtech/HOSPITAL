package com.hms.controller.platform;

import com.hms.dto.ConsultationStatementDTO;
import com.hms.entity.ConsultationStatement;
import com.hms.service.platform.PlatformStatementService;
import com.hms.util.ApiErrors;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform/statements")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformStatementController {

    @Autowired
    private PlatformStatementService statementService;

    @GetMapping
    public ResponseEntity<?> getStatements(@RequestParam(name = "hospitalType", defaultValue = "HOSPITAL") String hospitalType) {
        try {
            List<ConsultationStatement> list = statementService.getStatementsByType(hospitalType);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ApiErrors.handle(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> createStatement(
            @RequestParam(name = "hospitalType", defaultValue = "HOSPITAL") String hospitalType,
            @Valid @RequestBody ConsultationStatementDTO dto) {
        try {
            ConsultationStatement saved = statementService.createStatement(hospitalType, dto);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ApiErrors.handle(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStatement(
            @PathVariable Long id,
            @RequestParam(name = "hospitalType", defaultValue = "HOSPITAL") String hospitalType,
            @Valid @RequestBody ConsultationStatementDTO dto) {
        try {
            ConsultationStatement updated = statementService.updateStatement(id, hospitalType, dto);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ApiErrors.handle(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStatement(
            @PathVariable Long id,
            @RequestParam(name = "hospitalType", defaultValue = "HOSPITAL") String hospitalType) {
        try {
            statementService.deleteStatement(id, hospitalType);
            return ResponseEntity.ok(Map.of("message", "Statement deleted successfully"));
        } catch (Exception e) {
            return ApiErrors.handle(e);
        }
    }
}
