package com.classroom.auth;

import com.classroom.auth.dto.CreateUserRequest;
import com.classroom.auth.model.User;
import com.classroom.auth.repository.UserRepository;
import com.classroom.auth.service.AuthService;
import com.classroom.util.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on an empty users database.
 *
 * It exists to undo a knot: AdminUserController is annotated @PreAuthorize hasRole('ADMIN')
 * at class level, so /api/admin/users requires it too. On a freshly created database you
 * need an administrator token to create the first administrator, and the only way out was a
 * hand-written INSERT - to be redone in every new environment, with the password hash
 * computed somewhere else.
 *
 * THE ONE CONDITION THAT MAKES THIS CLASS HARMLESS, and it must be held tightly: it acts
 * only on an EMPTY table. It promotes nobody, updates nobody, touches no existing user. On a
 * populated database it returns before looking at anything else. If somebody one day added a
 * branch that writes to a non-empty table, this would stop being a help at startup and
 * become a shortcut to administrator privileges.
 *
 * It goes through AuthService.register and not through the repository: that way the password
 * passes the same PasswordEncoder and the same checks as any other user, and there is no
 * second way of creating one that could drift from the first.
 */
@Slf4j
@Component
public class FirstAdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final String email;
    private final String password;
    private final String name;

    FirstAdminBootstrap(UserRepository userRepository,
                    AuthService authService,
                    @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
                    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
                    @Value("${BOOTSTRAP_ADMIN_NAME:Amministratore}") String name) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        if (email.isBlank() || password.isBlank()) {
            // At WARN and not in silence: an empty users database plus a service that starts
            // saying nothing is exactly how this problem comes back, and the next person
            // ends up in front of it with no clue.
            logger.warn("No users in the database and no initial administrator to create. "
                    + "Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD in .env and "
                    + "restart, or insert the first administrator by hand: without one, "
                    + "/api/admin/users is unreachable because it requires an admin token.");
            return;
        }

        CreateUserRequest request = new CreateUserRequest(email, email, password, "admin", name);

        User created = authService.register(request);
        logger.info("First administrator created on an empty database - userId={} email={}. "
                + "Change the password on first login and clear BOOTSTRAP_ADMIN_PASSWORD in .env.",
                created.getId(), LogSanitizer.maskEmail(email));
    }
}
