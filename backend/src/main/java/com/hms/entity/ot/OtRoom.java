package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ot_room")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtRoom extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "room_code", length = 50)
    private String roomCode;

    @Column(name = "table_count")
    private Integer tableCount = 1;

    @Column(nullable = false, length = 30)
    private String status = "AVAILABLE";

    @Column(length = 500)
    private String notes;
}
