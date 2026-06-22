package com.hms.controller.ot;

import com.hms.dto.ot.OtBillingChargeRequest;
import com.hms.dto.ot.OtBookingRequest;
import com.hms.dto.ot.OtStatusRequest;
import com.hms.entity.ot.*;
import com.hms.service.ot.OtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/ot")
@PreAuthorize("hasAnyRole('OT_ADMIN', 'HOSPITAL_ADMIN')")
public class OtController {

    @Autowired
    private OtService otService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(otService.dashboard());
    }

    @GetMapping("/lookups")
    public ResponseEntity<?> lookups() {
        return ResponseEntity.ok(otService.lookups());
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> bookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(otService.bookings(from, to));
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(@Valid @RequestBody OtBookingRequest request) {
        return ResponseEntity.ok(otService.createBooking(request));
    }

    @PutMapping("/bookings/{id}")
    public ResponseEntity<?> updateBooking(@PathVariable Long id, @Valid @RequestBody OtBookingRequest request) {
        return ResponseEntity.ok(otService.updateBooking(id, request));
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<?> details(@PathVariable Long id) {
        return ResponseEntity.ok(otService.bookingDetails(id));
    }

    @PutMapping("/bookings/{id}/pre-op-checklist")
    public ResponseEntity<?> preOp(@PathVariable Long id, @RequestBody PreOpChecklist checklist) {
        return ResponseEntity.ok(otService.savePreOp(id, checklist));
    }

    @PutMapping("/bookings/{id}/who-checklist")
    public ResponseEntity<?> who(@PathVariable Long id, @RequestBody WhoChecklist checklist) {
        return ResponseEntity.ok(otService.saveWho(id, checklist));
    }

    @PostMapping("/bookings/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id, @Valid @RequestBody OtStatusRequest request) {
        return ResponseEntity.ok(otService.updateStatus(id, request));
    }

    @PostMapping("/bookings/{id}/staff")
    public ResponseEntity<?> staff(@PathVariable Long id, @RequestBody OtStaffAssignment assignment) {
        return ResponseEntity.ok(otService.assignStaff(id, assignment));
    }

    @PostMapping("/bookings/{id}/equipment")
    public ResponseEntity<?> equipmentAssignment(@PathVariable Long id, @RequestBody EquipmentAssignment assignment) {
        return ResponseEntity.ok(otService.assignEquipment(id, assignment));
    }

    @PostMapping("/bookings/{id}/instruments")
    public ResponseEntity<?> instrumentAssignment(@PathVariable Long id, @RequestBody InstrumentAssignment assignment) {
        return ResponseEntity.ok(otService.assignInstrument(id, assignment));
    }

    @PostMapping("/bookings/{id}/anesthesia")
    public ResponseEntity<?> anesthesia(@PathVariable Long id, @RequestBody AnesthesiaRecord record) {
        return ResponseEntity.ok(otService.saveAnesthesia(id, record));
    }

    @PostMapping("/bookings/{id}/implants")
    public ResponseEntity<?> implants(@PathVariable Long id, @RequestBody ImplantUsage usage) {
        return ResponseEntity.ok(otService.saveImplant(id, usage));
    }

    @PostMapping("/bookings/{id}/recovery")
    public ResponseEntity<?> recovery(@PathVariable Long id, @RequestBody RecoveryRoom record) {
        return ResponseEntity.ok(otService.saveRecovery(id, record));
    }

    @PostMapping("/bookings/{id}/consumables")
    public ResponseEntity<?> consumables(@PathVariable Long id, @RequestBody OtConsumableUsage usage) {
        return ResponseEntity.ok(otService.saveConsumable(id, usage));
    }

    @PostMapping("/bookings/{id}/charges")
    public ResponseEntity<?> charges(@PathVariable Long id, @Valid @RequestBody OtBillingChargeRequest request) {
        return ResponseEntity.ok(otService.addCharge(id, request));
    }

    @PutMapping("/bookings/{id}/notes")
    public ResponseEntity<?> notes(@PathVariable Long id, @RequestBody Map<String, String> notes) {
        return ResponseEntity.ok(otService.saveNotes(id, notes));
    }

    @PostMapping("/bookings/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id) {
        return ResponseEntity.ok(otService.completeAndGenerateBill(id));
    }

    @GetMapping("/rooms")
    public ResponseEntity<?> rooms() { return ResponseEntity.ok(otService.rooms()); }

    @PostMapping("/rooms")
    public ResponseEntity<?> saveRoom(@RequestBody OtRoom room) { return ResponseEntity.ok(otService.saveRoom(room)); }

    @GetMapping("/surgeries")
    public ResponseEntity<?> surgeries() { return ResponseEntity.ok(otService.surgeries()); }

    @PostMapping("/surgeries")
    public ResponseEntity<?> saveSurgery(@RequestBody Surgery surgery) { return ResponseEntity.ok(otService.saveSurgery(surgery)); }

    @GetMapping("/equipment")
    public ResponseEntity<?> equipment() { return ResponseEntity.ok(otService.equipment()); }

    @PostMapping("/equipment")
    public ResponseEntity<?> saveEquipment(@RequestBody Equipment equipment) { return ResponseEntity.ok(otService.saveEquipment(equipment)); }

    @GetMapping("/instrument-sets")
    public ResponseEntity<?> instruments() { return ResponseEntity.ok(otService.instruments()); }

    @PostMapping("/instrument-sets")
    public ResponseEntity<?> saveInstrument(@RequestBody InstrumentSet instrument) { return ResponseEntity.ok(otService.saveInstrument(instrument)); }

    @GetMapping("/reports")
    public ResponseEntity<?> reports() { return ResponseEntity.ok(otService.reports()); }
}
