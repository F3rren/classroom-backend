package com.prenotazioni;

import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.repository.IUtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite per il fix IDOR/leak-password su /api/prenotazioni.
 * Owner (A) crea una prenotazione; Other (B), senza alcun rapporto con essa,
 * non deve poterla leggere ne' vederne la password in chiaro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PrenotazioneControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IUtenteRepository utenteRepository;

    @Autowired
    private IAulaRepository aulaRepository;

    @Autowired
    private IPrenotazioneRepository prenotazioneRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long prenotazioneIdDiOwner;
    private String tokenOwner;
    private String tokenOther;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();
        utenteRepository.deleteAll();

        Utente owner = nuovoUtente("owner@test.it", "owner", "password-owner", "Owner Test");
        utenteRepository.save(owner);

        Utente other = nuovoUtente("other@test.it", "other", "password-other", "Other Test");
        utenteRepository.save(other);

        Aula aula = new Aula();
        aula.setNome("Aula IT Test");
        aula.setPiano(1);
        aula.setCapienza(20);
        aula.setVirtual(false);
        aula.setStato("libera");
        aulaRepository.save(aula);

        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setAula(aula);
        prenotazione.setUtente(owner);
        prenotazione.setInizio(LocalDateTime.now().plusDays(1));
        prenotazione.setFine(LocalDateTime.now().plusDays(1).plusHours(2));
        prenotazione.setStato("prenotata");
        prenotazione.setDescrizione("Riunione privata di owner");
        prenotazione.setDataCreazione(LocalDateTime.now());
        prenotazioneRepository.save(prenotazione);
        prenotazioneIdDiOwner = prenotazione.getId();

        tokenOwner = login("owner@test.it", "password-owner");
        tokenOther = login("other@test.it", "password-other");
    }

    private Utente nuovoUtente(String email, String username, String rawPassword, String nome) {
        Utente u = new Utente();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setNome(nome);
        u.setRuolo("user");
        u.setDataRegistrazione(LocalDateTime.now());
        return u;
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void otherUserCannotReadOwnersBooking() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).doesNotContain("Riunione privata di owner");
    }

    @Test
    void otherUserCannotReadOwnersBookingDetails() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner + "/details",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerCanStillReadTheirOwnBooking() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Riunione privata di owner");
    }

    @Test
    void passwordIsNeverSerializedInAnyPrenotazioneResponse() {
        ResponseEntity<String> ownerView = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)),
                String.class);
        assertThat(ownerView.getBody()).doesNotContain("password-owner");
        assertThat(ownerView.getBody()).doesNotContain("\"password\"");

        ResponseEntity<String> listView = rest.exchange(
                "/api/prenotazioni",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);
        assertThat(listView.getBody()).doesNotContain("password-owner");
        assertThat(listView.getBody()).doesNotContain("\"password\"");
    }

    @Test
    void listEndpointHidesOwnerPiiFromOtherAuthenticatedUsers() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).doesNotContain("owner@test.it");
    }
}
