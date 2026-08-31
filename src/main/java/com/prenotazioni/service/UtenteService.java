package com.prenotazioni.service;

import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IUtenteRepository;
import com.prenotazioni.repository.NotificaRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class UtenteService {

    private static final Logger logger = LoggerFactory.getLogger(UtenteService.class);

    private final IUtenteRepository utenteRepository;
    
    private final NotificaRepository notificaRepository;
    
    private final IPrenotazioneRepository prenotazioneRepository;

    UtenteService(IUtenteRepository utenteRepository, NotificaRepository notificaRepository, IPrenotazioneRepository prenotazioneRepository) {
        this.utenteRepository = utenteRepository;
        this.notificaRepository = notificaRepository;
        this.prenotazioneRepository = prenotazioneRepository;
    }

    public Utente findById(Long id) {
        logger.info("INIZIO - Ricerca utente per ID: {}", id);
        Utente utente = utenteRepository.findById(id).orElse(null);
        if (utente != null) {
            logger.info("FINE - Utente trovato per ID: {}", id);
        } else {
            logger.warn("FINE - Utente non trovato per ID: {}", id);
        }
        return utente;
    }

    public Utente findByUsername(String username) {
        logger.info("INIZIO - Ricerca utente per username: {}", username);
        Utente utente = utenteRepository.findByUsername(username);
        if (utente != null) {
            logger.info("FINE - Utente trovato per username: {}", username);
        } else {
            logger.warn("FINE - Utente non trovato per username: {}", username);
        }
        return utente;
    }

    public Utente findByEmail(String email) {
        logger.info("INIZIO - Ricerca utente per email: {}", email);
        Utente utente = utenteRepository.findByEmail(email);
        if (utente != null) {
            logger.info("FINE - Utente trovato per email: {}", email);
        } else {
            logger.warn("FINE - Utente non trovato per email: {}", email);
        }
        return utente;
    }

    public List<Utente> findAll() {
        logger.info("INIZIO - Recupero di tutti gli utenti");
        List<Utente> utenti = utenteRepository.findAll();
        logger.info("FINE - Recuperati {} utenti", utenti.size());
        return utenti;
    }

    public Utente save(Utente utente) {
        logger.info("INIZIO - Salvataggio utente con email: {}", utente.getEmail());
        Utente savedUtente = utenteRepository.save(utente);
        logger.info("FINE - Utente salvato con ID: {}", savedUtente.getId());
        return savedUtente;
    }

    @Transactional
    public void deleteById(Long id) {
        logger.info("INIZIO - Eliminazione utente e dati associati per ID: {}", id);
        // Prima elimina le notifiche dell'utente
        logger.info("Eliminazione notifiche per utente ID: {}", id);
        notificaRepository.deleteByUtenteId(id);
        
        // Poi elimina le prenotazioni dell'utente
        logger.info("Eliminazione prenotazioni per utente ID: {}", id);
        prenotazioneRepository.deleteByUtenteId(id);
        
        // Infine, elimina l'utente
        logger.info("Eliminazione utente ID: {}", id);
        utenteRepository.deleteById(id);
        logger.info("FINE - Eliminazione completata per utente ID: {}", id);
    }
}