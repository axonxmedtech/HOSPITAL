package com.hms.service.help;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.HelpProgress;
import com.hms.repository.HelpProgressRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.UserAuthenticationDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpProgressServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HelpProgressRepository repository;
    private SecurityContextHelper security;
    private HelpProgressService service;

    @BeforeEach
    void setUp() {
        repository = mock(HelpProgressRepository.class);
        security = mock(SecurityContextHelper.class);
        service = new HelpProgressService(repository, security, mapper);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void signedInAs(Long userId, String role, Long hospitalId) {
        when(security.getCurrentUserDetails())
                .thenReturn(new UserAuthenticationDetails(userId, role, hospitalId, List.of()));
    }

    @Test
    void eachUserHasTheirOwnKeyFromTheToken() {
        assertThat(HelpProgressService.scopeKey(new UserAuthenticationDetails(7L, "NURSE", 3L, List.of())))
                .isEqualTo("NURSE:3:-:7");
        assertThat(HelpProgressService.scopeKey(new UserAuthenticationDetails(1L, "SUPER_ADMIN", null, List.of())))
                .isEqualTo("SUPER_ADMIN:P:-:1");
        // The same numeric id in another role or facility is a different person.
        assertThat(HelpProgressService.scopeKey(new UserAuthenticationDetails(7L, "DOCTOR", 3L, List.of())))
                .isNotEqualTo("NURSE:3:-:7");
    }

    @Test
    void nothingSavedYetReadsAsEmpty() {
        signedInAs(7L, "NURSE", 3L);
        when(repository.findByScopeKey("NURSE:3:-:7")).thenReturn(Optional.empty());
        assertThat(service.read().isObject()).isTrue();
        assertThat(service.read().size()).isZero();
    }

    @Test
    void savesAndReadsBackTheCallersOwnRow() throws Exception {
        signedInAs(7L, "NURSE", 3L);
        when(repository.findByScopeKey("NURSE:3:-:7")).thenReturn(Optional.empty());
        JsonNode data = mapper.readTree("{\"guides\":{\"first-nurse\":[\"patients\"]},\"tours\":{\"basics\":true}}");
        service.save(data);

        ArgumentCaptor<HelpProgress> saved = ArgumentCaptor.forClass(HelpProgress.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getScopeKey()).isEqualTo("NURSE:3:-:7");
        assertThat(saved.getValue().getHospitalId()).isEqualTo(3L);

        when(repository.findByScopeKey("NURSE:3:-:7")).thenReturn(Optional.of(saved.getValue()));
        assertThat(service.read()).isEqualTo(data);
    }

    @Test
    void refusesAnythingButASmallJsonObject() {
        signedInAs(7L, "NURSE", 3L);
        assertThatThrownBy(() -> service.save(mapper.createArrayNode()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(null)).isInstanceOf(IllegalArgumentException.class);
        String huge = "x".repeat(HelpProgressService.MAX_BYTES);
        assertThatThrownBy(() -> service.save(mapper.createObjectNode().put("k", huge)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void aDamagedRowReadsAsEmptyInsteadOfFailing() {
        signedInAs(7L, "NURSE", 3L);
        HelpProgress row = new HelpProgress();
        row.setData("not json");
        when(repository.findByScopeKey("NURSE:3:-:7")).thenReturn(Optional.of(row));
        assertThat(service.read().size()).isZero();
    }
}
