package com.prenotazioni.auth.service;

import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.auth.repository.IUtenteRepository;
import com.prenotazioni.auth.client.DatiUtenteClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UtenteService {

    private static final Logger logger = LoggerFactory.getLogger(UtenteService.class);

    private final IUtenteRepository utenteRepository;
    
    private final DatiUtenteClient datiUtenteClient;

    UtenteService(IUtenteRepository utenteRepository, DatiUtenteClient datiUtenteClient) {
        this.utenteRepository = utenteRepository;
        this.datiUtenteClient = datiUtenteClient;
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

    /**
     * Cancella l'utente e cio' che gli appartiene negli altri servizi.
     *
     * NON e' piu' atomica, ed e' bene saperlo leggendo questo metodo e non scoprendolo
     * da un dato incoerente. Prima della separazione era una sola transazione e le chiavi
     * esterne garantivano che non restasse nulla di orfano; ora notifiche e prenotazioni
     * stanno in database che questo servizio non puo' toccare.
     *
     * @Transactional resta, ma copre soltanto la riga in questo database: non annulla nulla
     * di cio' che gli altri servizi hanno gia' fatto.
     *
     * L'ordine e' voluto: prima i dati dipendenti, poi l'utente. Se una cancellazione a
     * valle fallisce l'utente NON viene rimosso, cosi' l'operazione resta ripetibile e le
     * righe rimaste hanno ancora un proprietario a cui essere ricondotte. Rimuovere prima
     * l'utente lascerebbe dati di cui non si sa piu' di chi siano.
     */
    @Transactional
    public void deleteById(Long id) {
        logger.debug("INIZIO - Eliminazione utente e dati associati per ID: {}", id);

        if (!datiUtenteClient.eliminaDatiDi(id)) {
            logger.error("Utente ID {} NON eliminato: la cancellazione dei suoi dati in un altro "
                    + "servizio e' fallita. L'operazione e' ripetibile.", id);
            throw new IllegalStateException(
                    "Impossibile eliminare i dati dell'utente negli altri servizi.");
        }

        logger.info("Eliminazione utente ID: {}", id);
        utenteRepository.deleteById(id);
        logger.debug("FINE - Eliminazione completata per utente ID: {}", id);
    }
}