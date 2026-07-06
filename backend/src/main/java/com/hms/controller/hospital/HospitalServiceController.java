package com.hms.controller.hospital;

import com.hms.dto.HospitalServiceDTO;
import com.hms.entity.HospitalServiceEntity;
import com.hms.entity.InventoryMasterItem;
import com.hms.repository.InventoryMasterItemRepository;
import com.hms.service.hospital.HospitalServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/hospital", "/clinic", "/pharmacy"})
public class HospitalServiceController {

    @Autowired
    private HospitalServiceService serviceService;

    @Autowired
    private InventoryMasterItemRepository masterItemRepository;

    private HospitalServiceDTO toDto(HospitalServiceEntity s) {
        return new HospitalServiceDTO(
                s.getId(), s.getName(), s.getCharge(),
                serviceService.getMasterItemIdsForService(s.getId()),
                serviceService.getItemNamesForService(s.getId()));
    }

    // --- Global master item list (read-only for hospital roles) ---
    @GetMapping("/inventory-master")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<InventoryMasterItem>> listMasterItems() {
        return ResponseEntity.ok(masterItemRepository.findAllByOrderByNameAsc());
    }

    // --- Per-hospital services ---
    @GetMapping("/services")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<HospitalServiceDTO>> listServices() {
        List<HospitalServiceDTO> dtos = serviceService.listServices().stream()
                .map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/services")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> createService(@RequestBody HospitalServiceDTO dto) {
        try {
            HospitalServiceEntity saved = serviceService.createService(dto.getName(), dto.getCharge(), dto.getMasterItemIds());
            return ResponseEntity.ok(toDto(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateService(@PathVariable Long id, @RequestBody HospitalServiceDTO dto) {
        try {
            HospitalServiceEntity saved = serviceService.updateService(id, dto.getName(), dto.getCharge(), dto.getMasterItemIds());
            return ResponseEntity.ok(toDto(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/services/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> deleteService(@PathVariable Long id) {
        try {
            serviceService.deleteService(id);
            return ResponseEntity.ok("Service deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
