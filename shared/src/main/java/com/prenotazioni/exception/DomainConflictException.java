package com.prenotazioni.exception;

/**
 * L'operazione contrasta con lo stato attuale dei dati. Diventa un 409.
 *
 * E' il caso del nome di aula gia' in uso, che prima era un null e che il controller
 * presentava come 400: uno stato che il chiamante non puo' correggere riformulando la
 * richiesta non e' un errore di sintassi, e 409 lo dice, 400 no.
 */
public class DomainConflictException extends ApplicationException {

    public DomainConflictException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
