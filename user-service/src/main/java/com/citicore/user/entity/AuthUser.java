package com.citicore.user.entity;

/**
 * Represents the authenticated user extracted from the JWT token.
 * Set as the Spring Security principal by JwtAuthFilter.
 *
 * Accessible anywhere via:
 *   AuthUser user = (AuthUser) SecurityContextHolder.getContext()
 *                               .getAuthentication().getPrincipal();
 */
public class AuthUser {

    private final Long id;
    private final String email;
    private final String role;

    public AuthUser(Long id, String email, String role) {
        this.id    = id;
        this.email = email;
        this.role  = role;
    }

    public Long getId()     { return id; }
    public String getEmail(){ return email; }
    public String getRole() { return role; }
}