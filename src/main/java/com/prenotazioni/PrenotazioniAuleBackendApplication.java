package com.prenotazioni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class PrenotazioniAuleBackendApplication {

    private static final Logger logger = LoggerFactory.getLogger(PrenotazioniAuleBackendApplication.class);

    public static void main(String[] args) {
        logger.info("Azione completata con successo");
        SpringApplication.run(PrenotazioniAuleBackendApplication.class, args);
    }
}
