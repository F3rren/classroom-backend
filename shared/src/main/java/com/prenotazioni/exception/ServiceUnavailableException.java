package com.prenotazioni.exception;

/**
 * Un servizio a valle non ha risposto, e l'operazione va ripetuta.
 *
 * Si traduce in 503 e non in 500, ed e' una distinzione che serve a chi legge la risposta:
 * un 500 dice "e' rotto qualcosa", un 503 dice "riprova". Sono due azioni diverse.
 *
 * Il caso concreto per cui e' nata: la cancellazione di un utente cancella prima i suoi dati
 * negli altri servizi e solo dopo l'utente. Se uno di quei servizi non risponde, l'operazione
 * si ferma a meta' - e l'unica cosa che la porta a termine e' che qualcuno la ripeta. Finche'
 * quel fallimento arrivava come "errore interno del server", ripetere non era la conclusione
 * ovvia, e la meta' fatta restava li'.
 *
 * Va usata SOLO quando ripetere ha davvero senso: un servizio irraggiungibile, un timeout,
 * un 5xx a valle. Un rifiuto a valle - un 400, un 404 - non e' questo: ripetere darebbe lo
 * stesso esito e l'invito a farlo sarebbe una bugia.
 */
public class ServiceUnavailableException extends ApplicationException {

    public ServiceUnavailableException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
