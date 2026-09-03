package com.prenotazioni.prenotazione;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Servizio prenotazioni: aule, corsi e prenotazioni.
 *
 * Il componentScan e' esplicito perche' questo servizio non vive piu' sotto
 * com.prenotazioni ma sotto com.prenotazioni.prenotazione, come gia' facevano
 * auth-service e notifica-service. Senza, i bean condivisi (JwtVerifier, JwtAuthFilter,
 * SecurityConfig, i due handler di errore, GlobalExceptionHandler) resterebbero fuori
 * dalla scansione: il servizio partirebbe senza filtro JWT e ogni endpoint sarebbe
 * raggiungibile senza token.
 *
 * Prima non serviva, ma solo perche' questo servizio condivideva il package radice con
 * shared - ed era proprio quella sovrapposizione il problema: rendeva il confine fra i
 * due moduli invisibile al compilatore.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.prenotazioni.prenotazione", "com.prenotazioni.config",
        "com.prenotazioni.security", "com.prenotazioni.exception"})
public class PrenotazioneServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrenotazioneServiceApplication.class, args);
    }
}
