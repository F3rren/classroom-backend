package com.prenotazioni.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica le DECISIONI di instradamento, non i servizi a valle.
 *
 * Le rotte puntano a una porta su cui non ascolta nessuno, e questo rende le due
 * risposte distinguibili senza avviare alcun servizio ne' uno stub HTTP:
 *
 *  - un percorso instradato prova a raggiungere il servizio e fallisce con 5xx;
 *  - un percorso bloccato o sconosciuto risponde 404 senza uscire dal gateway.
 *
 * Cio' che si verifica e' quindi esattamente la scelta del gateway, che e' la sua
 * unica responsabilita'.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.gateway.routes[0].id=notifiche-interne-bloccate",
        "spring.cloud.gateway.routes[0].uri=forward:/rotta-non-esposta",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/notifiche/interne/**",
        "spring.cloud.gateway.routes[1].id=notifiche",
        "spring.cloud.gateway.routes[1].uri=http://localhost:9",
        "spring.cloud.gateway.routes[1].predicates[0]=Path=/api/notifiche/**",
        "spring.cloud.gateway.routes[2].id=applicazione",
        "spring.cloud.gateway.routes[2].uri=http://localhost:9",
        "spring.cloud.gateway.routes[2].predicates[0]=Path=/api/auth/**,/api/me/**,/api/rooms/**,/api/prenotazioni/**,/api/admin/**"
})
class InstradamentoTest {

    @Autowired
    private WebTestClient client;

    @Test
    void leRotteInterneNonSonoRaggiungibiliDallEsterno() {
        // 404 e non 5xx: la richiesta non e' nemmeno partita verso notifica-service.
        // E' il controllo piu' importante del file: quelle rotte creano notifiche
        // arbitrarie e devono restare una conversazione fra servizi.
        client.post().uri("/api/notifiche/interne/cancellazione-prenotazione")
                .exchange()
                .expectStatus().isNotFound();

        client.delete().uri("/api/notifiche/interne/utente/1")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void lePathPubblicheDelleNotificheVengonoInstradate() {
        // 5xx: il gateway ha deciso di inoltrare e non ha trovato nessuno in ascolto.
        // E' la prova che la rotta e' stata riconosciuta.
        client.get().uri("/api/notifiche")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void leRottePrincipaliDellApplicazioneVengonoInstradate() {
        for (String percorso : new String[]{"/api/rooms", "/api/prenotazioni", "/api/me", "/api/admin/users"}) {
            client.get().uri(percorso)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    @Test
    void ilLoginVieneInstradatoComeIlResto() {
        // Rotta pubblica, ma per il gateway non e' un caso speciale: non valida token,
        // quindi non deve distinguere fra rotte protette e no.
        client.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void unPercorsoSconosciutoRestaFuori() {
        client.get().uri("/api/inventato")
                .exchange()
                .expectStatus().isNotFound();
    }
}
