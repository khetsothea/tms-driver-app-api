package com.svtrucking.logistics.driverapp.controller;

import com.svtrucking.logistics.security.AuthenticatedUserUtil;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver-app")
@RequiredArgsConstructor
public class DriverAppBootstrapController {

  private final AuthenticatedUserUtil authenticatedUserUtil;

  @GetMapping("/bootstrap")
  public Map<String, Object> bootstrap(Authentication authentication) {
    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", authenticatedUserUtil.getCurrentUserId());
    user.put(
        "roles",
        authentication == null
            ? List.of()
            : authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    user.put("derivedSegments", List.of());

    root.put("user", user);
    root.put("screens", Map.of());
    root.put("features", Map.of());
    root.put("policies", Map.of());
    root.put(
        "meta",
        Map.of(
            "generatedAt", Instant.now().toString(),
            "resolutionTraceVersion", "driver-app-api-default"));
    return root;
  }
}
