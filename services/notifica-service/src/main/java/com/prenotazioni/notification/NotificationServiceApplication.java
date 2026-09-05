package com.prenotazioni.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Servizio notifiche.
 *
 * Il componentScan risale a com.prenotazioni perche' i bean condivisi (JwtVerifier,
 * JwtAuthFilter, SecurityConfig, i due handler di errore) vivono nel modulo shared sotto
 * quel package: senza, questo servizio partirebbe senza filtro JWT e ogni endpoint
 * risulterebbe raggiungibile senza token.
 *
 * Le entita' e i repository restano confinati al package di questo servizio, cosi' non
 * puo' accidentalmente mappare tabelle che non possiede.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.prenotazioni.notification", "com.prenotazioni.config", "com.prenotazioni.security", "com.prenotazioni.exception"})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
