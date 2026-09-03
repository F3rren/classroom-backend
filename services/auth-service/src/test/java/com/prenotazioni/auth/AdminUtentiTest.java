package com.prenotazioni.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.auth.repository.IUtenteRepository;
import com.prenotazioni.model.Ruolo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amministrazione degli utenti dopo la separazione.
 *
 * Questi casi stavano in AdminManagementTest, nel modulo applicativo, che li verificava
 * insieme ad aule e prenotazioni. Sono seguiti gli endpoint.
 *
 * Il test sulla cancellazione e' il piu' importante: prima era una transazione unica con
 * chiavi esterne, ora e' una sequenza di chiamate di rete che puo' fallire a meta'. Qui i
 * servizi a valle non esistono, quindi ogni cancellazione fallisce - ed e' proprio la
 * condizione che serve verificare, perche' dimostra che l'utente NON viene rimosso quando
 * i suoi dati altrove non lo sono. Se fosse il contrario, resterebbero righe orfane di cui
 * nessuno saprebbe piu' il proprietario.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminUtentiTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private IUtenteRepository utenteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenAdmin;
    private Long idUtenteNormale;

    @BeforeEach
    void setUp() {
        utenteRepository.deleteAll();
        salva("admin-utenti@test.it", "admin-utenti", Ruolo.ADMIN);
        idUtenteNormale = salva("normale@test.it", "normale", Ruolo.USER);
        tokenAdmin = login("admin-utenti@test.it");
    }

    private Long salva(String email, String username, Ruolo ruolo) {
        Utente u = new Utente();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("password-di-prova"));
        u.setNome(username);
        u.setRuolo(ruolo);
        u.setDataRegistrazione(LocalDateTime.now());
        return utenteRepository.save(u).getId();
    }

    @SuppressWarnings("unchecked")
    private String login(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", "password-di-prova"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokenAdmin);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> chiama(String url, HttpMethod metodo, Object corpo) {
        return rest.exchange(url, metodo, new HttpEntity<>(corpo, headers()), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> corpoDi(ResponseEntity<String> resp) throws Exception {
        return objectMapper.readValue(resp.getBody(), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaUtentiNonEsponeLePassword() throws Exception {
        ResponseEntity<String> resp = chiama("/api/admin/utenti", HttpMethod.GET, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).doesNotContain("password");

        Map<String, Object> data = (Map<String, Object>) corpoDi(resp).get("data");
        assertThat((List<Object>) data.get("users")).hasSize(2);
    }

    @Test
    void registrazioneConEmailGiaUsataVieneRifiutata() throws Exception {
        Map<String, String> corpo = Map.of("username", "nuovo", "nome", "Nuovo",
                "email", "normale@test.it", "password", "password-lunga", "ruolo", "user");

        ResponseEntity<String> resp = chiama("/api/admin/utenti", HttpMethod.POST, corpo);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(corpoDi(resp).get("success")).isEqualTo(false);
    }

    @Test
    void registrazioneConUsernameGiaUsatoVieneRifiutata() throws Exception {
        Map<String, String> corpo = Map.of("username", "normale", "nome", "Nuovo",
                "email", "un-altra@test.it", "password", "password-lunga", "ruolo", "user");

        ResponseEntity<String> resp = chiama("/api/admin/utenti", HttpMethod.POST, corpo);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aggiornamentoDiUnUtenteInesistenteRisponde404() {
        Map<String, String> corpo = Map.of("username", "x", "nome", "X", "email", "x@test.it");

        ResponseEntity<String> resp = chiama("/api/admin/utenti/999999", HttpMethod.PUT, corpo);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancellazioneDiUnUtenteInesistenteRisponde404() {
        ResponseEntity<String> resp = chiama("/api/admin/utenti/999999", HttpMethod.DELETE, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void seIServiziAValleNonRispondonoLUtenteNonVieneCancellato() {
        // In questo test non esistono ne' prenotazione-service ne' notifica-service: le chiamate
        // falliscono, e l'utente deve restare. E' la garanzia che sostituisce la chiave
        // esterna persa con la separazione.
        ResponseEntity<String> resp = chiama("/api/admin/utenti/" + idUtenteNormale, HttpMethod.DELETE, null);

        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(utenteRepository.findById(idUtenteNormale))
                .as("l'utente non deve sparire se le sue prenotazioni non sono state cancellate")
                .isPresent();
    }
}
