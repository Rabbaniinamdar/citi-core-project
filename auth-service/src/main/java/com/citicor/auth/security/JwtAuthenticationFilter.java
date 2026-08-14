package com.citicor.auth.security;

import com.citicor.auth.exception.AuthException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getServletPath();

        if (path.startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }
        // 1. Get Authorization header
        final String authHeader = request.getHeader("Authorization");

        String jwt = null;
        String username = null;

        // 2. Check Bearer token

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7); // remove "Bearer "
                try {
                    username = jwtUtils.extractUsername(jwt);
                } catch (Exception e) {
                    // ❗ DO NOT BREAK REQUEST HERE
                }
            }
            // 3. If username exists and not already authenticated
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 4. Validate token
                if (isTokenValid(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // 5. Set authentication in context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            throw new AuthException("Token expired", HttpStatus.UNAUTHORIZED);
        } catch (MalformedJwtException ex) {
            throw new AuthException("Malformed JWT token", HttpStatus.UNAUTHORIZED);
        } catch (SignatureException ex) {
            throw new AuthException("Invalid JWT signature", HttpStatus.UNAUTHORIZED);
        } catch (Exception ex) {
            throw new AuthException("Invalid JWT token", HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = jwtUtils.extractUsername(token);
        return username.equals(userDetails.getUsername());
    }
}