package com.prenotazioni.service;

import com.prenotazioni.model.Notifica;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.NotificaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificaService {

    @Autowired
    private NotificaRepository notificaRepository;

    public List<Notifica> getNotificheByUtente(Long utenteId) {
        return notificaRepository.findByUtenteIdOrderByDataCreazioneDesc(utenteId);
    }

    public List<Notifica> getNotificheNonLetteByUtente(Long utenteId) {
        return notificaRepository.findByUtenteIdAndLettaFalseOrderByDataCreazioneDesc(utenteId);
    }

    public Long getCountNotificheNonLette(Long utenteId) {
        return notificaRepository.countByUtenteIdAndLettaFalse(utenteId);
    }

    public Notifica createNotifica(Utente utente, String tipo, String titolo, String messaggio) {
        Notifica notifica = new Notifica();
        notifica.setUtente(utente);
        notifica.setTipo(tipo);
        notifica.setTitolo(titolo);
        notifica.setMessaggio(messaggio);
        notifica.setDataCreazione(LocalDateTime.now());
        notifica.setLetta(false);
        
        return notificaRepository.save(notifica);
    }

    public Notifica createNotificaCancellazionePrenotazione(Utente utente, Long prenotazioneId, 
            String nomeStanza, String adminNome, String dataPrenotazione, String oraInizio, String oraFine) {
        return createNotificaCancellazionePrenotazione(utente, prenotazioneId, nomeStanza, adminNome, 
            dataPrenotazione, oraInizio, oraFine, null);
    }

    public Notifica createNotificaCancellazionePrenotazione(Utente utente, Long prenotazioneId, 
            String nomeStanza, String adminNome, String dataPrenotazione, String oraInizio, String oraFine, String motivo) {
        
        String titolo = "Prenotazione Cancellata";
        
        // Costruisci il messaggio base
        String messaggioBase = String.format(
            "La tua prenotazione per la stanza '%s' del %s dalle %s alle %s è stata cancellata dall'amministratore %s.",
            nomeStanza, dataPrenotazione, oraInizio, oraFine, adminNome
        );
        
        // Aggiungi il motivo se specificato
        String messaggio = messaggioBase;
        if (motivo != null && !motivo.trim().isEmpty()) {
            messaggio = messaggioBase + "\n\nMotivo: " + motivo.trim();
        }
        
        Notifica notifica = new Notifica();
        notifica.setUtente(utente);
        notifica.setTipo("CANCELLAZIONE_PRENOTAZIONE");
        notifica.setTitolo(titolo);
        notifica.setMessaggio(messaggio);
        notifica.setPrenotazioneId(prenotazioneId);
        notifica.setNomeStanza(nomeStanza);
        notifica.setAdminNome(adminNome);
        notifica.setDataCreazione(LocalDateTime.now());
        notifica.setLetta(false);
        
        return notificaRepository.save(notifica);
    }

    public Optional<Notifica> getNotificaById(Long id) {
        return notificaRepository.findById(id);
    }

    public Notifica markAsRead(Long notificaId) {
        Optional<Notifica> notificaOpt = notificaRepository.findById(notificaId);
        if (notificaOpt.isPresent()) {
            Notifica notifica = notificaOpt.get();
            notifica.setLetta(true);
            return notificaRepository.save(notifica);
        }
        throw new RuntimeException("Notifica non trovata");
    }

    public void markAllAsRead(Long utenteId) {
        List<Notifica> notifiche = notificaRepository.findByUtenteIdAndLettaFalseOrderByDataCreazioneDesc(utenteId);
        for (Notifica notifica : notifiche) {
            notifica.setLetta(true);
            notificaRepository.save(notifica);
        }
    }

    @Transactional
    public void deleteReadNotifications(Long utenteId) {
        notificaRepository.deleteByUtenteIdAndLettaTrue(utenteId);
    }

    public void deleteNotifica(Long id) {
        notificaRepository.deleteById(id);
    }
}