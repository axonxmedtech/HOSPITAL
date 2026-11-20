package com.hms.controller.hospital;

import com.hms.security.RequireModule;
import com.hms.service.hospital.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/notifications")
@RequireModule("NURSING")
// Nurse incharges use the same dashboard bell as staff nurses. NURSE_INCHARGE was missing from
// every endpoint below, so their bell 403'd on each page load. The service only ever returns the
// caller's own notifications, so widening the role exposes nothing.
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'NURSE_INCHARGE')")
    public ResponseEntity<?> listMy() {
        return ResponseEntity.ok(notificationService.getMyNotifications());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('NURSE', 'NURSE_INCHARGE')")
    public ResponseEntity<?> unreadCount() {
        return ResponseEntity.ok(notificationService.getUnreadCount());
    }

    @PutMapping("/{publicId}/read")
    @PreAuthorize("hasAnyRole('NURSE', 'NURSE_INCHARGE')")
    public ResponseEntity<?> markRead(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(notificationService.markAsRead(publicId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('NURSE', 'NURSE_INCHARGE')")
    public ResponseEntity<?> markAllRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }
}
