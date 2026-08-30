package com.prenotazioni.service;

import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IUtenteRepository;
import com.prenotazioni.dto.RegisterRequest;

import java.util.List;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private IUtenteRepository utenteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    public Utente login(String email, String password) {
        logger.info("INIZIO METODO login");
        Utente utente = utenteRepository.findByEmail(email);
        if (utente == null || !passwordEncoder.matches(password, utente.getPassword())) {
            logger.info("FINE METODO login - Credenziali non valide");
            return null;
        }
        logger.info("Login riuscito per utente: {}", email);
        // Aggiorna l'ultimo accesso
        utente.setUltimoAccesso(LocalDateTime.now());
        utenteRepository.save(utente);
        logger.info("FINE METODO login");
        return utente;
    }

    public Utente register(RegisterRequest request) {
        // Controlla se email o username sono già registrati
        logger.info("INIZIO METODO register");
        if (utenteRepository.findByEmail(request.getEmail()) != null) {
            logger.info("Tentativo di registrazione fallito - Email già esistente: {}", request.getEmail());
            return null;
        }
        if (utenteRepository.findByUsername(request.getUsername()) != null) {
            logger.info("Tentativo di registrazione fallito - Username già esistente: {}", request.getUsername());
            return null;
        }
        // Crea il nuovo utente
        logger.info("Registrazione nuovo utente - Email: {}, Username: {}", request.getEmail(), request.getUsername());
        Utente utente = new Utente();
        utente.setEmail(request.getEmail());
        utente.setNome(request.getNome());
        utente.setPassword(passwordEncoder.encode(request.getPassword()));
        utente.setRuolo(request.getRuolo());
        utente.setUsername(request.getUsername());
        
        // Imposta la data di registrazione (non modificabile)
        utente.setDataRegistrazione(LocalDateTime.now());
        // ultimoAccesso viene aggiornato solo al login
        logger.info("Utente creato con successo - Email: {}, Username: {}", request.getEmail(), request.getUsername());
        logger.info("FINE METODO register");
        return utenteRepository.save(utente);
    }

    public List<Utente> getAllUsers() {
        logger.info("INIZIO METODO getAllUsers");
        List<Utente> utenti = utenteRepository.findAll();
        logger.info("FINE METODO getAllUsers - Totale utenti trovati: {}", utenti.size());
        return utenti;
    }

    public Utente updateUtente(Long id, RegisterRequest request) {
        logger.info("INIZIO METODO updateUtente - UtenteId: {}", id);
        Utente utente = utenteRepository.findById(id).orElse(null);
        if (utente == null) {
            logger.info("Tentativo di aggiornamento fallito - UtenteId non trovato: {}", id);
            logger.info("FINE METODO updateUtente - UtenteId non trovato: {}", id);
            return null;
        }

        // Controlla se la nuova email o username sono già in uso da un altro utente
        Utente utenteConEmail = utenteRepository.findByEmail(request.getEmail());
        if (utenteConEmail != null && !utenteConEmail.getId().equals(id)) {
            logger.info("Tentativo di aggiornamento fallito - Email già in uso: {}", request.getEmail());
            return null;
        }
        Utente utenteConUsername = utenteRepository.findByUsername(request.getUsername());
        if (utenteConUsername != null && !utenteConUsername.getId().equals(id)) {
            logger.info("Tentativo di aggiornamento fallito - Username già in uso: {}", request.getUsername());
            return null;
        }
        // Aggiorna i campi modificabili
        logger.info("Aggiornamento dati utente - UtenteId: {}", id);
        utente.setEmail(request.getEmail());
        utente.setNome(request.getNome());
        
        // Aggiorna la password solo se ne viene fornita una nuova
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            utente.setPassword(passwordEncoder.encode(request.getPassword()));
            logger.info("Password aggiornata per UtenteId: {}", id);
        } else {
            logger.info("Password non modificata per UtenteId: {} - Mantenuta password esistente", id);
        }
        
        // Normalizza e valida il ruolo: solo 'admin' o 'user' (minuscolo)
        String ruolo = request.getRuolo();
        if (ruolo != null) {
            ruolo = ruolo.toLowerCase().trim();
            // Se il ruolo non è valido, mantieni quello esistente
            if (!"admin".equals(ruolo) && !"user".equals(ruolo)) {
                logger.warn("Ruolo non valido '{}' fornito. Mantenuto ruolo esistente: {}", request.getRuolo(), utente.getRuolo());
                ruolo = utente.getRuolo();
            }
        } else {
            ruolo = utente.getRuolo(); // Mantieni esistente se null
        }
        utente.setRuolo(ruolo);
        utente.setUsername(request.getUsername());
        
        // NON modifichiamo dataRegistrazione - rimane quella originale
        // ultimoAccesso viene aggiornato solo al login
        logger.info("Utente aggiornato con successo - UtenteId: {}", id);
        logger.info("FINE METODO updateUtente - UtenteId: {}", id);
        return utenteRepository.save(utente);
    }
}
