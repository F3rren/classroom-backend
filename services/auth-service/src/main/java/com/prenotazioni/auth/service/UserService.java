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

    private final UserRepository userRepository;
    
    private final UserDataClient userDataClient;

    UserService(UserRepository userRepository, UserDataClient userDataClient) {
        this.userRepository = userRepository;
        this.userDataClient = userDataClient;
    }

    public User findById(Long id) {
        logger.debug("START - looking up user by ID: {}", id);
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            logger.debug("END - user found by ID: {}", id);
        } else {
            logger.warn("END - no user found with ID: {}", id);
        }
        return user;
    }

    /**
     * Deletes the user and what belongs to them in the other services.
     *
     * It is NOT atomic any more, and that is better learnt from reading this method than
     * discovered from inconsistent data. Before the split it was a single transaction and
     * the foreign keys guaranteed nothing was left orphaned; notifications and bookings now
     * live in databases this service cannot touch.
     *
     * @Transactional stays, but it covers only the row in this database: it undoes nothing
     * of what the other services have already done.
     *
     * The order is deliberate: dependent data first, the user last. If a downstream deletion
     * fails the user is NOT removed, so the operation stays repeatable and the rows left
     * behind still have an owner they can be traced back to. Removing the user first would
     * leave data nobody can attribute any more.
     */
    @Transactional
    public void deleteById(Long id) {
        logger.debug("START - deleting user and associated data for ID: {}", id);

        List<String> notDeleted = userDataClient.deleteDataOf(id);
        if (!notDeleted.isEmpty()) {
            String what = String.join(" e ", notDeleted);
            logger.error("User ID {} NOT deleted: {} could not be removed. "
                    + "L'operazione e' ripetibile e va ripetuta.", id, what);
            // 503 and not 500: it tells the reader that retrying is worth it, and retrying
            // is the only thing that finishes the deletion. With "internal server error",
            // retrying was not the obvious conclusion, and the half-done work stayed put.
            throw new ServiceUnavailableException("USER_DELETE_INCOMPLETE",
                    "Could not delete " + what + " of utente " + id,
                    "L'utente non e' stato eliminato perche' " + what + " non si sono potute "
                            + "rimuovere. Riprova fra qualche istante.");
        }

        logger.info("Deleting user ID: {}", id);
        userRepository.deleteById(id);
        logger.debug("END - deletion complete for user ID: {}", id);
    }
}