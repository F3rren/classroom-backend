package com.prenotazioni.service;

import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Corso;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.ICorsoRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.repository.IUtenteRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PrenotazioneService {
    
    private static final Logger logger = LoggerFactory.getLogger(PrenotazioneService.class);
    
    @Autowired
    private IPrenotazioneRepository prenotazioneRepository;
    
    @Autowired
    private IAulaRepository aulaRepository;
    
    @Autowired
    private ICorsoRepository corsoRepository;
    
    @Autowired
    private IUtenteRepository utenteRepository;

    // Prenota un'aula per una lezione
    @Transactional
    public Prenotazione prenotaAula(Long aulaId, Long corsoId, Long utenteId, LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        logger.info("INIZIO METODO prenotaAula");
        logger.info("Richiesta prenotazione aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", aulaId, corsoId, utenteId, inizio, fine);
        
        // Verifica disponibilità
        if (!isAulaDisponibile(aulaId, inizio, fine)) {
            logger.info("Aula ID {} non disponibile per il periodo {} - {}", aulaId, inizio, fine);
            return null; // Aula non disponibile
        }
        
        Optional<Aula> aula = aulaRepository.findById(aulaId);
        Optional<Utente> utente = utenteRepository.findById(utenteId);
        
        if (aula.isEmpty()) {
            logger.info("Aula con ID {} non trovata nel database", aulaId);
            return null;
        }
        if (utente.isEmpty()) {
            logger.info("Utente con ID {} non trovato nel database", utenteId);
            return null;
        }
        
        // Corso opzionale - può essere null per prenotazioni libere
        Optional<Corso> corso = Optional.empty();
        if (corsoId != null) {
            corso = corsoRepository.findById(corsoId);
            if (corso.isEmpty()) {
                logger.info("Corso con ID {} non trovato nel database", corsoId);
                // Se viene fornito un corsoId ma non esiste, fallisce
                return null;
            }
        }

        logger.info("Creazione prenotazione per aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", aulaId, corsoId, utenteId, inizio, fine);
        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setAula(aula.get());
        prenotazione.setCorso(corso.orElse(null)); // Può essere null
        prenotazione.setUtente(utente.get());
        prenotazione.setInizio(inizio);
        prenotazione.setFine(fine);
        prenotazione.setStato("prenotata");
        prenotazione.setDescrizione(descrizione);
        prenotazione.setDataCreazione(LocalDateTime.now());
        
        Prenotazione savedPrenotazione = prenotazioneRepository.save(prenotazione);
        
        // Aggiorna lo stato dell'aula se la prenotazione è attiva ADESSO
        aggiornaStatoAula(aulaId);
        
        logger.info("Prenotazione salvata con successo - ID: {}, Aula: '{}', Utente: '{}', Periodo: {} - {}", savedPrenotazione.getId(), aula.get().getNome(), utente.get().getEmail(), inizio, fine);
        logger.info("FINE METODO prenotaAula");
        return savedPrenotazione;
    }
    
    // Blocca un'aula (solo admin)
    @Transactional
    public Prenotazione bloccaAula(Long aulaId, Long utenteAdminId, LocalDateTime inizio, LocalDateTime fine, String motivo) {
        logger.info("INIZIO METODO bloccaAula");
        logger.info("Richiesta blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, utenteAdminId, inizio, fine);
        
        // Verifica disponibilità
        if (!isAulaDisponibile(aulaId, inizio, fine)) {
            logger.info("Aula ID {} non disponibile per il periodo {} - {}", aulaId, inizio, fine);
            return null; // Aula non disponibile
        }
        
        Optional<Aula> aula = aulaRepository.findById(aulaId);
        Optional<Utente> admin = utenteRepository.findById(utenteAdminId);
        
        if (aula.isEmpty() || admin.isEmpty() || !"admin".equals(admin.get().getRuolo())) {
            logger.info("Errore nel blocco aula - AulaId: {}, AdminId: {}. Verifica esistenza e ruolo admin.", 
                         aulaId, utenteAdminId);
            return null;
        }
        
        logger.info("Blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, utenteAdminId, inizio, fine);
        Prenotazione blocco = new Prenotazione();
        blocco.setAula(aula.get());
        blocco.setCorso(null); // Nessun corso per i blocchi
        blocco.setUtente(admin.get());
        blocco.setInizio(inizio);
        blocco.setFine(fine);
        blocco.setStato("bloccata");
        blocco.setDescrizione(motivo);
        blocco.setDataCreazione(LocalDateTime.now());
        
        logger.info("Blocco aula creato con successo - ID: {}, Aula: '{}', Admin: '{}', Periodo: {} - {}", blocco.getId(), aula.get().getNome(), admin.get().getEmail(), inizio, fine);
        logger.info("FINE METODO bloccaAula");
        return prenotazioneRepository.save(blocco);
    }
    
    // Mette un'aula in manutenzione
    public Prenotazione aulaInManutenzione(Long aulaId, Long utenteAdminId, LocalDateTime inizio, LocalDateTime fine, String dettagli) {
        logger.info("INIZIO METODO aulaInManutenzione");
        logger.info("Richiesta manutenzione aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, utenteAdminId, inizio, fine);

        Optional<Aula> aula = aulaRepository.findById(aulaId);
        Optional<Utente> admin = utenteRepository.findById(utenteAdminId);
        
        if (aula.isEmpty() || admin.isEmpty() || !"admin".equals(admin.get().getRuolo())) {
            logger.info("Errore nella manutenzione aula - AulaId: {}, AdminId: {}. Verifica esistenza e ruolo admin.", aulaId, utenteAdminId);
            return null;
        }
        
        logger.info("Impostazione aula in manutenzione - AulaId: {}, AdminId: {}, Periodo: {} - {}", aulaId, utenteAdminId, inizio, fine);
        Prenotazione manutenzione = new Prenotazione();
        manutenzione.setAula(aula.get());
        manutenzione.setCorso(null);
        manutenzione.setUtente(admin.get());
        manutenzione.setInizio(inizio);
        manutenzione.setFine(fine);
        manutenzione.setStato("manutenzione");
        manutenzione.setDescrizione(dettagli);
        manutenzione.setDataCreazione(LocalDateTime.now());
        
        logger.info("Aula messa in manutenzione con successo - ID: {}, Aula: '{}', Admin: '{}', Periodo: {} - {}", manutenzione.getId(), aula.get().getNome(), admin.get().getEmail(), inizio, fine);
        logger.info("FINE METODO aulaInManutenzione");
        return prenotazioneRepository.save(manutenzione);
    }
    
    // Verifica se un'aula è disponibile in un determinato periodo
    public boolean isAulaDisponibile(Long aulaId, LocalDateTime inizio, LocalDateTime fine) {
        logger.info("INIZIO METODO isAulaDisponibile");
        logger.info("Verifica disponibilità aula - AulaId: {}, Periodo: {} - {}", aulaId, inizio, fine);
        List<Prenotazione> conflitti = prenotazioneRepository.findConflittingReservations(aulaId, inizio, fine);
        boolean disponibile = conflitti.isEmpty();
        logger.info("Risultato verifica disponibilità aula - AulaId: {}, Periodo: {} - {}", aulaId, inizio, fine, disponibile);
        return disponibile;
    }
    
    // Ottiene tutte le prenotazioni di un'aula in una data specifica
    public List<Prenotazione> getPrenotazioniAula(Long aulaId, LocalDateTime data) {
        logger.info("INIZIO METODO getPrenotazioniAula");
        logger.info("Recupero prenotazioni per aula - AulaId: {}, Data: {}", aulaId, data.toLocalDate());
        LocalDateTime inizioGiornata = data.toLocalDate().atStartOfDay();
        LocalDateTime fineGiornata = inizioGiornata.plusDays(1).minusSeconds(1);

        List<Prenotazione> prenotazioni = prenotazioneRepository.findByAulaAndPeriod(aulaId, inizioGiornata, fineGiornata);
        logger.info("Recuperate {} prenotazioni per aula ID {} nella data {}", prenotazioni.size(), aulaId, data.toLocalDate());
        logger.info("FINE METODO getPrenotazioniAula");
        return prenotazioni;
    }
    
    // Ottiene lo stato attuale di un'aula
    public String getStatoAula(Long aulaId, LocalDateTime momento) {
        logger.info("INIZIO METODO getStatoAula");
        logger.info("Verifica stato aula - AulaId: {}, Momento: {}", aulaId, momento);
        List<Prenotazione> prenotazioniAttive = prenotazioneRepository.findActiveReservations(aulaId, momento);
            
        if (prenotazioniAttive.isEmpty()) {
            logger.info("Stato aula - AulaId: {}, Momento: {} - LIBERA", aulaId, momento);
            return "LIBERA";
        }
        
        // Priorità: MANUTENZIONE > BLOCCATA > PRENOTATA
        for (Prenotazione p : prenotazioniAttive) {
            if ("manutenzione".equalsIgnoreCase(p.getStato())) {
                logger.info("Stato aula - AulaId: {}, Momento: {} - MANUTENZIONE", aulaId, momento);
                return "MANUTENZIONE";
            }
        }
        
        for (Prenotazione p : prenotazioniAttive) {
            if ("bloccata".equalsIgnoreCase(p.getStato())) {
                logger.info("Stato aula - AulaId: {}, Momento: {} - BLOCCATA", aulaId, momento);
                return "BLOCCATA";
            }
        }
        
        logger.info("Stato aula - AulaId: {}, Momento: {} - PRENOTATA", aulaId, momento);
        logger.info("FINE METODO getStatoAula");
        return "PRENOTATA";
    }
    
    // Aggiorna lo stato dell'aula in base alle prenotazioni attive
    private void aggiornaStatoAula(Long aulaId) {
        logger.info("INIZIO METODO aggiornaStatoAula - AulaId: {}", aulaId);
        
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
                .anyMatch(p -> "manutenzione".equalsIgnoreCase(p.getStato()));
            boolean hasBloccata = prenotazioniAttive.stream()
                .anyMatch(p -> "bloccata".equalsIgnoreCase(p.getStato()));
            
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
            logger.info("Aggiornamento stato aula {} da '{}' a '{}'", aulaId, aula.getStato(), nuovoStato);
            aula.setStato(nuovoStato);
            aulaRepository.save(aula);
        } else {
            logger.info("Stato aula {} rimane invariato: '{}'", aulaId, aula.getStato());
        }
        
        logger.info("FINE METODO aggiornaStatoAula");
    }
    
    // Annulla una prenotazione
    @Transactional
    public boolean annullaPrenotazione(Long prenotazioneId, Long utenteId) {
        logger.info("INIZIO METODO annullaPrenotazione");
        logger.info("Richiesta annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        Optional<Prenotazione> prenotazione = prenotazioneRepository.findById(prenotazioneId);
        
        if (prenotazione.isEmpty()) {
            logger.info("Prenotazione con ID {} non trovata per annullamento", prenotazioneId);
            return false;
        }
        
        logger.info("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        Prenotazione p = prenotazione.get();
        
        // Solo il creatore o un admin può annullare
        Optional<Utente> utente = utenteRepository.findById(utenteId);
        if (utente.isEmpty()) {
            logger.info("Utente con ID {} non trovato nel database per annullamento prenotazione", utenteId);
            return false;
        }
        
        logger.info("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", prenotazioneId, utenteId);
        boolean isCreatore = p.getUtente().getId().equals(utenteId);
        boolean isAdmin = "admin".equals(utente.get().getRuolo());
        
        if (!isCreatore && !isAdmin) {
            logger.info("Utente ID {} non autorizzato ad annullare la prenotazione ID {}", utenteId, prenotazioneId);
            return false;
        }
        

        p.setStato("annullata");
        prenotazioneRepository.save(p);
        
        // Aggiorna lo stato dell'aula
        aggiornaStatoAula(p.getAula().getId());
        
        logger.info("Prenotazione ID {} annullata con successo da Utente ID {}", prenotazioneId, utenteId);
        logger.info("FINE METODO annullaPrenotazione");
        return true;
    }
    
    // Lista tutte le prenotazioni per gestione admin
    public List<Prenotazione> getAllPrenotazioni() {
        logger.info("INIZIO METODO getAllPrenotazioni");
        logger.info("Recupero tutte le prenotazioni dal database");
        List<Prenotazione> prenotazioni = prenotazioneRepository.findAll();
        logger.info("Recuperate {} prenotazioni totali", prenotazioni.size());
        logger.info("FINE METODO getAllPrenotazioni");
        return prenotazioni;
    }
    
    // Lista prenotazioni per utente
    public List<Prenotazione> getPrenotazioniUtente(Long utenteId) {
        logger.info("INIZIO METODO getPrenotazioniUtente");
        logger.info("Recupero prenotazioni per utente - UtenteId: {}", utenteId);
        List<Prenotazione> prenotazioni = prenotazioneRepository.findByUtenteId(utenteId);
        logger.info("Recuperate {} prenotazioni per utente ID {}", prenotazioni.size(), utenteId);
        logger.info("FINE METODO getPrenotazioniUtente");
        return prenotazioni;
    }
    
    // Ottieni dettagli completi per una specifica aula
    public List<Map<String, Object>> getRoomCompleteDetails(Long aulaId) {
        logger.info("INIZIO METODO getRoomCompleteDetails");
        logger.info("Recupero dettagli completi per aula - AulaId: {}", aulaId);
        logger.info("FINE METODO getRoomCompleteDetails");
        return prenotazioneRepository.findCompleteDetailsByAulaId(aulaId);
    }
    
    // Ottieni dettagli completi di tutte le prenotazioni
    public List<Map<String, Object>> getAllCompleteDetails() {
        logger.info("INIZIO METODO getAllCompleteDetails");
        logger.info("Recupero dettagli completi per tutte le prenotazioni");
        logger.info("FINE METODO getAllCompleteDetails");
        return prenotazioneRepository.findAllCompleteDetails();
    }
    
    // Ottieni una singola prenotazione per ID
    public Prenotazione getPrenotazioneById(Long id) {
        logger.info("INIZIO METODO getPrenotazioneById");
        logger.info("Recupero prenotazione per ID - PrenotazioneId: {}", id);
        Optional<Prenotazione> prenotazione = prenotazioneRepository.findById(id);
        logger.info("FINE METODO getPrenotazioneById");
        return prenotazione.orElse(null);
    }
    
    // Ottieni dettagli completi per una singola prenotazione
    public List<Map<String, Object>> getPrenotazioneCompleteDetails(Long prenotazioneId) {
        logger.info("INIZIO METODO getPrenotazioneCompleteDetails");
        logger.info("Recupero dettagli completi per prenotazione - PrenotazioneId: {}", prenotazioneId);
        logger.info("FINE METODO getPrenotazioneCompleteDetails");
        return prenotazioneRepository.findCompleteDetailsByPrenotazioneId(prenotazioneId);
    }
    
    // Lista prenotazioni per stato
    public List<Prenotazione> getPrenotazioniByStato(String stato) {
        logger.info("INIZIO METODO getPrenotazioniByStato");
        logger.info("Recupero prenotazioni per stato - Stato: {}", stato);
        logger.info("FINE METODO getPrenotazioniByStato");
        return prenotazioneRepository.findByStato(stato);
    }
    
    // Lista prenotazioni future
    public List<Prenotazione> getPrenotazioniFuture() {
        logger.info("INIZIO METODO getPrenotazioniFuture");
        logger.info("Recupero prenotazioni future a partire da ora");
        logger.info("FINE METODO getPrenotazioniFuture");
        return prenotazioneRepository.findPrenotazioniFuture(LocalDateTime.now());
    }
    
    // Metodo admin per annullare qualsiasi prenotazione
    @Transactional
    public boolean annullaPrenotazioneAsAdmin(Long prenotazioneId, Long adminId, String motivo) {
        logger.info("INIZIO METODO annullaPrenotazioneAsAdmin");
        logger.info("Richiesta annullamento prenotazione da admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        Optional<Prenotazione> prenotazioneOpt = prenotazioneRepository.findById(prenotazioneId);
        if (prenotazioneOpt.isEmpty()) {
            logger.info("Prenotazione non trovata - PrenotazioneId: {}", prenotazioneId);
            return false;
        }
        
        logger.info("Richiesta annullamento prenotazione da admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        Prenotazione prenotazione = prenotazioneOpt.get();
        
        // Verifica che l'admin esista
        logger.info("Verifica esistenza admin - AdminId: {}", adminId);
        Optional<Utente> admin = utenteRepository.findById(adminId);
        if (admin.isEmpty() || !"admin".equals(admin.get().getRuolo())) {
            logger.info("Utente non è un admin valido - AdminId: {}", adminId);
            return false;
        }
        
        logger.info("Annullamento prenotazione da parte dell'admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        // Gli admin possono eliminare qualsiasi prenotazione, indipendentemente dallo stato
        prenotazione.setStato("annullata");
        
        logger.info("Aggiornamento descrizione prenotazione per indicare azione admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", prenotazioneId, adminId, motivo);
        // Aggiorna la descrizione per indicare l'azione admin
        String descrizioneOriginale = prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "";
        String nuovaDescrizione = descrizioneOriginale + 
            (descrizioneOriginale.isEmpty() ? "" : " | ") +
            "ANNULLATA DALL'AMMINISTRATORE: " + motivo;
        prenotazione.setDescrizione(nuovaDescrizione);

        logger.info("Salvataggio prenotazione aggiornata - PrenotazioneId: {}", prenotazioneId);
        prenotazioneRepository.save(prenotazione);
        
        // Aggiorna lo stato dell'aula
        aggiornaStatoAula(prenotazione.getAula().getId());
        
        logger.info("FINE METODO annullaPrenotazioneAsAdmin");
        return true;
    }

    // Aggiorna una prenotazione esistente
    @Transactional
    public Prenotazione updatePrenotazione(Long prenotazioneId, Long aulaId, Long corsoId, Long utenteId, LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        logger.info("INIZIO METODO updatePrenotazione");
        logger.info("Richiesta aggiornamento prenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", prenotazioneId, aulaId, corsoId, utenteId, inizio, fine);
        
        // Trova la prenotazione esistente
        Optional<Prenotazione> prenotazioneOpt = prenotazioneRepository.findById(prenotazioneId);
        if (prenotazioneOpt.isEmpty()) {
            logger.info("Prenotazione con ID {} non trovata per aggiornamento", prenotazioneId);
            return null;
        }
        
        Prenotazione prenotazione = prenotazioneOpt.get();
        
        // Verifica autorizzazione - solo il creatore o un admin può modificare
        Optional<Utente> utente = utenteRepository.findById(utenteId);
        if (utente.isEmpty()) {
            logger.info("Utente con ID {} non trovato nel database per aggiornamento prenotazione", utenteId);
            return null;
        }
        
        boolean isCreatore = prenotazione.getUtente().getId().equals(utenteId);
        boolean isAdmin = "admin".equals(utente.get().getRuolo());
        
        if (!isCreatore && !isAdmin) {
            logger.info("Utente ID {} non autorizzato a modificare la prenotazione ID {}", utenteId, prenotazioneId);
            return null;
        }
        
        // Verifica che l'aula esista
        Optional<Aula> aula = aulaRepository.findById(aulaId);
        if (aula.isEmpty()) {
            logger.info("Aula con ID {} non trovata nel database", aulaId);
            return null;
        }
        
        // Verifica disponibilità aula per il nuovo periodo (escludendo questa prenotazione)
        if (!isAulaDisponibileEscludendo(aulaId, inizio, fine, prenotazioneId)) {
            logger.info("Aula ID {} non disponibile per il periodo {} - {} (esclusa prenotazione {})", aulaId, inizio, fine, prenotazioneId);
            return null;
        }
        
        // Corso opzionale
        Optional<Corso> corso = Optional.empty();
        if (corsoId != null) {
            corso = corsoRepository.findById(corsoId);
            if (corso.isEmpty()) {
                logger.info("Corso con ID {} non trovato nel database", corsoId);
                return null;
            }
        }
        
        // Aggiorna i campi
        logger.info("Aggiornamento campi prenotazione - PrenotazioneId: {}", prenotazioneId);
        prenotazione.setAula(aula.get());
        prenotazione.setCorso(corso.orElse(null));
        prenotazione.setInizio(inizio);
        prenotazione.setFine(fine);
        prenotazione.setDescrizione(descrizione);
        
        Prenotazione savedPrenotazione = prenotazioneRepository.save(prenotazione);
        logger.info("Prenotazione aggiornata con successo - ID: {}, Aula: '{}', Utente: '{}', Periodo: {} - {}", savedPrenotazione.getId(), aula.get().getNome(), utente.get().getEmail(), inizio, fine);
        logger.info("FINE METODO updatePrenotazione");
        return savedPrenotazione;
    }
    
    // Verifica disponibilità aula escludendo una prenotazione specifica
    private boolean isAulaDisponibileEscludendo(Long aulaId, LocalDateTime inizio, LocalDateTime fine, Long prenotazioneIdEsclusa) {
        logger.info("Verifica disponibilità aula escludendo prenotazione - AulaId: {}, Periodo: {} - {}, Esclusa: {}", aulaId, inizio, fine, prenotazioneIdEsclusa);
        List<Prenotazione> conflitti = prenotazioneRepository.findConflittingReservationsExcluding(aulaId, inizio, fine, prenotazioneIdEsclusa);
        boolean disponibile = conflitti.isEmpty();
        logger.info("Risultato verifica disponibilità aula (esclusa prenotazione {}) - AulaId: {}, Disponibile: {}", prenotazioneIdEsclusa, aulaId, disponibile);
        return disponibile;
    }
}
