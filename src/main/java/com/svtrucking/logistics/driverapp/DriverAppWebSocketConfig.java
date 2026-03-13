package com.svtrucking.logistics.driverapp;

import com.svtrucking.logistics.config.JwtHandshakeInterceptor;
import com.svtrucking.logistics.security.JwtUtil;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class DriverAppWebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtUtil jwtUtil;
  private final UserDetailsService userDetailsService;

  @Value("${app.websocket.allowed-origins:*}")
  private String allowedOrigins;

  public DriverAppWebSocketConfig(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
    this.jwtUtil = jwtUtil;
    this.userDetailsService = userDetailsService;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    String[] origins =
        Arrays.stream(allowedOrigins.split(",")).map(String::trim).toArray(String[]::new);

    JwtHandshakeInterceptor jwtInterceptor =
        new JwtHandshakeInterceptor(jwtUtil, userDetailsService, origins);

    registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).addInterceptors(jwtInterceptor);

    registry
        .addEndpoint("/ws-sockjs")
        .setAllowedOriginPatterns(origins)
        .addInterceptors(jwtInterceptor)
        .withSockJS()
        .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config
        .enableSimpleBroker("/topic", "/queue")
        .setTaskScheduler(taskScheduler())
        .setHeartbeatValue(new long[] {10_000, 10_000});

    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

            if (SimpMessageType.CONNECT.equals(accessor.getMessageType())) {
              String auth = accessor.getFirstNativeHeader("Authorization");
              if (auth == null || auth.isBlank()) {
                auth = accessor.getFirstNativeHeader("token");
              }

              String token = null;
              if (auth != null) {
                token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
              }

              if (token != null && !token.isBlank()) {
                try {
                  String username = jwtUtil.extractUsername(token);
                  if (username != null) {
                    UserDetails details = userDetailsService.loadUserByUsername(username);
                    if (jwtUtil.validateToken(token, details)) {
                      Authentication authentication =
                          new UsernamePasswordAuthenticationToken(
                              details, null, details.getAuthorities());
                      accessor.setUser(authentication);
                    }
                  }
                } catch (Exception ignored) {
                  // Leave unauthenticated; controller-level guards will reject if needed.
                }
              }
            }
            return message;
          }
        });
  }

  @Bean
  @Primary
  public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("driverapp-stomp-heartbeat-");
    scheduler.initialize();
    return scheduler;
  }
}
