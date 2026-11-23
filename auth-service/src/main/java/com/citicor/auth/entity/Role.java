package com.citicor.auth.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "roles")
    private List<User> users;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Predefined role constants matching ADR-005
    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    public static final String ROLE_KYC_OFFICER = "ROLE_KYC_OFFICER";
    public static final String ROLE_KYC_MANAGER = "ROLE_KYC_MANAGER";
    public static final String ROLE_SUPPORT_AGENT = "ROLE_SUPPORT_AGENT";
    public static final String ROLE_OPERATIONS_OFFICER = "ROLE_OPERATIONS_OFFICER";
    public static final String ROLE_COMPLIANCE_OFFICER = "ROLE_COMPLIANCE_OFFICER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    // Role hierarchy for authorization
    public static final java.util.Set<String> STAFF_ROLES = java.util.Set.of(
            ROLE_KYC_OFFICER,
            ROLE_KYC_MANAGER,
            ROLE_SUPPORT_AGENT,
            ROLE_OPERATIONS_OFFICER,
            ROLE_COMPLIANCE_OFFICER,
            ROLE_ADMIN
    );

    public static final java.util.Set<String> KYC_REVIEW_ROLES = java.util.Set.of(
            ROLE_KYC_OFFICER,
            ROLE_KYC_MANAGER
    );

    public static final java.util.Set<String> ADMIN_ROLES = java.util.Set.of(
            ROLE_ADMIN
    );
}