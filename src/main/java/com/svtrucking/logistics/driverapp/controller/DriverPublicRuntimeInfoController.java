package com.svtrucking.logistics.driverapp.controller;

import com.svtrucking.logistics.core.ApiResponse;
import com.svtrucking.logistics.dto.RuntimeInfoDto;
import com.svtrucking.logistics.service.RuntimeInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/runtime-info")
@RequiredArgsConstructor
public class DriverPublicRuntimeInfoController {

  private final RuntimeInfoService runtimeInfoService;

  @GetMapping
  public ResponseEntity<ApiResponse<RuntimeInfoDto>> getRuntimeInfo() {
    return ResponseEntity.ok(new ApiResponse<>(true, "Runtime info fetched", runtimeInfoService.getRuntimeInfo()));
  }
}
