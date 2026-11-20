


package com.citicore.account.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtUtils {

    private final SecretKey key;

    @Value("${jwt.secret}")
    private String secret;

    public JwtUtils(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET environment variable not set");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        if (secret.equals("CHANGE_ME_IN_PRODUCTION_MIN_32_CHARS") ||
            secret.equals("local-dev-secret-minimum-32-characters-long-for-hs256") ||
            secret.equals("test-secret-minimum-32-characters-long-for-hs256")) {
            throw new IllegalStateException("JWT_SECRET is using default/placeholder value - change in production!");
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return extractClaims(token).get("userId",Long.class);
    }
    public String getEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public List<String> getRoles(String token) {
        Claims claims = extractClaims(token);

        Object rolesObj = claims.get("roles");

        if (rolesObj == null) {
            return List.of();
        }

        if (rolesObj instanceof List<?>) {
            return ((List<?>) rolesObj)
                    .stream()
                    .map(Object::toString)
                    .toList();
        }

        return List.of();
    }
}
