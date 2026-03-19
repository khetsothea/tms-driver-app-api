package com.svtrucking.logistics.driverapp.messaging;

import com.svtrucking.tms.events.DriverEvent;
import com.svtrucking.tms.events.EventTopics;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DriverAppEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public DriverAppEventPublisher(ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider) {
    this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
  }

  public void publishDriverNotificationAction(Long driverId, String action, Long notificationId) {
    if (kafkaTemplate == null || driverId == null || action == null) {
      return;
    }
    DriverEvent event = new DriverEvent(
        UUID.randomUUID().toString(),
        "tms-driver-app-api",
        action,
        driverId,
        "driver-app",
        Instant.now(),
        Map.of("notificationId", notificationId == null ? "" : String.valueOf(notificationId)));
    kafkaTemplate.send(EventTopics.DRIVER_EVENTS, String.valueOf(driverId), event);
  }
}
