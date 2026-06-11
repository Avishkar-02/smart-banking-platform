package com.smartbanking.notificationservice.controller;

import com.smartbanking.notificationservice.dto.NotificationResponse;
import com.smartbanking.notificationservice.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@DisplayName("NotificationController Integration Tests")
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private NotificationService notificationService;

    private static final String USER_UUID = "user-uuid-123";

    @Test
    @DisplayName("GET /user/{userUuid} — 200 with list of notifications")
    void shouldGetNotifications200() throws Exception {
        NotificationResponse notification = NotificationResponse.builder()
                .uuid("notif-uuid-001")
                .recipientEmail("avishkar@test.com")
                .notificationType("WELCOME")
                .subject("Welcome to Smart Banking Platform, Avishkar!")
                .status("SENT")
                .createdAt(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .build();

        when(notificationService.getNotificationsForUser(USER_UUID))
                .thenReturn(List.of(notification));

        mockMvc.perform(get("/api/notifications/user/" + USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].notificationType").value("WELCOME"))
                .andExpect(jsonPath("$.data[0].status").value("SENT"))
                .andExpect(jsonPath("$.data[0].recipientEmail")
                        .value("avishkar@test.com"));
    }

    @Test
    @DisplayName("GET /user/{userUuid} — 200 with empty list when no notifications")
    void shouldReturn200WithEmptyList() throws Exception {
        when(notificationService.getNotificationsForUser(anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/user/" + USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /user/{userUuid} — shows FAILED notification with reason")
    void shouldShowFailedNotificationWithReason() throws Exception {
        NotificationResponse failedNotif = NotificationResponse.builder()
                .uuid("notif-uuid-002")
                .recipientEmail("avishkar@test.com")
                .notificationType("TRANSFER_SUCCESS")
                .subject("Transfer Successful")
                .status("FAILED")
                .failureReason("SMTP connection refused")
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationService.getNotificationsForUser(USER_UUID))
                .thenReturn(List.of(failedNotif));

        mockMvc.perform(get("/api/notifications/user/" + USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].failureReason")
                        .value("SMTP connection refused"));
    }
}