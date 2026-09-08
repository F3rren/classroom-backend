package com.classroom.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * The booking service: rooms, courses and bookings.
 *
 * The componentScan is explicit because this service no longer lives under
 * com.classroom but under com.classroom.booking, as auth-service and
 * notification-service already did. Without it the shared beans (JwtVerifier, JwtAuthFilter,
 * SecurityConfig, the two error handlers, GlobalExceptionHandler) would fall outside the
 * scan: the service would start with no JWT filter and every endpoint would be reachable
 * without a token.
 *
 * It was not needed before, but only because this service shared its root package with
 * shared - and that overlap was itself the problem: it made the boundary between the two
 * modules invisible to the compiler.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.classroom.booking", "com.classroom.config",
        "com.classroom.security", "com.classroom.exception"})
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
