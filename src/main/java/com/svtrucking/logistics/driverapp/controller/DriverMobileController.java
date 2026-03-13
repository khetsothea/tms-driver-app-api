package com.svtrucking.logistics.driverapp.controller;

import com.svtrucking.logistics.core.ApiResponse;
import com.svtrucking.logistics.dto.DriverDto;
import com.svtrucking.logistics.dto.LiveDriverDto;
import com.svtrucking.logistics.dto.requests.DeviceTokenRequest;
import com.svtrucking.logistics.exception.DriverNotFoundException;
import com.svtrucking.logistics.model.Driver;
import com.svtrucking.logistics.model.Vehicle;
import com.svtrucking.logistics.model.VehicleDriver;
import com.svtrucking.logistics.repository.DriverRepository;
import com.svtrucking.logistics.repository.VehicleDriverRepository;
import com.svtrucking.logistics.security.AuthenticatedUserUtil;
import com.svtrucking.logistics.service.DriverService;
import com.svtrucking.logistics.service.LiveDriverQueryService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
@Slf4j
public class DriverMobileController {

  private final AuthenticatedUserUtil authUtil;
  private final DriverRepository driverRepository;
  private final DriverService driverService;
  private final VehicleDriverRepository vehicleDriverRepository;
  private final LiveDriverQueryService liveDriverQueryService;

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DriverDto>> getDriverById(
      @PathVariable Long id, Authentication authentication) {
    try {
      Long accessibleDriverId = resolveAccessibleDriverId(id, authentication);
      Driver driver = findDriverOrThrow(accessibleDriverId);
      DriverDto dto = DriverDto.fromEntity(driver, false, true);
      dto.setLatitude(dto.getLatitude() != null ? dto.getLatitude() : 0.0);
      dto.setLongitude(dto.getLongitude() != null ? dto.getLongitude() : 0.0);
      return ResponseEntity.ok(ApiResponse.success("Driver found.", dto));
    } catch (DriverNotFoundException e) {
      log.warn("Driver {} not found: {}", id, e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.fail("Driver not found."));
    } catch (ResponseStatusException e) {
      log.warn("Driver {} access rejected: {}", id, e.getReason());
      return responseStatusFailure(e);
    } catch (Exception e) {
      log.error("Failed to load driver {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ApiResponse.fail("Failed to load driver."));
    }
  }

  @GetMapping("/me/profile")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DriverDto>> getMyProfile() {
    try {
      Long driverId = currentDriverIdOrThrow();
      Driver driver = findDriverOrThrow(driverId);
      DriverDto dto = DriverDto.fromEntity(driver, false, true);
      dto.setLatitude(dto.getLatitude() != null ? dto.getLatitude() : 0.0);
      dto.setLongitude(dto.getLongitude() != null ? dto.getLongitude() : 0.0);
      return ResponseEntity.ok(ApiResponse.success("Driver profile retrieved.", dto));
    } catch (DriverNotFoundException e) {
      log.warn("Current driver profile not found: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.fail("Driver profile not found."));
    } catch (ResponseStatusException e) {
      log.warn("Current driver profile rejected: {}", e.getReason());
      return responseStatusFailure(e);
    } catch (Exception e) {
      log.error("Failed to load current driver profile: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ApiResponse.fail("Failed to load driver profile."));
    }
  }

  @GetMapping("/me/id-card")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getMyIdCard() {
    try {
      Long driverId = currentDriverIdOrThrow();
      Driver driver = findDriverOrThrow(driverId);

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("driverId", driver.getId());
      payload.put("idCardExpiry", driver.getIdCardExpiry());
      payload.put("firstName", driver.getFirstName());
      payload.put("lastName", driver.getLastName());
      payload.put("name", driver.getName());
      payload.put("phone", driver.getPhone());
      payload.put("profilePicture", driver.getProfilePicture());
      return ResponseEntity.ok(ApiResponse.success("Driver ID card retrieved.", payload));
    } catch (DriverNotFoundException e) {
      log.warn("Current driver id-card not found: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.fail("Driver ID card not found."));
    } catch (ResponseStatusException e) {
      log.warn("Current driver id-card rejected: {}", e.getReason());
      return responseStatusFailure(e);
    } catch (Exception e) {
      log.error("Failed to load current driver id-card: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ApiResponse.fail("Failed to load driver ID card."));
    }
  }

  @PutMapping(value = "/me/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DriverDto>> updateMyProfile(
      @Valid @RequestBody DriverMobileProfileUpdateRequest request) {
    try {
      Long driverId = currentDriverIdOrThrow();
      Driver driver = findDriverOrThrow(driverId);
      driver.setFirstName(request.firstName().trim());
      driver.setLastName(request.lastName().trim());
      driver.setPhone(request.phoneNumber().trim());
      Driver saved = driverRepository.save(driver);
      return ResponseEntity.ok(ApiResponse.success("Profile updated.", DriverDto.fromEntity(saved, false, true)));
    } catch (DriverNotFoundException e) {
      log.warn("Current driver profile update target not found: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.fail("Driver profile not found."));
    } catch (ResponseStatusException e) {
      log.warn("Current driver profile update rejected: {}", e.getReason());
      return responseStatusFailure(e);
    } catch (Exception e) {
      log.error("Failed to update current driver profile: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(ApiResponse.fail("Failed to update profile."));
    }
  }

  @GetMapping("/my-vehicle")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getMyVehicle() {
    try {
      Long driverId = currentDriverIdOrThrow();
      Driver driver = findDriverOrThrow(driverId);
      VehicleDriver assignment = vehicleDriverRepository.findActiveByDriverId(driverId).orElse(null);
      Vehicle vehicle = assignment != null ? assignment.getVehicle() : driver.getCurrentAssignedVehicle();
      if (vehicle == null) {
        return ResponseEntity.ok(ApiResponse.success("No vehicle assigned", null));
      }

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("id", vehicle.getId());
      payload.put("licensePlate", vehicle.getLicensePlate());
      payload.put("plate", vehicle.getLicensePlate());
      payload.put("vehiclePlate", vehicle.getLicensePlate());
      payload.put("type", vehicle.getType());
      payload.put("status", vehicle.getStatus());
      payload.put("model", vehicle.getModel());
      payload.put("manufacturer", vehicle.getManufacturer());
      payload.put("vin", vehicle.getVin());
      payload.put("yearMade", vehicle.getYearMade());
      payload.put("truckSize", vehicle.getTruckSize());
      payload.put("fuelConsumption", vehicle.getFuelConsumption());
      return ResponseEntity.ok(ApiResponse.success("Assigned vehicle retrieved.", payload));
    } catch (DriverNotFoundException e) {
      log.warn("Current driver vehicle lookup target not found: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.fail("Driver profile not found."));
    } catch (ResponseStatusException e) {
      log.warn("Current driver vehicle lookup rejected: {}", e.getReason());
      return responseStatusFailure(e);
    } catch (Exception e) {
      log.error("Failed to load current driver vehicle: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(ApiResponse.fail("Failed to load assigned vehicle."));
    }
  }

  @PostMapping(value = "/{driverId}/upload-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> uploadProfilePicture(
      @PathVariable Long driverId,
      @RequestParam("profilePicture") MultipartFile file,
      Authentication authentication) {
    try {
      Long accessibleDriverId = resolveAccessibleDriverId(driverId, authentication);
      String fileUrl = driverService.saveProfilePicture(accessibleDriverId, file);
      return ResponseEntity.ok(ApiResponse.success("Profile picture updated.", fileUrl));
    } catch (DriverNotFoundException e) {
      log.warn("Profile picture upload target {} not found: {}", driverId, e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.fail("Driver not found."));
    } catch (ResponseStatusException e) {
      log.warn("Profile picture upload rejected for driver {}: {}", driverId, e.getReason());
      return responseStatusFailure(e);
    } catch (Exception e) {
      log.error("Failed to upload profile picture for driver {}: {}", driverId, e.getMessage(), e);
      return ResponseEntity.badRequest().body(ApiResponse.fail("Failed to upload profile picture."));
    }
  }

  @PostMapping("/update-device-token")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<String>> updateDeviceToken(
      @RequestBody DeviceTokenRequest request) {
    try {
      Long driverId = currentDriverIdOrThrow();
      driverService.updateDeviceToken(driverId, request.getDeviceToken());
      return ResponseEntity.ok(ApiResponse.success("Token updated."));
    } catch (ResponseStatusException e) {
      log.warn("Device token update rejected: {}", e.getReason());
      return responseStatusFailure(e);
    } catch (Exception e) {
      log.error("Failed to update device token: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(ApiResponse.fail("Failed to update device token."));
    }
  }

  @GetMapping("/{driverId}/latest-location")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<LiveDriverDto>> latestForDriver(
      @PathVariable Long driverId, Authentication authentication) {
    Long accessibleDriverId = resolveAccessibleDriverId(driverId, authentication);
    return liveDriverQueryService
        .getLatestForDriver(accessibleDriverId)
        .map(dto -> ResponseEntity.ok(ApiResponse.success("OK", dto)))
        .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("No data", null)));
  }

  private Long resolveAccessibleDriverId(Long requestedDriverId, Authentication authentication) {
    if (isAdmin(authentication)) {
      return requestedDriverId;
    }

    Long currentDriverId = currentDriverIdOrThrow();
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

  private Long currentDriverIdOrThrow() {
    try {
      return authUtil.getCurrentDriverId();
    } catch (RuntimeException e) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Authenticated user is not assigned to a driver",
          e);
    }
  }

  private <T> ResponseEntity<ApiResponse<T>> responseStatusFailure(ResponseStatusException e) {
    String message =
        (e.getReason() == null || e.getReason().isBlank())
            ? "Request rejected."
            : e.getReason();
    return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.fail(message));
  }

  private Driver findDriverOrThrow(Long driverId) {
    return driverRepository
        .findById(driverId)
        .orElseThrow(() -> new DriverNotFoundException(driverId));
  }

  public record DriverMobileProfileUpdateRequest(
      String firstName,
      String lastName,
      String phoneNumber) {}
}
