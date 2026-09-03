package com.prenotazioni.auth.service;

import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.auth.repository.IUtenteRepository;
import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.dto.UpdateUserRequest;
import com.prenotazioni.util.LogSanitizer;

import java.util.List;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final IUtenteRepository utenteRepository;

    private final PasswordEncoder passwordEncoder;

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    AuthService(IUtenteRepository utenteRepository, PasswordEncoder passwordEncoder) {
        this.utenteRepository = utenteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Utente login(String email, String password) {
        Utente utente = utenteRepository.findByEmail(email);
        if (utente == null || !passwordEncoder.matches(password, utente.getPassword())) {
            // WARN e non INFO: un login fallito e' un segnale di sicurezza (brute-force,
            // credenziali compromesse) e deve restare visibile anche alzando il livello.
            logger.warn("Login fallito - credenziali non valide per {}", LogSanitizer.maskEmail(email));
            // Questo null RESTA, a differenza degli altri di questa classe: ha un solo
            // significato possibile, credenziali sbagliate, quindi il chiamante non deve
            // dedurre nulla. E' il null AMBIGUO il problema, non il null in se'.
            return null;
        }
        utente.setUltimoAccesso(LocalDateTime.now());
        utenteRepository.save(utente);
        logger.info("Login riuscito - utenteId={} ({})", utente.getId(), LogSanitizer.maskEmail(email));
        return utente;
    }

    public Utente register(CreateUserRequest request) {
        // Controlla se email o username sono già registrati
        if (utenteRepository.findByEmail(request.getEmail()) != null) {
            // Il codice resta USER_ALREADY_EXISTS, gia' esposto e veritiero. A cambiare e'
            // il messaggio: prima non diceva QUALE dei due campi fosse in conflitto, e chi
            // lo leggeva non sapeva cosa correggere.
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Email gia' registrata",
                    "Questa email e' gia' associata a un altro utente.");
        }
        if (utenteRepository.findByUsername(request.getUsername()) != null) {
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Username gia' registrato: " + request.getUsername(),
                    "Questo username e' gia' in uso.");
        }
        Utente utente = new Utente();
        utente.setEmail(request.getEmail());
        utente.setNome(request.getNome());
        utente.setPassword(passwordEncoder.encode(request.getPassword()));
        utente.setRuolo(Ruolo.da(request.getRuolo()));
        utente.setUsername(request.getUsername());
        
        // Imposta la data di registrazione (non modificabile)
        utente.setDataRegistrazione(LocalDateTime.now());
        // ultimoAccesso viene aggiornato solo al login
        Utente salvato = utenteRepository.save(utente);
        logger.info("Utente creato - utenteId={} ({})", salvato.getId(), LogSanitizer.maskEmail(salvato.getEmail()));
        return salvato;
    }

    public List<Utente> getAllUsers() {
        List<Utente> utenti = utenteRepository.findAll();
        logger.debug("Elenco utenti recuperato - totale={}", utenti.size());
        return utenti;
    }

    public Utente updateUtente(Long id, UpdateUserRequest request) {
        Utente utente = utenteRepository.findById(id).orElse(null);
        if (utente == null) {
            throw ResourceNotFoundException.perId("Utente", "USER_NOT_FOUND", id);
        }

        // Controlla se la nuova email o username sono già in uso da un altro utente
        Utente utenteConEmail = utenteRepository.findByEmail(request.getEmail());
        if (utenteConEmail != null && !utenteConEmail.getId().equals(id)) {
            // 409 e non piu' 404: prima questo caso tornava lo stesso null di "utente
            // inesistente", e la risposta diceva "utente non trovato" di un utente che
            // esiste eccome. Era una risposta falsa, non solo imprecisa.
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Email gia' in uso da un altro utente",
                    "Questa email e' gia' associata a un altro utente.");
        }
        Utente utenteConUsername = utenteRepository.findByUsername(request.getUsername());
        if (utenteConUsername != null && !utenteConUsername.getId().equals(id)) {
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Username gia' in uso da un altro utente",
                    "Questo username e' gia' in uso.");
        }
        // Aggiorna i campi modificabili
        utente.setEmail(request.getEmail());
        utente.setNome(request.getNome());
        
        // Aggiorna la password solo se ne viene fornita una nuova
        boolean passwordCambiata = request.getPassword() != null && !request.getPassword().trim().isEmpty();
        if (passwordCambiata) {
            utente.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        // Il formato del ruolo (admin|user, case-insensitive) e' gia' garantito da @Pattern
        // sul DTO; qui resta solo la normalizzazione e il fallback per un ruolo non fornito.
        Ruolo ruolo = request.getRuolo() != null ? Ruolo.da(request.getRuolo()) : utente.getRuolo();
        utente.setRuolo(ruolo);
        utente.setUsername(request.getUsername());
        
        // NON modifichiamo dataRegistrazione - rimane quella originale
        // ultimoAccesso viene aggiornato solo al login
        Utente salvato = utenteRepository.save(utente);
        logger.info("Utente aggiornato - utenteId={} passwordCambiata={}", id, passwordCambiata);
        return salvato;
    }
}
