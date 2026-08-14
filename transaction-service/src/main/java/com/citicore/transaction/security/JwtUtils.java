


package com.citicore.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtUtils {

    private final SecretKey key;

    public JwtUtils(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
