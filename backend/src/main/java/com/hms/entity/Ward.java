package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "wards")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ward_id")
    private Long wardId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ward_name", nullable = false)
    private String wardName;

    @Column(name = "bed_price", nullable = false)
    private java.math.BigDecimal bedPrice;

    @Column(name = "total_beds", nullable = false)
    private Integer totalBeds;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
