package com.prenotazioni.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * The user service.
 *
 * It is the only service that SIGNS tokens: it owns jjwt-impl and the users table. The
 * others merely verify them, with JwtVerifier in shared, knowing the same secret. That is
 * what lets booking-service authorise a request without ever calling this service.
 *
 * The componentScan reaches up to com.prenotazioni to pick up the shared beans (JwtVerifier,
 * JwtAuthFilter, SecurityConfig, the two error handlers), which live in shared under that
 * package: without it the service would start with no JWT filter.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.prenotazioni.auth", "com.prenotazioni.config", "com.prenotazioni.security", "com.prenotazioni.exception"})
// com.prenotazioni.model is here for Role.JpaConverter, which is @Converter(autoApply =
// true) but lives in shared: outside the scan range the autoApply does not take effect, and
// Hibernate would go back to mapping the enum by ORDINAL. The symptom here is a DDL that
// fails, but on an existing schema it would be worse: it would write 0 and 1 in place of
// 'admin' and 'user', violating user_role_check and making the data unreadable to the other
// services.
@EntityScan(basePackages = {"com.prenotazioni.auth.model", "com.prenotazioni.model"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
