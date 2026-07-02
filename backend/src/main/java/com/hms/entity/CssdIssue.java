package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Checkout log tracking sterile tray dispatch to a clinical area (Form 35 core). */
@Entity
@Table(name = "cssd_issue")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CssdIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "tray_id", nullable = false)
    private Long trayId;

    @Column(name = "issued_to_department", nullable = false, length = 50)
    private String issuedToDepartment;

    @Column(name = "issued_by_name", length = 150)
    private String issuedByName;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "issue_time", nullable = false)
    private LocalDateTime issueTime;
}
