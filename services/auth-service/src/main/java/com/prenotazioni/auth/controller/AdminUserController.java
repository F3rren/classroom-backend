package com.prenotazioni.auth.controller;

import com.prenotazioni.config.RequestCorrelationFilter;
import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.dto.DeletedUserResponse;
import com.prenotazioni.auth.dto.UpdateUserRequest;
import com.prenotazioni.auth.dto.UserListPayload;
import com.prenotazioni.auth.dto.UserRegisterAck;
import com.prenotazioni.auth.dto.UserSummaryDto;
import com.prenotazioni.auth.dto.UserUpdateAck;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.auth.service.AuthService;
import com.prenotazioni.auth.service.UserService;
import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.util.LogSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AdminUserController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserController.class);

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

    @PostMapping
    @Operation(summary = "Create a new user (admin only)")
    public ResponseEntity<ApiEnvelope<UserRegisterAck>> register(@Valid @RequestBody CreateUserRequest request) {
        String sessionId = generateSessionId();
        logger.debug("register - user created by an admin | {} | role={}", LogSanitizer.maskEmail(request.getEmail()), request.getRole());

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

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user (admin only)")
    public ResponseEntity<ApiEnvelope<UserUpdateAck>> updateUser(@PathVariable("id") Long id, @Valid @RequestBody UpdateUserRequest request) {
        String sessionId = generateSessionId();
        logger.debug("updateUser - userId={} role={}", id, request.getRole());

        if (id == null || id <= 0) {
            logger.warn("END updateUser - invalid user ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_USER_ID", "Invalid user id",
                                  "L'ID dell'utente deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

        User updated = authService.updateUser(id, request);
        logger.info("Utente modificato da admin - utenteId={}", updated.getId());

        return new ResponseEntity<>(
            createSuccessResponse("Utente aggiornato con successo dall'amministratore", new UserUpdateAck(updated), sessionId),
            HttpStatus.OK
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user and their data (admin only)")
    public ResponseEntity<ApiEnvelope<DeletedUserResponse>> deleteUser(@PathVariable("id") Long id) {
        String sessionId = generateSessionId();
        logger.debug("START deleteUser - user ID: {}", id);

        if (id == null || id <= 0) {
            logger.warn("END deleteUser - invalid user ID: {}", id);
            return new ResponseEntity<>(
                createErrorResponse("INVALID_USER_ID", "Invalid user id",
                                  "L'ID dell'utente deve essere un numero positivo valido.", sessionId),
                HttpStatus.BAD_REQUEST
            );
        }

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
