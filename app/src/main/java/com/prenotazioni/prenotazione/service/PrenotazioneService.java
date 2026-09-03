package com.prenotazioni.prenotazione.service;

import com.prenotazioni.prenotazione.dto.PrenotazioneDettaglioDto;
import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.prenotazione.model.Corso;
import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.prenotazione.model.Prenotazione;
import com.prenotazioni.prenotazione.model.ProprietarioPrenotazione;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.prenotazione.model.StatoAula;
import com.prenotazioni.prenotazione.model.StatoPrenotazione;
import com.prenotazioni.prenotazione.repository.IAulaRepository;
import com.prenotazioni.prenotazione.repository.ICorsoRepository;
import com.prenotazioni.prenotazione.repository.IPrenotazioneRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PrenotazioneService {
    
    private static final Logger logger = LoggerFactory.getLogger(PrenotazioneService.class);
    
    private final IPrenotazioneRepository prenotazioneRepository;
    
    private final IAulaRepository aulaRepository;
    
    private final ICorsoRepository corsoRepository;
    

    PrenotazioneService(IPrenotazioneRepository prenotazioneRepository, IAulaRepository aulaRepository, ICorsoRepository corsoRepository) {
        this.prenotazioneRepository = prenotazioneRepository;
        this.aulaRepository = aulaRepository;
        this.corsoRepository = corsoRepository;
    }

    // Prenota un'aula per una lezione
    @Transactional
    public Prenotazione prenotaAula(Long aulaId, Long corsoId, ProprietarioPrenotazione proprietario, LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        logger.debug("INIZIO METODO prenotaAula");
        logger.debug("Richiesta prenotazione aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", aulaId, corsoId, proprietario.getId(), inizio, fine);
        
        // Verifica disponibilità
        if (!isAulaDisponibile(aulaId, inizio, fine)) {
            logger.warn("Aula ID {} non disponibile per il periodo {} - {}", aulaId, inizio, fine);
            throw new BookingConflictException("BOOKING_CONFLICT",
                    "Aula " + aulaId + " occupata dal " + inizio + " al " + fine,
                    "L'aula non e' disponibile nel periodo richiesto.");
        }
        
        Optional<Aula> aula = aulaRepository.findById(aulaId);

        if (aula.isEmpty()) {
            // 404 e non piu' 409: aula inesistente e aula occupata erano entrambe un null,
            // e il controller le presentava tutte come conflitto. Sono cose diverse.
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", aulaId);
        }
        // Non si verifica piu' che l'utente esista: questo servizio non ha piu' la tabella
        // utenti. A garantirlo e' il token, che auth-service ha firmato al login. La finestra
        // di un utente cancellato con un token ancora valido e' limitata dalla scadenza.
        
        // Corso opzionale - può essere null per prenotazioni libere
        Optional<Corso> corso = Optional.empty();
        if (corsoId != null) {
            corso = corsoRepository.findById(corsoId);
            if (corso.isEmpty()) {
                // Il corso e' facoltativo, ma se indicato deve esistere: passarne uno
                // inesistente e' un errore del chiamante, non una prenotazione libera.
                throw ResourceNotFoundException.perId("Corso", "COURSE_NOT_FOUND", corsoId);
            }
        }

        logger.debug("Creazione prenotazione per aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", aulaId, corsoId, proprietario.getId(), inizio, fine);
        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setAula(aula.get());
        prenotazione.setCorso(corso.orElse(null)); // Può essere null
        prenotazione.setUtente(proprietario);
        prenotazione.setInizio(inizio);
        prenotazione.setFine(fine);
        prenotazione.setStato(StatoPrenotazione.PRENOTATA);
        prenotazione.setDescrizione(descrizione);
        prenotazione.setDataCreazione(LocalDateTime.now());
        
        Prenotazione savedPrenotazione = prenotazioneRepository.save(prenotazione);
        
        // Aggiorna lo stato dell'aula se la prenotazione è attiva ADESSO
        aggiornaStatoAula(aulaId);
        
        logger.info("Prenotazione creata - id={} aula='{}' utenteId={} periodo={} - {}", savedPrenotazione.getId(), aula.get().getNome(), proprietario.getId(), inizio, fine);
        logger.debug("FINE METODO prenotaAula");
        return savedPrenotazione;
    }
    
    // Blocca un'aula (solo admin)
    @Transactional
    public Prenotazione bloccaAula(Long aulaId, ProprietarioPrenotazione admin, LocalDateTime inizio, LocalDateTime fine, String motivo) {
        logger.debug("INIZIO METODO bloccaAula");
        logger.debug("Richiesta blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, admin.getId(), inizio, fine);
        
        // Verifica disponibilità
        if (!isAulaDisponibile(aulaId, inizio, fine)) {
            logger.warn("Aula ID {} non disponibile per il periodo {} - {}", aulaId, inizio, fine);
            throw new BookingConflictException("BLOCK_CONFLICT",
                    "Aula " + aulaId + " occupata dal " + inizio + " al " + fine,
                    "L'aula non e' disponibile nel periodo richiesto.");
        }
        
        Optional<Aula> aula = aulaRepository.findById(aulaId);

        // Il ruolo non si rilegge dal database: arriva dal token, e il controller
        // e' gia' annotato @PreAuthorize("hasRole('ADMIN')").
        if (aula.isEmpty()) {
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", aulaId);
        }
        
        logger.debug("Blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, admin.getId(), inizio, fine);
        Prenotazione blocco = new Prenotazione();
        blocco.setAula(aula.get());
        blocco.setCorso(null); // Nessun corso per i blocchi
        blocco.setUtente(admin);
        blocco.setInizio(inizio);
        blocco.setFine(fine);
        blocco.setStato(StatoPrenotazione.BLOCCATA);
        blocco.setDescrizione(motivo);
        blocco.setDataCreazione(LocalDateTime.now());
        
        logger.info("Blocco aula creato - id={} aula='{}' adminId={} periodo={} - {}", blocco.getId(), aula.get().getNome(), admin.getId(), inizio, fine);
        logger.debug("FINE METODO bloccaAula");
        return prenotazioneRepository.save(blocco);
    }
    
    // Verifica se un'aula è disponibile in un determinato periodo
    public boolean isAulaDisponibile(Long aulaId, LocalDateTime inizio, LocalDateTime fine) {
        logger.debug("INIZIO METODO isAulaDisponibile");
        logger.debug("Verifica disponibilità aula - AulaId: {}, Periodo: {} - {}", aulaId, inizio, fine);
        List<Prenotazione> conflitti = prenotazioneRepository.findConflittingReservations(aulaId, inizio, fine);
        boolean disponibile = conflitti.isEmpty();
        logger.debug("Risultato verifica disponibilità aula - AulaId: {}, Periodo: {} - {}", aulaId, inizio, fine, disponibile);
        return disponibile;
    }
    
    // Ottiene lo stato attuale di un'aula
    public String getStatoAula(Long aulaId, LocalDateTime momento) {
        logger.debug("INIZIO METODO getStatoAula");
        logger.debug("Verifica stato aula - AulaId: {}, Momento: {}", aulaId, momento);
        List<Prenotazione> prenotazioniAttive = prenotazioneRepository.findActiveReservations(aulaId, momento);
            
        if (prenotazioniAttive.isEmpty()) {
            logger.debug("Stato aula - AulaId: {}, Momento: {} - LIBERA", aulaId, momento);
            return "LIBERA";
        }
        
        // Priorità: MANUTENZIONE > BLOCCATA > PRENOTATA
        for (Prenotazione p : prenotazioniAttive) {
            if (p.getStato() == StatoPrenotazione.MANUTENZIONE) {
                logger.debug("Stato aula - AulaId: {}, Momento: {} - MANUTENZIONE", aulaId, momento);
                return "MANUTENZIONE";
            }
        }
        
        for (Prenotazione p : prenotazioniAttive) {
            if (p.getStato() == StatoPrenotazione.BLOCCATA) {
                logger.debug("Stato aula - AulaId: {}, Momento: {} - BLOCCATA", aulaId, momento);
                return "BLOCCATA";
            }
        }
        
        logger.debug("Stato aula - AulaId: {}, Momento: {} - PRENOTATA", aulaId, momento);
        logger.debug("FINE METODO getStatoAula");
        return "PRENOTATA";
    }
    
    // Aggiorna lo stato dell'aula in base alle prenotazioni attive
    private void aggiornaStatoAula(Long aulaId) {
        logger.debug("INIZIO METODO aggiornaStatoAula - AulaId: {}", aulaId);
        
        Optional<Aula> aulaOpt = aulaRepository.findById(aulaId);
        if (aulaOpt.isEmpty()) {
            logger.warn("Aula non trovata per aggiornamento stato - AulaId: {}", aulaId);
            return;
        }
        
        Aula aula = aulaOpt.get();
        LocalDateTime ora = LocalDateTime.now();
        
        // Ottieni prenotazioni attive in questo momento
        List<Prenotazione> prenotazioniAttive = prenotazioneRepository.findActiveReservations(aulaId, ora);
        
        StatoAula nuovoStato;
        if (prenotazioniAttive.isEmpty()) {
            nuovoStato = StatoAula.LIBERA;
        } else {
            // Controlla se c'è una prenotazione di manutenzione o bloccata
            boolean hasManutenzione = prenotazioniAttive.stream()
                .anyMatch(p -> p.getStato() == StatoPrenotazione.MANUTENZIONE);
            boolean hasBloccata = prenotazioniAttive.stream()
                .anyMatch(p -> p.getStato() == StatoPrenotazione.BLOCCATA);
            
            if (hasManutenzione) {
                nuovoStato = StatoAula.MANUTENZIONE;
            } else if (hasBloccata) {
                nuovoStato = StatoAula.BLOCCATA;
            } else {
                nuovoStato = StatoAula.OCCUPATA;
            }
        }
        
        // Aggiorna solo se lo stato è cambiato
        if (nuovoStato != aula.getStato()) {
            logger.debug("Aggiornamento stato aula {} da '{}' a '{}'", aulaId, aula.getStato(), nuovoStato);
            aula.setStato(nuovoStato);
            aulaRepository.save(aula);
        } else {
            logger.debug("Stato aula {} rimane invariato: '{}'", aulaId, aula.getStato());
        }
        
        logger.debug("FINE METODO aggiornaStatoAula");
    }
    
    // Annulla una prenotazione
    @Transactional
    public boolean annullaPrenotazione(Long prenotazioneId, Long utenteId, boolean isAdmin) {
        logger.debug("INIZIO METODO annullaPrenotazione");
        logger.debug("Richiesta annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        Optional<Prenotazione> prenotazione = prenotazioneRepository.findById(prenotazioneId);
        
        if (prenotazione.isEmpty()) {
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", prenotazioneId);
        }
        
        logger.debug("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        Prenotazione p = prenotazione.get();
        
        // Solo il creatore o un admin può annullare

        logger.debug("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        boolean isCreatore = p.getUtente().getId().equals(utenteId);
                
        if (!isCreatore && !isAdmin) {
            // AccessDeniedException e non un booleano: il gestore globale la traduce gia'
            // in 403. Prima il controller doveva RIFARE questo stesso controllo per capire
            // se il false significasse "non autorizzato" o qualcos'altro.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Puoi annullare solo le tue prenotazioni.");
        }

        // Solo una prenotazione attiva puo' essere annullata da questo endpoint. Senza questo
        // controllo un secondo annullamento riusciva e rispondeva "annullata con successo"
        // pur non cambiando nulla; i blocchi e le manutenzioni, che non sono "prenotata",
        // si annullano dall'endpoint admin (annullaPrenotazioneAsAdmin, volutamente permissivo
        // sullo stato). La regola e' sullo stato, non sul ruolo: vale anche per gli admin.
        if (!p.getStato().isAttiva()) {
            // 409: la prenotazione esiste ed e' visibile, ma il suo stato non ammette
            // l'annullamento. Non e' "non trovata" e non e' "non autorizzato".
            throw new DomainConflictException("INVALID_STATE",
                    "Prenotazione " + prenotazioneId + " in stato " + p.getStato().getValore(),
                    "Questa prenotazione non puo' essere annullata nello stato attuale.");
        }

        p.setStato(StatoPrenotazione.ANNULLATA);
        prenotazioneRepository.save(p);
        
        // Aggiorna lo stato dell'aula
        aggiornaStatoAula(p.getAula().getId());
        
        logger.info("Prenotazione ID {} annullata con successo da Utente ID {}", prenotazioneId, utenteId);
        logger.debug("FINE METODO annullaPrenotazione");
        return true;
    }
    
    // Lista tutte le prenotazioni per gestione admin
    public List<Prenotazione> getAllPrenotazioni() {
        logger.debug("INIZIO METODO getAllPrenotazioni");
        logger.debug("Recupero tutte le prenotazioni dal database");
        List<Prenotazione> prenotazioni = prenotazioneRepository.findAll();
        logger.debug("Recuperate {} prenotazioni totali", prenotazioni.size());
        logger.debug("FINE METODO getAllPrenotazioni");
        return prenotazioni;
    }
    
    // Lista prenotazioni per utente
    public List<Prenotazione> getPrenotazioniUtente(Long utenteId) {
        logger.debug("INIZIO METODO getPrenotazioniUtente");
        logger.debug("Recupero prenotazioni per utente - UtenteId: {}", utenteId);
        List<Prenotazione> prenotazioni = prenotazioneRepository.findByUtenteId(utenteId);
        logger.debug("Recuperate {} prenotazioni per utente ID {}", prenotazioni.size(), utenteId);
        logger.debug("FINE METODO getPrenotazioniUtente");
        return prenotazioni;
    }
    
    // Ottieni dettagli completi per una specifica aula
    public List<PrenotazioneDettaglioDto> getRoomCompleteDetails(Long aulaId) {
        logger.debug("INIZIO METODO getRoomCompleteDetails");
        logger.debug("Recupero dettagli completi per aula - AulaId: {}", aulaId);
        logger.debug("FINE METODO getRoomCompleteDetails");
        return prenotazioneRepository.findCompleteDetailsByAulaId(aulaId);
    }
    
    // Ottieni dettagli completi di tutte le prenotazioni
    public List<PrenotazioneDettaglioDto> getAllCompleteDetails() {
        logger.debug("INIZIO METODO getAllCompleteDetails");
        logger.debug("Recupero dettagli completi per tutte le prenotazioni");
        logger.debug("FINE METODO getAllCompleteDetails");
        return prenotazioneRepository.findAllCompleteDetails();
    }
    
    // Ottieni una singola prenotazione per ID
    public Prenotazione getPrenotazioneById(Long id) {
        logger.debug("INIZIO METODO getPrenotazioneById");
        logger.debug("Recupero prenotazione per ID - PrenotazioneId: {}", id);
        Optional<Prenotazione> prenotazione = prenotazioneRepository.findById(id);
        logger.debug("FINE METODO getPrenotazioneById");
        return prenotazione.orElse(null);
    }
    
    // Ottieni dettagli completi per una singola prenotazione
    public List<PrenotazioneDettaglioDto> getPrenotazioneCompleteDetails(Long prenotazioneId) {
        logger.debug("INIZIO METODO getPrenotazioneCompleteDetails");
        logger.debug("Recupero dettagli completi per prenotazione - PrenotazioneId: {}", prenotazioneId);
        logger.debug("FINE METODO getPrenotazioneCompleteDetails");
        return prenotazioneRepository.findCompleteDetailsByPrenotazioneId(prenotazioneId);
    }
    
    // Lista prenotazioni per stato
    public List<Prenotazione> getPrenotazioniByStato(String stato) {
        logger.debug("INIZIO METODO getPrenotazioniByStato");
        logger.debug("Recupero prenotazioni per stato - Stato: {}", stato);
        logger.debug("FINE METODO getPrenotazioniByStato");
        return prenotazioneRepository.findByStato(StatoPrenotazione.da(stato));
    }
    
    // Lista prenotazioni future
    public List<Prenotazione> getPrenotazioniFuture() {
        logger.debug("INIZIO METODO getPrenotazioniFuture");
        logger.debug("Recupero prenotazioni future a partire da ora");
        logger.debug("FINE METODO getPrenotazioniFuture");
        return prenotazioneRepository.findPrenotazioniFuture(LocalDateTime.now());
    }
    
    // Metodo admin per annullare qualsiasi prenotazione
    @Transactional
    public boolean annullaPrenotazioneAsAdmin(Long prenotazioneId, Long adminId, String motivo) {
        logger.debug("INIZIO METODO annullaPrenotazioneAsAdmin");
        logger.debug("Richiesta annullamento prenotazione da admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        Optional<Prenotazione> prenotazioneOpt = prenotazioneRepository.findById(prenotazioneId);
        if (prenotazioneOpt.isEmpty()) {
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", prenotazioneId);
        }
        
        logger.debug("Richiesta annullamento prenotazione da admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        Prenotazione prenotazione = prenotazioneOpt.get();
        
        // Il ruolo admin e' gia' stato verificato dal filtro JWT e da @PreAuthorize:
        // rileggerlo qui richiederebbe una chiamata ad auth-service a ogni cancellazione.
        
        logger.debug("Annullamento prenotazione da parte dell'admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        // Gli admin possono eliminare qualsiasi prenotazione, indipendentemente dallo stato
        prenotazione.setStato(StatoPrenotazione.ANNULLATA);
        
        logger.debug("Aggiornamento descrizione prenotazione per indicare azione admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        // Aggiorna la descrizione per indicare l'azione admin
        String descrizioneOriginale = prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "";
        String nuovaDescrizione = descrizioneOriginale + 
            (descrizioneOriginale.isEmpty() ? "" : " | ") +
            "ANNULLATA DALL'AMMINISTRATORE: " + motivo;
        prenotazione.setDescrizione(nuovaDescrizione);

        logger.debug("Salvataggio prenotazione aggiornata - PrenotazioneId: {}", prenotazioneId);
        prenotazioneRepository.save(prenotazione);
        
        // Aggiorna lo stato dell'aula
        aggiornaStatoAula(prenotazione.getAula().getId());
        
        logger.debug("FINE METODO annullaPrenotazioneAsAdmin");
        return true;
    }

    // Aggiorna una prenotazione esistente
    @Transactional
    public Prenotazione updatePrenotazione(Long prenotazioneId, Long aulaId, Long corsoId, Long utenteId, boolean isAdmin, LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        logger.debug("INIZIO METODO updatePrenotazione");
        logger.debug("Richiesta aggiornamento prenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", prenotazioneId, aulaId, corsoId, utenteId, inizio, fine);
        
        // Trova la prenotazione esistente
        Optional<Prenotazione> prenotazioneOpt = prenotazioneRepository.findById(prenotazioneId);
        if (prenotazioneOpt.isEmpty()) {
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", prenotazioneId);
        }
        
        Prenotazione prenotazione = prenotazioneOpt.get();
        
        // Verifica autorizzazione - solo il creatore o un admin può modificare
        boolean isCreatore = prenotazione.getUtente().getId().equals(utenteId);
                
        if (!isCreatore && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Puoi modificare solo le tue prenotazioni.");
        }
        
        // Verifica che l'aula esista
        Optional<Aula> aula = aulaRepository.findById(aulaId);
        if (aula.isEmpty()) {
            // 404 e non piu' 409: aula inesistente e aula occupata erano entrambe un null,
            // e il controller le presentava tutte come conflitto. Sono cose diverse.
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", aulaId);
        }
        
        // Verifica disponibilità aula per il nuovo periodo (escludendo questa prenotazione)
        if (!isAulaDisponibileEscludendo(aulaId, inizio, fine, prenotazioneId)) {
            throw new BookingConflictException("UPDATE_CONFLICT",
                    "Aula " + aulaId + " occupata dal " + inizio + " al " + fine,
                    "L'aula non e' disponibile nel nuovo periodo richiesto.");
        }
        
        // Corso opzionale
        Optional<Corso> corso = Optional.empty();
        if (corsoId != null) {
            corso = corsoRepository.findById(corsoId);
            if (corso.isEmpty()) {
                // Il corso e' facoltativo, ma se indicato deve esistere: passarne uno
                // inesistente e' un errore del chiamante, non una prenotazione libera.
                throw ResourceNotFoundException.perId("Corso", "COURSE_NOT_FOUND", corsoId);
            }
        }
        
        // Aggiorna i campi
        logger.debug("Aggiornamento campi prenotazione - PrenotazioneId: {}", prenotazioneId);
        prenotazione.setAula(aula.get());
        prenotazione.setCorso(corso.orElse(null));
        prenotazione.setInizio(inizio);
        prenotazione.setFine(fine);
        prenotazione.setDescrizione(descrizione);
        
        Prenotazione savedPrenotazione = prenotazioneRepository.save(prenotazione);
        logger.info("Prenotazione aggiornata - id={} aula='{}' utenteId={} periodo={} - {}", savedPrenotazione.getId(), aula.get().getNome(), utenteId, inizio, fine);
        logger.debug("FINE METODO updatePrenotazione");
        return savedPrenotazione;
    }
    
    // Verifica disponibilità aula escludendo una prenotazione specifica
    private boolean isAulaDisponibileEscludendo(Long aulaId, LocalDateTime inizio, LocalDateTime fine, Long prenotazioneIdEsclusa) {
        logger.debug("Verifica disponibilità aula escludendo prenotazione - AulaId: {}, Periodo: {} - {}, Esclusa: {}", aulaId, inizio, fine, prenotazioneIdEsclusa);
        List<Prenotazione> conflitti = prenotazioneRepository.findConflittingReservationsExcluding(aulaId, inizio, fine, prenotazioneIdEsclusa);
        boolean disponibile = conflitti.isEmpty();
        logger.debug("Risultato verifica disponibilità aula (esclusa prenotazione {}) - AulaId: {}, Disponibile: {}", prenotazioneIdEsclusa, aulaId, disponibile);
        return disponibile;
    }
}
