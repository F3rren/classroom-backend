package com.prenotazioni.prenotazione.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller per gestire il routing di Single Page Application (React)
 * Reindirizza tutte le richieste non-API a index.html per permettere a React Router di funzionare
 */
@Controller
public class WebController {

    /**
     * Reindirizza tutte le richieste (tranne quelle per /api e file statici) a index.html
     * Questo permette a React Router di gestire il routing lato client
     */
    @RequestMapping(value = {
        "/",
        "/login",
        "/dashboard",
        "/admin/**",
        "/rooms/**",
        "/bookings/**",
        "/profile/**"
    })
    public String forward(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Non reindirizzare le chiamate API
        if (path.startsWith("/api")) {
            return null;
        }
        
        // Reindirizza a index.html per SPA routing
        return "forward:/index.html";
    }
}