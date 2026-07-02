package com.hms.service;

import com.hms.dto.*;
import com.hms.entity.Employee;
import com.hms.entity.LeaveRequest;
import com.hms.entity.Payroll;
import com.hms.entity.ShiftRoster;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.HrService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HrServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private ShiftRosterRepository rosterRepository;
    @Mock private LeaveRequestRepository leaveRepository;
    @Mock private PayrollRepository payrollRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private HrService service;

    private static final Long HOSPITAL_ID = 1L;

    private Employee employee(String status, LocalDate licenseExpiry) {
        Employee e = new Employee();
        e.setId(10L);
        e.setHospitalId(HOSPITAL_ID);
        e.setUserId(50L);
        e.setEmployeeCode("EMP-1-1");
        e.setDepartment("Nursing");
        e.setDesignation("Staff Nurse");
        e.setStatus(status);
        e.setLicenseExpiry(licenseExpiry);
        return e;
    }

    // ===== BR-1: unique employee code =====

    @Test
    void onboardEmployee_generatesSequentialCode() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(employeeRepository.countByHospitalId(HOSPITAL_ID)).thenReturn(2L);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        EmployeeOnboardRequest req = new EmployeeOnboardRequest();
        req.setUserId(50L);
        req.setDepartment("Nursing");
        req.setDesignation("Staff Nurse");
        req.setJoiningDate(LocalDate.now());

        Employee saved = service.onboardEmployee(req);

        assertThat(saved.getEmployeeCode()).isEqualTo("EMP-1-3");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    // ===== BR-2: roster overlap / consecutive night gate =====

    @Test
    void createRosterSlot_rejectedWhenAlreadyScheduledSameDay() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        Employee emp = employee("ACTIVE", null);
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(emp));
        LocalDate date = LocalDate.now().plusDays(1);
        when(rosterRepository.findByHospitalIdAndEmployeeIdAndDate(HOSPITAL_ID, 10L, date))
                .thenReturn(Optional.of(new ShiftRoster()));

        ShiftRosterRequest req = new ShiftRosterRequest();
        req.setEmployeeId(10L);
        req.setDepartment("Nursing");
        req.setShift("MORNING");
        req.setDate(date);

        assertThatThrownBy(() -> service.createRosterSlot(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BR-2");
        verify(rosterRepository, never()).save(any());
    }

    @Test
    void createRosterSlot_rejectedForConsecutiveNightShifts() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        Employee emp = employee("ACTIVE", null);
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(emp));
        LocalDate date = LocalDate.now().plusDays(2);
        LocalDate previousDay = date.minusDays(1);
        when(rosterRepository.findByHospitalIdAndEmployeeIdAndDate(HOSPITAL_ID, 10L, date)).thenReturn(Optional.empty());
        ShiftRoster prevNight = new ShiftRoster();
        prevNight.setShift("NIGHT");
        when(rosterRepository.findByHospitalIdAndEmployeeIdAndDate(HOSPITAL_ID, 10L, previousDay)).thenReturn(Optional.of(prevNight));

        ShiftRosterRequest req = new ShiftRosterRequest();
        req.setEmployeeId(10L);
        req.setDepartment("Nursing");
        req.setShift("NIGHT");
        req.setDate(date);

        assertThatThrownBy(() -> service.createRosterSlot(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consecutive");
    }

    // ===== BR-4: license expiry blocks scheduling =====

    @Test
    void createRosterSlot_rejectedWhenLicenseExpired() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        Employee emp = employee("ACTIVE", LocalDate.now().minusDays(5));
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(emp));

        ShiftRosterRequest req = new ShiftRosterRequest();
        req.setEmployeeId(10L);
        req.setDepartment("Nursing");
        req.setShift("MORNING");
        req.setDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> service.createRosterSlot(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BR-4");
        verify(rosterRepository, never()).save(any());
    }

    @Test
    void createRosterSlot_succeedsForActiveUnexpiredEmployee() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        Employee emp = employee("ACTIVE", LocalDate.now().plusYears(1));
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(emp));
        LocalDate date = LocalDate.now().plusDays(1);
        when(rosterRepository.findByHospitalIdAndEmployeeIdAndDate(eq(HOSPITAL_ID), eq(10L), any())).thenReturn(Optional.empty());
        when(rosterRepository.save(any(ShiftRoster.class))).thenAnswer(i -> i.getArgument(0));

        ShiftRosterRequest req = new ShiftRosterRequest();
        req.setEmployeeId(10L);
        req.setDepartment("Nursing");
        req.setShift("morning");
        req.setDate(date);

        ShiftRoster saved = service.createRosterSlot(req);

        assertThat(saved.getShift()).isEqualTo("MORNING");
        assertThat(saved.getStatus()).isEqualTo("SCHEDULED");
    }

    // ===== BR-3: leave approval updates roster =====

    @Test
    void approveLeave_marksEmployeeOnLeaveAndCancelsOverlappingRoster() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserId()).thenReturn(99L);
        LeaveRequest leave = new LeaveRequest();
        leave.setId(4L);
        leave.setHospitalId(HOSPITAL_ID);
        leave.setEmployeeId(10L);
        leave.setStartDate(LocalDate.now());
        leave.setEndDate(LocalDate.now().plusDays(2));
        leave.setStatus("PENDING");
        when(leaveRepository.findByIdAndHospitalId(4L, HOSPITAL_ID)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        Employee emp = employee("ACTIVE", null);
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(emp));

        ShiftRoster overlapping = new ShiftRoster();
        overlapping.setStatus("SCHEDULED");
        when(rosterRepository.findByHospitalIdAndEmployeeIdAndDateBetween(HOSPITAL_ID, 10L, leave.getStartDate(), leave.getEndDate()))
                .thenReturn(List.of(overlapping));

        LeaveApprovalRequest req = new LeaveApprovalRequest();
        req.setStatus("APPROVED");

        LeaveRequest saved = service.approveLeave(4L, req);

        assertThat(saved.getStatus()).isEqualTo("APPROVED");
        assertThat(emp.getStatus()).isEqualTo("ON_LEAVE");
        assertThat(overlapping.getStatus()).isEqualTo("ON_LEAVE");
        verify(rosterRepository).save(overlapping);
    }

    @Test
    void approveLeave_rejectedWhenAlreadyDecided() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        LeaveRequest leave = new LeaveRequest();
        leave.setId(4L);
        leave.setHospitalId(HOSPITAL_ID);
        leave.setStatus("APPROVED");
        when(leaveRepository.findByIdAndHospitalId(4L, HOSPITAL_ID)).thenReturn(Optional.of(leave));

        LeaveApprovalRequest req = new LeaveApprovalRequest();
        req.setStatus("REJECTED");

        assertThatThrownBy(() -> service.approveLeave(4L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been");
    }

    // ===== payroll =====

    @Test
    void processPayroll_rejectedForDuplicateMonth() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        Employee emp = employee("ACTIVE", null);
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(emp));
        when(payrollRepository.findByHospitalIdAndEmployeeIdAndSalaryMonth(HOSPITAL_ID, 10L, "2026-06"))
                .thenReturn(Optional.of(new Payroll()));

        PayrollProcessRequest.PayrollLineItem item = new PayrollProcessRequest.PayrollLineItem();
        item.setEmployeeId(10L);
        item.setGrossSalary(new BigDecimal("50000"));
        PayrollProcessRequest req = new PayrollProcessRequest();
        req.setSalaryMonth("2026-06");
        req.setEntries(List.of(item));

        assertThatThrownBy(() -> service.processPayroll(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been processed");
        verify(payrollRepository, never()).save(any());
    }

    @Test
    void processPayroll_computesNetSalary() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("hr@hospital.com");
        Employee emp = employee("ACTIVE", null);
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(emp));
        when(payrollRepository.findByHospitalIdAndEmployeeIdAndSalaryMonth(HOSPITAL_ID, 10L, "2026-06")).thenReturn(Optional.empty());
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(i -> i.getArgument(0));

        PayrollProcessRequest.PayrollLineItem item = new PayrollProcessRequest.PayrollLineItem();
        item.setEmployeeId(10L);
        item.setGrossSalary(new BigDecimal("50000"));
        item.setDeductions(new BigDecimal("5000"));
        PayrollProcessRequest req = new PayrollProcessRequest();
        req.setSalaryMonth("2026-06");
        req.setEntries(List.of(item));

        List<Payroll> results = service.processPayroll(req);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNetSalary()).isEqualByComparingTo("45000");
    }
}
