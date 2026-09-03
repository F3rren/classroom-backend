package com.prenotazioni.exception;

/**
 * La richiesta contiene un dato che non e' accettabile. Diventa un 400.
 *
 * Serve per i casi che Bean Validation non copre perche' dipendono dal dominio: un valore
 * di enum inesistente in un path variable, per esempio. Va usata con parsimonia - se una
 * regola si puo' esprimere con un'annotazione su un DTO, quella e' la sede giusta, perche'
 * finisce anche nello schema OpenAPI.
 *
 * NON e' un alias di IllegalArgumentException. Quella indica un errore di programmazione
 * ed e' giusto che finisca in un 500: mapparla a 400 nasconderebbe i bug del server
 * facendoli sembrare colpa del chiamante.
 */
public class InvalidRequestException extends ApplicationException {

    public InvalidRequestException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
