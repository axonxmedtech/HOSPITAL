package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Equipment extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(length = 80)
    private String serialNumber;

    @Column(nullable = false, length = 30)
    private String status = "AVAILABLE";

    @Column(length = 500)
    private String notes;
}
