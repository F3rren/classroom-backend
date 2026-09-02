package com.prenotazioni.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * Servizio utenti.
 *
 * E' l'unico servizio che FIRMA i token: possiede jjwt-impl e la tabella utenti. Gli altri
 * si limitano a verificarli, con JwtVerifier in shared, conoscendo lo stesso segreto. E'
 * cio' che permette a booking-service di autorizzare una richiesta senza mai chiamare
 * questo servizio.
 *
 * Il componentScan risale a com.prenotazioni per raccogliere i bean condivisi (JwtVerifier,
 * JwtAuthFilter, SecurityConfig, i due handler di errore), che vivono in shared sotto quel
 * package: senza, il servizio partirebbe senza filtro JWT.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.prenotazioni.auth", "com.prenotazioni.setting", "com.prenotazioni.security", "com.prenotazioni.exception"})
// com.prenotazioni.model serve per Ruolo.JpaConverter, che e' @Converter(autoApply = true)
// ma vive in shared: fuori dal raggio di scansione l'autoApply non si applica, e Hibernate
// tornerebbe a mappare l'enum per ORDINALE. Il sintomo qui e' un DDL che fallisce, ma su uno
// schema gia' esistente sarebbe peggio: scriverebbe 0 e 1 al posto di 'admin' e 'user',
// violando utente_ruolo_check e rendendo illeggibili i dati agli altri servizi.
@EntityScan(basePackages = {"com.prenotazioni.auth.model", "com.prenotazioni.model"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
