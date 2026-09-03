package com.prenotazioni.prenotazione.repository;

import com.prenotazioni.prenotazione.model.Corso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ICorsoRepository extends JpaRepository<Corso, Long> {}
