package com.eastapp.backend.activity.api;

import com.eastapp.backend.activity.service.NotificationService;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return notificationService.list(principal, page, size);
    }

    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return notificationService.unreadCount(principal);
    }

    @GetMapping("/{notificationId}")
    NotificationResponse detail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID notificationId
    ) {
        return notificationService.detail(principal, notificationId);
    }

    @PatchMapping("/{notificationId}/read")
    NotificationResponse markRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID notificationId
    ) {
        return notificationService.markRead(principal, notificationId);
    }

    @DeleteMapping("/{notificationId}")
    ResponseEntity<Void> dismiss(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID notificationId
    ) {
        notificationService.dismiss(principal, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices")
    ResponseEntity<Void> registerDevice(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RegisterPushDeviceRequest request
    ) {
        notificationService.registerDevice(principal, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices/unregister")
    ResponseEntity<Void> unregisterDevice(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UnregisterPushDeviceRequest request
    ) {
        notificationService.unregisterDevice(principal, request.token());
        return ResponseEntity.noContent().build();
    }
}
