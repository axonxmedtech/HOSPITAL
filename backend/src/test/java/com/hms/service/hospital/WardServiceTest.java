package com.hms.service.hospital;

import com.hms.dto.CreateWardRequest;
import com.hms.dto.WardResponse;
import com.hms.entity.Bed;
import com.hms.entity.Ward;
import com.hms.repository.BedRepository;
import com.hms.repository.NurseProfileRepository;
import com.hms.repository.WardRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers WardService.createWard — in particular the bed-count guard added for the CodeQL
 * "user-controlled data in arithmetic expression" finding (total bounded to [0, MAX_BEDS_PER_WARD],
 * bed numbers computed in long) and the sequential bed-code generation.
 */
@ExtendWith(MockitoExtension.class)
class WardServiceTest {

    @Mock WardRepository wardRepository;
    @Mock BedRepository bedRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock HospitalWebSocketHandler webSocketHandler;
    @Mock NurseProfileRepository nurseProfileRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks WardService service;

    private CreateWardRequest req(String name, Integer totalBeds) {
        CreateWardRequest r = new CreateWardRequest();
        r.setWardName(name);
        r.setBedPrice(BigDecimal.TEN);
        r.setTotalBeds(totalBeds);
        r.setFloorNumber(1);
        return r;
    }

    private Ward savedWard(Long id, String name, Integer total) {
        Ward w = new Ward();
        w.setWardId(id);
        w.setHospitalId(7L);
        w.setWardName(name);
        w.setTotalBeds(total);
        return w;
    }

    @Test
    void createWard_createsOneBedPerCount_withSequentialCodes() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(wardRepository.save(any())).thenReturn(savedWard(3L, "ICU", 3));
        when(bedRepository.findByWardIdAndHospitalId(3L, 7L)).thenReturn(List.of());
        when(bedRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WardResponse resp = service.createWard(req("ICU", 3));

        assertThat(resp.getWardName()).isEqualTo("ICU");
        ArgumentCaptor<Bed> captor = ArgumentCaptor.forClass(Bed.class);
        verify(bedRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Bed::getBedCode)
                .containsExactly("ICU-B1", "ICU-B2", "ICU-B3");
        assertThat(captor.getAllValues()).allSatisfy(b -> {
            assertThat(b.getStatus()).isEqualTo("available");
            assertThat(b.getHospitalId()).isEqualTo(7L);
            assertThat(b.getWardId()).isEqualTo(3L);
        });
    }

    @Test
    void createWard_continuesNumbering_afterExistingBeds() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(wardRepository.save(any())).thenReturn(savedWard(3L, "ICU", 2));
        Bed existing = new Bed();
        existing.setBedCode("ICU-B5");
        when(bedRepository.findByWardIdAndHospitalId(3L, 7L)).thenReturn(List.of(existing));
        when(bedRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.createWard(req("ICU", 2));

        ArgumentCaptor<Bed> captor = ArgumentCaptor.forClass(Bed.class);
        verify(bedRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Bed::getBedCode)
                .containsExactly("ICU-B6", "ICU-B7");
    }

    @Test
    void createWard_withNullTotalBeds_createsNoBeds() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(wardRepository.save(any())).thenReturn(savedWard(3L, "ICU", null));
        when(bedRepository.findByWardIdAndHospitalId(3L, 7L)).thenReturn(List.of());

        service.createWard(req("ICU", null));

        verify(bedRepository, never()).save(any());
    }

    @Test
    void getWardsForAdmission_includesAvailableWardWithoutNurseIncharge() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        Ward ward = savedWard(3L, "General", 1);
        ward.setInchargeNurseId(null);
        Bed available = new Bed();
        available.setStatus("available");
        when(wardRepository.findByHospitalId(7L)).thenReturn(List.of(ward));
        when(bedRepository.findByWardIdAndHospitalId(3L, 7L)).thenReturn(List.of(available));

        List<WardResponse> wards = service.getWardsForAdmission();

        assertThat(wards).extracting(WardResponse::getWardId).containsExactly(3L);
    }

    @Test
    void createWard_rejectsCountAboveMax_andCreatesNoBeds() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(wardRepository.save(any())).thenReturn(savedWard(3L, "ICU", 2001));

        assertThatThrownBy(() -> service.createWard(req("ICU", 2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");

        verify(bedRepository, never()).save(any());
    }

    @Test
    void createWard_rejectsNegativeCount() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(wardRepository.save(any())).thenReturn(savedWard(3L, "ICU", -1));

        assertThatThrownBy(() -> service.createWard(req("ICU", -1)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(bedRepository, never()).save(any());
    }
}
