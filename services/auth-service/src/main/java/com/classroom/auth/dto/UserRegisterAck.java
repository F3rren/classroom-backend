package com.classroom.auth.dto;

import com.classroom.model.Role;
import com.classroom.auth.model.User;

/** Summary of a user just created by an admin (POST /api/admin/users). */
public record UserRegisterAck(Long userId, String email, String username, Role role) {

    public UserRegisterAck(User user) {
        this(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
    }
}
