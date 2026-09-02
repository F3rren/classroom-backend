package com.prenotazioni.exception;

/**
 * Sollevata quando una scrittura su una prenotazione viola il vincolo DB
 * anti-sovrapposizione (prenotazioni_no_overlap) per una prenotazione concorrente.
 * Consolida in un unico punto (GlobalExceptionHandler) i 3 cataloghi di
 * DataIntegrityViolationException gia' presenti in PrenotazioneController.
 */
public class BookingConflictException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public BookingConflictException(String errorCode, String message, String userMessage) {
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
