package com.prenotazioni.auth.repository;

import com.prenotazioni.auth.model.Utente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IUtenteRepository extends JpaRepository<Utente, Long> {
	Utente findByEmail(String email);
	Utente findByUsername(String username);
}
