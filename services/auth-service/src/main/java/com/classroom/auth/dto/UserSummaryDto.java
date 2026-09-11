package com.classroom.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.classroom.model.Role;
import com.classroom.auth.model.User;
import com.classroom.util.Timestamps;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The public view of a User (never the password), reused by GET /api/me,
 * GET /api/admin/users and the nested "user" object in the login response.
 *
 * registeredAt and lastLogin are omitted rather than null when unset, so the login response
 * - which does not include them today - stays byte for byte the same.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The public view of a user. It never carries the password")
public record UserSummaryDto(
        @Schema(description = "The user's id", example = "7")
        Long id,
        @Schema(description = "The unique username", example = "m.rossi")
        String username,
        @Schema(description = "The full name", example = "Mario Rossi")
        String name,
        @Schema(description = "The user's email address", example = "mario.rossi@example.it")
        String email,
        @Schema(description = "The application role", example = "user")
        Role role,
        @Schema(description = "The registration date. Omitted when not available", example = "2026-01-15 09:30:00")
        String registeredAt,
        @Schema(description = "The last login. Omitted when not available", example = "2026-08-31 14:05:00")
        String lastLogin) {

    public static UserSummaryDto basic(User user) {
        return new UserSummaryDto(
                user.getId(),
                user.getUsername() != null ? user.getUsername() : "",
                user.getName() != null ? user.getName() : "",
                user.getEmail() != null ? user.getEmail() : "",
                user.getRole(),
                null,
                null);
    }

    /** Used by GET /api/me: an unset lastLogin means "just now", a missing registeredAt is omitted. */
    public static UserSummaryDto forProfile(User user) {
        UserSummaryDto base = basic(user);
        // Timestamps.format returns null on null input, so a ternary here would be redundant
        String registeredAt = Timestamps.format(user.getRegisteredAt());
        String lastLogin = user.getLastLogin() != null
                ? Timestamps.format(user.getLastLogin())
                : Timestamps.now();
        return new UserSummaryDto(base.id(), base.username(), base.name(), base.email(), base.role(),
                registeredAt, lastLogin);
    }

    /** Used by GET /api/admin/users: a missing registeredAt or lastLogin becomes "" (never omitted). */
    public static UserSummaryDto forAdminListing(User user) {
        UserSummaryDto base = basic(user);
        String registeredAt = user.getRegisteredAt() != null
                ? Timestamps.format(user.getRegisteredAt()) : "";
        String lastLogin = user.getLastLogin() != null
                ? Timestamps.format(user.getLastLogin()) : "";
        return new UserSummaryDto(base.id(), base.username(), base.name(), base.email(), base.role(),
                registeredAt, lastLogin);
    }
}
