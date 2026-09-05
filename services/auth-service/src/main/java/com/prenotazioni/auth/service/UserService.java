package com.prenotazioni.auth.service;

import java.util.List;
import com.prenotazioni.exception.ServiceUnavailableException;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.auth.repository.UserRepository;
import com.prenotazioni.auth.client.UserDataClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository utenteRepository;
    
    private final UserDataClient userDataClient;

    UserService(UserRepository utenteRepository, UserDataClient userDataClient) {
        this.utenteRepository = utenteRepository;
        this.userDataClient = userDataClient;
    }

    public User findById(Long id) {
        logger.debug("INIZIO - Ricerca utente per ID: {}", id);
        User utente = utenteRepository.findById(id).orElse(null);
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

        List<String> nonEliminati = userDataClient.eliminaDatiDi(id);
        if (!nonEliminati.isEmpty()) {
            String cosa = String.join(" e ", nonEliminati);
            logger.error("Utente ID {} NON eliminato: {} non si sono potute cancellare. "
                    + "L'operazione e' ripetibile e va ripetuta.", id, cosa);
            // 503 e non 500: dice a chi legge che ripetere ha senso, ed e' l'unica cosa che
            // porta a termine la cancellazione. Con "errore interno del server" ripetere non
            // era la conclusione ovvia, e la meta' fatta restava li'.
            throw new ServiceUnavailableException("USER_DELETE_INCOMPLETE",
                    "Could not delete " + cosa + " of utente " + id,
                    "L'utente non e' stato eliminato perche' " + cosa + " non si sono potute "
                            + "rimuovere. Riprova fra qualche istante.");
        }

        logger.info("Eliminazione utente ID: {}", id);
        utenteRepository.deleteById(id);
        logger.debug("FINE - Eliminazione completata per utente ID: {}", id);
    }
}