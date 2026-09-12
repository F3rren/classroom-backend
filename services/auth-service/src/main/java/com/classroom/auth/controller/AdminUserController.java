package com.classroom.auth.controller;

import com.classroom.config.RequestCorrelationFilter;
import com.classroom.auth.dto.CreateUserRequest;
import com.classroom.auth.dto.DeletedUserResponse;
import com.classroom.auth.dto.UpdateUserRequest;
import com.classroom.auth.dto.UserListPayload;
import com.classroom.auth.dto.UserRegisterAck;
import com.classroom.auth.dto.UserSummaryDto;
import com.classroom.auth.dto.UserUpdateAck;
import com.classroom.auth.model.User;
import com.classroom.auth.service.AuthService;
import com.classroom.auth.service.UserService;
import com.classroom.dto.ApiEnvelope;
import com.classroom.util.LogSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User administration.
 *
 * These endpoints used to sit in AdminController together with those for rooms and
 * bookings: a single controller injecting five services from three different domains. The
 * path stays /api/admin/... because that is what the frontend calls, but it is now the
 * gateway that decides which service serves it.
 *
 * hasRole('ADMIN') works here exactly as before: the role comes from the token, so nothing
 * has to be queried in order to authorise.
 */
@RestController
// A prefix of its own, not shared with booking-service. Both services used to expose
// /api/admin and the gateway told them apart by listing this one's exact paths: a list
// copied from here that nobody kept in step. Adding an endpoint would have sent it silently
// to the other service, with a 404 and no explanation.
@RequestMapping("/api/admin/users")
@Tag(name = "User administration")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminUserController {

    /**
     * On the annotation and not as an if at the top of each method, which is how it used to
     * be written - here and in booking-service, eight copies in three different Italian
     * wordings of the identical rule. A constant because an annotation attribute has to be a
     * compile-time one; private because this is the only class in this service that takes a
     * user id from the path.
     */
    private static final String USER_ID_POSITIVE = "L'ID dell'utente deve essere un numero positivo.";

    private final AuthService authService;
    private final UserService userService;

    AdminUserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** The same id the error handler will see, not a different one. */
    private String generateSessionId() {
        return RequestCorrelationFilter.current();
    }

    private <T> ApiEnvelope<T> createErrorResponse(String errorCode, String message, String userMessage, String sessionId) {
        return ApiEnvelope.error(errorCode, message, userMessage, sessionId);
    }

    private <T> ApiEnvelope<T> createSuccessResponse(String message, T data, String sessionId) {
        return ApiEnvelope.success(message, data, sessionId);
    }

    // @ModelAttribute, not @RequestBody: query parameters, one per field, is what turns this
    // into fillable inputs in Swagger UI instead of a JSON box to type by hand. Springdoc
    // reads the same @Schema on CreateUserRequest's fields either way, so nothing there
    // changed - only how the values arrive. Login stays JSON-bodied on purpose: unlike this
    // one, its password must never sit in a URL, where it would reach access logs, browser
    // history and any Referer header sent afterwards.
    @PostMapping
    @Operation(summary = "Create a new user (admin only)")
    public ResponseEntity<ApiEnvelope<UserRegisterAck>> register(@Valid @ModelAttribute CreateUserRequest request) {
        String sessionId = generateSessionId();
        logger.debug("register - user created by an admin | {} | role={}", LogSanitizer.maskEmail(request.email()), request.role());

        User user = authService.register(request);

        logger.info("User created by an admin - userId={} role={}", user.getId(), user.getRole());

        return new ResponseEntity<>(
            createSuccessResponse("Utente registrato con successo dall'amministratore", new UserRegisterAck(user), sessionId),
            HttpStatus.CREATED
        );
    }

    @GetMapping
    @Operation(summary = "List every user (admin only)")
    public ResponseEntity<ApiEnvelope<UserListPayload>> getAllUsers() {
        String sessionId = generateSessionId();
        logger.debug("START getAllUsers - full user list requested");

        List<User> users = authService.getAllUsers();
        List<UserSummaryDto> safeUsers = users.stream()
            .map(UserSummaryDto::forAdminListing)
            .collect(Collectors.toList());

        logger.debug("END getAllUsers - users fetched, total: {}", users.size());
        return new ResponseEntity<>(
            createSuccessResponse("Lista utenti recuperata con successo", new UserListPayload(safeUsers), sessionId),
            HttpStatus.OK
        );
    }

    // Same reasoning as register(): query parameters instead of a JSON body, for real form
    // fields in Swagger UI. The password field stays here too, but it is OPTIONAL for an
    // update (empty means "leave it unchanged" - see UpdateUserRequest) and this is an
    // admin-only, ADMIN-INITIATED action, not the credential a user types to log themselves
    // in - the login endpoint is the one this project keeps out of any URL.
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user (admin only)")
    public ResponseEntity<ApiEnvelope<UserUpdateAck>> updateUser(
            @PathVariable("id") @Positive(message = USER_ID_POSITIVE) Long id,
            @Valid @ModelAttribute UpdateUserRequest request) {
        String sessionId = generateSessionId();
        logger.debug("updateUser - userId={} role={}", id, request.role());

        User updated = authService.updateUser(id, request);
        logger.info("User updated by an admin - userId={}", updated.getId());

        return new ResponseEntity<>(
            createSuccessResponse("Utente aggiornato con successo dall'amministratore", new UserUpdateAck(updated), sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user and their data (admin only)")
    public ResponseEntity<ApiEnvelope<DeletedUserResponse>> deleteUser(
            @PathVariable("id") @Positive(message = USER_ID_POSITIVE) Long id) {
        String sessionId = generateSessionId();
        logger.debug("START deleteUser - user ID: {}", id);

        if (userService.findById(id) == null) {
            logger.warn("END deleteUser - user not found - ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("USER_NOT_FOUND", "Utente not found or not deletable",
                                  String.format("L'utente con ID %d non esiste o non può essere eliminato.", id), sessionId),
                HttpStatus.NOT_FOUND
            );
        }

        // Cascades through the user's notifications and bookings before the user itself.
        // See UserService.deleteById: the cascade is no longer atomic, and the order is what
        // keeps the operation repeatable when part of it fails.
        userService.deleteById(id);

        logger.debug("END deleteUser - user deleted - ID: {}", id);
        return new ResponseEntity<>(
            createSuccessResponse("Utente eliminato con successo", new DeletedUserResponse(id), sessionId),
            HttpStatus.OK
        );
    }

    // ==================== room management endpoints ====================
}
