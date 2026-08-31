package com.prenotazioni.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Richiesta di login. Solo @NotBlank qui: il formato email e la lunghezza minima
 * password restano controlli manuali nel controller, ESEGUITI DOPO il rate limiter -
 * spostarli in Bean Validation li farebbe girare prima, indebolendo la protezione
 * anti brute-force (un attaccante potrebbe far fallire la validazione senza mai
 * consumare un tentativo dal rate limiter).
 */
public class LoginRequest {

    @NotBlank(message = "L'email è obbligatoria per effettuare il login.")
    private String email;

    @NotBlank(message = "La password è obbligatoria per effettuare il login.")
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
