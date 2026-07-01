package com.hms.controller.hospital;

import com.hms.dto.AnaesthesiaRecordRequest;
import com.hms.dto.ClinicalHandoverRequest;
import com.hms.dto.OperationRecordRequest;
import com.hms.dto.OtBookingRequest;
import com.hms.dto.OtChecklistRequest;
import com.hms.dto.PacuRecordRequest;
import com.hms.dto.PostopOrdersRequest;
import com.hms.service.hospital.OtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ipd/{admissionId}/ot/bookings")
public class OtController {

    @Autowired
    private OtService otService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getBookings(@PathVariable Long admissionId) {
        try {
            return ResponseEntity.ok(otService.getBookingsForAdmission(admissionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> scheduleBooking(@PathVariable Long admissionId,
                                             @RequestBody OtBookingRequest request) {
        try {
            return ResponseEntity.ok(otService.scheduleBooking(admissionId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{bookingId}/checklist")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getChecklist(@PathVariable Long admissionId,
                                          @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.getChecklist(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/status")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long admissionId,
                                          @PathVariable Long bookingId,
                                          @RequestParam String status) {
        try {
            return ResponseEntity.ok(otService.updateStatus(bookingId, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/checklist")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> signChecklist(@PathVariable Long admissionId,
                                           @PathVariable Long bookingId,
                                           @RequestBody OtChecklistRequest request) {
        try {
            return ResponseEntity.ok(otService.signChecklist(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== Operation Record (Form 18) =====

    @GetMapping("/{bookingId}/operation-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOperationRecord(@PathVariable Long admissionId,
                                                @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.getOperationRecord(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/operation-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createOperationRecord(@PathVariable Long admissionId,
                                                   @PathVariable Long bookingId,
                                                   @RequestBody OperationRecordRequest request) {
        try {
            return ResponseEntity.ok(otService.createOperationRecord(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/operation-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateOperationRecord(@PathVariable Long admissionId,
                                                   @PathVariable Long bookingId,
                                                   @RequestBody OperationRecordRequest request) {
        try {
            return ResponseEntity.ok(otService.updateOperationRecord(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/operation-record/finalize")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> finalizeOperationRecord(@PathVariable Long admissionId,
                                                     @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.finalizeOperationRecord(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== Anaesthesia Record (Form 19) =====

    @GetMapping("/{bookingId}/anaesthesia-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getAnaesthesiaRecord(@PathVariable Long admissionId,
                                                  @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.getAnaesthesiaRecord(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/anaesthesia-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> startAnaesthesiaRecord(@PathVariable Long admissionId,
                                                    @PathVariable Long bookingId,
                                                    @RequestBody AnaesthesiaRecordRequest request) {
        try {
            return ResponseEntity.ok(otService.startAnaesthesiaRecord(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/anaesthesia-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateAnaesthesiaRecord(@PathVariable Long admissionId,
                                                     @PathVariable Long bookingId,
                                                     @RequestBody AnaesthesiaRecordRequest request) {
        try {
            return ResponseEntity.ok(otService.updateAnaesthesiaRecord(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/anaesthesia-record/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> completeAnaesthesiaRecord(@PathVariable Long admissionId,
                                                       @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.completeAnaesthesiaRecord(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== PACU / Recovery Record (Form 20) =====

    @GetMapping("/{bookingId}/pacu-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPacuRecord(@PathVariable Long admissionId,
                                           @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.getPacuRecord(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/pacu-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> startPacuRecord(@PathVariable Long admissionId,
                                             @PathVariable Long bookingId,
                                             @RequestBody PacuRecordRequest request) {
        try {
            return ResponseEntity.ok(otService.startPacuRecord(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/pacu-record")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updatePacuRecord(@PathVariable Long admissionId,
                                              @PathVariable Long bookingId,
                                              @RequestBody PacuRecordRequest request) {
        try {
            return ResponseEntity.ok(otService.updatePacuRecord(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/pacu-record/transfer")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> transferPacuRecord(@PathVariable Long admissionId,
                                                @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.transferPacuRecord(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== Clinical Handover (Form 22) =====

    @GetMapping("/{bookingId}/handover")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getClinicalHandover(@PathVariable Long admissionId,
                                                 @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.getClinicalHandover(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/handover")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> initiateHandover(@PathVariable Long admissionId,
                                              @PathVariable Long bookingId,
                                              @RequestBody ClinicalHandoverRequest request) {
        try {
            return ResponseEntity.ok(otService.initiateHandover(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/handover")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateHandover(@PathVariable Long admissionId,
                                            @PathVariable Long bookingId,
                                            @RequestBody ClinicalHandoverRequest request) {
        try {
            return ResponseEntity.ok(otService.updateHandover(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/handover/accept")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> acceptHandover(@PathVariable Long admissionId,
                                            @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.acceptHandover(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ===== Post-operative Orders (Form 21) =====

    @GetMapping("/{bookingId}/postop-orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPostopOrders(@PathVariable Long admissionId,
                                             @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.getPostopOrders(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/postop-orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> savePostopOrders(@PathVariable Long admissionId,
                                              @PathVariable Long bookingId,
                                              @RequestBody PostopOrdersRequest request) {
        try {
            return ResponseEntity.ok(otService.savePostopOrders(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/postop-orders/sign")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> signPostopOrders(@PathVariable Long admissionId,
                                              @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.signPostopOrders(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
