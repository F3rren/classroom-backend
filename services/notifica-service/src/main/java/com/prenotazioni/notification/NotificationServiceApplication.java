package com.prenotazioni.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * The notification service.
 *
 * The componentScan reaches up to com.prenotazioni because the shared beans (JwtVerifier,
 * JwtAuthFilter, SecurityConfig, the two error handlers) live in the shared module under
 * that package: without it this service would start with no JWT filter, and every endpoint
 * would be reachable without a token.
 *
 * Entities and repositories stay confined to this service's own package, so it cannot
 * accidentally map tables it does not own.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.prenotazioni.notification", "com.prenotazioni.config", "com.prenotazioni.security", "com.prenotazioni.exception"})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
