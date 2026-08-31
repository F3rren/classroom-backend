package com.prenotazioni.dto;

import java.util.List;

/** Lista utenti per GET /api/admin/users, senza password (UserSummaryDto). */
public class UserListPayload {
    private final List<UserSummaryDto> users;
    private final int totalUsers;

    public UserListPayload(List<UserSummaryDto> users) {
        this.users = users;
        this.totalUsers = users.size();
    }

    public List<UserSummaryDto> getUsers() { return users; }
    public int getTotalUsers() { return totalUsers; }
}
