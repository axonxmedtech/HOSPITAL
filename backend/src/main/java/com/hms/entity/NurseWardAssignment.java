package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nurse_ward_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"nurse_id", "ward_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NurseWardAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nurse_id", nullable = false)
    private Long nurseId;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;
}
