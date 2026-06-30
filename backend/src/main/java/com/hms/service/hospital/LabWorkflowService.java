package com.hms.service.hospital;

import com.hms.dto.LabOrderRequest;
import com.hms.dto.LabResultRequest;
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

/**
 * LabWorkflowService — Core service for the lab order lifecycle.
 *
 * Status machine:
 *   ORDERED → (lab tech collects) → SAMPLE_COLLECTED → (lab tech enters result) → COMPLETED
 *                                                                               → CANCELLED (doctor/admin only)
 *
 * All mutations:
 * - Enforce hospital scope
 * - Write AuditLog
 * - Broadcast WebSocket REFRESH_DATA event
 *
 * getOrders() enriches each LabOrder with its LabResult (if present).
 */
@Service
public class LabWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(LabWorkflowService.class);

    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private MrdService mrdService;
    @Autowired private HospitalWebSocketHandler webSocketHandler;
    @Autowired private BillingService billingService;

    // ─── Order placement ──────────────────────────────────────────────────────

    /**
     * Doctor places a new lab order. Sets status = ORDERED.
     */
    @Transactional
    public LabOrder placeOrder(LabOrderRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        LabOrder order = new LabOrder();
        order.setHospitalId(hospitalId);
        order.setTestName(req.getTestName());
        if (req.getLabTestMasterId() != null) {
            order.setLabTestMasterId(req.getLabTestMasterId());
        }
        order.setPatientId(req.getPatientId());
        order.setIpdAdmissionId(req.getIpdAdmissionId());
        order.setOpdId(req.getOpdId());
        order.setNotes(req.getNotes());
        order.setPriority(req.getPriority() != null ? req.getPriority() : "ROUTINE");
        order.setStatus("ORDERED");
        order.setOrderedByName(email);

        LabOrder saved = labOrderRepository.save(order);
        audit("LAB_ORDER_PLACED", "Lab order placed: " + req.getTestName() +
              (req.getIpdAdmissionId() != null ? " (IPD " + req.getIpdAdmissionId() + ")" : ""), hospitalId);

        // Auto-Billing
        if (saved.getIpdAdmissionId() != null) {
            try {
                java.math.BigDecimal fee = "STAT".equalsIgnoreCase(saved.getPriority())
                        ? new java.math.BigDecimal("500.00")
                        : new java.math.BigDecimal("300.00");
                billingService.postIpdCharge(saved.getIpdAdmissionId(), "Laboratory test - " + saved.getTestName(), fee);
            } catch (Exception e) {
                log.warn("Failed to auto-bill lab order: {}", e.getMessage());
            }
        }

        broadcast(hospitalId);
        return saved;
    }

    // ─── Query / enrichment ───────────────────────────────────────────────────

    /**
     * Returns orders (with embedded result if available).
     * Filters can be combined: status + ipdAdmissionId, patientId, or dashboard-level pagination.
     */
    public Map<String, Object> getOrders(String status, Long ipdAdmissionId, Long patientId, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        if (ipdAdmissionId != null && status != null) {
            List<LabOrder> orders = labOrderRepository
                    .findByHospitalIdAndIpdAdmissionIdAndStatusOrderByCreatedAtDesc(hospitalId, ipdAdmissionId, status);
            return listResult(enrichOrderList(orders), orders.size());

        } else if (ipdAdmissionId != null) {
            List<LabOrder> orders = labOrderRepository
                    .findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDesc(hospitalId, ipdAdmissionId);
            return listResult(enrichOrderList(orders), orders.size());

        } else if (patientId != null) {
            List<LabOrder> orders = labOrderRepository
                    .findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId);
            return listResult(enrichOrderList(orders), orders.size());

        } else if (status != null) {
            Page<LabOrder> page = labOrderRepository
                    .findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status, pageable);
            return pageResult(page);

        } else {
            Page<LabOrder> page = labOrderRepository
                    .findByHospitalIdOrderByCreatedAtDesc(hospitalId, pageable);
            return pageResult(page);
        }
    }

    /** Get a single order with its result. */
    public Map<String, Object> getOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        LabOrder order = labOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + publicId));
        Map<String, Object> dto = new HashMap<>();
        dto.put("order", order);
        labResultRepository.findByLabOrderId(order.getId()).ifPresent(r -> dto.put("result", r));
        return dto;
    }

    // ─── Status transitions ───────────────────────────────────────────────────

    /**
     * Lab technician marks sample as collected.
     * Transition: ORDERED → SAMPLE_COLLECTED
     */
    @Transactional
    public LabOrder collectSample(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        LabOrder order = requireOrder(publicId, hospitalId);
        if (order.getIpdAdmissionId() != null) {
            mrdService.validateAdmissionActive(order.getIpdAdmissionId());
        }

        if (!"ORDERED".equals(order.getStatus()))
            throw new IllegalStateException(
                    "Can only collect sample for ORDERED orders; current status: " + order.getStatus());

        order.setStatus("SAMPLE_COLLECTED");
        order.setSampleCollectedAt(LocalDateTime.now());
        order.setSampleCollectedByName(email);
        order.setUpdatedAt(LocalDateTime.now());

        LabOrder saved = labOrderRepository.save(order);
        audit("SAMPLE_COLLECTED", "Sample collected for: " + order.getTestName(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /**
     * Lab technician enters result.
     * Transition: SAMPLE_COLLECTED → COMPLETED
     * Creates a LabResult row; order status becomes COMPLETED.
     */
    @Transactional
    public Map<String, Object> enterResult(String publicId, LabResultRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        LabOrder order = requireOrder(publicId, hospitalId);
        if (order.getIpdAdmissionId() != null) {
            mrdService.validateAdmissionActive(order.getIpdAdmissionId());
        }

        if (labResultRepository.existsByLabOrderId(order.getId()))
            throw new IllegalStateException("Result already entered for this order");
        if (!"SAMPLE_COLLECTED".equals(order.getStatus()))
            throw new IllegalStateException(
                    "Order must be in SAMPLE_COLLECTED status before entering result; current: " + order.getStatus());

        // Create result
        LabResult result = new LabResult();
        result.setHospitalId(hospitalId);
        result.setLabOrderId(order.getId());
        result.setPatientId(order.getPatientId());
        result.setParameters(req.getParameters());
        result.setResultSummary(req.getResultSummary());
        result.setIsAbnormal(req.getIsAbnormal() != null ? req.getIsAbnormal() : false);
        result.setResultedByName(email);
        result.setResultedAt(LocalDateTime.now());
        result.setVerifiedByName(req.getVerifiedByName());
        LabResult savedResult = labResultRepository.save(result);

        // Complete the order
        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        labOrderRepository.save(order);

        audit("LAB_RESULT_ENTERED", "Result entered for: " + order.getTestName() +
              (result.getIsAbnormal() ? " [ABNORMAL]" : ""), hospitalId);
        broadcast(hospitalId);

        Map<String, Object> dto = new HashMap<>();
        dto.put("order", order);
        dto.put("result", savedResult);
        return dto;
    }

    /**
     * Doctor or admin cancels an order.
     * Not allowed once COMPLETED.
     */
    @Transactional
    public LabOrder cancelOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        LabOrder order = requireOrder(publicId, hospitalId);
        if ("COMPLETED".equals(order.getStatus()))
            throw new IllegalStateException("Cannot cancel a completed order");

        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        LabOrder saved = labOrderRepository.save(order);
        audit("LAB_ORDER_CANCELLED", "Cancelled lab order: " + order.getTestName(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private LabOrder requireOrder(String publicId, Long hospitalId) {
        return labOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + publicId));
    }

    private Map<String, Object> pageResult(Page<LabOrder> page) {
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

    private List<Map<String, Object>> enrichOrderList(List<LabOrder> orders) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (LabOrder o : orders) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("order", o);
            labResultRepository.findByLabOrderId(o.getId()).ifPresent(r -> dto.put("result", r));
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
