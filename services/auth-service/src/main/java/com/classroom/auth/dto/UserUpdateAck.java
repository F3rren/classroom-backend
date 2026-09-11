package com.classroom.auth.dto;

import com.classroom.model.Role;
import com.classroom.auth.model.User;

/** Summary of a user just updated by an admin (PUT /api/admin/users/{id}). */
public record UserUpdateAck(Long userId, String email, String username, String name, Role role) {

    public UserUpdateAck(User user) {
        this(user.getId(), user.getEmail(), user.getUsername(),
                user.getName() != null ? user.getName() : "", user.getRole());
    }
}
