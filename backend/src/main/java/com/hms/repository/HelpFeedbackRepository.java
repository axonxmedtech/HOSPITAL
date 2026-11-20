package com.hms.repository;

import com.hms.entity.HelpFeedback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HelpFeedbackRepository extends JpaRepository<HelpFeedback, Long> {

    long countByScopeKeyAndCreatedAtAfter(String scopeKey, LocalDateTime since);

    /** Per answer: [entryId, helpful, notHelpful]. */
    @Query("SELECT f.entryId, "
            + "SUM(CASE WHEN f.kind = 'HELPFUL' THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN f.kind = 'NOT_HELPFUL' THEN 1 ELSE 0 END) "
            + "FROM HelpFeedback f WHERE f.entryId IS NOT NULL AND f.createdAt >= :since GROUP BY f.entryId")
    List<Object[]> ratingsSince(@Param("since") LocalDateTime since);

    /** Open unanswered questions, most asked first: [normalized, sampleQuestion, count, lastAsked]. */
    @Query("SELECT f.normalized, MAX(f.question), COUNT(f), MAX(f.createdAt) FROM HelpFeedback f "
            + "WHERE f.kind = 'UNANSWERED' AND f.resolved = false "
            + "AND f.createdAt >= :since AND f.normalized IS NOT NULL "
            + "GROUP BY f.normalized ORDER BY COUNT(f) DESC, MAX(f.createdAt) DESC")
    List<Object[]> openQuestionsSince(@Param("since") LocalDateTime since, Pageable page);

    /** Which roles asked each open question: [normalized, role]. */
    @Query("SELECT DISTINCT f.normalized, f.role FROM HelpFeedback f "
            + "WHERE f.kind = 'UNANSWERED' AND f.resolved = false "
            + "AND f.createdAt >= :since AND f.normalized IN :questions")
    List<Object[]> rolesFor(@Param("since") LocalDateTime since, @Param("questions") List<String> questions);

    @Modifying
    @Query("UPDATE HelpFeedback f SET f.resolved = true "
            + "WHERE f.kind = 'UNANSWERED' AND f.normalized = :normalized")
    int resolve(@Param("normalized") String normalized);
}
