package com.classroom.auth.dto;

/** Confirmation that a user was deleted (DELETE /api/admin/users/{id}). */
public record DeletedUserResponse(Long deletedUserId) {
}
