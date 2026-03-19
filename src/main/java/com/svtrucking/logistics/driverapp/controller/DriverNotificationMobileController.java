package com.svtrucking.logistics.driverapp.controller;

import com.svtrucking.logistics.core.ApiResponse;
import com.svtrucking.logistics.driverapp.messaging.DriverAppEventPublisher;
import com.svtrucking.logistics.modules.notification.dto.NotificationDTO;
import com.svtrucking.logistics.modules.notification.model.DriverNotification;
import com.svtrucking.logistics.modules.notification.service.DriverNotificationService;
import com.svtrucking.logistics.security.AuthenticatedUserUtil;
import com.svtrucking.logistics.service.LocalizedMessageService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class DriverNotificationMobileController {

  private final DriverNotificationService driverNotificationService;
  private final AuthenticatedUserUtil authUtil;
  private final DriverAppEventPublisher eventPublisher;
  private final LocalizedMessageService messages;

  @GetMapping({"/driver/{driverId}", "/drivers/{driverId}", "/me"})
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getDriverNotifications(
      @PathVariable(name = "driverId", required = false) Long driverId,
      @RequestParam(defaultValue = "unreadFirst") String order,
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime since,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    int safeSize = Math.min(Math.max(size, 1), 100);

    Page<DriverNotification> result;
    if (since != null) {
      result = driverNotificationService.getNewSince(resolvedDriverId, since, page, safeSize);
    } else if (unreadOnly) {
      result = driverNotificationService.getUnreadNotifications(resolvedDriverId, page, safeSize);
    } else if ("newest".equalsIgnoreCase(order)) {
      result =
          driverNotificationService.getNotificationsNewestFirst(resolvedDriverId, page, safeSize);
    } else {
      result =
          driverNotificationService.getNotificationsUnreadFirst(resolvedDriverId, page, safeSize);
    }

    List<NotificationDTO> content =
        result.getContent().stream().map(driverNotificationService::convertToDTO).toList();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("content", content);
    payload.put("page", result.getNumber());
    payload.put("size", result.getSize());
    payload.put("totalElements", result.getTotalElements());
    payload.put("totalPages", result.getTotalPages());
    payload.put("last", result.isLast());
    payload.put("order", order);
    payload.put("unreadOnly", unreadOnly);
    if (since != null) {
      payload.put("since", since);
    }
    payload.put("unreadCount", driverNotificationService.countUnread(resolvedDriverId));

    return ResponseEntity.ok(ApiResponse.ok(messages.get("api.driver.notifications.loaded"), payload));
  }

  @PutMapping({
    "/driver/{driverId}/{notificationId}/read",
    "/drivers/{driverId}/{notificationId}/read",
    "/me/{notificationId}/read"
  })
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> markAsRead(
      @PathVariable(name = "driverId", required = false) Long driverId,
      @PathVariable Long notificationId,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    driverNotificationService.markAsRead(notificationId, resolvedDriverId);
    eventPublisher.publishDriverNotificationAction(
        resolvedDriverId, "notification.mark-read", notificationId);
    return ResponseEntity.ok(
        ApiResponse.success(messages.get("api.driver.notifications.marked_read")));
  }

  @PutMapping("/driver/{notificationId}/read")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> markAsReadLegacy(
      @PathVariable Long notificationId,
      Authentication authentication) {
    Long resolvedDriverId = authUtil.getCurrentDriverId();
    if (isAdmin(authentication)) {
      resolvedDriverId = null;
    }
    driverNotificationService.markAsRead(notificationId, resolvedDriverId);
    eventPublisher.publishDriverNotificationAction(
        resolvedDriverId, "notification.mark-read", notificationId);
    return ResponseEntity.ok(
        ApiResponse.success(messages.get("api.driver.notifications.marked_read")));
  }

  @PatchMapping({"/driver/{driverId}/mark-all-read", "/drivers/{driverId}/read", "/me/read"})
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> markAllAsRead(
      @PathVariable(name = "driverId", required = false) Long driverId,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    driverNotificationService.markAllAsReadByDriver(resolvedDriverId);
    return ResponseEntity.ok(
        ApiResponse.success(messages.get("api.driver.notifications.all_marked_read")));
  }

  @DeleteMapping({
    "/driver/{driverId}/{notificationId}",
    "/drivers/{driverId}/{notificationId}",
    "/me/{notificationId}"
  })
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> deleteDriverNotification(
      @PathVariable(name = "driverId", required = false) Long driverId,
      @PathVariable Long notificationId,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    driverNotificationService.deleteNotification(notificationId, resolvedDriverId);
    eventPublisher.publishDriverNotificationAction(
        resolvedDriverId, "notification.delete", notificationId);
    return ResponseEntity.ok(ApiResponse.success(messages.get("api.driver.notifications.deleted")));
  }

  @DeleteMapping("/driver/{notificationId}")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> deleteDriverNotificationLegacy(
      @PathVariable Long notificationId, Authentication authentication) {
    Long resolvedDriverId = authUtil.getCurrentDriverId();
    if (isAdmin(authentication)) {
      resolvedDriverId = null;
    }
    driverNotificationService.deleteNotification(notificationId, resolvedDriverId);
    eventPublisher.publishDriverNotificationAction(
        resolvedDriverId, "notification.delete", notificationId);
    return ResponseEntity.ok(ApiResponse.success(messages.get("api.driver.notifications.deleted")));
  }

  @DeleteMapping({"/driver/{driverId}/all", "/drivers/{driverId}", "/me"})
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> deleteAllForDriver(
      @PathVariable(name = "driverId", required = false) Long driverId,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    driverNotificationService.deleteAllNotificationsForDriver(resolvedDriverId);
    return ResponseEntity.ok(ApiResponse.success(messages.get("api.driver.notifications.all_deleted")));
  }

  @DeleteMapping({"/driver/{driverId}/delete-read", "/drivers/{driverId}/read", "/me/read"})
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> deleteReadForDriver(
      @PathVariable(name = "driverId", required = false) Long driverId,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    driverNotificationService.deleteReadNotificationsForDriver(resolvedDriverId);
    return ResponseEntity.ok(ApiResponse.success(messages.get("api.driver.notifications.read_deleted")));
  }

  @DeleteMapping({"/driver/{driverId}/batch", "/drivers/{driverId}/bulk-delete", "/me/bulk-delete"})
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> deleteBatchForDriver(
      @PathVariable(name = "driverId", required = false) Long driverId,
      @RequestBody IdsPayload payload,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    driverNotificationService.deleteBatchForDriver(resolvedDriverId, payload.ids());
    return ResponseEntity.ok(ApiResponse.success(messages.get("api.driver.notifications.batch_deleted")));
  }

  @GetMapping({
    "/driver/{driverId}/count",
    "/drivers/{driverId}/unread-count",
    "/me/unread-count"
  })
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<Long>> countDriverUnread(
      @PathVariable(name = "driverId", required = false) Long driverId,
      Authentication authentication) {
    Long resolvedDriverId = resolveTargetDriverId(driverId, authentication);
    long count = driverNotificationService.countUnread(resolvedDriverId);
    return ResponseEntity.ok(ApiResponse.ok(messages.get("api.common.unread_count_fetched"), count));
  }

  private Long resolveTargetDriverId(Long requestedDriverId, Authentication authentication) {
    if (requestedDriverId != null) {
      return resolveAccessibleDriverId(requestedDriverId, authentication);
    }

    if (isAdmin(authentication)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, messages.get("api.driver.notifications.driver_id_required"));
    }

    return authUtil.getCurrentDriverId();
  }

  private Long resolveAccessibleDriverId(Long requestedDriverId, Authentication authentication) {
    if (isAdmin(authentication)) {
      return requestedDriverId;
    }

    Long currentDriverId = authUtil.getCurrentDriverId();
    if (!currentDriverId.equals(requestedDriverId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Driver access is limited to the current user");
    }
    return currentDriverId;
  }

  private boolean isAdmin(Authentication authentication) {
    if (authentication == null) {
      return false;
    }

    return authentication.getAuthorities().stream()
        .map(grantedAuthority -> grantedAuthority.getAuthority())
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_SUPERADMIN".equals(authority));
  }

  public record IdsPayload(List<Long> ids) {}
}
