package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.IpdAdmission;
import com.hms.entity.IpdInsuranceClaim;
import com.hms.entity.IpdInsuranceClaimEvent;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.BillingRepository;
import com.hms.repository.IpdInsuranceClaimEventRepository;
import com.hms.repository.IpdInsuranceClaimRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Insurance / TPA claims for inpatients (cashless). The desk records each step the insurer takes;
 * the approved amount is the insurer's share, so at discharge the patient pays only the rest,
 * and the settlement is posted to the IPD bill when the money arrives.
 *
 * <pre>
 * DRAFT -> PREAUTH_SUBMITTED -> PREAUTH_APPROVED (amount) -> [ENHANCEMENT_SUBMITTED -> PREAUTH_APPROVED]
 *       -> FINAL_SUBMITTED -> SETTLED (amount, deductions)
 * QUERY: the insurer asked something (answered by resubmitting). Ends: PREAUTH_REJECTED,
 * CLAIM_REJECTED, WITHDRAWN, SETTLED.
 * </pre>
 */
@Service
public class IpdInsuranceService {

    public static final String DRAFT = "DRAFT";
    public static final String PREAUTH_SUBMITTED = "PREAUTH_SUBMITTED";
    public static final String QUERY = "QUERY";
    public static final String PREAUTH_APPROVED = "PREAUTH_APPROVED";
    public static final String PREAUTH_REJECTED = "PREAUTH_REJECTED";
    public static final String ENHANCEMENT_SUBMITTED = "ENHANCEMENT_SUBMITTED";
    public static final String FINAL_SUBMITTED = "FINAL_SUBMITTED";
    public static final String SETTLED = "SETTLED";
    public static final String CLAIM_REJECTED = "CLAIM_REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    static final Set<String> CLOSED = Set.of(PREAUTH_REJECTED, SETTLED, CLAIM_REJECTED, WITHDRAWN);
    /** While in these states the approved amount is still expected from the insurer. */
    static final Set<String> EXPECTING = Set.of(PREAUTH_APPROVED, ENHANCEMENT_SUBMITTED, FINAL_SUBMITTED, QUERY);
    static final Set<String> PAYER_TYPES = Set.of("TPA", "INSURANCE", "GOVT_SCHEME", "CORPORATE");

    private static final Map<String, Set<String>> NEXT = Map.of(
            DRAFT, Set.of(PREAUTH_SUBMITTED, WITHDRAWN),
            PREAUTH_SUBMITTED, Set.of(QUERY, PREAUTH_APPROVED, PREAUTH_REJECTED, WITHDRAWN),
            QUERY, Set.of(PREAUTH_SUBMITTED, ENHANCEMENT_SUBMITTED, FINAL_SUBMITTED, WITHDRAWN),
            PREAUTH_APPROVED, Set.of(ENHANCEMENT_SUBMITTED, FINAL_SUBMITTED, WITHDRAWN),
            ENHANCEMENT_SUBMITTED, Set.of(PREAUTH_APPROVED, QUERY, FINAL_SUBMITTED),
            FINAL_SUBMITTED, Set.of(QUERY, CLAIM_REJECTED));

    private static final Logger logger = LoggerFactory.getLogger(IpdInsuranceService.class);

    private final IpdInsuranceClaimRepository claims;
    private final IpdInsuranceClaimEventRepository events;
    private final BillingRepository billingRepository;
    private final BillingService billingService;
    private final IpdCareAccess access;
    private final FrontDeskAccess frontDesk;
    private final com.hms.service.AuditLogService auditLogService;

    public IpdInsuranceService(IpdInsuranceClaimRepository claims, IpdInsuranceClaimEventRepository events,
                               BillingRepository billingRepository, BillingService billingService, IpdCareAccess access,
                               FrontDeskAccess frontDesk, com.hms.service.AuditLogService auditLogService) {
        this.claims = claims;
        this.events = events;
        this.billingRepository = billingRepository;
        this.billingService = billingService;
        this.access = access;
        this.frontDesk = frontDesk;
        this.auditLogService = auditLogService;
    }

    /** The details a claim is opened or corrected with. */
    public record ClaimDetails(String payerType, String insurerName, String tpaName, String policyNumber,
                               String memberId, String claimNumber, BigDecimal requestedAmount, String note) {}

    /** The admission's claims (latest first), each with its timeline. Doctors may read. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forAdmission(Long ipdAdmissionId) {
        IpdAdmission a = access.admission(ipdAdmissionId);
        String role = access.role();
        if (!"DOCTOR".equals(role) && !frontDesk.isFrontDesk(a.getHospitalId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (IpdInsuranceClaim c : claims.findByHospitalIdAndIpdAdmissionIdOrderByIdDesc(a.getHospitalId(), a.getId())) {
            Map<String, Object> m = view(c);
            m.put("events", events.findByHospitalIdAndClaimIdOrderByIdAsc(a.getHospitalId(), c.getId()));
            out.add(m);
        }
        return out;
    }

    /** Open claims of the hospital, for the TPA desk. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> openClaims() {
        Long hospitalId = access.hospitalId();
        frontDesk.require(hospitalId, "see insurance claims");
        Set<String> open = new HashSet<>(NEXT.keySet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (IpdInsuranceClaim c : claims.findByHospitalIdAndStatusInOrderByUpdatedAtDescIdDesc(hospitalId, open)) {
            Map<String, Object> m = view(c);
            IpdAdmission a = access.admission(c.getIpdAdmissionId());
            m.put("ipdNumber", a.getIpdNumber());
            m.put("admissionStatus", a.getStatus());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public IpdInsuranceClaim open(Long ipdAdmissionId, ClaimDetails d) {
        IpdAdmission a = access.admission(ipdAdmissionId);
        frontDesk.require(a.getHospitalId(), "record an insurance claim");
        if ("CANCELLED".equals(a.getStatus())) throw new IllegalArgumentException("This admission was cancelled");
        boolean openExists = claims.findByHospitalIdAndIpdAdmissionIdOrderByIdDesc(a.getHospitalId(), a.getId()).stream()
                .anyMatch(c -> !CLOSED.contains(c.getStatus()));
        if (openExists) throw new IllegalArgumentException("This admission already has an open claim");
        IpdInsuranceClaim c = new IpdInsuranceClaim();
        c.setHospitalId(a.getHospitalId());
        c.setIpdAdmissionId(a.getId());
        c.setStatus(DRAFT);
        c.setCreatedBy(access.actor());
        apply(c, d);
        c.setUpdatedAt(now());
        IpdInsuranceClaim saved = claims.save(c);
        event(saved, DRAFT, saved.getRequestedAmount(), d.note() == null ? "Claim opened" : d.note());
        audit(saved, a, "INSURANCE_CLAIM_OPENED", saved.getInsurerName());
        return saved;
    }

    @Transactional
    public IpdInsuranceClaim update(String publicId, ClaimDetails d) {
        IpdInsuranceClaim c = find(publicId);
        frontDesk.require(c.getHospitalId(), "edit an insurance claim");
        if (CLOSED.contains(c.getStatus())) throw new IllegalArgumentException("This claim is closed");
        apply(c, d);
        c.setUpdatedAt(now());
        return claims.save(c);
    }

    /** Records the insurer's next step. An approval needs the approved amount. */
    @Transactional
    public IpdInsuranceClaim advance(String publicId, String status, BigDecimal amount, String claimNumber, String note) {
        IpdInsuranceClaim c = find(publicId);
        frontDesk.require(c.getHospitalId(), "update an insurance claim");
        String to = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = NEXT.getOrDefault(c.getStatus(), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalArgumentException("A claim that is " + label(c.getStatus()) + " cannot become " + label(to));
        }
        BigDecimal amt = money(amount);
        if (PREAUTH_APPROVED.equals(to)) {
            if (amt == null || amt.signum() <= 0) throw new IllegalArgumentException("Enter the amount the insurer approved");
            c.setApprovedAmount(amt);
        } else if (amt != null && (PREAUTH_SUBMITTED.equals(to) || ENHANCEMENT_SUBMITTED.equals(to) || FINAL_SUBMITTED.equals(to))) {
            c.setRequestedAmount(amt);
        }
        if (claimNumber != null && !claimNumber.isBlank()) c.setClaimNumber(trim(claimNumber, 80));
        String n = note == null || note.isBlank() ? null : trim(note, 500);
        if ((QUERY.equals(to) || PREAUTH_REJECTED.equals(to) || CLAIM_REJECTED.equals(to)) && n == null) {
            throw new IllegalArgumentException("Enter the insurer's query or reason");
        }
        c.setStatus(to);
        c.setUpdatedAt(now());
        IpdInsuranceClaim saved = claims.save(c);
        event(saved, to, amt, n);
        audit(saved, access.admission(c.getIpdAdmissionId()), "INSURANCE_CLAIM_" + to,
                label(to) + (amt == null ? "" : " " + amt) + (n == null ? "" : ": " + n));
        return saved;
    }

    /**
     * The insurer paid: posts the amount to the admission's IPD bill as an INSURANCE payment and
     * closes the claim. A deduction (what the insurer would not pay) becomes the patient's due.
     */
    @Transactional
    public IpdInsuranceClaim settle(String publicId, BigDecimal amount, BigDecimal deduction, String deductionReason, String reference) {
        IpdInsuranceClaim c = find(publicId);
        frontDesk.require(c.getHospitalId(), "record an insurance settlement");
        if (!FINAL_SUBMITTED.equals(c.getStatus()) && !PREAUTH_APPROVED.equals(c.getStatus())) {
            throw new IllegalArgumentException("Submit the final claim before recording the settlement");
        }
        BigDecimal paid = money(amount);
        if (paid == null || paid.signum() <= 0) throw new IllegalArgumentException("Enter the amount the insurer paid");
        BigDecimal cut = money(deduction);
        if (cut != null && cut.signum() > 0 && (deductionReason == null || deductionReason.isBlank())) {
            throw new IllegalArgumentException("Enter the reason for the deduction");
        }
        IpdAdmission a = access.admission(c.getIpdAdmissionId());
        Billing bill = billingRepository.findByIpdAdmissionId(a.getId()).stream()
                .filter(b -> Objects.equals(b.getHospitalId(), a.getHospitalId()))
                .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getPaymentStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This admission has no IPD bill to post the settlement to"));
        billingService.recordInsuranceSettlement(bill.getId(), paid,
                reference == null || reference.isBlank() ? c.getClaimNumber() : trim(reference, 200),
                "Insurance settlement: " + c.getInsurerName(), "DISCHARGED".equals(a.getStatus()));
        c.setSettledAmount(paid);
        c.setDeductionAmount(cut);
        c.setDeductionReason(cut == null ? null : trim(deductionReason, 255));
        c.setStatus(SETTLED);
        c.setUpdatedAt(now());
        IpdInsuranceClaim saved = claims.save(c);
        event(saved, SETTLED, paid, cut == null || cut.signum() == 0 ? null : "Deduction " + cut + ": " + c.getDeductionReason());
        audit(saved, a, "INSURANCE_CLAIM_SETTLED", "Settled " + paid + (cut == null ? "" : ", deduction " + cut));
        return saved;
    }

    /** What the insurer is still expected to pay for this admission (0 when nothing is approved or it already paid). */
    @Transactional(readOnly = true)
    public BigDecimal expectedFromInsurer(IpdAdmission a) {
        return claims.findByHospitalIdAndIpdAdmissionIdOrderByIdDesc(a.getHospitalId(), a.getId()).stream()
                .filter(c -> EXPECTING.contains(c.getStatus()) && c.getApprovedAmount() != null)
                .map(IpdInsuranceClaim::getApprovedAmount)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private IpdInsuranceClaim find(String publicId) {
        return claims.findByPublicIdAndHospitalId(publicId, access.hospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    }

    private static void apply(IpdInsuranceClaim c, ClaimDetails d) {
        String type = d.payerType() == null ? "" : d.payerType().trim().toUpperCase(Locale.ROOT);
        if (!PAYER_TYPES.contains(type)) throw new IllegalArgumentException("Select who pays: TPA, insurance company, government scheme or corporate");
        if (d.insurerName() == null || d.insurerName().isBlank()) throw new IllegalArgumentException("Enter the insurer or scheme name");
        c.setPayerType(type);
        c.setInsurerName(trim(d.insurerName(), 150));
        c.setTpaName(blankToNull(d.tpaName(), 150));
        c.setPolicyNumber(blankToNull(d.policyNumber(), 80));
        c.setMemberId(blankToNull(d.memberId(), 80));
        if (d.claimNumber() != null && !d.claimNumber().isBlank()) c.setClaimNumber(trim(d.claimNumber(), 80));
        BigDecimal req = money(d.requestedAmount());
        if (req != null) c.setRequestedAmount(req);
    }

    private static BigDecimal money(BigDecimal v) {
        if (v == null) return null;
        if (v.signum() < 0) throw new IllegalArgumentException("Amounts cannot be negative");
        if (v.compareTo(new BigDecimal("100000000")) > 0) throw new IllegalArgumentException("The amount is too large");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trim(String s, int max) {
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String blankToNull(String s, int max) {
        return s == null || s.isBlank() ? null : trim(s, max);
    }

    static String label(String status) {
        return status == null ? "" : status.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private void event(IpdInsuranceClaim c, String status, BigDecimal amount, String note) {
        IpdInsuranceClaimEvent e = new IpdInsuranceClaimEvent();
        e.setHospitalId(c.getHospitalId());
        e.setClaimId(c.getId());
        e.setStatus(status);
        e.setAmount(amount);
        e.setNote(note);
        e.setCreatedBy(access.actor());
        events.save(e);
    }

    private void audit(IpdInsuranceClaim c, IpdAdmission a, String action, String details) {
        try {
            auditLogService.logAction(action, a.getIpdNumber() + " — " + details, access.email(), c.getHospitalId(),
                    "IPD", a.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit {}", action, e);
        }
    }

    private static Map<String, Object> view(IpdInsuranceClaim c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publicId", c.getPublicId());
        m.put("ipdAdmissionId", c.getIpdAdmissionId());
        m.put("payerType", c.getPayerType());
        m.put("insurerName", c.getInsurerName());
        m.put("tpaName", c.getTpaName());
        m.put("policyNumber", c.getPolicyNumber());
        m.put("memberId", c.getMemberId());
        m.put("claimNumber", c.getClaimNumber());
        m.put("status", c.getStatus());
        m.put("requestedAmount", c.getRequestedAmount());
        m.put("approvedAmount", c.getApprovedAmount());
        m.put("settledAmount", c.getSettledAmount());
        m.put("deductionAmount", c.getDeductionAmount());
        m.put("deductionReason", c.getDeductionReason());
        m.put("nextSteps", NEXT.getOrDefault(c.getStatus(), Set.of()).stream().sorted().toList());
        m.put("canSettle", FINAL_SUBMITTED.equals(c.getStatus()) || PREAUTH_APPROVED.equals(c.getStatus()));
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }
}
