package com.svtrucking.logistics.driverapp.controller;

import com.svtrucking.logistics.core.ApiResponse;
import com.svtrucking.logistics.dto.DispatchDto;
import com.svtrucking.logistics.dto.DispatchStatusHistoryDto;
import com.svtrucking.logistics.dto.DriverDispatchDto;
import com.svtrucking.logistics.dto.LoadProofDto;
import com.svtrucking.logistics.dto.UnloadProofDto;
import com.svtrucking.logistics.dto.request.BreakdownReportRequest;
import com.svtrucking.logistics.dto.request.UpdateDispatchStatusRequest;
import com.svtrucking.logistics.dto.response.DispatchStatusUpdateResponse;
import com.svtrucking.logistics.enums.DispatchStatus;
import com.svtrucking.logistics.exception.InvalidDispatchDataException;
import com.svtrucking.logistics.exception.ResourceNotFoundException;
import com.svtrucking.logistics.model.Dispatch;
import com.svtrucking.logistics.model.TransportOrder;
import com.svtrucking.logistics.repository.DispatchRepository;
import com.svtrucking.logistics.repository.TransportOrderRepository;
import com.svtrucking.logistics.security.AuthenticatedUserUtil;
import com.svtrucking.logistics.service.DispatchService;
import com.svtrucking.logistics.service.LoadProofService;
import com.svtrucking.logistics.service.UnloadProofService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/driver/dispatches")
@RequiredArgsConstructor
public class DriverDispatchMobileController {

  private final DispatchService dispatchService;
  private final LoadProofService loadProofService;
  private final UnloadProofService unloadProofService;
  private final AuthenticatedUserUtil authUtil;
  private final DispatchRepository dispatchRepository;
  private final TransportOrderRepository transportOrderRepository;

  @GetMapping("/driver/{driverId}/processing")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<List<DispatchDto>>> getProcessingDispatches(
      @PathVariable Long driverId,
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @RequestParam(required = false) String q,
      Authentication authentication) {
    Long accessibleDriverId = resolveAccessibleDriverId(driverId, authentication);

    EnumSet<DispatchStatus> processingStatuses =
        EnumSet.of(
            DispatchStatus.ASSIGNED,
            DispatchStatus.DRIVER_CONFIRMED,
            DispatchStatus.IN_QUEUE,
            DispatchStatus.ARRIVED_LOADING,
            DispatchStatus.LOADING,
            DispatchStatus.LOADED,
            DispatchStatus.IN_TRANSIT,
            DispatchStatus.ARRIVED_UNLOADING,
            DispatchStatus.UNLOADING,
            DispatchStatus.UNLOADED);

    List<DispatchStatus> filterStatuses = new ArrayList<>(processingStatuses);
    if (status != null && !status.isEmpty()) {
      filterStatuses =
          status.stream()
              .map(
                  item -> {
                    try {
                      return DispatchStatus.valueOf(item.trim().toUpperCase());
                    } catch (Exception e) {
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .toList();
    }

    List<DispatchDto> result;
    boolean hasFilter = q != null && !q.isBlank();
    if (hasFilter) {
      result =
          dispatchService
              .filterDispatches(
                  accessibleDriverId,
                  null,
                  filterStatuses.isEmpty() ? null : filterStatuses.get(0),
                  null,
                  null,
                  q,
                  null,
                  null,
                  null,
                  null,
                  from,
                  to,
                  Pageable.unpaged())
              .getContent();
    } else {
      result =
          dispatchService
              .getDispatchesByDriverWithStatuses(
                  accessibleDriverId, filterStatuses, Pageable.unpaged())
              .getContent();
    }

    return ResponseEntity.ok(new ApiResponse<>(true, "ទាញយកបញ្ជាដឹកជញ្ជូនកំពុងដំណើរការ", result));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchDto>> getDispatchById(
      @PathVariable Long id, Authentication authentication) {
    DispatchDto dispatch = dispatchService.getDispatchById(id);
    enforceDispatchAccess(dispatch, authentication);

    LoadProofDto loadProof = loadProofService.getProofByDispatchId(id);
    if (loadProof != null) {
      dispatch.setLoadingProofImages(loadProof.getImageUrls());
      dispatch.setLoadingSignature(loadProof.getSignatureUrl());
    }

    UnloadProofDto unloadProof = unloadProofService.getProofByDispatchId(id);
    if (unloadProof != null) {
      dispatch.setUnloadingProofImages(unloadProof.getImageUrls());
      dispatch.setUnloadingSignature(unloadProof.getSignatureUrl());
    }

    return ResponseEntity.ok(new ApiResponse<>(true, "រកឃើញបញ្ជាដឹកជញ្ជូន។", dispatch));
  }

  @GetMapping("/{id}/status-history")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<List<DispatchStatusHistoryDto>>> getDispatchStatusHistory(
      @PathVariable Long id, Authentication authentication) {
    DispatchDto dispatch = dispatchService.getDispatchById(id);
    enforceDispatchAccess(dispatch, authentication);

    List<DispatchStatusHistoryDto> history = dispatchService.getStatusHistory(id);
    return ResponseEntity.ok(new ApiResponse<>(true, "ទាញយកប្រវត្តិស្ថានភាពបានជោគជ័យ។", history));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchStatusUpdateResponse>> updateDispatchStatus(
      @PathVariable Long id,
      @Valid @RequestBody UpdateDispatchStatusRequest request,
      Authentication authentication) {
    return doUpdateDispatchStatus(id, request, authentication);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchStatusUpdateResponse>> updateDispatchStatusCompatibility(
      @PathVariable Long id,
      @Valid @RequestBody UpdateDispatchStatusRequest request,
      Authentication authentication) {
    return doUpdateDispatchStatus(id, request, authentication);
  }

  @GetMapping("/{id}/available-actions")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchStatusUpdateResponse>> getAvailableActions(
      @PathVariable Long id, Authentication authentication) {
    try {
      DispatchDto dispatch = dispatchService.getDispatchById(id);
      enforceDispatchAccess(dispatch, authentication);

      DispatchStatusUpdateResponse response = dispatchService.getAvailableActionsForDispatch(id);
      return ResponseEntity.ok(
          new ApiResponse<>(true, "ទាញយកសកម្មភាពដែលមាននៅលើបានជោគជ័យ។", response));
    } catch (ResourceNotFoundException ex) {
      Map<String, String> errors = new HashMap<>();
      errors.put("dispatchId", ex.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new ApiResponse<>(false, ex.getMessage(), null, errors));
    } catch (Exception ex) {
      Map<String, String> errors = new HashMap<>();
      errors.put("_global", ex.getMessage());
      return ResponseEntity.badRequest()
          .body(
              new ApiResponse<>(
                  false, "Failed to fetch available actions: " + ex.getMessage(), null, errors));
    }
  }

  @PostMapping(value = "/{dispatchId}/load", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<LoadProofDto>> submitLoadProof(
      @PathVariable Long dispatchId,
      @RequestParam(required = false) String remarks,
      @RequestParam(required = false) List<MultipartFile> images,
      @RequestParam(required = false) MultipartFile signature,
      Authentication authentication) {
    DispatchDto dispatch = dispatchService.getDispatchById(dispatchId);
    enforceDispatchAccess(dispatch, authentication);

    try {
      List<MultipartFile> imageList = images != null ? images : List.of();
      LoadProofDto proof =
          loadProofService.submitLoadProof(dispatchId, remarks, imageList, signature);
      return ResponseEntity.ok(new ApiResponse<>(true, "បានដាក់ស្នើភស្ដុតាងពេលផ្ទុក។", proof));
    } catch (IllegalStateException e) {
      Map<String, String> errors = new HashMap<>();
      errors.put("status", e.getMessage());
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(false, e.getMessage(), null, errors));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ApiResponse<>(false, "បរាជ័យក្នុងការដាក់ស្នើភស្ដុតាងពេលផ្ទុក។", null));
    }
  }

  @PostMapping(value = "/{dispatchId}/unload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<?> markAsUnloaded(
      @PathVariable Long dispatchId,
      @RequestParam(required = false) String remarks,
      @RequestParam(required = false) String address,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      @RequestParam(value = "images", required = false) List<MultipartFile> images,
      @RequestParam(value = "signature", required = false) MultipartFile signature,
      Authentication authentication) {
    DispatchDto dispatch = dispatchService.getDispatchById(dispatchId);
    enforceDispatchAccess(dispatch, authentication);

    dispatchService.markAsUnloaded(
        dispatchId, remarks, address, latitude, longitude, images, signature);
    return ResponseEntity.ok(Map.of("message", "បានដាក់ស្នើព័ត៌មានផ្ទុកចេញដោយជោគជ័យ។"));
  }

  @GetMapping("/driver/{driverId}")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<Page<DriverDispatchDto>> getDispatchesByDriverWithDateRange(
      @PathVariable Long driverId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @PageableDefault(size = 20) Pageable pageable,
      Authentication authentication) {
    Long accessibleDriverId = resolveAccessibleDriverId(driverId, authentication);
    Page<DispatchDto> page = dispatchService.getDispatchesByDriverId(accessibleDriverId, pageable);
    return ResponseEntity.ok(page.map(DriverDispatchDto::from));
  }

  @GetMapping("/me/pending")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<Page<DriverDispatchDto>> getMyPendingDispatches(
      @PageableDefault(
              sort = "startTime",
              direction = org.springframework.data.domain.Sort.Direction.DESC,
              size = 100)
          Pageable pageable) {
    Long driverId = authUtil.getCurrentDriverId();
    List<DispatchStatus> pendingStatuses =
        Arrays.asList(
            DispatchStatus.PLANNED,
            DispatchStatus.PENDING,
            DispatchStatus.SCHEDULED,
            DispatchStatus.ASSIGNED);

    Page<DispatchDto> page =
        dispatchService.getDispatchesByDriverWithStatuses(driverId, pendingStatuses, pageable);
    return ResponseEntity.ok(page.map(DriverDispatchDto::from));
  }

  @GetMapping("/me/in-progress")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<Page<DriverDispatchDto>> getMyInProgressDispatches(
      @PageableDefault(sort = "startTime", size = 100) Pageable pageable) {
    Long driverId = authUtil.getCurrentDriverId();
    List<DispatchStatus> inProgressStatuses =
        Arrays.asList(
            DispatchStatus.DRIVER_CONFIRMED,
            DispatchStatus.APPROVED,
            DispatchStatus.ARRIVED_LOADING,
            DispatchStatus.IN_QUEUE,
            DispatchStatus.LOADING,
            DispatchStatus.LOADED,
            DispatchStatus.AT_HUB,
            DispatchStatus.HUB_LOADING,
            DispatchStatus.IN_TRANSIT,
            DispatchStatus.IN_TRANSIT_BREAKDOWN,
            DispatchStatus.PENDING_INVESTIGATION,
            DispatchStatus.ARRIVED_UNLOADING,
            DispatchStatus.UNLOADING,
            DispatchStatus.UNLOADED,
            DispatchStatus.SAFETY_PASSED);

    Page<DispatchDto> page =
        dispatchService.getDispatchesByDriverWithStatuses(driverId, inProgressStatuses, pageable);
    return ResponseEntity.ok(page.map(DriverDispatchDto::from));
  }

  @GetMapping("/me/completed")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<Page<DriverDispatchDto>> getMyCompletedDispatches(
      @PageableDefault(
              sort = "updatedDate",
              direction = org.springframework.data.domain.Sort.Direction.DESC,
              size = 100)
          Pageable pageable) {
    Long driverId = authUtil.getCurrentDriverId();
    List<DispatchStatus> completedStatuses =
        Arrays.asList(
            DispatchStatus.DELIVERED,
            DispatchStatus.FINANCIAL_LOCKED,
            DispatchStatus.CLOSED,
            DispatchStatus.COMPLETED,
            DispatchStatus.CANCELLED,
            DispatchStatus.REJECTED,
            DispatchStatus.SAFETY_FAILED);

    Page<DispatchDto> page =
        dispatchService.getDispatchesByDriverWithStatuses(driverId, completedStatuses, pageable);
    return ResponseEntity.ok(page.map(DriverDispatchDto::from));
  }

  @GetMapping("/driver/{driverId}/status")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<Page<DriverDispatchDto>> getDispatchesByDriverWithStatusFilter(
      @PathVariable Long driverId,
      @RequestParam(required = false) String status,
      Pageable pageable,
      Authentication authentication) {
    Long accessibleDriverId = resolveAccessibleDriverId(driverId, authentication);

    List<DispatchStatus> statuses = null;
    if (status != null && !status.isBlank()) {
      try {
        statuses =
            Arrays.stream(status.split(","))
                .map(item -> DispatchStatus.valueOf(item.trim().toUpperCase()))
                .collect(Collectors.toList());
      } catch (IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Page.empty(pageable));
      }
    }

    Page<DispatchDto> page =
        dispatchService.getDispatchesByDriverWithStatuses(accessibleDriverId, statuses, pageable);
    return ResponseEntity.ok(page.map(DriverDispatchDto::from));
  }

  @GetMapping("/by-order/{orderId}")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchDto>> getDispatchByOrderId(
      @PathVariable Long orderId, Authentication authentication) {
    TransportOrder order =
        transportOrderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

    Dispatch dispatch =
        dispatchRepository.findByTransportOrderOrderByCreatedDateDesc(order).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dispatch not found for order"));

    DispatchDto dto = dispatchService.getDispatchById(dispatch.getId());
    enforceDispatchAccess(dto, authentication);
    return ResponseEntity.ok(ApiResponse.success("រកឃើញបញ្ជាដឹកជញ្ជូន។", dto));
  }

  @PostMapping("/{id}/accept")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchDto>> acceptDispatch(
      @PathVariable Long id, Authentication authentication) {
    DispatchDto existing = dispatchService.getDispatchById(id);
    enforceDispatchAccess(existing, authentication);

    DispatchDto dto = dispatchService.acceptDispatch(id);
    return ResponseEntity.ok(new ApiResponse<>(true, "អ្នកបើកបរបានទទួលបញ្ជាដឹកជញ្ជូន។", dto));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchDto>> rejectDispatch(
      @PathVariable Long id,
      @RequestParam(required = false) String reason,
      @RequestBody(required = false) Map<String, Object> payload,
      Authentication authentication) {
    DispatchDto existing = dispatchService.getDispatchById(id);
    enforceDispatchAccess(existing, authentication);

    String resolvedReason = resolveRejectReason(reason, payload);
    DispatchDto dto = dispatchService.rejectDispatch(id, resolvedReason);
    return ResponseEntity.ok(new ApiResponse<>(true, "អ្នកបើកបរបានបដិសេធបញ្ជាដឹកជញ្ជូន។", dto));
  }

  @PostMapping("/{id}/breakdown")
  @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_ADMIN','ROLE_SUPERADMIN')")
  public ResponseEntity<ApiResponse<DispatchDto>> reportBreakdown(
      @PathVariable Long id,
      @Valid @RequestBody BreakdownReportRequest request,
      Authentication authentication) {
    DispatchDto existing = dispatchService.getDispatchById(id);
    enforceDispatchAccess(existing, authentication);

    DispatchDto result =
        dispatchService.reportBreakdown(
            id, request.getLocation(), request.getDescription(), request.getLat(), request.getLng());
    return ResponseEntity.ok(new ApiResponse<>(true, "Breakdown reported successfully.", result));
  }

  private ResponseEntity<ApiResponse<DispatchStatusUpdateResponse>> doUpdateDispatchStatus(
      Long id, UpdateDispatchStatusRequest request, Authentication authentication) {
    DispatchDto dispatch = dispatchService.getDispatchById(id);
    enforceDispatchAccess(dispatch, authentication);

    if (request.getStatus() == null) {
      Map<String, String> errors = new HashMap<>();
      errors.put("status", "Status is required");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(false, "សូមផ្តល់ស្ថានភាពសម្រាប់បញ្ជាដឹកជញ្ជូន។", null, errors));
    }

    try {
      DispatchStatusUpdateResponse response =
          dispatchService.updateDispatchStatusWithResponse(
              id, request.getStatus(), request.getReason(), request.getMetadata());

      return ResponseEntity.ok(
          new ApiResponse<>(true, "បានធ្វើបច្ចុប្បន្នភាពស្ថានភាពបញ្ជាដឹកជញ្ជូនដោយជោគជ័យ។", response));
    } catch (SecurityException ex) {
      Map<String, String> errors = new HashMap<>();
      errors.put("_global", ex.getMessage());
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(new ApiResponse<>(false, ex.getMessage(), null, errors));
    } catch (InvalidDispatchDataException ex) {
      Map<String, String> errors = new HashMap<>();
      errors.put(
          ex.getField() != null ? ex.getField() : "_global",
          ex.getReason() != null ? ex.getReason() : ex.getMessage());
      if (ex.getCode() != null && !ex.getCode().isBlank()) {
        errors.put("code", ex.getCode());
      }
      if (ex.getRequiredInput() != null && !ex.getRequiredInput().isBlank()) {
        errors.put("requiredInput", ex.getRequiredInput());
      }
      if (ex.getNextAllowedAction() != null && !ex.getNextAllowedAction().isBlank()) {
        errors.put("nextAllowedAction", ex.getNextAllowedAction());
      }
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(false, ex.getMessage(), null, errors));
    } catch (IllegalArgumentException ex) {
      Map<String, String> errors = new HashMap<>();
      errors.put("status", ex.getMessage());
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(false, "ស្ថានភាពមិនត្រឹមត្រូវ: " + request.getStatus(), null, errors));
    } catch (Exception ex) {
      Map<String, String> errors = new HashMap<>();
      errors.put("_global", ex.getMessage());
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(false, "Failed to update dispatch status", null, errors));
    }
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

  private void enforceDispatchAccess(DispatchDto dispatch, Authentication authentication) {
    if (dispatch == null || isAdmin(authentication)) {
      return;
    }

    Long currentDriverId = authUtil.getCurrentDriverId();
    if (dispatch.getDriverId() == null || !currentDriverId.equals(dispatch.getDriverId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Dispatch access is limited to the current driver");
    }
  }

  private boolean isAdmin(Authentication authentication) {
    if (authentication == null) {
      return false;
    }

    return authentication.getAuthorities().stream()
        .map(grantedAuthority -> grantedAuthority.getAuthority())
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_SUPERADMIN".equals(authority));
  }

  private String resolveRejectReason(String queryReason, Map<String, Object> payload) {
    if (queryReason != null && !queryReason.isBlank()) {
      return queryReason;
    }

    if (payload != null) {
      Object bodyReason = payload.get("reason");
      if (bodyReason != null && !bodyReason.toString().isBlank()) {
        return bodyReason.toString().trim();
      }
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required");
  }
}
