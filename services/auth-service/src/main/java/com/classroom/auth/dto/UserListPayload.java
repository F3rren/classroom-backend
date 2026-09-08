package com.classroom.auth.dto;

import lombok.Getter;

import java.util.List;

/** The user list for GET /api/admin/users, without passwords (UserSummaryDto). */
@Getter
public class UserListPayload {
    private final List<UserSummaryDto> users;
    private final int totalUsers;

    public UserListPayload(List<UserSummaryDto> users) {
        this.users = users;
        this.totalUsers = users.size();
    }
}
