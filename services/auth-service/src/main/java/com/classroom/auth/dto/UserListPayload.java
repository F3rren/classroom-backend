package com.classroom.auth.dto;

import java.util.List;

/** The user list for GET /api/admin/users, without passwords (UserSummaryDto). */
public record UserListPayload(List<UserSummaryDto> users, int totalUsers) {

    public UserListPayload(List<UserSummaryDto> users) {
        this(users, users.size());
    }
}
