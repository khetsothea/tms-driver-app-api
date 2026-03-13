package com.svtrucking.logistics.driverapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.svtrucking.logistics.core.ApiResponse;
import com.svtrucking.logistics.dto.DriverDto;
import com.svtrucking.logistics.exception.DriverNotFoundException;
import com.svtrucking.logistics.model.Driver;
import com.svtrucking.logistics.repository.DriverRepository;
import com.svtrucking.logistics.repository.VehicleDriverRepository;
import com.svtrucking.logistics.security.AuthenticatedUserUtil;
import com.svtrucking.logistics.service.DriverService;
import com.svtrucking.logistics.service.LiveDriverQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class DriverMobileControllerTest {

  @Mock private AuthenticatedUserUtil authUtil;
  @Mock private DriverRepository driverRepository;
  @Mock private DriverService driverService;
  @Mock private VehicleDriverRepository vehicleDriverRepository;
  @Mock private LiveDriverQueryService liveDriverQueryService;

  private DriverMobileController controller;

  @BeforeEach
  void setUp() {
    controller =
        new DriverMobileController(
            authUtil,
            driverRepository,
            driverService,
            vehicleDriverRepository,
            liveDriverQueryService);
  }

  @Test
  void getDriverById_returnsForbiddenWhenDriverRequestsAnotherDriver() {
    when(authUtil.getCurrentDriverId()).thenReturn(11L);

    ResponseEntity<ApiResponse<DriverDto>> response =
        controller.getDriverById(
            22L,
            new UsernamePasswordAuthenticationToken(
                "driver",
                "n/a",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))));

    assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).contains("limited to the current user");
  }

  @Test
  void getDriverById_returnsNotFoundWhenDriverMissing() {
    when(authUtil.getCurrentDriverId()).thenReturn(11L);
    when(driverRepository.findById(11L)).thenReturn(java.util.Optional.empty());

    ResponseEntity<ApiResponse<DriverDto>> response =
        controller.getDriverById(
            11L,
            new UsernamePasswordAuthenticationToken(
                "driver",
                "n/a",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))));

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).contains("Driver not found");
  }

  @Test
  void getDriverById_returnsDriverWhenSelfAccessAllowed() {
    Driver driver = new Driver();
    driver.setId(11L);
    driver.setFirstName("Sok");
    driver.setLastName("Dara");

    when(authUtil.getCurrentDriverId()).thenReturn(11L);
    when(driverRepository.findById(11L)).thenReturn(java.util.Optional.of(driver));

    ResponseEntity<ApiResponse<DriverDto>> response =
        controller.getDriverById(
            11L,
            new UsernamePasswordAuthenticationToken(
                "driver",
                "n/a",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData()).isNotNull();
    assertThat(response.getBody().getData().getId()).isEqualTo(11L);
    assertThat(response.getBody().getData().getLatitude()).isEqualTo(0.0);
    assertThat(response.getBody().getData().getLongitude()).isEqualTo(0.0);
  }
}
