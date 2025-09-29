package com.prenotazioni.service;

import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IUtenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UtenteService {

    @Autowired
    private IUtenteRepository utenteRepository;

    public Utente findById(Long id) {
        return utenteRepository.findById(id).orElse(null);
    }

    public Utente findByUsername(String username) {
        return utenteRepository.findByUsername(username);
    }

    public Utente findByEmail(String email) {
        return utenteRepository.findByEmail(email);
    }

    public List<Utente> findAll() {
        return utenteRepository.findAll();
    }

    public Utente save(Utente utente) {
        return utenteRepository.save(utente);
    }

    public void deleteById(Long id) {
        utenteRepository.deleteById(id);
    }

    public boolean existsById(Long id) {
        return utenteRepository.existsById(id);
    }
}