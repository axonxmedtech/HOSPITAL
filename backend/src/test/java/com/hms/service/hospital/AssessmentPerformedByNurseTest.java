package com.hms.service.hospital;

import com.hms.entity.InitialAssessment;
import com.hms.entity.IpdAdmission;
import com.hms.entity.VulnerabilityAssessment;
import com.hms.repository.InitialAssessmentRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.VulnerabilityAssessmentRepository;
import com.hms.security.NurseAccessGuard;
import com.hms.security.PerformingNurseResolver;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Initial and Vulnerability Assessments credit a performing nurse, like every other nursing
 * record. The rule itself (logged-in nurse with Separate Nurse Login ON, the in-charge's choice
 * with it OFF, null for doctor / admin) lives in PerformingNurseResolver; these tests pin that
 * both saves go through it and store what it returns, and that a refused choice writes nothing.
 */
class AssessmentPerformedByNurseTest {

    private static final Long HOSPITAL = 7L;
    private static final Long ADMISSION = 900L;

    private static IpdAdmission admission() {
        IpdAdmission a = new IpdAdmission();
        a.setId(ADMISSION);
        a.setHospitalId(HOSPITAL);
        return a;
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Initial {
        @Mock InitialAssessmentRepository repository;
        @Mock IpdAdmissionRepository ipdAdmissionRepository;
        @Mock SecurityContextHelper securityHelper;
        @Mock NurseAccessGuard nurseAccessGuard;
        @Mock AuditLogService auditLogService;
        @Mock FormAccessService formAccessService;
        @Mock PerformingNurseResolver performingNurseResolver;
        @Mock com.hms.service.RealtimeNotifier notifier;
        @InjectMocks InitialAssessmentService service;

        @BeforeEach void setUp() {
            when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL);
            when(ipdAdmissionRepository.findById(ADMISSION)).thenReturn(Optional.of(admission()));
            when(repository.findByIpdAdmissionId(ADMISSION)).thenReturn(Optional.empty());
            lenient().when(repository.save(any(InitialAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test void storesTheNurseTheResolverCredits() {
            InitialAssessment incoming = new InitialAssessment();
            incoming.setPerformedByNurseId(31L);
            when(performingNurseResolver.resolve(31L)).thenReturn(31L);

            InitialAssessment saved = service.save(ADMISSION, incoming);

            assertThat(saved.getPerformedByNurseId()).isEqualTo(31L);
        }

        @Test void aMissingChoiceUnderSharedLoginIsRefused_andNothingIsSaved() {
            when(performingNurseResolver.resolve(null))
                    .thenThrow(new IllegalArgumentException("Performed By nurse is required"));

            assertThatThrownBy(() -> service.save(ADMISSION, new InitialAssessment()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Performed By");
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Vulnerability {
        @Mock VulnerabilityAssessmentRepository repository;
        @Mock IpdAdmissionRepository ipdAdmissionRepository;
        @Mock SecurityContextHelper securityHelper;
        @Mock NurseAccessGuard nurseAccessGuard;
        @Mock AuditLogService auditLogService;
        @Mock FormAccessService formAccessService;
        @Mock PerformingNurseResolver performingNurseResolver;
        @Mock com.hms.service.RealtimeNotifier notifier;
        @InjectMocks VulnerabilityAssessmentService service;

        @BeforeEach void setUp() {
            when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL);
            when(ipdAdmissionRepository.findById(ADMISSION)).thenReturn(Optional.of(admission()));
            when(repository.findByIpdAdmissionId(ADMISSION)).thenReturn(Optional.empty());
            lenient().when(repository.save(any(VulnerabilityAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test void storesTheNurseTheResolverCredits() {
            VulnerabilityAssessment incoming = new VulnerabilityAssessment();
            incoming.setPerformedByNurseId(32L);
            when(performingNurseResolver.resolve(32L)).thenReturn(32L);

            VulnerabilityAssessment saved = service.save(ADMISSION, incoming);

            assertThat(saved.getPerformedByNurseId()).isEqualTo(32L);
        }

        @Test void aDoctorEntryCreditsNoNurse() {
            when(performingNurseResolver.resolve(null)).thenReturn(null);

            VulnerabilityAssessment saved = service.save(ADMISSION, new VulnerabilityAssessment());

            assertThat(saved.getPerformedByNurseId()).isNull();
        }

        @Test void aMissingChoiceUnderSharedLoginIsRefused_andNothingIsSaved() {
            when(performingNurseResolver.resolve(null))
                    .thenThrow(new IllegalArgumentException("Performed By nurse is required"));

            assertThatThrownBy(() -> service.save(ADMISSION, new VulnerabilityAssessment()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repository, never()).save(any());
        }
    }
}
