package com.classroom.auth.dto;

import com.classroom.model.Role;
import com.classroom.auth.model.User;
import lombok.Getter;

/** Summary of a user just updated by an admin (PUT /api/admin/users/{id}). */
@Getter
public class UserUpdateAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final String name;
    private final Role role;

    public UserUpdateAck(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.name = user.getName() != null ? user.getName() : "";
        this.role = user.getRole();
    }
}
