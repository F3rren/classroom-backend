package com.prenotazioni.exception;

/**
 * Conflitto specifico delle prenotazioni: la fascia oraria e' gia' occupata.
 *
 * Resta un tipo a se' anche se non aggiunge campi a DomainConflictException, perche'
 * PrenotazioneController lo lancia esplicitamente quando traduce la violazione del vincolo
 * anti-sovrapposizione, ed e' quel nome a rendere leggibile il punto in cui accade.
 * Ereditando, non duplica piu' errorCode e userMessage.
 */
public class BookingConflictException extends DomainConflictException {

    public BookingConflictException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
