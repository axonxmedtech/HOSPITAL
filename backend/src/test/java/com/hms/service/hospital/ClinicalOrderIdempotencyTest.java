package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.LabOrder;
import com.hms.entity.RequestIdempotency;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.repository.PatientNurseAssignmentRepository;
import com.hms.repository.RequestIdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A lab order sent twice (double-click, resend after a lost response) orders the tests once; the
 * retry is answered with the tests already ordered. A different key is a new order.
 */
@ExtendWith(MockitoExtension.class)
class ClinicalOrderIdempotencyTest {

    @Mock LabOrderRepository labOrders;
    @Mock MedicalRecordRepository medicalRecords;
    @Mock PatientNurseAssignmentRepository nurseAssignments;
    @Mock NotificationService notificationService;
    @Mock IpdCareAccess access;
    @Mock com.hms.service.AuditLogService auditLogService;
    @Mock RequestIdempotencyRepository idemRepo;

    private IpdInvestigationService service;
    private final List<LabOrder> table = new ArrayList<>();
    private final List<RequestIdempotency> keys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new IpdInvestigationService(labOrders, medicalRecords, nurseAssignments, notificationService,
                access, auditLogService, new RequestIdempotencyService(idemRepo));
        IpdAdmission a = new IpdAdmission();
        a.setId(900L);
        a.setHospitalId(7L);
        a.setPatientId(500L);
        a.setIpdNumber("IPD-9");
        when(access.requireOrderableForUpdate(900L)).thenReturn(a);
        lenient().when(medicalRecords.findByIpdAdmissionIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        lenient().when(medicalRecords.save(any())).thenAnswer(inv -> {
            com.hms.entity.MedicalRecord r = inv.getArgument(0);
            r.setId(77L);
            return r;
        });
        lenient().when(nurseAssignments.findByIpdAdmissionIdAndIsActiveTrue(anyLong())).thenReturn(Optional.empty());
        lenient().when(labOrders.save(any(LabOrder.class))).thenAnswer(inv -> {
            LabOrder o = inv.getArgument(0);
            o.setId(100L + table.size());
            table.add(o);
            return o;
        });
        lenient().when(labOrders.findAllById(any())).thenAnswer(inv -> {
            List<Long> ids = new ArrayList<>();
            ((Iterable<Long>) inv.getArgument(0)).forEach(ids::add);
            return table.stream().filter(o -> ids.contains(o.getId())).toList();
        });
        lenient().when(idemRepo.save(any(RequestIdempotency.class))).thenAnswer(inv -> {
            keys.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(idemRepo.findByHospitalIdAndScopeAndIdempotencyKey(any(), any(), any())).thenAnswer(inv ->
                keys.stream().filter(k -> k.getHospitalId().equals(inv.getArgument(0))
                        && k.getScope().equals(inv.getArgument(1))
                        && k.getIdempotencyKey().equals(inv.getArgument(2))).findFirst());
    }

    @Test
    void aRetriedOrderOrdersTheTestsOnce_andAnswersWithTheSameTests() {
        List<Map<String, Object>> first = service.order(900L, List.of("CBC", "LFT"), "ROUTINE", null, "lab-abc");
        List<Map<String, Object>> retry = service.order(900L, List.of("CBC", "LFT"), "ROUTINE", null, "lab-abc");

        assertThat(table).as("two tests ordered, once").hasSize(2);
        assertThat(retry).hasSize(2);
        assertThat(retry).extracting(m -> m.get("testName")).containsExactlyElementsOf(
                first.stream().map(m -> m.get("testName")).toList());
    }

    @Test
    void aDifferentKeyIsANewOrder_andNoKeyBehavesAsBefore() {
        service.order(900L, List.of("CBC"), "ROUTINE", null, "lab-1");
        service.order(900L, List.of("CBC"), "ROUTINE", null, "lab-2");
        service.order(900L, List.of("CBC"), "ROUTINE", null, null);

        assertThat(table).hasSize(3);
    }

    @Test
    void aMalformedKeyIsRefused() {
        assertThatThrownBy(() -> service.order(900L, List.of("CBC"), "ROUTINE", null, "bad key!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(table).isEmpty();
    }
}
