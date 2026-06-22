package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ot_booking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtBooking extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "patient_uhid", length = 80)
    private String patientUhid;

    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    @Column(name = "ipd_number", length = 80)
    private String ipdNumber;

    @Column(name = "surgeon_id")
    private Long surgeonId;

    @Column(name = "assistant_surgeon_id")
    private Long assistantSurgeonId;

    @Column(name = "ot_room_id")
    private Long otRoomId;

    @Column(name = "ot_table", length = 50)
    private String otTable;

    @Column(length = 100)
    private String specialty;

    @Column(name = "procedure_name", nullable = false, length = 160)
    private String procedureName;

    @Column(length = 1000)
    private String diagnosis;

    @Column(name = "expected_duration_minutes")
    private Integer expectedDurationMinutes = 60;

    @Column(length = 30)
    private String priority = "ELECTIVE";

    @Column(name = "surgery_type", length = 30)
    private String surgeryType = "ELECTIVE";

    @Column(length = 30)
    private String status = "WAITING";

    @Column(name = "clearance_status", length = 30)
    private String clearanceStatus = "PENDING_CLEARANCE";

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "intra_op_notes", length = 4000)
    private String intraOpNotes;

    @Column(name = "post_op_orders", length = 4000)
    private String postOpOrders;

    @Column(name = "billing_id")
    private Long billingId;
}
