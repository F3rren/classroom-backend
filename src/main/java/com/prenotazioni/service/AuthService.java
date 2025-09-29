package com.prenotazioni.service;

import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IUtenteRepository;
import com.prenotazioni.dto.RegisterRequest;

import java.util.List;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private IUtenteRepository utenteRepository;
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    public Utente login(String email, String password) {
        logger.info("INIZIO METODO login");
        Utente utente = utenteRepository.findByEmailAndPassword(email, password);
        if (utente != null) {
            logger.info("Login riuscito per utente: {}", email);
            // Aggiorna l'ultimo accesso
            utente.setUltimoAccesso(LocalDateTime.now());
            utenteRepository.save(utente);
        }
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
        utente.setPassword(request.getPassword());
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

    public boolean deleteUtente(Long id) {
        logger.info("INIZIO METODO deleteUtente - UtenteId: {}", id);
        if (!utenteRepository.existsById(id)) {
            logger.info("Tentativo di eliminazione fallito - UtenteId non trovato: {}", id);
            logger.info("FINE METODO deleteUtente - UtenteId non trovato: {}", id);
            return false;
        }
        utenteRepository.deleteById(id);
        logger.info("FINE METODO deleteUtente - UtenteId eliminato: {}", id);
        return true;
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
        utente.setPassword(request.getPassword());
        utente.setRuolo(request.getRuolo());
        utente.setUsername(request.getUsername());
        
        // NON modifichiamo dataRegistrazione - rimane quella originale
        // ultimoAccesso viene aggiornato solo al login
        logger.info("Utente aggiornato con successo - UtenteId: {}", id);
        logger.info("FINE METODO updateUtente - UtenteId: {}", id);
        return utenteRepository.save(utente);
    }
}
