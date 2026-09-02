package com.prenotazioni.service;

import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IUtenteRepository;
import com.prenotazioni.client.NotificaClient;
import com.prenotazioni.repository.IPrenotazioneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UtenteService {

    private static final Logger logger = LoggerFactory.getLogger(UtenteService.class);

    private final IUtenteRepository utenteRepository;
    
    private final NotificaClient notificaClient;
    
    private final IPrenotazioneRepository prenotazioneRepository;

    UtenteService(IUtenteRepository utenteRepository, NotificaClient notificaClient, IPrenotazioneRepository prenotazioneRepository) {
        this.utenteRepository = utenteRepository;
        this.notificaClient = notificaClient;
        this.prenotazioneRepository = prenotazioneRepository;
    }

    public Utente findById(Long id) {
        logger.debug("INIZIO - Ricerca utente per ID: {}", id);
        Utente utente = utenteRepository.findById(id).orElse(null);
        if (utente != null) {
            logger.debug("FINE - Utente trovato per ID: {}", id);
        } else {
            logger.warn("FINE - Utente non trovato per ID: {}", id);
        }
        return utente;
    }

    @Transactional
    public void deleteById(Long id) {
        logger.debug("INIZIO - Eliminazione utente e dati associati per ID: {}", id);
        // Prima elimina le notifiche dell'utente
        logger.info("Eliminazione notifiche per utente ID: {}", id);
        notificaClient.eliminaNotificheUtente(id);
        
        // Poi elimina le prenotazioni dell'utente
        logger.info("Eliminazione prenotazioni per utente ID: {}", id);
        prenotazioneRepository.deleteByUtenteId(id);
        
        // Infine, elimina l'utente
        logger.info("Eliminazione utente ID: {}", id);
        utenteRepository.deleteById(id);
        logger.debug("FINE - Eliminazione completata per utente ID: {}", id);
    }
}