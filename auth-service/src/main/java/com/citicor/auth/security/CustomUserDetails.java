package com.citicor.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public class CustomUserDetails implements UserDetails {

    private Long id;
    private String email;
    private String password;
    private List<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id, String email, String password,
                             List<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public List<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}