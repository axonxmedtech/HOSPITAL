package com.hms.service.hospital;
import com.hms.util.LogSanitizer;

import com.hms.entity.Billing;
import com.hms.entity.Hospital;
import com.hms.entity.Appointment;
import com.hms.repository.BillingRepository;
import com.hms.repository.HospitalRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class BillingService {

    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String BILL_NOT_FOUND = "Bill not found";
    private static final String KIND_PAYMENT = "PAYMENT";
    private static final String BILL_LABEL = "Bill ";
    private static final String BILLING_MODULE = "BILLING";


    private static final Logger logger = LoggerFactory.getLogger(BillingService.class);

    @Autowired private TenantCustomIdAllocator customIdAllocator;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;

    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;

    // Deliberately no OpdRepository: billing does not reach into the clinical record. The OPD's
    // status is owned by the doctor's consultation; this service only moves money.

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;


    /**
     * Auto-generate a bill for a completed appointment
     */
    @Transactional
    public void autoGenerateOpdBill(Appointment appointment) {
        if (billingRepository.existsByAppointmentId(appointment.getId())) {
            logger.warn("Skipping bill auto-generation: Bill already exists for appointment {}", appointment.getId());
            return;
        }

        Hospital hospital = hospitalRepository.findById(appointment.getHospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));

        // Check module access
        if (hospital.getModules() == null || !hospital.getModules().contains(BILLING_MODULE)) {
            logger.warn("Skipping bill generation for appointment {}. BILLING module disabled.", appointment.getId());
            return;
        }

        // A visit costs the same however the patient arrived: charge the case-paper fee alongside
        // the consultation fee, exactly as the walk-in OPD bill does (createOpdBill). Previously
        // an appointment patient was billed the consultation fee only, silently skipping the
        // hospital's case-paper fee — the same encounter priced two different ways.
        BigDecimal caseFee = hospital.getCasePaperFee();
        if (caseFee == null || caseFee.compareTo(BigDecimal.ZERO) < 0) {
            caseFee = BigDecimal.ZERO;
        }
        // A free consultation still owes the case-paper fee, as it does on the walk-in bill.
        // Previously a 0 consultation fee skipped the bill entirely, dropping the case-paper fee.
        BigDecimal fee = hospital.getConsultationFee();
        if (fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            fee = BigDecimal.ZERO;
        }
        if (fee.add(caseFee).compareTo(BigDecimal.ZERO) > 0) {
            final BigDecimal casePaperFee = caseFee;
            BigDecimal total = fee.add(casePaperFee);

            Billing bill = new Billing();
            bill.setHospitalId(hospital.getId());
            bill.setPatientId(appointment.getPatientId());
            bill.setDoctorId(appointment.getDoctorId());
            bill.setAppointmentId(appointment.getId());
            bill.setAmount(total);
            bill.setPaymentStatus("PENDING"); // nothing collected yet
            bill.setDescription(casePaperFee.compareTo(BigDecimal.ZERO) <= 0
                    ? "Consultation Fee - Auto Generated"
                    : fee.compareTo(BigDecimal.ZERO) > 0
                            ? "OPD - Case Paper + Consultation"
                            : "OPD - Case Paper");

            // Attempt to resolve related opdId from MedicalRecord
            try {
                java.util.Optional<com.hms.entity.MedicalRecord> recordOpt = medicalRecordRepository.findByAppointmentId(appointment.getId());
                recordOpt.ifPresent(r -> bill.setOpdId(r.getOpdId()));
            } catch (Exception e) {
                logger.warn("Could not resolve related opdId for appointment {}", appointment.getId(), e);
            }

            Billing saved = saveBillWithCollisionRetry(bill);

            // Itemised breakdown, so the receipt matches the walk-in OPD bill.
            try {
                if (casePaperFee.compareTo(BigDecimal.ZERO) > 0) {
                    com.hms.entity.BillingItem caseItem = new com.hms.entity.BillingItem();
                    caseItem.setBillingId(saved.getId());
                    caseItem.setHospitalId(hospital.getId());
                    caseItem.setDescription("Case Paper Fee");
                    caseItem.setAmount(casePaperFee);
                    billingItemRepository.save(caseItem);
                }

                if (fee.compareTo(BigDecimal.ZERO) > 0) {
                    com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
                    item.setBillingId(saved.getId());
                    item.setHospitalId(hospital.getId());
                    item.setDescription("Consultation Fee");
                    item.setAmount(fee);
                    billingItemRepository.save(item);
                }
            } catch (Exception e) {
                logger.warn("Failed to create billing items for auto-bill {}", saved.getId(), e);
            }


            logger.info("Auto-generated bill for appointment: {} with amount: {}", appointment.getId(), total);
        } else {
            logger.warn("Skipped bill generation for appointment {}. Fee is null or zero. Hospital Fee: {}",
                    appointment.getId(), fee);
        }
    }

    /**
     * Get all bills for current hospital with optional search
     */
    public Page<Billing> getAllBills(String search, String status, Pageable pageable) {
        return getAllBills(search, status, null, pageable);
    }

    /** Bill categories offered by the list filter: what a bill is for. */
    public static final java.util.List<String> CATEGORIES = java.util.List.of("OPD", "IPD");

    /** The bills list, optionally narrowed to one category (OPD / IPD). */
    public Page<Billing> getAllBills(String search, String status, String category, Pageable pageable) {
        return getAllBills(search, status, category, null, null, pageable);
    }

    /**
     * The bills list with all its filters: category (OPD / IPD), payment status, search and a
     * bill-date window (inclusive dates). Read-only.
     */
    public Page<Billing> getAllBills(String search, String status, String category,
                                     java.time.LocalDate from, java.time.LocalDate to, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        java.util.List<String> statuses = statusesForFilter(status);
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The 'to' date is before the 'from' date");
        }
        String c = normaliseCategory(category);
        if (c == null && from == null && to == null) {
            return unfilteredByCategoryOrDate(hospitalId, search, statuses, pageable);
        }
        boolean anyStatus = statuses == null;
        return billingRepository.findFiltered(hospitalId, c == null, c == null ? "" : c,
                anyStatus, anyStatus ? java.util.List.of("") : statuses,
                search == null ? "" : search.trim(),
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), pageable);
    }

    /** OPD / IPD, or null for "all categories". */
    private static String normaliseCategory(String category) {
        if (category == null || category.isBlank()) return null;
        String c = category.trim().toUpperCase(java.util.Locale.ROOT);
        if (!CATEGORIES.contains(c)) {
            throw new IllegalArgumentException("Unknown bill category: " + category);
        }
        return c;
    }

    /** The list with only search and status. Both are applied in the query, never per page. */
    private Page<Billing> unfilteredByCategoryOrDate(Long hospitalId, String search,
                                                     java.util.List<String> statuses, Pageable pageable) {
        boolean hasSearch = search != null && !search.isEmpty();
        if (hasSearch && statuses != null) {
            return billingRepository.searchBillingsByStatus(hospitalId, search, statuses, pageable);
        }
        if (hasSearch) {
            return billingRepository.searchBillings(hospitalId, search, pageable);
        }
        if (statuses != null) {
            return billingRepository.findByHospitalIdAndPaymentStatusIn(hospitalId, statuses, pageable);
        }
        return billingRepository.findByHospitalId(hospitalId, pageable);
    }

    /**
     * The stored statuses a list filter covers. Discharge finalises an IPD bill as CLOSED — only
     * possible once it is fully settled — but the list only offers Pending / Paid / Partial, so
     * every discharged patient's bill vanished from Billing. "PAID" therefore means settled.
     * Returns null for "no filter".
     */
    static java.util.List<String> statusesForFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase(java.util.Locale.ROOT);
        return "PAID".equals(s) ? java.util.List.of("PAID", STATUS_CLOSED) : java.util.List.of(s);
    }

    /**
     * Update payment status.
     *
     * <p>Transactional and taken under a row lock on the bill, because marking a bill PAID also
     * back-fills the outstanding remainder into the payment ledger. Reading what has been
     * collected and writing the remainder has to be one serialised step: unlocked, two "Mark as
     * Paid" clicks -- a double-click, or two staff on the same bill -- both read the same
     * collected figure, both computed the same remainder, and both inserted it. The bill then
     * showed twice its own total as collected.
     */
    @Transactional
    public Billing updateStatus(Long id, String status, String paymentMethod, String paymentReference) {
        if (!"PENDING".equalsIgnoreCase(status) && 
            !"PARTIAL".equalsIgnoreCase(status) && 
            !"PAID".equalsIgnoreCase(status) && 
            !STATUS_CLOSED.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Invalid billing status value: " + status);
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateBillingAccess(hospitalId);

        Billing bill = billingRepository.findByIdAndHospitalIdForUpdate(id, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException(BILL_NOT_FOUND));

        // CLOSED is terminal: updateBillItems and recalculateTotal both refuse to change it. This
        // was the one way back, and marking a written-off bill PAID also writes a ledger row for
        // its whole balance, inventing money that was never collected.
        if (STATUS_CLOSED.equalsIgnoreCase(bill.getPaymentStatus()) && !STATUS_CLOSED.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("A closed bill cannot be reopened");
        }
        if (STATUS_CANCELLED.equalsIgnoreCase(bill.getPaymentStatus())) {
            throw new IllegalArgumentException("A cancelled bill cannot be changed");
        }

        bill.setPaymentStatus(status);
        if ("PAID".equalsIgnoreCase(status)) {
            try {
                String userEmail = securityHelper.getCurrentUserEmail();
                String userRole = securityHelper.getCurrentUserRole();
                bill.setMarkedPaidBy(userRole + " (" + userEmail + ")");
            } catch (Exception e) {
                logger.warn("Failed to set markedPaidBy during billing payment status update: {}", e.getMessage());
            }
        }
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            bill.setPaymentMethod(paymentMethod);
        }
        if (paymentReference != null && !paymentReference.isEmpty()) {
            bill.setPaymentReference(paymentReference);
        }

        Billing saved = saveBillWithCollisionRetry(bill);

        // Create audit log
        try {
            auditLogService.logAction(
                    "BILLING_STATUS_CHANGED",
                    BILL_LABEL + saved.getCustomId() + " status updated to " + status + ".",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    BILLING_MODULE,
                    saved.getPublicId(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for billing status update", e);
        }

        // If the bill is marked as PAID, ensure that corresponding BillingPayment is recorded so both sections are synchronized
        if ("PAID".equalsIgnoreCase(status)) {
            try {
                // Back-fill what is still owed on the NET payable (after any discount).
                BillMoney money = moneyFor(saved);
                if (money.balance().signum() > 0) {
                    com.hms.entity.BillingPayment payment = new com.hms.entity.BillingPayment();
                    payment.setBillingId(saved.getId());
                    payment.setHospitalId(saved.getHospitalId());
                    payment.setAmount(money.balance());
                    payment.setMode(paymentMethod != null && !paymentMethod.isEmpty() ? paymentMethod : "CASH");
                    payment.setReference(paymentReference);
                    payment.setKind(KIND_PAYMENT);
                    payment.setRecordedBy(currentActor());
                    billingPaymentRepository.save(payment);
                }
            } catch (Exception e) {
                logger.warn("Failed to auto-create BillingPayment for PAID status update", e);
            }
        }

        // NOTE: taking payment deliberately does NOT touch the OPD's status.
        //
        // Money and medicine are separate concerns. This used to set the linked OPD to COMPLETED
        // whenever a bill was marked PAID, which meant the cashier closed the patient's clinical
        // encounter. That was wrong in three ways:
        //   1. Paying at the counter is not evidence a doctor saw the patient. With "bill before
        //      OPD" it would have completed the case before the consultation even happened.
        //   2. A patient who owes money is not clinically unfinished — an unpaid balance is a
        //      billing state (PENDING / PARTIAL), not a reason to hold the encounter open.
        //   3. It stranded cases: with "bill before OPD" and no extras, the bill is already PAID
        //      at entry and never re-marked, so this never fired and the OPD sat unfinished.
        //
        // The OPD is now completed by the doctor finishing the consultation (DoctorService), and
        // what is owed lives entirely on the bill.

        try {
            webSocketHandler.broadcast(saved.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from updateStatus", e);
        }

        return saved;
    }

    private void validateBillingAccess(Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));
        if (hospital.getModules() == null || !hospital.getModules().contains(BILLING_MODULE)) {
            throw new IllegalArgumentException("BILLING module is disabled for your hospital.");
        }
    }

    /**
     * "Payment first" flow: create the OPD bill (case paper + consultation) and mark it PAID
     * right at OPD entry, recording the payment — WITHOUT completing the OPD (the consultation
     * still has to happen). Deliberately does not reuse updateStatus(), which would flip the OPD
     * to COMPLETED on PAID. Returns null when the BILLING module is off.
     */
    @Transactional
    public com.hms.entity.Billing createPaidOpdBillAtEntry(Long opdId, Long patientId, Long doctorId,
                                                           String paymentMethod, String paymentReference) {
        Billing bill = createOpdBill(opdId, patientId, doctorId);
        if (bill == null) return null; // BILLING module disabled

        // Same vocabulary as the mark-as-paid flow: CASH or UPI (UPI carries a UTR reference).
        String method = "UPI".equalsIgnoreCase(paymentMethod) ? "UPI" : "CASH";
        String reference = "UPI".equals(method) ? paymentReference : null;

        bill.setPaymentStatus("PAID");
        bill.setPaymentMethod(method);
        bill.setPaymentReference(reference);
        try {
            bill.setMarkedPaidBy(securityHelper.getCurrentUserRole() + " (" + securityHelper.getCurrentUserEmail() + ")");
        } catch (Exception ignored) { /* best-effort attribution only */ }
        Billing saved = saveBillWithCollisionRetry(bill);

        // The ledger row is the RECORD OF THE MONEY, not a nice-to-have mirror — everything
        // downstream derives from it: the bill's status (recalculateTotal), the "Received"/
        // "Balance" lines on the printed receipt, and the payments view.
        //
        // So this write must NOT be best-effort. If it were swallowed, the bill would be left
        // claiming PAID with an empty ledger, and the next recalculateTotal would see paid=0 and
        // silently flip it back to PENDING — the patient's payment would vanish and the receipt
        // would print "Received 0". Let a failure roll back the whole @Transactional method
        // instead: no bill is better than a bill that lies about money. The caller (OpdService)
        // already treats pay-first billing as best-effort, so the OPD case is still created and
        // the fee is simply collected at the counter instead.
        com.hms.entity.BillingPayment payment = new com.hms.entity.BillingPayment();
        payment.setBillingId(saved.getId());
        payment.setHospitalId(saved.getHospitalId());
        payment.setAmount(saved.getAmount() != null ? saved.getAmount() : java.math.BigDecimal.ZERO);
        payment.setMode(method);
        payment.setReference(reference);
        billingPaymentRepository.save(payment);

        return saved;
    }

    /**
     * Create a combined OPD bill (case paper fee + consultation fee) for an OPD case
     * @param opdId OPD id
     * @param patientId Patient id
     * @param doctorId Doctor id
     */
    @Transactional
    public com.hms.entity.Billing createOpdBill(Long opdId, Long patientId, Long doctorId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));

        // Check module access
        if (hospital.getModules() == null || !hospital.getModules().contains(BILLING_MODULE)) {
            logger.warn("Skipping OPD bill generation for OPD {}. BILLING module disabled.", opdId);
            return null;
        }

        // Fees: prefer hospital-configured values, fallback to previous defaults
        java.math.BigDecimal caseFee = hospital.getCasePaperFee();
        if (caseFee == null || caseFee.compareTo(java.math.BigDecimal.ZERO) < 0) {
            caseFee = new java.math.BigDecimal("100.00");
        }

        java.math.BigDecimal consultFee = hospital.getConsultationFee();
        // Only an unset fee falls back to the default. An explicit 0 is a free consultation; it
        // used to be replaced by 500, charging patients a fee the hospital had switched off.
        if (consultFee == null || consultFee.compareTo(java.math.BigDecimal.ZERO) < 0) {
            consultFee = new java.math.BigDecimal("500.00");
        }

        java.math.BigDecimal total = caseFee.add(consultFee);

        Billing bill = new Billing();
        bill.setHospitalId(hospitalId);
        bill.setPatientId(patientId);
        bill.setDoctorId(doctorId != null ? doctorId : 0L);
        bill.setOpdId(opdId);
        bill.setAmount(total);
        bill.setDescription("OPD - Case Paper + Consultation");
        bill.setPaymentStatus("PENDING");
        bill.setAppointmentId(null);

        Billing saved = saveBillWithCollisionRetry(bill);

        // Create billing items for breakdown
        com.hms.entity.BillingItem item1 = new com.hms.entity.BillingItem();
        item1.setBillingId(saved.getId());
        item1.setHospitalId(hospitalId);
        item1.setDescription("Case Paper Fee");
        item1.setAmount(caseFee);
        billingItemRepository.save(item1);

        com.hms.entity.BillingItem item2 = new com.hms.entity.BillingItem();
        item2.setBillingId(saved.getId());
        item2.setHospitalId(hospitalId);
        item2.setDescription("Consultation Fee");
        item2.setAmount(consultFee);
        billingItemRepository.save(item2);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after OPD bill creation", e);
        }

        logger.info("OPD bill generated for OPD {} patient {} with amount {}", opdId, patientId, total);
        return saved;
    }

    /**
     * What a legacy IPD bill (no lines, stored amount 0) is worth: its ward's bed price, the same
     * figure the IPD bill screen and mark-as-PAID already use. Zero for anything else. The
     * admission and ward are only read when they belong to the bill's own hospital.
     */
    public BigDecimal legacyIpdBedPrice(Billing bill) {
        if (!"IPD".equalsIgnoreCase(bill.getBillingType()) || bill.getIpdAdmissionId() == null) {
            return BigDecimal.ZERO;
        }
        com.hms.entity.IpdAdmission ipd = ipdAdmissionRepository.findById(bill.getIpdAdmissionId())
                .filter(a -> java.util.Objects.equals(a.getHospitalId(), bill.getHospitalId()))
                .orElse(null);
        if (ipd == null || ipd.getWardId() == null) return BigDecimal.ZERO;
        com.hms.entity.Ward ward = wardRepository.findById(ipd.getWardId())
                .filter(w -> java.util.Objects.equals(w.getHospitalId(), bill.getHospitalId()))
                .orElse(null);
        return ward != null && ward.getBedPrice() != null ? ward.getBedPrice() : BigDecimal.ZERO;
    }

    // ─── Money on a bill, and the operations that change it ────────────────────────────────

    private static final java.util.Set<String> PAYMENT_MODES = java.util.Set.of("CASH", "CARD", "UPI");

    /** Gross, discount, net, paid and balance for one bill — the single source for every screen. */
    public BillMoney moneyFor(Billing bill) {
        return BillMoney.of(bill,
                billingItemRepository.findByBillingId(bill.getId()),
                billingMedicineRepository.findByBillingId(bill.getId()),
                billingPaymentRepository.findByBillingId(bill.getId()),
                () -> legacyIpdBedPrice(bill));
    }

    private String currentActor() {
        try {
            return securityHelper.getCurrentUserRole() + " (" + securityHelper.getCurrentUserEmail() + ")";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    private Billing lockedBill(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateBillingAccess(hospitalId);
        return billingRepository.findByIdAndHospitalIdForUpdate(id, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException(BILL_NOT_FOUND));
    }

    private void requireAdmin(String message) {
        if (!"HOSPITAL_ADMIN".equalsIgnoreCase(securityHelper.getCurrentUserRole())) {
            throw new org.springframework.security.access.AccessDeniedException(message);
        }
    }

    /** CASH / CARD / UPI ("ONLINE" from older screens means UPI). */
    public static String normaliseMode(String mode) {
        String m = mode == null ? "" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        if ("ONLINE".equals(m)) m = "UPI";
        if (!PAYMENT_MODES.contains(m)) {
            throw new IllegalArgumentException("Payment method must be Cash, Card or UPI");
        }
        return m;
    }

    private void afterMoneyChange(Billing bill, String action, String details) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(),
                    bill.getHospitalId(), BILLING_MODULE, bill.getPublicId(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit {}", action, e);
        }
        try {
            webSocketHandler.broadcast(bill.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast refresh after {}", action, e);
        }
    }

    private static Map<String, Object> moneyMap(Billing bill, BillMoney m) {
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("billingId", bill.getId());
        out.put("customId", bill.getCustomId());
        out.put("status", bill.getPaymentStatus());
        out.put("grossAmount", m.gross());
        out.put("discountAmount", m.discount());
        out.put("totalAmount", m.net());
        out.put("paidAmount", m.paid());
        out.put("balance", m.balance());
        return out;
    }

    /**
     * Take a payment — full or part — against what is still owed. Taken under the bill's row
     * lock so a double-clicked button can't post the same money twice.
     */
    @Transactional
    public Map<String, Object> recordPayment(Long id, BigDecimal amount, String mode, String reference) {
        Billing bill = lockedBill(id);
        if (STATUS_CANCELLED.equalsIgnoreCase(bill.getPaymentStatus()) || STATUS_CLOSED.equalsIgnoreCase(bill.getPaymentStatus())) {
            throw new IllegalArgumentException("This bill is " + bill.getPaymentStatus().toLowerCase() + " and can't take payments");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Enter an amount greater than zero");
        }
        String m = normaliseMode(mode);
        String ref = reference == null ? null : reference.trim();
        if ("UPI".equals(m) && (ref == null || ref.isEmpty())) {
            throw new IllegalArgumentException("UTR / reference is required for UPI payments");
        }
        BillMoney before = moneyFor(bill);
        if (amount.compareTo(before.balance()) > 0) {
            throw new com.hms.exception.ConflictException(
                    "The remaining balance is " + before.balance() + "; cannot accept a payment of " + amount + ".");
        }

        com.hms.entity.BillingPayment p = new com.hms.entity.BillingPayment();
        p.setBillingId(bill.getId());
        p.setHospitalId(bill.getHospitalId());
        p.setAmount(amount);
        p.setMode(m);
        p.setReference(ref == null || ref.isEmpty() ? null : ref);
        p.setKind(KIND_PAYMENT);
        p.setRecordedBy(currentActor());
        billingPaymentRepository.save(p);

        BillMoney after = moneyFor(bill);
        String implied = after.impliedStatus();
        if (implied != null) bill.setPaymentStatus(implied);
        bill.setPaymentMethod(m);
        if (p.getReference() != null) bill.setPaymentReference(p.getReference());
        if ("PAID".equals(implied)) bill.setMarkedPaidBy(currentActor());
        billingRepository.save(bill);

        afterMoneyChange(bill, "BILLING_PAYMENT_RECORDED",
                "Recorded payment of " + amount + " (" + m + ") for bill " + bill.getCustomId() + ".");
        return moneyMap(bill, after);
    }

    /**
     * Discount / concession. Gross charges stay as they were; net payable drops. A discount can
     * never push net below what was already collected — that money would have to be refunded.
     * Zero removes the discount.
     */
    @Transactional
    public Map<String, Object> applyDiscount(Long id, BigDecimal amount, String reason) {
        Billing bill = lockedBill(id);
        String st = bill.getPaymentStatus();
        if (STATUS_CANCELLED.equalsIgnoreCase(st) || STATUS_CLOSED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("A " + st.toLowerCase() + " bill can't be discounted");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Enter a discount of zero or more");
        }
        if (amount.signum() > 0 && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required for a discount");
        }
        BillMoney before = moneyFor(bill);
        BigDecimal max = before.gross().subtract(before.paid()).max(BigDecimal.ZERO);
        if (amount.compareTo(max) > 0) {
            throw new IllegalArgumentException("The discount can be at most " + max + " — " + before.paid()
                    + " is already collected on a bill of " + before.gross() + ". Refund first to discount more.");
        }
        bill.setDiscountAmount(amount.signum() == 0 ? null : amount);
        bill.setDiscountReason(amount.signum() == 0 ? null : reason.trim());
        bill.setDiscountedBy(amount.signum() == 0 ? null : currentActor());

        BillMoney after = moneyFor(bill);
        String implied = after.impliedStatus();
        if (implied != null) bill.setPaymentStatus(implied);
        billingRepository.save(bill);

        afterMoneyChange(bill, "BILLING_DISCOUNT_APPLIED",
                "Discount of " + amount + " on bill " + bill.getCustomId()
                        + (amount.signum() > 0 ? " (" + reason.trim() + ")" : " removed") + ".");
        return moneyMap(bill, after);
    }

    /** Pay money back without changing the charges (the patient will owe it again). */
    public Map<String, Object> refund(Long id, BigDecimal amount, String mode, String reason) {
        return refund(id, amount, mode, reason, false);
    }

    /**
     * Pay money back. Admin only; recorded as a negative ledger row so collections net it out.
     *
     * <p>reduceBill = the patient was overcharged: the same amount is added to the bill's
     * discount, so a settled bill stays settled instead of showing the refund as owed again.
     */
    @Transactional
    public Map<String, Object> refund(Long id, BigDecimal amount, String mode, String reason, boolean reduceBill) {
        requireAdmin("Only the hospital admin can refund money");
        Billing bill = lockedBill(id);
        String m = normaliseMode(mode);
        BillMoney before = moneyFor(bill);
        validateRefund(bill, amount, reason, before);
        writeRefund(bill, amount, m, reason.trim());
        if (reduceBill) addRefundToDiscount(bill, amount, reason.trim(), before.gross());

        BillMoney after = moneyFor(bill);
        syncLiveStatus(bill, after);
        billingRepository.save(bill);
        afterMoneyChange(bill, "BILLING_REFUND_RECORDED",
                "Refunded " + amount + " (" + m + ") on bill " + bill.getCustomId() + ": " + reason.trim() + ".");
        return moneyMap(bill, after);
    }

    private static void validateRefund(Billing bill, BigDecimal amount, String reason, BillMoney before) {
        if (STATUS_CANCELLED.equalsIgnoreCase(bill.getPaymentStatus())) {
            throw new IllegalArgumentException("This bill is already cancelled");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Enter a refund amount greater than zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for a refund");
        }
        if (amount.compareTo(before.paid()) > 0) {
            throw new IllegalArgumentException("Only " + before.paid() + " has been collected on this bill");
        }
    }

    /** An overcharge refund also lowers the bill, so a settled bill stays settled. */
    private void addRefundToDiscount(Billing bill, BigDecimal amount, String reason, BigDecimal gross) {
        BigDecimal current = bill.getDiscountAmount() == null ? BigDecimal.ZERO : bill.getDiscountAmount();
        bill.setDiscountAmount(current.add(amount).min(gross));
        String note = "Refund: " + reason;
        String prev = bill.getDiscountReason();
        String merged = prev == null || prev.isBlank() ? note : prev + "; " + note;
        bill.setDiscountReason(merged.length() > 255 ? merged.substring(0, 255) : merged);
        bill.setDiscountedBy(currentActor());
    }

    /** A live (not closed) bill takes the status its ledger implies. */
    private static void syncLiveStatus(Billing bill, BillMoney money) {
        if (STATUS_CLOSED.equalsIgnoreCase(bill.getPaymentStatus())) return;
        String implied = money.impliedStatus();
        if (implied != null) bill.setPaymentStatus(implied);
    }

    /**
     * Cancel (void) a bill. Admin only. Anything collected is refunded first, so the bill ends
     * with zero net collected. IPD bills are not cancelled — they are finalised at discharge.
     */
    @Transactional
    public Map<String, Object> cancel(Long id, String reason, String refundMode) {
        requireAdmin("Only the hospital admin can cancel a bill");
        Billing bill = lockedBill(id);
        String st = bill.getPaymentStatus();
        if (STATUS_CANCELLED.equalsIgnoreCase(st)) throw new IllegalArgumentException("This bill is already cancelled");
        if (STATUS_CLOSED.equalsIgnoreCase(st)) throw new IllegalArgumentException("A closed bill can't be cancelled");
        if ("IPD".equalsIgnoreCase(bill.getBillingType())) {
            throw new IllegalArgumentException("IPD bills are settled at discharge — use a discount or refund instead");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to cancel a bill");
        }
        BillMoney before = moneyFor(bill);
        if (before.paid().signum() > 0) {
            writeRefund(bill, before.paid(), normaliseMode(refundMode), "Bill cancelled: " + reason.trim());
        }
        bill.setPaymentStatus(STATUS_CANCELLED);
        bill.setCancelReason(reason.trim());
        bill.setCancelledBy(currentActor());
        bill.setCancelledAt(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()));
        billingRepository.save(bill);
        afterMoneyChange(bill, "BILLING_CANCELLED", "Cancelled bill " + bill.getCustomId() + ": " + reason.trim()
                + (before.paid().signum() > 0 ? " (refunded " + before.paid() + ")" : "") + ".");
        return moneyMap(bill, moneyFor(bill));
    }

    /**
     * Settles the bill of an OPD visit that is being cancelled. Nothing collected: the bill is
     * cancelled with the visit, whoever cancels (no money moves, so no admin approval is needed).
     * Money collected: the admin cancels and refunds in one step; anyone else leaves the bill as
     * it is, and the returned note says the admin must refund it from Billing.
     *
     * @return a sentence for the person cancelling, or null when there was no bill to touch
     */
    @Transactional
    public String settleForCancelledOpd(Long opdId, String reason, String refundMode) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Billing found = billingRepository.findFirstByOpdIdOrderByIdDesc(opdId).orElse(null);
        if (found == null || !java.util.Objects.equals(found.getHospitalId(), hospitalId)) return null;
        Billing bill = billingRepository.findByIdAndHospitalIdForUpdate(found.getId(), hospitalId).orElse(null);
        if (bill == null) return null;
        String st = bill.getPaymentStatus();
        if (STATUS_CANCELLED.equalsIgnoreCase(st) || STATUS_CLOSED.equalsIgnoreCase(st)) return null;
        String why = "OPD visit cancelled: " + reason;
        BillMoney money = moneyFor(bill);
        if (money.paid().signum() > 0) {
            if (!"HOSPITAL_ADMIN".equalsIgnoreCase(securityHelper.getCurrentUserRole())) {
                return BILL_LABEL + bill.getCustomId() + " still holds the ₹" + money.paid().stripTrailingZeros().toPlainString()
                        + " collected. Ask the hospital admin to refund it from Billing.";
            }
            if (refundMode == null || refundMode.isBlank()) {
                throw new IllegalArgumentException("Choose how the ₹" + money.paid().stripTrailingZeros().toPlainString()
                        + " collected is refunded: Cash, Card or UPI.");
            }
            writeRefund(bill, money.paid(), normaliseMode(refundMode), why);
        }
        bill.setPaymentStatus(STATUS_CANCELLED);
        bill.setCancelReason(why.length() > 255 ? why.substring(0, 255) : why);
        bill.setCancelledBy(currentActor());
        bill.setCancelledAt(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()));
        billingRepository.save(bill);
        afterMoneyChange(bill, "BILLING_CANCELLED", "Cancelled bill " + bill.getCustomId() + " with its OPD visit"
                + (money.paid().signum() > 0 ? " (refunded " + money.paid() + ")" : "") + ".");
        return money.paid().signum() > 0
                ? BILL_LABEL + bill.getCustomId() + " cancelled and ₹" + money.paid().stripTrailingZeros().toPlainString() + " refunded."
                : BILL_LABEL + bill.getCustomId() + " cancelled.";
    }

    /** Keeps an OPD visit's open bill under the doctor the patient now sees. */
    @Transactional
    public void moveOpdBillToDoctor(Long opdId, Long doctorId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        billingRepository.findFirstByOpdIdOrderByIdDesc(opdId)
                .filter(b -> java.util.Objects.equals(b.getHospitalId(), hospitalId))
                .filter(b -> !STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus()))
                .ifPresent(b -> { b.setDoctorId(doctorId); billingRepository.save(b); });
    }

    private void writeRefund(Billing bill, BigDecimal amount, String mode, String reason) {
        com.hms.entity.BillingPayment r = new com.hms.entity.BillingPayment();
        r.setBillingId(bill.getId());
        r.setHospitalId(bill.getHospitalId());
        r.setAmount(amount.negate());
        r.setMode(mode);
        r.setKind("REFUND");
        r.setNote(reason.length() > 255 ? reason.substring(0, 255) : reason);
        r.setRecordedBy(currentActor());
        billingPaymentRepository.save(r);
    }

    /** Live bills of this hospital; a legacy bill hand-marked PAID with no ledger counts as settled. */
    private boolean mayStillOwe(Billing b, Long hospitalId) {
        if (!java.util.Objects.equals(b.getHospitalId(), hospitalId)) return false;
        String st = b.getPaymentStatus();
        if (STATUS_CANCELLED.equalsIgnoreCase(st) || STATUS_CLOSED.equalsIgnoreCase(st)) return false;
        return !"PAID".equalsIgnoreCase(st) || !billingPaymentRepository.findByBillingId(b.getId()).isEmpty();
    }

    /** Every bill of this patient that still has money owing (cancelled and closed bills excluded). */
    public java.util.List<Map<String, Object>> outstandingBillsForPatient(Long patientId, Long hospitalId) {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (patientId == null) return out;
        for (Billing b : billingRepository.findByPatientIdOrderByCreatedAtDesc(patientId)) {
            if (!mayStillOwe(b, hospitalId)) continue;
            BillMoney m = moneyFor(b);
            if (m.balance().signum() > 0) {
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("billingId", b.getId());
                row.put("customId", b.getCustomId());
                row.put("billingType", b.getBillingType());
                row.put("balance", m.balance());
                out.add(row);
            }
        }
        return out;
    }

    /** Running totals for the daily collection report, by payment mode. */
    private static final class CollectionTally {
        final Map<String, BigDecimal> collected = new java.util.LinkedHashMap<>();
        final Map<String, BigDecimal> refunded = new java.util.LinkedHashMap<>();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        int receipts = 0;

        CollectionTally() {
            for (String m : java.util.List.of("CASH", "CARD", "UPI")) {
                collected.put(m, BigDecimal.ZERO);
                refunded.put(m, BigDecimal.ZERO);
            }
        }

        /** Counts one ledger row and returns it as a report line. */
        Map<String, Object> add(com.hms.entity.BillingPayment p, Billing b, String patientName) {
            String mode = p.getMode() == null ? "CASH" : p.getMode().trim().toUpperCase(java.util.Locale.ROOT);
            if ("ONLINE".equals(mode)) mode = "UPI";
            boolean isRefund = p.getAmount().signum() < 0;
            BigDecimal abs = p.getAmount().abs();
            if (isRefund) {
                refunded.merge(mode, abs, BigDecimal::add);
                totalOut = totalOut.add(abs);
            } else {
                collected.merge(mode, abs, BigDecimal::add);
                totalIn = totalIn.add(abs);
                receipts++;
            }
            Map<String, Object> t = new java.util.HashMap<>();
            t.put("id", p.getId());
            t.put("time", p.getCreatedAt());
            t.put("billNo", b.getCustomId());
            t.put("billingType", b.getBillingType());
            t.put("patientName", patientName);
            t.put("mode", mode);
            t.put("kind", isRefund ? "REFUND" : KIND_PAYMENT);
            t.put("amount", abs);
            t.put("reference", p.getReference());
            t.put("note", p.getNote());
            t.put("recordedBy", p.getRecordedBy());
            return t;
        }
    }

    /**
     * Daily cash-closing: every payment and refund taken on one day, totalled by mode, with
     * who recorded it — what the counter hands over at the end of a shift.
     */
    public Map<String, Object> dailyCollection(java.time.LocalDate date) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateBillingAccess(hospitalId);
        java.time.LocalDateTime from = date.atStartOfDay();
        java.util.List<com.hms.entity.BillingPayment> rows = billingPaymentRepository
                .findByHospitalIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        hospitalId, from, from.plusDays(1));

        Map<Long, Billing> bills = new java.util.HashMap<>();
        for (Billing b : billingRepository.findAllById(rows.stream().map(com.hms.entity.BillingPayment::getBillingId).distinct().toList())) {
            if (java.util.Objects.equals(b.getHospitalId(), hospitalId)) bills.put(b.getId(), b);
        }
        Map<Long, String> names = new java.util.HashMap<>();
        for (com.hms.entity.Patient p : patientRepository.findAllById(bills.values().stream().map(Billing::getPatientId).filter(java.util.Objects::nonNull).distinct().toList())) {
            names.put(p.getId(), p.getName());
        }

        CollectionTally tally = new CollectionTally();
        java.util.List<Map<String, Object>> txns = new java.util.ArrayList<>();
        for (com.hms.entity.BillingPayment p : rows) {
            Billing b = bills.get(p.getBillingId());
            if (b != null && p.getAmount() != null) {
                txns.add(tally.add(p, b, names.getOrDefault(b.getPatientId(), "—")));
            }
        }
        Map<String, BigDecimal> collected = tally.collected;
        Map<String, BigDecimal> refunded = tally.refunded;
        BigDecimal totalIn = tally.totalIn;
        BigDecimal totalOut = tally.totalOut;
        int receipts = tally.receipts;
        Map<String, BigDecimal> net = new java.util.LinkedHashMap<>();
        collected.forEach((m, in) -> net.put(m, in.subtract(refunded.getOrDefault(m, BigDecimal.ZERO))));
        refunded.forEach((m, outAmt) -> net.putIfAbsent(m, outAmt.negate()));

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("date", date.toString());
        out.put("collectedByMode", collected);
        out.put("refundedByMode", refunded);
        out.put("netByMode", net);
        out.put("totalCollected", totalIn);
        out.put("totalRefunded", totalOut);
        out.put("netCollected", totalIn.subtract(totalOut));
        out.put("receiptCount", receipts);
        out.put("transactions", txns);
        return out;
    }

    /**
     * Replace a bill's charge lines, atomically.
     *
     * <p>Refuses a negative charge, and refuses a new total below what has already been
     * collected: that left the bill reading PAID with a negative balance and no refund recorded.
     */
    @Transactional
    public Billing replaceBillItems(Long id, java.util.List<com.hms.dto.HospitalFeeDTO> items) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Billing billing = billingRepository.findByIdAndHospitalIdForUpdate(id, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException(BILL_NOT_FOUND));

        if ("PAID".equalsIgnoreCase(billing.getPaymentStatus()) || STATUS_CLOSED.equalsIgnoreCase(billing.getPaymentStatus())
                || STATUS_CANCELLED.equalsIgnoreCase(billing.getPaymentStatus())) {
            throw new IllegalArgumentException("Cannot edit items of a paid, closed or cancelled bill");
        }

        java.util.List<com.hms.entity.BillingItem> replacement = new java.util.ArrayList<>();
        BigDecimal newItemsTotal = BigDecimal.ZERO;
        if (items != null) {
            for (com.hms.dto.HospitalFeeDTO itemDto : items) {
                if (itemDto.getName() == null || itemDto.getName().trim().isEmpty()) {
                    continue;
                }
                BigDecimal amt = itemDto.getDefaultAmount() != null ? itemDto.getDefaultAmount() : BigDecimal.ZERO;
                if (amt.signum() < 0) {
                    throw new IllegalArgumentException("A charge cannot be negative");
                }
                com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
                item.setBillingId(id);
                item.setHospitalId(hospitalId);
                item.setDescription(itemDto.getName().trim());
                item.setAmount(amt);
                replacement.add(item);
                newItemsTotal = newItemsTotal.add(amt);
            }
        }

        BigDecimal medicinesTotal = BigDecimal.ZERO;
        for (com.hms.entity.BillingMedicine med : billingMedicineRepository.findByBillingId(id)) {
            if (med.getAmount() != null) medicinesTotal = medicinesTotal.add(med.getAmount());
        }
        BigDecimal collected = BigDecimal.ZERO;
        for (com.hms.entity.BillingPayment p : billingPaymentRepository.findByBillingId(id)) {
            if (p.getAmount() != null) collected = collected.add(p.getAmount());
        }
        BigDecimal newGross = newItemsTotal.add(medicinesTotal);
        BigDecimal keptDiscount = billing.getDiscountAmount() == null ? BigDecimal.ZERO
                : billing.getDiscountAmount().min(newGross).max(BigDecimal.ZERO);
        if (newGross.subtract(keptDiscount).compareTo(collected) < 0) {
            throw new IllegalArgumentException("The new total is less than the " + collected
                    + " already collected on this bill");
        }

        billingItemRepository.deleteAll(billingItemRepository.findByBillingId(id));
        billingItemRepository.saveAll(replacement);
        recalculateTotal(id);
        return billing;
    }

    /**
     * Centralized utility to recalculate top-level bill amount based on active items.
     * Prevents IPD denormalized totals divergence.
     */
    public void recalculateTotal(Long billingId) {
        Billing bill = billingRepository.findById(billingId).orElse(null);
        if (bill != null) {
            java.util.List<com.hms.entity.BillingItem> items = billingItemRepository.findByBillingId(billingId);
            BigDecimal total = BigDecimal.ZERO;
            for (com.hms.entity.BillingItem item : items) {
                if (item.getAmount() != null) {
                    total = total.add(item.getAmount());
                }
            }
            java.util.List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(billingId);
            for (com.hms.entity.BillingMedicine med : medicines) {
                if (med.getAmount() != null) {
                    total = total.add(med.getAmount());
                }
            }
            bill.setAmount(total);
            syncStatusToLedger(bill, total);
            billingRepository.save(bill);
            logger.info("Recalculated total for bill {}: {} (status {})", LogSanitizer.clean(billingId), total, LogSanitizer.clean(bill.getPaymentStatus()));
        }
    }

    /**
     * Keep a bill's status honest: it is derived from what the ledger has actually collected
     * against the current total, never asserted independently.
     *
     *   paid >= total  -> PAID
     *   0 < paid < total -> PARTIAL   (a balance is owed)
     *   paid == 0      -> PENDING
     *
     * This is what makes "bill before OPD" work: the patient pre-pays the consultation +
     * case-paper fee, then the doctor adds a procedure or in-clinic medicines, the total rises
     * above what was collected, and the bill correctly drops back to PARTIAL with a balance for
     * reception to collect at checkout — instead of silently continuing to read PAID while money
     * is owed. CLOSED (written off / cancelled) is a terminal decision and is never overridden.
     */
    private void syncStatusToLedger(Billing bill, BigDecimal total) {
        String st = bill.getPaymentStatus();
        if (STATUS_CLOSED.equalsIgnoreCase(st) || STATUS_CANCELLED.equalsIgnoreCase(st)) return;

        java.util.List<com.hms.entity.BillingPayment> ledger = billingPaymentRepository.findByBillingId(bill.getId());

        // A bill marked PAID with no ledger rows at all is a legacy/hand-marked record from
        // before payments were itemised. Treat it as settled rather than "paid nothing" — the
        // printed receipt applies the same rule — otherwise recalculating a total would silently
        // un-pay it. Every bill written today records its money, so this only shields old rows.
        if (ledger.isEmpty() && "PAID".equalsIgnoreCase(st)) return;

        // Status follows the NET payable (gross - discount), so a concession that covers the
        // remaining balance settles the bill.
        BillMoney money = BillMoney.of(bill, java.util.List.of(), java.util.List.of(), ledger, () -> total);
        String implied = money.impliedStatus();
        if (implied != null) bill.setPaymentStatus(implied);
    }

    @Transactional
    public Billing createManualBill(Long patientId, Long doctorId, String billingType, BigDecimal amount, String description, String paymentStatus, String paymentMethod, String paymentReference, java.util.List<com.hms.entity.BillingItem> items) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateBillingAccess(hospitalId);

        Billing bill = new Billing();
        bill.setHospitalId(hospitalId);
        bill.setPatientId(patientId);
        bill.setDoctorId(doctorId != null ? doctorId : 0L);
        bill.setBillingType(billingType != null && !billingType.isEmpty() ? billingType : "GENERAL");
        bill.setAmount(amount != null ? amount : BigDecimal.ZERO);
        bill.setDescription(description != null && !description.isEmpty() ? description : "Manual Bill");

        String status = "PAID".equalsIgnoreCase(paymentStatus) ? "PAID" : "PENDING";
        bill.setPaymentStatus(status);
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            bill.setPaymentMethod(paymentMethod);
        }
        if (paymentReference != null && !paymentReference.isEmpty()) {
            bill.setPaymentReference(paymentReference);
        }
        if ("PAID".equals(status)) {
            try {
                bill.setMarkedPaidBy(securityHelper.getCurrentUserRole() + " (" + securityHelper.getCurrentUserEmail() + ")");
            } catch (Exception ignored) {}
        }

        Billing saved = saveBillWithCollisionRetry(bill);

        BigDecimal itemTotal = BigDecimal.ZERO;
        if (items != null && !items.isEmpty()) {
            for (com.hms.entity.BillingItem item : items) {
                item.setBillingId(saved.getId());
                item.setHospitalId(hospitalId);
                billingItemRepository.save(item);
                if (item.getAmount() != null) {
                    itemTotal = itemTotal.add(item.getAmount());
                }
            }
            if (itemTotal.compareTo(BigDecimal.ZERO) > 0) {
                saved.setAmount(itemTotal);
                saved = billingRepository.save(saved);
            }
        }

        if ("PAID".equals(status)) {
            com.hms.entity.BillingPayment payment = new com.hms.entity.BillingPayment();
            payment.setBillingId(saved.getId());
            payment.setHospitalId(hospitalId);
            payment.setAmount(saved.getAmount());
            payment.setMode(paymentMethod != null ? paymentMethod : "CASH");
            payment.setReference(paymentReference);
            billingPaymentRepository.save(payment);
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after manual bill creation", e);
        }

        logger.info("Created manual bill {} for patient {} with amount {}", saved.getId(), patientId, saved.getAmount());
        return saved;
    }

    /** Historical entry point: allocate and save in the caller's atomic transaction. */
    @Transactional
    public Billing saveBillWithCollisionRetry(Billing bill) {
        ensureBillingCustomId(bill);
        return billingRepository.save(bill);
    }

    public String allocateBillingCustomId(Long hospitalId) {
        return customIdAllocator.allocateBilling(hospitalId);
    }

    public void ensureBillingCustomId(Billing bill) {
        if (bill.getHospitalId() == null) throw new IllegalArgumentException("Hospital is required");
        if (bill.getCustomId() == null || bill.getCustomId().isBlank()) {
            bill.setCustomId(allocateBillingCustomId(bill.getHospitalId()));
        }
        // Preserve assigned numbers; database uniqueness rejects supplied collisions.
    }
}
