package com.hms.dto;

import lombok.Data;

@Data
public class BedResponse {
    private Long bedId;
    private String bedCode;
    private String status;
    /** When the bed entered its current status (from the bed status audit), if recorded. */
    private java.time.LocalDateTime statusSince;

    /** A hold in force on this bed for a planned admission, if any. */
    private String reservationId;
    private String reservedFor;
    private Long reservedPatientId;
    private java.time.LocalDateTime reservedUntil;

    /** Copies a hold onto the response (no-op for null). */
    public void applyHold(com.hms.entity.BedReservation hold) {
        if (hold == null) return;
        this.reservationId = hold.getPublicId();
        this.reservedFor = hold.getPatientName();
        this.reservedPatientId = hold.getPatientId();
        this.reservedUntil = hold.getReservedUntil();
    }
}
