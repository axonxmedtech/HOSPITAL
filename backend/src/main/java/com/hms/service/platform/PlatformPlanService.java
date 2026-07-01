package com.hms.service.platform;

import com.hms.dto.AssignPlanRequest;
import com.hms.dto.CreatePlanRequest;
import com.hms.dto.SubscriptionInfoDTO;
import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlatformPlanService {

    private static final Logger logger = LoggerFactory.getLogger(PlatformPlanService.class);

    @Autowired private PlanRepository planRepository;
    @Autowired private HospitalRepository hospitalRepository;
    @Autowired private HospitalPlanSubscriptionRepository subscriptionRepository;
    @Autowired private HospitalSettingRepository hospitalSettingRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;

    // ─── Plan CRUD ─────────────────────────────────────────────────────────

    @Transactional
    public Plan createPlan(CreatePlanRequest req) {
        Plan plan = new Plan();
        plan.setName(req.getName());
        plan.setType(HospitalType.valueOf(req.getType()));
        plan.setMonthlyPrice(req.getMonthlyPrice());
        plan.setYearlyPrice(req.getYearlyPrice());
        plan.setModules(req.getModules() != null ? req.getModules() : new ArrayList<>());
        plan.setFeatures(req.getFeatures() != null ? req.getFeatures() : new ArrayList<>());
        plan.setInClinic(Boolean.TRUE.equals(req.getInClinic()));
        plan.setIsActive(true);
        Plan saved = planRepository.save(plan);
        logAction("PLAN_CREATED", "Created plan: " + saved.getName() + " [" + saved.getType() + "]");
        return saved;
    }

    @Transactional
    public Plan updatePlan(String publicId, CreatePlanRequest req) {
        Plan plan = planRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + publicId));

        plan.setName(req.getName());
        plan.setMonthlyPrice(req.getMonthlyPrice());
        plan.setYearlyPrice(req.getYearlyPrice());
        plan.setInClinic(Boolean.TRUE.equals(req.getInClinic()));

        if (req.getModules() != null) {
            plan.getModules().clear();
            plan.getModules().addAll(req.getModules());
        }
        if (req.getFeatures() != null) {
            plan.getFeatures().clear();
            plan.getFeatures().addAll(req.getFeatures());
        }

        Plan saved = planRepository.save(plan);
        propagateModulesToSubscribers(saved);
        logAction("PLAN_UPDATED", "Updated plan: " + saved.getName());
        return saved;
    }

    @Transactional
    public void deletePlan(String publicId) {
        Plan plan = planRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + publicId));

        long activeCount = subscriptionRepository.countByPlan_IdAndIsCurrentTrue(plan.getId());
        if (activeCount > 0) {
            throw new IllegalArgumentException(
                "This plan is assigned to " + activeCount + " active entities. Reassign them before deleting.");
        }

        planRepository.delete(plan);
        logAction("PLAN_DELETED", "Deleted plan: " + plan.getName());
    }

    public List<Plan> getAllPlans() {
        return planRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Plan> getPlansByType(HospitalType type) {
        return planRepository.findByTypeOrderByCreatedAtDesc(type);
    }

    // ─── Plan Assignment ────────────────────────────────────────────────────

    @Transactional
    public HospitalPlanSubscription assignPlan(String planPublicId, AssignPlanRequest req) {
        Plan plan = planRepository.findByPublicId(planPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planPublicId));

        Hospital hospital = hospitalRepository.findByPublicId(req.getHospitalPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital/Clinic/Pharmacy not found"));

        if (plan.getType() != hospital.getType()) {
            throw new IllegalArgumentException(
                "Plan type '" + plan.getType() + "' does not match entity type '" + hospital.getType() + "'");
        }

        BillingPeriod period = BillingPeriod.valueOf(req.getBillingPeriod());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = period == BillingPeriod.MONTHLY ? now.plusMonths(1) : now.plusYears(1);

        subscriptionRepository.deactivateCurrentSubscription(hospital.getId());

        HospitalPlanSubscription sub = new HospitalPlanSubscription();
        sub.setHospitalId(hospital.getId());
        sub.setPlan(plan);
        sub.setBillingPeriod(period);
        sub.setAssignedAt(now);
        sub.setExpiresAt(expiresAt);
        sub.setIsCurrent(true);
        sub.setAssignedBy(resolveCurrentUserId());
        HospitalPlanSubscription saved = subscriptionRepository.save(sub);

        applyPlanToHospital(hospital, plan);

        logAction("PLAN_ASSIGNED",
            "Assigned plan '" + plan.getName() + "' to '" + hospital.getName() + "' [" + period + "]");
        return saved;
    }

    // ─── Subscription Info for Hospital Admin ───────────────────────────────

    public SubscriptionInfoDTO getSubscriptionInfo(Long hospitalId) {
        HospitalPlanSubscription sub = subscriptionRepository
                .findByHospitalIdAndIsCurrentTrue(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found"));

        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));

        SubscriptionInfoDTO dto = new SubscriptionInfoDTO();
        dto.setPlanName(sub.getPlan().getName());
        dto.setPlanType(sub.getPlan().getType().name());
        dto.setBillingPeriod(sub.getBillingPeriod().name());
        dto.setMonthlyPrice(sub.getPlan().getMonthlyPrice());
        dto.setYearlyPrice(sub.getPlan().getYearlyPrice());
        dto.setFeatures(sub.getPlan().getFeatures());
        dto.setAssignedAt(sub.getAssignedAt());
        dto.setExpiresAt(sub.getExpiresAt());
        dto.setSubscriptionStatus(hospital.getSubscriptionStatus());
        return dto;
    }

    // ─── Internal helpers ───────────────────────────────────────────────────

    private void propagateModulesToSubscribers(Plan plan) {
        List<HospitalPlanSubscription> currentSubs =
                subscriptionRepository.findByPlan_IdAndIsCurrentTrue(plan.getId());
        for (HospitalPlanSubscription sub : currentSubs) {
            hospitalRepository.findById(sub.getHospitalId()).ifPresent(h -> applyPlanToHospital(h, plan));
        }
    }

    private void applyPlanToHospital(Hospital hospital, Plan plan) {
        ArrayList<String> modules = new ArrayList<>(plan.getModules());
        if (Boolean.TRUE.equals(plan.getInClinic())) {
            if (!modules.contains("IN_CLINIC")) modules.add("IN_CLINIC");
        } else {
            modules.remove("IN_CLINIC");
        }
        hospital.setModules(modules);
        hospital.setSubscriptionStatus("ACTIVE");
        hospitalRepository.save(hospital);

        boolean inClinicEnabled = Boolean.TRUE.equals(plan.getInClinic());
        hospitalSettingRepository.findByHospital(hospital).ifPresent(setting -> {
            setting.setInClinic(inClinicEnabled);
            hospitalSettingRepository.save(setting);
        });
    }

    private Long resolveCurrentUserId() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            return userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void logAction(String action, String details) {
        try {
            String performedBy = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);
            log.setPerformedBy(performedBy);
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Audit log failed: {}", e.getMessage(), e);
        }
    }
}
