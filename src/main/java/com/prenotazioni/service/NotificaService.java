package com.prenotazioni.service;

import com.prenotazioni.model.Notifica;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.NotificaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificaService {

    private static final Logger logger = LoggerFactory.getLogger(NotificaService.class);

    private final NotificaRepository notificaRepository;

    NotificaService(NotificaRepository notificaRepository) {
        this.notificaRepository = notificaRepository;
    }

    public List<Notifica> getNotificheByUtente(Long utenteId) {
        logger.debug("INIZIO - Recupero notifiche per utente ID: {}", utenteId);
        List<Notifica> notifiche = notificaRepository.findByUtenteIdOrderByDataCreazioneDesc(utenteId);
        logger.debug("FINE - Recuperate {} notifiche per utente ID: {}", notifiche.size(), utenteId);
        return notifiche;
    }

    public List<Notifica> getNotificheNonLetteByUtente(Long utenteId) {
        logger.debug("INIZIO - Recupero notifiche non lette per utente ID: {}", utenteId);
        List<Notifica> notifiche = notificaRepository.findByUtenteIdAndLettaFalseOrderByDataCreazioneDesc(utenteId);
        logger.debug("FINE - Recuperate {} notifiche non lette per utente ID: {}", notifiche.size(), utenteId);
        return notifiche;
    }

    public Long getCountNotificheNonLette(Long utenteId) {
        logger.debug("INIZIO - Conteggio notifiche non lette per utente ID: {}", utenteId);
        Long count = notificaRepository.countByUtenteIdAndLettaFalse(utenteId);
        logger.debug("FINE - Trovate {} notifiche non lette per utente ID: {}", count, utenteId);
        return count;
    }

    public Notifica createNotifica(Utente utente, String tipo, String titolo, String messaggio) {
        logger.debug("INIZIO - Creazione notifica per utente ID: {}, Tipo: {}, Titolo: {}", utente.getId(), tipo, titolo);
        Notifica notifica = new Notifica();
        notifica.setUtente(utente);
        notifica.setTipo(tipo);
        notifica.setTitolo(titolo);
        notifica.setMessaggio(messaggio);
        notifica.setDataCreazione(LocalDateTime.now());
        notifica.setLetta(false);
        
        Notifica savedNotifica = notificaRepository.save(notifica);
        logger.debug("FINE - Notifica creata con successo con ID: {}", savedNotifica.getId());
        return savedNotifica;
    }

    public Notifica createNotificaCancellazionePrenotazione(Utente utente, Long prenotazioneId, 
            String nomeStanza, String adminNome, String dataPrenotazione, String oraInizio, String oraFine) {
        return createNotificaCancellazionePrenotazione(utente, prenotazioneId, nomeStanza, adminNome, 
            dataPrenotazione, oraInizio, oraFine, null);
    }

    public Notifica createNotificaCancellazionePrenotazione(Utente utente, Long prenotazioneId, 
            String nomeStanza, String adminNome, String dataPrenotazione, String oraInizio, String oraFine, String motivo) {
        
        logger.debug("INIZIO - Creazione notifica di cancellazione per utente ID: {}, Prenotazione ID: {}", utente.getId(), prenotazioneId);

        String titolo = "Cancellazione Prenotazione: " + nomeStanza;
        String messaggio;

        if (adminNome != null) {
            messaggio = String.format(
                "La tua prenotazione per la stanza '%s' il %s dalle %s alle %s è stata cancellata dall'amministratore %s.",
                nomeStanza, dataPrenotazione, oraInizio, oraFine, adminNome
            );
            if (motivo != null && !motivo.trim().isEmpty()) {
                messaggio += " Motivo: " + motivo;
            }
        } else {
            messaggio = String.format(
                "Hai annullato la tua prenotazione per la stanza '%s' il %s dalle %s alle %s.",
                nomeStanza, dataPrenotazione, oraInizio, oraFine
            );
        }
        
        Notifica notifica = createNotifica(utente, "cancellazione", titolo, messaggio);
        logger.debug("FINE - Notifica di cancellazione creata con ID: {}", notifica.getId());
        return notifica;
    }

    public Optional<Notifica> getNotificaById(Long notificaId) {
        logger.debug("INIZIO - Recupero notifica per ID: {}", notificaId);
        Optional<Notifica> notifica = notificaRepository.findById(notificaId);
        if (notifica.isPresent()) {
            logger.debug("FINE - Notifica trovata con ID: {}", notificaId);
        } else {
            logger.warn("FINE - Notifica non trovata con ID: {}", notificaId);
        }
        return notifica;
    }

    public Optional<Notifica> markAsRead(Long notificaId, Long utenteId) {
        logger.debug("INIZIO - Tentativo di segnare notifica ID: {} come letta per utente ID: {}", notificaId, utenteId);
        Optional<Notifica> notificaOpt = notificaRepository.findById(notificaId);
        
        if (notificaOpt.isPresent()) {
            Notifica notifica = notificaOpt.get();
            // Verifica che la notifica appartenga all'utente corretto
            if (notifica.getUtente().getId().equals(utenteId)) {
                notifica.setLetta(true);
                Notifica updatedNotifica = notificaRepository.save(notifica);
                logger.debug("FINE - Notifica ID: {} segnata come letta.", notificaId);
                return Optional.of(updatedNotifica);
            } else {
                logger.warn("FINE - Tentativo fallito. L'utente ID: {} non è autorizzato a modificare la notifica ID: {}", utenteId, notificaId);
                return Optional.empty(); // Utente non autorizzato
            }
        }
        
        logger.warn("FINE - Tentativo fallito. Notifica ID: {} non trovata.", notificaId);
        return Optional.empty(); // Notifica non trovata
    }

    @Transactional
    public void markAllAsRead(Long utenteId) {
        logger.debug("INIZIO - Segna tutte le notifiche come lette per utente ID: {}", utenteId);
        notificaRepository.segnaTutteComeLette(utenteId);
        logger.debug("FINE - Tutte le notifiche per utente ID: {} sono state segnate come lette.", utenteId);
    }

    @Transactional
    public void deleteNotifica(Long notificaId) {
        logger.debug("INIZIO - Eliminazione notifica ID: {}", notificaId);
        notificaRepository.deleteById(notificaId);
        logger.debug("FINE - Eliminazione completata per notifica ID: {}", notificaId);
    }

    @Transactional
    public void deleteReadNotifications(Long utenteId) {
        logger.debug("INIZIO - Eliminazione notifiche lette per utente ID: {}", utenteId);
        notificaRepository.deleteByUtenteIdAndLettaTrue(utenteId);
        logger.debug("FINE - Eliminazione notifiche lette completata per utente ID: {}", utenteId);
    }
}