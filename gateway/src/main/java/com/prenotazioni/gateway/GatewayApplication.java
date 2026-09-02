package com.prenotazioni.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto di ingresso unico verso i servizi.
 *
 * Non c'e' altro codice oltre a questa classe: le rotte sono dichiarate in
 * application.yml, perche' sono configurazione e non logica. Aggiungere un servizio
 * significa aggiungere una voce a quel file, non ricompilare questo modulo.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
