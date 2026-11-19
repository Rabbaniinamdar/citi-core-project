package com.citicore.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Config Server security configuration.
 *
 * The encrypt/decrypt endpoints are the only actuator endpoints that accept
 * POST bodies and must not be left unauthenticated. This filter chain locks
 * them down. All other endpoints are read-only and are left open so that
 * microservices can resolve configuration without a shared secret.
 *
 * The Config Server must NOT be publicly reachable. It is exposed only to
 * authorized clients through the approved network and authentication design
 * (see documentation/engineering/configuration-management.md).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain configServerFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(
                request -> request.getRequestURI().startsWith("/encrypt")
                    || request.getRequestURI().startsWith("/decrypt")
            )
            .authorizeHttpRequests(authz -> authz
                .anyRequest().denyAll()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/encrypt", "/decrypt"));
        return http.build();
    }
}