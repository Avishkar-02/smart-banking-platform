package com.smartbanking.notificationservice.controller;

import com.smartbanking.common.dto.ApiResponse;
import com.smartbanking.notificationservice.dto.NotificationResponse;
import com.smartbanking.notificationservice.service.NotificationService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    // GET /api/notifications/user/{userUuid}
    // Returns every notification sent to this user — for admin/support use.
    // In production this endpoint would be restricted to ADMIN role via API Gateway.
    @GetMapping("/user/{userUuid}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>>
    getNotificationsForUser(
            @PathVariable @NotBlank String userUuid) {

        log.debug("Get notifications request for user: {}", userUuid);
        List<NotificationResponse> notifications =
                notificationService.getNotificationsForUser(userUuid);

        return ResponseEntity.ok(
                ApiResponse.success(notifications,
                        "Notifications fetched. Count: "
                                + notifications.size()));
    }
}