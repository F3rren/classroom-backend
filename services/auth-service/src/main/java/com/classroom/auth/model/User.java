package com.classroom.auth.model;

import com.classroom.model.Role;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A user of the system.
 *
 * THE ROLE IS STORED LOWERCASE ('admin' or 'user'), and that is not cosmetic: the column has
 * a CHECK constraint admitting exactly those two values, and the JSON carries the same
 * strings, so a client comparing role === "admin" keeps working. Role.JpaConverter is what
 * holds both ends to it.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "users")
// When a User is referenced as a LAZY relation, Hibernate loads it as a proxy subclass that
// adds a public "hibernateLazyInitializer" getter; without this exclusion Jackson tries to
// serialise it and fails with InvalidDefinitionException (no serialiser for
// ByteBuddyInterceptor).
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @ToString.Include
    private String username;

    @Column(nullable = false, length = 100)
    @ToString.Include
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    @ToString.Include
    private String email;

    // Left out of toString on purpose - onlyExplicitlyIncluded=true already means "excluded
    // unless marked", so this is the same guarantee the old @ToString.Exclude gave, just
    // expressed as an absence instead of an exclusion.
    @Column(nullable = false, length = 255)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(nullable = false, length = 20)
    // Stored lowercase by Role's converter (CHECK constraint user_role_check)
    @ToString.Include
    private Role role;

    @Column(name = "registered_at", nullable = false, updatable = false)
    @ToString.Include
    private LocalDateTime registeredAt;

    @Column(name = "last_login")
    @ToString.Include
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        if (registeredAt == null) {
            registeredAt = LocalDateTime.now();
        }
        // No case normalisation here: conversion from a string goes through Role.from(),
        // which accepts any case and always returns the right constant.
        if (role == null) {
            role = Role.USER;
        }
    }

}
