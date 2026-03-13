package com.svtrucking.logistics.driverapp;

import com.svtrucking.logistics.controller.DriverMaintenanceController;
import com.svtrucking.logistics.controller.DriverPerformanceController;
import com.svtrucking.logistics.controller.driver.DriverBannerController;
import com.svtrucking.logistics.controller.driver.DriverHomeLayoutController;
import com.svtrucking.logistics.controller.drivers.DriverAppIncidentController;
import com.svtrucking.logistics.controller.drivers.DriverPortalController;
import com.svtrucking.logistics.controller.drivers.DriverSafetyCheckController;
import com.svtrucking.logistics.controller.drivers.DriverSelfAssignmentController;
import com.svtrucking.logistics.controller.drivers.DriverTrackingSessionController;
import com.svtrucking.logistics.controller.drivers.PublicAppVersionController;
import com.svtrucking.logistics.driverapp.controller.DriverDispatchMobileController;
import com.svtrucking.logistics.driverapp.controller.DriverAppBootstrapController;
import com.svtrucking.logistics.driverapp.controller.DriverLocationMobileController;
import com.svtrucking.logistics.driverapp.controller.DriverMobileController;
import com.svtrucking.logistics.driverapp.controller.DriverNotificationMobileController;
import com.svtrucking.logistics.driverapp.controller.DriverPublicRuntimeInfoController;
import com.svtrucking.logistics.driverapp.controller.DriverUserSettingMobileController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.svtrucking.logistics")
@PropertySource("classpath:application-uploads.properties")
@EnableJpaRepositories(basePackages = {
    "com.svtrucking.logistics.repository",
    "com.svtrucking.logistics.modules.notification.repository",
    "com.svtrucking.logistics.settings.repository"
})
@ComponentScan(
    basePackages = "com.svtrucking.logistics",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.svtrucking\\.logistics\\.controller\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.svtrucking\\.logistics\\.modules\\.notification\\.controller\\..*"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.svtrucking.logistics.config.WebSocketConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.svtrucking.logistics.security.SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.svtrucking.logistics.Application.class)
    })
@Import({
    DriverMaintenanceController.class,
    DriverPerformanceController.class,
    DriverBannerController.class,
    DriverHomeLayoutController.class,
    DriverAppIncidentController.class,
    DriverAppBootstrapController.class,
    DriverDispatchMobileController.class,
    DriverLocationMobileController.class,
    DriverPortalController.class,
    DriverSafetyCheckController.class,
    DriverSelfAssignmentController.class,
    DriverTrackingSessionController.class,
    DriverMobileController.class,
    PublicAppVersionController.class,
    DriverNotificationMobileController.class,
    DriverUserSettingMobileController.class,
    DriverPublicRuntimeInfoController.class
})
public class DriverAppApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(DriverAppApiApplication.class, args);
  }
}
