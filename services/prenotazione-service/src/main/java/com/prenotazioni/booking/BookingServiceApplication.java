package com.prenotazioni.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * The booking service: rooms, courses and bookings.
 *
 * The componentScan is explicit because this service no longer lives under
 * com.prenotazioni but under com.prenotazioni.booking, as auth-service and
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
@ComponentScan(basePackages = {"com.prenotazioni.booking", "com.prenotazioni.config",
        "com.prenotazioni.security", "com.prenotazioni.exception"})
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
