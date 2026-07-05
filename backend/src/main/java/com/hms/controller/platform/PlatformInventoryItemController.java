package com.hms.controller.platform;

import com.hms.entity.InventoryMasterItem;
import com.hms.service.platform.PlatformInventoryItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform/inventory-master")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformInventoryItemController {

    @Autowired
    private PlatformInventoryItemService service;

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<InventoryMasterItem>> getItems(
            @RequestParam(value = "search", required = false) String search,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(service.listItems(search, pageable));
    }

    @PostMapping
    public ResponseEntity<?> createItem(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(service.createItem(body.get("name")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/import-csv")
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(service.importCsv(file));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
