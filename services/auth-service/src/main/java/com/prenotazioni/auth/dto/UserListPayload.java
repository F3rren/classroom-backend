package com.prenotazioni.auth.dto;

import lombok.Getter;

import java.util.List;

/** Lista utenti per GET /api/admin/users, senza password (UserSummaryDto). */
@Getter
public class UserListPayload {
    private final List<UserSummaryDto> users;
    private final int totalUsers;

    public UserListPayload(List<UserSummaryDto> users) {
        this.users = users;
        this.totalUsers = users.size();
    }
}
