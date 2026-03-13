package com.svtrucking.logistics.driverapp.controller;

import com.svtrucking.logistics.core.ApiResponse;
import com.svtrucking.logistics.model.UserSetting;
import com.svtrucking.logistics.security.AuthenticatedUserUtil;
import com.svtrucking.logistics.service.UserSettingService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-settings")
@CrossOrigin(origins = "*")
public class DriverUserSettingMobileController {

  private final UserSettingService userSettingService;
  private final AuthenticatedUserUtil authenticatedUserUtil;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserSetting>>> getSettings() {
    Long userId = authenticatedUserUtil.getCurrentUserId();
    return ResponseEntity.ok(
        new ApiResponse<>(true, "Settings retrieved", userSettingService.getSettingsByUserId(userId)));
  }

  @PostMapping("/update")
  public ResponseEntity<ApiResponse<UserSetting>> updateUserSetting(
      @RequestBody Map<String, String> payload) {
    String key = payload.get("key");
    String value = payload.get("value");

    if (key == null || value == null) {
      log.warn("Missing key or value in setting update payload: {}", payload);
      return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Missing key or value", null));
    }

    Long userId = authenticatedUserUtil.getCurrentUserId();
    UserSetting updatedSetting = userSettingService.updateSetting(userId, key, value);
    return ResponseEntity.ok(new ApiResponse<>(true, "Setting updated", updatedSetting));
  }
}
