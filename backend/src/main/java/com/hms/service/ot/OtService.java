package com.hms.service.ot;

import com.hms.dto.ot.OtBillingChargeRequest;
import com.hms.dto.ot.OtBookingRequest;
import com.hms.dto.ot.OtStatusRequest;
import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.HospitalInventory;
import com.hms.entity.ot.*;
import com.hms.repository.BillingItemRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalInventoryRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.ot.*;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class OtService {

    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private OtRoomRepository roomRepository;
    @Autowired private SurgeryRepository surgeryRepository;
    @Autowired private OtBookingRepository bookingRepository;
    @Autowired private PreOpChecklistRepository preOpChecklistRepository;
    @Autowired private OtStaffAssignmentRepository staffAssignmentRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private EquipmentAssignmentRepository equipmentAssignmentRepository;
    @Autowired private InstrumentSetRepository instrumentSetRepository;
    @Autowired private InstrumentAssignmentRepository instrumentAssignmentRepository;
    @Autowired private WhoChecklistRepository whoChecklistRepository;
    @Autowired private SurgeryStatusLogRepository statusLogRepository;
    @Autowired private AnesthesiaRecordRepository anesthesiaRecordRepository;
    @Autowired private ImplantUsageRepository implantUsageRepository;
    @Autowired private RecoveryRoomRepository recoveryRoomRepository;
    @Autowired private OtConsumableUsageRepository consumableUsageRepository;
    @Autowired private OtBillingRepository otBillingRepository;
    @Autowired private BillingRepository billingRepository;
    @Autowired private BillingItemRepository billingItemRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private HospitalInventoryRepository hospitalInventoryRepository;

    public Map<String, Object> dashboard() {
        Long hospitalId = hospitalId();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        long todays = bookingRepository.countByHospitalIdAndScheduledStartBetween(hospitalId, start, end);
        long completed = bookingRepository.countByHospitalIdAndStatusIgnoreCase(hospitalId, "COMPLETED");
        long ongoing = bookingRepository.countByHospitalIdAndStatusIgnoreCase(hospitalId, "ONGOING");
        long emergency = bookingRepository.countByHospitalIdAndPriorityIgnoreCase(hospitalId, "EMERGENCY");
        long rooms = roomRepository.findByHospitalIdOrderByName(hospitalId).size();
        long availableRooms = roomRepository.countByHospitalIdAndStatusIgnoreCase(hospitalId, "AVAILABLE");
        long pending = bookingRepository.countByHospitalIdAndClearanceStatusIgnoreCase(hospitalId, "PENDING_CLEARANCE");
        long staff = staffAssignmentRepository.countByHospitalId(hospitalId);
        long equipment = equipmentRepository.countByHospitalIdAndStatusIgnoreCase(hospitalId, "AVAILABLE");
        long instruments = instrumentSetRepository.countByHospitalIdAndStatusIgnoreCase(hospitalId, "STERILIZED");
        int utilization = rooms == 0 ? 0 : (int) Math.min(100, Math.round((todays * 100.0) / Math.max(1, rooms * 6)));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("todaysSurgeries", todays);
        map.put("ongoingSurgeries", ongoing);
        map.put("completedSurgeries", completed);
        map.put("emergencySurgeries", emergency);
        map.put("availableOtRooms", availableRooms);
        map.put("otUtilization", utilization);
        map.put("pendingClearances", pending);
        map.put("otStaffOnDuty", staff);
        map.put("equipmentAvailable", equipment);
        map.put("sterilizedInstrumentSets", instruments);
        return map;
    }

    public Map<String, Object> lookups() {
        Long hospitalId = hospitalId();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("patients", patientRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId));
        map.put("doctors", doctorRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId));
        map.put("rooms", roomRepository.findByHospitalIdOrderByName(hospitalId));
        map.put("equipment", equipmentRepository.findByHospitalIdOrderByName(hospitalId));
        map.put("instrumentSets", instrumentSetRepository.findByHospitalIdOrderByName(hospitalId));
        map.put("inventory", hospitalInventoryRepository.findByHospitalIdAndIsActiveTrue(hospitalId));
        return map;
    }

    public List<OtBooking> bookings(LocalDate from, LocalDate to) {
        Long hospitalId = hospitalId();
        if (from != null && to != null) {
            return bookingRepository.findByHospitalIdAndScheduledStartBetweenOrderByScheduledStart(
                    hospitalId, from.atStartOfDay(), to.atTime(LocalTime.MAX));
        }
        return bookingRepository.findByHospitalIdOrderByScheduledStartDesc(hospitalId);
    }

    @Transactional
    public OtBooking createBooking(OtBookingRequest request) {
        validateBookingWindow(request.getScheduledStart(), request.getScheduledEnd());
        Long hospitalId = hospitalId();
        patientRepository.findById(request.getPatientId())
                .filter(p -> hospitalId.equals(p.getHospitalId()))
                .orElseThrow(() -> new RuntimeException("Patient not found for this hospital"));

        OtBooking booking = new OtBooking();
        applyBookingRequest(booking, request);
        booking.setHospitalId(hospitalId);
        booking.setCreatedBy(userEmail());
        if (booking.getOtRoomId() != null) {
            ensureRoomAvailable(hospitalId, booking);
        }
        OtBooking saved = bookingRepository.save(booking);
        ensureDefaultChecklists(saved);
        logStatus(saved, "SCHEDULED", "Surgery booked");
        return saved;
    }

    @Transactional
    public OtBooking updateBooking(Long id, OtBookingRequest request) {
        validateBookingWindow(request.getScheduledStart(), request.getScheduledEnd());
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(id, hospitalId);
        applyBookingRequest(booking, request);
        if (booking.getOtRoomId() != null) {
            ensureRoomAvailable(hospitalId, booking);
        }
        return bookingRepository.save(booking);
    }

    public Map<String, Object> bookingDetails(Long id) {
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(id, hospitalId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("booking", booking);
        details.put("preOpChecklist", preOpChecklistRepository.findByHospitalIdAndOtBookingId(hospitalId, id).orElse(null));
        details.put("whoChecklist", whoChecklistRepository.findByHospitalIdAndOtBookingId(hospitalId, id).orElse(null));
        details.put("staffAssignments", staffAssignmentRepository.findByHospitalIdAndOtBookingId(hospitalId, id));
        details.put("equipmentAssignments", equipmentAssignmentRepository.findByHospitalIdAndOtBookingId(hospitalId, id));
        details.put("instrumentAssignments", instrumentAssignmentRepository.findByHospitalIdAndOtBookingId(hospitalId, id));
        details.put("statusLog", statusLogRepository.findByHospitalIdAndOtBookingIdOrderByEventTime(hospitalId, id));
        details.put("anesthesiaRecords", anesthesiaRecordRepository.findByHospitalIdAndOtBookingIdOrderByCreatedAtDesc(hospitalId, id));
        details.put("implants", implantUsageRepository.findByHospitalIdAndOtBookingId(hospitalId, id));
        details.put("recoveryRecords", recoveryRoomRepository.findByHospitalIdAndOtBookingIdOrderByCreatedAtDesc(hospitalId, id));
        details.put("consumables", consumableUsageRepository.findByHospitalIdAndOtBookingId(hospitalId, id));
        details.put("charges", otBillingRepository.findByHospitalIdAndOtBookingId(hospitalId, id));
        return details;
    }

    @Transactional
    public PreOpChecklist savePreOp(Long bookingId, PreOpChecklist checklist) {
        Long hospitalId = hospitalId();
        getBooking(bookingId, hospitalId);
        PreOpChecklist target = preOpChecklistRepository.findByHospitalIdAndOtBookingId(hospitalId, bookingId).orElse(new PreOpChecklist());
        target.setHospitalId(hospitalId);
        target.setCreatedBy(userEmail());
        target.setOtBookingId(bookingId);
        target.setConsentSigned(Boolean.TRUE.equals(checklist.getConsentSigned()));
        target.setBloodAvailable(Boolean.TRUE.equals(checklist.getBloodAvailable()));
        target.setCbc(Boolean.TRUE.equals(checklist.getCbc()));
        target.setLft(Boolean.TRUE.equals(checklist.getLft()));
        target.setKft(Boolean.TRUE.equals(checklist.getKft()));
        target.setPtInr(Boolean.TRUE.equals(checklist.getPtInr()));
        target.setEcg(Boolean.TRUE.equals(checklist.getEcg()));
        target.setChestXray(Boolean.TRUE.equals(checklist.getChestXray()));
        target.setCrossMatching(Boolean.TRUE.equals(checklist.getCrossMatching()));
        target.setPhysicianFitness(Boolean.TRUE.equals(checklist.getPhysicianFitness()));
        target.setPacClearance(Boolean.TRUE.equals(checklist.getPacClearance()));
        target.setNotes(checklist.getNotes());
        target.setStatus(isPreOpReady(target) ? "READY_FOR_OT" : "PENDING_CLEARANCE");
        PreOpChecklist saved = preOpChecklistRepository.save(target);
        OtBooking booking = getBooking(bookingId, hospitalId);
        booking.setClearanceStatus(saved.getStatus());
        bookingRepository.save(booking);
        return saved;
    }

    @Transactional
    public WhoChecklist saveWho(Long bookingId, WhoChecklist checklist) {
        Long hospitalId = hospitalId();
        getBooking(bookingId, hospitalId);
        WhoChecklist target = whoChecklistRepository.findByHospitalIdAndOtBookingId(hospitalId, bookingId).orElse(new WhoChecklist());
        target.setHospitalId(hospitalId);
        target.setCreatedBy(userEmail());
        target.setOtBookingId(bookingId);
        target.setPatientIdentity(Boolean.TRUE.equals(checklist.getPatientIdentity()));
        target.setSiteMarked(Boolean.TRUE.equals(checklist.getSiteMarked()));
        target.setConsentSigned(Boolean.TRUE.equals(checklist.getConsentSigned()));
        target.setAllergiesChecked(Boolean.TRUE.equals(checklist.getAllergiesChecked()));
        target.setBloodAvailable(Boolean.TRUE.equals(checklist.getBloodAvailable()));
        target.setTeamIntroduction(Boolean.TRUE.equals(checklist.getTeamIntroduction()));
        target.setAntibioticGiven(Boolean.TRUE.equals(checklist.getAntibioticGiven()));
        target.setImagingDisplayed(Boolean.TRUE.equals(checklist.getImagingDisplayed()));
        target.setInstrumentCount(Boolean.TRUE.equals(checklist.getInstrumentCount()));
        target.setSpongeCount(Boolean.TRUE.equals(checklist.getSpongeCount()));
        target.setFinalCount(Boolean.TRUE.equals(checklist.getFinalCount()));
        target.setSpecimenLabeled(Boolean.TRUE.equals(checklist.getSpecimenLabeled()));
        target.setProcedureConfirmed(Boolean.TRUE.equals(checklist.getProcedureConfirmed()));
        target.setRecoveryPlan(Boolean.TRUE.equals(checklist.getRecoveryPlan()));
        target.setStatus(isWhoComplete(target) ? "COMPLETED" : "PENDING");
        return whoChecklistRepository.save(target);
    }

    @Transactional
    public SurgeryStatusLog updateStatus(Long bookingId, OtStatusRequest request) {
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(bookingId, hospitalId);
        String status = normalize(request.getStatus());
        if ("COMPLETED".equals(status)) {
            requireWhoComplete(hospitalId, bookingId);
        }
        booking.setStatus(status);
        bookingRepository.save(booking);
        return logStatus(booking, status, request.getNotes());
    }

    @Transactional
    public OtStaffAssignment assignStaff(Long bookingId, OtStaffAssignment assignment) {
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(bookingId, hospitalId);
        if (assignment.getStaffName() == null || assignment.getStaffName().trim().isEmpty()) {
            throw new IllegalArgumentException("Staff name is required");
        }
        List<OtStaffAssignment> conflicts = staffAssignmentRepository.findStaffConflicts(
                hospitalId, assignment.getStaffUserId(), assignment.getDoctorId(), assignment.getStaffName(),
                booking.getScheduledStart(), booking.getScheduledEnd(), booking.getId());
        if (!conflicts.isEmpty()) {
            throw new RuntimeException("Staff member is already allocated during this time");
        }
        assignment.setHospitalId(hospitalId);
        assignment.setCreatedBy(userEmail());
        assignment.setOtBookingId(bookingId);
        return staffAssignmentRepository.save(assignment);
    }

    @Transactional
    public EquipmentAssignment assignEquipment(Long bookingId, EquipmentAssignment assignment) {
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(bookingId, hospitalId);
        equipmentRepository.findById(assignment.getEquipmentId())
                .filter(e -> hospitalId.equals(e.getHospitalId()))
                .orElseThrow(() -> new RuntimeException("Equipment not found"));
        if (!equipmentAssignmentRepository.findEquipmentConflicts(hospitalId, assignment.getEquipmentId(), booking.getScheduledStart(), booking.getScheduledEnd(), booking.getId()).isEmpty()) {
            throw new RuntimeException("Equipment is already allocated during this time");
        }
        assignment.setHospitalId(hospitalId);
        assignment.setCreatedBy(userEmail());
        assignment.setOtBookingId(bookingId);
        return equipmentAssignmentRepository.save(assignment);
    }

    @Transactional
    public InstrumentAssignment assignInstrument(Long bookingId, InstrumentAssignment assignment) {
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(bookingId, hospitalId);
        instrumentSetRepository.findById(assignment.getInstrumentSetId())
                .filter(i -> hospitalId.equals(i.getHospitalId()))
                .orElseThrow(() -> new RuntimeException("Instrument set not found"));
        if (!instrumentAssignmentRepository.findInstrumentConflicts(hospitalId, assignment.getInstrumentSetId(), booking.getScheduledStart(), booking.getScheduledEnd(), booking.getId()).isEmpty()) {
            throw new RuntimeException("Instrument set is already allocated during this time");
        }
        assignment.setHospitalId(hospitalId);
        assignment.setCreatedBy(userEmail());
        assignment.setOtBookingId(bookingId);
        return instrumentAssignmentRepository.save(assignment);
    }

    @Transactional
    public AnesthesiaRecord saveAnesthesia(Long bookingId, AnesthesiaRecord record) {
        Long hospitalId = hospitalId();
        getBooking(bookingId, hospitalId);
        record.setHospitalId(hospitalId);
        record.setCreatedBy(userEmail());
        record.setOtBookingId(bookingId);
        return anesthesiaRecordRepository.save(record);
    }

    @Transactional
    public ImplantUsage saveImplant(Long bookingId, ImplantUsage usage) {
        Long hospitalId = hospitalId();
        getBooking(bookingId, hospitalId);
        usage.setHospitalId(hospitalId);
        usage.setCreatedBy(userEmail());
        usage.setOtBookingId(bookingId);
        ImplantUsage saved = implantUsageRepository.save(usage);
        if (usage.getCharge() != null && usage.getCharge().compareTo(BigDecimal.ZERO) > 0) {
            addCharge(bookingId, charge("Implant Charges - " + usage.getImplantName(), usage.getCharge(), usage.getSerialNumber()));
        }
        return saved;
    }

    @Transactional
    public RecoveryRoom saveRecovery(Long bookingId, RecoveryRoom record) {
        Long hospitalId = hospitalId();
        getBooking(bookingId, hospitalId);
        record.setHospitalId(hospitalId);
        record.setCreatedBy(userEmail());
        record.setOtBookingId(bookingId);
        return recoveryRoomRepository.save(record);
    }

    @Transactional
    public OtConsumableUsage saveConsumable(Long bookingId, OtConsumableUsage usage) {
        Long hospitalId = hospitalId();
        getBooking(bookingId, hospitalId);
        usage.setHospitalId(hospitalId);
        usage.setCreatedBy(userEmail());
        usage.setOtBookingId(bookingId);
        OtConsumableUsage saved = consumableUsageRepository.save(usage);
        deductHospitalInventory(hospitalId, saved);
        BigDecimal amount = saved.getUnitCharge() == null ? BigDecimal.ZERO : saved.getUnitCharge().multiply(BigDecimal.valueOf(saved.getQuantity()));
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            addCharge(bookingId, charge("Consumable Charges - " + saved.getItemName(), amount, "Qty " + saved.getQuantity()));
        }
        return saved;
    }

    @Transactional
    public OtBilling addCharge(Long bookingId, OtBillingChargeRequest request) {
        Long hospitalId = hospitalId();
        getBooking(bookingId, hospitalId);
        OtBilling charge = new OtBilling();
        charge.setHospitalId(hospitalId);
        charge.setCreatedBy(userEmail());
        charge.setOtBookingId(bookingId);
        charge.setChargeType(request.getChargeType());
        charge.setAmount(request.getAmount());
        charge.setNotes(request.getNotes());
        return otBillingRepository.save(charge);
    }

    @Transactional
    public Billing completeAndGenerateBill(Long bookingId) {
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(bookingId, hospitalId);
        requireWhoComplete(hospitalId, bookingId);
        List<OtBilling> charges = otBillingRepository.findByHospitalIdAndOtBookingId(hospitalId, bookingId);
        if (charges.isEmpty()) {
            addDefaultCharges(booking);
            charges = otBillingRepository.findByHospitalIdAndOtBookingId(hospitalId, bookingId);
        }

        BigDecimal total = charges.stream()
                .map(OtBilling::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Billing bill = new Billing();
        bill.setHospitalId(hospitalId);
        bill.setPatientId(booking.getPatientId());
        bill.setDoctorId(booking.getSurgeonId() != null ? booking.getSurgeonId() : 0L);
        bill.setIpdAdmissionId(booking.getIpdAdmissionId());
        bill.setBillingType(booking.getIpdAdmissionId() != null ? "IPD" : "OPD");
        bill.setAmount(total);
        bill.setDescription("OT Surgery Charges - " + booking.getProcedureName());
        bill.setPaymentStatus("PENDING");
        Billing saved = billingRepository.save(bill);

        for (OtBilling charge : charges) {
            BillingItem item = new BillingItem();
            item.setBillingId(saved.getId());
            item.setHospitalId(hospitalId);
            item.setDescription(charge.getChargeType());
            item.setAmount(charge.getAmount());
            billingItemRepository.save(item);
            charge.setBillingId(saved.getId());
            otBillingRepository.save(charge);
        }

        booking.setBillingId(saved.getId());
        booking.setStatus("COMPLETED");
        bookingRepository.save(booking);
        logStatus(booking, "COMPLETED", "Surgery completed and OT bill generated");
        return saved;
    }

    public List<OtRoom> rooms() { return roomRepository.findByHospitalIdOrderByName(hospitalId()); }
    public OtRoom saveRoom(OtRoom room) { room.setHospitalId(hospitalId()); room.setCreatedBy(userEmail()); return roomRepository.save(room); }
    public List<Surgery> surgeries() { return surgeryRepository.findByHospitalIdAndActiveTrueOrderByName(hospitalId()); }
    public Surgery saveSurgery(Surgery surgery) { surgery.setHospitalId(hospitalId()); surgery.setCreatedBy(userEmail()); return surgeryRepository.save(surgery); }
    public List<Equipment> equipment() { return equipmentRepository.findByHospitalIdOrderByName(hospitalId()); }
    public Equipment saveEquipment(Equipment equipment) { equipment.setHospitalId(hospitalId()); equipment.setCreatedBy(userEmail()); return equipmentRepository.save(equipment); }
    public List<InstrumentSet> instruments() { return instrumentSetRepository.findByHospitalIdOrderByName(hospitalId()); }
    public InstrumentSet saveInstrument(InstrumentSet instrument) { instrument.setHospitalId(hospitalId()); instrument.setCreatedBy(userEmail()); return instrumentSetRepository.save(instrument); }

    @Transactional
    public OtBooking saveNotes(Long bookingId, Map<String, String> notes) {
        Long hospitalId = hospitalId();
        OtBooking booking = getBooking(bookingId, hospitalId);
        booking.setIntraOpNotes(notes.get("intraOpNotes"));
        booking.setPostOpOrders(notes.get("postOpOrders"));
        return bookingRepository.save(booking);
    }

    public Map<String, Object> reports() {
        Long hospitalId = hospitalId();
        Map<String, Object> reports = new LinkedHashMap<>();
        reports.put("dailyOtRegister", bookings(LocalDate.now(), LocalDate.now()));
        reports.put("emergencySurgeries", bookingRepository.countByHospitalIdAndPriorityIgnoreCase(hospitalId, "EMERGENCY"));
        reports.put("cancelledSurgeries", bookingRepository.countByHospitalIdAndStatusIgnoreCase(hospitalId, "CANCELLED"));
        reports.put("completedSurgeries", bookingRepository.countByHospitalIdAndStatusIgnoreCase(hospitalId, "COMPLETED"));
        reports.put("otRevenue", otBillingRepository.sumRevenue(hospitalId));
        reports.put("implantUsage", implantUsageRepository.findAll().stream().filter(i -> hospitalId.equals(i.getHospitalId())).toList());
        reports.put("instrumentUsage", instrumentAssignmentRepository.findAll().stream().filter(i -> hospitalId.equals(i.getHospitalId())).toList());
        return reports;
    }

    private void applyBookingRequest(OtBooking booking, OtBookingRequest request) {
        booking.setPatientId(request.getPatientId());
        booking.setPatientUhid(request.getPatientUhid());
        booking.setIpdAdmissionId(request.getIpdAdmissionId());
        booking.setIpdNumber(request.getIpdNumber());
        booking.setSurgeonId(request.getSurgeonId());
        booking.setAssistantSurgeonId(request.getAssistantSurgeonId());
        booking.setOtRoomId(request.getOtRoomId());
        booking.setOtTable(request.getOtTable());
        booking.setSpecialty(request.getSpecialty());
        booking.setProcedureName(request.getProcedureName());
        booking.setDiagnosis(request.getDiagnosis());
        booking.setExpectedDurationMinutes(request.getExpectedDurationMinutes() != null ? request.getExpectedDurationMinutes() : 60);
        booking.setPriority(normalizeOrDefault(request.getPriority(), "ELECTIVE"));
        booking.setSurgeryType(normalizeOrDefault(request.getSurgeryType(), "ELECTIVE"));
        booking.setScheduledStart(request.getScheduledStart());
        booking.setScheduledEnd(request.getScheduledEnd());
        booking.setRemarks(request.getRemarks());
    }

    private void ensureDefaultChecklists(OtBooking booking) {
        PreOpChecklist pre = new PreOpChecklist();
        pre.setHospitalId(booking.getHospitalId());
        pre.setCreatedBy(userEmail());
        pre.setOtBookingId(booking.getId());
        preOpChecklistRepository.save(pre);

        WhoChecklist who = new WhoChecklist();
        who.setHospitalId(booking.getHospitalId());
        who.setCreatedBy(userEmail());
        who.setOtBookingId(booking.getId());
        whoChecklistRepository.save(who);
    }

    private void ensureRoomAvailable(Long hospitalId, OtBooking booking) {
        roomRepository.findById(booking.getOtRoomId())
                .filter(room -> hospitalId.equals(room.getHospitalId()))
                .orElseThrow(() -> new RuntimeException("OT room not found"));
        if (!bookingRepository.findRoomConflicts(hospitalId, booking.getOtRoomId(), booking.getScheduledStart(), booking.getScheduledEnd(), booking.getId()).isEmpty()) {
            throw new RuntimeException("OT room is already booked for the selected time");
        }
    }

    private void validateBookingWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Scheduled end must be after scheduled start");
        }
    }

    private boolean isPreOpReady(PreOpChecklist c) {
        return Boolean.TRUE.equals(c.getConsentSigned())
                && Boolean.TRUE.equals(c.getBloodAvailable())
                && Boolean.TRUE.equals(c.getCbc())
                && Boolean.TRUE.equals(c.getLft())
                && Boolean.TRUE.equals(c.getKft())
                && Boolean.TRUE.equals(c.getPtInr())
                && Boolean.TRUE.equals(c.getEcg())
                && Boolean.TRUE.equals(c.getChestXray())
                && Boolean.TRUE.equals(c.getCrossMatching())
                && Boolean.TRUE.equals(c.getPhysicianFitness())
                && Boolean.TRUE.equals(c.getPacClearance());
    }

    private boolean isWhoComplete(WhoChecklist c) {
        return Boolean.TRUE.equals(c.getPatientIdentity())
                && Boolean.TRUE.equals(c.getSiteMarked())
                && Boolean.TRUE.equals(c.getConsentSigned())
                && Boolean.TRUE.equals(c.getAllergiesChecked())
                && Boolean.TRUE.equals(c.getBloodAvailable())
                && Boolean.TRUE.equals(c.getTeamIntroduction())
                && Boolean.TRUE.equals(c.getAntibioticGiven())
                && Boolean.TRUE.equals(c.getImagingDisplayed())
                && Boolean.TRUE.equals(c.getInstrumentCount())
                && Boolean.TRUE.equals(c.getSpongeCount())
                && Boolean.TRUE.equals(c.getFinalCount())
                && Boolean.TRUE.equals(c.getSpecimenLabeled())
                && Boolean.TRUE.equals(c.getProcedureConfirmed())
                && Boolean.TRUE.equals(c.getRecoveryPlan());
    }

    private void requireWhoComplete(Long hospitalId, Long bookingId) {
        WhoChecklist who = whoChecklistRepository.findByHospitalIdAndOtBookingId(hospitalId, bookingId)
                .orElseThrow(() -> new RuntimeException("WHO checklist is required before completing surgery"));
        if (!isWhoComplete(who)) {
            throw new RuntimeException("WHO Surgical Safety Checklist must be completed before completing surgery");
        }
    }

    private SurgeryStatusLog logStatus(OtBooking booking, String status, String notes) {
        SurgeryStatusLog log = new SurgeryStatusLog();
        log.setHospitalId(booking.getHospitalId());
        log.setCreatedBy(userEmail());
        log.setOtBookingId(booking.getId());
        log.setStatus(status);
        log.setNotes(notes);
        log.setEventTime(LocalDateTime.now());
        return statusLogRepository.save(log);
    }

    private void deductHospitalInventory(Long hospitalId, OtConsumableUsage usage) {
        if (usage.getItemName() == null || usage.getQuantity() == null || usage.getQuantity() <= 0) {
            return;
        }
        List<HospitalInventory> stocks = hospitalInventoryRepository.findByNameAndHospitalIdAndIsActiveTrue(usage.getItemName(), hospitalId);
        stocks.sort(Comparator.comparing(HospitalInventory::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(HospitalInventory::getId));
        int remaining = usage.getQuantity();
        for (HospitalInventory stock : stocks) {
            if (remaining <= 0) break;
            int available = stock.getStockQuantity() == null ? 0 : stock.getStockQuantity();
            int deduct = Math.min(available, remaining);
            if (deduct > 0) {
                stock.setStockQuantity(available - deduct);
                hospitalInventoryRepository.save(stock);
                remaining -= deduct;
            }
        }
        if (remaining > 0) {
            throw new RuntimeException("Insufficient inventory for " + usage.getItemName());
        }
    }

    private void addDefaultCharges(OtBooking booking) {
        addCharge(booking.getId(), charge("OT Charges", new BigDecimal("0.00"), "Default charge placeholder"));
        addCharge(booking.getId(), charge("Surgeon Fee", new BigDecimal("0.00"), null));
        addCharge(booking.getId(), charge("Anesthesia Fee", new BigDecimal("0.00"), null));
        addCharge(booking.getId(), charge("Equipment Charges", new BigDecimal("0.00"), null));
        addCharge(booking.getId(), charge("Recovery Charges", new BigDecimal("0.00"), null));
        addCharge(booking.getId(), charge("Cleaning Charges", new BigDecimal("0.00"), null));
        addCharge(booking.getId(), charge("Nursing Charges", new BigDecimal("0.00"), null));
    }

    private OtBillingChargeRequest charge(String type, BigDecimal amount, String notes) {
        OtBillingChargeRequest request = new OtBillingChargeRequest();
        request.setChargeType(type);
        request.setAmount(amount);
        request.setNotes(notes);
        return request;
    }

    private OtBooking getBooking(Long id, Long hospitalId) {
        return bookingRepository.findById(id)
                .filter(b -> hospitalId.equals(b.getHospitalId()))
                .orElseThrow(() -> new RuntimeException("OT booking not found"));
    }

    private Long hospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital context is required");
        }
        return hospitalId;
    }

    private String userEmail() {
        return securityHelper.getCurrentUserEmail();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase().replace(' ', '_');
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? fallback : normalized;
    }
}
