package com.hms.service.help;

import com.hms.entity.HelpFeedback;
import com.hms.entity.HelpFeedback.Kind;
import com.hms.repository.HelpFeedbackRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.UserAuthenticationDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpFeedbackServiceTest {

    private HelpFeedbackRepository repository;
    private HelpFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(HelpFeedbackRepository.class);
        SecurityContextHelper security = mock(SecurityContextHelper.class);
        when(security.getCurrentUserDetails()).thenReturn(new UserAuthenticationDetails(7L, "RECEPTIONIST", 3L, List.of()));
        service = new HelpFeedbackService(repository, security);
    }

    @Test
    void patientDetailsTypedIntoHelpAreNotStored() {
        assertThat(HelpFeedbackService.scrub("discharge ramesh 9822012345 on 12/03/2026 mail a.b@x.com"))
                .doesNotContain("9822012345").doesNotContain("12/03/2026").doesNotContain("a.b@x.com")
                .contains("[number]").contains("[email]");
        // Short numbers in ordinary questions stay readable.
        assertThat(HelpFeedbackService.scrub("print 2 copies")).isEqualTo("print 2 copies");
        assertThat(HelpFeedbackService.scrub("x".repeat(500))).hasSize(HelpFeedbackService.MAX_QUESTION);
    }

    @Test
    void theSameQuestionAskedDifferentlyIsCountedTogether() {
        assertThat(HelpFeedbackService.normalise("How do I  ADMIT a patient?"))
                .isEqualTo(HelpFeedbackService.normalise("how do i admit a patient"));
    }

    @Test
    void recordsAnUnansweredQuestionWithoutAName() {
        service.record("unanswered", null, "where is the x-ray module for 9822012345", "opd");
        ArgumentCaptor<HelpFeedback> saved = ArgumentCaptor.forClass(HelpFeedback.class);
        verify(repository).save(saved.capture());
        HelpFeedback f = saved.getValue();
        assertThat(f.getKind()).isEqualTo(Kind.UNANSWERED.name());
        assertThat(f.getQuestion()).doesNotContain("9822012345");
        assertThat(f.getRole()).isEqualTo("RECEPTIONIST");
        assertThat(f.getHospitalId()).isEqualTo(3L);
        assertThat(f.getScreen()).isEqualTo("opd");
    }

    @Test
    void ratingsNeedARealAnswerId() {
        service.record("HELPFUL", "desk-new-opd", null, null);
        verify(repository).save(any());
        assertThatThrownBy(() -> service.record("NOT_HELPFUL", "<script>", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record("LOVE_IT", "desk-new-opd", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record("UNANSWERED", null, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneLoginCannotFloodIt() {
        when(repository.countByScopeKeyAndCreatedAtAfter(anyString(), any())).thenReturn((long) HelpFeedbackService.DAILY_LIMIT);
        assertThat(service.record("HELPFUL", "desk-new-opd", null, null)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void insightsListTheMostAskedOpenQuestionsWithWhoAsked() {
        LocalDateTime now = LocalDateTime.now();
        when(repository.openQuestionsSince(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {"x ray", "x-ray?", 4L, now}));
        when(repository.rolesFor(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {"x ray", "DOCTOR"}, new Object[] {"x ray", "RECEPTIONIST"}));
        when(repository.ratingsSince(any())).thenReturn(List.<Object[]>of(
                new Object[] {"a", 5L, 0L}, new Object[] {"b", 1L, 3L}));
        Map<String, Object> out = service.insights(30);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> qs = (List<Map<String, Object>>) out.get("questions");
        assertThat(qs.get(0)).containsEntry("count", 4L);
        assertThat(qs.get(0).get("roles").toString()).isEqualTo("[DOCTOR, RECEPTIONIST]");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answers = (List<Map<String, Object>>) out.get("answers");
        assertThat(answers.get(0)).containsEntry("entryId", "b"); // least helpful first
    }
}
