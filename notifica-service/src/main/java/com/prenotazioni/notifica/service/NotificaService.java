package com.prenotazioni.notifica.service;

import com.prenotazioni.notifica.model.Notifica;
import com.prenotazioni.notifica.repository.NotificaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
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

    public Notifica createNotifica(Long utenteId, String tipo, String titolo, String messaggio) {
        logger.debug("INIZIO - Creazione notifica per utente ID: {}, Tipo: {}, Titolo: {}", utenteId, tipo, titolo);
        Notifica notifica = new Notifica();
        notifica.setUtenteId(utenteId);
        notifica.setTipo(tipo);
        notifica.setTitolo(titolo);
        notifica.setMessaggio(messaggio);
        notifica.setDataCreazione(LocalDateTime.now());
        notifica.setLetta(false);
        
        Notifica savedNotifica = notificaRepository.save(notifica);
        logger.debug("FINE - Notifica creata con successo con ID: {}", savedNotifica.getId());
        return savedNotifica;
    }

    public Notifica createNotificaCancellazionePrenotazione(Long utenteId, Long prenotazioneId, 
            String nomeStanza, String adminNome, String dataPrenotazione, String oraInizio, String oraFine, String motivo) {
        
        logger.debug("INIZIO - Creazione notifica di cancellazione per utente ID: {}, Prenotazione ID: {}", utenteId, prenotazioneId);

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
        
        Notifica notifica = createNotifica(utenteId, "cancellazione", titolo, messaggio);

        // Queste quattro colonne esistevano gia' sull'entita' ma NESSUNO le valorizzava:
        // erano permanentemente null da prima della separazione in servizi. Sono le uniche
        // che permettono al frontend di collegare la notifica alla prenotazione senza
        // interpretare il testo del messaggio, quindi vanno riempite.
        notifica.setPrenotazioneId(prenotazioneId);
        notifica.setNomeStanza(nomeStanza);
        notifica.setAdminNome(adminNome);
        notifica.setDataPrenotazione(componiIstante(dataPrenotazione, oraInizio));
        notifica = notificaRepository.save(notifica);
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
            if (notifica.getUtenteId().equals(utenteId)) {
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

    /**
     * Elimina tutte le notifiche di un utente, chiamato quando l'utente viene eliminato.
     *
     * Prima era una riga dentro la transazione di UtenteService, che cancellava notifiche,
     * prenotazioni e utente insieme. Ora e' un'operazione a se': se fallisce, l'utente puo'
     * risultare eliminato mentre le sue notifiche restano.
     */
    @Transactional
    public void deleteAllByUtente(Long utenteId) {
        logger.info("Eliminazione di tutte le notifiche dell'utenteId={}", utenteId);
        notificaRepository.deleteByUtenteId(utenteId);
    }

    /**
     * Ricompone data e ora in un istante. Restituisce null invece di sollevare se il
     * formato non e' quello atteso: una notifica con un campo in meno resta utile, una
     * cancellazione che fallisce per un timestamp malformato no.
     */
    private static LocalDateTime componiIstante(String data, String ora) {
        if (data == null || ora == null) {
            return null;
        }
        try {
            return LocalDate.parse(data).atTime(LocalTime.parse(ora));
        } catch (DateTimeParseException e) {
            logger.warn("Data prenotazione non interpretabile: data={} ora={}", data, ora);
            return null;
        }
    }
}
