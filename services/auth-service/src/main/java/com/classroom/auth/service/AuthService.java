package com.classroom.auth.service;

import com.classroom.auth.model.User;
import com.classroom.exception.DomainConflictException;
import com.classroom.auth.exception.ResourceType;
import com.classroom.model.Role;
import com.classroom.auth.repository.UserRepository;
import com.classroom.auth.dto.CreateUserRequest;
import com.classroom.auth.dto.UpdateUserRequest;
import com.classroom.util.LogSanitizer;

import java.util.List;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            // WARN and not INFO: a failed login is a security signal (brute force,
            // compromised credentials) and has to stay visible when the level is raised.
            logger.warn("Login failed - invalid credentials for {}", LogSanitizer.maskEmail(email));
            // This null STAYS, unlike the others in this class: it has one possible meaning,
            // wrong credentials, so the caller has nothing to infer. The AMBIGUOUS null was
            // the problem, not null itself.
            return null;
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        logger.info("Login succeeded - userId={} ({})", user.getId(), LogSanitizer.maskEmail(email));
        return user;
    }

    public User register(CreateUserRequest request) {
        // Is the email or the username already registered?
        if (userRepository.findByEmail(request.getEmail()) != null) {
            // The code stays USER_ALREADY_EXISTS, already exposed and truthful. What changed
            // is the message: it used not to say WHICH of the two fields was in conflict, and
            // whoever read it did not know what to correct.
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Email already registered",
                    "Questa email e' gia' associata a un altro utente.");
        }
        if (userRepository.findByUsername(request.getUsername()) != null) {
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Username already registered: " + request.getUsername(),
                    "Questo username e' gia' in uso.");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.from(request.getRole()));
        user.setUsername(request.getUsername());

        // The registration date is set once here and never changed afterwards.
        user.setRegisteredAt(LocalDateTime.now());
        // lastLogin is only ever updated by a login.
        User saved = userRepository.save(user);
        logger.info("User created - userId={} ({})", saved.getId(), LogSanitizer.maskEmail(saved.getEmail()));
        return saved;
    }

    public List<User> getAllUsers() {
        List<User> users = userRepository.findAll();
        logger.debug("User list fetched - total={}", users.size());
        return users;
    }

    public User updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw ResourceType.USER.notFoundById(id);
        }

        // Is the new email or username already taken by somebody else?
        User userWithEmail = userRepository.findByEmail(request.getEmail());
        if (userWithEmail != null && !userWithEmail.getId().equals(id)) {
            // 409 and no longer 404: this case used to return the same null as "no such
            // user", and the answer said "user not found" about a user that existed
            // perfectly well. That was a false answer, not merely an imprecise one.
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Email already used by another user",
                    "Questa email e' gia' associata a un altro utente.");
        }
        User userWithUsername = userRepository.findByUsername(request.getUsername());
        if (userWithUsername != null && !userWithUsername.getId().equals(id)) {
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Username already used by another user",
                    "Questo username e' gia' in uso.");
        }
        // The fields that may change.
        user.setEmail(request.getEmail());
        user.setName(request.getName());

        // The password changes only when a new one is actually supplied.
        boolean passwordChanged = request.getPassword() != null && !request.getPassword().trim().isEmpty();
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // The FORM of the role (admin|user, case-insensitive) is already guaranteed by
        // @Pattern on the DTO; what is left here is the normalisation and the fallback for a
        // role that was not supplied at all.
        Role role = request.getRole() != null ? Role.from(request.getRole()) : user.getRole();
        user.setRole(role);
        user.setUsername(request.getUsername());

        // registeredAt is deliberately NOT touched - it keeps its original value.
        // lastLogin is only ever updated by a login.
        User saved = userRepository.save(user);
        logger.info("User updated - userId={} passwordChanged={}", id, passwordChanged);
        return saved;
    }
}
