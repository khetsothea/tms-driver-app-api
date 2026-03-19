package com.svtrucking.logistics.driverapp.controller;

import com.svtrucking.logistics.dto.PresenceHeartbeatDto;
import com.svtrucking.logistics.dto.SpoofingAlertDto;
import com.svtrucking.logistics.dto.requests.DriverLocationUpdateDto;
import com.svtrucking.logistics.service.DriverTrackingSessionService;
import com.svtrucking.logistics.service.LiveLocationCacheServiceInterface;
import com.svtrucking.logistics.service.LocationIngestService;
import com.svtrucking.logistics.service.TelematicsProxyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
public class DriverLocationMobileController {

  private final SimpMessagingTemplate messagingTemplate;
  private final LocationIngestService ingest;
  private final DriverTrackingSessionService trackingSessionService;
  private final LiveLocationCacheServiceInterface cacheService;
  private final TelematicsProxyService telematicsProxy;
  private final Counter locationUnauthorizedCounter;
  private final Counter heartbeatUnauthorizedCounter;

  public DriverLocationMobileController(
      SimpMessagingTemplate messagingTemplate,
      LocationIngestService ingest,
      DriverTrackingSessionService trackingSessionService,
      TelematicsProxyService telematicsProxy,
      MeterRegistry meterRegistry,
      @Autowired(required = false) LiveLocationCacheServiceInterface cacheService) {
    this.messagingTemplate = messagingTemplate;
    this.ingest = ingest;
    this.trackingSessionService = trackingSessionService;
    this.telematicsProxy = telematicsProxy;
    this.cacheService = cacheService;
    this.locationUnauthorizedCounter =
        Counter.builder("tracking.location.unauthorized.count")
            .description("Unauthorized responses for /api/driver/location/update")
            .register(meterRegistry);
    this.heartbeatUnauthorizedCounter =
        Counter.builder("tracking.heartbeat.unauthorized.count")
            .description("Unauthorized responses for /api/driver/presence/heartbeat")
            .register(meterRegistry);
  }

  private static final String T_LOC_ONE = "/topic/driver-location/";
  private static final String T_LOC_ALL = "/topic/driver-location/all";
  private static final String T_PRES_ONE = "/topic/driver-presence/";
  private static final String T_PRES_ALL = "/topic/driver-presence/all";

  private static final long ONLINE_MS = 35_000L;
  private static final long IDLE_MS = 180_000L;

  private enum Presence {
    ONLINE,
    IDLE,
    OFFLINE
  }

  @MessageMapping("/location.update")
  @SendToUser("/queue/location-status")
  public Map<String, Object> handleLocationUpdate(@Valid @Payload DriverLocationUpdateDto update) {
    Map<String, Object> ack = new HashMap<>();
    try {
      sanitizeUpdate(update);
      normalizeTelemetry(update);

      long nowMs = System.currentTimeMillis();
      Map<String, Object> live = ingest.accept(update);

      if (live == null) {
        refreshPresenceForSkippedUpdate(update, nowMs, ack);
        return ack;
      }

      live.putIfAbsent("serverTime", nowMs);
      live.putIfAbsent("clientTime", update.effectiveEpochMillisOr(nowMs));
      live.put("lastSeen", nowMs);
      Presence status = computePresence(nowMs, nowMs);
      live.put("presenceStatus", status.name());
      live.put("isOnline", status == Presence.ONLINE);
      fanoutLocation(update.getDriverId(), live);
      markDriverOnline(update.getDriverId());

      ack.put("ok", true);
      ack.put("driverId", update.getDriverId());
      ack.put("serverTime", nowMs);
      ack.put("message", "Location update processed.");
      return ack;
    } catch (Exception e) {
      log.error(
          "Error processing location [driver={}]: {}",
          update != null ? update.getDriverId() : null,
          e.getMessage(),
          e);
      ack.put("ok", false);
      ack.put("message", "Server error while processing location.");
      return ack;
    }
  }

  @MessageMapping("/ping")
  @SendToUser("/queue/location-status")
  public String ping() {
    return "pong";
  }

  @PostMapping("/api/driver/location/update")
  public ResponseEntity<?> restLocationUpdate(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody DriverLocationUpdateDto update) {
    try {
      if (telematicsProxy.isForwardingEnabled()) {
        return (ResponseEntity<?>) telematicsProxy.forward(
            "/api/driver/location/update", authorization, update);
      }

      String token = DriverTrackingSessionService.extractBearerToken(authorization);
      return ResponseEntity.ok(processLocationUpdate(token, update));
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        locationUnauthorizedCounter.increment();
      }
      return ResponseEntity.status(e.getStatusCode())
          .body(Map.of("ok", false, "message", e.getReason() == null ? "Request rejected" : e.getReason()));
    } catch (Exception e) {
      log.error(
          "Error processing REST location [driver={}]: {}",
          update != null ? update.getDriverId() : null,
          e.getMessage(),
          e);
      return ResponseEntity.internalServerError()
          .body(Map.of("ok", false, "message", "Server error while processing location."));
    }
  }

  @PostMapping("/api/driver/location/update/batch")
  public ResponseEntity<Map<String, Object>> restLocationUpdateBatch(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody List<DriverLocationUpdateDto> updates) {
    try {
      if (telematicsProxy.isForwardingEnabled()) {
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) telematicsProxy.forward(
            "/api/driver/location/update/batch", authorization, updates);
      }

      int accepted = 0;
      int skipped = 0;
      String token = DriverTrackingSessionService.extractBearerToken(authorization);

      if (updates != null) {
        for (DriverLocationUpdateDto update : updates) {
          try {
            Map<String, Object> ack = processLocationUpdate(token, update);
            if (Boolean.TRUE.equals(ack.get("ok"))) {
              accepted++;
            } else {
              skipped++;
            }
          } catch (ResponseStatusException perItem) {
            if (perItem.getStatusCode() == HttpStatus.UNAUTHORIZED
                || perItem.getStatusCode() == HttpStatus.FORBIDDEN) {
              throw perItem;
            }
            skipped++;
            log.warn(
                "Skipped bad point for driver={} at clientTime={}: {}",
                update != null ? update.getDriverId() : null,
                update != null ? update.getClientTime() : null,
                perItem.getReason());
          } catch (Exception perItem) {
            skipped++;
            log.warn(
                "Skipped bad point for driver={} at clientTime={}: {}",
                update != null ? update.getDriverId() : null,
                update != null ? update.getClientTime() : null,
                perItem.getMessage());
          }
        }
      }

      return ResponseEntity.ok(
          Map.of(
              "ok", true,
              "message", "Batch processed: " + accepted + " accepted, " + skipped + " skipped",
              "accepted", accepted,
              "skipped", skipped));
    } catch (ResponseStatusException e) {
      return ResponseEntity.status(e.getStatusCode())
          .body(Map.of("ok", false, "message", e.getReason() == null ? "Request rejected" : e.getReason()));
    } catch (Exception e) {
      log.error("Batch location update failed: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("ok", false, "message", "Server error while processing batch updates."));
    }
  }

  private Map<String, Object> processLocationUpdate(
      String token, DriverLocationUpdateDto update) {
    Map<String, Object> ack = new HashMap<>();
    sanitizeUpdate(update);
    trackingSessionService.validateLocationWriteOrThrow(
        token, update.getDriverId(), update.getSessionId());
    normalizeTelemetry(update);

    long nowMs = System.currentTimeMillis();
    Map<String, Object> live = ingest.accept(update);

    if (live == null) {
      refreshPresenceForSkippedUpdate(update, nowMs, ack);
      return ack;
    }

    live.putIfAbsent("serverTime", nowMs);
    live.putIfAbsent("clientTime", update.effectiveEpochMillisOr(nowMs));
    live.put("lastSeen", nowMs);
    Presence status = computePresence(nowMs, nowMs);
    live.put("presenceStatus", status.name());
    live.put("isOnline", status == Presence.ONLINE);
    fanoutLocation(update.getDriverId(), live);
    markDriverOnline(update.getDriverId());

    ack.put("ok", true);
    ack.put("driverId", update.getDriverId());
    ack.put("serverTime", nowMs);
    ack.put("message", "Location update processed.");
    return ack;
  }

  @PostMapping("/api/driver/presence/heartbeat")
  public ResponseEntity<?> presenceHeartbeat(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody PresenceHeartbeatDto dto) {
    try {
      if (telematicsProxy.isForwardingEnabled()) {
        return (ResponseEntity<?>) telematicsProxy.forward(
            "/api/driver/presence/heartbeat", authorization, dto);
      }

      String token = DriverTrackingSessionService.extractBearerToken(authorization);
      trackingSessionService.validateLocationWriteOrThrow(token, dto.getDriverId(), null);

      long nowMs = System.currentTimeMillis();
      Long clientTs = dto.getTs() != null ? dto.getTs() : nowMs;
      Map<String, Object> extra =
          ingest.markPresence(
              dto.getDriverId(),
              dto.getBattery(),
              dto.getGpsEnabled(),
              dto.getDevice(),
              clientTs,
              dto.getReason());

      Long lastSeenMs = ingest.lastSeenEpochMs(dto.getDriverId());
      if (lastSeenMs == null) {
        lastSeenMs = nowMs;
      }

      Map<String, Object> presence =
          buildPresencePayload(
              dto.getDriverId(),
              clientTs,
              dto.getBattery(),
              dto.getGpsEnabled(),
              dto.getDevice(),
              dto.getReason(),
              lastSeenMs,
              nowMs);
      if (extra != null) {
        presence.putAll(extra);
      }

      fanoutPresence(dto.getDriverId(), presence);
      markDriverOnline(dto.getDriverId());

      return ResponseEntity.ok(
          Map.of(
              "status", "ok",
              "message", "heartbeat accepted",
              "serverTime", nowMs,
              "presenceStatus", presence.get("presenceStatus")));
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        heartbeatUnauthorizedCounter.increment();
      }
      return ResponseEntity.status(e.getStatusCode())
          .body(
              Map.of(
                  "status", "error",
                  "message", e.getReason() == null ? "Request rejected" : e.getReason()));
    } catch (Exception e) {
      log.error("Error in presence heartbeat [driver={}]: {}", dto.getDriverId(), e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "error", "message", e.getMessage()));
    }
  }

  @PostMapping("/api/driver/logout")
  public ResponseEntity<?> driverLogout(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam Long driverId) {
    try {
      if (telematicsProxy.isForwardingEnabled()) {
        return (ResponseEntity<?>) telematicsProxy.forward(
            "/api/driver/logout?driverId=" + driverId, authorization, Map.of());
      }

      if (cacheService != null) {
        cacheService.evictDriverLocation(driverId);
      }

      ingest.markPresence(driverId, null, null, "logout", System.currentTimeMillis(), "logout");

      long nowMs = System.currentTimeMillis();
      Map<String, Object> presence =
          buildPresencePayload(driverId, nowMs, null, null, "logout", "logout", 0L, nowMs);
      presence.put("presenceStatus", Presence.OFFLINE.name());
      presence.put("isOnline", false);
      fanoutPresence(driverId, presence);

      return ResponseEntity.ok(
          Map.of(
              "status", "ok",
              "message", "Driver logged out successfully",
              "driverId", driverId,
              "serverTime", nowMs));
    } catch (Exception e) {
      log.error("Error during driver logout [driver={}]: {}", driverId, e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "error", "message", "Failed to logout driver"));
    }
  }

  @PostMapping("/api/locations/spoofing-alert")
  @PreAuthorize("hasAuthority('driver:location:update')")
  public ResponseEntity<Map<String, Object>> reportSpoofingAttempt(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody SpoofingAlertDto alert) {
    try {
      if (telematicsProxy.isForwardingEnabled()) {
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) telematicsProxy.forward(
            "/api/locations/spoofing-alert", authorization, alert);
      }

      log.warn(
          "GPS SPOOFING ALERT - Driver {}: {} at [{}, {}], isMocked={}, accuracy={}m, speed={}m/s",
          alert.getDriverId(),
          alert.getReason(),
          alert.getLatitude(),
          alert.getLongitude(),
          alert.getIsMocked(),
          alert.getAccuracy(),
          alert.getSpeed());

      return ResponseEntity.ok(
          Map.of(
              "status", "ok",
              "message", "Spoofing alert received and logged",
              "driverId", alert.getDriverId(),
              "serverTime", System.currentTimeMillis()));
    } catch (Exception e) {
      log.error("Error processing spoofing alert: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "error", "message", "Failed to process alert"));
    }
  }

  private static Presence computePresence(long lastSeenMs, long nowMs) {
    long dt = Math.max(0, nowMs - lastSeenMs);
    if (dt <= ONLINE_MS) {
      return Presence.ONLINE;
    }
    if (dt <= IDLE_MS) {
      return Presence.IDLE;
    }
    return Presence.OFFLINE;
  }

  private static void putIfNotNull(Map<String, Object> payload, String key, Object value) {
    if (value != null) {
      payload.put(key, value);
    }
  }

  private void sanitizeUpdate(DriverLocationUpdateDto update) {
    if (update == null) {
      throw new IllegalArgumentException("Update body is required");
    }
    if (update.getLatitude() == null || update.getLongitude() == null) {
      throw new IllegalArgumentException("latitude and longitude are required");
    }
    if (update.getLatitude() < -90
        || update.getLatitude() > 90
        || update.getLongitude() < -180
        || update.getLongitude() > 180) {
      throw new IllegalArgumentException("Invalid latitude/longitude bounds");
    }
  }

  private void normalizeTelemetry(DriverLocationUpdateDto update) {
    if (update.getBatteryLevel() != null
        && (update.getBatteryLevel() < 0 || update.getBatteryLevel() > 100)) {
      update.setBatteryLevel(null);
    }
    if (update.getSpeed() != null && update.getSpeed() < 0) {
      update.setSpeed(0.0);
    }
    if (update.getHeading() != null && update.getHeading() < 0) {
      update.setHeading(0.0);
    }
  }

  private Map<String, Object> buildPresencePayload(
      Long driverId,
      Long clientTs,
      Integer battery,
      Boolean gps,
      String device,
      String reason,
      long lastSeenMs,
      long nowMs) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("driverId", driverId);
    payload.put("serverTime", nowMs);
    payload.put("clientTime", clientTs != null ? clientTs : nowMs);
    payload.put("lastSeen", lastSeenMs);

    Presence status = computePresence(lastSeenMs, nowMs);
    payload.put("presenceStatus", status.name());
    payload.put("isOnline", status == Presence.ONLINE);

    putIfNotNull(payload, "battery", battery);
    putIfNotNull(payload, "gpsEnabled", gps);
    putIfNotNull(payload, "device", device);
    putIfNotNull(payload, "reason", reason);
    return payload;
  }

  private void refreshPresenceForSkippedUpdate(
      DriverLocationUpdateDto update, long nowMs, Map<String, Object> ack) {
    ingest.markPresence(
        update.getDriverId(),
        update.getBatteryLevel(),
        update.getGpsOn(),
        update.getSource(),
        update.effectiveEpochMillisOr(nowMs),
        "dup/throttle");

    Long lastSeenMs = ingest.lastSeenEpochMs(update.getDriverId());
    if (lastSeenMs == null) {
      lastSeenMs = nowMs;
    }

    Map<String, Object> presence =
        buildPresencePayload(
            update.getDriverId(),
            update.effectiveEpochMillisOr(nowMs),
            update.getBatteryLevel(),
            update.getGpsOn(),
            update.getSource(),
            "dup/throttle",
            lastSeenMs,
            nowMs);
    fanoutPresence(update.getDriverId(), presence);
    markDriverOnline(update.getDriverId());

    ack.put("ok", true);
    ack.put("dedup", true);
    ack.put("driverId", update.getDriverId());
    ack.put("serverTime", nowMs);
    ack.put("message", "Location ignored (duplicate/too soon). Presence refreshed.");
  }

  private void fanoutPresence(Long driverId, Map<String, Object> payload) {
    messagingTemplate.convertAndSend(T_PRES_ONE + driverId, payload);
    messagingTemplate.convertAndSend(T_PRES_ALL, payload);
  }

  private void fanoutLocation(Long driverId, Map<String, Object> live) {
    messagingTemplate.convertAndSend(T_LOC_ONE + driverId, live);
    messagingTemplate.convertAndSend(T_LOC_ALL, live);
  }

  private void markDriverOnline(Long driverId) {
    if (cacheService != null) {
      cacheService.markDriverOnline(driverId);
    }
  }
}
