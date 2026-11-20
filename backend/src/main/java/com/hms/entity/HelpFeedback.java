package com.hms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One piece of feedback on in-app help: an answer marked helpful or not, or a question the
 * assistant could not answer. Used by AxonX to improve the help content. The question text is
 * scrubbed of numbers and email addresses before it is stored, and no name is kept with it.
 */
@Entity
@Table(name = "help_feedback")
@Data
@NoArgsConstructor
public class HelpFeedback {

    public enum Kind { HELPFUL, NOT_HELPFUL, UNANSWERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The asker's facility; null for the Super Admin. */
    @Column(name = "hospital_id")
    private Long hospitalId;

    /** Only to limit how much one login can send in a day; never shown. */
    @Column(name = "scope_key", nullable = false, length = 160)
    private String scopeKey;

    @Column(name = "role", nullable = false, length = 40)
    private String role;

    /** A {@link Kind} name. Kept as plain text so the column is a VARCHAR on every schema. */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    /** The help answer rated (HELPFUL / NOT_HELPFUL). */
    @Column(name = "entry_id", length = 60)
    private String entryId;

    /** The question asked (UNANSWERED), scrubbed. */
    @Column(name = "question", length = 200)
    private String question;

    /** The question in a comparable form, so the same question asked twice is counted together. */
    @Column(name = "normalized", length = 200)
    private String normalized;

    /** The screen the user was on. */
    @Column(name = "screen", length = 60)
    private String screen;

    /** Set when AxonX has written an answer for this question. */
    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
