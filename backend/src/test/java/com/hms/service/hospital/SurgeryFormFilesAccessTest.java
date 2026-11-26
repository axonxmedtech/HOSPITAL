package com.hms.service.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.SaveSurgeryFormRequest;
import com.hms.entity.Surgery;
import com.hms.entity.SurgeryForm;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.SurgeryFormRepository;
import com.hms.repository.SurgeryRepository;
import com.hms.security.NurseAccessGuard;
import com.hms.security.NurseWriteAccess;
import com.hms.security.PerformingNurseResolver;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Files & Access is enforced on the server for the 15 OT/NABH forms, not only hidden in the
 * Consent Forms tab. Off ⇒ nobody saves or signs; On ⇒ the chosen role(s) edit. The OT
 * in-charge holds no Files & Access role, so for them only the on/off switch applies.
 */
@ExtendWith(MockitoExtension.class)
class SurgeryFormFilesAccessTest {

    private static final Long HOSPITAL = 7L;

    @Mock SurgeryFormRepository formRepository;
    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock SurgeryRepository surgeryRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock NurseAccessGuard nurseAccessGuard;
    @Mock NurseWriteAccess nurseWriteAccess;
    @Mock PerformingNurseResolver performingNurseResolver;
    @Mock com.hms.service.RealtimeNotifier notifier;
    @Mock FormAccessService formAccessService;
    @org.mockito.Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks SurgeryFormService service;

    @BeforeEach
    void setUp() {
        lenient().when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL);
        lenient().when(securityHelper.getCurrentUserId()).thenReturn(42L);
        Surgery s = new Surgery();
        s.setId(1L);
        s.setHospitalId(HOSPITAL);
        s.setIpdAdmissionId(900L);
        s.setPatientId(500L);
        s.setEncounterType(Surgery.ENCOUNTER_IPD);
        lenient().when(surgeryRepository.findById(1L)).thenReturn(Optional.of(s));
        lenient().when(formRepository.findBySurgeryIdAndFormTypeAndIsCurrentTrue(any(), any())).thenReturn(Optional.empty());
        lenient().when(formRepository.save(any(SurgeryForm.class))).thenAnswer(inv -> {
            SurgeryForm f = inv.getArgument(0);
            if (f.getId() == null) f.setId(100L);
            if (f.getCreatedAt() == null) f.setCreatedAt(LocalDateTime.now());
            return f;
        });
    }

    private static SaveSurgeryFormRequest req(String type) {
        SaveSurgeryFormRequest r = new SaveSurgeryFormRequest();
        r.setSurgeryId(1L);
        r.setFormType(type);
        r.setData(Map.of("k", "v"));
        return r;
    }

    @Test
    void aDoctorCannotSaveAFormTheHospitalMadeNurseOnly_andNothingIsWritten() {
        when(securityHelper.getCurrentUserRole()).thenReturn("DOCTOR");
        doThrow(new AccessDeniedException("You do not have edit access to this form"))
                .when(formAccessService).assertCanEdit("WHO_CHECKLIST");

        assertThatThrownBy(() -> service.save(req("WHO_CHECKLIST")))
                .isInstanceOf(AccessDeniedException.class);
        verify(formRepository, never()).save(any());
    }

    @Test
    void signingIsRefusedTheSameWay() {
        when(securityHelper.getCurrentUserRole()).thenReturn("NURSE");
        doThrow(new AccessDeniedException("You do not have edit access to this form"))
                .when(formAccessService).assertCanEdit("GA_CONSENT");

        assertThatThrownBy(() -> service.sign(1L, "GA_CONSENT"))
                .isInstanceOf(AccessDeniedException.class);
        verify(formRepository, never()).save(any());
    }

    @Test
    void theOtInchargeIsCheckedOnlyAgainstTheOnOffSwitch() {
        when(securityHelper.getCurrentUserRole()).thenReturn("OT_INCHARGE");

        assertThatCode(() -> service.save(req("WHO_CHECKLIST"))).doesNotThrowAnyException();
        verify(formAccessService).assertEnabled("WHO_CHECKLIST");
        verify(formAccessService, never()).assertCanEdit(any());
    }

    @Test
    void theOtInchargeCannotSaveAFormThatIsTurnedOff() {
        when(securityHelper.getCurrentUserRole()).thenReturn("OT_INCHARGE");
        doThrow(new AccessDeniedException("This form is turned off for your hospital"))
                .when(formAccessService).assertEnabled("GA_CONSENT");

        assertThatThrownBy(() -> service.save(req("GA_CONSENT"))).isInstanceOf(AccessDeniedException.class);
        verify(formRepository, never()).save(any());
    }

    @Test
    void aFormTypeOutsideFilesAndAccessIsLeftToTheOtPermissions() {
        lenient().when(securityHelper.getCurrentUserRole()).thenReturn("NURSE");

        assertThatCode(() -> service.save(req("SOME_LOCAL_FORM"))).doesNotThrowAnyException();
        verify(formAccessService, never()).assertCanEdit(any());
        verify(formAccessService, never()).assertEnabled(any());
    }
}
