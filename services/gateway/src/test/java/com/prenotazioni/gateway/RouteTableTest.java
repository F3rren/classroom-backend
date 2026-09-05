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
 * /api/admin: auth-service sotto /api/admin/users, prenotazione-service tutto il resto.
 * Spring Cloud Gateway valuta le rotte nell'ordine in cui sono dichiarate, quindi quella
 * piu' specifica deve venire prima. Se qualcuno le riordinasse, /api/admin/users finirebbe
 * a prenotazione-service e risponderebbe 404 - senza errori di configurazione, senza log,
 * e senza niente che indichi il perche'.
 */
@SpringBootTest
class RouteTableTest {

    @Autowired
    private RouteLocator routes;

    /** L'id della prima rotta che accetta il percorso, come farebbe il gateway. */
    private String firstRouteMatching(String path) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        List<Route> ordinate = routes.getRoutes().collectList().block();
        assertThat(ordinate).as("nessuna rotta caricata: application.yml non e' stato letto").isNotEmpty();
        for (Route r : ordinate) {
            if (Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(exchange)).block())) {
                return r.getId();
            }
        }
        return null;
    }

    /** L'indirizzo a cui una rotta manda, per distinguere i servizi a valle. */
    private String destinazioneDi(String routeId) {
        return routes.getRoutes()
                .filter(r -> r.getId().equals(routeId))
                .map(r -> r.getUri().toString())
                .blockFirst();
    }

    @Test
    void gliUtentiAmministrativiVannoAlServizioUtenti() {
        // LA regressione da tenere chiusa: questa rotta e' dichiarata PRIMA di quella
        // generica su /api/admin/**, ed e' l'ordine a farla vincere.
        assertThat(firstRouteMatching("/api/admin/users")).isEqualTo("authentication");
        assertThat(firstRouteMatching("/api/admin/users/42")).isEqualTo("authentication");
    }

    @Test
    void ilRestoDiAdminVaAlServizioPrenotazioni() {
        assertThat(firstRouteMatching("/api/admin/rooms")).isEqualTo("application");
        assertThat(firstRouteMatching("/api/admin/bookings")).isEqualTo("application");
    }

    @Test
    void soloLOrdineDecideChiRiceveGliUtentiAmministrativi() {
        // Senza questo, i due test sopra potrebbero passare per costruzione: se
        // /api/admin/users corrispondesse a una rotta sola, l'ordine non conterebbe e non
        // ci sarebbe niente da tenere fermo. Qui si pretende che ENTRAMBE lo accettino,
        // cosi' l'unica cosa che manda la richiesta al servizio giusto e' la posizione.
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users").build());
        List<String> whoAcceptsIt = routes.getRoutes()
                .filter(r -> Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(exchange)).block()))
                .map(Route::getId)
                .collectList().block();

        assertThat(whoAcceptsIt).containsExactly("authentication", "application");
    }

    @Test
    void leDueRotteAdminPuntanoAServiziDiversi() {
        // Se puntassero allo stesso, l'ordine non conterebbe e questi test non
        // proverebbero niente: e' cio' che rende significativi i due sopra.
        assertThat(destinazioneDi("authentication")).isNotEqualTo(destinazioneDi("application"));
    }

    @Test
    void leRotteInterneRestanoFuoriDallaPortata() {
        // Sono chiamate da altri servizi, non dal browser: esporle darebbe a chiunque abbia
        // un token da admin la possibilita' di fabbricare notifiche arbitrarie.
        assertThat(firstRouteMatching("/api/notifications/internal/user/1")).isEqualTo("notifications-internal-blocked");
        assertThat(firstRouteMatching("/api/bookings/internal/user/1")).isEqualTo("bookings-internal-blocked");
    }

    @Test
    void ogniPercorsoPubblicoTrovaUnaRotta() {
        // Un percorso senza rotta non da' un errore di configurazione: da' un 404 a chi
        // chiama, ed e' il modo in cui un endpoint nuovo resta invisibile dopo essere stato
        // scritto e messo in produzione.
        for (String path : new String[]{
                "/api/auth/login", "/api/me", "/api/rooms", "/api/bookings",
                "/api/notifications", "/api/admin/users", "/api/admin/rooms"}) {
            assertThat(firstRouteMatching(path))
                    .as("nessuna rotta per %s", path)
                    .isNotNull();
        }
    }

    @Test
    void unPercorsoInventatoNonTrovaRotte() {
        assertThat(firstRouteMatching("/percorso/che/non/esiste")).isNull();
    }
}
