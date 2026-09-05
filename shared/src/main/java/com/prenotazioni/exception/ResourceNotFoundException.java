package com.prenotazioni.exception;

/**
 * La risorsa richiesta non esiste. Diventa un 404.
 *
 * Sostituisce il null che i service restituivano per "non trovata", indistinguibile da
 * quello che restituivano per "operazione fallita". Il caso peggiore era deleteAula, che
 * usava false per entrambi: un'aula ancora referenziata da una prenotazione rispondeva 404,
 * cioe' "non esiste", su un'aula che esisteva eccome.
 */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }

    /** Scorciatoia per il caso piu' comune: entita' cercata per id. */
    public static ResourceNotFoundException perId(String tipo, String codice, Object id) {
        return new ResourceNotFoundException(codice,
                String.format("%s not found with id: %s", tipo, id),
                String.format("%s richiesta non esiste.", tipo));
    }
}
