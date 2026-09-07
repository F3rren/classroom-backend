package com.classroom.auth.dto;

import lombok.Value;

/** Confirmation that a user was deleted (DELETE /api/admin/users/{id}). */
@Value
public class DeletedUserResponse {
    Long deletedUserId;
}
