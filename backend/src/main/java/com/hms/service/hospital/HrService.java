package com.hms.service.hospital;

import com.hms.dto.*;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Human Resources & Workforce Management (Form 39 core). Covers: employee onboarding
 * (BR-1 unique code), shift rostering with an overlap/consecutive-night gate (BR-2) and a
 * license-expiry scheduling block (BR-4), leave request/approval that auto-updates the
 * roster (BR-3), and monthly payroll compilation. BR-6: employees are only ever exited,
 * never deleted. BR-7: every method is tenant-guarded.
 *
 * Scope note: biometric attendance capture/locking (BR-5) is deferred — payroll here is
 * compiled directly from HR-submitted line items rather than gated on a biometric lock,
 * since no attendance hardware integration exists yet.
 */
@Service
public class HrService {

    private static final Logger log = LoggerFactory.getLogger(HrService.class);

    private static final Set<String> VALID_SHIFTS = Set.of("MORNING", "EVENING", "NIGHT", "ON_CALL");
    private static final Set<String> VALID_LEAVE_TYPES = Set.of("CASUAL", "SICK", "EARNED", "MATERNITY");

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ShiftRosterRepository rosterRepository;
    @Autowired private LeaveRequestRepository leaveRepository;
    @Autowired private PayrollRepository payrollRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;

    @Transactional
    public Employee onboardEmployee(EmployeeOnboardRequest request) {
        Long hospitalId = requireHospital();
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("A linked user account is required");
        }
        if (request.getDepartment() == null || request.getDepartment().isBlank()) {
            throw new IllegalArgumentException("Department is required");
        }
        if (request.getDesignation() == null || request.getDesignation().isBlank()) {
            throw new IllegalArgumentException("Designation is required");
        }
        if (request.getJoiningDate() == null) {
            throw new IllegalArgumentException("Joining date is required");
        }

        Employee employee = new Employee();
        employee.setHospitalId(hospitalId);
        employee.setUserId(request.getUserId());
        employee.setEmployeeCode("EMP-" + hospitalId + "-" + (employeeRepository.countByHospitalId(hospitalId) + 1));
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setJoiningDate(request.getJoiningDate());
        employee.setLicenseNumber(request.getLicenseNumber());
        employee.setLicenseExpiry(request.getLicenseExpiry());
        employee.setStatus("ACTIVE");
        Employee saved = employeeRepository.save(employee);
        audit("HR_EMPLOYEE_ONBOARDED", "Employee " + saved.getEmployeeCode() + " onboarded (" + saved.getDesignation() + ")", hospitalId);
        return saved;
    }

    public List<Employee> getEmployees() {
        return employeeRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    /** BR-6: exited staff are archived, never deleted. */
    @Transactional
    public Employee exitEmployee(Long employeeId) {
        Long hospitalId = requireHospital();
        Employee employee = employeeRepository.findByIdAndHospitalId(employeeId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if ("EXITED".equals(employee.getStatus())) {
            throw new IllegalStateException("Employee has already exited");
        }
        employee.setStatus("EXITED");
        Employee saved = employeeRepository.save(employee);
        audit("HR_EMPLOYEE_EXITED", "Employee " + saved.getEmployeeCode() + " exited", hospitalId);
        return saved;
    }

    /**
     * BR-2: rejects a second shift for the same employee/day and consecutive night duties.
     * BR-4: rejects scheduling a clinician whose professional license has expired.
     */
    @Transactional
    public ShiftRoster createRosterSlot(ShiftRosterRequest request) {
        Long hospitalId = requireHospital();
        Employee employee = employeeRepository.findByIdAndHospitalId(request.getEmployeeId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new IllegalStateException("Cannot schedule an employee with status " + employee.getStatus());
        }
        if (employee.getLicenseExpiry() != null && employee.getLicenseExpiry().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Cannot schedule " + employee.getEmployeeCode()
                    + ": professional license expired on " + employee.getLicenseExpiry() + " (BR-4)");
        }
        String shift = request.getShift() == null ? "" : request.getShift().toUpperCase();
        if (!VALID_SHIFTS.contains(shift)) {
            throw new IllegalArgumentException("Shift must be one of " + VALID_SHIFTS);
        }
        if (request.getDate() == null) {
            throw new IllegalArgumentException("Roster date is required");
        }
        if (request.getDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Roster date cannot be in the past");
        }

        if (rosterRepository.findByHospitalIdAndEmployeeIdAndDate(hospitalId, employee.getId(), request.getDate()).isPresent()) {
            throw new IllegalStateException("Employee already has a shift scheduled on " + request.getDate() + " (BR-2)");
        }
        if ("NIGHT".equals(shift)) {
            LocalDate previousDay = request.getDate().minusDays(1);
            rosterRepository.findByHospitalIdAndEmployeeIdAndDate(hospitalId, employee.getId(), previousDay)
                    .filter(prev -> "NIGHT".equals(prev.getShift()))
                    .ifPresent(prev -> {
                        throw new IllegalStateException("Employee already worked a NIGHT shift the previous day — consecutive night rotations are not allowed (BR-2)");
                    });
        }

        ShiftRoster slot = new ShiftRoster();
        slot.setHospitalId(hospitalId);
        slot.setEmployeeId(employee.getId());
        slot.setDepartment(request.getDepartment());
        slot.setShift(shift);
        slot.setDate(request.getDate());
        slot.setStatus("SCHEDULED");
        ShiftRoster saved = rosterRepository.save(slot);
        audit("HR_ROSTER_SCHEDULED", employee.getEmployeeCode() + " scheduled " + shift + " on " + request.getDate(), hospitalId);
        return saved;
    }

    public List<ShiftRoster> getRoster() {
        return rosterRepository.findByHospitalIdOrderByDateDesc(requireHospital());
    }

    /** Employee self-service leave submission, resolved against the caller's own employee profile. */
    @Transactional
    public LeaveRequest requestLeave(LeaveRequestSubmitRequest request) {
        Long hospitalId = requireHospital();
        Employee employee = employeeRepository.findByHospitalIdAndUserId(hospitalId, securityHelper.getCurrentUserId())
                .orElseThrow(() -> new IllegalStateException("No employee profile is linked to your account"));

        String leaveType = request.getLeaveType() == null ? "" : request.getLeaveType().toUpperCase();
        if (!VALID_LEAVE_TYPES.contains(leaveType)) {
            throw new IllegalArgumentException("Leave type must be one of " + VALID_LEAVE_TYPES);
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must not be before start date");
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setHospitalId(hospitalId);
        leave.setEmployeeId(employee.getId());
        leave.setLeaveType(leaveType);
        leave.setStartDate(request.getStartDate());
        leave.setEndDate(request.getEndDate());
        leave.setStatus("PENDING");
        LeaveRequest saved = leaveRepository.save(leave);
        audit("HR_LEAVE_REQUESTED", employee.getEmployeeCode() + " requested " + leaveType
                + " leave " + request.getStartDate() + " to " + request.getEndDate(), hospitalId);
        return saved;
    }

    /**
     * BR-3: approving a leave marks the employee ON_LEAVE and cancels any roster slots
     * already scheduled within the leave window so a coordinator can arrange coverage.
     */
    @Transactional
    public LeaveRequest approveLeave(Long leaveId, LeaveApprovalRequest request) {
        Long hospitalId = requireHospital();
        LeaveRequest leave = leaveRepository.findByIdAndHospitalId(leaveId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));
        if (!"PENDING".equals(leave.getStatus())) {
            throw new IllegalStateException("Leave request has already been " + leave.getStatus().toLowerCase());
        }
        String status = request.getStatus() == null ? "" : request.getStatus().toUpperCase();
        if (!status.equals("APPROVED") && !status.equals("REJECTED")) {
            throw new IllegalArgumentException("Status must be APPROVED or REJECTED");
        }

        leave.setStatus(status);
        leave.setApprovedBy(securityHelper.getCurrentUserId());
        LeaveRequest saved = leaveRepository.save(leave);

        if ("APPROVED".equals(status)) {
            employeeRepository.findByIdAndHospitalId(leave.getEmployeeId(), hospitalId).ifPresent(employee -> {
                if (!"EXITED".equals(employee.getStatus())) {
                    employee.setStatus("ON_LEAVE");
                    employeeRepository.save(employee);
                }
            });
            List<ShiftRoster> overlapping = rosterRepository.findByHospitalIdAndEmployeeIdAndDateBetween(
                    hospitalId, leave.getEmployeeId(), leave.getStartDate(), leave.getEndDate());
            for (ShiftRoster slot : overlapping) {
                slot.setStatus("ON_LEAVE");
                rosterRepository.save(slot);
            }
            audit("HR_LEAVE_APPROVED", "Leave #" + leave.getId() + " approved — " + overlapping.size() + " roster slot(s) marked ON_LEAVE", hospitalId);
        } else {
            audit("HR_LEAVE_REJECTED", "Leave #" + leave.getId() + " rejected", hospitalId);
        }

        return saved;
    }

    public List<LeaveRequest> getLeaveRequests() {
        return leaveRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    /** Compiles a monthly payroll run from HR-submitted gross/deduction line items. */
    @Transactional
    public List<Payroll> processPayroll(PayrollProcessRequest request) {
        Long hospitalId = requireHospital();
        if (request.getSalaryMonth() == null || !request.getSalaryMonth().matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Salary month must be in YYYY-MM format");
        }
        if (request.getEntries() == null || request.getEntries().isEmpty()) {
            throw new IllegalArgumentException("At least one payroll line item is required");
        }

        List<Payroll> results = new java.util.ArrayList<>();
        for (PayrollProcessRequest.PayrollLineItem item : request.getEntries()) {
            Employee employee = employeeRepository.findByIdAndHospitalId(item.getEmployeeId(), hospitalId)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + item.getEmployeeId()));
            if ("EXITED".equals(employee.getStatus())) {
                throw new IllegalStateException("Cannot process payroll for exited employee " + employee.getEmployeeCode());
            }
            if (item.getGrossSalary() == null || item.getGrossSalary().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Gross salary must be positive for " + employee.getEmployeeCode());
            }
            if (payrollRepository.findByHospitalIdAndEmployeeIdAndSalaryMonth(hospitalId, employee.getId(), request.getSalaryMonth()).isPresent()) {
                throw new IllegalStateException("Payroll for " + employee.getEmployeeCode() + " has already been processed for " + request.getSalaryMonth());
            }

            BigDecimal deductions = item.getDeductions() == null ? BigDecimal.ZERO : item.getDeductions();
            Payroll payroll = new Payroll();
            payroll.setHospitalId(hospitalId);
            payroll.setEmployeeId(employee.getId());
            payroll.setSalaryMonth(request.getSalaryMonth());
            payroll.setGrossSalary(item.getGrossSalary());
            payroll.setDeductions(deductions);
            payroll.setNetSalary(item.getGrossSalary().subtract(deductions));
            results.add(payrollRepository.save(payroll));
        }

        audit("HR_PAYROLL_PROCESSED", "Payroll processed for " + request.getSalaryMonth() + " — " + results.size() + " employee(s)", hospitalId);
        return results;
    }

    public List<Payroll> getPayroll() {
        return payrollRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    // ===== Helpers =====

    private Long requireHospital() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return hospitalId;
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details);
            entry.setPerformedBy(securityHelper.getCurrentUserEmail());
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
