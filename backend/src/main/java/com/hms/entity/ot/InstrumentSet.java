package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "instrument_set")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentSet extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(length = 60)
    private String setType;

    @Column(nullable = false, length = 30)
    private String status = "STERILIZED";

    @Column(length = 1000)
    private String contents;
}
