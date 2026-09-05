package com.prenotazioni.auth;

import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.auth.repository.UserRepository;
import com.prenotazioni.auth.service.AuthService;
import com.prenotazioni.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea il primo amministratore su un database utenti vuoto.
 *
 * Esiste per sciogliere un nodo: AdminUtentiController e' annotato @PreAuthorize
 * hasRole('ADMIN') a livello di classe, quindi anche /api/admin/users lo richiede. Su un
 * database appena creato serve un token da amministratore per creare il primo
 * amministratore, e l'unica via d'uscita era una INSERT scritta a mano - da rifare a ogni
 * ambiente nuovo, e con l'hash della password calcolato fuori.
 *
 * LA CONDIZIONE CHE RENDE QUESTA CLASSE INNOCUA e' una sola, e va tenuta stretta: agisce
 * esclusivamente a tabella VUOTA. Non promuove nessuno, non aggiorna nessuno, non tocca
 * alcun utente esistente. A database popolato esce prima di guardare qualunque altra cosa.
 * Se un giorno qualcuno aggiungesse un ramo che scrive su una tabella non vuota, questo
 * smetterebbe di essere un aiuto all'avvio e diventerebbe una scorciatoia per ottenere
 * privilegi da amministratore.
 *
 * Passa da AuthService.register e non dal repository: cosi' la password attraversa lo
 * stesso PasswordEncoder e gli stessi controlli di ogni altro utente, e non esiste una
 * seconda strada per crearne uno che possa divergere dalla prima.
 */
@Component
public class FirstAdminBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(FirstAdminBootstrap.class);

    private final UserRepository userRepository;
    private final AuthService authService;
    private final String email;
    private final String password;
    private final String nome;

    FirstAdminBootstrap(UserRepository userRepository,
                    AuthService authService,
                    @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
                    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
                    @Value("${BOOTSTRAP_ADMIN_NOME:Amministratore}") String nome) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.email = email;
        this.password = password;
        this.nome = nome;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        if (email.isBlank() || password.isBlank()) {
            // A WARN e non in silenzio: un database utenti vuoto e un servizio che parte
            // senza dire niente e' esattamente il modo in cui questo problema si
            // ripresenta, e la prossima persona ci ritrova davanti senza indizi.
            logger.warn("Nessun utente nel database e nessun amministratore iniziale da creare. "
                    + "Valorizzare BOOTSTRAP_ADMIN_EMAIL e BOOTSTRAP_ADMIN_PASSWORD in .env e "
                    + "riavviare, oppure inserire il primo amministratore a mano: senza, "
                    + "/api/admin/users non e' raggiungibile perche' richiede un token da admin.");
            return;
        }

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail(email);
        request.setUsername(email);
        request.setPassword(password);
        request.setNome(nome);
        request.setRuolo("admin");

        User created = authService.register(request);
        logger.info("Primo amministratore creato su database vuoto - utenteId={} email={}. "
                + "Cambiare la password al primo accesso e svuotare BOOTSTRAP_ADMIN_PASSWORD in .env.",
                created.getId(), LogSanitizer.maskEmail(email));
    }
}
