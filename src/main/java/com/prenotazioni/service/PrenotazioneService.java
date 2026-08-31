package com.prenotazioni.service;

import com.prenotazioni.dto.PrenotazioneDettaglioDto;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Corso;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.ICorsoRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.repository.IUtenteRepository;

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
    
    private final IUtenteRepository utenteRepository;

    PrenotazioneService(IPrenotazioneRepository prenotazioneRepository, IAulaRepository aulaRepository, ICorsoRepository corsoRepository, IUtenteRepository utenteRepository) {
        this.prenotazioneRepository = prenotazioneRepository;
        this.aulaRepository = aulaRepository;
        this.corsoRepository = corsoRepository;
        this.utenteRepository = utenteRepository;
    }

    // Prenota un'aula per una lezione
    @Transactional
    public Prenotazione prenotaAula(Long aulaId, Long corsoId, Long utenteId, LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        logger.debug("INIZIO METODO prenotaAula");
        logger.debug("Richiesta prenotazione aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", aulaId, corsoId, utenteId, inizio, fine);
        
        // Verifica disponibilità
        if (!isAulaDisponibile(aulaId, inizio, fine)) {
            logger.warn("Aula ID {} non disponibile per il periodo {} - {}", aulaId, inizio, fine);
            return null; // Aula non disponibile
        }
        
        Optional<Aula> aula = aulaRepository.findById(aulaId);
        Optional<Utente> utente = utenteRepository.findById(utenteId);
        
        if (aula.isEmpty()) {
            logger.warn("Aula con ID {} non trovata nel database", aulaId);
            return null;
        }
        if (utente.isEmpty()) {
            logger.warn("Utente con ID {} non trovato nel database", utenteId);
            return null;
        }
        
        // Corso opzionale - può essere null per prenotazioni libere
        Optional<Corso> corso = Optional.empty();
        if (corsoId != null) {
            corso = corsoRepository.findById(corsoId);
            if (corso.isEmpty()) {
                logger.warn("Corso con ID {} non trovato nel database", corsoId);
                // Se viene fornito un corsoId ma non esiste, fallisce
                return null;
            }
        }

        logger.debug("Creazione prenotazione per aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", aulaId, corsoId, utenteId, inizio, fine);
        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setAula(aula.get());
        prenotazione.setCorso(corso.orElse(null)); // Può essere null
        prenotazione.setUtente(utente.get());
        prenotazione.setInizio(inizio);
        prenotazione.setFine(fine);
        prenotazione.setStato(StatoPrenotazione.PRENOTATA);
        prenotazione.setDescrizione(descrizione);
        prenotazione.setDataCreazione(LocalDateTime.now());
        
        Prenotazione savedPrenotazione = prenotazioneRepository.save(prenotazione);
        
        // Aggiorna lo stato dell'aula se la prenotazione è attiva ADESSO
        aggiornaStatoAula(aulaId);
        
        logger.info("Prenotazione creata - id={} aula='{}' utenteId={} periodo={} - {}", savedPrenotazione.getId(), aula.get().getNome(), utente.get().getId(), inizio, fine);
        logger.debug("FINE METODO prenotaAula");
        return savedPrenotazione;
    }
    
    // Blocca un'aula (solo admin)
    @Transactional
    public Prenotazione bloccaAula(Long aulaId, Long utenteAdminId, LocalDateTime inizio, LocalDateTime fine, String motivo) {
        logger.debug("INIZIO METODO bloccaAula");
        logger.debug("Richiesta blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, utenteAdminId, inizio, fine);
        
        // Verifica disponibilità
        if (!isAulaDisponibile(aulaId, inizio, fine)) {
            logger.warn("Aula ID {} non disponibile per il periodo {} - {}", aulaId, inizio, fine);
            return null; // Aula non disponibile
        }
        
        Optional<Aula> aula = aulaRepository.findById(aulaId);
        Optional<Utente> admin = utenteRepository.findById(utenteAdminId);
        
        if (aula.isEmpty() || admin.isEmpty() || !"admin".equals(admin.get().getRuolo())) {
            logger.warn("Errore nel blocco aula - AulaId: {}, AdminId: {}. Verifica esistenza e ruolo admin.", 
                         aulaId, utenteAdminId);
            return null;
        }
        
        logger.debug("Blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, utenteAdminId, inizio, fine);
        Prenotazione blocco = new Prenotazione();
        blocco.setAula(aula.get());
        blocco.setCorso(null); // Nessun corso per i blocchi
        blocco.setUtente(admin.get());
        blocco.setInizio(inizio);
        blocco.setFine(fine);
        blocco.setStato(StatoPrenotazione.BLOCCATA);
        blocco.setDescrizione(motivo);
        blocco.setDataCreazione(LocalDateTime.now());
        
        logger.info("Blocco aula creato - id={} aula='{}' adminId={} periodo={} - {}", blocco.getId(), aula.get().getNome(), admin.get().getId(), inizio, fine);
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
        
        String nuovoStato;
        if (prenotazioniAttive.isEmpty()) {
            nuovoStato = "libera";
        } else {
            // Controlla se c'è una prenotazione di manutenzione o bloccata
            boolean hasManutenzione = prenotazioniAttive.stream()
                .anyMatch(p -> p.getStato() == StatoPrenotazione.MANUTENZIONE);
            boolean hasBloccata = prenotazioniAttive.stream()
                .anyMatch(p -> p.getStato() == StatoPrenotazione.BLOCCATA);
            
            if (hasManutenzione) {
                nuovoStato = "manutenzione";
            } else if (hasBloccata) {
                nuovoStato = "bloccata";
            } else {
                nuovoStato = "occupata";
            }
        }
        
        // Aggiorna solo se lo stato è cambiato
        if (!nuovoStato.equals(aula.getStato())) {
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
    public boolean annullaPrenotazione(Long prenotazioneId, Long utenteId) {
        logger.debug("INIZIO METODO annullaPrenotazione");
        logger.debug("Richiesta annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        Optional<Prenotazione> prenotazione = prenotazioneRepository.findById(prenotazioneId);
        
        if (prenotazione.isEmpty()) {
            logger.warn("Prenotazione con ID {} non trovata per annullamento", prenotazioneId);
            return false;
        }
        
        logger.debug("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        Prenotazione p = prenotazione.get();
        
        // Solo il creatore o un admin può annullare
        Optional<Utente> utente = utenteRepository.findById(utenteId);
        if (utente.isEmpty()) {
            logger.warn("Utente con ID {} non trovato nel database per annullamento prenotazione", utenteId);
            return false;
        }
        
        logger.debug("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        boolean isCreatore = p.getUtente().getId().equals(utenteId);
        boolean isAdmin = "admin".equals(utente.get().getRuolo());
        
        if (!isCreatore && !isAdmin) {
            logger.warn("Utente ID {} non autorizzato ad annullare la prenotazione ID {}", utenteId, prenotazioneId);
            return false;
        }

        // Solo una prenotazione attiva puo' essere annullata da questo endpoint. Senza questo
        // controllo un secondo annullamento riusciva e rispondeva "annullata con successo"
        // pur non cambiando nulla; i blocchi e le manutenzioni, che non sono "prenotata",
        // si annullano dall'endpoint admin (annullaPrenotazioneAsAdmin, volutamente permissivo
        // sullo stato). La regola e' sullo stato, non sul ruolo: vale anche per gli admin.
        if (!p.getStato().isAttiva()) {
            logger.warn("Prenotazione ID {} non annullabile: stato attuale '{}'", prenotazioneId, p.getStato());
            return false;
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
            logger.warn("Prenotazione non trovata - PrenotazioneId: {}", prenotazioneId);
            return false;
        }
        
        logger.debug("Richiesta annullamento prenotazione da admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        Prenotazione prenotazione = prenotazioneOpt.get();
        
        // Verifica che l'admin esista
        logger.debug("Verifica esistenza admin - AdminId: {}", adminId);
        Optional<Utente> admin = utenteRepository.findById(adminId);
        if (admin.isEmpty() || !"admin".equals(admin.get().getRuolo())) {
            logger.warn("Utente non è un admin valido - AdminId: {}", adminId);
            return false;
        }
        
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
    public Prenotazione updatePrenotazione(Long prenotazioneId, Long aulaId, Long corsoId, Long utenteId, LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        logger.debug("INIZIO METODO updatePrenotazione");
        logger.debug("Richiesta aggiornamento prenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", prenotazioneId, aulaId, corsoId, utenteId, inizio, fine);
        
        // Trova la prenotazione esistente
        Optional<Prenotazione> prenotazioneOpt = prenotazioneRepository.findById(prenotazioneId);
        if (prenotazioneOpt.isEmpty()) {
            logger.warn("Prenotazione con ID {} non trovata per aggiornamento", prenotazioneId);
            return null;
        }
        
        Prenotazione prenotazione = prenotazioneOpt.get();
        
        // Verifica autorizzazione - solo il creatore o un admin può modificare
        Optional<Utente> utente = utenteRepository.findById(utenteId);
        if (utente.isEmpty()) {
            logger.warn("Utente con ID {} non trovato nel database per aggiornamento prenotazione", utenteId);
            return null;
        }
        
        boolean isCreatore = prenotazione.getUtente().getId().equals(utenteId);
        boolean isAdmin = "admin".equals(utente.get().getRuolo());
        
        if (!isCreatore && !isAdmin) {
            logger.warn("Utente ID {} non autorizzato a modificare la prenotazione ID {}", utenteId, prenotazioneId);
            return null;
        }
        
        // Verifica che l'aula esista
        Optional<Aula> aula = aulaRepository.findById(aulaId);
        if (aula.isEmpty()) {
            logger.warn("Aula con ID {} non trovata nel database", aulaId);
            return null;
        }
        
        // Verifica disponibilità aula per il nuovo periodo (escludendo questa prenotazione)
        if (!isAulaDisponibileEscludendo(aulaId, inizio, fine, prenotazioneId)) {
            logger.warn("Aula ID {} non disponibile per il periodo {} - {} (esclusa prenotazione {})", aulaId, inizio, fine, prenotazioneId);
            return null;
        }
        
        // Corso opzionale
        Optional<Corso> corso = Optional.empty();
        if (corsoId != null) {
            corso = corsoRepository.findById(corsoId);
            if (corso.isEmpty()) {
                logger.warn("Corso con ID {} non trovato nel database", corsoId);
                return null;
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
        logger.info("Prenotazione aggiornata - id={} aula='{}' utenteId={} periodo={} - {}", savedPrenotazione.getId(), aula.get().getNome(), utente.get().getId(), inizio, fine);
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
