package com.citicor.auth.config;

import com.citicor.auth.security.CustomAuthEntryPoint;
import com.citicor.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomAuthEntryPoint customAuthEntryPoint;
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, CustomAuthEntryPoint customAuthEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.customAuthEntryPoint = customAuthEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())  // Disable CSRF
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()  // Public auth endpoints
                        .requestMatchers("/h2-console/**").permitAll()  // Public H2 console
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()  // All other requests need authentication
                )
                .sessionManagement(sess -> sess
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // Stateless sessions (for JWT)
                )
                .headers(headers -> headers
                        // Allow inline scripts and styles for the H2 Console (Not recommended for production)
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'self';")
                        )
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthEntryPoint)
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();  // Password encoder for user passwords
    }
}