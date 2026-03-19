package com.svtrucking.logistics.driverapp;

import com.svtrucking.logistics.enums.RoleType;
import com.svtrucking.logistics.security.ApiKeyFilter;
import com.svtrucking.logistics.security.JwtAuthFilter;
import com.svtrucking.logistics.service.CustomUserDetailsService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity(prePostEnabled = true)
public class DriverAppApiSecurityConfig {
  private final JwtAuthFilter jwtAuthFilter;
  private final ApiKeyFilter apiKeyFilter;
  private final CustomUserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;

  public DriverAppApiSecurityConfig(
      JwtAuthFilter jwtAuthFilter,
      ApiKeyFilter apiKeyFilter,
      CustomUserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.apiKeyFilter = apiKeyFilter;
    this.userDetailsService = userDetailsService;
    this.passwordEncoder = passwordEncoder;
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins)
      throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(appCorsConfigurationSource(allowedOrigins)))
        .authorizeHttpRequests(authz ->
            authz
                .requestMatchers(
                    "/error",
                    "/api/public/**",
                    "/uploads/**",
                    "/ws",
                    "/ws/**",
                    "/ws-sockjs/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/privacy.html",
                    "/terms.html")
                .permitAll()
                .requestMatchers("/api/internal/**")
                .permitAll()
                .requestMatchers("/api/admin/**")
                .hasAnyAuthority("ROLE_" + RoleType.ADMIN.name(), "ROLE_" + RoleType.SUPERADMIN.name())
                .anyRequest()
                .authenticated())
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationProvider(authenticationProvider())
        .addFilterBefore(
            jwtAuthFilter,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(apiKeyFilter, JwtAuthFilter.class);

    return http.build();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(passwordEncoder);
    authProvider.setUserDetailsService(userDetailsService);
    return authProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  CorsConfigurationSource appCorsConfigurationSource(
      @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {
    CorsConfiguration cors = new CorsConfiguration();
    List<String> origins = parseAllowedOrigins(allowedOrigins);
    boolean allowAnyOrigin = origins.stream().anyMatch(o -> "*".equals(o));
    if (allowAnyOrigin) {
      cors.setAllowedOriginPatterns(List.of("*"));
      cors.setAllowCredentials(false);
    } else {
      cors.setAllowedOrigins(origins);
      cors.setAllowCredentials(true);
    }
    cors.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
    cors.setAllowedHeaders(
        Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Cache-Control",
            "Pragma",
            "x-request-id",
            "x-retried"));
    cors.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  private static List<String> parseAllowedOrigins(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return List.of("http://localhost:4200");
    }
    String[] parts = raw.split(",");
    List<String> out = new ArrayList<>();
    for (String p : parts) {
      String v = p == null ? "" : p.trim();
      if (!v.isEmpty()) {
        out.add(v);
      }
    }
    return out.isEmpty() ? List.of("http://localhost:4200") : out;
  }
}
