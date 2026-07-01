package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DailyFluidBalance - Cache/Aggregated daily fluid summary.
 * Tracks total daily intake, total daily output, and calculated net balance.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "daily_fluid_balance", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"admission_id", "balance_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyFluidBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "balance_date", nullable = false)
    private LocalDate balanceDate;

    @Column(name = "total_intake", nullable = false)
    private Integer totalIntake = 0;

    @Column(name = "total_output", nullable = false)
    private Integer totalOutput = 0;

    @Column(name = "net_balance", nullable = false)
    private Integer netBalance = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
        this.netBalance = this.totalIntake - this.totalOutput;
    }
}
