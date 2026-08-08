package com.hms.service.hospital;

import com.hms.dto.IcuStayResponse;
import com.hms.entity.IpdBedHistory;
import com.hms.entity.Ward;
import com.hms.entity.WardType;
import com.hms.repository.IpdBedHistoryRepository;
import com.hms.repository.WardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ICU stays are derived from the bed history rather than stored again — every transfer already
 * writes an ipd_bed_history row with the ward, bed and in/out times, so a second table would be a
 * copy that can disagree with the first.
 */
@ExtendWith(MockitoExtension.class)
class IcuStayServiceTest {

    @Mock IpdBedHistoryRepository bedHistoryRepository;
    @Mock WardRepository wardRepository;

    @InjectMocks IcuStayService service;

    private IpdBedHistory stint(Long wardId, LocalDateTime from, LocalDateTime to) {
        IpdBedHistory h = new IpdBedHistory();
        h.setIpdAdmissionId(412L);
        h.setWardId(wardId);
        h.setBedId(3L);
        h.setAssignedAt(from);
        h.setReleasedAt(to);
        return h;
    }

    private Ward ward(Long id, String name, WardType type) {
        Ward w = new Ward();
        w.setWardId(id);
        w.setWardName(name);
        w.setWardType(type);
        return w;
    }

    @Test
    void returnsOnlyTheStintsSpentInAnIcuWard() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(1L, LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 4, 9, 0)),
                stint(2L, LocalDateTime.of(2026, 8, 4, 9, 0), LocalDateTime.of(2026, 8, 9, 11, 0)),
                stint(1L, LocalDateTime.of(2026, 8, 9, 11, 0), null)));
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward(1L, "Ward A", WardType.IPD)));
        when(wardRepository.findById(2L)).thenReturn(Optional.of(ward(2L, "ICU-1", WardType.ICU)));

        List<IcuStayResponse> stays = service.forAdmission(412L);

        assertThat(stays).hasSize(1);
        assertThat(stays.get(0).wardName()).isEqualTo("ICU-1");
        assertThat(stays.get(0).nights()).isEqualTo(5);
        assertThat(stays.get(0).current()).isFalse();
    }

    /** A patient still in ICU has no release time; the stay is open, not zero-length. */
    @Test
    void anOngoingIcuStayHasNoEndAndIsMarkedCurrent() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(2L, LocalDateTime.now().minusDays(6), null)));
        when(wardRepository.findById(2L)).thenReturn(Optional.of(ward(2L, "ICU-1", WardType.ICU)));

        List<IcuStayResponse> stays = service.forAdmission(412L);

        assertThat(stays).hasSize(1);
        assertThat(stays.get(0).releasedAt()).isNull();
        assertThat(stays.get(0).current()).isTrue();
        // Measured to now rather than reported as zero — six days in ICU is not "0 nights".
        assertThat(stays.get(0).nights()).isEqualTo(6);
    }

    @Test
    void anAdmissionThatNeverWentToIcuHasNoStays() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(1L, LocalDateTime.of(2026, 8, 1, 10, 0), null)));
        when(wardRepository.findById(1L)).thenReturn(Optional.of(ward(1L, "Ward A", WardType.IPD)));

        assertThat(service.forAdmission(412L)).isEmpty();
    }

    /** Several stints in one ward must not mean several lookups of that ward. */
    @Test
    void looksEachWardUpOnceHoweverManyStintsUseIt() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(2L, LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 2, 10, 0)),
                stint(2L, LocalDateTime.of(2026, 8, 4, 9, 0), LocalDateTime.of(2026, 8, 6, 9, 0)),
                stint(2L, LocalDateTime.of(2026, 8, 8, 9, 0), null)));
        when(wardRepository.findById(2L)).thenReturn(Optional.of(ward(2L, "ICU-1", WardType.ICU)));

        assertThat(service.forAdmission(412L)).hasSize(3);
        verify(wardRepository, times(1)).findById(2L);
    }

    /** A history row whose ward has since been deleted must be skipped, not crash the panel. */
    @Test
    void skipsAStintWhoseWardNoLongerExists() {
        when(bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(412L)).thenReturn(List.of(
                stint(99L, LocalDateTime.of(2026, 8, 1, 10, 0), null)));
        when(wardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.forAdmission(412L)).isEmpty();
    }

    @Test
    void aNullAdmissionIdYieldsNoStaysRatherThanQueryingForThem() {
        assertThat(service.forAdmission(null)).isEmpty();
    }
}
