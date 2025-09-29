package com.prenotazioni.repository;

import com.prenotazioni.model.Utente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IUtenteRepository extends JpaRepository<Utente, Long> {
	Utente findByEmailAndPassword(String email, String password);
	Utente findByEmail(String email);
	Utente findByUsername(String username);
}
