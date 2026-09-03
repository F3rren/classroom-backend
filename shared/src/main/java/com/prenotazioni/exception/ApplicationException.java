package com.prenotazioni.exception;

/**
 * Base delle eccezioni con cui il dominio segnala un esito che il chiamante deve conoscere.
 *
 * Esiste perche' prima quegli esiti viaggiavano come null e false: un service restituiva
 * null e il controller doveva DEDURRE il motivo, scrivendo a mano un messaggio che poteva
 * essere sbagliato. Con un tipo, il motivo viaggia insieme all'errore e GlobalExceptionHandler
 * lo traduce una volta sola, invece che ogni controller a modo suo.
 *
 * I tre campi sono quelli che l'envelope espone gia':
 *  - errorCode: codice stabile su cui il frontend puo' ramificare;
 *  - getMessage(): descrizione tecnica, finisce nei log;
 *  - userMessage: la frase mostrata a chi usa l'applicazione.
 *
 * Astratta di proposito: e' il sottotipo a determinare lo stato HTTP, quindi lanciare
 * "un'eccezione applicativa generica" non deve essere possibile - vorrebbe dire non aver
 * deciso che genere di errore sia.
 */
public abstract class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    protected ApplicationException(String errorCode, String message, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
