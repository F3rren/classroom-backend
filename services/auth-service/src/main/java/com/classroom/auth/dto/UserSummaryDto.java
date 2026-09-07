package com.classroom.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.classroom.model.Role;
import com.classroom.auth.model.User;
import com.classroom.util.Timestamps;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * The public view of a User (never the password), reused by GET /api/me,
 * GET /api/admin/users and the nested "user" object in the login response.
 *
 * registeredAt and lastLogin are omitted rather than null when unset, so the login response
 * - which does not include them today - stays byte for byte the same.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Schema(description = "The public view of a user. It never carries the password")
public class UserSummaryDto {

    @Schema(description = "The user's id", example = "7")
    private Long id;
    @Schema(description = "The unique username", example = "m.rossi")
    private String username;
    @Schema(description = "The full name", example = "Mario Rossi")
    private String name;
    @Schema(description = "The user's email address", example = "mario.rossi@example.it")
    private String email;
    @Schema(description = "The application role", example = "user")
    private Role role;
    @Schema(description = "The registration date. Omitted when not available", example = "2026-01-15 09:30:00")
    private String registeredAt;
    @Schema(description = "The last login. Omitted when not available", example = "2026-08-31 14:05:00")
    private String lastLogin;

    public static UserSummaryDto basic(User user) {
        UserSummaryDto dto = new UserSummaryDto();
        dto.id = user.getId();
        dto.username = user.getUsername() != null ? user.getUsername() : "";
        dto.name = user.getName() != null ? user.getName() : "";
        dto.email = user.getEmail() != null ? user.getEmail() : "";
        dto.role = user.getRole();
        return dto;
    }

    /** Used by GET /api/me: an unset lastLogin means "just now", a missing registeredAt is omitted. */
    public static UserSummaryDto forProfile(User user) {
        UserSummaryDto dto = basic(user);
        // Timestamps.format returns null on null input, so a ternary here would be redundant
        dto.registeredAt = Timestamps.format(user.getRegisteredAt());
        dto.lastLogin = user.getLastLogin() != null
                ? Timestamps.format(user.getLastLogin())
                : Timestamps.now();
        return dto;
    }

    /** Used by GET /api/admin/users: a missing registeredAt or lastLogin becomes "" (never omitted). */
    public static UserSummaryDto forAdminListing(User user) {
        UserSummaryDto dto = basic(user);
        dto.registeredAt = user.getRegisteredAt() != null
                ? Timestamps.format(user.getRegisteredAt()) : "";
        dto.lastLogin = user.getLastLogin() != null
                ? Timestamps.format(user.getLastLogin()) : "";
        return dto;
    }
}
