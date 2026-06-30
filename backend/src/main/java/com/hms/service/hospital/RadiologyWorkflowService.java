package com.hms.service.hospital;

import com.hms.dto.RadiologyOrderRequest;
import com.hms.dto.RadiologyResultRequest;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RadiologyWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(RadiologyWorkflowService.class);

    @Autowired private RadiologyOrderRepository radiologyOrderRepository;
    @Autowired private RadiologyResultRepository radiologyResultRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private MrdService mrdService;
    @Autowired private HospitalWebSocketHandler webSocketHandler;
    @Autowired private BillingService billingService;

    // ─── Order placement ──────────────────────────────────────────────────────

    @Transactional
    public RadiologyOrder placeOrder(RadiologyOrderRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        RadiologyOrder order = new RadiologyOrder();
        order.setHospitalId(hospitalId);
        order.setTestName(req.getTestName());
        if (req.getRadiologyTestMasterId() != null) {
            order.setRadiologyTestMasterId(req.getRadiologyTestMasterId());
        }
        order.setPatientId(req.getPatientId());
        order.setIpdAdmissionId(req.getIpdAdmissionId());
        order.setOpdId(req.getOpdId());
        order.setNotes(req.getNotes());
        order.setPriority(req.getPriority() != null ? req.getPriority() : "ROUTINE");
        order.setStatus("ORDERED");
        order.setOrderedByName(email);

        RadiologyOrder saved = radiologyOrderRepository.save(order);
        audit("RADIOLOGY_ORDER_PLACED", "Radiology order placed: " + req.getTestName() +
              (req.getIpdAdmissionId() != null ? " (IPD " + req.getIpdAdmissionId() + ")" : ""), hospitalId);

        // Auto-Billing
        if (saved.getIpdAdmissionId() != null) {
            try {
                java.math.BigDecimal fee = "STAT".equalsIgnoreCase(saved.getPriority())
                        ? new java.math.BigDecimal("1200.00")
                        : new java.math.BigDecimal("800.00");
                billingService.postIpdCharge(saved.getIpdAdmissionId(), "Radiology scan - " + saved.getTestName(), fee);
            } catch (Exception e) {
                log.warn("Failed to auto-bill radiology order: {}", e.getMessage());
            }
        }

        broadcast(hospitalId);
        return saved;
    }

    // ─── Query / enrichment ───────────────────────────────────────────────────

    public Map<String, Object> getOrders(String status, Long ipdAdmissionId, Long patientId, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        if (ipdAdmissionId != null && status != null) {
            List<RadiologyOrder> orders = radiologyOrderRepository
                    .findByHospitalIdAndIpdAdmissionIdAndStatusOrderByCreatedAtDesc(hospitalId, ipdAdmissionId, status);
            return listResult(enrichOrderList(orders), orders.size());

        } else if (ipdAdmissionId != null) {
            List<RadiologyOrder> orders = radiologyOrderRepository
                    .findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDesc(hospitalId, ipdAdmissionId);
            return listResult(enrichOrderList(orders), orders.size());

        } else if (patientId != null) {
            List<RadiologyOrder> orders = radiologyOrderRepository
                    .findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId);
            return listResult(enrichOrderList(orders), orders.size());

        } else if (status != null) {
            Page<RadiologyOrder> page = radiologyOrderRepository
                    .findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status, pageable);
            return pageResult(page);

        } else {
            Page<RadiologyOrder> page = radiologyOrderRepository
                    .findByHospitalIdOrderByCreatedAtDesc(hospitalId, pageable);
            return pageResult(page);
        }
    }

    public Map<String, Object> getOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        RadiologyOrder order = radiologyOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Radiology order not found: " + publicId));
        Map<String, Object> dto = new HashMap<>();
        dto.put("order", order);
        radiologyResultRepository.findByRadiologyOrderId(order.getId()).ifPresent(r -> dto.put("result", r));
        return dto;
    }

    // ─── Status transitions ───────────────────────────────────────────────────

    /**
     * Radiology technician marks study as conducted / scan completed.
     * Transition: ORDERED → STUDY_CONDUCTED
     */
    @Transactional
    public RadiologyOrder conductStudy(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        RadiologyOrder order = requireOrder(publicId, hospitalId);
        if (order.getIpdAdmissionId() != null) {
            mrdService.validateAdmissionActive(order.getIpdAdmissionId());
        }
        if (!"ORDERED".equals(order.getStatus()))
            throw new IllegalStateException(
                    "Can only conduct study for ORDERED orders; current status: " + order.getStatus());

        order.setStatus("STUDY_CONDUCTED");
        order.setStudyConductedAt(LocalDateTime.now());
        order.setStudyConductedByName(email);
        order.setUpdatedAt(LocalDateTime.now());

        RadiologyOrder saved = radiologyOrderRepository.save(order);
        audit("RADIOLOGY_STUDY_CONDUCTED", "Radiology study conducted for: " + order.getTestName(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /**
     * Radiology technician enters findings and impression.
     * Transition: STUDY_CONDUCTED → COMPLETED
     */
    @Transactional
    public Map<String, Object> enterResult(String publicId, RadiologyResultRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        RadiologyOrder order = requireOrder(publicId, hospitalId);
        if (order.getIpdAdmissionId() != null) {
            mrdService.validateAdmissionActive(order.getIpdAdmissionId());
        }

        if (radiologyResultRepository.existsByRadiologyOrderId(order.getId()))
            throw new IllegalStateException("Result already entered for this order");
        if (!"STUDY_CONDUCTED".equals(order.getStatus()))
            throw new IllegalStateException(
                    "Order must be in STUDY_CONDUCTED status before entering result; current: " + order.getStatus());

        // Create result
        RadiologyResult result = new RadiologyResult();
        result.setHospitalId(hospitalId);
        result.setRadiologyOrderId(order.getId());
        result.setPatientId(order.getPatientId());
        result.setFindings(req.getFindings());
        result.setImpression(req.getImpression());
        result.setIsAbnormal(req.getIsAbnormal() != null ? req.getIsAbnormal() : false);
        result.setResultFileUrl(req.getResultFileUrl());
        result.setResultedByName(email);
        result.setResultedAt(LocalDateTime.now());
        result.setVerifiedByName(req.getVerifiedByName());
        RadiologyResult savedResult = radiologyResultRepository.save(result);

        // Complete the order
        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        radiologyOrderRepository.save(order);

        audit("RADIOLOGY_RESULT_ENTERED", "Result entered for: " + order.getTestName() +
              (result.getIsAbnormal() ? " [ABNORMAL]" : ""), hospitalId);
        broadcast(hospitalId);

        Map<String, Object> dto = new HashMap<>();
        dto.put("order", order);
        dto.put("result", savedResult);
        return dto;
    }

    @Transactional
    public RadiologyOrder cancelOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        RadiologyOrder order = requireOrder(publicId, hospitalId);
        if ("COMPLETED".equals(order.getStatus()))
            throw new IllegalStateException("Cannot cancel a completed order");

        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        RadiologyOrder saved = radiologyOrderRepository.save(order);
        audit("RADIOLOGY_ORDER_CANCELLED", "Cancelled radiology order: " + order.getTestName(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private RadiologyOrder requireOrder(String publicId, Long hospitalId) {
        return radiologyOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Radiology order not found: " + publicId));
    }

    private Map<String, Object> pageResult(Page<RadiologyOrder> page) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", enrichOrderList(page.getContent()));
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        result.put("number", page.getNumber());
        return result;
    }

    private Map<String, Object> listResult(List<Map<String, Object>> enriched, long total) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", enriched);
        result.put("totalElements", total);
        return result;
    }

    private List<Map<String, Object>> enrichOrderList(List<RadiologyOrder> orders) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (RadiologyOrder o : orders) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("order", o);
            radiologyResultRepository.findByRadiologyOrderId(o.getId()).ifPresent(r -> dto.put("result", r));
            enriched.add(dto);
        }
        return enriched;
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            String actor = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details);
            entry.setPerformedBy(actor);
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }

    private void broadcast(Long hospitalId) {
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed: {}", e.getMessage());
        }
    }
}
