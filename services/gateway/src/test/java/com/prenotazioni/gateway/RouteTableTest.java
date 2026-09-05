package com.prenotazioni.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La tabella delle rotte VERA, quella di application.yml.
 *
 * Serve perche' InstradamentoTest, che sembra coprire l'instradamento, in realta' dichiara
 * le proprie rotte in @TestPropertySource: verifica il meccanismo del gateway su una tabella
 * sintetica, non su quella che gira in produzione. Un errore nella tabella vera passerebbe
 * di li' senza che nessuno se ne accorga.
 *
 * Cio' che va tenuto fermo e' soprattutto UN ORDINE. Due servizi diversi espongono
 * /api/admin: auth-service sotto /api/admin/utenti, prenotazione-service tutto il resto.
 * Spring Cloud Gateway valuta le rotte nell'ordine in cui sono dichiarate, quindi quella
 * piu' specifica deve venire prima. Se qualcuno le riordinasse, /api/admin/utenti finirebbe
 * a prenotazione-service e risponderebbe 404 - senza errori di configurazione, senza log,
 * e senza niente che indichi il perche'.
 */
@SpringBootTest
class RouteTableTest {

    @Autowired
    private RouteLocator rotte;

    /** L'id della prima rotta che accetta il percorso, come farebbe il gateway. */
    private String primaRottaChePrende(String percorso) {
        ServerWebExchange scambio = MockServerWebExchange.from(MockServerHttpRequest.get(percorso).build());
        List<Route> ordinate = rotte.getRoutes().collectList().block();
        assertThat(ordinate).as("nessuna rotta caricata: application.yml non e' stato letto").isNotEmpty();
        for (Route r : ordinate) {
            if (Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(scambio)).block())) {
                return r.getId();
            }
        }
        return null;
    }

    /** L'indirizzo a cui una rotta manda, per distinguere i servizi a valle. */
    private String destinazioneDi(String idRotta) {
        return rotte.getRoutes()
                .filter(r -> r.getId().equals(idRotta))
                .map(r -> r.getUri().toString())
                .blockFirst();
    }

    @Test
    void gliUtentiAmministrativiVannoAlServizioUtenti() {
        // LA regressione da tenere chiusa: questa rotta e' dichiarata PRIMA di quella
        // generica su /api/admin/**, ed e' l'ordine a farla vincere.
        assertThat(primaRottaChePrende("/api/admin/utenti")).isEqualTo("autenticazione");
        assertThat(primaRottaChePrende("/api/admin/utenti/42")).isEqualTo("autenticazione");
    }

    @Test
    void ilRestoDiAdminVaAlServizioPrenotazioni() {
        assertThat(primaRottaChePrende("/api/admin/rooms")).isEqualTo("applicazione");
        assertThat(primaRottaChePrende("/api/admin/prenotazioni")).isEqualTo("applicazione");
    }

    @Test
    void soloLOrdineDecideChiRiceveGliUtentiAmministrativi() {
        // Senza questo, i due test sopra potrebbero passare per costruzione: se
        // /api/admin/utenti corrispondesse a una rotta sola, l'ordine non conterebbe e non
        // ci sarebbe niente da tenere fermo. Qui si pretende che ENTRAMBE lo accettino,
        // cosi' l'unica cosa che manda la richiesta al servizio giusto e' la posizione.
        ServerWebExchange scambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/utenti").build());
        List<String> chiLoAccetta = rotte.getRoutes()
                .filter(r -> Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(scambio)).block()))
                .map(Route::getId)
                .collectList().block();

        assertThat(chiLoAccetta).containsExactly("autenticazione", "applicazione");
    }

    @Test
    void leDueRotteAdminPuntanoAServiziDiversi() {
        // Se puntassero allo stesso, l'ordine non conterebbe e questi test non
        // proverebbero niente: e' cio' che rende significativi i due sopra.
        assertThat(destinazioneDi("autenticazione")).isNotEqualTo(destinazioneDi("applicazione"));
    }

    @Test
    void leRotteInterneRestanoFuoriDallaPortata() {
        // Sono chiamate da altri servizi, non dal browser: esporle darebbe a chiunque abbia
        // un token da admin la possibilita' di fabbricare notifiche arbitrarie.
        assertThat(primaRottaChePrende("/api/notifiche/interne/utente/1")).isEqualTo("notifiche-interne-bloccate");
        assertThat(primaRottaChePrende("/api/prenotazioni/interne/utente/1")).isEqualTo("prenotazioni-interne-bloccate");
    }

    @Test
    void ogniPercorsoPubblicoTrovaUnaRotta() {
        // Un percorso senza rotta non da' un errore di configurazione: da' un 404 a chi
        // chiama, ed e' il modo in cui un endpoint nuovo resta invisibile dopo essere stato
        // scritto e messo in produzione.
        for (String percorso : new String[]{
                "/api/auth/login", "/api/me", "/api/rooms", "/api/prenotazioni",
                "/api/notifiche", "/api/admin/utenti", "/api/admin/rooms"}) {
            assertThat(primaRottaChePrende(percorso))
                    .as("nessuna rotta per %s", percorso)
                    .isNotNull();
        }
    }

    @Test
    void unPercorsoInventatoNonTrovaRotte() {
        assertThat(primaRottaChePrende("/percorso/che/non/esiste")).isNull();
    }
}
